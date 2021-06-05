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

package com.openexchange.chronos.storage;

import java.util.List;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.Transp;
import com.openexchange.chronos.service.SearchFilter;
import com.openexchange.chronos.service.SearchOptions;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.exception.OXException;
import com.openexchange.search.SearchTerm;

/**
 * {@link EventStorage}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public interface EventStorage {

    /**
     * Generates the next unique identifier for inserting new event data.
     * <p/>
     * <b>Note:</b> This method should only be called within an active transaction, i.e. if the storage has been initialized using
     * {@link DBTransactionPolicy#NO_TRANSACTIONS} in favor of an externally controlled transaction.
     *
     * @return The next unique event identifier
     */
    String nextId() throws OXException;

    /**
     * Gets the number of events in the storage, which includes the sum of all non-recurring events, the series master events, and the
     * overridden exceptional occurrences from event series.
     *
     * @return The number of events in the storage
     */
    long countEvents() throws OXException;

    /**
     * Counts the number of events matching specific search criteria.
     *
     * @param searchTerm The search term to use
     * @return The number of matching events
     */
    long countEvents(SearchTerm<?> searchTerm) throws OXException;

    /**
     * Counts the number of <i>tombstone</i> events matching specific search criteria.
     *
     * @param searchTerm The search term to use
     * @return The number of matching events
     */
    long countEventTombstones(SearchTerm<?> searchTerm) throws OXException;

    /**
     * Loads a specific event.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventData}.
     *
     * @param eventId The identifier of the event to load
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The event, or <code>null</code> if not found
     */
    Event loadEvent(String eventId, EventField[] fields) throws OXException;

    /**
     * Loads multiple events.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventData}.
     *
     * @param eventIds The identifiers of the events to load
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The found events, or an empty list if none were found
     */
    List<Event> loadEvents(List<String> eventIds, EventField[] fields) throws OXException;

    /**
     * Loads a specific exception from a recurring event series.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventData}.
     *
     * @param seriesId The identifier of the event series to load
     * @param recurrenceId The recurrence identifier of the exception to load
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The event exception
     */
    Event loadException(String seriesId, RecurrenceId recurrenceId, EventField[] fields) throws OXException;

    /**
     * Loads all overridden occurrences (<i>change exceptions</i>) from a recurring event series.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventData}.
     *
     * @param seriesId The identifier of the event series to load
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The event exceptions, or an empty list if there are none
     */
    List<Event> loadExceptions(String seriesId, EventField[] fields) throws OXException;

    /**
     * Searches for events.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventData}.
     *
     * @param searchTerm The search term to use
     * @param searchOptions The search options to apply, or <code>null</code> if not specified
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The found events
     */
    List<Event> searchEvents(SearchTerm<?> searchTerm, SearchOptions searchOptions, EventField[] fields) throws OXException;

    /**
     * Searches for events.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventData}.
     *
     * @param searchTerm The search term to use
     * @param filters A list of additional filters to be applied on the search, or <code>null</code> if not specified
     * @param searchOptions The search options to apply, or <code>null</code> if not specified
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The found events
     */
    List<Event> searchEvents(SearchTerm<?> searchTerm, List<SearchFilter> filters, SearchOptions searchOptions, EventField[] fields) throws OXException;

    /**
     * Searches for previously deleted events in the stored <i>tombstone</i> records.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventTombstoneData}.
     *
     * @param searchTerm The search term to use
     * @param searchOptions The search options to apply, or <code>null</code> if not specified
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The found events
     */
    List<Event> searchEventTombstones(SearchTerm<?> searchTerm, SearchOptions searchOptions, EventField[] fields) throws OXException;

    /**
     * Searches for events of one or more attendees that overlap a specific timerange.
     * <p/>
     * <b>Note:</b> Only the fields from the <i>event</i> storage are loaded, so any auxiliary data (attendees, alarms, attachments)
     * needs to be retrieved afterwards explicitly from the corresponding storages, e.g. using
     * {@link CalendarStorageUtilities#loadAdditionalEventData}.
     *
     * @param attendees The attendees to restrict the results to
     * @param includeTransparent <code>true</code> to also include events marks as {@link Transp#TRANSPARENT}, <code>false</code>, otherwise
     * @param searchOptions The search options to apply (containing the start- and end of the queried range)
     * @param fields The event fields to retrieve from the storage, or <code>null</code> to query all available data
     * @return The found events
     */
    List<Event> searchOverlappingEvents(List<Attendee> attendees, boolean includeTransparent, SearchOptions searchOptions, EventField[] fields) throws OXException;

    /**
     * Inserts a new event into the database.
     *
     * @param event The event to insert
     */
    void insertEvent(Event event) throws OXException;

    /**
     * Inserts multiple new events into the database.
     *
     * @param events The events to insert
     */
    void insertEvents(List<Event> events) throws OXException;

    /**
     * Updates an existing event.
     *
     * @param event The event to update
     */
    void updateEvent(Event event) throws OXException;

    /**
     * Deletes an existing event.
     *
     * @param eventId The identifier of the event to delete
     */
    void deleteEvent(String eventId) throws OXException;

    /**
     * Deletes multiple existing events.
     *
     * @param eventIds The identifiers of the events to delete
     */
    void deleteEvents(List<String> eventIds) throws OXException;

    /**
     * Deletes all existing events, including any <i>tombstone</i> records.
     *
     * @return <code>true</code> if something was actually deleted, <code>false</code>, otherwise
     */
    boolean deleteAllEvents() throws OXException;

    /**
     * Inserts a new (or overwrites a previously existing) <i>tombstone</i> record for a specific event into the database.
     *
     * @param event The event to insert the tombstone for
     */
    void insertEventTombstone(Event event) throws OXException;

    /**
     * Inserts new (or overwrites previously existing) <i>tombstone</i> records for multiple events into the database.
     *
     * @param events The events to insert the tombstones for
     */
    void insertEventTombstones(List<Event> events) throws OXException;

}
