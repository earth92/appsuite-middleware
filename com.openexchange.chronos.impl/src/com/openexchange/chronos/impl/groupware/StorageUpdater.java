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

package com.openexchange.chronos.impl.groupware;

import static com.openexchange.chronos.common.CalendarUtils.find;
import static com.openexchange.chronos.common.CalendarUtils.getFields;
import static com.openexchange.chronos.impl.Utils.ACCOUNT_ID;
import static com.openexchange.chronos.impl.Utils.getFolderIdTerm;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.openexchange.chronos.Attachment;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.impl.CalendarFolder;
import com.openexchange.chronos.impl.Consistency;
import com.openexchange.chronos.impl.Utils;
import com.openexchange.chronos.impl.osgi.Services;
import com.openexchange.chronos.impl.session.CalendarUserSettings;
import com.openexchange.chronos.impl.session.DefaultCalendarSession;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.service.CalendarEventNotificationService;
import com.openexchange.chronos.service.CalendarHandler;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarService;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.SearchOptions;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.chronos.storage.operation.OSGiCalendarStorageOperation;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.search.SearchTerm;
import com.openexchange.search.SingleSearchTerm.SingleOperation;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;

/**
 * {@link StorageUpdater} - Update events by removing or replacing an attendee or by deleting the event.
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.0
 */
public class StorageUpdater {

    private static final EventField[] SEARCH_FIELDS = { EventField.ID, EventField.SERIES_ID, EventField.RECURRENCE_ID, EventField.FOLDER_ID, EventField.CREATED_BY, EventField.MODIFIED_BY,
        EventField.CALENDAR_USER, EventField.ORGANIZER, EventField.ATTENDEES };

    private final ServiceLookup services;
    private final CalendarStorage storage;
    private final SimpleResultTracker tracker;
    private final int attendeeId;
    private final CalendarUser replacement;
    private final Date date;
    private final EntityResolver entityResolver;
    private final CalendarEventNotificationService notificationService;

    /**
     * Initializes a new {@link StorageUpdater}.
     *
     * @param storage The underlying calendar storage
     * @param entityResolver The entity resolver to use
     * @param dbProvider The database provider to use
     * @param attendeeId The identifier of the attendee
     * @param destinationUserId The identifier of the destination user
     */
    public StorageUpdater(ServiceLookup services, CalendarStorage storage, EntityResolver entityResolver, CalendarEventNotificationService notificationService, int attendeeId, int destinationUserId) throws OXException {
        super();
        this.services = services;
        this.entityResolver = entityResolver;
        this.attendeeId = attendeeId;
        this.replacement = entityResolver.prepareUserAttendee(destinationUserId);
        this.tracker = new SimpleResultTracker();
        this.date = new Date();
        this.storage = storage;
        this.notificationService = notificationService;
    }

    /**
     * Removes the attendee from the event and updates it
     *
     * @param event The event to remove the attendee from
     * @throws OXException Various
     */
    void removeAttendeeFrom(Event event) throws OXException {
        List<Attendee> updatedAttendees = new ArrayList<Attendee>(event.getAttendees());
        Attendee attendee = CalendarUtils.find(updatedAttendees, attendeeId);
        if (null != attendee) {
            Event eventUpdate = new Event();
            eventUpdate.setId(event.getId());
            updatedAttendees.remove(attendee);
            eventUpdate.setAttendees(updatedAttendees);
            Consistency.setModified(date, eventUpdate, replacement);
            storage.getAlarmStorage().deleteAlarms(event.getId(), attendeeId);
            storage.getAlarmTriggerStorage().deleteTriggers(Collections.singletonList(event.getId()), attendeeId);
            storage.getAttendeeStorage().deleteAttendees(event.getId(), Collections.singletonList(attendee));
            storage.getEventStorage().updateEvent(eventUpdate);
            Event updatedEvent = EventMapper.getInstance().copy(event, null, (EventField[]) null);
            updatedEvent = EventMapper.getInstance().copy(eventUpdate, updatedEvent, (EventField[]) null);
            tracker.addUpdate(event, updatedEvent);
        }
    }

    /**
     * Removes the attendee from the events and update them
     *
     * @param events The events to remove the attendee from
     * @throws OXException Various
     */
    void removeAttendeeFrom(List<Event> events) throws OXException {
        for (final Event event : events) {
            removeAttendeeFrom(event);
        }
    }

    /**
     * Delete an event for the attendee
     *
     * @param event The event to delete
     * @param session The {@link Session}. Is used to remove the attachments for an event.
     * @throws OXException Various
     */
    void deleteEvent(Event event, Session session) throws OXException {
        storage.getAlarmStorage().deleteAlarms(event.getId());
        storage.getAlarmTriggerStorage().deleteTriggers(event.getId());
        storage.getAttachmentStorage().deleteAttachments(session, CalendarUtils.getFolderView(event, attendeeId), event.getId());
        storage.getAttendeeStorage().deleteAttendees(event.getId());
        storage.getConferenceStorage().deleteConferences(event.getId());
        storage.getEventStorage().deleteEvent(event.getId());
        tracker.addDelete(event, date.getTime());
    }

    /**
     * Delete all given events for the attendee
     *
     * @param events The events to delete
     * @param session The {@link Session}. Is used to remove the attachments for an event.
     * @throws OXException Various
     */
    void deleteEvent(List<Event> events, Session session) throws OXException {
        if (null == events || 0 == events.size()) {
            return;
        }
        List<String> eventIds = Arrays.asList(CalendarUtils.getObjectIDs(events));
        for (Event event : events) {
            storage.getAttachmentStorage().deleteAttachments(session, CalendarUtils.getFolderView(event, attendeeId), event.getId());
        }
        storage.getAlarmTriggerStorage().deleteTriggers(eventIds);
        storage.getAlarmStorage().deleteAlarms(eventIds);
        storage.getConferenceStorage().deleteConferences(eventIds);
        storage.getAttendeeStorage().deleteAttendees(eventIds);
        storage.getEventStorage().deleteEvents(eventIds);
        for (Event event : events) {
            tracker.addDelete(event, date.getTime());
        }
    }

    /**
     * Check event fields where the attendee could be referenced in and replaces the attendee
     *
     * @param event The {@link Event} to update
     * @throws OXException Various
     */
    void replaceAttendeeIn(Event event, Context context) throws OXException {
        Event eventUpdate = new Event();
        boolean updated = false;
        if (CalendarUtils.matches(event.getCreatedBy(), attendeeId)) {
            eventUpdate.setCreatedBy(replacement);
            updated = true;
        }
        if (CalendarUtils.matches(event.getModifiedBy(), attendeeId)) {
            eventUpdate.setModifiedBy(replacement);
            updated = true;
        }
        if (CalendarUtils.matches(event.getCalendarUser(), attendeeId)) {
            eventUpdate.setCalendarUser(replacement);
            updated = true;
        }
        if (null != event.getOrganizer()) {
            if (CalendarUtils.matches(event.getOrganizer(), attendeeId)) {
                replaceOrganizer(event, eventUpdate, context.getContextId());
                updated = true;
            } else if (CalendarUtils.matches(event.getOrganizer().getSentBy(), attendeeId)) {
                Organizer organizer = new Organizer(event.getOrganizer());
                organizer.setSentBy(replacement);
                eventUpdate.setOrganizer(organizer);
                updated = true;
            }
        }
        if (updated) {
            eventUpdate.setId(event.getId());
            Consistency.setModified(date, eventUpdate, replacement);
            storage.getEventStorage().updateEvent(eventUpdate);
            Event updatedEvent = EventMapper.getInstance().copy(event, null, (EventField[]) null);
            updatedEvent = EventMapper.getInstance().copy(eventUpdate, updatedEvent, (EventField[]) null);
            tracker.addUpdate(event, updatedEvent);
        }
    }

    /**
     * Check event fields where the attendee could be referenced in and replaces the attendee
     *
     * @param events The {@link Event}s to update
     * @throws OXException Various
     */
    void replaceAttendeeIn(List<Event> events, Context context) throws OXException {
        for (final Event event : events) {
            replaceAttendeeIn(event, context);
        }
    }

    /**
     * Removes any references to the internal user from multiple events. This includes removing the user from the list of attendees,
     * removing his alarms and -triggers, as well as replacing him in the created-by-, modified-by-, organizer- and calendar-user-
     * properties.
     *
     * @param events The events to remove the user from
     */
    public void removeUserReferences(List<Event> events, Context context) throws OXException {
        if (null == events || events.isEmpty()) {
            return;
        }
        /*
         * delete any alarms and alarm triggers
         */
        storage.getAlarmTriggerStorage().deleteTriggers(attendeeId);
        storage.getAlarmStorage().deleteAlarms(attendeeId);
        /*
         * remove user references from each event
         */
        for (Event event : events) {
            removeUserReferences(event, context);
        }
    }

    private void removeUserReferences(Event event, Context context) throws OXException {
        Event eventUpdate = new Event();
        boolean updated = false;
        /*
         * remove user in event metadata
         */
        if (CalendarUtils.matches(event.getCreatedBy(), attendeeId)) {
            eventUpdate.setCreatedBy(replacement);
            updated = true;
        }
        if (CalendarUtils.matches(event.getModifiedBy(), attendeeId)) {
            eventUpdate.setModifiedBy(replacement);
            updated = true;
        }
        if (CalendarUtils.matches(event.getCalendarUser(), attendeeId)) {
            eventUpdate.setCalendarUser(replacement);
            updated = true;
        }
        if (CalendarUtils.matches(event.getOrganizer(), attendeeId)) {
            replaceOrganizer(event, eventUpdate, context.getContextId());
            updated = true;
        }
        /*
         * remove user from attendees
         */
        Attendee attendee = CalendarUtils.find(event.getAttendees(), attendeeId);
        if (null != attendee) {
            storage.getAttendeeStorage().deleteAttendees(event.getId(), Collections.singletonList(attendee));
            List<Attendee> updatedAttendees = new ArrayList<Attendee>(event.getAttendees());
            updatedAttendees.remove(attendee);
            eventUpdate.setAttendees(updatedAttendees);
            updated = true;
        }
        /*
         * update event data in storage & track update result
         */
        if (updated) {
            eventUpdate.setId(event.getId());
            Consistency.setModified(date, eventUpdate, replacement);
            storage.getEventStorage().updateEvent(eventUpdate);
            Event updatedEvent = EventMapper.getInstance().copy(event, null, (EventField[]) null);
            updatedEvent = EventMapper.getInstance().copy(eventUpdate, updatedEvent, (EventField[]) null);
            tracker.addUpdate(event, updatedEvent);
        }
    }

    /**
     * Replaces the organizer with the replacement. Ensures further the correct event folder after a
     * switch and that the new organizer is added to the attendees
     * <p>
     * {@link CalendarParameters#PARAMETER_DEFAULT_ATTENDEE} is <b>NOT</b> validated.
     *
     * @param event The original event
     * @param eventUpdate The event delta to set the update in
     * @param contextId The context ID
     * @throws OXException Various
     * @see CalendarParameters#PARAMETER_DEFAULT_ATTENDEE
     */
    private void replaceOrganizer(Event event, Event eventUpdate, int contextId) throws OXException {
        eventUpdate.setOrganizer(entityResolver.applyEntityData(new Organizer(), replacement.getEntity()));
        /*
         * Ensure new organizer participates
         */
        Attendee attendee = CalendarUtils.find(event.getAttendees(), replacement.getEntity());
        if (null == attendee) {
            attendee = entityResolver.prepareUserAttendee(replacement.getEntity());
            attendee = entityResolver.applyEntityData(attendee, replacement.getEntity());
            attendee.setPartStat(ParticipationStatus.ACCEPTED);
            if (null == event.getFolderId()) {
                attendee.setFolderId(new CalendarUserSettings(contextId, replacement.getEntity(), services).getDefaultFolderId());
            }
            storage.getAttendeeStorage().insertAttendees(event.getId(), Collections.singletonList(attendee));
            List<Attendee> updatedAttendees = new ArrayList<Attendee>(event.getAttendees());
            updatedAttendees.add(attendee);
            eventUpdate.setAttendees(updatedAttendees);
        }
    }

    /**
     * Searches for all events in which the attendee participates in
     *
     * @return A {@link List} of {@link Event}s
     * @throws OXException If events can't be loaded
     */
    List<Event> searchEvents() throws OXException {
        return searchEvents(CalendarUtils.getSearchTerm(AttendeeField.ENTITY, SingleOperation.EQUALS, Integer.valueOf(attendeeId)));
    }

    /**
     * Searches for events with the given {@link SearchTerm}
     *
     * @param searchTerm The {@link SearchTerm}
     * @return A {@link List} of {@link Event}s
     * @throws OXException If events can't be loaded
     */
    List<Event> searchEvents(SearchTerm<?> searchTerm) throws OXException {
        List<Event> events = storage.getEventStorage().searchEvents(searchTerm, null, SEARCH_FIELDS);
        return storage.getUtilities().loadAdditionalEventData(attendeeId, events, new EventField[] { EventField.ATTENDEES });
    }

    /**
     * Notifies the {@link CalendarHandler}s
     *
     * @param session The admin session
     * @param parameters Additional calendar parameters, or <code>null</code> if not set
     */
    void notifyCalendarHandlers(Session session, CalendarParameters parameters) throws OXException {
        tracker.notifyCalenderHandlers(session, entityResolver, notificationService, parameters);
    }

    /**
     * Delete the default account for the attendee
     *
     * @throws OXException In case account can't be deleted
     */
    void deleteAccount() throws OXException {
        try {
            storage.getAccountStorage().deleteAccount(attendeeId, CalendarAccount.DEFAULT_ACCOUNT.getAccountId(), CalendarUtils.DISTANT_FUTURE);
        } catch (OXException e) {
            if ("CAL-4044".equals(e.getErrorCode())) {
                // "Account not found [account %1$d]"; ignore
                return;
            }
            throw e;
        }
    }

    /**
     * Purges all event data within a specific folder.
     *
     * @param folder The calendar folder to delete the contained events in
     * @return The number of deleted events
     */
    public static int removeEventsInFolder(CalendarFolder folder) throws OXException {
        /*
         * prepare search term to lookup events associated with the folder
         */
        CalendarSession session = new DefaultCalendarSession(folder.getSession(), Services.getService(CalendarService.class, true));
        SearchTerm<?> searchTerm = getFolderIdTerm(session, folder);
        /*
         * search & remove any found event data
         */
        final int BATCH_SIZE = 500;
        SearchOptions searchOptions = new SearchOptions().setLimits(0, BATCH_SIZE);
        return new OSGiCalendarStorageOperation<Integer>(Services.getServiceLookup(), session.getContextId(), ACCOUNT_ID) {

            @SuppressWarnings("synthetic-access")
            @Override
            protected Integer call(CalendarStorage storage) throws OXException {
                int totalDeleted = 0;
                int deleted;
                do {
                    deleted = removeEventsInFolder(storage, searchTerm, searchOptions, folder);
                    totalDeleted += deleted;
                } while (0 < deleted);
                return I(totalDeleted);
            }
        }.executeUpdate().intValue();
    }

    private static int removeEventsInFolder(CalendarStorage storage, SearchTerm<?> searchTerm, SearchOptions searchOptions, CalendarFolder folder) throws OXException {
        /*
         * load necessary data from original events in folder
         */
        EventField[] fields = getFields((EventField[]) null, (EventField[]) null);
        List<Event> originalEvents = storage.getEventStorage().searchEvents(searchTerm, searchOptions, fields);
        if (null == originalEvents || 0 == originalEvents.size()) {
            return 0;
        }
        originalEvents = storage.getUtilities().loadAdditionalEventData(-1, originalEvents, null);
        /*
         * derive kind of deletion for each event
         */
        List<Event> eventsToDelete = new ArrayList<Event>(originalEvents.size());
        List<Entry<Event, Attendee>> attendeesToDeleteByEvent = new ArrayList<Entry<Event, Attendee>>();
        for (Event originalEvent : originalEvents) {
            if (Utils.deleteRemovesEvent(folder, originalEvent)) {
                /*
                 * deletion of not group-scheduled event / by organizer / last user attendee
                 */
                eventsToDelete.add(originalEvent);
            } else {
                /*
                 * deletion as one of the attendees
                 */
                Attendee userAttendee = find(originalEvent.getAttendees(), folder.getCalendarUserId());
                if (null != userAttendee) {
                    attendeesToDeleteByEvent.add(new AbstractMap.SimpleEntry<Event, Attendee>(originalEvent, userAttendee));
                }
            }
        }
        /*
         * perform deletion & return number of processed events
         */
        if (0 < eventsToDelete.size()) {
            deleteEvents(storage, folder, eventsToDelete);
        }
        if (0 < attendeesToDeleteByEvent.size()) {
            deleteAttendees(storage, attendeesToDeleteByEvent);
        }
        return eventsToDelete.size() + attendeesToDeleteByEvent.size();
    }

    private static void deleteEvents(CalendarStorage storage, CalendarFolder folder, List<Event> eventsToDelete) throws OXException {
        /*
         * collect data to delete
         */
        List<String> eventIds = new ArrayList<String>(eventsToDelete.size());
        Map<String, List<Attachment>> attachmentsByEventId = new HashMap<String, List<Attachment>>();
        for (Event originalEvent : eventsToDelete) {
            eventIds.add(originalEvent.getId());
            if (null != originalEvent.getAttachments() && 0 < originalEvent.getAttachments().size()) {
                attachmentsByEventId.put(originalEvent.getId(), originalEvent.getAttachments());
            }
        }
        /*
         * perform deletions
         */
        storage.getAlarmStorage().deleteAlarms(eventIds);
        storage.getAlarmTriggerStorage().deleteTriggers(eventIds);
        storage.getConferenceStorage().deleteConferences(eventIds);
        storage.getAttendeeStorage().deleteAttendees(eventIds);
        storage.getEventStorage().deleteEvents(eventIds);
        if (0 < attachmentsByEventId.size()) {
            storage.getAttachmentStorage().deleteAttachments(folder.getSession(), Collections.singletonMap(folder.getId(), attachmentsByEventId));
        }
    }

    private static void deleteAttendees(CalendarStorage storage, List<Entry<Event, Attendee>> attendeesToDeleteByEvent) throws OXException {
        /*
         * collect data to delete
         */
        Map<Integer, List<String>> eventIdsByUserId = new HashMap<Integer, List<String>>();
        for (Entry<Event, Attendee> attendeeToDeleteByEvent : attendeesToDeleteByEvent) {
            Event event = attendeeToDeleteByEvent.getKey();
            Attendee attendee = attendeeToDeleteByEvent.getValue();
            com.openexchange.tools.arrays.Collections.put(eventIdsByUserId, I(attendee.getEntity()), event.getId());
        }
        /*
         * perform deletions
         */
        for (Entry<Event, Attendee> attendeeToDeleteByEvent : attendeesToDeleteByEvent) {
            Event event = attendeeToDeleteByEvent.getKey();
            Attendee attendee = attendeeToDeleteByEvent.getValue();
            storage.getAttendeeStorage().deleteAttendees(event.getId(), Collections.singletonList(attendee));
            storage.getAlarmStorage().deleteAlarms(event.getId(), attendee.getEntity());
        }
        for (Entry<Integer, List<String>> entry : eventIdsByUserId.entrySet()) {
            storage.getAlarmTriggerStorage().deleteTriggers(entry.getValue(), i(entry.getKey()));
        }
    }

}
