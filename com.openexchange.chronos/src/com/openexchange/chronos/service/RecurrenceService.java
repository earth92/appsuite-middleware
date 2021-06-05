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

package com.openexchange.chronos.service;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.exception.OXException;

/**
 * {@link RecurrenceService}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 * @since v7.10.0
 */
public interface RecurrenceService {

    /**
     * Calculates the expanded instances of a recurring event with optional boundaries and an optional limit.
     * If no limit is given an internal limit kicks in avoiding an endless calculation.
     * If no boundaries are given the calculation starts with the first occurrence and lasts until the end of the series.
     *
     * @param master The master event containing all necessary information like recurrence rule, star and end date, timezones etc.
     * @param start The left side boundary for the calculation. Optional, can be null.
     * @param end The right side boundary for the calculation. Optional, can be null.
     * @param limit The maximum number of calculated instances. Optional, can be null.
     * @return
     */
    public Iterator<Event> calculateInstances(Event master, Calendar start, Calendar end, Integer limit) throws OXException;

    /**
     * Calculates the expanded instances of a recurring event with optional boundaries and an optional limit.
     * If no limit is given an internal limit kicks in avoiding an endless calculation.
     * If no boundaries are given the calculation starts with the first occurrence and lasts until the end of the series.
     *
     * @param master The master event containing all necessary information like recurrence rule, star and end date, timezones etc.
     * @param start The left side boundary for the calculation. Optional, can be null.
     * @param end The right side boundary for the calculation. Optional, can be null.
     * @param limit The maximum number of calculated instances. Optional, can be null.
     * @param changeExceptions List of changeExceptions. Make sure this matches the change exception dates of the master, otherwise you might get weird results. Optional, can be null;
     * @return
     */
    public Iterator<Event> calculateInstancesRespectExceptions(Event master, Calendar start, Calendar end, Integer limit, List<Event> changeExceptions) throws OXException;

    /**
     * Calculates a reccurrence date position for a given 1-based position of a recurring event.
     *
     * @param master The master event containing all necessary information like recurrence rule, star and end date, timezones etc.
     * @param position The 1-based position.
     * @return The date position of a given 1-based position. Null if the position is out of boundaries.
     */
    public Calendar calculateRecurrenceDatePosition(Event master, int position) throws OXException;

    /**
     * Calculates a 1-based recurrence position for a given reccurence date position of a recurring event.
     *
     * @param master The master event containing all necessary information like recurrence rule, star and end date, timezones etc.
     * @param datePosition The date position. Must match a start date.
     * @return The Position of the given datePosition. 1-based. 0 if not found or out of boundaries.
     */
    public int calculateRecurrencePosition(Event master, Calendar datePosition) throws OXException;

    /**
     * Initializes a new iterator for the recurrence set of an event series, iterating over the occurrences of the event series.
     * <p/>
     * Any exception dates (as per {@link Event#getDeleteExceptionDates()}) and overridden instances (as per {@link
     * Event#getChangeExceptionDates()}) are skipped implicitly, so that those occurrences won't be included in the resulting iterator.
     * <p/>
     * Iteration starts with the first occurrence matching the recurrence rule, i.e. the iterator is forwarded to the first occurrence
     * automatically if the series master event's start (or the passed <code>from</code> date) does not fall into the pattern.
     *
     * @param seriesMaster The series master event
     * @param from The left side (inclusive) boundary for the calculation, or <code>null</code> to start with the first occurrence
     * @param until The right side (exclusive) boundary for the calculation, or <code>null</code> for no limitation
     * @return The resulting event occurrence iterator
     */
    RecurrenceIterator<Event> iterateEventOccurrences(Event seriesMaster, Date from, Date until) throws OXException;

    /**
     * Initializes a new recurrence iterator for the recurrence identifiers of a recurrence data object.
     * <p/>
     * Any exception dates (as defined in the passed recurrence data) are ignored implicitly, and won't be contained in the resulting
     * iterator.
     * <p/>
     * Iteration starts with the first occurrence matching the recurrence rule, i.e. the iterator is forwarded to the first occurrence
     * automatically if the recurrence start does not fall into the pattern.
     *
     * @param recurrenceData The recurrence data
     * @return The resulting recurrence id iterator
     */
    RecurrenceIterator<RecurrenceId> iterateRecurrenceIds(RecurrenceData recurrenceData) throws OXException;

    /**
     * Initializes a new recurrence iterator for the recurrence identifiers of a recurrence data object.
     * <p/>
     * Any exception dates (as defined in the passed recurrence data) are ignored implicitly, and won't be contained in the resulting
     * iterator.
     * <p/>
     * Iteration starts with the first occurrence matching the recurrence rule, i.e. the iterator is forwarded to the first
     * occurrence automatically if the recurrence start (or the passed <code>from</code> date) does not fall into the pattern.
     *
     * @param recurrenceData The recurrence data
     * @param from The left side (inclusive) boundary for the calculation, or <code>null</code> to start with the first occurrence
     * @param until The right side (exclusive) boundary for the calculation, or <code>null</code> for no limitation
     * @return The resulting event occurrence iterator
     */
    RecurrenceIterator<RecurrenceId> iterateRecurrenceIds(RecurrenceData recurrenceData, Date from, Date until) throws OXException;

    /**
     * Initializes a new recurrence iterator for the recurrence identifiers of a recurrence data object.
     * <p/>
     * Any exception dates (as defined in the passed recurrence data) are ignored implicitly, and won't be contained in the resulting
     * iterator. Iteration starts with the first occurrence matching the recurrence rule, i.e. the iterator is forwarded to the first
     * occurrence automatically if the recurrence start does not fall into the pattern.
     *
     * @param recurrenceData The recurrence data
     * @param startPosition The 1-based position of the occurrence in the recurrence set to start with, or <code>null</code> to start with
     *            the first occurrence
     * @param limit The maximum number of calculated occurrences, or <code>null</code> for no limitation
     * @return The resulting recurrence iterator
     */
    RecurrenceIterator<RecurrenceId> iterateRecurrenceIds(RecurrenceData recurrenceData, Integer startPosition, Integer limit) throws OXException;

    /**
     * Checks the recurrence data for validity.
     *
     * @param recurrenceData The recurrence data to check
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RRULE}
     */
    void validate(RecurrenceData recurrenceData) throws OXException;

    /**
     * Gets a value indicating whether a specific recurrence rule ends on a specific date or after a number of occurrences or not. I.e.,
     * whether the rule contains a limiting <code>UNTIL</code> or <code>COUNT</code> part or not.
     *
     * @param recurrenceRule The recurrence rule to check
     * @return <code>true</code> if the recurrence rule is not limited, <code>false</code>, otherwise
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RRULE}
     */
    boolean isUnlimited(String recurrenceRule) throws OXException;

    /**
     * Calculates the last occurrence of a recurring event series represented by the supplied recurrence data.
     *
     * @param recurrenceData The recurrence data to get the last occurrence for
     * @return The recurrence identifier of the last occurrence, <code>null</code> for a never ending series, or a synthetic recurrence
     *         identifier for the supplied series start date
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RRULE}
     */
    RecurrenceId getLastOccurrence(RecurrenceData recurrenceData) throws OXException;

}
