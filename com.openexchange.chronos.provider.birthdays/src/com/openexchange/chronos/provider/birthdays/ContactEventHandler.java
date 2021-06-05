/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 *
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.chronos.provider.birthdays;

import static com.openexchange.chronos.provider.birthdays.BirthdaysCalendarProvider.PROVIDER_ID;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.I2i;
import static com.openexchange.osgi.Tools.requireService;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import com.openexchange.chronos.common.CreateResultImpl;
import com.openexchange.chronos.common.DefaultCalendarEvent;
import com.openexchange.chronos.common.DeleteResultImpl;
import com.openexchange.chronos.common.UpdateResultImpl;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.account.AdministrativeCalendarAccountService;
import com.openexchange.chronos.provider.basic.BasicCalendarAccess;
import com.openexchange.chronos.provider.caching.AlarmHelper;
import com.openexchange.chronos.service.CalendarEvent;
import com.openexchange.chronos.service.CalendarEventNotificationService;
import com.openexchange.chronos.service.CreateResult;
import com.openexchange.chronos.service.DeleteResult;
import com.openexchange.chronos.service.UpdateResult;
import com.openexchange.context.ContextService;
import com.openexchange.event.CommonEvent;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.server.ServiceLookup;

/**
 * {@link ContactEventHandler}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since 7.10.0
 */
public class ContactEventHandler implements EventHandler {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContactEventHandler.class);

    /** The event topic used when a new contact was created */
    private static final String TOPIC_CREATE = "com/openexchange/groupware/contact/insert";

    /** The event topic used when a contact was updated */
    private static final String TOPIC_UPDATE = "com/openexchange/groupware/contact/update";

    /** The event topic used when a contact was deleted */
    private static final String TOPIC_DELETE = "com/openexchange/groupware/contact/delete";

    /** The handled event topics */
    public static final String[] TOPICS = { TOPIC_CREATE, TOPIC_UPDATE, TOPIC_DELETE };

    private final ServiceLookup services;

    /**
     * Initializes a new {@link ContactEventHandler}.
     *
     * @param services A service lookup reference
     */
    public ContactEventHandler(ServiceLookup services) {
        super();
        this.services = services;
    }

    @Override
    public void handleEvent(Event event) {
        if (null == event || null == event.getTopic() || event.containsProperty(CommonEvent.REMOTE_MARKER)) {
            LOG.debug("Skipping invalid event: {}", event);
            return;
        }
        CommonEvent commonEvent = (CommonEvent) event.getProperty(CommonEvent.EVENT_KEY);
        if (null == commonEvent) {
            LOG.info("Unable to handle event \"{}\" due to missing common event data, skipping.", event.getTopic());
            return;
        }
        Map<Integer, Set<Integer>> affectedUsersWithFolder = commonEvent.getAffectedUsersWithFolder();
        if (null == affectedUsersWithFolder || affectedUsersWithFolder.isEmpty()) {
            LOG.info("Unable to handle event \"{}\" due to incomplete information about affected users, skipping.", event.getTopic());
            return;
        }
        handle(event.getTopic(), commonEvent, affectedUsersWithFolder.keySet());
    }

    private void handle(String topic, CommonEvent event, Set<Integer> affectedUsers) {
        try {
            switch (topic) {
                case TOPIC_CREATE:
                    handleCreate(event.getContextId(), affectedUsers, (Contact) event.getActionObj());
                    break;
                case TOPIC_UPDATE:
                    handleUpdate(event.getContextId(), affectedUsers, (Contact) event.getOldObj(), (Contact) event.getActionObj());
                    break;
                case TOPIC_DELETE:
                    handleDelete(event.getContextId(), affectedUsers, (Contact) event.getActionObj());
                    break;
                default:
                    LOG.info("Skipping event handling for unexpected topic \"{}\".", topic);
                    break;
                // TODO: React to folder delete and update events as well, since they can cause contacts to be removed or to be inaccessible.
            }
        } catch (Exception e) {
            LOG.warn("Error handling event \"{}\": {}", topic, e.getMessage(), e);
        }
    }

    private void handleCreate(int contextId, Collection<Integer> affectedUserIds, Contact newContact) throws OXException {
        if (null != newContact.getBirthday()) {
            processNewBirthday(contextId, affectedUserIds, newContact);
        }
    }

    private void handleUpdate(int contextId, Collection<Integer> affectedUserIds, Contact originalContact, Contact updatedContact) throws OXException {
        if (null == originalContact.getBirthday()) {
            if (null != updatedContact.getBirthday()) {
                processNewBirthday(contextId, affectedUserIds, updatedContact);
            }
            return;
        } else if (null != updatedContact.getBirthday()) {
            if (false == originalContact.getBirthday().equals(updatedContact.getBirthday())) {
                processChangedBirthday(contextId, affectedUserIds, updatedContact);
            }
            return;
        } else {
            processRemovedBirthday(contextId, affectedUserIds, originalContact);
        }
    }

    public void handleDelete(int contextId, Set<Integer> affectedUserIds, Contact deletedContact) throws OXException {
        if (null == deletedContact.getBirthday()) {
            return;
        }
        processRemovedBirthday(contextId, affectedUserIds, deletedContact);
    }

    private void processNewBirthday(int contextId, Collection<Integer> affectedUserIds, Contact contact) throws OXException {
        Context context = services.getService(ContextService.class).getContext(contextId);
        for (CalendarAccount account : getBirthdaysCalendarAccounts(context, affectedUserIds)) {
            com.openexchange.chronos.Event event = loadEvent(contact, context, account);
            insertDefaultAlarms(context, account, contact);
            notifyHandlers(EventType.creation, context, account, event, contact);
        }
    }

    private com.openexchange.chronos.Event loadEvent(Contact contact, Context context, CalendarAccount account) throws OXException {
        com.openexchange.chronos.Event event = getEventConverter(account).getSeriesMaster(contact);
        return getAlarmHelper(context, account).applyAlarms(event);
    }

    private void processChangedBirthday(int contextId, Collection<Integer> affectedUserIds, Contact contact) throws OXException {
        Context context = services.getService(ContextService.class).getContext(contextId);
        for (CalendarAccount account : getBirthdaysCalendarAccounts(context, affectedUserIds)) {
            com.openexchange.chronos.Event event = loadEvent(contact, context, account);
            recreateAlarms(context, account, contact);
            notifyHandlers(EventType.update, context, account, event, contact);
        }
    }

    private void recreateAlarms(Context context, CalendarAccount account, Contact contact) throws OXException {
        deleteAlarms(context, account, contact);
        insertDefaultAlarms(context, account, contact);
    }

    private void processRemovedBirthday(int contextId, Collection<Integer> affectedUserIds, Contact contact) throws OXException {
        Context context = services.getService(ContextService.class).getContext(contextId);
        for (CalendarAccount account : getBirthdaysCalendarAccounts(context, affectedUserIds)) {
            com.openexchange.chronos.Event event = loadEvent(contact, context, account);
            deleteAlarms(context, account, contact);
            notifyHandlers(EventType.deletion, context, account, event, contact);
        }
    }

    private void deleteAlarms(Context context, CalendarAccount account, Contact contact) throws OXException {
        String eventId = getEventConverter(account).getEventId(contact);
        getAlarmHelper(context, account).deleteAlarms(eventId);
    }

    private void notifyHandlers(EventType type, Context context, CalendarAccount account, com.openexchange.chronos.Event original, Contact contact) throws OXException {
        com.openexchange.chronos.Event updated = loadEvent(contact, context, account);
        CalendarEvent event = getEvent(type, context, account, original, updated);
        services.getService(CalendarEventNotificationService.class).notifyHandlers(event);
    }

    private CalendarEvent getEvent(EventType type, Context context, CalendarAccount account, com.openexchange.chronos.Event original, com.openexchange.chronos.Event updated) {
        List<CreateResult> creations = null;
        List<UpdateResult> updates = null;
        List<DeleteResult> deletions = null;
        switch (type) {
            case creation:
                creations = Collections.singletonList(new CreateResultImpl(updated));
                break;
            case deletion:
                deletions = Collections.singletonList(new DeleteResultImpl(new Date().getTime(), original));
                break;
            case update:
                updates = Collections.singletonList(new UpdateResultImpl(original, updated));
                break;
            default:
                // Should never occur
                return null;
        }
        return new DefaultCalendarEvent(context.getContextId(),
                                        account.getAccountId(),
                                        account.getUserId(),
                                        Collections.singletonMap(I(account.getUserId()), Collections.singletonList(BasicCalendarAccess.FOLDER_ID)),
                                        creations,
                                        updates,
                                        deletions,
                                        null,
                                        null,
                                        null);
    }

    private void insertDefaultAlarms(Context context, CalendarAccount account, Contact contact) throws OXException {
        AlarmHelper alarmHelper = getAlarmHelper(context, account);
        if (alarmHelper.hasDefaultAlarms()) {
            alarmHelper.insertDefaultAlarms(getEventConverter(account).getSeriesMaster(contact));
        }
    }

    private AlarmHelper getAlarmHelper(Context context, CalendarAccount account) {
        return new AlarmHelper(services, context, account);
    }

    private EventConverter getEventConverter(CalendarAccount account) {
        return new EventConverter(services, Locale.US, account.getUserId());
    }

    private List<CalendarAccount> getBirthdaysCalendarAccounts(Context context, Collection<Integer> affectedUserIds) throws OXException {
        AdministrativeCalendarAccountService accountService = requireService(AdministrativeCalendarAccountService.class, services);
        return accountService.getAccounts(context.getContextId(), I2i(affectedUserIds), PROVIDER_ID);
    }

    private enum EventType {
        creation,
        deletion,
        update
    }

}
