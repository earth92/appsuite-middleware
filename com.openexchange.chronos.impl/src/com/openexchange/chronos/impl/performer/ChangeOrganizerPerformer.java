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

package com.openexchange.chronos.impl.performer;

import static com.openexchange.chronos.EventField.ORGANIZER;
import static com.openexchange.chronos.common.CalendarUtils.collectAttendees;
import static com.openexchange.chronos.impl.Check.requireUpToDateTimestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.RecurrenceRange;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DefaultCalendarObjectResource;
import com.openexchange.chronos.common.mapping.DefaultEventUpdate;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.impl.CalendarFolder;
import com.openexchange.chronos.impl.Check;
import com.openexchange.chronos.impl.Consistency;
import com.openexchange.chronos.impl.InternalCalendarResult;
import com.openexchange.chronos.impl.Role;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.type.PublicType;

/**
 * {@link ChangeOrganizerPerformer}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.2
 */
public class ChangeOrganizerPerformer extends AbstractUpdatePerformer {

    /**
     * Initializes a new {@link ChangeOrganizerPerformer}.
     *
     * @param storage The underlying calendar storage
     * @param session The calendar session
     * @param folder The calendar folder representing the current view on the events
     * @throws OXException If the calendar user can't be resolved
     */
    public ChangeOrganizerPerformer(CalendarStorage storage, CalendarSession session, CalendarFolder folder) throws OXException {
        super(storage, session, folder);
    }

    /**
     * Initializes a new {@link ChangeOrganizerPerformer}.
     *
     * @param storage The underlying calendar storage
     * @param session The calendar session
     * @param folder The calendar folder representing the current view on the events
     * @param roles The {@link Role}
     * @throws OXException If the calendar user can't be resolved
     */
    public ChangeOrganizerPerformer(CalendarStorage storage, CalendarSession session, CalendarFolder folder, EnumSet<Role> roles) throws OXException {
        super(storage, session, folder, roles);
    }

    /**
     * Performs the update operation.
     *
     * @param eventId The identifier of the event to update
     * @param recurrenceId The optional id of the recurrence.
     * @param organizer The new organizer to set
     * @param clientTimestamp The client timestamp to catch concurrent modifications
     * @return The update result
     * @throws OXException If data could not be loaded or constraints are not fulfilled
     */
    public InternalCalendarResult perform(String eventId, RecurrenceId recurrenceId, Organizer organizer, Long clientTimestamp) throws OXException {
        /*
         * Check if feature is enabled
         */
        if (false == session.getConfig().isOrganizerChangeAllowed()) {
            throw CalendarExceptionCodes.FORBIDDEN_CHANGE.create(eventId, ORGANIZER);
        }

        /*
         * Load original event data & check permissions
         */
        Event originalEvent = loadEventData(eventId);
        Check.eventIsVisible(folder, originalEvent);
        Check.eventIsInFolder(originalEvent, folder);
        requireWritePermissions(originalEvent);
        if (null != clientTimestamp) {
            requireUpToDateTimestamp(originalEvent, clientTimestamp.longValue());
        }

        /*
         * Ensure that new organizer is set and internal
         */
        if (null == organizer) {
            throw CalendarExceptionCodes.FORBIDDEN_CHANGE.create(eventId, ORGANIZER);
        }
        if (false == CalendarUtils.isInternal(organizer, CalendarUserType.INDIVIDUAL)) {
            throw CalendarExceptionCodes.INVALID_CALENDAR_USER.create(organizer.getUri(), Integer.valueOf(organizer.getEntity()), CalendarUserType.INDIVIDUAL);
        }
        /*
         * Ensure that event is group scheduled
         */
        if (CalendarUtils.isPseudoGroupScheduled(originalEvent) || false == CalendarUtils.isGroupScheduled(originalEvent)) {
            throw CalendarExceptionCodes.FORBIDDEN_CHANGE.create(eventId, ORGANIZER);
        }
        /*
         * Ensure that attendees and current organizer are internal users
         */
        if (containsExternal(originalEvent.getAttendees()) || false == CalendarUtils.isInternal(originalEvent.getOrganizer(), CalendarUserType.INDIVIDUAL)) {
            throw CalendarExceptionCodes.FORBIDDEN_CHANGE.create(eventId, ORGANIZER);
        }

        /*
         * Update a single event
         */
        if (false == CalendarUtils.isSeriesMaster(originalEvent)) {
            if (CalendarUtils.isSeriesException(originalEvent)) {
                throw CalendarExceptionCodes.FORBIDDEN_CHANGE.create(eventId, ORGANIZER);
            }
            Event updatedEvent = updateEvent(originalEvent, organizer);
            schedulingHelper.trackUpdate(new DefaultCalendarObjectResource(updatedEvent), new DefaultEventUpdate(originalEvent, updatedEvent));
            return resultTracker.getResult();
        }

        /*
         * Update a series starting at the master event
         */
        if (null == recurrenceId) {
            if (CalendarUtils.isSeriesException(originalEvent)) {
                throw CalendarExceptionCodes.FORBIDDEN_CHANGE.create(eventId, ORGANIZER);
            }
            Event updatedEvent = updateEvent(originalEvent, organizer);
            List<Event> updatedChangeExceptions = updateExceptions(updatedEvent, organizer);
            CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(updatedEvent, updatedChangeExceptions);
            schedulingHelper.trackUpdate(updatedResource, new DefaultEventUpdate(originalEvent, updatedEvent));
            return resultTracker.getResult();
        }

        /*
         * update "this and future" recurrences; first split the series at this recurrence
         */
        Check.recurrenceRangeMatches(recurrenceId, RecurrenceRange.THISANDFUTURE);
        Entry<CalendarObjectResource, CalendarObjectResource> splitResult = new SplitPerformer(this).split(originalEvent, recurrenceId.getValue(), null);
        CalendarObjectResource detachedSeries = splitResult.getKey();
        if (null != detachedSeries) {
            schedulingHelper.trackCreation(detachedSeries, collectAttendees(detachedSeries, Boolean.FALSE, (CalendarUserType[]) null));
        }

        Event updatedSeriesMaster = splitResult.getValue().getSeriesMaster();
        if (null == updatedSeriesMaster) {
            throw CalendarExceptionCodes.UNEXPECTED_ERROR.create("Unable to track update. Reason: Nothing was changed.");
        }
        updatedSeriesMaster = updateEvent(updatedSeriesMaster, organizer);
        List<Event> updatedChangeExceptions = updateExceptions(updatedSeriesMaster, organizer);
        CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(updatedSeriesMaster, updatedChangeExceptions);
        schedulingHelper.trackUpdate(updatedResource, new DefaultEventUpdate(originalEvent, updatedSeriesMaster));
        return resultTracker.getResult();
    }

    /**
     * Applies the organizer change to a new {@link Event} so that only relevant fields will be updated
     *
     * @param organizer The new organizer
     * @param originalEvent The original event
     * @param lastModified The date to set the {@link Event#getLastModified()} to
     * @return A delta {@link Event}
     * @throws OXException If resolving fails
     */
    private Event prepareChanges(Event originalEvent, Organizer organizer) {
        Event updatedEvent = new Event();
        updatedEvent.setId(originalEvent.getId());

        if (originalEvent.containsSeriesId()) {
            updatedEvent.setSeriesId(originalEvent.getSeriesId());
        }

        // Set new organizer
        updatedEvent.setOrganizer(organizer);

        // Update meta data
        updatedEvent.setSequence(originalEvent.getSequence() + 1);
        Consistency.setModified(timestamp, updatedEvent, calendarUser);

        return updatedEvent;
    }

    private boolean containsExternal(List<Attendee> attendees) {
        for (Attendee attendee : attendees) {
            if (CalendarUtils.isExternalUser(attendee)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update the organizer for a single event.
     *
     * @param originalEvent The original event
     * @param organizer The new organizer
     * @return The updated {@link Event}
     * @param lastModified The date to set the {@link Event#getLastModified()} to
     * @throws OXException If updating fails
     */
    private Event updateEvent(Event originalEvent, Organizer organizer) throws OXException {
        Event prepareChanges = prepareChanges(originalEvent, organizer);
        storage.getEventStorage().updateEvent(prepareChanges);
        insertOrganizerAsAttendee(originalEvent, organizer);
        Event updatedEvent = loadEventData(originalEvent.getId());
        resultTracker.trackUpdate(originalEvent, updatedEvent);
        return updatedEvent;
    }

    /**
     * Loads series exceptions and applies the new organizer to them.
     * Results will be tracked.
     *
     * @param updatedEvent The updated series master
     * @param organizer The new organizer
     * @param lastModified The date to set the {@link Event#getLastModified()} to
     * @return The updated change exceptions, or an empty list if there are none
     * @throws OXException If updating fails
     */
    private List<Event> updateExceptions(Event updatedEvent, Organizer organizer) throws OXException {
        List<Event> updatedChangeExceptions = new ArrayList<Event>();
        for (Event e : loadExceptionData(updatedEvent)) {
            storage.getEventStorage().updateEvent(prepareChanges(e, organizer));
            insertOrganizerAsAttendee(e, organizer);
            Event updatedChangeException = loadEventData(e.getId());
            updatedChangeExceptions.add(updatedChangeException);
            resultTracker.trackUpdate(e, updatedChangeException);
        }
        return updatedChangeExceptions;
    }

    /**
     * Adds the new organizer to the list of attendees if necessary
     *
     * @param originalEvent The original {@link Event}
     * @param organizer The new organizer as {@link CalendarUser}
     * @throws OXException If updating fails
     */
    private void insertOrganizerAsAttendee(Event originalEvent, Organizer organizer) throws OXException {
        // Ensure new organizer is attendee
        if (CalendarUtils.contains(originalEvent.getAttendees(), organizer)) {
            return;
        }

        // Add organizer as attendee
        int organizerId = organizer.getEntity();
        Attendee attendee = session.getEntityResolver().prepareUserAttendee(organizerId);
        if (false == PublicType.getInstance().equals(folder.getType())) {
            attendee.setFolderId(session.getConfig().getDefaultFolderId(organizerId));
            attendee.setPartStat(session.getConfig().getInitialPartStat(organizerId, false));
        } else {
            attendee.setPartStat(session.getConfig().getInitialPartStat(organizerId, true));
        }
        attendee.setTimestamp(timestamp.getTime());
        storage.getAttendeeStorage().insertAttendees(originalEvent.getId(), Collections.singletonList(attendee));

        // Add default alarm
        List<Alarm> alarms = CalendarUtils.isAllDay(originalEvent) ? session.getConfig().getDefaultAlarmDate(organizerId) : session.getConfig().getDefaultAlarmDateTime(organizerId);
        if (null != alarms && false == alarms.isEmpty()) {
            // Reload event to insert alarms in the correct folder
            Event event = loadEventData(originalEvent.getId());
            List<Alarm> insertAlarms = insertAlarms(event, organizerId, alarms, true);
            storage.getAlarmTriggerStorage().insertTriggers(event, Collections.singletonMap(Integer.valueOf(organizerId), insertAlarms));
        }
    }
}
