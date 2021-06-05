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

package com.openexchange.chronos.itip.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.dmfs.rfc5545.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.SchedulingControl;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.mapping.DefaultEventUpdate;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.itip.generators.ITipMailGenerator;
import com.openexchange.chronos.itip.generators.ITipNotificationMailGeneratorFactory;
import com.openexchange.chronos.itip.generators.NotificationMail;
import com.openexchange.chronos.itip.generators.NotificationParticipant;
import com.openexchange.chronos.itip.osgi.Services;
import com.openexchange.chronos.itip.sender.MailSenderService;
import com.openexchange.chronos.itip.tools.ITipUtils;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.service.CalendarEvent;
import com.openexchange.chronos.service.CalendarHandler;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarService;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.CreateResult;
import com.openexchange.chronos.service.DeleteResult;
import com.openexchange.chronos.service.EventUpdate;
import com.openexchange.chronos.service.RecurrenceIterator;
import com.openexchange.chronos.service.RecurrenceService;
import com.openexchange.chronos.service.UpdateResult;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.notify.State;
import com.openexchange.groupware.tools.mappings.common.CollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.ItemUpdate;

/**
 * {@link ITipHandler}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.0
 */
public class ITipHandler implements CalendarHandler {

    private final static Logger LOG = LoggerFactory.getLogger(ITipHandler.class);

    /**
     * Contains the fields that are updated if a series is updated
     */
    private final static EventField[] SERIES_UPDATE = new EventField[] { EventField.SEQUENCE };

    /**
     * Contains the fields that are updated if a master event has a new change exception
     */
    private final static EventField[] MASTER_EXCEPTION_UPDATE = new EventField[] { EventField.CHANGE_EXCEPTION_DATES };

    /**
     * Contains the fields that are updated if a master event has a new delete exception
     */
    private final static EventField[] EXCEPTION_DELETE = new EventField[] { EventField.DELETE_EXCEPTION_DATES };

    private static final EnumSet<EventField> allButAttendee = EnumSet.allOf(EventField.class);
    private static final EnumSet<AttendeeField> allButDeleted = EnumSet.allOf(AttendeeField.class);
    static {
        allButAttendee.remove(EventField.ATTENDEES);
        allButAttendee.remove(EventField.LAST_MODIFIED);
        allButAttendee.remove(EventField.MODIFIED_BY);
        allButAttendee.remove(EventField.TIMESTAMP);
        allButDeleted.remove(AttendeeField.HIDDEN);
        allButDeleted.remove(AttendeeField.TRANSP);
    }

    private final ITipNotificationMailGeneratorFactory generators;
    private final MailSenderService sender;

    public ITipHandler(ITipNotificationMailGeneratorFactory generatorFactory, MailSenderService sender) {
        this.generators = generatorFactory;
        this.sender = sender;
    }

    @Override
    public void handle(CalendarEvent event) {
        if (!shouldHandle(event)) {
            return;
        }

        try {
            List<CreateResult> creations = new LinkedList<>(event.getCreations());
            List<UpdateResult> updates = new LinkedList<>(event.getUpdates());
            List<DeleteResult> deletions = new LinkedList<>(event.getDeletions());

            if (creations.size() > 0) {
                for (CreateResult create : creations) {
                    handleCreate(create, creations, updates, event);
                }
            }

            if (updates.size() > 0) {
                Set<UpdateResult> ignore = new HashSet<>();
                for (UpdateResult update : updates) {
                    if (ignore.contains(update)) {
                        continue;
                    }
                    handleUpdate(update, updates, ignore, event);
                }
            }

            if (deletions.size() > 0) {
                Set<DeleteResult> ignore = new HashSet<>();
                for (DeleteResult delete : deletions) {
                    if (ignore.contains(delete)) {
                        continue;
                    }
                    handleDelete(delete, deletions, ignore, event);
                }
            }
        } catch (OXException oe) {
            LOG.error("Unable to handle CalendarEvent", oe);
        }
    }

    protected boolean shouldHandle(CalendarEvent event) {
        if (event == null || event.getAccountId() != CalendarAccount.DEFAULT_ACCOUNT.getAccountId()) {
            return false;
        }

        ConfigurationService configurationService = Services.getService(ConfigurationService.class);
        if (null == configurationService || false == configurationService.getBoolProperty("com.openexchange.calendar.useLegacyScheduling", false)) {
            return false;
        }

        if (event.getCalendarParameters() != null) {
            if (SchedulingControl.NONE.equals(event.getCalendarParameters().get(CalendarParameters.PARAMETER_SCHEDULING, SchedulingControl.class))) {
                return false;
            }
        }

        outer: for (UpdateResult updateResult : event.getUpdates()) {
            if (updateResult.getUpdatedFields().stream().anyMatch(field -> allButAttendee.contains(field))) {
                break outer;
            }
            CollectionUpdate<Attendee, AttendeeField> attendeeUpdates = updateResult.getAttendeeUpdates();
            if (!attendeeUpdates.getAddedItems().isEmpty() || !attendeeUpdates.getRemovedItems().isEmpty()) {
                break outer;
            }
            List<? extends ItemUpdate<Attendee, AttendeeField>> updatedItems = attendeeUpdates.getUpdatedItems();
            if (updatedItems.isEmpty()) {
                break outer;
            }
            for (ItemUpdate<Attendee, AttendeeField> updatedItem : updatedItems) {
                if (updatedItem.getUpdatedFields().stream().anyMatch(field -> allButDeleted.contains(field))) {
                    break outer;
                }
            }
            return false;
        }

        return true;
    }

    private void handleCreate(CreateResult create, List<CreateResult> creations, List<UpdateResult> updates, CalendarEvent event) throws OXException {
        UpdateResult master = getExceptionMaster(create, updates);
        if (null != master) {
            // Gather other creations to this series
            List<CreateResult> group = creations.stream().filter(c -> master.getUpdate().getId().equals(c.getCreatedEvent().getSeriesId())).collect(Collectors.toList());
            // Handle as update
            for (CreateResult c : group) {
                if (false == isIgnorableException(master, c)) {
                    handle(event, State.Type.NEW, master.getOriginal(), c.getCreatedEvent(), null);
                }
            }
            // Remove master to avoid additional mail
            updates.remove(master);
        } else {
            handle(event, State.Type.NEW, null, create.getCreatedEvent(), null);
        }
    }

    private void handleUpdate(UpdateResult update, List<UpdateResult> updates, Set<UpdateResult> ignore, CalendarEvent event) throws OXException {
        List<UpdateResult> exceptions = Collections.emptyList();

        if (CalendarUtils.isSeriesMaster(update.getUpdate()) && update.containsAnyChangeOf(EXCEPTION_DELETE)) {
            // Handle as delete
            RecurrenceService service = Services.getService(RecurrenceService.class, true);
            RecurrenceIterator<Event> recurrenceIterator = service.iterateEventOccurrences(update.getOriginal(), null, null);

            // Get delete exceptions
            List<RecurrenceId> newDeletions = new LinkedList<>(update.getUpdate().getDeleteExceptionDates());
            if (null != update.getOriginal().getDeleteExceptionDates() && false == update.getOriginal().getDeleteExceptionDates().isEmpty()) {
                newDeletions.removeAll(update.getOriginal().getDeleteExceptionDates());
            }

            // Iterate over all occurrences until all new deletions are consumed
            while (recurrenceIterator.hasNext() && false == newDeletions.isEmpty()) {
                Event next = recurrenceIterator.next();
                Iterator<RecurrenceId> iterator = newDeletions.iterator();
                Inner: while (iterator.hasNext()) {
                    RecurrenceId recurrenceId = iterator.next();
                    if (next.getRecurrenceId().equals(recurrenceId)) {
                        handle(event, State.Type.DELETED, null, EventMapper.getInstance().copy(next, new Event(), (EventField[]) null), null);
                        // Consume element from above list
                        iterator.remove();
                        break Inner;
                    }
                }
            }
            return;
        } else if (update.getUpdate().containsSeriesId()) {
            // Check for series update
            // Get all events of the series
            String seriesId = update.getUpdate().getSeriesId();
            List<UpdateResult> eventGroup = updates.stream().filter(u -> !ignore.contains(u)).filter(u -> seriesId.equals(u.getUpdate().getSeriesId())).collect(Collectors.toList());

            // Check if there is a group to handle
            if (eventGroup.size() > 1) {
                // Check if master is present and if every update is a series update
                Optional<UpdateResult> master = eventGroup.stream().filter(u -> seriesId.equals(u.getUpdate().getId())).findFirst();
                if (master.isPresent() && CalendarUtils.isSeriesMaster(master.get().getUpdate())) {
                    UpdateResult masterUpdate = master.get();
                    if (eventGroup.stream().filter(u -> u.containsAnyChangeOf(SERIES_UPDATE)).collect(Collectors.toList()).size() == eventGroup.size()) {
                        // Series update, remove those items from the update list and the master from the exceptions
                        ignore.addAll(eventGroup);
                        eventGroup.remove(masterUpdate);

                        // Set for processing
                        update = masterUpdate;
                        exceptions = eventGroup;
                    } else {
                        Set<EventField> fields = new HashSet<>(masterUpdate.getUpdatedFields());
                        fields.remove(EventField.TIMESTAMP);
                        fields.remove(EventField.LAST_MODIFIED);
                        if (fields.isEmpty()) {
                            // Exception update, no update on master
                            ignore.add(masterUpdate);
                        }
                    }
                }
            }
        }

        if (isMove(update)) {
            return;
        }

        // Handle update
        handle(event, State.Type.MODIFIED, update.getOriginal(), update.getUpdate(), exceptions.stream().map(UpdateResult::getUpdate).collect(Collectors.toList()));
    }

    private static final EventField[] NOT_MOVE_EVENT_FIELDS;

    static {
        List<EventField> allitems = new ArrayList<>(Arrays.asList(EventField.values()));
        allitems.removeAll(Arrays.asList(EventField.ATTENDEES, EventField.TIMESTAMP, EventField.LAST_MODIFIED));
        NOT_MOVE_EVENT_FIELDS = allitems.toArray(new EventField[allitems.size()]);
    }

    /**
     * 
     * Checks if the {@link UpdateResult} only contains a move operation
     *
     * @param update The {@link UpdateResult} to check
     * @return <code>true</code> if it is only a move operation, <code>false</code> otherwise
     */
    private boolean isMove(UpdateResult update) {
        // @formatter:off
        if (update.containsAnyChangeOf(NOT_MOVE_EVENT_FIELDS) ||
           update.getAttendeeUpdates() == null || 
           (update.getAttendeeUpdates().getAddedItems() != null && update.getAttendeeUpdates().getAddedItems().isEmpty() == false) ||
           (update.getAttendeeUpdates().getRemovedItems() != null && update.getAttendeeUpdates().getRemovedItems().isEmpty() == false) ||
           update.getAttendeeUpdates().getUpdatedItems() == null ||
           update.getAttendeeUpdates().getUpdatedItems().size() != 1 || 
           update.getAttendeeUpdates().getUpdatedItems().get(0).getUpdatedFields().size() != 1 || 
           update.getAttendeeUpdates().getUpdatedItems().get(0).getUpdatedFields().iterator().next().equals(AttendeeField.FOLDER_ID) == false ) {
            return false;
        }
        // @formatter:on

        return true;
    }

    private void handleDelete(DeleteResult delete, List<DeleteResult> deletions, Set<DeleteResult> ignore, CalendarEvent event) throws OXException {
        List<DeleteResult> exceptions = Collections.emptyList();

        // Check for series update
        if (delete.getOriginal().containsSeriesId()) {
            // Get all events of the series
            String seriesId = delete.getOriginal().getSeriesId();
            List<DeleteResult> eventGroup = deletions.stream().filter(u -> !ignore.contains(u)).filter(u -> seriesId.equals(u.getOriginal().getSeriesId())).collect(Collectors.toList());

            // Check if there is a group to handle
            if (eventGroup.size() > 1) {
                // Check if master is present
                Optional<DeleteResult> master = eventGroup.stream().filter(u -> seriesId.equals(u.getOriginal().getId())).findFirst();
                if (master.isPresent() && CalendarUtils.isSeriesMaster(master.get().getOriginal())) {
                    // Series update, remove those items from the update list and the master from the exceptions
                    ignore.addAll(eventGroup);
                    eventGroup.remove(master.get());

                    // Set for processing
                    delete = master.get();
                    exceptions = eventGroup;
                }
            }
        }

        handle(event, State.Type.DELETED, null, delete.getOriginal(), exceptions.stream().map(DeleteResult::getOriginal).collect(Collectors.toList()));
    }

    private void handle(CalendarEvent event, State.Type type, Event original, Event update, List<Event> exceptions) throws OXException {
        CalendarSession session = getCalendarService().init(event.getSession(), event.getCalendarParameters());
        int onBehalfOf = onBehalfOf(session, update, event.getCalendarUser());
        CalendarUser principal = ITipUtils.getPrincipal(event.getCalendarParameters());
        String comment = event.getCalendarParameters().get(CalendarParameters.PARAMETER_COMMENT, String.class);

        // Copy event to avoid UOE due UnmodifieableEvent
        if (null != original) {
            original = EventMapper.getInstance().copy(original, new Event(), (EventField[]) null);
        }
        ITipMailGenerator generator = generators.create(original, EventMapper.getInstance().copy(update, new Event(), (EventField[]) null), session, onBehalfOf, principal, comment);
        List<NotificationParticipant> recipients = generator.getRecipients();
        for (NotificationParticipant notificationParticipant : recipients) {
            NotificationMail mail;
            switch (type) {
                case NEW:
                    if (CalendarUtils.isSeriesMaster(original) && CalendarUtils.isSeriesException(update)) {
                        mail = generator.generateCreateExceptionMailFor(notificationParticipant);
                    } else {
                        mail = generator.generateCreateMailFor(notificationParticipant);
                    }
                    break;
                case MODIFIED:
                    mail = generator.generateUpdateMailFor(notificationParticipant);
                    break;
                case DELETED:
                    mail = generator.generateDeleteMailFor(notificationParticipant);
                    break;
                default:
                    mail = null;
            }
            if (mail != null) {
                if (mail.getStateType() == null) {
                    mail.setStateType(type);
                }
                if (null != exceptions && null != mail.getMessage()) {
                    // Set exceptions
                    for (Event exception : exceptions) {
                        mail.getMessage().addException(exception);
                    }
                }
                sender.sendMail(mail, session, principal, comment);
            }
        }
    }

    private int onBehalfOf(CalendarSession session, Event event, int calendarUser) {
        return calendarUser == session.getUserId() ? CalendarUtils.isOrganizer(event, calendarUser) ? -1 : event.getOrganizer().getEntity() : calendarUser;
    }

    private UpdateResult getExceptionMaster(CreateResult create, List<UpdateResult> updates) {
        if (CalendarUtils.isSeriesException(create.getCreatedEvent())) {
            return updates.stream().filter(u -> CalendarUtils.isSeriesMaster(u.getUpdate()) && create.getCreatedEvent().getSeriesId().equals(u.getUpdate().getId()) && u.containsAnyChangeOf(MASTER_EXCEPTION_UPDATE)).findAny().orElse(null);
        }
        return null;
    }

    /**
     * Don't send messages in case the attendee added an personal alarm
     *
     * @param master The master event to build an {@link EventUpdate} on
     * @param created The exception that has been created
     * @return <code>true</code> if the creation of the exception can be ignored.
     */
    private boolean isIgnorableException(UpdateResult master, CreateResult created) {
        EventUpdate eventUpdate = DefaultEventUpdate.builder() //@formatter:off
            .originalEvent(master.getUpdate())
            .updatedEvent(created.getCreatedEvent())
            .ignoredEventFields(EventField.TIMESTAMP, EventField.LAST_MODIFIED, EventField.START_DATE, EventField.END_DATE, EventField.CREATED, EventField.RECURRENCE_RULE,
                                EventField.RECURRENCE_ID, EventField.ALARMS, EventField.EXTENDED_PROPERTIES, EventField.CHANGE_EXCEPTION_DATES, EventField.ID, EventField.ATTENDEE_PRIVILEGES)
            .considerUnset(true)
            .ignoreDefaults(true)
            .build(); //@formatter:on
        /*
         * Exception was created to add/change/remove an alarm without changing anything else. So ignore it
         */
        if (eventUpdate.isEmpty()) {
            // Have a detailed look on start and end date again
            return compareByDate(master.getOriginal(), created.getCreatedEvent());
        }
        return false;
    }

    /**
     * Compares if two events are starting and ending on the same hours and minutes.
     *
     * @param e1 One event
     * @param e2 The other event
     * @return <code>true</code> if the start and end date are equal based on the hour and minutes
     *         <code>false</code> otherwise
     */
    private boolean compareByDate(Event e1, Event e2) {
        return compareByDate(e1.getStartDate(), e2.getStartDate()) && compareByDate(e1.getEndDate(), e2.getEndDate());
    }

    /**
     * 
     * Compares if two DateTimes have the same hours and minutes set
     *
     * @param date1 One {@link DateTime}
     * @param date2 The other {@link DateTime}
     * @return <code>true</code> if hours and minutes match, <code>false</code> otherwise
     */
    private boolean compareByDate(DateTime date1, DateTime date2) {
        if (date1.getHours() == 0) {
            // Still all day event?
            return date2.getHours() == 0;
        }

        if (date1.getHours() == date2.getHours()) {
            // Check by minutes
            return date1.getMinutes() == date2.getMinutes();
        }
        return false;
    }

    private CalendarService getCalendarService() throws OXException {
        return Services.get().getServiceSafe(CalendarService.class);
    }
}
