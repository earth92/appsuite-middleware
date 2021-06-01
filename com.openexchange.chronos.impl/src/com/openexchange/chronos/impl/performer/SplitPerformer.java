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

import static com.openexchange.chronos.common.CalendarUtils.compare;
import static com.openexchange.chronos.common.CalendarUtils.initRecurrenceRule;
import static com.openexchange.chronos.common.CalendarUtils.isFloating;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesMaster;
import static com.openexchange.chronos.common.CalendarUtils.splitExceptionDates;
import static com.openexchange.chronos.impl.Check.requireUpToDateTimestamp;
import static com.openexchange.chronos.impl.Utils.injectRecurrenceData;
import static com.openexchange.java.Autoboxing.i;
import static com.openexchange.tools.arrays.Collections.isNullOrEmpty;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.UUID;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Duration;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.RelatedTo;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DefaultCalendarObjectResource;
import com.openexchange.chronos.common.DefaultRecurrenceData;
import com.openexchange.chronos.common.mapping.DefaultEventUpdate;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.impl.CalendarFolder;
import com.openexchange.chronos.impl.Check;
import com.openexchange.chronos.impl.Consistency;
import com.openexchange.chronos.impl.InternalCalendarResult;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.RecurrenceIterator;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.java.util.TimeZones;

/**
 * {@link SplitPerformer}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class SplitPerformer extends AbstractUpdatePerformer {

    /**
     * Initializes a new {@link SplitPerformer}.
     *
     * @param storage The underlying calendar storage
     * @param session The calendar session
     * @param folder The calendar folder representing the current view on the events
     */
    public SplitPerformer(CalendarStorage storage, CalendarSession session, CalendarFolder folder) throws OXException {
        super(storage, session, folder);
    }

    /**
     * Initializes a new {@link SplitPerformer}, taking over the settings from another update performer.
     *
     * @param updatePerformer The update performer to take over the settings from
     */
    public SplitPerformer(AbstractUpdatePerformer updatePerformer) {
        super(updatePerformer);
    }

    /**
     * Performs the split operation.
     *
     * @param objectId The identifier of the event to split
     * @param splitPoint The (minimum inclusive) date or date-time where the split is to occur
     * @param uid A new unique identifier to assign to the new part of the series, or <code>null</code> if not set
     * @param clientTimestamp The client timestamp to catch concurrent modifications
     * @return The split result
     */
    public InternalCalendarResult perform(String objectId, DateTime splitPoint, String uid, long clientTimestamp) throws OXException {
        /*
         * load original event data & check permissions
         */
        Event originalEvent = requireUpToDateTimestamp(loadEventData(objectId), clientTimestamp);
        if (false == isSeriesMaster(originalEvent)) {
            throw CalendarExceptionCodes.INVALID_SPLIT.create(originalEvent.getId(), splitPoint);
        }
        Check.eventIsInFolder(originalEvent, folder);
        requireWritePermissions(originalEvent);
        /*
         * perform the split, track scheduling messages & return result
         */
        Entry<CalendarObjectResource, CalendarObjectResource> splitResult = split(originalEvent, splitPoint, uid);
        if (null != splitResult.getKey()) {
            schedulingHelper.trackCreation(splitResult.getKey());
        }
        DefaultEventUpdate eventUpdate = new DefaultEventUpdate(originalEvent, splitResult.getValue().getSeriesMaster());
        schedulingHelper.trackUpdate(splitResult.getValue(), eventUpdate);
        return resultTracker.getResult();
    }
    
    /**
     * Performs the split operation.
     * <p/>
     * Any event creations and updates are tracked accordingly in the underlying result tracker, but no
     * scheduling messages or notifications are prepared yet.
     *
     * @param originalEvent The original series master event to split
     * @param splitPoint The (minimum inclusive) date or date-time where the split is to occur
     * @param uid A new unique identifier to assign to the new part of the series, or <code>null</code> if not set
     * @return A map entry, where the key holds the (newly created) <i>detached</i> resource prior the split point (or <code>null</code>
     *         if no split took place), and the value the updated resource after it
     */
    public Entry<CalendarObjectResource, CalendarObjectResource> split(Event originalEvent, DateTime splitPoint, String uid) throws OXException {
        /*
         * check the supplied split point for validity & derive next recurrence
         */
        if (splitPoint.before(originalEvent.getStartDate())) {
            throw CalendarExceptionCodes.INVALID_SPLIT.create(originalEvent.getId(), splitPoint);
        }
        TimeZone timeZone = isFloating(originalEvent) ? TimeZones.UTC : originalEvent.getStartDate().getTimeZone();
        DefaultRecurrenceData originalRecurrenceData = new DefaultRecurrenceData(originalEvent.getRecurrenceRule(), originalEvent.getStartDate(), null);
        RecurrenceIterator<RecurrenceId> iterator = session.getRecurrenceService().iterateRecurrenceIds(originalRecurrenceData, new Date(splitPoint.getTimestamp()), null);
        if (false == iterator.hasNext()) {
            throw CalendarExceptionCodes.INVALID_SPLIT.create(originalEvent.getId(), splitPoint);
        }
        RecurrenceId nextRecurrenceId = iterator.next();
        /*
         * load further data of original series & prepare common related-to value to link the splitted series
         */
        Map<Integer, List<Alarm>> originalAlarmsByUserId = storage.getAlarmStorage().loadAlarms(originalEvent);
        List<Event> originalChangeExceptions = loadExceptionData(originalEvent);
        RelatedTo relatedTo = new RelatedTo("X-CALENDARSERVER-RECURRENCE-SET", UUID.randomUUID().toString());
        /*
         * prepare a new series event representing the 'detached' part prior to the split time, based on the original series master
         */
        Event detachedSeriesMaster = EventMapper.getInstance().copy(originalEvent, null, true, (EventField[]) null);
        detachedSeriesMaster.setId(storage.getEventStorage().nextId());
        detachedSeriesMaster.setSeriesId(detachedSeriesMaster.getId());
        detachedSeriesMaster.setUid(Strings.isNotEmpty(uid) ? Check.uidIsUnique(session, storage, uid) : UUID.randomUUID().toString());
        detachedSeriesMaster.setFilename(null);
        detachedSeriesMaster.setRelatedTo(relatedTo);
        Consistency.setCreated(session, timestamp, detachedSeriesMaster, session.getUserId());
        Consistency.setModified(session, timestamp, detachedSeriesMaster, session.getUserId());
        /*
        * prepare event update for the original series for the part after on or after the split time;
         */
        Event updatedSeriesMaster = EventMapper.getInstance().copy(
            originalEvent, null, EventField.ID, EventField.SERIES_ID, EventField.START_DATE, EventField.END_DATE, EventField.RECURRENCE_RULE);
        updatedSeriesMaster.setRelatedTo(relatedTo);
        Consistency.setModified(session, timestamp, updatedSeriesMaster, session.getUserId());
        /*
         * distribute recurrence dates prior / on or after the split time
         */
        if (false == isNullOrEmpty(originalEvent.getRecurrenceDates())) {
            Entry<SortedSet<RecurrenceId>, SortedSet<RecurrenceId>> splittedRecurrenceDates = splitExceptionDates(originalEvent.getRecurrenceDates(), splitPoint);
            detachedSeriesMaster.setRecurrenceDates(splittedRecurrenceDates.getKey());
            updatedSeriesMaster.setRecurrenceDates(splittedRecurrenceDates.getValue());
        }
        /*
         * distribute delete exception dates prior / on or after the split time
         */
        if (false == isNullOrEmpty(originalEvent.getDeleteExceptionDates())) {
            Entry<SortedSet<RecurrenceId>, SortedSet<RecurrenceId>> splittedExceptionDates = splitExceptionDates(originalEvent.getDeleteExceptionDates(), splitPoint);
            detachedSeriesMaster.setDeleteExceptionDates(splittedExceptionDates.getKey());
            updatedSeriesMaster.setDeleteExceptionDates(splittedExceptionDates.getValue());
        }
        /*
         * distribute change exception dates prior / on or after the split time
         */
        if (false == isNullOrEmpty(originalEvent.getChangeExceptionDates())) {
            Entry<SortedSet<RecurrenceId>, SortedSet<RecurrenceId>> splittedExceptionDates = splitExceptionDates(originalEvent.getChangeExceptionDates(), splitPoint);
            detachedSeriesMaster.setChangeExceptionDates(splittedExceptionDates.getKey());
            updatedSeriesMaster.setChangeExceptionDates(splittedExceptionDates.getValue());
        }
        /*
         * adjust recurrence rule for the detached series to have a fixed UNTIL one second or day prior the split point
         */
        RecurrenceRule detachedRule = initRecurrenceRule(originalEvent.getRecurrenceRule());
        DateTime until = splitPoint.addDuration(splitPoint.isAllDay() ? new Duration(-1, 1, 0) : new Duration(-1, 0, 1));
        detachedRule.setUntil(until);
        detachedSeriesMaster.setRecurrenceRule(detachedRule.toString());
        if (detachedSeriesMaster.getStartDate().after(until) || false == hasFurtherOccurrences(detachedSeriesMaster, null)) {
            /*
             * no occurrences in 'detached' series, so no split is needed
             */
            CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(originalEvent, originalChangeExceptions);
            return new AbstractMap.SimpleEntry<CalendarObjectResource, CalendarObjectResource>(null, updatedResource);
        }
        /*
         * adjust recurrence rule, start- and end-date for the updated event series to begin on or after the split point
         */
        RecurrenceRule updatedRule = initRecurrenceRule(originalEvent.getRecurrenceRule());
        if (null != updatedRule.getCount()) {
            DefaultRecurrenceData detachedRecurrenceData = new DefaultRecurrenceData(detachedSeriesMaster.getRecurrenceRule(), originalEvent.getStartDate(), null);
            for (iterator = session.getRecurrenceService().iterateRecurrenceIds(detachedRecurrenceData); iterator.hasNext(); iterator.next()) {
                ;
            }
            updatedRule.setCount(i(updatedRule.getCount()) - iterator.getPosition());
            updatedSeriesMaster.setRecurrenceRule(updatedRule.toString());
        }
        updatedSeriesMaster.setStartDate(CalendarUtils.calculateStart(originalEvent, nextRecurrenceId));
        updatedSeriesMaster.setEndDate(CalendarUtils.calculateEnd(originalEvent, nextRecurrenceId));
        if (false == hasFurtherOccurrences(updatedSeriesMaster, null)) {
            /*
             * no occurrences in updated series, so no split is needed
             */
            CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(originalEvent, originalChangeExceptions);
            return new AbstractMap.SimpleEntry<CalendarObjectResource, CalendarObjectResource>(null, updatedResource);
        }
        /*
         * insert the new detached series event, taking over any auxiliary data from the original series
         */
        Check.quotaNotExceeded(storage, session);
        storage.getEventStorage().insertEvent(detachedSeriesMaster);
        storage.getAttendeeStorage().insertAttendees(detachedSeriesMaster.getId(), originalEvent.getAttendees());
        storage.getAttachmentStorage().insertAttachments(session.getSession(), folder.getId(), detachedSeriesMaster.getId(), originalEvent.getAttachments());
        Map<Integer, List<Alarm>> newAlarmsByUserId = insertAlarms(detachedSeriesMaster, originalAlarmsByUserId, true);
        storage.getAlarmTriggerStorage().insertTriggers(detachedSeriesMaster, newAlarmsByUserId);
        detachedSeriesMaster = loadEventData(detachedSeriesMaster.getId());
        resultTracker.trackCreation(detachedSeriesMaster);
        /*
         * assign existing change exceptions to new detached event series, if prior split time
         */
        List<Event> detachedChangeExceptions = new ArrayList<Event>();
        for (Event originalChangeException : originalChangeExceptions) {
            if (0 > compare(originalChangeException.getRecurrenceId().getValue(), splitPoint, timeZone)) {
                Event exceptionUpdate = EventMapper.getInstance().copy(originalChangeException, null, EventField.ID);
                EventMapper.getInstance().copy(detachedSeriesMaster, exceptionUpdate, EventField.SERIES_ID, EventField.UID, EventField.FILENAME, EventField.RELATED_TO);
                Consistency.setModified(session, timestamp, exceptionUpdate, session.getUserId());
                storage.getEventStorage().updateEvent(exceptionUpdate);
                Event updatedChangeException = loadEventData(originalChangeException.getId());
                resultTracker.trackUpdate(originalChangeException, updatedChangeException);
                detachedChangeExceptions.add(updatedChangeException);
            }
        }
        CalendarObjectResource detachedResource = new DefaultCalendarObjectResource(detachedSeriesMaster, detachedChangeExceptions);
        /*
         * update the original event series; also decorate original change exceptions on or after the split with the related-to marker
         */
        storage.getEventStorage().updateEvent(updatedSeriesMaster);
        updatedSeriesMaster = loadEventData(originalEvent.getId());
        resultTracker.trackUpdate(originalEvent, updatedSeriesMaster);
        List<Event> updatedChangeExceptions = new ArrayList<Event>();
        for (Event originalChangeException : originalChangeExceptions) {
            if (0 <= compare(originalChangeException.getRecurrenceId().getValue(), splitPoint, timeZone)) {
                Event exceptionUpdate = EventMapper.getInstance().copy(originalChangeException, null, EventField.ID);
                exceptionUpdate.setRelatedTo(relatedTo);
                // workaround to hide a possibly incorrect recurrence position in passed recurrence id for legacy storage
                // TODO: remove once no longer needed
                injectRecurrenceData(exceptionUpdate, new DefaultRecurrenceData(updatedSeriesMaster.getRecurrenceRule(), updatedSeriesMaster.getStartDate()));
                Consistency.setModified(session, timestamp, exceptionUpdate, session.getUserId());
                storage.getEventStorage().updateEvent(exceptionUpdate);
                Event updatedChangeException = loadEventData(originalChangeException.getId());
                resultTracker.trackUpdate(originalChangeException, updatedChangeException);
                updatedChangeExceptions.add(updatedChangeException);
            }
        }
        CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(updatedSeriesMaster, updatedChangeExceptions);
        return new AbstractMap.SimpleEntry<CalendarObjectResource, CalendarObjectResource>(detachedResource, updatedResource);
    }

}
