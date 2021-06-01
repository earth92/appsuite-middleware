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

package com.openexchange.chronos.alarm.message.impl;

import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.AlarmAction;
import com.openexchange.chronos.AlarmTrigger;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.alarm.message.AlarmNotificationService;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.provider.AdministrativeCalendarProvider;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.CalendarProvider;
import com.openexchange.chronos.provider.CalendarProviderRegistry;
import com.openexchange.chronos.provider.account.AdministrativeCalendarAccountService;
import com.openexchange.chronos.service.CalendarUtilities;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.RecurrenceIterator;
import com.openexchange.chronos.service.RecurrenceService;
import com.openexchange.chronos.storage.AdministrativeAlarmTriggerStorage;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.chronos.storage.CalendarStorageFactory;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.database.provider.SimpleDBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.util.Pair;
import com.openexchange.ratelimit.Rate;
import com.openexchange.ratelimit.RateLimiterFactory;

/**
 *
 * {@link SingleMessageDeliveryTask} executes the delivery for a calendar message alarm.
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.1
 */
class SingleMessageDeliveryTask implements Runnable {
    
    /**
     * A {@link Builder} for the {@link SingleMessageDeliveryTask}
     *
     * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
     * @since v7.10.1
     */
    public static class Builder {
        Context ctx;
        private Alarm alarm;
        private CalendarStorageFactory factory;
        private AlarmTrigger trigger;
        private CalendarUtilities calUtil;
        private long processed;
        private int account;
        private MessageAlarmDeliveryWorker callback;
        private DatabaseService dbservice;
        private AdministrativeAlarmTriggerStorage storage;
        private AlarmNotificationService alarmNotificationService;
        private CalendarProviderRegistry calendarProviderRegistry;
        private AdministrativeCalendarAccountService administrativeCalendarAccountService;
        private RateLimiterFactory rateLimitFactory;
        private RecurrenceService recurrenceService;

        public Builder setCtx(Context ctx) {
            this.ctx = ctx;
            return this;
        }

        public Builder setAlarm(Alarm alarm) {
            this.alarm = alarm;
            return this;
        }

        public Builder setFactory(CalendarStorageFactory factory) {
            this.factory = factory;
            return this;
        }

        public Builder setTrigger(AlarmTrigger trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder setCalUtil(CalendarUtilities calUtil) {
            this.calUtil = calUtil;
            return this;
        }

        public Builder setProcessed(long processed) {
            this.processed = processed;
            return this;
        }

        public Builder setAccount(int account) {
            this.account = account;
            return this;
        }

        public Builder setCallback(MessageAlarmDeliveryWorker callback) {
            this.callback = callback;
            return this;
        }

        public Builder setDbservice(DatabaseService dbservice) {
            this.dbservice = dbservice;
            return this;
        }

        public Builder setStorage(AdministrativeAlarmTriggerStorage storage) {
            this.storage = storage;
            return this;
        }

        public Builder setAlarmNotificationService(AlarmNotificationService alarmNotificationService) {
            this.alarmNotificationService = alarmNotificationService;
            return this;
        }

        public Builder setCalendarProviderRegistry(CalendarProviderRegistry calendarProviderRegistry) {
            this.calendarProviderRegistry = calendarProviderRegistry;
            return this;
        }

        public Builder setAdministrativeCalendarAccountService(AdministrativeCalendarAccountService administrativeCalendarAccountService) {
            this.administrativeCalendarAccountService = administrativeCalendarAccountService;
            return this;
        }

        public Builder setRateLimitFactory(RateLimiterFactory rateLimitFactory) {
            this.rateLimitFactory = rateLimitFactory;
            return this;
        }
        
        public Builder setRecurrenceService(RecurrenceService recurrenceService) {
            this.recurrenceService = recurrenceService;
            return this;
        }

        public SingleMessageDeliveryTask build() {
            return new SingleMessageDeliveryTask(   dbservice,
                                                    storage,
                                                    alarmNotificationService,
                                                    factory,
                                                    calUtil,
                                                    calendarProviderRegistry,
                                                    administrativeCalendarAccountService,
                                                    rateLimitFactory,
                                                    recurrenceService,
                                                    ctx,
                                                    account,
                                                    alarm,
                                                    trigger,
                                                    processed,
                                                    callback);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SingleMessageDeliveryTask.class);

    Context ctx;
    private final Alarm alarm;
    private final CalendarStorageFactory factory;
    private final AlarmTrigger trigger;
    private final CalendarUtilities calUtil;
    private final long processed;
    private final int account;
    private final MessageAlarmDeliveryWorker callback;
    private final DatabaseService dbservice;
    private final AdministrativeAlarmTriggerStorage storage;
    private final AlarmNotificationService notificationService;
    private final CalendarProviderRegistry calendarProviderRegistry;
    private final AdministrativeCalendarAccountService administrativeCalendarAccountService;
    private final RateLimiterFactory rateLimitFactory;
    private final RecurrenceService recurrenceService;

    /**
     * Initializes a new {@link SingleMessageDeliveryTask}.
     *
     * @param dbservice The {@link DatabaseService}
     * @param storage The {@link AdministrativeAlarmTriggerStorage}
     * @param notificationService The {@link AlarmNotificationService}
     * @param factory The {@link CalendarStorageFactory}
     * @param calUtil The {@link CalendarUtilities}
     * @param calendarProviderRegistry The {@link CalendarProviderRegistry}
     * @param administrativeCalendarAccountService An {@link AdministrativeCalendarAccountService}
     * @param rateLimitFactory The {@link RateLimiterFactory}
     * @param recurrenceService The {@link RecurrenceService}
     * @param ctx The {@link Context}
     * @param account The account id
     * @param alarm The {@link Alarm}
     * @param trigger The {@link AlarmTrigger}
     * @param callback The {@link MessageAlarmDeliveryWorker} which started this task
     * @param processed The processed value
     */
    protected SingleMessageDeliveryTask(   DatabaseService dbservice,
                                        AdministrativeAlarmTriggerStorage storage,
                                        AlarmNotificationService notificationService,
                                        CalendarStorageFactory factory,
                                        CalendarUtilities calUtil,
                                        CalendarProviderRegistry calendarProviderRegistry,
                                        AdministrativeCalendarAccountService administrativeCalendarAccountService,
                                        RateLimiterFactory rateLimitFactory,
                                        RecurrenceService recurrenceService,
                                        Context ctx,
                                        int account,
                                        Alarm alarm,
                                        AlarmTrigger trigger,
                                        long processed,
                                        MessageAlarmDeliveryWorker callback) {
        this.ctx = ctx;
        this.alarm = alarm;
        this.factory = factory;
        this.trigger = trigger;
        this.calUtil = calUtil;
        this.processed = processed;
        this.account = account;
        this.callback = callback;
        this.dbservice = dbservice;
        this.storage = storage;
        this.notificationService = notificationService;
        this.calendarProviderRegistry = calendarProviderRegistry;
        this.administrativeCalendarAccountService = administrativeCalendarAccountService;
        this.rateLimitFactory = rateLimitFactory;
        this.recurrenceService = recurrenceService;
    }

    @Override
    public void run() {
        LOG.trace("Started SingleMessageDeliveryTask for event {} and alarm {} in context {}", trigger.getEventId(), I(alarm.getId()), I(ctx.getContextId()));
        Connection writeCon = null;
        boolean isReadOnly = true;
        boolean processFinished = false;
        try {
            writeCon = dbservice.getWritable(ctx);
            writeCon.setAutoCommit(false);
            // do the delivery and update the db entries (e.g. like setting the acknowledged field)
            Event event = prepareEvent(writeCon);
            writeCon.commit();
            processFinished = true;
            if (event != null) {
                Databases.autocommit(writeCon);
                dbservice.backWritable(ctx, writeCon);
                writeCon = null;
                
                Event mailEvent = event;
                if (CalendarUtils.isSeriesMaster(event) && trigger.getRecurrenceId() != null) {
                    Date from = new Date(trigger.getRecurrenceId().getValue().getTimestamp());
                    RecurrenceIterator<Event> iterator = recurrenceService.iterateEventOccurrences(event, from, null);
                    if (iterator.hasNext()) {
                        mailEvent = iterator.next();
                    }
                }
                
                // send the message
                sendMessage(mailEvent);
                // If the triggers has been updated (deleted + inserted) check if a trigger needs to be scheduled.
                writeCon = dbservice.getWritable(ctx);
                writeCon.setAutoCommit(false);
                isReadOnly = checkEvent(writeCon, event);
                writeCon.commit();
            }
        } catch (OXException | SQLException e) {
            LOG.error("Unable to send message.", e);
            if (writeCon != null) {
                // rollback the last transaction
                Databases.rollback(writeCon);
                // if the error occurred during the process retry the hole operation once
                if (processFinished == false) {
                    try {
                        LOG.debug("Retrying operation...");
                        writeCon.setAutoCommit(false);
                        // do the delivery and update the db entries (e.g. like setting the acknowledged field)
                        Event event = prepareEvent(writeCon);
                        writeCon.commit();
                        if (event != null) {
                            isReadOnly = false;
                            sendMessage(event);
                        }
                        processFinished = true;
                        // If the triggers has been updated (deleted + inserted) check if a trigger needs to be scheduled.
                        if (event != null) {
                            checkEvent(writeCon, event);
                            writeCon.commit();
                        }
                    } catch (SQLException | OXException e1) {
                        // Nothing that can be done. Do a rollback and reset the processed value if necessary
                        LOG.trace(e1.getMessage(), e1);
                        Databases.rollback(writeCon);
                        if (processFinished == false) {
                            try {
                                storage.setProcessingStatus(writeCon, Collections.singletonMap(new Pair<>(I(ctx.getContextId()), I(account)), Collections.singletonList(trigger)), Long.valueOf(0l));
                                isReadOnly = false;
                            } catch (@SuppressWarnings("unused") OXException e2) {
                                // ignore
                            }
                        }
                    }
                }
            }
        } catch(Exception ex) {
            LOG.error(ex.getMessage(), ex);
            throw ex;
        } finally {
            callback.remove(new Key(ctx.getContextId(), account, trigger.getEventId(), alarm.getId()));
            Databases.autocommit(writeCon);
            if (writeCon != null) {
                if (isReadOnly) {
                    dbservice.backWritableAfterReading(ctx, writeCon);
                } else {
                    dbservice.backWritable(ctx, writeCon);
                }
            }
        }
    }

    /**
     * Checks if the delivery is still necessary and prepares the event for it:
     * - Acknowledges the alarm
     * - Deletes and reinserts the alarm triggers
     * - updates the event timestamp
     *
     * @param writeCon A writable connection
     * @return The prepared Event or null
     * @throws OXException
     */
    private Event prepareEvent(Connection writeCon) throws OXException {
        SimpleDBProvider provider = new SimpleDBProvider(writeCon, writeCon);
        CalendarStorage calStorage = factory.create(ctx, account, optEntityResolver(ctx.getContextId()), provider, DBTransactionPolicy.NO_TRANSACTIONS);
        AlarmTrigger loadedTrigger = calStorage.getAlarmTriggerStorage().loadTrigger(trigger.getAlarm().intValue());
        if (loadedTrigger == null || loadedTrigger.getProcessed().longValue() != processed) {
            // Abort since the triggers is either gone or picked up by another node (e.g. because of an update)
            LOG.trace("Skipped message alarm task for {}. Its trigger is not up to date!", new Key(ctx.getContextId(), account, trigger.getEventId(), alarm.getId()));
            return null;
        }
        Event event = null;
        CalendarAccount calendarAccount = null;
        AdministrativeCalendarProvider adminCalProvider = null;
        try {
            calendarAccount = administrativeCalendarAccountService.getAccount(ctx.getContextId(), trigger.getUserId().intValue(), account);
            if (calendarAccount == null) {
                LOG.trace("Unable to load calendar account.");
                return null;
            }
            CalendarProvider calendarProvider = calendarProviderRegistry.getCalendarProvider(calendarAccount.getProviderId());
            if (calendarProvider instanceof AdministrativeCalendarProvider) {
                adminCalProvider = (AdministrativeCalendarProvider) calendarProvider;
                event = adminCalProvider.getEventByAlarm(ctx, calendarAccount, trigger.getEventId(), null);
            } else {
                LOG.trace("Unable to load event for the given provider.");
                return null;
            }
        } catch (OXException e) {
            LOG.trace("Unable to load event with id {}: {}",trigger.getEventId(), e.getMessage());
            throw e;
        }
        if (event == null) {
            LOG.trace("Unable to load event with id {}.", trigger.getEventId());
            return null;
        }
        List<Alarm> alarms = event.getAlarms();
        for (Alarm tmpAlarm : alarms) {
            if (tmpAlarm.getId() == alarm.getId()) {
                tmpAlarm.setAcknowledged(new Date());
                tmpAlarm.setTimestamp(System.currentTimeMillis());
                break;
            }
        }
        calStorage.getAlarmStorage().updateAlarms(event, trigger.getUserId().intValue(), alarms);
        Map<Integer, List<Alarm>> loadAlarms = calStorage.getAlarmStorage().loadAlarms(event);
        calStorage.getAlarmTriggerStorage().deleteTriggers(event.getId());
        calStorage.getAlarmTriggerStorage().insertTriggers(event, loadAlarms);
        adminCalProvider.touchEvent(ctx, calendarAccount, trigger.getEventId());
        return event;
    }

    /**
     * Delivers the message
     *
     * @param event The event of the alarm
     */
    private void sendMessage(Event event) {
        Key key = new Key(ctx.getContextId(), account, event.getId(), alarm.getId());
        try {
            int userId = trigger.getUserId().intValue();
            int contextId = ctx.getContextId();
            if (notificationService.isEnabled(userId, contextId)) {
                if (checkRateLimit(alarm.getAction(), notificationService.getRate(userId, contextId), trigger.getUserId().intValue(), ctx.getContextId())) {
                    notificationService.send(event, alarm, ctx.getContextId(), account, trigger.getUserId().intValue(), trigger.getTime().longValue());
                    LOG.trace("Message successfully send for {}", key);
                } else {
                    LOG.info("Due to the rate limit it is not possible to send the message for {}", key);
                }
            } else {
                LOG.trace("Message dropped because the AlarmNotificationService is not enabled for user {}.", trigger.getUserId());
            }
        } catch (OXException e) {
            LOG.warn("Unable to send message for calendar alarm ({}): {}", key, e.getMessage(), e);
        }
    }

    private static final String RATE_LIMIT_PREFIX = "MESSAGE_ALARM_";

    /**
     * Checks the rate limit
     *
     * @param action The action to check
     * @param rate The rate to consider
     * @param userId The user id
     * @param ctxId The context id
     * @return <code>false</code> in case the rate limit is reached, <code>true</code> otherwise
     */
    private boolean checkRateLimit(AlarmAction action, Rate rate, int userId, int ctxId) {
        if (!rate.isEnabled() || rateLimitFactory == null) {
            return true;
        }
        try {
            return rateLimitFactory.createLimiter(RATE_LIMIT_PREFIX + action.getValue(), rate, userId, ctxId).acquire();
        } catch (OXException e) {
            LOG.warn("Unable to create RateLimiter.", e);
            return false;
        }
    }

    /**
     * Checks if the event contains message alarms which needs to be triggered soon
     *
     * @param writeCon The write connection to use
     * @param event The even to check
     * @return <code>true</code> if the write connections is used for read only, <code>false</code> otherwise
     * @throws OXException
     */
    private boolean checkEvent(Connection writeCon, Event event) throws OXException {
        int cid = ctx.getContextId();
        List<AlarmTrigger> triggers = callback.checkEvents(writeCon, Collections.singletonList(event), cid, account, true);
        if (triggers.isEmpty() == false) {
            CalendarStorage calStorage = factory.create(ctx, account, optEntityResolver(cid), new SimpleDBProvider(writeCon, writeCon), DBTransactionPolicy.NO_TRANSACTIONS);
            for (AlarmTrigger tmpTrigger : triggers) {
                callback.scheduleTaskForEvent(writeCon, calStorage, new Key(cid, account, tmpTrigger.getEventId(), tmpTrigger.getAlarm().intValue()), tmpTrigger);
            }
            return false;
        }
        return true;
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

}