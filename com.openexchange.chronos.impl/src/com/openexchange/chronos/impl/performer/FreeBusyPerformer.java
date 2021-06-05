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

import static com.openexchange.chronos.common.CalendarUtils.filter;
import static com.openexchange.chronos.common.CalendarUtils.find;
import static com.openexchange.chronos.common.CalendarUtils.getFields;
import static com.openexchange.chronos.common.CalendarUtils.isGroupScheduled;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesMaster;
import static com.openexchange.chronos.common.CalendarUtils.matches;
import static com.openexchange.chronos.impl.Utils.anonymizeIfNeeded;
import static com.openexchange.chronos.impl.Utils.getRecurrenceIterator;
import static com.openexchange.tools.arrays.Collections.put;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Duration;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.Availability;
import com.openexchange.chronos.Available;
import com.openexchange.chronos.BusyType;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.FbType;
import com.openexchange.chronos.FreeBusyTime;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.common.AvailabilityUtils;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DefaultRecurrenceData;
import com.openexchange.chronos.common.FreeBusyUtils;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.impl.Check;
import com.openexchange.chronos.impl.Comparators;
import com.openexchange.chronos.impl.Utils;
import com.openexchange.chronos.impl.osgi.Services;
import com.openexchange.chronos.service.AvailableField;
import com.openexchange.chronos.service.CalendarAvailabilityService;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.FreeBusyResult;
import com.openexchange.chronos.service.RecurrenceIterator;
import com.openexchange.chronos.service.SearchOptions;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.exception.OXException;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.util.TimeZones;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link FreeBusyPerformer}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.0
 */
public class FreeBusyPerformer extends AbstractFreeBusyPerformer {

    private static final boolean AVAILABILITY_ENABLED = false;

    /**
     * Initializes a new {@link FreeBusyPerformer}.
     *
     * @param storage The underlying calendar storage
     * @param session The calendar session
     */
    public FreeBusyPerformer(CalendarSession session, CalendarStorage storage) {
        super(session, storage);
    }

    /**
     * Performs the free/busy operation.
     *
     * @param attendees The attendees to get the free/busy data for
     * @param from The start of the requested time range
     * @param until The end of the requested time range
     * @param merge <code>true</code> to merge the resulting free/busy-times, <code>false</code>, otherwise
     * @return The free/busy times for each of the requested attendees, wrapped within a free/busy result structure
     */
    public Map<Attendee, FreeBusyResult> perform(List<Attendee> attendees, Date from, Date until, boolean merge) throws OXException {
        if (null == attendees || attendees.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Attendee, FreeBusyResult> results = new HashMap<Attendee, FreeBusyResult>(attendees.size());
        /*
         * resolve passed attendees prior lookup & get intersecting events per resolved attendee
         */
        Map<Attendee, Attendee> resolvedAttendees = resolveAttendees(session.getEntityResolver(), attendees);
        Map<Attendee, List<Event>> eventsPerAttendee = getOverlappingEvents(new ArrayList<Attendee>(resolvedAttendees.keySet()), from, until);
        /*
         * derive (merged) free/busy times for found events, mapped back to the requested attendees
         */
        Map<Attendee, List<FreeBusyTime>> freeBusyPerAttendee = new HashMap<Attendee, List<FreeBusyTime>>(eventsPerAttendee.size());
        for (Map.Entry<Attendee, List<Event>> entry : eventsPerAttendee.entrySet()) {
            Attendee attendee = resolvedAttendees.get(entry.getKey());
            if (null == attendee) {
                session.addWarning(CalendarExceptionCodes.UNEXPECTED_ERROR.create("Skipping free/busy times from unexpected attendee " + entry.getKey()));
                continue;
            }
            List<Event> events = entry.getValue();
            if (null == events || events.isEmpty()) {
                freeBusyPerAttendee.put(attendee, Collections.emptyList());
                continue;
            }
            List<FreeBusyTime> freeBusyTimes = FreeBusyPerformerUtil.adjustToBoundaries(FreeBusyPerformerUtil.getFreeBusyTimes(events, getTimeZone(attendee)), from, until);
            if (merge && 1 < freeBusyTimes.size()) {
                freeBusyTimes = FreeBusyUtils.mergeFreeBusy(freeBusyTimes);
            }
            freeBusyPerAttendee.put(attendee, freeBusyTimes);
        }

        // Disabled until further notice
        if (!AVAILABILITY_ENABLED) {
            for (Attendee attendee : attendees) {
                List<FreeBusyTime> freeBusyTimes = freeBusyPerAttendee.get(attendee);
                if (null == freeBusyTimes) {
                    OXException e = CalendarExceptionCodes.INVALID_CALENDAR_USER.create(attendee.getUri(), Autoboxing.I(attendee.getEntity()), attendee.getCuType());
                    results.put(attendee, new FreeBusyResult(null, Collections.singletonList(e)));
                } else {
                    results.put(attendee, new FreeBusyResult(freeBusyTimes, null));
                }
            }
            return results;
        }

        // Get the available time for the attendees
        CalendarAvailabilityService availabilityService = Services.getService(CalendarAvailabilityService.class);
        Map<Attendee, Availability> availabilityPerAttendee = availabilityService.getAttendeeAvailability(session, attendees, from, until);

        TimeZone timeZone = Utils.getTimeZone(session);
        // Expand any recurring available instances
        expandRecurringInstances(until, availabilityPerAttendee, timeZone);
        // Adjust the intervals
        adjustIntervals(from, until, availabilityPerAttendee, timeZone);

        // Check for not existing users
        for (Attendee attendee : attendees) {
            if (!freeBusyPerAttendee.containsKey(attendee)) {
                List<OXException> warnings = Collections.singletonList(CalendarExceptionCodes.INVALID_CALENDAR_USER.create(attendee.getUri(), Autoboxing.I(attendee.getEntity()), attendee.getCuType()));
                FreeBusyResult result = new FreeBusyResult();
                result.setWarnings(warnings);
                results.put(attendee, result);
            }
        }

        // Iterate over all calendar availability blocks for all attendees
        for (Attendee attendee : attendees) {
            Availability availability = availabilityPerAttendee.get(attendee);
            // For each availability block and each calendar available block create an equivalent free busy time slot
            List<FreeBusyTime> freeBusyTimes = availability == null ? new ArrayList<FreeBusyTime>(0) : calculateFreeBusyTimes(availability, timeZone);
            // Adjust the ranges of the FreeBusyTime slots that are marked as FREE
            // in regard to the mergedFreeBusyTimes
            List<FreeBusyTime> eventsFreeBusyTimes = freeBusyPerAttendee.get(attendee);
            if (eventsFreeBusyTimes == null) {
                continue;
            }
            if (eventsFreeBusyTimes.isEmpty()) {
                // The empty event free/busy list is unmodifiable, so we create a modifiable empty list
                eventsFreeBusyTimes = new ArrayList<>(0);
            }

            adjustRanges(freeBusyTimes, eventsFreeBusyTimes);
            // Sort by starting date
            Collections.sort(eventsFreeBusyTimes, Comparators.FREE_BUSY_DATE_TIME_COMPARATOR);

            // Create result
            FreeBusyResult result = new FreeBusyResult();
            result.setFreeBusyTimes(eventsFreeBusyTimes);

            // Set result for attendee
            results.put(attendee, result);
        }

        return results;
    }

    /**
     * Gets a list of overlapping events in a certain range for each requested attendee.
     *
     * @param attendees The attendees to query free/busy information for
     * @param from The start date of the period to consider
     * @param until The end date of the period to consider
     * @return The overlapping events, mapped to each attendee
     */
    private Map<Attendee, List<Event>> getOverlappingEvents(List<Attendee> attendees, Date from, Date until) throws OXException {
        /*
         * prepare & filter internal attendees for lookup
         */
        Check.hasFreeBusy(ServerSessionAdapter.valueOf(session.getSession()));
        attendees = filter(attendees, Boolean.TRUE, CalendarUserType.INDIVIDUAL, CalendarUserType.RESOURCE, CalendarUserType.GROUP);
        if (0 == attendees.size()) {
            return Collections.emptyMap();
        }
        /*
         * search (potentially) overlapping events for the attendees
         */
        Map<Attendee, List<Event>> eventsPerAttendee = new HashMap<Attendee, List<Event>>(attendees.size());
        for (Attendee attendee : attendees) {
            eventsPerAttendee.put(attendee, new ArrayList<Event>());
        }
        SearchOptions searchOptions = new SearchOptions(session).setRange(from, until);
        EventField[] fields = getFields(FreeBusyPerformerUtil.FREEBUSY_FIELDS, EventField.ORGANIZER, EventField.DELETE_EXCEPTION_DATES, EventField.CHANGE_EXCEPTION_DATES, EventField.RECURRENCE_ID);
        List<Event> eventsInPeriod = storage.getEventStorage().searchOverlappingEvents(attendees, true, searchOptions, fields);
        if (0 == eventsInPeriod.size()) {
            return eventsPerAttendee;
        }
        readAttendeeData(eventsInPeriod, Boolean.TRUE);
        /*
         * step through events & build free/busy per requested attendee
         */
        for (Event eventInPeriod : eventsInPeriod) {
            if (false == considerForFreeBusy(eventInPeriod)) {
                continue; // exclude events classified as 'private' (but keep 'confidential' ones)
            }
            for (Attendee attendee : attendees) {
                String folderID;
                if (isGroupScheduled(eventInPeriod)) {
                    /*
                     * include if attendee does attend
                     */
                    Attendee eventAttendee = find(eventInPeriod.getAttendees(), attendee);
                    if (null == eventAttendee || eventAttendee.isHidden() || ParticipationStatus.DECLINED.equals(eventAttendee.getPartStat())) {
                        continue;
                    }
                    folderID = CalendarUserType.INDIVIDUAL.equals(eventAttendee.getCuType()) ? chooseFolderID(eventInPeriod) : null;
                } else {
                    /*
                     * include if attendee matches event owner
                     */
                    if (false == matches(eventInPeriod.getCalendarUser(), attendee.getEntity())) {
                        continue;
                    }
                    folderID = eventInPeriod.getFolderId();
                }
                if (isSeriesMaster(eventInPeriod)) {
                    /*
                     * expand & add all (non overridden) instances of event series in period, expanded by the actual event duration
                     */
                    Date iteratorFrom = from;
                    if (null != eventInPeriod.getEndDate()) {
                        Duration duration = CalendarUtils.getDuration(eventInPeriod.getEndDate(), eventInPeriod.getStartDate());
                        iteratorFrom = new Date(duration.addTo(TimeZones.UTC, from.getTime()));
                    }
                    Iterator<RecurrenceId> iterator = getRecurrenceIterator(session, eventInPeriod, iteratorFrom, until);
                    while (iterator.hasNext()) {
                        put(eventsPerAttendee, attendee, FreeBusyPerformerUtil.getResultingOccurrence(getResultingEvent(eventInPeriod, folderID), eventInPeriod, iterator.next()));
                        getSelfProtection().checkEventCollection(eventsPerAttendee.get(attendee));
                    }
                } else {
                    /*
                     * add event in period
                     */
                    put(eventsPerAttendee, attendee, getResultingEvent(eventInPeriod, folderID));
                    getSelfProtection().checkEventCollection(eventsPerAttendee.get(attendee));
                }
                getSelfProtection().checkMap(eventsPerAttendee);
            }
        }
        return eventsPerAttendee;
    }

    /**
     * Performs the merged free/busy operation.
     *
     * @param attendees The attendees to query free/busy information for
     * @param from The start date of the period to consider
     * @param until The end date of the period to consider
     * @return The free/busy result
     */
    public Map<Attendee, List<FreeBusyTime>> performMerged(List<Attendee> attendees, Date from, Date until) throws OXException {
        Map<Attendee, List<Event>> eventsPerAttendee = getOverlappingEvents(attendees, from, until);
        Map<Attendee, List<FreeBusyTime>> freeBusyDataPerAttendee = new HashMap<Attendee, List<FreeBusyTime>>(eventsPerAttendee.size());
        for (Map.Entry<Attendee, List<Event>> entry : eventsPerAttendee.entrySet()) {
            freeBusyDataPerAttendee.put(entry.getKey(), FreeBusyPerformerUtil.mergeFreeBusy(entry.getValue(), from, until, Utils.getTimeZone(session)));
        }
        return freeBusyDataPerAttendee;
    }

    /**
     * Calculates the free/busy time ranges from the user defined availability and the free/busy operation
     *
     * @param attendees The attendees to calculate the free/busy information for
     * @param from The start time of the interval
     * @param until The end time of the interval
     * @return A {@link Map} with a {@link FreeBusyResult} per {@link Attendee}
     */
    public Map<Attendee, FreeBusyResult> performCalculateFreeBusyTime(List<Attendee> attendees, Date from, Date until) throws OXException {
        // Get the free busy data for the attendees
        Map<Attendee, List<FreeBusyTime>> freeBusyPerAttendee = performMerged(attendees, from, until);

        // Disabled until further notice
        if (!AVAILABILITY_ENABLED) {
            Map<Attendee, FreeBusyResult> results = new HashMap<>();
            for (Map.Entry<Attendee, List<FreeBusyTime>> attendeeEntry : freeBusyPerAttendee.entrySet()) {
                FreeBusyResult result = new FreeBusyResult();
                result.setFreeBusyTimes(attendeeEntry.getValue());
                results.put(attendeeEntry.getKey(), result);
            }
            return results;
        }

        // Get the available time for the attendees
        CalendarAvailabilityService availabilityService = Services.getService(CalendarAvailabilityService.class);
        Map<Attendee, Availability> availabilityPerAttendee = availabilityService.getAttendeeAvailability(session, attendees, from, until);

        TimeZone timeZone = Utils.getTimeZone(session);
        // Expand any recurring available instances
        expandRecurringInstances(until, availabilityPerAttendee, timeZone);
        // Adjust the intervals
        adjustIntervals(from, until, availabilityPerAttendee, timeZone);

        // Check for not existing users
        Map<Attendee, FreeBusyResult> results = new HashMap<>();
        for (Attendee attendee : attendees) {
            if (!freeBusyPerAttendee.containsKey(attendee)) {
                List<OXException> warnings = Collections.singletonList(CalendarExceptionCodes.INVALID_CALENDAR_USER.create(attendee.getUri(), Autoboxing.I(attendee.getEntity()), attendee.getCuType()));
                FreeBusyResult result = new FreeBusyResult();
                result.setWarnings(warnings);
                results.put(attendee, result);
            }
        }

        // Iterate over all calendar availability blocks for all attendees
        for (Attendee attendee : attendees) {
            Availability availability = availabilityPerAttendee.get(attendee);
            // For each availability block and each calendar available block create an equivalent free busy time slot
            List<FreeBusyTime> freeBusyTimes = availability == null ? new ArrayList<FreeBusyTime>(0) : calculateFreeBusyTimes(availability, timeZone);
            // Adjust the ranges of the FreeBusyTime slots that are marked as FREE
            // in regard to the mergedFreeBusyTimes
            List<FreeBusyTime> eventsFreeBusyTimes = freeBusyPerAttendee.get(attendee);
            if (eventsFreeBusyTimes == null) {
                continue;
            }
            if (eventsFreeBusyTimes.isEmpty()) {
                // The empty event free/busy list is unmodifiable, so we create a modifiable empty list
                eventsFreeBusyTimes = new ArrayList<>(0);
            }

            adjustRanges(freeBusyTimes, eventsFreeBusyTimes);
            // Sort by starting date
            Collections.sort(eventsFreeBusyTimes, Comparators.FREE_BUSY_DATE_TIME_COMPARATOR);

            // Create result
            FreeBusyResult result = new FreeBusyResult();
            result.setFreeBusyTimes(eventsFreeBusyTimes);

            // Set result for attendee
            results.put(attendee, result);
        }

        return results;
    }

    /**
     * Expands any recurring instances found in the specified available times {@link Map}
     *
     * @param availableTimes The available times for the attendees
     * @param timeZone The {@link TimeZone} of the user
     */
    private void expandRecurringInstances(Date until, Map<Attendee, Availability> availableTimes, TimeZone timeZone) throws OXException {
        for (Availability calendarAvailability : availableTimes.values()) {
            List<Available> auxAvailable = new ArrayList<>();
            Date endTime = new Date(CalendarUtils.getDateInTimeZone(calendarAvailability.getEndTime(), timeZone));
            for (Iterator<Available> iterator = calendarAvailability.getAvailable().iterator(); iterator.hasNext();) {
                Available available = iterator.next();
                // No recurring available block? Skip
                if (!available.contains(AvailableField.rrule)) {
                    continue;
                }
                Date availableStartTime = new Date(CalendarUtils.getDateInTimeZone(available.getStartTime(), timeZone));
                Date availableEndTime = new Date(CalendarUtils.getDateInTimeZone(available.getEndTime(), timeZone));
                RecurrenceIterator<RecurrenceId> recurrenceIterator = session.getRecurrenceService().iterateRecurrenceIds(new DefaultRecurrenceData(available.getRecurrenceRule(), available.getStartTime(), null));
                // Find out the duration of the "seed" available block
                long duration = availableEndTime.getTime() - availableStartTime.getTime();
                while (recurrenceIterator.hasNext()) {
                    RecurrenceId nextOccurrence = recurrenceIterator.next();
                    Date startOfOccurrence = new Date(nextOccurrence.getValue().getTimestamp());
                    // We reached the availability's end? Stop
                    if (startOfOccurrence.after(until) || startOfOccurrence.after(endTime)) {
                        break;
                    }
                    // Determine the end of the occurrence's instance
                    Date endOfOccurrence = new Date(startOfOccurrence.getTime() + duration);
                    // Create the occurrence
                    Available occ = available.clone();
                    occ.setStartTime(new DateTime(startOfOccurrence.getTime()));
                    occ.setEndTime(new DateTime(endOfOccurrence.getTime()));
                    // Add to the auxiliary
                    auxAvailable.add(occ);
                }
                // Remove the "seed" available, and only retain its occurrences
                iterator.remove();
            }
            calendarAvailability.getAvailable().addAll(auxAvailable);
        }
    }

    /**
     * Adjusts the ranges of the {@link FreeBusyTime} slots that are marked as FREE
     * in regard to the mergedFreeBusyTimes
     *
     * @param freeBusyTimes
     * @param eventsFreeBusyTimes
     */
    private void adjustRanges(List<FreeBusyTime> freeBusyTimes, List<FreeBusyTime> eventsFreeBusyTimes) {
        List<FreeBusyTime> auxiliaryList = new ArrayList<>();
        for (FreeBusyTime eventFreeBusyTime : eventsFreeBusyTimes) {
            Iterator<FreeBusyTime> iterator = freeBusyTimes.iterator();
            while (iterator.hasNext()) {
                FreeBusyTime availabilityFreeBusyTime = iterator.next();
                if (AvailabilityUtils.contained(availabilityFreeBusyTime.getStartTime(), availabilityFreeBusyTime.getEndTime(), eventFreeBusyTime.getStartTime(), eventFreeBusyTime.getEndTime())) {
                    // If the freeBusyTime block of the availability is entirely contained with in the freeBusyTime of the event, then ignore that freeBusyTime block
                    iterator.remove();
                } else if (AvailabilityUtils.precedesAndIntersects(eventFreeBusyTime.getStartTime(), eventFreeBusyTime.getEndTime(), availabilityFreeBusyTime.getStartTime(), availabilityFreeBusyTime.getEndTime())) {
                    // If the freeBusyTime of the event precedes and intersects with the freeBusyTime block of the availability
                    // then adjust the start time of the freeBusyTime block of the availability
                    availabilityFreeBusyTime.setStartTime(eventFreeBusyTime.getEndTime());
                } else if (AvailabilityUtils.succeedsAndIntersects(eventFreeBusyTime.getStartTime(), eventFreeBusyTime.getEndTime(), availabilityFreeBusyTime.getStartTime(), availabilityFreeBusyTime.getEndTime())) {
                    // If the freeBusyTime of the event precedes and intersects with the freeBusyTime block of the availability
                    // then adjust the end time of the freeBusyTime block of the availability
                    availabilityFreeBusyTime.setEndTime(eventFreeBusyTime.getStartTime());
                } else if (AvailabilityUtils.contained(eventFreeBusyTime.getStartTime(), eventFreeBusyTime.getEndTime(), availabilityFreeBusyTime.getStartTime(), availabilityFreeBusyTime.getEndTime())) {
                    // If the freeBusyTime of the event is entirely contained with in the freeBusyTime block of the availability,
                    // then split the freeBusyTime block of the availability and remove it from the list
                    splitFreeBusyTime(auxiliaryList, eventFreeBusyTime, availabilityFreeBusyTime);
                    // Remove
                    iterator.remove();
                }
            }
        }

        // Append all event freeBusyTime blocks
        eventsFreeBusyTimes.addAll(auxiliaryList);
        // Append all availability blocks
        eventsFreeBusyTimes.addAll(freeBusyTimes);
    }

    /**
     * Create an equivalent free busy time slot for each of the available blocks in the
     * specified {@link Availability}
     *
     * @param availability The {@link Availability} block
     * @param timeZone The user's {@link TimeZone}
     * @return A {@link List} with {@link FreeBusyTime} slots
     */
    private List<FreeBusyTime> calculateFreeBusyTimes(Availability availability, TimeZone timeZone) {
        List<FreeBusyTime> freeBusyTimes = new ArrayList<>();
        // Get the availability's start/end times
        Date availabilityStartTime = new Date(CalendarUtils.getDateInTimeZone(availability.getStartTime(), timeZone));
        Date endTime = new Date(CalendarUtils.getDateInTimeZone(availability.getEndTime(), timeZone));
        // Mark the entire block as busy if there are no available blocks
        if (availability.getAvailable().isEmpty()) {
            freeBusyTimes.add(createFreeBusyTime(availability.getBusyType(), availabilityStartTime, endTime));
            return freeBusyTimes;
        }

        Date availableStartTime = availabilityStartTime;
        Date availableEndTime = endTime;
        java.util.Collections.sort(availability.getAvailable(), Comparators.AVAILABLE_DATE_TIME_COMPARATOR);
        for (Available available : availability.getAvailable()) {
            // Get the available block's start/end times
            availableStartTime = new Date(CalendarUtils.getDateInTimeZone(available.getStartTime(), timeZone));
            availableEndTime = new Date(CalendarUtils.getDateInTimeZone(available.getEndTime(), timeZone));

            // Check if the first block is already FREE (i.e. available.startTime == availability.startTime)
            if (!availableStartTime.equals(availabilityStartTime)) {
                // Create a split for the availability component with the equivalent BusyType
                freeBusyTimes.add(createFreeBusyTime(availability.getBusyType(), availabilityStartTime, availableStartTime));
            }
            // Start from available end time on the next iteration
            availabilityStartTime = availableEndTime;
        }

        //Create the last block
        if (endTime.after(availableEndTime)) {
            freeBusyTimes.add(createFreeBusyTime(availability.getBusyType(), availabilityStartTime, endTime));
        }
        return freeBusyTimes;
    }

    /**
     * Adjusts (if necessary) the intervals of the specified {@link Availability} blocks according to the specified
     * range
     *
     * @param from The starting point of the interval
     * @param until The ending point of the interval
     * @param availableTimes The {@link Availability} blocks to adjust
     * @param timeZone The user's {@link TimeZone}
     */
    private void adjustIntervals(Date from, Date until, Map<Attendee, Availability> availableTimes, TimeZone timeZone) {
        for (Iterator<Availability> iterator = availableTimes.values().iterator(); iterator.hasNext();) {
            Availability availability = iterator.next();
            Date start = new Date(CalendarUtils.getDateInTimeZone(availability.getStartTime(), timeZone));
            Date end = new Date(CalendarUtils.getDateInTimeZone(availability.getEndTime(), timeZone));

            // Discard all availability blocks that are outside the requested interval
            if (start.after(until) || end.before(from)) {
                iterator.remove();
                continue;
            }

            // Check for any intersections
            if (end.after(from) && start.before(until)) {
                // Intersection detected; adjust the availability blocks as well
                if (start.before(from)) {
                    availability.setStartTime(new DateTime(from.getTime()));
                }
                if (end.after(until)) {
                    availability.setEndTime(new DateTime(until.getTime()));
                }

                Iterator<Available> availableIterator = availability.getAvailable().iterator();
                while (availableIterator.hasNext()) {
                    Available available = availableIterator.next();
                    Date availableStart = new Date(CalendarUtils.getDateInTimeZone(available.getStartTime(), timeZone));
                    Date availableEnd = new Date(CalendarUtils.getDateInTimeZone(available.getEndTime(), timeZone));
                    if (availableStart.after(until) || availableEnd.before(from)) {
                        availableIterator.remove();
                        continue;
                    }

                    // Intersection of available and range
                    if (availableEnd.after(from) && availableStart.before(until)) {
                        if (availableStart.before(from)) {
                            available.setStartTime(new DateTime(from.getTime()));
                        }
                        if (availableEnd.after(until)) {
                            available.setEndTime(new DateTime(until.getTime()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Given that the {@link Event}'s free busy time block is fully contained
     * with in the availability's block, it splits the specified {@link FreeBusyTime}
     * in two intervals:
     *
     * <p><b>Interval A</b>
     * <ul>
     * <li>Start time: The start time of the availability's block</li>
     * <li>End time: The start time of the event's block</li>
     * </ul>
     * </p>
     *
     * <p>
     * <b>Interval B</b>
     * <ul>
     * <li>Start time: The end time of the event's block</li>
     * <li>End time: The end time of the availability's block</li>
     * </ul>
     * </p>
     *
     * @param auxiliaryList The auxiliary {@link List} to add the new intervals
     * @param freeBusyTime The middle {@link FreeBusyTime} block
     */
    private void splitFreeBusyTime(List<FreeBusyTime> auxiliaryList, FreeBusyTime freeBusyTime, FreeBusyTime toSplit) {
        auxiliaryList.add(createFreeBusyTime(toSplit.getFbType(), toSplit.getStartTime(), freeBusyTime.getStartTime()));
        auxiliaryList.add(createFreeBusyTime(toSplit.getFbType(), freeBusyTime.getEndTime(), toSplit.getEndTime()));
    }

    /**
     * Creates and returns a {@link FreeBusyTime} instance with the specified {@link FbType}
     * in the specified interval
     *
     * @param fbType The free/busy type
     * @param startTime The start time of the instance
     * @param endTime The end time of the instance
     * @return The {@link FreeBusyTime}
     */
    private FreeBusyTime createFreeBusyTime(FbType fbType, Date startTime, Date endTime) {
        return new FreeBusyTime(fbType, startTime, endTime);
    }

    /**
     * Creates and returns a {@link FreeBusyTime} instance with the specified {@link BusyType}
     * in the specified interval
     *
     * @param busyType The free/busy type
     * @param startTime The start time of the instance
     * @param endTime The end time of the instance
     * @return The {@link FreeBusyTime}
     */
    private FreeBusyTime createFreeBusyTime(BusyType busyType, Date startTime, Date endTime) {
        return createFreeBusyTime(AvailabilityUtils.convertFreeBusyType(busyType), startTime, endTime);
    }

    /**
     * Gets a resulting userized event for the free/busy result based on the supplied event data. Only a subset of properties is copied
     * over, and a folder identifier is applied optionally, depending on the user's access permissions for the actual event data.
     *
     * @param event The event data to get the result for
     * @param folderID The folder identifier representing the user's view on the event, or <code>null</code> if not accessible in any folder
     * @return The resulting event representing the free/busy slot
     */
    private Event getResultingEvent(Event event, String folderID) throws OXException {
        if (null != folderID) {
            Event resultingEvent = EventMapper.getInstance().copy(event, new Event(), FreeBusyPerformerUtil.FREEBUSY_FIELDS);
            resultingEvent.setFolderId(folderID);
            return anonymizeIfNeeded(session, resultingEvent);
        }
        return EventMapper.getInstance().copy(event, new Event(), FreeBusyPerformerUtil.RESTRICTED_FREEBUSY_FIELDS);
    }

    /**
     * Resolves the supplied list of attendees using the supplied entity resolver, and associates them in a map to the passed ones.
     *
     * @param entityResolver The entity resolver to use
     * @param requestedAttendees The attendees as requested from the client
     * @return The resolved attendees in a map as keys, associated with their supplied variants as values
     */
    private static Map<Attendee, Attendee> resolveAttendees(EntityResolver entityResolver, List<Attendee> requestedAttendees) throws OXException {
        if (null == requestedAttendees || requestedAttendees.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Attendee, Attendee> resolvedAttendees = new HashMap<Attendee, Attendee>(requestedAttendees.size());
        for (Attendee requestedAttendee : requestedAttendees) {
            resolvedAttendees.put(entityResolver.prepare(requestedAttendee, true), requestedAttendee);
        }
        return resolvedAttendees;
    }

}
