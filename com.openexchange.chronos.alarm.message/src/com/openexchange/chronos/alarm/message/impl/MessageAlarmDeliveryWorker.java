/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.chronos.alarm.message.impl;

import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.AlarmAction;
import com.openexchange.chronos.AlarmTrigger;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.alarm.message.AlarmNotificationService;
import com.openexchange.chronos.provider.CalendarProviderRegistry;
import com.openexchange.chronos.provider.account.AdministrativeCalendarAccountService;
import com.openexchange.chronos.service.CalendarUtilities;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.RecurrenceService;
import com.openexchange.chronos.storage.AdministrativeAlarmTriggerStorage;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.chronos.storage.CalendarStorageFactory;
import com.openexchange.context.ContextService;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.database.cleanup.CleanUpExecution;
import com.openexchange.database.cleanup.CleanUpExecutionConnectionProvider;
import com.openexchange.database.cleanup.DatabaseCleanUpExceptionCode;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.database.provider.SimpleDBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.java.util.Pair;
import com.openexchange.ratelimit.RateLimiterFactory;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;

/**
 * The {@link MessageAlarmDeliveryWorker} checks if there are any message alarm triggers (e.g. email and sms) which needed to be executed within the next timeframe ({@link #lookAhead}).
 * It then marks those triggers as processed and schedules a {@link SingleMessageDeliveryTask} at the appropriate time (shifted forward by the {@link #shifts} value)
 * for each of them.
 *
 * It also picks up old triggers, which are already marked as processed by other threats, if they are overdue ({@link #overdueWaitTime}).
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.1
 */
public class MessageAlarmDeliveryWorker implements CleanUpExecution {

    protected static final Logger LOG = LoggerFactory.getLogger(MessageAlarmDeliveryWorker.class);
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private final AdministrativeAlarmTriggerStorage storage;
    private final DatabaseService dbservice;
    private final ContextService ctxService;
    private final TimerService timerService;

    private final Map<Key, ScheduledTimerTask> scheduledTasks = new ConcurrentHashMap<>();
    private final CalendarStorageFactory factory;
    private final CalendarUtilities calUtil;

    private final AlarmNotificationServiceRegistry registry;
    private final CalendarProviderRegistry calendarProviderRegistry;
    private final AdministrativeCalendarAccountService administrativeCalendarAccountService;
    private final RecurrenceService recurrenceService;
    private final RateLimiterFactory rateLimitFactory;
    private final int lookAhead;
    private final int overdueWaitTime;

    /**
     * Initializes a new {@link MessageAlarmDeliveryWorker}.
     *
     * @param services The {@link ServiceLookup} to get various services from
     * @param registry The {@link AlarmNotificationServiceRegistry} used to send the notification
     * @param lookAhead The time value in minutes the worker is looking ahead.
     * @param overdueWaitTime The time in minutes to wait until an old trigger is picked up.
     * @throws OXException In case a service is missing
     */
    public MessageAlarmDeliveryWorker(ServiceLookup services, AlarmNotificationServiceRegistry registry, int lookAhead, int overdueWaitTime) throws OXException {
        this.storage = services.getServiceSafe(AdministrativeAlarmTriggerStorage.class);
        this.dbservice = services.getServiceSafe(DatabaseService.class);
        this.ctxService = services.getServiceSafe(ContextService.class);
        this.timerService = services.getServiceSafe(TimerService.class);
        this.factory = services.getServiceSafe(CalendarStorageFactory.class);
        this.calUtil = services.getServiceSafe(CalendarUtilities.class);
        this.calendarProviderRegistry = services.getServiceSafe(CalendarProviderRegistry.class);
        this.administrativeCalendarAccountService = services.getServiceSafe(AdministrativeCalendarAccountService.class);
        this.rateLimitFactory = services.getServiceSafe(RateLimiterFactory.class);
        this.recurrenceService = services.getServiceSafe(RecurrenceService.class);
        this.registry = registry;
        this.lookAhead = lookAhead;
        this.overdueWaitTime = overdueWaitTime;
    }

    @Override
    public boolean isApplicableFor(String schema, int representativeContextId, int databasePoolId, CleanUpExecutionConnectionProvider connectionProvider) throws OXException {
        try {
            return Databases.executeQuery(connectionProvider.getConnection(),
                (rs) -> Boolean.TRUE, // We have a result, so we are fine
                "SELECT 1 FROM updateTask WHERE taskName=?",
                (s) -> s.setString(1, MessageAlarmDeliveryWorkerUpdateTask.TASK_NAME)).booleanValue();
        } catch (SQLException e) {
            throw DatabaseCleanUpExceptionCode.SQL_ERROR.create(e.getMessage(), e);
        }
    }

    @Override
    public void executeFor(String schema, int representativeContextId, int databasePoolId, CleanUpExecutionConnectionProvider connectionProvider) throws OXException {
        LOG.info("Started alarm delivery worker...");
        Calendar until = Calendar.getInstance(UTC);
        until.add(Calendar.MINUTE, lookAhead);
        try {
            Calendar currentUTCTime = Calendar.getInstance(UTC);
            Connection connection = connectionProvider.getConnection();
            Map<Pair<Integer, Integer>, List<AlarmTrigger>> lockedTriggers = null;
            Calendar overdueTime = Calendar.getInstance(UTC);
            overdueTime.add(Calendar.MINUTE, -Math.abs(overdueWaitTime));

            lockedTriggers = storage.getAndLockTriggers(connection, until.getTime(), overdueTime.getTime(), false, registry.getActions());
            if (lockedTriggers.isEmpty()) {
                return;
            }

            lockedTriggers = storage.getAndLockTriggers(connection, until.getTime(), overdueTime.getTime(), true, registry.getActions());
            if (lockedTriggers.isEmpty()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
            storage.setProcessingStatus(connection, lockedTriggers, Long.valueOf(currentUTCTime.getTimeInMillis()));
            spawnDeliveryTaskForTriggers(connection, lockedTriggers, currentUTCTime);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            // Nothing that can be done here. Just retry it with the next run
            LOG.error(e.getMessage(), e);
        }
        LOG.info("Alarm delivery worker run finished!");
    }

    /**
     * Spawns an delivery worker for the given triggers
     *
     * @param connection The connection to use
     * @param lockedTriggers The triggers to spawn a delivery task for
     * @param currentUTCTime The current UTC time
     * @throws OXException
     */
    private void spawnDeliveryTaskForTriggers(Connection connection, Map<Pair<Integer, Integer>, List<AlarmTrigger>> lockedTriggers, Calendar currentUTCTime) throws OXException {
        for (Entry<Pair<Integer, Integer>, List<AlarmTrigger>> entry : lockedTriggers.entrySet()) {
            int cid = entry.getKey().getFirst().intValue();
            int account = entry.getKey().getSecond().intValue();
            CalendarStorage calendarStorage = factory.create(ctxService.getContext(cid), account, optEntityResolver(cid), new SimpleDBProvider(connection, connection), DBTransactionPolicy.NO_TRANSACTIONS);
            for (AlarmTrigger trigger : entry.getValue()) {
                try {
                    Alarm alarm = calendarStorage.getAlarmStorage().loadAlarm(trigger.getAlarm().intValue());
                    Calendar calTriggerTime = Calendar.getInstance(UTC);
                    calTriggerTime.setTimeInMillis(trigger.getTime().longValue());
                    Calendar now = Calendar.getInstance(UTC);
                    AlarmNotificationService alarmNotificationService = registry.getService(alarm.getAction());
                    if (alarmNotificationService == null) {
                        LOG.error("Missing required AlarmNotificationService for alarm action \"{}\"", alarm.getAction().getValue());
                        throw ServiceExceptionCode.absentService(AlarmNotificationService.class);
                    }

                    Integer shift = I(alarmNotificationService.getShift());
                    long delay = (calTriggerTime.getTimeInMillis() - now.getTimeInMillis()) - (shift == null ? 0 : shift.intValue());
                    if (delay < 0) {
                        delay = 0;
                    }

                    SingleMessageDeliveryTask task = createTask(cid, account, alarm, trigger, currentUTCTime.getTimeInMillis(), alarmNotificationService);
                    ScheduledTimerTask timer = timerService.schedule(task, delay, TimeUnit.MILLISECONDS);
                    Key key = key(cid, account, trigger.getEventId(), alarm.getId());
                    scheduledTasks.put(key, timer);
                    LOG.trace("Created a new alarm task for {}", key);
                } catch (UnsupportedOperationException e) {
                    LOG.error("Can't handle message alarms as long as the legacy storage is used.");
                    continue;
                }
            }
        }
    }

    /**
     * Creates a new {@link SingleMessageDeliveryTask}
     *
     * @param cid The context id
     * @param account The account id
     * @param alarm The {@link Alarm}
     * @param trigger The {@link AlarmTrigger}
     * @param processed The processed value
     * @param alarmNotificationService The {@link AlarmNotificationService}
     * @return The task
     * @throws OXException If the context couldn't be loaded or if no {@link AlarmNotificationService} is registered for the {@link AlarmAction} of the alarm
     */
    private SingleMessageDeliveryTask createTask(int cid, int account, Alarm alarm, AlarmTrigger trigger, long processed, AlarmNotificationService alarmNotificationService) throws OXException {
        return new SingleMessageDeliveryTask.Builder() //@formatter:off
                                         .setDbservice(dbservice)
                                         .setStorage(storage)
                                         .setAlarmNotificationService(alarmNotificationService)
                                         .setFactory(factory)
                                         .setCalUtil(calUtil)
                                         .setCalendarProviderRegistry(calendarProviderRegistry)
                                         .setAdministrativeCalendarAccountService(administrativeCalendarAccountService)
                                         .setRecurrenceService(recurrenceService)
                                         .setCtx(ctxService.getContext(cid))
                                         .setAccount(account)
                                         .setAlarm(alarm)
                                         .setTrigger(trigger)
                                         .setProcessed(processed)
                                         .setCallback(this)
                                         .setRateLimitFactory(rateLimitFactory)
                                         .build(); //@formatter:on
    }

    /**
     * Creates a {@link Key}
     *
     * @param cid The context id
     * @param account The account id
     * @param eventId The event id
     * @param alarm The alarm id
     * @return the {@link Key}
     */
    Key key(int cid, int account, String eventId, int alarm) {
        return new Key(cid, account, eventId, alarm);
    }

    /**
     * Cancels all tasks for the given event id
     *
     * @param cid The context id
     * @param accountId The account id
     * @param eventId The event id to cancel tasks for. E.g. because the event is deleted.
     */
    public void cancelAll(int cid, int accountId, String eventId) {
        Iterator<Entry<Key, ScheduledTimerTask>> iterator = scheduledTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Key, ScheduledTimerTask> entry = iterator.next();
            Key key = entry.getKey();
            if (key.getCid() == cid && key.getAccount() == accountId && key.getEventId().equals(eventId)) {
                LOG.trace("Canceled message alarm task for {}", key);
                entry.getValue().cancel();
                iterator.remove();
            }
        }
    }

    /**
     * Cancels all tasks for the given event ids
     *
     * @param cid The context id
     * @param accountId The account id
     * @param eventIds The event ids to cancel tasks for. E.g. because those events are deleted.
     */
    public void cancelAll(int cid, int accountId, Collection<String> eventIds) {
        for (String eventId : eventIds) {
            cancelAll(cid, accountId, eventId);
        }
    }

    /**
     * Checks if the given events contain alarm trigger which must be triggered before the next run of the {@link MessageAlarmDeliveryWorker} and
     * schedules a task for each trigger.
     *
     * @param events A list of updated and newly created events
     * @param cid The context id
     * @param account The account id
     */
    public void checkAndScheduleTasksForEvents(List<Event> events, int cid, int account) {
        Connection readCon = null;
        Connection writeCon = null;
        try {
            readCon = dbservice.getReadOnly(cid);
            boolean successful = false;
            boolean readOnly = true;
            try {
                List<AlarmTrigger> triggers = checkEvents(readCon, events, cid, account, false);
                if (triggers.isEmpty() == false) {
                    dbservice.backReadOnly(cid, readCon);
                    readCon = null;
                    // If there are due alarm triggers get a writable connection and lock those triggers
                    writeCon = dbservice.getWritable(cid);
                    writeCon.setAutoCommit(false);
                    triggers = checkEvents(writeCon, events, cid, account, true);
                    if (triggers.isEmpty() == false) {
                        readOnly = false;
                        CalendarStorage calStorage = factory.create(ctxService.getContext(cid), account, optEntityResolver(cid), new SimpleDBProvider(writeCon, writeCon), DBTransactionPolicy.NO_TRANSACTIONS);
                        for (AlarmTrigger trigger : triggers) {
                            scheduleTaskForEvent(writeCon, calStorage, key(cid, account, trigger.getEventId(), trigger.getAlarm().intValue()), trigger);
                        }
                    }
                    writeCon.commit();
                    successful = true;
                }
            } catch (SQLException e) {
                LOG.error("Error while scheduling message alarm task: {}", e.getMessage(), e);
            } finally {
                if (readCon != null) {
                    dbservice.backReadOnly(cid, readCon);
                }
                if (writeCon != null) {
                    if (successful == false) {
                        Databases.rollback(writeCon);
                    }
                    Databases.autocommit(writeCon);
                    if (readOnly) {
                        dbservice.backWritableAfterReading(cid, writeCon);
                    } else {
                        dbservice.backWritable(cid, writeCon);
                    }
                }
            }
        } catch (OXException e) {
            LOG.error("Error while trying to handle event: {}", e.getMessage(), e);
            // Can be ignored. Triggers are picked up with the next run of the MessageAlarmDeliveryWorker
        }
    }

    /**
     * Checks the given events for message alarms which need to be triggered soon
     *
     * @param con The connection to use
     * @param events The events to check
     * @param cid The id of the context the events belong to
     * @param account The id of the account the events belong to
     * @param isWriteCon The whether the given connection is a write connection or not
     * @return A list of AlarmTriggers which needs to be scheduled
     * @throws OXException
     */
    List<AlarmTrigger> checkEvents(Connection con, List<Event> events, int cid, int account, boolean isWriteCon) throws OXException {
        Calendar cal = Calendar.getInstance(UTC);
        cal.add(Calendar.MINUTE, lookAhead);

        List<AlarmTrigger> result = null;
        for (Event event : events) {
            Map<Pair<Integer, Integer>, List<AlarmTrigger>> triggerMap = storage.getMessageAlarmTriggers(con, cid, account, event.getId(), isWriteCon, registry.getActions());
            // Schedule a task for all triggers before the next usual interval
            for (Entry<Pair<Integer, Integer>, List<AlarmTrigger>> entry : triggerMap.entrySet()) {
                for (AlarmTrigger trigger : entry.getValue()) {
                    Key key = key(cid, account, event.getId(), trigger.getAlarm().intValue());
                    if (trigger.getTime().longValue() > cal.getTimeInMillis()) {
                        cancelTask(key);
                        continue;
                    }
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(trigger);
                }
            }
        }
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * Schedules a task for the given {@link AlarmTrigger}
     *
     * @param writeCon The write connection to use
     * @param storage The {@link CalendarStorage} to use
     * @param key The key
     * @param trigger The {@link AlarmTrigger}
     * @throws OXException
     */
    void scheduleTaskForEvent(Connection writeCon, CalendarStorage storage, Key key, AlarmTrigger trigger) throws OXException {
        try {
            Alarm alarm = storage.getAlarmStorage().loadAlarm(trigger.getAlarm().intValue());
            scheduleTask(writeCon, key, alarm, trigger);
        } catch (UnsupportedOperationException e) {
            LOG.error("Can't handle message alarms as long as the legacy storage is used.");
        }
    }

    /**
     * Schedules a {@link SingleMessageDeliveryTask} for the given {@link AlarmTriggerWrapper}
     *
     * @param con The connection to use
     * @param key The {@link Key} to the {@link SingleMessageDeliveryTask}
     * @param alarm The {@link Alarm} of the {@link AlarmTriggerWrapper}
     * @param trigger The {@link AlarmTriggerWrapper}
     */
    private void scheduleTask(Connection con, Key key, Alarm alarm, AlarmTrigger trigger) {
        cancelTask(key);
        boolean processingSet = false;
        try {
            Calendar calTriggerTime = Calendar.getInstance(UTC);
            calTriggerTime.setTimeInMillis(trigger.getTime().longValue());
            Calendar now = Calendar.getInstance(UTC);

            storage.setProcessingStatus(con, Collections.singletonMap(new Pair<>(I(key.getCid()), I(key.getAccount())), Collections.singletonList(trigger)), Long.valueOf(now.getTimeInMillis()));
            processingSet = true;
            AlarmNotificationService alarmNotificationService = registry.getService(alarm.getAction());
            if (alarmNotificationService == null) {
                LOG.error("Missing required AlarmNotificationService for alarm action \"{}\"", alarm.getAction().getValue());
                throw ServiceExceptionCode.absentService(AlarmNotificationService.class);
            }
            Integer shift = I(alarmNotificationService.getShift());
            long delay = (calTriggerTime.getTimeInMillis() - now.getTimeInMillis()) - (shift == null ? 0 : shift.intValue());
            if (delay < 0) {
                delay = 0;
            }

            LOG.trace("Created new task for {}", key);
            SingleMessageDeliveryTask task = createTask(key.getCid(), key.getAccount(), alarm, trigger, now.getTimeInMillis(), alarmNotificationService);
            ScheduledTimerTask timer = timerService.schedule(task, delay, TimeUnit.MILLISECONDS);
            scheduledTasks.put(key, timer);
        } catch (OXException e) {
            if (processingSet) {
                try {
                    // If the error is thrown after the processed value is successfully set then set it back to 0 so the next task can pick it up
                    storage.setProcessingStatus(con, Collections.singletonMap(new Pair<>(I(key.getCid()), I(key.getAccount())), Collections.singletonList(trigger)), Long.valueOf(0l));
                } catch (OXException e1) {
                    // Can be ignored. The trigger is picked up once the trigger time is overdue.
                }
            }
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * Cancels the task specified by the key if one exists
     *
     * @param key The key
     */
    private void cancelTask(Key key) {
        ScheduledTimerTask scheduledTimerTask = scheduledTasks.remove(key);
        if (scheduledTimerTask != null) {
            LOG.trace("Canceled message alarm task for {}", key);
            scheduledTimerTask.cancel();
        }
    }

    /**
     * Cancels all running thread and tries to reset their processed values
     */
    public void cancel() {
        Map<Integer, List<Entry<Key, ScheduledTimerTask>>> entries = cancelAllScheduledTasks();
        for (Entry<Integer, List<Entry<Key, ScheduledTimerTask>>> cidEntry : entries.entrySet()) {
            Connection con = null;
            try {
                Map<Pair<Integer, Integer>, List<AlarmTrigger>> triggers = new HashMap<>(cidEntry.getValue().size());
                for (Entry<Key, ScheduledTimerTask> entry : cidEntry.getValue()) {
                    Key key = entry.getKey();
                    AlarmTrigger trigger = new AlarmTrigger();
                    trigger.setAlarm(I(key.getId()));
                    Pair<Integer, Integer> pair = new Pair<Integer, Integer>(I(key.getCid()), I(key.getAccount()));
                    List<AlarmTrigger> list = triggers.get(pair);
                    if (list == null) {
                        list = new ArrayList<>();
                        triggers.put(pair, list);
                    }
                    list.add(trigger);
                    LOG.trace("Try to reset the processed status of the alarm trigger for {}", key);
                }
                con = dbservice.getWritable(cidEntry.getKey().intValue());
                if (storage != null && con != null) {
                    storage.setProcessingStatus(con, triggers, Long.valueOf(0l));
                    LOG.trace("Successfully resetted the processed stati for context {}.", cidEntry.getKey());
                }
            } catch (OXException e1) {
                // ignore
            } finally {
                Databases.close(con);
            }
        }

        scheduledTasks.clear();
    }

    /**
     * Cancels all scheduled tasks and returns mapping of cids to those tasks
     *
     * @return The cid / List of entries mapping
     */
    private Map<Integer, List<Entry<Key, ScheduledTimerTask>>> cancelAllScheduledTasks() {
        Map<Integer, List<Entry<Key, ScheduledTimerTask>>> entries = new HashMap<>();
        for (Entry<Key, ScheduledTimerTask> entry : scheduledTasks.entrySet()) {
            Key key = entry.getKey();
            entry.getValue().cancel();
            List<Entry<Key, ScheduledTimerTask>> list = entries.get(I(key.getCid()));
            if (list == null) {
                list = new ArrayList<>();
                entries.put(I(key.getCid()), list);
            }
            list.add(entry);
            LOG.trace("Canceled message alarm delivery task for {}", key);
        }
        return entries;
    }

    /**
     * Optionally gets an entity resolver for the supplied context.
     *
     * @param contextId The identifier of the context to get the entity resolver for
     * @return The entity resolver, or <code>null</code> if not available
     */
    private EntityResolver optEntityResolver(int contextId) {
        try {
            return calUtil.getEntityResolver(contextId);
        } catch (OXException e) {
            LOG.trace("Error getting entity resolver for context: {}", Integer.valueOf(contextId), e);
        }
        return null;
    }

    /**
     * Removes the {@link SingleMessageDeliveryTask} defined by the given key from the local map.
     *
     * @param key The key to remove
     */
    public void remove(Key key) {
        if (key != null) {
            scheduledTasks.remove(key);
        }
    }

}
