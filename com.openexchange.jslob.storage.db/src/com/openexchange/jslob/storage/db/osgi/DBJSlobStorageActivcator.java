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

package com.openexchange.jslob.storage.db.osgi;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.database.CreateTableService;
import com.openexchange.database.DatabaseService;
import com.openexchange.groupware.delete.DeleteListener;
import com.openexchange.groupware.update.DefaultUpdateTaskProviderService;
import com.openexchange.groupware.update.UpdateTaskProviderService;
import com.openexchange.java.Streams;
import com.openexchange.jslob.JSlobService;
import com.openexchange.jslob.storage.JSlobStorage;
import com.openexchange.jslob.storage.db.DBJSlobStorage;
import com.openexchange.jslob.storage.db.Services;
import com.openexchange.jslob.storage.db.cache.CachingJSlobStorage;
import com.openexchange.jslob.storage.db.cache.Constants;
import com.openexchange.jslob.storage.db.groupware.DBJSlobCreateTableService;
import com.openexchange.jslob.storage.db.groupware.DBJSlobCreateTableTask;
import com.openexchange.jslob.storage.db.groupware.DBJSlobIncreaseBlobSizeTask;
import com.openexchange.jslob.storage.db.groupware.JSlobDBDeleteListener;
import com.openexchange.jslob.storage.db.groupware.JsonStorageTableUtf8Mb4UpdateTask;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondEventConstants;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;

/**
 * {@link DBJSlobStorageActivcator}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class DBJSlobStorageActivcator extends HousekeepingActivator {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DBJSlobStorageActivcator.class);

    /** List of known JSlobService identifiers */
    public static final List<String> SERVICE_IDS = new CopyOnWriteArrayList<String>();

    /**
     * Initializes a new {@link DBJSlobStorageActivcator}.
     */
    public DBJSlobStorageActivcator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { DatabaseService.class, ConfigurationService.class, ThreadPoolService.class };
    }

    @Override
    protected void startBundle() throws Exception {
        LOG.info("Starting bundle: com.openexchange.jslob.storage.db");
        try {
            Services.setServices(this);
            DBJSlobStorage dbJSlobStorage = new DBJSlobStorage(this);
            final CachingJSlobStorage cachingJSlobStorage = CachingJSlobStorage.initialize(dbJSlobStorage);
            registerService(JSlobStorage.class, cachingJSlobStorage);
            /*
             * Register services for table creation
             */
            registerService(CreateTableService.class, new DBJSlobCreateTableService());
            registerService(UpdateTaskProviderService.class, new DefaultUpdateTaskProviderService(new DBJSlobCreateTableTask(), new DBJSlobIncreaseBlobSizeTask(), new JsonStorageTableUtf8Mb4UpdateTask()));
            /*
             * Register delete listener
             */
            registerService(DeleteListener.class, new JSlobDBDeleteListener(dbJSlobStorage), null);
            /*
             * Add tracker for cache service
             */
            final BundleContext context = this.context;
            {
                final String regionName = Constants.REGION_NAME;
                final ServiceTrackerCustomizer<CacheService, CacheService> stc =
                    new ServiceTrackerCustomizer<CacheService, CacheService>() {

                        @Override
                        public CacheService addingService(final ServiceReference<CacheService> reference) {
                            final CacheService cacheService = context.getService(reference);
                            try {
                                final byte[] ccf =
                                    ("jcs.region." + regionName + "=\n"
                                   + "jcs.region." + regionName + ".cacheattributes=org.apache.jcs.engine.CompositeCacheAttributes\n"
                                   + "jcs.region." + regionName + ".cacheattributes.MaxObjects=10000000\n"
                                   + "jcs.region." + regionName + ".cacheattributes.MemoryCacheName=org.apache.jcs.engine.memory.lru.LRUMemoryCache\n"
                                   + "jcs.region." + regionName + ".cacheattributes.UseMemoryShrinker=true\n"
                                   + "jcs.region." + regionName + ".cacheattributes.MaxMemoryIdleTimeSeconds=360\n"
                                   + "jcs.region." + regionName + ".cacheattributes.ShrinkerIntervalSeconds=60\n"
                                   + "jcs.region." + regionName + ".elementattributes=org.apache.jcs.engine.ElementAttributes\n"
                                   + "jcs.region." + regionName + ".elementattributes.IsEternal=false\n"
                                   + "jcs.region." + regionName + ".elementattributes.MaxLifeSeconds=-1\n"
                                   + "jcs.region." + regionName + ".elementattributes.IdleTime=360\n"
                                   + "jcs.region." + regionName + ".elementattributes.IsSpool=false\n"
                                   + "jcs.region." + regionName + ".elementattributes.IsRemote=false\n"
                                   + "jcs.region." + regionName + ".elementattributes.IsLateral=false\n").getBytes();
                                cacheService.loadConfiguration(Streams.newByteArrayInputStream(ccf));
                                addService(CacheService.class, cacheService);
                                CachingJSlobStorage.setCacheService(cacheService);
                                return cacheService;
                            } catch (Exception e) {
                                LOG.error("Starting up cache for 'com.openexchange.jslob.storage.db' failed.", e);
                            }
                            context.ungetService(reference);
                            return null;
                        }

                        @Override
                        public void modifiedService(final ServiceReference<CacheService> reference, final CacheService service) {
                            // Ignore
                        }

                        @Override
                        public void removedService(final ServiceReference<CacheService> reference, final CacheService service) {
                            if (null != service) {
                                try {
                                    CachingJSlobStorage.setCacheService(null);
                                    removeService(CacheService.class);
                                    final Cache cache = service.getCache(regionName);
                                    cache.clear();
                                    cache.dispose();
                                } catch (Exception e) {
                                    LOG.error("Stopping cache for 'com.openexchange.jslob.storage.db' failed.", e);
                                }
                            }
                        }

                    };
                track(CacheService.class, stc);
            }
            /*
             * Add tracker for JSlobService instances
             */
            {
                final ServiceTrackerCustomizer<JSlobService, JSlobService> stc =
                    new ServiceTrackerCustomizer<JSlobService, JSlobService>() {

                        @Override
                        public void removedService(final ServiceReference<JSlobService> reference, final JSlobService service) {
                            if (null != service) {
                                context.ungetService(reference);
                            }
                        }

                        @Override
                        public void modifiedService(final ServiceReference<JSlobService> reference, final JSlobService service) {
                            // ignore
                        }

                        @Override
                        public JSlobService addingService(final ServiceReference<JSlobService> reference) {
                            final JSlobService service = context.getService(reference);
                            SERVICE_IDS.add(service.getIdentifier());
                            return null;
                        }
                    };
                track(JSlobService.class, stc);
            }
            openTrackers();

            {
                EventHandler eventHandler = new EventHandler() {

                    @Override
                    public void handleEvent(final Event event) {
                        String topic = event.getTopic();
                        if (SessiondEventConstants.TOPIC_REMOVE_CONTAINER.equals(topic) || SessiondEventConstants.TOPIC_REMOVE_DATA.equals(topic)) {
                            Map<String, Session> container = (Map<String, Session>) event.getProperty(SessiondEventConstants.PROP_CONTAINER);
                            ThreadPoolService threadPool = DBJSlobStorageActivcator.this.getService(ThreadPoolService.class);
                            if (null == threadPool) {
                                doHandleSessionContainer(container);
                            } else {
                                threadPool.submit(new AbstractTask<Void>() {

                                    @Override
                                    public Void call() throws Exception {
                                        doHandleSessionContainer(container);
                                        return null;
                                    }
                                });
                            }
                        } else if (SessiondEventConstants.TOPIC_REMOVE_SESSION.equals(topic)) {
                            Session session = (Session) event.getProperty(SessiondEventConstants.PROP_SESSION);
                            cachingJSlobStorage.dropAllUserJSlobs(session.getUserId(), session.getContextId());
                        }
                    }

                    void doHandleSessionContainer(Map<String, Session> container) {
                        Set<UsID> set = new HashSet<UsID>(container.size());
                        for (Session session : container.values()) {
                            if (!session.isTransient()) {
                                int contextId = session.getContextId();
                                int userId = session.getUserId();
                                if (set.add(new UsID(userId, contextId))) {
                                    cachingJSlobStorage.dropAllUserJSlobs(userId, contextId);
                                }
                            }
                        }
                    }
                };

                Dictionary<String, Object> props = new Hashtable<String, Object>(2);
                props.put(EventConstants.EVENT_TOPIC, SessiondEventConstants.getAllTopics());
                registerService(EventHandler.class, eventHandler, props);
            }
        } catch (Exception e) {
            LOG.error("Starting bundle \"com.openexchange.jslob.storage.db\" failed", e);
            throw e;
        }
    }

    @Override
    public <S> boolean addService(final Class<S> clazz, final S service) {
        return super.addService(clazz, service);
    }

    @Override
    public <S> boolean removeService(final Class<? extends S> clazz) {
        return super.removeService(clazz);
    }

    @Override
    protected void stopBundle() throws Exception {
        LOG.info("Stopping bundle: com.openexchange.jslob.storage.db");
        try {
            CachingJSlobStorage.shutdown();
            super.stopBundle();
        } catch (Exception e) {
            LOG.error("Stopping bundle \"com.openexchange.jslob.storage.db\" failed", e);
            throw e;
        } finally {
            Services.setServices(null);
        }
    }

    // ------------------------------------------------------------------------------------------------------------------

    static final class UsID {

        final int userId;
        final int contextId;
        private final int hash;

        UsID(int userId, int contextId) {
            super();
            this.userId = userId;
            this.contextId = contextId;

            int prime = 31;
            int result = 1;
            result = prime * result + contextId;
            result = prime * result + userId;
            this.hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof UsID)) {
                return false;
            }
            UsID other = (UsID) obj;
            if (contextId != other.contextId) {
                return false;
            }
            if (userId != other.userId) {
                return false;
            }
            return true;
        }
    }

}
