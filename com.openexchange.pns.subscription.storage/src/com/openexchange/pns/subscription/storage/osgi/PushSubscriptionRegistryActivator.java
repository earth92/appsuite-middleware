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

package com.openexchange.pns.subscription.storage.osgi;

import java.util.Dictionary;
import java.util.Hashtable;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import com.openexchange.caching.CacheService;
import com.openexchange.caching.events.CacheEventService;
import com.openexchange.context.ContextService;
import com.openexchange.database.CreateTableService;
import com.openexchange.database.DatabaseService;
import com.openexchange.groupware.delete.DeleteListener;
import com.openexchange.groupware.update.DefaultUpdateTaskProviderService;
import com.openexchange.groupware.update.UpdateTaskProviderService;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.pns.PushSubscriptionRegistry;
import com.openexchange.pns.subscription.storage.CompositePushSubscriptionRegistry;
import com.openexchange.pns.subscription.storage.groupware.CreatePnsSubscriptionTable;
import com.openexchange.pns.subscription.storage.groupware.PnsCreateTableTask;
import com.openexchange.pns.subscription.storage.groupware.PnsDeleteListener;
import com.openexchange.pns.subscription.storage.groupware.PnsSubscriptionTablesUtf8Mb4UpdateTask;
import com.openexchange.pns.subscription.storage.groupware.PnsSubscriptionsAddExpiresColumTask;
import com.openexchange.pns.subscription.storage.groupware.PnsSubscriptionsAddIndexTask;
import com.openexchange.pns.subscription.storage.groupware.PnsSubscriptionsReindexTask;
import com.openexchange.pns.subscription.storage.groupware.PnsSubscriptionsUseUtf8mb4ForClientColumnTask;
import com.openexchange.pns.subscription.storage.rdb.RdbPushSubscriptionRegistry;
import com.openexchange.pns.subscription.storage.rdb.cache.RdbPushSubscriptionRegistryCache;
import com.openexchange.pns.subscription.storage.rdb.cache.RdbPushSubscriptionRegistryInvalidator;
import com.openexchange.sessiond.SessiondEventConstants;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.behavior.CallerRunsBehavior;


/**
 * {@link PushSubscriptionRegistryActivator}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class PushSubscriptionRegistryActivator extends HousekeepingActivator {

    private RdbPushSubscriptionRegistryCache cache;

    /**
     * Initializes a new {@link PushSubscriptionRegistryActivator}.
     */
    public PushSubscriptionRegistryActivator() {
        super();
    }

    @Override
    protected boolean stopOnServiceUnavailability() {
        return true;
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { DatabaseService.class, ContextService.class, CacheService.class, CacheEventService.class, ThreadPoolService.class };
    }

    @Override
    protected synchronized void startBundle() throws Exception {
        final Logger logger = org.slf4j.LoggerFactory.getLogger(PushSubscriptionRegistryActivator.class);

        // Create database-backed registry
        RdbPushSubscriptionRegistry persistentRegistry = new RdbPushSubscriptionRegistry(getService(DatabaseService.class), getService(ContextService.class));

        // Create cache instance
        final RdbPushSubscriptionRegistryCache cache = new RdbPushSubscriptionRegistryCache(persistentRegistry, getService(CacheEventService.class), getService(CacheService.class));
        this.cache = cache;
        persistentRegistry.setCache(cache);

        // Track subscription listeners
        PushSubscriptionListenerTracker listenerTracker = new PushSubscriptionListenerTracker(context);
        rememberTracker(listenerTracker);

        // Track subscription providers
        PushSubscriptionProviderTracker providerTracker = new PushSubscriptionProviderTracker(listenerTracker, context);
        rememberTracker(providerTracker);
        track(CacheEventService.class, new RdbPushSubscriptionRegistryInvalidator(cache, context));
        openTrackers();

        // Register update task, create table job and delete listener
        boolean registerGroupwareStuff = true;
        if (registerGroupwareStuff) {
            registerService(UpdateTaskProviderService.class, new DefaultUpdateTaskProviderService(
                new PnsCreateTableTask(),
                new PnsSubscriptionsReindexTask(),
                new PnsSubscriptionsAddExpiresColumTask(),
                new PnsSubscriptionsAddIndexTask(),
                new PnsSubscriptionTablesUtf8Mb4UpdateTask(),
                new PnsSubscriptionsUseUtf8mb4ForClientColumnTask()
            ));
            registerService(CreateTableService.class, new CreatePnsSubscriptionTable());
            registerService(DeleteListener.class, new PnsDeleteListener(persistentRegistry));
        }

        // Register service
        registerService(PushSubscriptionRegistry.class, new CompositePushSubscriptionRegistry(persistentRegistry, providerTracker, listenerTracker));

        // Register event handler
        {
            EventHandler eventHandler = new EventHandler() {

                @Override
                public void handleEvent(final Event event) {
                    if (false == SessiondEventConstants.TOPIC_LAST_SESSION.equals(event.getTopic())) {
                        return;
                    }

                    ThreadPoolService threadPool = getService(ThreadPoolService.class);
                    if (null == threadPool) {
                        doHandleEvent(event);
                    } else {
                        AbstractTask<Void> t = new AbstractTask<Void>() {

                            @Override
                            public Void call() throws Exception {
                                try {
                                    doHandleEvent(event);
                                } catch (Exception e) {
                                    logger.warn("Handling event {} failed.", event.getTopic(), e);
                                }
                                return null;
                            }
                        };
                        threadPool.submit(t, CallerRunsBehavior.<Void> getInstance());
                    }
                }

                /**
                 * Handles given event.
                 *
                 * @param lastSessionEvent The event
                 */
                protected void doHandleEvent(Event lastSessionEvent) {
                    Integer contextId = (Integer) lastSessionEvent.getProperty(SessiondEventConstants.PROP_CONTEXT_ID);
                    if (null != contextId) {
                        Integer userId = (Integer) lastSessionEvent.getProperty(SessiondEventConstants.PROP_USER_ID);
                        if (null != userId) {
                            cache.dropFor(userId.intValue(), contextId.intValue(), false);
                        }
                    }
                }
            };

            Dictionary<String, Object> serviceProperties = new Hashtable<>(1);
            serviceProperties.put(EventConstants.EVENT_TOPIC, SessiondEventConstants.TOPIC_LAST_SESSION);
            registerService(EventHandler.class, eventHandler, serviceProperties);
        }

        logger.info("Bundle {} successfully started", context.getBundle().getSymbolicName());
    }

    @Override
    protected synchronized void stopBundle() throws Exception {
        Logger logger = org.slf4j.LoggerFactory.getLogger(PushSubscriptionRegistryActivator.class);

        RdbPushSubscriptionRegistryCache cache = this.cache;
        if (null != cache) {
            this.cache = null;
            cache.clear(false);
        }

        super.stopBundle();

        logger.info("Bundle {} successfully stopped", context.getBundle().getSymbolicName());
    }

}
