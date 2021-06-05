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

import static com.openexchange.chronos.common.CalendarUtils.collectAttendees;
import static com.openexchange.chronos.common.CalendarUtils.contains;
import static com.openexchange.chronos.common.CalendarUtils.extractEMailAddress;
import static com.openexchange.chronos.common.CalendarUtils.find;
import static com.openexchange.chronos.common.CalendarUtils.getConferenceIds;
import static com.openexchange.chronos.common.CalendarUtils.getExceptionDateUpdates;
import static com.openexchange.chronos.common.CalendarUtils.getSimpleAttendeeUpdates;
import static com.openexchange.chronos.common.CalendarUtils.getUpdatedResource;
import static com.openexchange.chronos.common.CalendarUtils.getUserIDs;
import static com.openexchange.chronos.common.CalendarUtils.hasExternalOrganizer;
import static com.openexchange.chronos.common.CalendarUtils.initRecurrenceRule;
import static com.openexchange.chronos.common.CalendarUtils.isAllDay;
import static com.openexchange.chronos.common.CalendarUtils.isExternalUser;
import static com.openexchange.chronos.common.CalendarUtils.isInternal;
import static com.openexchange.chronos.common.CalendarUtils.isOpaqueTransparency;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesException;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesMaster;
import static com.openexchange.chronos.common.CalendarUtils.matches;
import static com.openexchange.chronos.impl.Check.requireUpToDateTimestamp;
import static com.openexchange.chronos.impl.Utils.extractReplies;
import static com.openexchange.chronos.impl.Utils.isSameMailDomain;
import static com.openexchange.tools.arrays.Collections.isNullOrEmpty;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.Attachment;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Conference;
import com.openexchange.chronos.ConferenceField;
import com.openexchange.chronos.DelegatingEvent;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.RecurrenceRange;
import com.openexchange.chronos.RelatedTo;
import com.openexchange.chronos.UnmodifiableEvent;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DefaultCalendarObjectResource;
import com.openexchange.chronos.common.mapping.AttendeeMapper;
import com.openexchange.chronos.common.mapping.ConferenceMapper;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.impl.CalendarFolder;
import com.openexchange.chronos.impl.Check;
import com.openexchange.chronos.impl.InternalCalendarResult;
import com.openexchange.chronos.impl.InternalEventUpdate;
import com.openexchange.chronos.impl.Role;
import com.openexchange.chronos.impl.Utils;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EventUpdate;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.type.PublicType;
import com.openexchange.groupware.tools.mappings.Mapping;
import com.openexchange.groupware.tools.mappings.common.AbstractSimpleCollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.CollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.ItemUpdate;
import com.openexchange.groupware.tools.mappings.common.SimpleCollectionUpdate;
import com.openexchange.tools.arrays.Arrays;

/**
 * {@link UpdatePerformer}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class UpdatePerformer extends AbstractUpdatePerformer {

    /**
     * Initializes a new {@link UpdatePerformer}.
     *
     * @param storage The underlying calendar storage
     * @param session The calendar session
     * @param folder The calendar folder representing the current view on the events
     * @param roles The {@link Role}s a user acts as.
     */
    public UpdatePerformer(CalendarStorage storage, CalendarSession session, CalendarFolder folder, EnumSet<Role> roles) throws OXException {
        super(storage, session, folder, roles);
    }

    /**
     * Initializes a new {@link UpdatePerformer}.
     *
     * @param storage The underlying calendar storage
     * @param session The calendar session
     * @param folder The calendar folder representing the current view on the events
     */
    public UpdatePerformer(CalendarStorage storage, CalendarSession session, CalendarFolder folder) throws OXException {
        super(storage, session, folder);
    }

    /**
     * Initializes a new {@link UpdatePerformer}, taking over the settings from another update performer.
     *
     * @param updatePerformer The update performer to take over the settings from
     */
    protected UpdatePerformer(AbstractUpdatePerformer updatePerformer) {
        super(updatePerformer);
    }

    /**
     * Performs the update operation.
     *
     * @param objectId The identifier of the event to update
     * @param recurrenceId The optional id of the recurrence.
     * @param updatedEventData The updated event data
     * @param clientTimestamp The client timestamp to catch concurrent modifications
     * @return The update result
     */
    public InternalCalendarResult perform(String objectId, RecurrenceId recurrenceId, Event updatedEventData, long clientTimestamp) throws OXException {
        getSelfProtection().checkEvent(updatedEventData);
        /*
         * load original event data & pre-process event update
         */
        Event originalEvent = requireUpToDateTimestamp(loadEventData(objectId), clientTimestamp);
        updatedEventData = restoreInjectedAttendeeDate(originalEvent, updatedEventData);
        /*
         * update event or event occurrence
         */
        if (null == recurrenceId && updatedEventData.containsRecurrenceId()) {
            recurrenceId = updatedEventData.getRecurrenceId();
        }
        InternalUpdateResult result;
        if (null == recurrenceId) {
            result = updateEvent(originalEvent, updatedEventData);
        } else if (isSeriesMaster(originalEvent)) {
            result = updateRecurrence(originalEvent, recurrenceId, updatedEventData);
        } else if (isSeriesException(originalEvent) && recurrenceId.matches(originalEvent.getRecurrenceId())) {
            result = updateEvent(originalEvent, updatedEventData);
        } else {
            throw CalendarExceptionCodes.INVALID_RECURRENCE_ID.create(String.valueOf(recurrenceId), null);
        }
        /*
         * track scheduling-related notifications & return result
         */
        handleScheduling(result.getEventUpdate(), result.getUpdatedEvent(), result.getUpdatedChangeExceptions());
        logPerform(result.getEventUpdate());
        return resultTracker.getResult();
    }

    /**
     * Handles any necessary scheduling after an update has been performed, i.e. tracks suitable scheduling messages and notifications.
     *
     * @param eventUpdate The event update describing the performed changes
     * @param updatedEvent The updated event as persisted in the storage
     * @param updatedChangeExceptions The (implicitly) updated change exceptions as persisted in the storage, or <code>null</code> if not applicable 
     */
    private void handleScheduling(InternalEventUpdate eventUpdate, Event updatedEvent, List<Event> updatedChangeExceptions) throws OXException {
        /*
         * prepare updated resource for scheduling messages and notifications
         */
        if (eventUpdate.isReschedule() && false == hasExternalOrganizer(eventUpdate.getOriginal())) {
            if (isSeriesMaster(eventUpdate.getOriginal())) {
                /*
                 * update of series, determine scheduling operations based on superset of attendees in all instances of the series
                 */
                CalendarObjectResource originalResource = eventUpdate.getOriginalResource();
                CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(updatedEvent, updatedChangeExceptions);
                AbstractSimpleCollectionUpdate<Attendee> collectedAttendeeUpdates = getSimpleAttendeeUpdates(
                    collectAttendees(originalResource, null, (CalendarUserType[]) null), collectAttendees(updatedResource, null, (CalendarUserType[]) null));
                if (false == collectedAttendeeUpdates.getRemovedItems().isEmpty()) {
                    schedulingHelper.trackDeletion(originalResource, null, collectedAttendeeUpdates.getRemovedItems());
                }
                if (false == collectedAttendeeUpdates.getRetainedItems().isEmpty()) {
                    schedulingHelper.trackUpdate(updatedResource, null, eventUpdate, collectedAttendeeUpdates.getRetainedItems());
                }
                if (false == collectedAttendeeUpdates.getAddedItems().isEmpty()) {
                    schedulingHelper.trackCreation(updatedResource, collectedAttendeeUpdates.getAddedItems());
                }
            } else {
                /*
                 * update of change exception or non-recurring, determine scheduling operations based on attendee updates in this event
                 */
                Event seriesMaster = eventUpdate.getOriginalSeriesMaster();
                if (false == eventUpdate.getAttendeeUpdates().getRemovedItems().isEmpty()) {
                    CalendarObjectResource originalResource = new DefaultCalendarObjectResource(eventUpdate.getOriginal());
                    schedulingHelper.trackDeletion(originalResource, seriesMaster, eventUpdate.getAttendeeUpdates().getRemovedItems());
                }
                if (false == eventUpdate.getAttendeeUpdates().getRetainedItems().isEmpty()) {
                    CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(updatedEvent);
                    schedulingHelper.trackUpdate(updatedResource, seriesMaster, eventUpdate, eventUpdate.getAttendeeUpdates().getRetainedItems());
                }
                if (false == eventUpdate.getAttendeeUpdates().getAddedItems().isEmpty()) {
                    schedulingHelper.trackCreation(new DefaultCalendarObjectResource(updatedEvent), eventUpdate.getAttendeeUpdates().getAddedItems());
                }
            }
        } else if (eventUpdate.getAttendeeUpdates().isReply(calendarUser)) {
            /*
             * track reply message from calendar user to organizer
             */
            Attendee userAttendee = find(eventUpdate.getOriginal().getAttendees(), calendarUserId);
            if (isSeriesMaster(eventUpdate.getOriginal())) {
                /*
                 * reply of series, build updated resource from updated master & exceptions 
                 */
                CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(updatedEvent, updatedChangeExceptions);
                schedulingHelper.trackReply(userAttendee, updatedResource, null, eventUpdate);
            } else {
                /*
                 * update of change exception or non-recurring, build updated resource from updated event & supply series master separately
                 */
                CalendarObjectResource updatedResource = new DefaultCalendarObjectResource(updatedEvent);
                schedulingHelper.trackReply(userAttendee, updatedResource, eventUpdate.getOriginalSeriesMaster(), eventUpdate);
            }
        } else {
            /*
             * track deletions for newly created delete exceptions
             */
            List<Event> deletedExceptions = eventUpdate.getDeletedExceptions();
            if (0 < deletedExceptions.size()) {
                schedulingHelper.trackDeletion(new DefaultCalendarObjectResource(deletedExceptions), eventUpdate.getOriginalSeriesMaster(), null);
            }
        }
    }    
    
    /**
     * Updates data of an existing event recurrence and tracks the update in the underlying calendar result. A new change exception event
     * will be created automatically unless it already exists for this event series.
     *
     * @param originalSeriesMaster The original series master event
     * @param recurrenceId The recurrence identifier targeting the event occurrence to update
     * @param updatedEventData The updated event data
     * @param ignoredFields Additional fields to ignore during the update; {@link #SKIPPED_FIELDS} are always skipped
     * @return The processed event update
     */
    protected InternalUpdateResult updateRecurrence(Event originalSeriesMaster, RecurrenceId recurrenceId, Event updatedEventData, EventField... ignoredFields) throws OXException {
        recurrenceId = Check.recurrenceIdExists(session.getRecurrenceService(), originalSeriesMaster, recurrenceId);
        if (contains(originalSeriesMaster.getDeleteExceptionDates(), recurrenceId)) {
            /*
             * cannot update a delete exception
             */
            throw CalendarExceptionCodes.EVENT_RECURRENCE_NOT_FOUND.create(originalSeriesMaster.getSeriesId(), recurrenceId);
        }
        if (null != recurrenceId.getRange()) {
            /*
             * update "this and future" recurrences; first split the series at this recurrence
             */
            Check.recurrenceRangeMatches(recurrenceId, RecurrenceRange.THISANDFUTURE);
            Entry<CalendarObjectResource, CalendarObjectResource> splitResult = new SplitPerformer(this).split(originalSeriesMaster, recurrenceId.getValue(), null);

            /*
             * track scheduling messages and notifications for the newly created, detached series (externals, only)
             */
            CalendarObjectResource detachedSeries = splitResult.getKey();
            if (null != detachedSeries) {
                schedulingHelper.trackCreation(detachedSeries, collectAttendees(detachedSeries, Boolean.FALSE, (CalendarUserType[]) null));
            }
            /*
             * then apply the update for the splitted series master event after rolling back the related-to field, taking over a new recurrence rule as needed
             */
            Event updatedSeriesMaster = splitResult.getValue().getSeriesMaster();
            if (null == updatedSeriesMaster) {
                throw CalendarExceptionCodes.UNEXPECTED_ERROR.create("Unable to track update. Reason: Nothing was changed.");
            }
            Event originalEvent = adjustUpdatedSeriesAfterSplit(originalSeriesMaster, updatedSeriesMaster);
            Event eventUpdate = adjustClientUpdateAfterSplit(originalSeriesMaster, updatedSeriesMaster, updatedEventData);
            return updateEvent(originalEvent, eventUpdate, EventField.ID, EventField.RECURRENCE_ID, EventField.DELETE_EXCEPTION_DATES, EventField.CHANGE_EXCEPTION_DATES);
        } else if (contains(originalSeriesMaster.getChangeExceptionDates(), recurrenceId)) {
            /*
             * update for existing change exception, perform update, touch master & track results
             */
            Check.recurrenceRangeMatches(recurrenceId, null);
            Event originalExceptionEvent = loadExceptionData(originalSeriesMaster, CalendarUtils.find(originalSeriesMaster.getChangeExceptionDates(), recurrenceId));
            InternalUpdateResult result = updateEvent(originalExceptionEvent, updatedEventData, ignoredFields);
            touch(originalSeriesMaster.getSeriesId());
            resultTracker.trackUpdate(originalSeriesMaster, loadEventData(originalSeriesMaster.getId()));
            return result;
        } else {
            /*
             * update for new change exception; prepare & insert a plain exception first, based on the original data from the master
             */
            Map<Integer, List<Alarm>> seriesMasterAlarms = storage.getAlarmStorage().loadAlarms(originalSeriesMaster);
            Event newExceptionEvent = prepareException(originalSeriesMaster, recurrenceId);
            Map<Integer, List<Alarm>> newExceptionAlarms = prepareExceptionAlarms(seriesMasterAlarms);
            Check.quotaNotExceeded(storage, session);
            storage.getEventStorage().insertEvent(newExceptionEvent);
            storage.getAttendeeStorage().insertAttendees(newExceptionEvent.getId(), originalSeriesMaster.getAttendees());
            storage.getAttachmentStorage().insertAttachments(session.getSession(), folder.getId(), newExceptionEvent.getId(), originalSeriesMaster.getAttachments());
            storage.getConferenceStorage().insertConferences(newExceptionEvent.getId(), prepareConferences(originalSeriesMaster.getConferences()));
            insertAlarms(newExceptionEvent, newExceptionAlarms, true);
            newExceptionEvent = loadEventData(newExceptionEvent.getId());
            resultTracker.trackCreation(newExceptionEvent, originalSeriesMaster);
            /*
             * perform the update on the newly created change exception
             * - recurrence rule is forcibly ignored during update to satisfy UsmFailureDuringRecurrenceTest.testShouldFailWhenTryingToMakeAChangeExceptionASeriesButDoesNot()
             * - sequence number is also ignored (since possibly incremented implicitly before) for internal organized events
             */
            InternalUpdateResult result = updateEvent(newExceptionEvent, updatedEventData, hasExternalOrganizer(originalSeriesMaster) ? //
                new EventField[] { EventField.ID, EventField.RECURRENCE_RULE } : //
                new EventField[] { EventField.ID, EventField.RECURRENCE_RULE, EventField.SEQUENCE });
            Event updatedExceptionEvent = result.getUpdatedEvent();
            /*
             * add change exception date to series master & track results
             */
            resultTracker.rememberOriginalEvent(originalSeriesMaster);
            addChangeExceptionDate(originalSeriesMaster, newExceptionEvent.getRecurrenceId(), false);
            Event updatedMasterEvent = loadEventData(originalSeriesMaster.getId());
            resultTracker.trackUpdate(originalSeriesMaster, updatedMasterEvent);
            /*
             * reset alarm triggers for series master event and new change exception & return result
             */
            storage.getAlarmTriggerStorage().deleteTriggers(updatedMasterEvent.getId());
            storage.getAlarmTriggerStorage().insertTriggers(updatedMasterEvent, seriesMasterAlarms);
            storage.getAlarmTriggerStorage().deleteTriggers(updatedExceptionEvent.getId());
            storage.getAlarmTriggerStorage().insertTriggers(updatedExceptionEvent, storage.getAlarmStorage().loadAlarms(updatedExceptionEvent));
            return result;
        }
    }

    /**
     * Updates data of an existing event based on the client supplied event data and tracks the update results accordingly. Permission-
     * and consistency-related checks are performed implicitly, however, no scheduling- or notification messages are tracked.
     *
     * @param originalEvent The original, plain event data
     * @param eventData The updated event data
     * @param ignoredFields Additional fields to ignore during the update; {@link #SKIPPED_FIELDS} are always skipped
     * @return The processed event update
     */
    protected InternalUpdateResult updateEvent(Event originalEvent, Event eventData, EventField... ignoredFields) throws OXException {
        /*
         * check if folder view on event is allowed
         */
        Check.eventIsInFolder(originalEvent, folder);
        /*
         * handle new delete exceptions from the calendar user's point of view beforehand
         */
        if (isSeriesMaster(originalEvent) && eventData.containsDeleteExceptionDates() &&
            false == hasExternalOrganizer(originalEvent) && false == deleteRemovesEvent(originalEvent)) {
            if (updateDeleteExceptions(originalEvent, eventData)) {
                originalEvent = loadEventData(originalEvent.getId());
            }
            /*
             * consider as handled, so ignore delete exception dates later on
             */
            ignoredFields = null != ignoredFields ? Arrays.add(ignoredFields, EventField.DELETE_EXCEPTION_DATES) : new EventField[] { EventField.DELETE_EXCEPTION_DATES };
        }
        /*
         * prepare event update & check conflicts as needed
         */
        boolean assumeExternalOrganizerUpdate = assumeExternalOrganizerUpdate(originalEvent, eventData);
        List<Event> originalChangeExceptions = isSeriesMaster(originalEvent) ? loadExceptionData(originalEvent) : null;
        Event originalSeriesMasterEvent = isSeriesException(originalEvent) ? optEventData(originalEvent.getSeriesId()) : null;
        InternalEventUpdate eventUpdate = new InternalEventUpdate(
            session, folder, originalEvent, originalChangeExceptions, originalSeriesMasterEvent, eventData, timestamp, assumeExternalOrganizerUpdate, Arrays.add(SKIPPED_FIELDS, ignoredFields));
        if (needsConflictCheck(eventUpdate)) {
            Check.noConflicts(storage, session, eventUpdate.getUpdate(), eventUpdate.getAttendeeUpdates().previewChanges());
        }
        /*
         * recursively perform pending deletions of change exceptions if required, checking permissions as needed
         */
        if (0 < eventUpdate.getExceptionUpdates().getRemovedItems().size()) {
            requireWritePermissions(originalEvent, assumeExternalOrganizerUpdate);
            for (Event removedException : eventUpdate.getExceptionUpdates().getRemovedItems()) {
                delete(removedException);
            }
        }
        /*
         * trigger calendar interceptors
         */
        interceptorRegistry.triggerInterceptorsOnBeforeUpdate(eventUpdate.getOriginal(), eventUpdate.getUpdate());
        /*
         * update event data in storage, checking permissions as required
         */
        storeEventUpdate(originalEvent, eventUpdate.getDelta(), eventUpdate.getUpdatedFields(), assumeExternalOrganizerUpdate);
        storeAttendeeUpdates(originalEvent, eventUpdate.getAttendeeUpdates(), assumeExternalOrganizerUpdate);
        storeConferenceUpdates(originalEvent, eventUpdate.getConferenceUpdates(), assumeExternalOrganizerUpdate);
        storeAttachmentUpdates(originalEvent, eventUpdate.getAttachmentUpdates(), assumeExternalOrganizerUpdate);
        /*
         * update passed alarms for calendar user, apply default alarms for newly added internal user attendees
         */
        if (eventData.containsAlarms()) {
            Event updatedEvent = loadEventData(originalEvent.getId());
            List<Alarm> originalAlarms = storage.getAlarmStorage().loadAlarms(originalEvent, calendarUserId);
            if (originalChangeExceptions != null) {

                List<Event> copies = new ArrayList<>(originalChangeExceptions.size());
                for(Event eve: originalChangeExceptions) {
                    copies.add(EventMapper.getInstance().copy(eve, null, EventMapper.getInstance().getAssignedFields(eve)));
                }

                List<Event> exceptionsWithAlarms = storage.getUtilities().loadAdditionalEventData(calendarUserId, copies, null);
                Map<Event, List<Alarm>> alarmsToUpdate = AlarmUpdateProcessor.getUpdatedExceptions(originalAlarms, eventData.getAlarms(), exceptionsWithAlarms);
                for (Entry<Event, List<Alarm>> toUpdate : alarmsToUpdate.entrySet()) {
                    updateAlarms(toUpdate.getKey(), calendarUserId, toUpdate.getKey().getAlarms(), toUpdate.getValue());
                }
            }
            updateAlarms(updatedEvent, calendarUserId, originalAlarms, eventData.getAlarms());
        }
        for (int userId : getUserIDs(eventUpdate.getAttendeeUpdates().getAddedItems())) {
            List<Alarm> defaultAlarm = isAllDay(eventUpdate.getUpdate()) ? session.getConfig().getDefaultAlarmDate(userId) : session.getConfig().getDefaultAlarmDateTime(userId);
            if (null != defaultAlarm) {
                insertAlarms(eventUpdate.getUpdate(), userId, defaultAlarm, true);
            }
        }
        /*
         * recursively perform pending updates of change exceptions if required
         */
        List<Event> updatedChangeExceptions = new ArrayList<Event>();
        for (ItemUpdate<Event, EventField> updatedException : eventUpdate.getExceptionUpdates().getUpdatedItems()) {
            InternalUpdateResult result = updateEvent(updatedException.getOriginal(), updatedException.getUpdate());
            updatedChangeExceptions.add(result.getUpdatedEvent());
        }
        /*
         * track update result & update any stored alarm triggers of all users if required
         */
        Event updatedEvent = loadEventData(originalEvent.getId());
        if (originalEvent.getId().equals(updatedEvent.getId()) && matches(originalEvent.getRecurrenceId(), updatedEvent.getRecurrenceId())) {
            resultTracker.trackUpdate(originalEvent, updatedEvent);
        } else {
            resultTracker.trackDeletion(originalEvent);
            resultTracker.trackCreation(updatedEvent);
        }
        Map<Integer, List<Alarm>> alarms;
        if (eventUpdate.containsAnyChangeOf(new EventField[] { EventField.START_DATE, EventField.END_DATE })) {
            storage.getAlarmTriggerStorage().deleteTriggers(originalEvent.getId());
            alarms = storage.getAlarmStorage().loadAlarms(updatedEvent);
        } else {
            alarms = storage.getAlarmStorage().loadAlarms(updatedEvent);
            storage.getAlarmTriggerStorage().deleteTriggers(originalEvent.getId());
        }
        storage.getAlarmTriggerStorage().insertTriggers(updatedEvent, alarms);
        /*
         * wrap so far results for further processing
         */
        return new InternalUpdateResult(eventUpdate, updatedEvent, updatedChangeExceptions);
    }

    /**
     * Determines if an incoming event update can be treated as initiated by the (external) organizer of a scheduling object resource or
     * not. If yes, certain checks may be skipped, e.g. the check against allowed attendee changes.
     * <p/>
     * An update is considered as <i>organizer-update</i> under certain circumstances, particularly:
     * <ul>
     * <li>the event has an <i>external</i> organizer</li>
     * <li>the organizer matches in the original and in the updated event</li>
     * <li>the unique identifier matches in the original and in the updated event</li>
     * <li>the updated event's sequence number is not smaller than the sequence number of the original event</li>
     * </ul>
     *
     * @param originalEvent The original event
     * @param updatedEvent The updated event
     * @return <code>true</code> if an external organizer update can be assumed, <code>false</code>, otherwise
     * @see <a href="https://bugs.open-xchange.com/show_bug.cgi?id=29566#c12">Bug 29566</a>,
     *      <a href="https://bugs.open-xchange.com/show_bug.cgi?id=23181"/>Bug 23181</a>
     */
    private boolean assumeExternalOrganizerUpdate(Event originalEvent, Event updatedEvent) {
        if (hasExternalOrganizer(originalEvent) && matches(originalEvent.getOrganizer(), updatedEvent.getOrganizer()) &&
            Objects.equals(originalEvent.getUid(), updatedEvent.getUid()) && updatedEvent.getSequence() >= originalEvent.getSequence()) {
            return true;
        }
        return false;
    }

    /**
     * Gets a value indicating whether conflict checks should take place along with the update or not.
     *
     * @param eventUpdate The event update to evaluate
     * @param attendeeHelper The attendee helper for the update
     * @return <code>true</code> if conflict checks should take place, <code>false</code>, otherwise
     */
    private boolean needsConflictCheck(EventUpdate eventUpdate) throws OXException {
        if (Utils.coversDifferentTimePeriod(eventUpdate.getOriginal(), eventUpdate.getUpdate())) {
            /*
             * (re-)check conflicts if event appears in a different time period
             */
            return true;
        }
        if (eventUpdate.getUpdatedFields().contains(EventField.TRANSP) && false == isOpaqueTransparency(eventUpdate.getOriginal())) {
            /*
             * (re-)check conflicts if transparency is now opaque
             */
            return true;
        }
        if (0 < eventUpdate.getAttendeeUpdates().getAddedItems().size()) {
            /*
             * (re-)check conflicts if there are new attendees
             */
            return true;
        }
        return false;
    }

    private boolean updateDeleteExceptions(Event originalEvent, Event updatedEvent) throws OXException {
        if (isSeriesMaster(originalEvent) && false == isNullOrEmpty(updatedEvent.getDeleteExceptionDates())) {
            if (deleteRemovesEvent(originalEvent)) {
                /*
                 * "real" delete exceptions for all attendees, take over as-is during normal update routine
                 */
                return false;
            }
            /*
             * check for newly indicated delete exceptions, from the calendar user's point of view
             */
            Attendee userAttendee = find(originalEvent.getAttendees(), calendarUserId);
            SortedSet<RecurrenceId> originalDeleteExceptionDates = Utils.applyExceptionDates(storage, originalEvent, calendarUserId).getDeleteExceptionDates();
            SimpleCollectionUpdate<RecurrenceId> exceptionDateUpdates = getExceptionDateUpdates(originalDeleteExceptionDates, updatedEvent.getDeleteExceptionDates());
            if (0 < exceptionDateUpdates.getRemovedItems().size() || null == userAttendee) {
                throw CalendarExceptionCodes.FORBIDDEN_CHANGE.create(originalEvent.getId(), EventField.DELETE_EXCEPTION_DATES);
            }
            if (0 < exceptionDateUpdates.getAddedItems().size()) {
                List<EventUpdate> attendeeEventUpdates = new ArrayList<EventUpdate>();
                for (RecurrenceId newDeleteException : exceptionDateUpdates.getAddedItems()) {
                    RecurrenceId recurrenceId = Check.recurrenceIdExists(session.getRecurrenceService(), originalEvent, newDeleteException);
                    if (contains(originalEvent.getChangeExceptionDates(), recurrenceId)) {
                        /*
                         * remove attendee from existing change exception
                         */
                        Event originalChangeException = loadExceptionData(originalEvent, recurrenceId);
                        Attendee originalAttendee = find(originalChangeException.getAttendees(), calendarUserId);
                        if (null != originalAttendee) {
                            attendeeEventUpdates.addAll(delete(originalChangeException, originalAttendee));
                        }
                    } else {
                        /*
                         * creation of new delete exception for this attendee
                         */
                        attendeeEventUpdates.addAll(deleteFromRecurrence(originalEvent, recurrenceId, userAttendee));
                    }
                }
                /*
                 * track reply scheduling messages as needed
                 */
                List<EventUpdate> attendeeReplies = extractReplies(attendeeEventUpdates, calendarUser);
                if (0 < attendeeReplies.size()) {
                    schedulingHelper.trackReply(userAttendee, getUpdatedResource(attendeeReplies), attendeeReplies);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Persists event data updates in the underlying calendar storage, verifying that the current user has appropriate write permissions
     * in order to do so.
     *
     * @param originalEvent The event being updated
     * @param deltaEvent The delta event providing the updated event data
     * @param updatedFields The actually updated fields
     * @param assumeExternalOrganizerUpdate <code>true</code> if an external organizer update can be assumed, <code>false</code>, otherwise
     * @return <code>true</code> if there were changes, <code>false</code>, otherwise
     */
    protected boolean storeEventUpdate(Event originalEvent, Event deltaEvent, Set<EventField> updatedFields, boolean assumeExternalOrganizerUpdate) throws OXException {
        HashSet<EventField> updatedEventFields = new HashSet<EventField>(updatedFields);
        updatedEventFields.removeAll(java.util.Arrays.asList(EventField.ATTACHMENTS, EventField.ALARMS, EventField.ATTENDEES));
        if (updatedEventFields.isEmpty()) {
            return false;
        }
        boolean realChange = false;
        for (EventField updatedField : updatedEventFields) {
            if (Arrays.contains(SKIPPED_FIELDS, updatedField)) {
                continue;
            }
            realChange = true;
            break;
        }
        if (realChange) {
            requireWritePermissions(originalEvent, assumeExternalOrganizerUpdate);
        } else {
            requireWritePermissions(originalEvent, session.getEntityResolver().prepareUserAttendee(calendarUserId), assumeExternalOrganizerUpdate);
        }
        storage.getEventStorage().updateEvent(deltaEvent);
        return true;
    }

    /**
     * Persists attendee updates in the underlying calendar storage, verifying that the current user has appropriate write permissions
     * in order to do so.
     *
     * @param originalEvent The event the attendees are updated for
     * @param attendeeUpdates The attendee updates to persist
     * @param assumeExternalOrganizerUpdate <code>true</code> if an external organizer update can be assumed, <code>false</code>, otherwise
     * @return <code>true</code> if there were changes, <code>false</code>, otherwise
     */
    protected boolean storeAttendeeUpdates(Event originalEvent, CollectionUpdate<Attendee, AttendeeField> attendeeUpdates, boolean assumeExternalOrganizerUpdate) throws OXException {
        if (attendeeUpdates.isEmpty()) {
            return false;
        }
        /*
         * perform attendee deletions
         */
        List<Attendee> removedItems = attendeeUpdates.getRemovedItems();
        if (0 < removedItems.size()) {
            requireWritePermissions(originalEvent, removedItems, assumeExternalOrganizerUpdate);
            storage.getEventStorage().insertEventTombstone(storage.getUtilities().getTombstone(originalEvent, timestamp, calendarUser));
            storage.getAttendeeStorage().insertAttendeeTombstones(originalEvent.getId(), storage.getUtilities().getTombstones(removedItems));
            storage.getAttendeeStorage().deleteAttendees(originalEvent.getId(), removedItems);
            storage.getAlarmStorage().deleteAlarms(originalEvent.getId(), getUserIDs(removedItems));
        }
        /*
         * perform attendee updates, ensure correct timestamp for each attendee
         */
        List<? extends ItemUpdate<Attendee, AttendeeField>> updatedItems = attendeeUpdates.getUpdatedItems();
        if (0 < updatedItems.size()) {
            List<Attendee> attendeesToUpdate = new ArrayList<Attendee>(updatedItems.size());
            for (ItemUpdate<Attendee, AttendeeField> attendeeToUpdate : updatedItems) {
                requireWritePermissions(originalEvent, attendeeToUpdate, assumeExternalOrganizerUpdate);
                Attendee originalAttendee = attendeeToUpdate.getOriginal();
                Attendee newAttendee = AttendeeMapper.getInstance().copy(originalAttendee, null, (AttendeeField[]) null);
                AttendeeMapper.getInstance().copy(attendeeToUpdate.getUpdate(), newAttendee, AttendeeField.RSVP, AttendeeField.HIDDEN, AttendeeField.COMMENT, AttendeeField.PARTSTAT, AttendeeField.ROLE, AttendeeField.EXTENDED_PARAMETERS, AttendeeField.TIMESTAMP);
                attendeesToUpdate.add(newAttendee);
            }
            storage.getAttendeeStorage().updateAttendees(originalEvent.getId(), attendeesToUpdate);
        }
        /*
         * perform attendee inserts, ensure correct timestamp for attendees
         */
        if (0 < attendeeUpdates.getAddedItems().size()) {
            requireWritePermissions(originalEvent, assumeExternalOrganizerUpdate);
            List<Attendee> addedAttendees = attendeeUpdates.getAddedItems();
            for (Attendee attendee : addedAttendees) {
                attendee.setTimestamp(timestamp.getTime());
            }
            storage.getAttendeeStorage().insertAttendees(originalEvent.getId(), addedAttendees);
        }
        return true;
    }

    /**
     * Persists conference updates in the underlying calendar storage, verifying that the current user has appropriate write permissions
     * in order to do so.
     *
     * @param originalEvent The event the conferences are updated for
     * @param conferenceUpdates The conference updates to persist
     * @param assumeExternalOrganizerUpdate <code>true</code> if an external organizer update can be assumed, <code>false</code>, otherwise
     * @return <code>true</code> if there were changes, <code>false</code>, otherwise
     */
    protected boolean storeConferenceUpdates(Event originalEvent, CollectionUpdate<Conference, ConferenceField> conferenceUpdates, boolean assumeExternalOrganizerUpdate) throws OXException {
        if (conferenceUpdates.isEmpty()) {
            return false;
        }
        requireWritePermissions(originalEvent, assumeExternalOrganizerUpdate);
        /*
         * perform conference deletions
         */
        if (0 < conferenceUpdates.getRemovedItems().size()) {
            storage.getConferenceStorage().deleteConferences(originalEvent.getId(), getConferenceIds(conferenceUpdates.getRemovedItems()));
        }
        /*
         * perform conference updates
         */
        if (0 < conferenceUpdates.getUpdatedItems().size()) {
            List<Conference> conferencesToUpdate = new ArrayList<Conference>(conferenceUpdates.getUpdatedItems().size());
            for (ItemUpdate<Conference, ConferenceField> conferenceToUpdate : conferenceUpdates.getUpdatedItems()) {
                Conference originalConference = conferenceToUpdate.getOriginal();
                Conference newConference = ConferenceMapper.getInstance().copy(originalConference, null, (ConferenceField[]) null);
                ConferenceMapper.getInstance().copy(conferenceToUpdate.getUpdate(), newConference, ConferenceField.URI, ConferenceField.LABEL, ConferenceField.FEATURES, ConferenceField.EXTENDED_PARAMETERS);
                conferencesToUpdate.add(newConference);
            }
            storage.getConferenceStorage().updateConferences(originalEvent.getId(), conferencesToUpdate);
        }
        /*
         * perform conference inserts
         */
        if (0 < conferenceUpdates.getAddedItems().size()) {
            storage.getConferenceStorage().insertConferences(originalEvent.getId(), prepareConferences(conferenceUpdates.getAddedItems()));
        }
        return true;
    }

    /**
     * Persists attachment updates in the underlying calendar storage, verifying that the current user has appropriate write permissions
     * in order to do so.
     *
     * @param originalEvent The event the attachments are updated for
     * @param attachmentUpdates The attachment updates to persist
     * @param assumeExternalOrganizerUpdate <code>true</code> if an external organizer update can be assumed, <code>false</code>, otherwise
     * @return <code>true</code> if there were changes, <code>false</code>, otherwise
     */
    protected boolean storeAttachmentUpdates(Event originalEvent, SimpleCollectionUpdate<Attachment> attachmentUpdates, boolean assumeExternalOrganizerUpdate) throws OXException {
        if (attachmentUpdates.isEmpty()) {
            return false;
        }
        requireWritePermissions(originalEvent, assumeExternalOrganizerUpdate);
        if (0 < attachmentUpdates.getRemovedItems().size()) {
            storage.getAttachmentStorage().deleteAttachments(session.getSession(), folder.getId(), originalEvent.getId(), attachmentUpdates.getRemovedItems());
        }
        if (0 < attachmentUpdates.getAddedItems().size()) {
            Check.attachmentsAreVisible(session, storage, attachmentUpdates.getAddedItems());
            storage.getAttachmentStorage().insertAttachments(session.getSession(), folder.getId(), originalEvent.getId(), prepareAttachments(attachmentUpdates.getAddedItems()));
        }
        return true;
    }

    /**
     * Restores data of foreign attendees that may have been injected previously from copies of the same group-scheduled event located
     * in calendar folders of other internal users, effectively undoing the applied changes so that they do not appear as being actively
     * updated by the client.
     *
     * @param originalEvent The original event
     * @param updatedEventData The updated event data
     * @return The possibly patched event, or the passed updated event data if not applicable
     * @see ResolvePerformer#injectKnownAttendeeData(Event, CalendarFolder)
     */
    protected Event restoreInjectedAttendeeDate(Event originalEvent, Event updatedEventData) {
        if (null == updatedEventData.getAttendees() || null == originalEvent.getAttendees() || null == originalEvent.getOrganizer() ||
            PublicType.getInstance().equals(folder.getType()) || isInternal(originalEvent.getOrganizer(), CalendarUserType.INDIVIDUAL)) {
            return updatedEventData;
        }
        Attendee calendarUserAttendee = find(originalEvent.getAttendees(), folder.getCalendarUserId());
        if (null == calendarUserAttendee) {
            return updatedEventData;
        }
        List<Attendee> restoredAttendees = new ArrayList<Attendee>(updatedEventData.getAttendees());
        boolean modified = false;
        for (ListIterator<Attendee> iterator = restoredAttendees.listIterator(); iterator.hasNext();) {
            Attendee attendee = iterator.next();
            /*
             * check if (virtually) internal attendee needs to be restored with original attendee data
             */
            if (matches(attendee, calendarUserAttendee) || matches(originalEvent.getOrganizer(), attendee) ||
                session.getConfig().isLookupPeerAttendeesForSameMailDomainOnly() && false == isSameMailDomain(extractEMailAddress(attendee.getUri()), extractEMailAddress(calendarUserAttendee.getUri()))) {
                continue; // not applicable
            }
            Attendee matchingOriginalAttendee = find(originalEvent.getAttendees(), attendee);
            if (null != matchingOriginalAttendee && isExternalUser(matchingOriginalAttendee)) {
                LOG.debug("Restoring previously injected attendee data {} from calendar {} back to {} in {}",
                    attendee, attendee.getFolderId(), matchingOriginalAttendee, updatedEventData);
                iterator.set(matchingOriginalAttendee);
                modified = true;
            }
        }
        if (modified) {
            /*
             * continue with restored attendee data
             */
            return new UnmodifiableEvent(updatedEventData) {

                @Override
                public List<Attendee> getAttendees() {
                    return restoredAttendees;
                }
            };
        }
        return updatedEventData;
    }

    /**
     * Adjusts the intermediate updated data of the series master event after a series split has been performed, effectively rolling back
     * an already applied value for the <i>related-to</i> field, so that the split can be recognized properly afterwards.
     *
     * @param originalSeriesMaster The original series master event (before the split)
     * @param updatedSeriesMaster The updated series master event (after the split)
     * @return The (possibly modified) updated event data to take over
     */
    protected static Event adjustUpdatedSeriesAfterSplit(Event originalSeriesMaster, Event updatedSeriesMaster) {
        RelatedTo originalRelatedTo = originalSeriesMaster.getRelatedTo();
        return new DelegatingEvent(updatedSeriesMaster) {

            @Override
            public RelatedTo getRelatedTo() {
                return originalRelatedTo;
            }
        };
    }

    /**
     * Adjusts the incoming client update for the series master event after a series split has been performed.
     * <p/>
     * This includes the selection of an appropriate recurrence rule, which may be necessary when the rule's <code>COUNT</code> attribute
     * was modified during the split operation.
     * <p/>
     * Also, the sequence number is forcibly incremented unless not already done before.
     *
     * @param originalSeriesMaster The original series master event (before the split)
     * @param updatedSeriesMaster The updated series master event (after the split)
     * @param clientUpdate The updated event data as passed by the client
     * @return The (possibly modified) client update to take over
     */
    protected static Event adjustClientUpdateAfterSplit(Event originalSeriesMaster, Event updatedSeriesMaster, Event clientUpdate) throws OXException {
        Event adjustedClientUpdate = EventMapper.getInstance().copy(clientUpdate, null, (EventField[]) null);
        /*
         * ensure the sequence number is incremented
         */
        if (originalSeriesMaster.getSequence() >= updatedSeriesMaster.getSequence() &&
            (false == clientUpdate.containsSequence() || originalSeriesMaster.getSequence() >= clientUpdate.getSequence())) {
            adjustedClientUpdate.setSequence(updatedSeriesMaster.getSequence() + 1);
        }
        /*
         * ensure the "related-to" value is set in the update
         */
        adjustedClientUpdate.setRelatedTo(updatedSeriesMaster.getRelatedTo());
        /*
         * adjust recurrence rule as needed
         */
        Mapping<? extends Object, Event> rruleMapping = EventMapper.getInstance().get(EventField.RECURRENCE_RULE);
        if (false == rruleMapping.isSet(clientUpdate) || rruleMapping.equals(updatedSeriesMaster, clientUpdate)) {
            /*
             * rrule not modified, nothing to do
             */
            return new UnmodifiableEvent(adjustedClientUpdate);
        }
        if (null == clientUpdate.getRecurrenceRule() || rruleMapping.equals(originalSeriesMaster, updatedSeriesMaster)) {
            /*
             * rrule is removed or was not changed by split, so take over new rrule from client as-is
             */
            return new UnmodifiableEvent(adjustedClientUpdate);
        }
        /*
         * rrule was modified during split, merge a possibly updated count value with client's rrule
         */
        RecurrenceRule updatedRule = initRecurrenceRule(updatedSeriesMaster.getRecurrenceRule());
        if (null != updatedRule.getCount()) {
            RecurrenceRule clientRule = initRecurrenceRule(clientUpdate.getRecurrenceRule());
            RecurrenceRule originalRule = initRecurrenceRule(originalSeriesMaster.getRecurrenceRule());
            if (null != clientRule.getCount() && clientRule.getCount().equals(originalRule.getCount())) {
                clientRule.setCount(updatedRule.getCount().intValue());
                adjustedClientUpdate.setRecurrenceRule(clientRule.toString());
            }
        }
        return new UnmodifiableEvent(adjustedClientUpdate);
    }

    protected static class InternalUpdateResult {

        private final InternalEventUpdate eventUpdate;
        private final List<Event> updatedChangeExceptions;
        private final Event updatedEvent;

        InternalUpdateResult(InternalEventUpdate eventUpdate, Event updatedEvent, List<Event> updatedChangeExceptions) {
            super();
            this.eventUpdate = eventUpdate;
            this.updatedChangeExceptions = updatedChangeExceptions;
            this.updatedEvent = updatedEvent;
        }

        List<Event> getUpdatedChangeExceptions() {
            return updatedChangeExceptions;
        }

        InternalEventUpdate getEventUpdate() {
            return eventUpdate;
        }

        Event getUpdatedEvent() {
            return updatedEvent;
        }
    }

}
