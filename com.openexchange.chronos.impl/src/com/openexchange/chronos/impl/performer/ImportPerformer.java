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

import static com.openexchange.chronos.common.CalendarUtils.getEventID;
import static com.openexchange.chronos.common.CalendarUtils.getEventsByUID;
import static com.openexchange.chronos.common.CalendarUtils.sortSeriesMasterFirst;
import static com.openexchange.chronos.impl.Utils.getCalendarUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.common.mapping.DefaultEventUpdate;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.ical.ImportedComponent;
import com.openexchange.chronos.impl.CalendarFolder;
import com.openexchange.chronos.impl.InternalCalendarResult;
import com.openexchange.chronos.impl.InternalCalendarStorageOperation;
import com.openexchange.chronos.impl.InternalImportResult;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.CreateResult;
import com.openexchange.chronos.service.EventID;
import com.openexchange.chronos.service.UIDConflictStrategy;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.exception.OXException;

/**
 * {@link ImportPerformer}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.0
 */
public class ImportPerformer {

    private final CalendarSession session;
    private final CalendarFolder folder;
    private final int calendarUserId;

    /**
     * Initializes a new {@link ImportPerformer}.
     *
     * @param session The calendar session
     * @param folder The calendar folder representing the current view on the events
     */
    public ImportPerformer(CalendarSession session, CalendarFolder folder) throws OXException {
        super();
        this.session = session;
        this.folder = folder;
        this.calendarUserId = getCalendarUser(session, folder).getEntity();
    }
    
    /**
     * Performs the import of one or more events.
     * <p/>
     * For each calendar object resource, a separate calendar storage operation is used implicitly.
     *
     * @param events The events to import
     * @return The import result
     * @throws OXException In case of error
     */
    public List<InternalImportResult> perform(List<Event> events) {
        if (null == events || events.isEmpty()) {
            return Collections.emptyList();
        }
        /*
         * import events (and possible associated overridden instances) grouped by UID event groups
         */
        List<InternalImportResult> results = new ArrayList<InternalImportResult>(events.size());
        for (List<Event> eventGroup : getEventsByUID(events, true).values()) {
            results.addAll(importEventGroup(sortSeriesMasterFirst(eventGroup)));
        }
        return results;
    }
    
    private List<InternalImportResult> importEventGroup(List<Event> eventGroup) {
        if (null == eventGroup || eventGroup.isEmpty()) {
            return Collections.emptyList();
        }
        /*
         * import events for a single calendar object resource within a dedicated storage operation
         */
        try {            
            return new InternalCalendarStorageOperation<List<InternalImportResult>>(session) {

                @Override
                protected List<InternalImportResult> execute(CalendarSession session, CalendarStorage storage) throws OXException {
                    return importEventGroup(storage, eventGroup);
                }
            }.executeUpdate();            
        } catch (OXException e) {
            /*
             * return appropriate error result for first event of group when import fails
             */
            return Collections.singletonList(new InternalImportResult(new InternalCalendarResult(session, calendarUserId, folder), 
                extractIndex(eventGroup.get(0)), extractWarnings(eventGroup.get(0)), e));
        }        
    }
    
    List<InternalImportResult> importEventGroup(CalendarStorage storage, List<Event> eventGroup) throws OXException {
        if (null == eventGroup || eventGroup.isEmpty()) {
            return Collections.emptyList();
        }
        /*
         * set UIDConflict strategy. Use THROW as default
         */
        UIDConflictStrategy strategy = session.get(CalendarParameters.UID_CONFLICT_STRATEGY, UIDConflictStrategy.class, UIDConflictStrategy.THROW);
        /*
         * import events (and possible associated overridden instances) grouped by UID event groups
         */
        List<InternalImportResult> results = new ArrayList<InternalImportResult>(eventGroup.size());
        /*
         * (re-) assign new UID to imported event if required
         */
        if (UIDConflictStrategy.REASSIGN.equals(strategy)) {
            results.addAll(handleUIDConflict(storage, strategy, eventGroup));
            return results;
        }

        /*
         * create first event (master or non-recurring)
         *
         * It is NOT possible to add event with another internal organizer
         */
        InternalImportResult result;
        result = createEvent(storage, eventGroup.get(0));

        /*
         * Check if UID Conflict needs to be handled
         */
        OXException e = result.getImportResult().getError();
        if (null != e && CalendarExceptionCodes.UID_CONFLICT.equals(e)) {
            if (false == UIDConflictStrategy.THROW.equals(strategy) && false == UIDConflictStrategy.REASSIGN.equals(strategy)) {
                results.addAll(handleUIDConflict(storage, strategy, eventGroup));
                return results;
            }
            throw e;
        }

        results.add(result);
        /*
         * create further events as change exceptions
         */
        addEventExceptions(storage, eventGroup, results, result);
        return results;
    }    

    /**
     * Resolves UID conflicts based on the strategy
     *
     * @param strategy The strategy
     * @param eventGroup The events with UID conflicts sorted by master first
     * @return The imported results
     * @throws OXException Various
     */
    private List<InternalImportResult> handleUIDConflict(CalendarStorage storage, UIDConflictStrategy strategy, List<Event> eventGroup) throws OXException {
        List<InternalImportResult> results = new LinkedList<InternalImportResult>();
        Event masterEvent = eventGroup.get(0);
        switch (strategy) {
            case REASSIGN: {
                String uid = UUID.randomUUID().toString();
                eventGroup.forEach(e -> e.setUid(uid));
                InternalImportResult result = createEvent(storage, masterEvent);
                results.add(result);
                if (isSuccess(result) && 1 < eventGroup.size()) {
                    // Event was created, add exceptions
                    addEventExceptions(storage, eventGroup, results, result);
                } // else; Failed to create event, return failure
                return results;
            }

            case UPDATE: {
                String eventId = session.getCalendarService().getUtilities().resolveByUID(session, masterEvent.getUid());
                Event loadEvent = storage.getEventStorage().loadEvent(eventId, null);
                loadEvent = storage.getUtilities().loadAdditionalEventData(session.getUserId(), loadEvent, null);
                if (null == loadEvent || new DefaultEventUpdate(loadEvent, masterEvent, false, EventField.LAST_MODIFIED, EventField.TIMESTAMP).getUpdatedFields().isEmpty()) {
                    // Nothing to update
                    return null;
                }
                // In case an event is NOT in the default calendar, update call will fail (if organizer is internal ...)
                UpdatePerformer u = new UpdatePerformer(storage, session, folder);

                long clientTimestamp = System.currentTimeMillis();
                InternalCalendarResult calendarResult = u.perform(eventId, loadEvent.getRecurrenceId(), masterEvent, clientTimestamp);
                InternalImportResult result = new InternalImportResult(calendarResult, getEventID(masterEvent), extractIndex(masterEvent), extractWarnings(masterEvent));
                results.add(result);

                if (isSuccess(result) && 1 < eventGroup.size()) {
                    ListIterator<Event> iterator = eventGroup.listIterator(1);
                    do {
                        Event event = iterator.next();
                        // Update event. UpdatePerformer will create event exceptions on its own, if necessary
                        calendarResult = u.perform(loadEvent.getId(), event.getRecurrenceId(), event, clientTimestamp);
                        InternalImportResult internalImportResult = new InternalImportResult(calendarResult, getEventID(event), extractIndex(event), extractWarnings(event));
                        results.add(internalImportResult);
                    } while (iterator.hasNext());
                }
                return results;
            }

            case UPDATE_OR_REASSIGN: {
                List<InternalImportResult> list;
                try {
                    list = handleUIDConflict(storage, UIDConflictStrategy.UPDATE, eventGroup);
                } catch (OXException e) {
                    org.slf4j.LoggerFactory.getLogger(ImportPerformer.class).warn("Could not update all events. Try to reassign event UID.", e);
                    list = Collections.emptyList();
                }
                if (list.isEmpty() || !isSuccess(list.get(0))) {
                    // Updated failed, try reassign
                    return handleUIDConflict(storage, UIDConflictStrategy.REASSIGN, eventGroup);
                }
                return list;
            }
            default:
                throw CalendarExceptionCodes.UNEXPECTED_ERROR.create("Unkown UIDConflictStrategy.");
        }
    }

    private boolean isSuccess(InternalImportResult result) {
        return null == result.getImportResult().getError();
    }

    /**
     * Add {@link InternalImportResult} of series exceptions to the results
     *
     * @param events The events with the same UID sorted by master first
     * @param results The results to return by the import action
     * @param result The {@link InternalImportResult} of the master event
     */
    private void addEventExceptions(CalendarStorage storage, List<Event> events, List<InternalImportResult> results, InternalImportResult result) throws OXException {
        if (1 < events.size()) {
            EventID masterEventID = result.getImportResult().getId();
            long clientTimestamp = result.getImportResult().getTimestamp();
            for (int i = 1; i < events.size(); i++) {
                result = createEventException(storage, masterEventID, events.get(i), clientTimestamp);
                results.add(result);
                clientTimestamp = result.getImportResult().getTimestamp();
            }
        }
    }

    private InternalImportResult createEvent(CalendarStorage storage, Event importedEvent) throws OXException {
        List<OXException> warnings = new ArrayList<OXException>();
        warnings.addAll(extractWarnings(importedEvent));
        InternalCalendarResult calendarResult = new CreatePerformer(storage, session, folder).perform(importedEvent);
        warnings.addAll(extractWarnings(storage));
        Event createdEvent = getFirstCreatedEvent(calendarResult);
        if (null == createdEvent) {
            throw CalendarExceptionCodes.UNEXPECTED_ERROR.create("No event created for \"" + importedEvent + "\"");
        }
        return new InternalImportResult(calendarResult, getEventID(createdEvent), extractIndex(importedEvent), warnings);
    }

    private InternalImportResult createEventException(CalendarStorage storage, EventID masterEventID, Event importedException, long clientTimestamp) throws OXException {
        if (null == masterEventID) {
            throw CalendarExceptionCodes.UNEXPECTED_ERROR.create("Cannot create exception for  \"" + importedException + "\" due to missing master event.");
        }
        List<OXException> warnings = new ArrayList<OXException>();
        warnings.addAll(extractWarnings(importedException));
        InternalCalendarResult calendarResult = new UpdatePerformer(storage, session, folder).perform(masterEventID.getObjectID(), null, importedException, clientTimestamp);
        warnings.addAll(extractWarnings(storage));
        Event createdEvent = getFirstCreatedEvent(calendarResult);
        if (null == createdEvent) {
            throw CalendarExceptionCodes.UNEXPECTED_ERROR.create("No event created for \"" + importedException + "\"");
        }
        return new InternalImportResult(calendarResult, getEventID(createdEvent), extractIndex(importedException), warnings);
    }

    private static List<OXException> extractWarnings(Event importedEvent) {
        if (ImportedComponent.class.isInstance(importedEvent)) {
            List<OXException> warnings = ((ImportedComponent) importedEvent).getWarnings();
            if (null != warnings) {
                return warnings;
            }
        }
        return Collections.emptyList();
    }

    private static List<OXException> extractWarnings(CalendarStorage storage) {
        Map<String, List<OXException>> warningsPerEventId = storage.getAndFlushWarnings();
        if (null == warningsPerEventId || warningsPerEventId.isEmpty()) {
            return Collections.emptyList();
        }
        List<OXException> warnings = new ArrayList<OXException>();
        for (List<OXException> value : warningsPerEventId.values()) {
            for (OXException e : value) {
                if (false == CalendarExceptionCodes.IGNORED_INVALID_DATA.equals(e)) {
                    warnings.addAll(value);
                }
            }
        }
        return warnings;
    }

    static int extractIndex(Event importedEvent) {
        if (ImportedComponent.class.isInstance(importedEvent)) {
            return ((ImportedComponent) importedEvent).getIndex();
        }
        return 0;
    }

    private static Event getFirstCreatedEvent(InternalCalendarResult createResult) {
        if (null != createResult) {
            List<CreateResult> creations = createResult.getUserizedResult().getCreations();
            if (null != creations && 0 < creations.size()) {
                return creations.get(0).getCreatedEvent();
            }
        }
        return null;
    }

}
