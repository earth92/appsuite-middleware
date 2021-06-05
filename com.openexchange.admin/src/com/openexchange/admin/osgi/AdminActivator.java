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

package com.openexchange.admin.osgi;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.osgi.Tools.withRanking;
import java.rmi.Remote;
import java.util.Dictionary;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import com.openexchange.admin.daemons.AdminDaemon;
import com.openexchange.admin.daemons.ClientAdminThread;
import com.openexchange.admin.daemons.ClientAdminThreadExtended;
import com.openexchange.admin.exceptions.OXGenericException;
import com.openexchange.admin.mysql.CreateAttachmentTables;
import com.openexchange.admin.mysql.CreateContactsTables;
import com.openexchange.admin.mysql.CreateInfostoreTables;
import com.openexchange.admin.mysql.CreateLdap2SqlTables;
import com.openexchange.admin.mysql.CreateMiscTables;
import com.openexchange.admin.mysql.CreateOXFolderTables;
import com.openexchange.admin.mysql.CreateSequencesTables;
import com.openexchange.admin.mysql.CreateSettingsTables;
import com.openexchange.admin.mysql.CreateVirtualFolderTables;
import com.openexchange.admin.plugins.BasicAuthenticatorPluginInterface;
import com.openexchange.admin.plugins.ContextDbLookupPluginInterface;
import com.openexchange.admin.plugins.OXContextPluginInterface;
import com.openexchange.admin.plugins.OXGroupPluginInterface;
import com.openexchange.admin.plugins.OXResourcePluginInterface;
import com.openexchange.admin.plugins.OXUserPluginInterface;
import com.openexchange.admin.plugins.UserServiceInterceptorBridge;
import com.openexchange.admin.services.AdminServiceRegistry;
import com.openexchange.admin.services.PluginInterfaces;
import com.openexchange.admin.taskmanagement.TaskManager;
import com.openexchange.admin.tools.AdminCache;
import com.openexchange.admin.tools.AdminCacheExtended;
import com.openexchange.admin.tools.PropertyHandlerExtended;
import com.openexchange.admin.tools.filestore.osgi.FilestoreDataMoveListenerTracker;
import com.openexchange.auth.Authenticator;
import com.openexchange.caching.CacheService;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.Reloadable;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.context.ContextService;
import com.openexchange.database.CreateTableService;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.JdbcProperties;
import com.openexchange.filestore.FileStorageUnregisterListenerRegistry;
import com.openexchange.groupware.filestore.FileLocationHandler;
import com.openexchange.groupware.userconfiguration.PermissionConfigurationChecker;
import com.openexchange.imagetransformation.ImageMetadataService;
import com.openexchange.imagetransformation.ImageTransformationService;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.osgi.RankingAwareNearRegistryServiceTracker;
import com.openexchange.osgi.RankingAwareRegistryServiceTrackerCustomizer;
import com.openexchange.osgi.RegistryServiceTrackerCustomizer;
import com.openexchange.password.mechanism.PasswordMechRegistry;
import com.openexchange.pluginsloaded.PluginsLoadedService;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.snippet.QuotaAwareSnippetService;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.timer.TimerService;
import com.openexchange.tools.pipesnfilters.PipesAndFiltersService;
import com.openexchange.user.UserService;
import com.openexchange.user.interceptor.UserServiceInterceptor;
import com.openexchange.user.interceptor.UserServiceInterceptorRegistry;
import com.openexchange.version.VersionService;

public class AdminActivator extends HousekeepingActivator {

    private AdminDaemon daemon;

    /**
     * Initializes a new {@link AdminActivator}.
     */
    public AdminActivator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { ConfigurationService.class, ThreadPoolService.class, PasswordMechRegistry.class, VersionService.class, JdbcProperties.class };
    }

    @Override
    public synchronized void startBundle() throws Exception {
        final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminActivator.class);
        AdminServiceRegistry.getInstance().addService(ThreadPoolService.class, getService(ThreadPoolService.class));
        AdminServiceRegistry.getInstance().addService(JdbcProperties.class, getService(JdbcProperties.class));

        track(PasswordMechRegistry.class, new RegistryServiceTrackerCustomizer<PasswordMechRegistry>(context, AdminServiceRegistry.getInstance(), PasswordMechRegistry.class));
        track(PipesAndFiltersService.class, new RegistryServiceTrackerCustomizer<PipesAndFiltersService>(context, AdminServiceRegistry.getInstance(), PipesAndFiltersService.class));
        track(ContextService.class, new RegistryServiceTrackerCustomizer<ContextService>(context, AdminServiceRegistry.getInstance(), ContextService.class));

        track(TimerService.class, new RegistryServiceTrackerCustomizer<TimerService>(context, AdminServiceRegistry.getInstance(), TimerService.class) {
            @Override
            protected void serviceAcquired(TimerService timerService) {
                TaskManager.getInstance().startCleaner(timerService);
            }
        });

        track(MailAccountStorageService.class, new RegistryServiceTrackerCustomizer<MailAccountStorageService>(context, AdminServiceRegistry.getInstance(), MailAccountStorageService.class));
        track(ConfigViewFactory.class, new RegistryServiceTrackerCustomizer<ConfigViewFactory>(context, AdminServiceRegistry.getInstance(), ConfigViewFactory.class));
        AdminCache.compareAndSetBundleContext(null, context);
        final ConfigurationService configurationService = getService(ConfigurationService.class);
        AdminCache.compareAndSetConfigurationService(null, configurationService);
        AdminServiceRegistry.getInstance().addService(ConfigurationService.class, configurationService);

        track(CreateTableService.class, new CreateTableCustomizer(context));
        track(CacheService.class, new RegistryServiceTrackerCustomizer<CacheService>(context, AdminServiceRegistry.getInstance(), CacheService.class));
        track(CapabilityService.class, new RegistryServiceTrackerCustomizer<CapabilityService>(context, AdminServiceRegistry.getInstance(), CapabilityService.class));
        track(SessiondService.class, new RegistryServiceTrackerCustomizer<SessiondService>(context, AdminServiceRegistry.getInstance(), SessiondService.class));
        track(PermissionConfigurationChecker.class, new RegistryServiceTrackerCustomizer<PermissionConfigurationChecker>(context, AdminServiceRegistry.getInstance(), PermissionConfigurationChecker.class));
        track(Remote.class, new OXContextInterfaceTracker(context)).open();
        UserServiceInterceptorRegistry interceptorRegistry = new UserServiceInterceptorRegistry(context);
        track(UserServiceInterceptor.class, interceptorRegistry);
        track(UserService.class, new RegistryServiceTrackerCustomizer<UserService>(context, AdminServiceRegistry.getInstance(), UserService.class));
        track(ImageTransformationService.class, new RegistryServiceTrackerCustomizer<ImageTransformationService>(context, AdminServiceRegistry.getInstance(), ImageTransformationService.class));
        track(ImageMetadataService.class, new RegistryServiceTrackerCustomizer<ImageMetadataService>(context, AdminServiceRegistry.getInstance(), ImageMetadataService.class));
        track(FileStorageUnregisterListenerRegistry.class, new RegistryServiceTrackerCustomizer<FileStorageUnregisterListenerRegistry>(context, AdminServiceRegistry.getInstance(), FileStorageUnregisterListenerRegistry.class));
        track(PluginsLoadedService.class, new RegistryServiceTrackerCustomizer<PluginsLoadedService>(context, AdminServiceRegistry.getInstance(), PluginsLoadedService.class));
        track(QuotaAwareSnippetService.class, new RankingAwareRegistryServiceTrackerCustomizer<QuotaAwareSnippetService>(context, AdminServiceRegistry.getInstance(), QuotaAwareSnippetService.class));

        // Plugin interfaces
        {
            final int defaultRanking = 100;

            final RankingAwareNearRegistryServiceTracker<BasicAuthenticatorPluginInterface> batracker = new RankingAwareNearRegistryServiceTracker<BasicAuthenticatorPluginInterface>(context, BasicAuthenticatorPluginInterface.class, defaultRanking);
            rememberTracker(batracker);

            final RankingAwareNearRegistryServiceTracker<OXContextPluginInterface> ctracker = new RankingAwareNearRegistryServiceTracker<OXContextPluginInterface>(context, OXContextPluginInterface.class, defaultRanking);
            rememberTracker(ctracker);

            final RankingAwareNearRegistryServiceTracker<OXUserPluginInterface> utracker = new RankingAwareNearRegistryServiceTracker<OXUserPluginInterface>(context, OXUserPluginInterface.class, defaultRanking);
            rememberTracker(utracker);

            final RankingAwareNearRegistryServiceTracker<OXGroupPluginInterface> gtracker = new RankingAwareNearRegistryServiceTracker<OXGroupPluginInterface>(context, OXGroupPluginInterface.class, defaultRanking);
            rememberTracker(gtracker);

            final RankingAwareNearRegistryServiceTracker<OXResourcePluginInterface> rtracker = new RankingAwareNearRegistryServiceTracker<OXResourcePluginInterface>(context, OXResourcePluginInterface.class, defaultRanking);
            rememberTracker(rtracker);

            final RankingAwareNearRegistryServiceTracker<ContextDbLookupPluginInterface> dbtracker = new RankingAwareNearRegistryServiceTracker<ContextDbLookupPluginInterface>(context, ContextDbLookupPluginInterface.class, defaultRanking);
            rememberTracker(dbtracker);

            final PluginInterfaces.Builder builder = new PluginInterfaces.Builder().basicAuthenticatorPlugins(batracker).contextPlugins(ctracker).groupPlugins(gtracker).resourcePlugins(rtracker).userPlugins(utracker).dbLookupPlugins(dbtracker);

            PluginInterfaces.setInstance(builder.build());
        }

        // FilestoreDataMoveListener
        FilestoreDataMoveListenerTracker dataMoveListenerTracker = new FilestoreDataMoveListenerTracker(context);
        rememberTracker(dataMoveListenerTracker);
        com.openexchange.admin.tools.filestore.FilestoreDataMover.setListeners(dataMoveListenerTracker);

        track(FileLocationHandler.class, new FilestoreLocationUpdaterCustomizer(context));

        log.info("Starting Admindaemon...");
        final AdminDaemon daemon = new AdminDaemon();
        this.daemon = daemon;
        daemon.getCurrentBundleStatus(context);
        daemon.registerBundleListener(context);
        try {
            AdminDaemon.initCache(configurationService);
            daemon.initAccessCombinationsInCache();
        } catch (OXGenericException e) {
            log.error("", e);
            throw e;
        } catch (ClassNotFoundException e) {
            log.error("", e);
            throw e;
        }

        {
            AdminCache.compareAndSetBundleContext(null, context);
            AdminCache.compareAndSetConfigurationService(null, configurationService);

            PropertyHandlerExtended prop = initCache(configurationService, log);
            log.debug("Loading context implementation: {}", prop.getProp(PropertyHandlerExtended.CONTEXT_STORAGE, null));
            log.debug("Loading util implementation: {}", prop.getProp(PropertyHandlerExtended.UTIL_STORAGE, null));
        }

        track(DatabaseService.class, new DatabaseServiceCustomizer(context, ClientAdminThread.cache.getPool())).open();
        track(DatabaseService.class, new DatabaseServiceCustomizer(context, ClientAdminThreadExtended.cache.getPool())).open();
        track(DatabaseService.class, new AdminDaemonInitializer(daemon, context)).open();

        // Open trackers
        openTrackers();

        {
            final Dictionary<?, ?> headers = context.getBundle().getHeaders();
            log.info("Version: {}", headers.get("Bundle-Version"));
            log.info("Name: {}", headers.get("Bundle-SymbolicName"));
        }
        log.info("Build: {}", getServiceSafe(VersionService.class).getVersionString());
        log.info("Admindaemon successfully started.");

        // The listener, which is called if a new plugin is registered
        {
            ServiceListener sl = new ServiceListener() {
                @Override
                public void serviceChanged(ServiceEvent ev) {
                    String symbolicName = ev.getServiceReference().getBundle().getSymbolicName();
                    log.info("Service: {}, {}", symbolicName, I(ev.getType()));
                    switch (ev.getType()) {
                        case ServiceEvent.REGISTERED:
                            log.info("{} registered service", symbolicName);
                            break;
                        default:
                            break;
                    }
                }
            };
            String filter = "(objectclass=" + OXUserPluginInterface.class.getName() + ")";
            try {
                context.addServiceListener(sl, filter);
            } catch (InvalidSyntaxException e) {
                log.warn("Filed adding service listener", e);
            }
        }

        // UserServiceInterceptor Bridge
        {
            Dictionary<String, Object> props = withRanking(200);
            props.put("name", "OXUser");
            registerService(OXUserPluginInterface.class, new UserServiceInterceptorBridge(interceptorRegistry), props);
        }

        //Register CreateTableServices
        registerService(CreateTableService.class, new CreateSequencesTables());
        registerService(CreateTableService.class, new CreateLdap2SqlTables());
        registerService(CreateTableService.class, new CreateOXFolderTables());
        registerService(CreateTableService.class, new CreateVirtualFolderTables());
        registerService(CreateTableService.class, new CreateSettingsTables());
        registerService(CreateTableService.class, new CreateContactsTables());
        registerService(CreateTableService.class, new CreateInfostoreTables());
        registerService(CreateTableService.class, new CreateAttachmentTables());
        registerService(CreateTableService.class, new CreateMiscTables());

        // Register authenticator
        AuthenticatorImpl authenticator = new AuthenticatorImpl();
        registerService(Authenticator.class, authenticator);
        registerService(Reloadable.class, authenticator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stopBundle() throws Exception {
        {
            TaskManager taskManager = TaskManager.getInstance();
            if (null != taskManager) {
                taskManager.shutdown();
            }
        }

        com.openexchange.admin.tools.filestore.FilestoreDataMover.setListeners(null);
        PluginInterfaces.setInstance(null);
        final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminActivator.class);
        log.info("Stopping RMI...");
        final AdminDaemon daemon = this.daemon;
        if (null != daemon) {
            daemon.unregisterRMI(context);
            this.daemon = null;
        }

        AdminServiceRegistry.getInstance().removeService(JdbcProperties.class);
        AdminServiceRegistry.getInstance().removeService(ThreadPoolService.class);

        super.stopBundle();
    }

    private PropertyHandlerExtended initCache(ConfigurationService service, org.slf4j.Logger logger) throws OXGenericException {
        AdminCacheExtended cache = new AdminCacheExtended();
        cache.initCache(service);
        cache.initCacheExtended();
        ClientAdminThreadExtended.cache = cache;
        PropertyHandlerExtended prop = cache.getProperties();
        logger.info("Cache and Pools initialized!");
        return prop;
    }

}
