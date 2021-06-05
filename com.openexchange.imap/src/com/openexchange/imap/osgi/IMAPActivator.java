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

package com.openexchange.imap.osgi;

import java.io.ByteArrayInputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.activation.MailcapCommandMap;
import org.eclipse.osgi.framework.console.CommandProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import com.openexchange.ajax.customizer.folder.AdditionalFolderField;
import com.openexchange.caching.CacheService;
import com.openexchange.caching.events.CacheEventService;
import com.openexchange.charset.CharsetService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.ForcedReloadable;
import com.openexchange.config.Reloadable;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.context.ContextService;
import com.openexchange.database.DatabaseService;
import com.openexchange.folderstorage.FolderService;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.settings.PreferencesItemService;
import com.openexchange.imap.IMAPProvider;
import com.openexchange.imap.cache.ListLsubCache;
import com.openexchange.imap.config.IMAPProperties;
import com.openexchange.imap.config.IMAPReloadable;
import com.openexchange.imap.config.IgnoreDeletedPreferencesItem;
import com.openexchange.imap.osgi.console.ClearListLsubCommandProvider;
import com.openexchange.imap.osgi.console.ListLsubCommandProvider;
import com.openexchange.imap.services.Services;
import com.openexchange.imap.storecache.IMAPStoreCache;
import com.openexchange.imap.threader.references.ConversationCache;
import com.openexchange.imap.util.ExtAccountFolderField;
import com.openexchange.jslob.ConfigTreeEquivalent;
import com.openexchange.log.audit.AuditLogService;
import com.openexchange.mail.FullnameArgument;
import com.openexchange.mail.api.MailProvider;
import com.openexchange.mail.categories.MailCategoriesConfigService;
import com.openexchange.mail.utils.MailFolderUtility;
import com.openexchange.mailaccount.MailAccountDeleteListener;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.openexchange.net.ssl.config.SSLConfigurationService;
import com.openexchange.net.ssl.config.UserAwareSSLConfigurationService;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.push.PushEventConstants;
import com.openexchange.secret.osgi.tools.WhiteboardSecretService;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondEventConstants;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.textxtraction.TextXtractService;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.behavior.CallerRunsBehavior;
import com.openexchange.timer.TimerService;
import com.openexchange.user.UserService;
import com.openexchange.version.VersionService;
import net.htmlparser.jericho.Config;
import net.htmlparser.jericho.LoggerProvider;

/**
 * {@link IMAPActivator} - The {@link BundleActivator activator} for IMAP bundle.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class IMAPActivator extends HousekeepingActivator {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IMAPActivator.class);

    private static final AtomicReference<BundleContext> CONTEXT_REFERENCE = new AtomicReference<>(null);

    /**
     * Gets the optional bundle context.
     *
     * @return The optional bundle context
     */
    public static Optional<BundleContext> getOptionalBundleContext() {
        return Optional.ofNullable(CONTEXT_REFERENCE.get());
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private WhiteboardSecretService secretService;

    /**
     * Initializes a new {@link IMAPActivator}
     */
    public IMAPActivator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] {
            ConfigurationService.class, CacheService.class, CacheEventService.class, UserService.class, MailAccountStorageService.class,
            ThreadPoolService.class, TimerService.class, SessiondService.class, DatabaseService.class, TextXtractService.class,
            EventAdmin.class, GroupService.class, ContextService.class, ConfigViewFactory.class, SSLSocketFactoryProvider.class,
            SSLConfigurationService.class, UserAwareSSLConfigurationService.class, CharsetService.class, VersionService.class };
    }

    @Override
    public void startBundle() throws Exception {
        try {
            CONTEXT_REFERENCE.set(context);
            Services.setServiceLookup(this);
            Config.LoggerProvider = LoggerProvider.DISABLED;
            IMAPStoreCache.initInstance();
            /*
             * Register IMAP mail provider
             */
            {
                Dictionary<String, String> dictionary = new Hashtable<>(1);
                dictionary.put("protocol", IMAPProvider.PROTOCOL_IMAP.toString());
                registerService(MailProvider.class, IMAPProvider.getInstance(), dictionary);
            }
            /*
             * Trackers
             */
            track(MailcapCommandMap.class, new MailcapServiceTracker(context));
            ListLsubInvalidator listLsubInvalidator = new ListLsubInvalidator(context);
            track(CacheEventService.class, listLsubInvalidator);
            trackService(FolderService.class);
            trackService(AuditLogService.class);
            openTrackers();
            /*
             * Command provider
             */
            registerService(CommandProvider.class, new ListLsubCommandProvider());
            registerService(CommandProvider.class, new ClearListLsubCommandProvider());
            registerService(MailAccountDeleteListener.class, listLsubInvalidator);
            registerService(ForcedReloadable.class, new IMAPPropertiesReloader());
            /*
             * Initialize cache regions
             */
            {
                String regionName = ExtAccountFolderField.REGION_NAME;
                byte[] ccf = ("jcs.region."+regionName+"=LTCP\n" +
                    "jcs.region."+regionName+".cacheattributes=org.apache.jcs.engine.CompositeCacheAttributes\n" +
                    "jcs.region."+regionName+".cacheattributes.MaxObjects=100000\n" +
                    "jcs.region."+regionName+".cacheattributes.MemoryCacheName=org.apache.jcs.engine.memory.lru.LRUMemoryCache\n" +
                    "jcs.region."+regionName+".cacheattributes.UseMemoryShrinker=true\n" +
                    "jcs.region."+regionName+".cacheattributes.MaxMemoryIdleTimeSeconds=360\n" +
                    "jcs.region."+regionName+".cacheattributes.ShrinkerIntervalSeconds=60\n" +
                    "jcs.region."+regionName+".elementattributes=org.apache.jcs.engine.ElementAttributes\n" +
                    "jcs.region."+regionName+".elementattributes.IsEternal=false\n" +
                    "jcs.region."+regionName+".elementattributes.MaxLifeSeconds=-1\n" +
                    "jcs.region."+regionName+".elementattributes.IdleTime=360\n" +
                    "jcs.region."+regionName+".elementattributes.IsSpool=false\n" +
                    "jcs.region."+regionName+".elementattributes.IsRemote=false\n" +
                    "jcs.region."+regionName+".elementattributes.IsLateral=false\n").getBytes();
                getService(CacheService.class).loadConfiguration(new ByteArrayInputStream(ccf), true);

                registerService(AdditionalFolderField.class, new ExtAccountFolderField());
            }
            {
                final String regionName = ConversationCache.REGION_NAME;
                final byte[] ccf = ("jcs.region."+regionName+"=\n" + // local only!
                    "jcs.region."+regionName+".cacheattributes=org.apache.jcs.engine.CompositeCacheAttributes\n" +
                    "jcs.region."+regionName+".cacheattributes.MaxObjects=1000000\n" +
                    "jcs.region."+regionName+".cacheattributes.MemoryCacheName=org.apache.jcs.engine.memory.lru.LRUMemoryCache\n" +
                    "jcs.region."+regionName+".cacheattributes.UseMemoryShrinker=true\n" +
                    "jcs.region."+regionName+".cacheattributes.MaxMemoryIdleTimeSeconds=360\n" +
                    "jcs.region."+regionName+".cacheattributes.ShrinkerIntervalSeconds=60\n" +
                    "jcs.region."+regionName+".elementattributes=org.apache.jcs.engine.ElementAttributes\n" +
                    "jcs.region."+regionName+".elementattributes.IsEternal=false\n" +
                    "jcs.region."+regionName+".elementattributes.MaxLifeSeconds=-1\n" +
                    "jcs.region."+regionName+".elementattributes.IdleTime=360\n" +
                    "jcs.region."+regionName+".elementattributes.IsSpool=false\n" +
                    "jcs.region."+regionName+".elementattributes.IsRemote=false\n" +
                    "jcs.region."+regionName+".elementattributes.IsLateral=false\n").getBytes();
                getService(CacheService.class).loadConfiguration(new ByteArrayInputStream(ccf), true);
                ConversationCache.initInstance(this);
            }
            /*
             * Register reloadable
             */
            registerService(Reloadable.class, IMAPReloadable.getInstance());
            /*
             * Register event handler
             */
            {
                EventHandler eventHandler = new EventHandler() {

                    @Override
                    public void handleEvent(Event event) {
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
                                        LOG.warn("Handling event {} failed.", event.getTopic(), e);
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
                                ListLsubCache.dropFor(userId.intValue(), contextId.intValue(), false, false);
                                IMAPStoreCache.getInstance().dropFor(userId.intValue(), contextId.intValue());

                                ConversationCache.getInstance().removeUserConversations(userId.intValue(), contextId.intValue());
                            }
                        }
                    }
                };

                Dictionary<String, Object> serviceProperties = new Hashtable<>(1);
                serviceProperties.put(EventConstants.EVENT_TOPIC, SessiondEventConstants.TOPIC_LAST_SESSION);
                registerService(EventHandler.class, eventHandler, serviceProperties);
            }
            {
                // The mail categories event handler
                EventHandler eventHandler = new EventHandler() {

                    @Override
                    public void handleEvent(Event event) {
                        if (false == MailCategoriesConfigService.TOPIC_REORGANIZE.equals(event.getTopic())) {
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
                                        LOG.warn("Handling event {} failed.", event.getTopic(), e);
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
                     * @param event The event
                     */
                    protected void doHandleEvent(Event event) {
                        Integer contextId = (Integer) event.getProperty(MailCategoriesConfigService.PROP_CONTEXT_ID);
                        if (null != contextId) {
                            Integer userId = (Integer) event.getProperty(MailCategoriesConfigService.PROP_USER_ID);
                            if (null != userId) {
                                ConversationCache.getInstance().removeUserConversations(userId.intValue(), contextId.intValue());
                            }
                        }
                    }
                };

                Dictionary<String, Object> serviceProperties = new Hashtable<>(1);
                serviceProperties.put(EventConstants.EVENT_TOPIC, MailCategoriesConfigService.TOPIC_REORGANIZE);
                registerService(EventHandler.class, eventHandler, serviceProperties);
            }

            {
                EventHandler eventHandler = new EventHandler() {

                    @Override
                    public void handleEvent(Event event) {
                        int contextId = ((Integer) event.getProperty("com.openexchange.passwordchange.contextId")).intValue();
                        int userId = ((Integer) event.getProperty("com.openexchange.passwordchange.userId")).intValue();
                        Session session = (Session) event.getProperty("com.openexchange.passwordchange.session");
                        ListLsubCache.dropFor(session);
                        IMAPStoreCache.getInstance().dropFor(userId, contextId);
                    }

                };

                Dictionary<String, Object> serviceProperties = new Hashtable<>(1);
                serviceProperties.put(EventConstants.EVENT_TOPIC, "com/openexchange/passwordchange");
                registerService(EventHandler.class, eventHandler, serviceProperties);
            }
            {
                EventHandler eventHandler = new EventHandler() {

                    @Override
                    public void handleEvent(Event event) {
                        if (Boolean.TRUE.equals(event.getProperty("__isRemoteEvent"))) { // Remotely received
                            Session session = ((Session) event.getProperty(PushEventConstants.PROPERTY_SESSION));
                            if (null != session) {
                                try {
                                    String folderId = (String) event.getProperty(PushEventConstants.PROPERTY_FOLDER);
                                    FullnameArgument fa = MailFolderUtility.prepareMailFolderParam(folderId);
                                    Boolean contentRelated = (Boolean) event.getProperty(PushEventConstants.PROPERTY_CONTENT_RELATED);
                                    if (null == contentRelated || false == contentRelated.booleanValue()) {
                                        ListLsubCache.clearCache(fa.getAccountId(), session);
                                    }
                                } catch (Exception e) {
                                    LOG.error("Failed to handle event: {}", event.getTopic(), e);
                                }
                            }
                        }
                    }
                };

                Dictionary<String, Object> serviceProperties = new Hashtable<>(1);
                serviceProperties.put(EventConstants.EVENT_TOPIC, PushEventConstants.getAllTopics());
                registerService(EventHandler.class, eventHandler, serviceProperties);
            }

            IgnoreDeletedPreferencesItem ignoreDeleted = new IgnoreDeletedPreferencesItem();
            registerService(ConfigTreeEquivalent.class, ignoreDeleted);
            registerService(PreferencesItemService.class, ignoreDeleted);

        } catch (Exception e) {
            LOG.error("", e);
            throw e;
        }
    }

    @Override
    public void stopBundle() throws Exception {
        try {
            super.stopBundle();
            /*
             * Clear service registry
             */
            ConversationCache.releaseInstance();
            IMAPStoreCache.shutDownInstance();
            Services.setServiceLookup(null);
            CONTEXT_REFERENCE.set(null);
            if (secretService != null) {
                secretService.close();
                secretService = null;
            }
        } catch (Exception e) {
            LOG.error("", e);
            throw e;
        }
    }

    // ----------------------------------------------------- Helper classes ----------------------------------------------------------------

    private static final class IMAPPropertiesReloader implements ForcedReloadable {

        /**
         * Initializes a new {@link IMAPPropertiesReloader}.
         */
        IMAPPropertiesReloader() {
            super();
        }

        @Override
        public void reloadConfiguration(ConfigurationService configService) {
            IMAPProperties.invalidateCache();
        }
    }

}
