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

package com.openexchange.chronos.provider.caching;

import static com.openexchange.chronos.common.CalendarUtils.getAlarmIDs;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesEvent;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesException;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.java.Autoboxing.i;
import static com.openexchange.java.Autoboxing.l;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.AlarmField;
import com.openexchange.chronos.AlarmTrigger;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.DelegatingEvent;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.EventFlag;
import com.openexchange.chronos.ExtendedProperty;
import com.openexchange.chronos.common.AlarmUtils;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.UpdateResultImpl;
import com.openexchange.chronos.common.UserConfigWrapper;
import com.openexchange.chronos.common.mapping.AlarmMapper;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.caching.internal.response.SingleEventResponseGenerator;
import com.openexchange.chronos.service.CalendarUtilities;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.RecurrenceIterator;
import com.openexchange.chronos.service.RecurrenceService;
import com.openexchange.chronos.service.UpdateResult;
import com.openexchange.chronos.storage.AlarmStorage;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.chronos.storage.CalendarStorageFactory;
import com.openexchange.chronos.storage.operation.OSGiCalendarStorageOperation;
import com.openexchange.context.ContextService;
import com.openexchange.conversion.ConversionService;
import com.openexchange.database.DatabaseService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.tools.mappings.common.CollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.ItemUpdate;
import com.openexchange.java.Strings;
import com.openexchange.osgi.Tools;
import com.openexchange.server.ServiceLookup;

/**
 * {@link AlarmHelper}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since 7.10.0
 */
public class AlarmHelper {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AlarmHelper.class);

    final ServiceLookup services;
    final Context context;
    final CalendarAccount account;

    /**
     * Initializes a new {@link AlarmHelper}.
     *
     * The passed service lookup reference should yield the {@link ContextService}, the {@link DatabaseService} and the
     * {@link CalendarStorageFactory}, and optionally the {@link CalendarUtilities} service.
     *
     * @param services The service lookup reference to use
     * @param context The context
     * @param account The calendar account
     */
    public AlarmHelper(ServiceLookup services, Context context, CalendarAccount account) {
        super();
        this.services = services;
        this.context = context;
        this.account = account;
    }

    /**
     * Loads and applies all alarm data of the account user associated with the supplied event.
     * <p/>
     * In case alarms are set, also the event's {@link EventField#TIMESTAMP} property is adjusted dynamically so that the maximum of the
     * timestamps is returned implicitly. Similarly, the event's {@link EventField#FLAGS} property is adjusted, too.
     *
     * @param event The event to load and apply the alarm data for
     * @return The event, enhanced with the loaded alarm data
     */
    public Event applyAlarms(Event event) throws OXException {
        return applyAlarms(event, loadAlarms(Collections.singletonList(event)).get(event.getId()));
    }

    /**
     * Loads and applies all alarm data of the account user associated with the supplied events.
     * <p/>
     * In case alarms are set, also the event's {@link EventField#TIMESTAMP} property is adjusted dynamically so that the maximum of the
     * timestamps is returned implicitly. Similarly, the event's {@link EventField#FLAGS} property is adjusted, too.
     *
     * @param events The events to load and apply the alarm data for
     * @return The events, enhanced with the loaded alarm data
     */
    public List<Event> applyAlarms(List<Event> events) throws OXException {
        if (null == events || 0 == events.size()) {
            return events;
        }
        Map<String, List<Alarm>> alarmsById = loadAlarms(events);
        if (alarmsById.isEmpty()) {
            return events;
        }
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            List<Alarm> alarms = alarmsById.get(event.getId());
            if (null != alarms && 0 < alarms.size()) {
                events.set(i, applyAlarms(event, alarms));
            }
        }
        return events;
    }

    /**
     * Loads all alarm data of the account user associated with the supplied events.
     *
     * @param events The events to load the alarm data for
     * @return The alarm data, mapped to the each event identifier
     */
    public Map<String, List<Alarm>> loadAlarms(final List<Event> events) throws OXException {
        return new OSGiCalendarStorageOperation<Map<String, List<Alarm>>>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected Map<String, List<Alarm>> call(CalendarStorage storage) throws OXException {
                return storage.getAlarmStorage().loadAlarms(events, account.getUserId());
            }
        }.executeQuery();
    }

    /**
     * Deletes stored alarm- and trigger data associated with a specific event of the calendar account.
     *
     * @param eventId The identifier of the event to delete the alarms for
     */
    public void deleteAlarms(final String eventId) throws OXException {
        new OSGiCalendarStorageOperation<Void>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected Void call(CalendarStorage storage) throws OXException {
                deleteAlarms(storage, eventId);
                return null;
            }
        }.executeUpdate();
    }

    /**
     * Deletes stored alarm- and trigger data associated with a specific event of the calendar account.
     *
     * @param storage The underlying calendar storage
     * @param eventId The identifier of the event to delete the alarms for
     */
    public void deleteAlarms(CalendarStorage storage, String eventId) throws OXException {
        storage.getAlarmStorage().deleteAlarms(eventId, account.getUserId());
        storage.getAlarmTriggerStorage().deleteTriggers(eventId);
    }

    /**
     * Deletes stored alarm- and trigger data associated with any event of the calendar account.
     */
    public void deleteAllAlarms() throws OXException {
        new OSGiCalendarStorageOperation<Void>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected Void call(CalendarStorage storage) throws OXException {
                storage.getAlarmStorage().deleteAlarms(account.getUserId());
                storage.getAlarmTriggerStorage().deleteTriggers(account.getUserId());
                return null;
            }
        }.executeUpdate();
    }

    /**
     * Replaces all alarms with new ones.
     * Basically the same as calling both {@link #deleteAllAlarms()} and {@link #insertDefaultAlarms(List)} but it is performed in one storage operation.
     */
    public void replaceAllAlarms(List<Event> events) throws OXException {
        final List<Alarm> defaultAlarms = getDefaultAlarms();
        final List<Alarm> defaultDateAlarms = getDateDefaultAlarms();
        new OSGiCalendarStorageOperation<Void>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected Void call(CalendarStorage storage) throws OXException {
                storage.getAlarmStorage().deleteAlarms(account.getUserId());
                storage.getAlarmTriggerStorage().deleteTriggers(account.getUserId());
                if ((null == defaultAlarms || defaultAlarms.isEmpty()) && (null == defaultDateAlarms || defaultDateAlarms.isEmpty())) {
                    return null;
                }
                insertDefaultAlarms(storage, defaultAlarms, defaultDateAlarms, events);
                return null;
            }
        }.executeUpdate();
    }

    /**
     * Gets a value indicating whether default alarms are configured for the calendar account or not.
     *
     * @return <code>true</code> if default alarms are configured, <code>false</code>, otherwise
     */
    public boolean hasDefaultAlarms() {
        List<Alarm> defaultAlarms = getDefaultAlarms();
        List<Alarm> defaultDateAlarms = getDateDefaultAlarms();
        return (null != defaultAlarms && !defaultAlarms.isEmpty()) || (defaultDateAlarms != null && !defaultDateAlarms.isEmpty());
    }

    /**
     * Gets the default alarms for date events configured in the calendar account.
     *
     * @return The default alarms, or <code>null</code> if none are defined
     */
    public List<Alarm> getDateDefaultAlarms() {
        try {
            UserConfigWrapper configWrapper = new UserConfigWrapper(Tools.requireService(ConversionService.class, services), account.getUserConfiguration());
            return configWrapper.getDefaultAlarmDate();
        } catch (Exception e) {
            LOG.warn("Error getting default alarm from user configuration \"{}\": {}", account.getUserConfiguration(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the default alarms configured in the calendar account.
     *
     * @return The default alarms, or <code>null</code> if none are defined
     */
    public List<Alarm> getDefaultAlarms() {
        try {
            UserConfigWrapper configWrapper = new UserConfigWrapper(Tools.requireService(ConversionService.class, services), account.getUserConfiguration());
            return configWrapper.getDefaultAlarmDateTime();
        } catch (Exception e) {
            LOG.warn("Error getting default alarm from user configuration \"{}\": {}", account.getUserConfiguration(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Inserts the configured default alarms and sets up corresponding triggers for the supplied event.
     *
     * @param event The event to insert the default alarms for
     */
    public void insertDefaultAlarms(Event event) throws OXException {
        insertDefaultAlarms(Collections.singletonList(event));
    }

    /**
     * Inserts the configured default alarms and sets up corresponding triggers list of events.
     *
     * @param events The events to insert the default alarms for
     */
    public void insertDefaultAlarms(final List<Event> events) throws OXException {
        final List<Alarm> defaultAlarms = getDefaultAlarms();
        final List<Alarm> defaultDateAlarms = getDateDefaultAlarms();
        if ((null == defaultAlarms || defaultAlarms.isEmpty()) && (null == defaultDateAlarms || defaultDateAlarms.isEmpty())) {
            return;
        }
        new OSGiCalendarStorageOperation<Void>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected Void call(CalendarStorage storage) throws OXException {
                insertDefaultAlarms(storage, defaultAlarms, defaultDateAlarms, events);
                return null;
            }
        }.executeUpdate();
    }

    /**
     * Inserts the configured default alarms and sets up corresponding triggers for the given events.
     *
     * @param storage The {@link CalendarStorage} to use
     * @param events The events to insert the default alarms for
     */
    public void insertDefaultAlarms(CalendarStorage storage, final List<Event> events) throws OXException {
        final List<Alarm> defaultAlarms = getDefaultAlarms();
        final List<Alarm> defaultDateAlarms = getDateDefaultAlarms();
        if ((null == defaultAlarms || defaultAlarms.isEmpty()) && (null == defaultDateAlarms || defaultDateAlarms.isEmpty())) {
            return;
        }
        insertDefaultAlarms(storage, defaultAlarms, defaultDateAlarms, events);
    }

    /**
     * Removes all previous alarms and adds the new default alarms sets up corresponding triggers for the given events.
     *
     * @param storage The {@link CalendarStorage} to use
     * @param events The events to change the default alarms for
     */
    public void changeDefaultAlarms(CalendarStorage storage, final List<Event> events) throws OXException {
        final List<Alarm> defaultAlarms = getDefaultAlarms();
        final List<Alarm> defaultDateAlarms = getDateDefaultAlarms();
        changeDefaultAlarms(storage, defaultAlarms, defaultDateAlarms, events);
    }

    /**
     * Loads alarm triggers for events in the underlying calendar account and filter them based on requested criteria. Supplementary event
     * data is loaded from the storage using the default implementation as needed.
     * <p/>
     * If possible, triggers for past occurrences of event series are implicitly forwarded to a later event occurrence that falls into
     * the requested time range.
     *
     * @param rangeFrom The lower (inclusive) boundary of the requested time range, or <code>null</code> if not limited
     * @param rangeUntil The upper (exclusive) boundary of the requested time range, or <code>null</code> if not limited
     * @param actions The alarm actions to include, or <code>null</code> to consider any alarm action
     * @return The loaded alarm triggers, or an empty list if there are none
     */
    public List<AlarmTrigger> getAlarmTriggers(Date rangeFrom, Date rangeUntil, Set<String> actions) throws OXException {
        return getAlarmTriggers(rangeFrom, rangeUntil, actions, (storage, trigger) -> {
            try {
                return SingleEventResponseGenerator.loadEvent(storage, account.getUserId(), trigger.getEventId(), trigger.getRecurrenceId(), null);
            } catch (OXException e) {
                LOG.warn("Error loading event {} referenced by alarm trigger", trigger.getEventId(), e);
                return null;
            }
        });
    }

    /**
     * Loads alarm triggers for events in the underlying calendar account and filter them based on requested criteria.
     * <p/>
     * If possible, triggers for past occurrences of event series are implicitly forwarded to a later event occurrence that falls into
     * the requested time range.
     *
     * @param rangeFrom The lower (inclusive) boundary of the requested time range, or <code>null</code> if not limited
     * @param rangeUntil The upper (exclusive) boundary of the requested time range, or <code>null</code> if not limited
     * @param actions The alarm actions to include, or <code>null</code> to consider any alarm action
     * @param loadEventFunction A function to retrieve the event referenced by a specific alarm trigger
     * @return The loaded alarm triggers, or an empty list if there are none
     */
    public List<AlarmTrigger> getAlarmTriggers(Date rangeFrom, Date rangeUntil, Set<String> actions, BiFunction<CalendarStorage, AlarmTrigger, Event> loadEventFunction) throws OXException {
        return new OSGiCalendarStorageOperation<List<AlarmTrigger>>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected List<AlarmTrigger> call(CalendarStorage storage) throws OXException {
                /*
                 * load trigger from storage & filter those that do not match the requested criteria
                 */
                List<AlarmTrigger> alarmTriggers = storage.getAlarmTriggerStorage().loadTriggers(account.getUserId(), rangeFrom, rangeUntil);
                for (Iterator<AlarmTrigger> iterator = alarmTriggers.iterator(); iterator.hasNext();) {
                    AlarmTrigger trigger = iterator.next();
                    /*
                     * skip triggers with other actions
                     */
                    if (null != actions && false == actions.contains(trigger.getAction())) {
                        iterator.remove();
                        continue;
                    }
                    /*
                     * skip if referenced event is no longer accessible & cleanup alarm trigger
                     */
                    Event event = loadEventFunction.apply(storage, trigger);
                    if (null == event) {
                        new OSGiCalendarStorageOperation<Void>(services, context.getContextId(), account.getAccountId()) {

                            @Override
                            protected Void call(CalendarStorage storage) throws OXException {
                                try {
                                    storage.getAlarmStorage().deleteAlarms(trigger.getEventId(), account.getUserId());
                                    storage.getAlarmTriggerStorage().deleteTriggers(trigger.getEventId());
                                    LOG.debug("Removed inaccessible alarm for event {} in account {}.", trigger.getEventId(), account);
                                } catch (OXException e) {
                                    LOG.warn("Error removing inaccessible alarm for event {} in account {}", trigger.getEventId(), account, e);
                                }
                                return null;
                            }
                        }.executeUpdate();
                        iterator.remove();
                        continue;
                    }
                    /*
                     * try and shift the trigger to a more recent occurrence if trigger time is before requested range as needed &
                     * finally remove triggers outside requested range
                     */
                    shiftIntoRange(storage, trigger, event, rangeFrom, rangeUntil);
                    if (false == AlarmUtils.isInRange(trigger, rangeFrom, rangeUntil)) {
                        iterator.remove();
                    }
                }
                return alarmTriggers;
            }
        }.executeQuery();
    }

    /**
     * Updates the user's alarms for a specific event.
     *
     * @param event The event to update the alarms for
     * @param updatedAlarms The updated alarms
     * @return The update result
     */
    public UpdateResult updateAlarms(final Event event, final List<Alarm> updatedAlarms, boolean touchEvent) throws OXException {
        return new OSGiCalendarStorageOperation<UpdateResult>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected UpdateResult call(CalendarStorage storage) throws OXException {
                /*
                 * update alarm data in storage
                 */
                List<Alarm> originalAlarms = storage.getAlarmStorage().loadAlarms(event, account.getUserId());
                if (false == updateAlarms(storage, event, originalAlarms, updatedAlarms)) {
                    return null;
                }
                Event updated = event;
                if (touchEvent) {
                    touch(storage, event.getId());
                    updated = storage.getEventStorage().loadEvent(event.getId(), null);
                    updated.setFolderId(event.getFolderId());
                }
                /*
                 * (re)-schedule any alarm triggers & return appropriate update result
                 */
                List<Alarm> newAlarms = storage.getAlarmStorage().loadAlarms(event, account.getUserId());
                Map<String, Map<Integer, List<Alarm>>> alarmsByUserByEventId = Collections.singletonMap(event.getId(), Collections.singletonMap(I(account.getUserId()), newAlarms));
                storage.getAlarmTriggerStorage().deleteTriggers(Collections.singletonList(event.getId()), account.getUserId());
                storage.getAlarmTriggerStorage().insertTriggers(alarmsByUserByEventId, Collections.singletonList(event));

                return new UpdateResultImpl(applyAlarms(event, originalAlarms), applyAlarms(updated, newAlarms));
            }
        }.executeUpdate();
    }

    /**
     * <i>Touches</i> an event in the storage by setting it's last modification timestamp and modified-by property to the current
     * timestamp and calendar user.
     *
     * @param storage The {@link CalendarStorage} to use
     * @param id The identifier of the event to <i>touch</i>
     */
    protected void touch(CalendarStorage storage, String id) throws OXException {
        Event eventUpdate = new Event();
        eventUpdate.setId(id);
        setModified(services.getServiceSafe(CalendarUtilities.class).getEntityResolver(context.getContextId()), new Date(), eventUpdate, account.getUserId());
        storage.getEventStorage().updateEvent(eventUpdate);
    }

    public static void setModified(EntityResolver resolver, Date lastModified, Event event, int modifiedBy) throws OXException {
        setModified(lastModified, event, resolver.applyEntityData(new CalendarUser(), modifiedBy));
    }

    public static void setModified(Date lastModified, Event event, CalendarUser modifiedBy) {
        event.setLastModified(lastModified);
        event.setModifiedBy(modifiedBy);
        event.setTimestamp(lastModified.getTime());
    }

    public void updateAlarmTriggers(final Event event) throws OXException {
        new OSGiCalendarStorageOperation<UpdateResult>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected UpdateResult call(CalendarStorage storage) throws OXException {
                List<Alarm> newAlarms = storage.getAlarmStorage().loadAlarms(event, account.getUserId());
                Map<String, Map<Integer, List<Alarm>>> alarmsByUserByEventId = Collections.singletonMap(event.getId(), Collections.singletonMap(I(account.getUserId()), newAlarms));
                storage.getAlarmTriggerStorage().deleteTriggers(Collections.singletonList(event.getId()), account.getUserId());
                storage.getAlarmTriggerStorage().insertTriggers(alarmsByUserByEventId, Collections.singletonList(event));
                return null;
            }
        }.executeUpdate();
    }

    /**
     * Returns the latest timestamp of alarms for this user.
     * Can be limited to an event.
     *
     * @param eventIds The optional event identifiers, can be null
     * @param userId The user identifier
     * @return The latest timestamp
     * @throws OXException
     */
    public Map<String, Long> getLatestTimestamps(final List<String> eventIds, final int userId) throws OXException {
        return new OSGiCalendarStorageOperation<Map<String, Long>>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected Map<String, Long> call(CalendarStorage storage) throws OXException {
                AlarmStorage alarmStorage = storage.getAlarmStorage();
                return eventIds == null || eventIds.isEmpty() ? Collections.emptyMap() : alarmStorage.getLatestTimestamp(eventIds, userId);
            }
        }.executeQuery();
    }

    /**
     * Returns the latest timestamp of alarms for this user.
     *
     * @param userId The user identifier
     * @return The latest timestamp
     * @throws OXException
     */
    public long getLatestTimestamp(final int userId) throws OXException {
        return new OSGiCalendarStorageOperation<Long>(services, context.getContextId(), account.getAccountId()) {

            @Override
            protected Long call(CalendarStorage storage) throws OXException {
                AlarmStorage alarmStorage = storage.getAlarmStorage();
                return L(alarmStorage.getLatestTimestamp(userId));
            }
        }.executeQuery().longValue();
    }

    /**
     * Updates (recreates) the alarm triggers for the given event
     *
     * @param storage The {@link CalendarStorage} to use
     * @param event The event to update the alarms for
     * @throws OXException
     */
    public void updateAlarmTriggers(CalendarStorage storage, final Event event) throws OXException {
        List<Alarm> newAlarms = storage.getAlarmStorage().loadAlarms(event, account.getUserId());
        storage.getAlarmTriggerStorage().deleteTriggers(Collections.singletonList(event.getId()), account.getUserId());
        if (null == newAlarms || newAlarms.isEmpty()) {
            return;
        }
        Map<String, Map<Integer, List<Alarm>>> alarmsByUserByEventId = Collections.singletonMap(event.getId(), Collections.singletonMap(I(account.getUserId()), newAlarms));
        storage.getAlarmTriggerStorage().insertTriggers(alarmsByUserByEventId, Collections.singletonList(event));
    }

    boolean updateAlarms(CalendarStorage storage, Event event, List<Alarm> originalAlarms, List<Alarm> updatedAlarms) throws OXException {
        CollectionUpdate<Alarm, AlarmField> alarmUpdates = AlarmUtils.getAlarmUpdates(originalAlarms, updatedAlarms);
        if (alarmUpdates.isEmpty()) {
            return false;
        }
        /*
         * delete removed alarms
         */
        List<Alarm> removedItems = alarmUpdates.getRemovedItems();
        if (0 < removedItems.size()) {
            storage.getAlarmStorage().deleteAlarms(event.getId(), account.getUserId(), getAlarmIDs(removedItems));
        }
        /*
         * save updated alarms
         */
        List<? extends ItemUpdate<Alarm, AlarmField>> updatedItems = alarmUpdates.getUpdatedItems();
        if (0 < updatedItems.size()) {
            List<Alarm> alarms = new ArrayList<Alarm>(updatedItems.size());
            for (ItemUpdate<Alarm, AlarmField> itemUpdate : updatedItems) {
                Alarm alarm = AlarmMapper.getInstance().copy(itemUpdate.getOriginal(), null, (AlarmField[]) null);
                AlarmMapper.getInstance().copy(itemUpdate.getUpdate(), alarm, AlarmField.values());
                alarm.setId(itemUpdate.getOriginal().getId());
                alarm.setUid(itemUpdate.getOriginal().getUid());
                alarm.setTimestamp(System.currentTimeMillis());
                alarms.add(alarm);
                //                alarms.add(Check.alarmIsValid(alarm));//TODO
            }
            storage.getAlarmStorage().updateAlarms(event, account.getUserId(), alarms);
        }
        /*
         * insert new alarms
         */
        List<Alarm> addedItems = alarmUpdates.getAddedItems();
        if (0 < addedItems.size()) {
            List<Alarm> newAlarms = new ArrayList<Alarm>(addedItems.size());
            for (Alarm alarm : addedItems) {
                Alarm newAlarm = AlarmMapper.getInstance().copy(alarm, null, (AlarmField[]) null);
                newAlarm.setId(storage.getAlarmStorage().nextId());
                newAlarm.setTimestamp(System.currentTimeMillis());
                if (false == newAlarm.containsUid() || Strings.isEmpty(newAlarm.getUid())) {
                    newAlarm.setUid(UUID.randomUUID().toString());
                }
                newAlarms.add(newAlarm);
            }
            storage.getAlarmStorage().insertAlarms(event, account.getUserId(), newAlarms);
        }

        return true;
    }

    int insertDefaultAlarms(CalendarStorage storage, List<Alarm> defaultAlarms, List<Alarm> defaultDateAlarms, List<Event> events) throws OXException {
        int count = 0;
        Map<String, Map<Integer, List<Alarm>>> alarmsByUserByEventId = new HashMap<String, Map<Integer, List<Alarm>>>(events.size());
        for (Event event : events) {
            List<Alarm> newAlarms;
            if (CalendarUtils.isAllDay(event)) {
                if (defaultDateAlarms == null || defaultDateAlarms.isEmpty()) {
                    continue;
                }
                newAlarms = prepareNewAlarms(storage, defaultDateAlarms);
            } else {
                if (defaultAlarms == null || defaultAlarms.isEmpty()) {
                    continue;
                }
                newAlarms = prepareNewAlarms(storage, defaultAlarms);
            }
            event.setAlarms(newAlarms);
            alarmsByUserByEventId.put(event.getId(), Collections.singletonMap(I(account.getUserId()), newAlarms));
            count += newAlarms.size();
        }
        storage.getAlarmStorage().insertAlarms(alarmsByUserByEventId);
        storage.getAlarmTriggerStorage().insertTriggers(alarmsByUserByEventId, events);
        return count;
    }

    int changeDefaultAlarms(CalendarStorage storage, List<Alarm> defaultAlarms, List<Alarm> defaultDateAlarms, List<Event> events) throws OXException {
        int count = 0;
        Map<String, Map<Integer, List<Alarm>>> alarmsByUserByEventId = new HashMap<String, Map<Integer, List<Alarm>>>(events.size());
        List<String> affectedEventIds = new ArrayList<>(events.size());
        for (Event event : events) {
            affectedEventIds.add(event.getId());
            List<Alarm> newAlarms;
            if (CalendarUtils.isAllDay(event)) {
                if (defaultDateAlarms == null || defaultDateAlarms.isEmpty()) {
                    continue;
                }
                newAlarms = prepareNewAlarms(storage, defaultDateAlarms);
            } else {
                if (defaultAlarms == null || defaultAlarms.isEmpty()) {
                    continue;
                }
                newAlarms = prepareNewAlarms(storage, defaultAlarms);
            }
            event.setAlarms(newAlarms);
            alarmsByUserByEventId.put(event.getId(), Collections.singletonMap(I(account.getUserId()), newAlarms));
            count += newAlarms.size();
        }
        storage.getAlarmTriggerStorage().deleteTriggers(affectedEventIds);
        storage.getAlarmStorage().deleteAlarms(affectedEventIds);
        storage.getAlarmStorage().insertAlarms(alarmsByUserByEventId);
        storage.getAlarmTriggerStorage().insertTriggers(alarmsByUserByEventId, events);
        return count;
    }

    /**
     * Try and shift the trigger to a more recent occurrence if trigger time is before requested range as needed
     *
     * @param storage The {@link CalendarStorage}
     * @param trigger The {@link AlarmTrigger} to shift
     * @param event The {@link Event}
     * @param rangeFrom The lower (inclusive) boundary of the requested time range, or <code>null</code> if not limited
     * @param rangeUntil The upper (exclusive) boundary of the requested time range, or <code>null</code> if not limited
     */
    void shiftIntoRange(CalendarStorage storage, AlarmTrigger trigger, Event event, Date rangeFrom, Date rangeUntil) {
        Date triggerTime = new Date(l(trigger.getTime()));
        if (null != rangeFrom && rangeFrom.after(triggerTime) && isSeriesEvent(event) && false == isSeriesException(event) && null != event.getRecurrenceRule()) {
            try {
                RecurrenceIterator<Event> recurrenceIterator = services.getService(RecurrenceService.class).iterateEventOccurrences(event, triggerTime, null);
                if (recurrenceIterator.hasNext()) {
                    Alarm alarm = storage.getAlarmStorage().loadAlarm(i(trigger.getAlarm()));
                    if (null == alarm) {
                        throw CalendarExceptionCodes.ALARM_NOT_FOUND.create(trigger.getAlarm(), trigger.getEventId());
                    }
                    AlarmUtils.shiftIntoRange(trigger, alarm, recurrenceIterator, rangeFrom, rangeUntil);
                }
            } catch (OXException e) {
                LOG.info("Unexpected error shifting alarm trigger for {} to later occurrence.", event.getId(), e);
            }
        }
    }

    /**
     * Prepares new alarms for the given default alarms
     *
     * @param storage The initialized {@link CalendarStorage} to use
     * @param defaultAlarms The default alarms
     * @return A list of new {@link Alarm}s
     * @throws OXException
     */
    private static List<Alarm> prepareNewAlarms(CalendarStorage storage, List<Alarm> defaultAlarms) throws OXException {
        List<Alarm> newAlarms = new ArrayList<Alarm>(defaultAlarms.size());
        for (Alarm alarm : defaultAlarms) {
            Alarm newAlarm = AlarmMapper.getInstance().copy(alarm, null, (AlarmField[]) null);
            newAlarm.setId(storage.getAlarmStorage().nextId());
            newAlarm.setUid(UUID.randomUUID().toString());
            newAlarm.setTimestamp(System.currentTimeMillis());
            AlarmUtils.addExtendedProperty(newAlarm, new ExtendedProperty("X-APPLE-LOCAL-DEFAULT-ALARM", "TRUE"), true);
            newAlarms.add(newAlarm);
        }
        return newAlarms;
    }

    /**
     * Applies a specific list of alarms for an event.
     * <p/>
     * In case alarms are set, also the event's {@link EventField#TIMESTAMP} property is adjusted dynamically so that the maximum of the
     * timestamps is returned implicitly. Similarly, the event's {@link EventField#FLAGS} property is adjusted, too.
     *
     * @param event The event to apply the alarms for
     * @param alarms The alarms to apply
     * @return A delegating event with the alarms applied
     */
    static Event applyAlarms(Event event, final List<Alarm> alarms) {
        if (null == event || null == alarms && null == event.getAlarms()) {
            return event;
        }
        return new DelegatingEvent(event) {

            @Override
            public List<Alarm> getAlarms() {
                return alarms;
            }

            @Override
            public boolean containsAlarms() {
                return true;
            }

            @Override
            public long getTimestamp() {
                long timestamp = super.getTimestamp();
                if (null != alarms && 0 < alarms.size()) {
                    for (Alarm alarm : alarms) {
                        timestamp = Math.max(timestamp, alarm.getTimestamp());
                    }
                }
                return timestamp;
            }

            @Override
            public EnumSet<EventFlag> getFlags() {
                EnumSet<EventFlag> flags = super.getFlags();
                if (null != alarms && 0 < alarms.size()) {
                    flags = EnumSet.copyOf(flags);
                    flags.add(EventFlag.ALARMS);
                }
                return flags;
            }

        };
    }

}
