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

package com.openexchange.admin.plugin.hosting.osgi;

import com.openexchange.admin.daemons.AdminDaemonService;
import com.openexchange.admin.plugin.hosting.PluginStarter;
import com.openexchange.admin.plugin.hosting.monitoring.Monitor;
import com.openexchange.admin.plugin.hosting.monitoring.MonitorMBean;
import com.openexchange.admin.plugin.hosting.services.AdminServiceRegistry;
import com.openexchange.admin.plugin.hosting.services.PluginInterfaces;
import com.openexchange.admin.plugin.hosting.storage.interfaces.OXContextGroupStorageInterface;
import com.openexchange.admin.plugin.hosting.storage.mysqlStorage.OXContextGroupMySQLStorage;
import com.openexchange.admin.plugins.BasicAuthenticatorPluginInterface;
import com.openexchange.admin.plugins.OXContextPluginInterface;
import com.openexchange.admin.plugins.OXGroupPluginInterface;
import com.openexchange.admin.plugins.OXResourcePluginInterface;
import com.openexchange.admin.plugins.OXUserPluginInterface;
import com.openexchange.admin.tools.AdminCache;
import com.openexchange.caching.CacheService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.context.ContextService;
import com.openexchange.database.DatabaseService;
import com.openexchange.i18n.I18nServiceRegistry;
import com.openexchange.java.Strings;
import com.openexchange.management.ManagementService;
import com.openexchange.management.osgi.HousekeepingManagementTracker;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.osgi.RankingAwareNearRegistryServiceTracker;
import com.openexchange.osgi.RegistryServiceTrackerCustomizer;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.tools.pipesnfilters.PipesAndFiltersService;

public class PluginHostingActivator extends HousekeepingActivator {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PluginHostingActivator.class);

    private PluginStarter starter = null;

    /**
     * Initializes a new {@link PluginHostingActivator}.
     */
    public PluginHostingActivator() {
        super();
    }

    @Override
    public void startBundle() throws Exception {
        AdminCache.compareAndSetBundleContext(null, context);
        final ConfigurationService configurationService = getService(ConfigurationService.class);
        {
            String property = configurationService.getProperty("CREATE_CONTEXT_USE_UNIT");
            if (null != property) {
                property = property.trim();
                if ("context".equalsIgnoreCase(property)) {
                    String sep = Strings.getLineSeparator();
                    LOG.warn("{}{}\tDetected usage of deprecated property \"CREATE_CONTEXT_USE_UNIT\". Please remove that property from '/opt/open-xchange/etc/hosting.properties' file.{}", sep, sep, sep);
                } else {
                    String sep = Strings.getLineSeparator();
                    LOG.warn("{}{}\tDetected usage of deprecated property \"CREATE_CONTEXT_USE_UNIT\", which specifies an unsupported(!) value {}. Please remove that property from '/opt/open-xchange/etc/hosting.properties' file.{}", sep, sep, property, sep);
                }
            }
        }

        AdminCache.compareAndSetConfigurationService(null, configurationService);
        AdminServiceRegistry.getInstance().addService(ConfigurationService.class, configurationService);
        final ConfigViewFactory configViewFactory = getService(ConfigViewFactory.class);
        AdminServiceRegistry.getInstance().addService(ConfigViewFactory.class, configViewFactory);
        track(ThreadPoolService.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), ThreadPoolService.class));
        track(ContextService.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), ContextService.class));
        track(ManagementService.class, new HousekeepingManagementTracker(context, MonitorMBean.MBEAN_NAME, MonitorMBean.MBEAN_DOMAIN, new Monitor()));
        track(PipesAndFiltersService.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), PipesAndFiltersService.class));
        track(CacheService.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), CacheService.class));
        track(DatabaseService.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), DatabaseService.class));
        track(SessiondService.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), SessiondService.class));
        track(I18nServiceRegistry.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), I18nServiceRegistry.class));

        // Register and track
        registerService(OXContextGroupStorageInterface.class, new OXContextGroupMySQLStorage());
        track(OXContextGroupStorageInterface.class, new RegistryServiceTrackerCustomizer<>(context, AdminServiceRegistry.getInstance(), OXContextGroupStorageInterface.class));

        // Plugin interfaces
        {
            final int defaultRanking = 100;

            final RankingAwareNearRegistryServiceTracker<BasicAuthenticatorPluginInterface> batracker = new RankingAwareNearRegistryServiceTracker<>(context, BasicAuthenticatorPluginInterface.class, defaultRanking);
            rememberTracker(batracker);

            final RankingAwareNearRegistryServiceTracker<OXContextPluginInterface> ctracker = new RankingAwareNearRegistryServiceTracker<>(context, OXContextPluginInterface.class, defaultRanking);
            rememberTracker(ctracker);

            final RankingAwareNearRegistryServiceTracker<OXUserPluginInterface> utracker = new RankingAwareNearRegistryServiceTracker<>(context, OXUserPluginInterface.class, defaultRanking);
            rememberTracker(utracker);

            final RankingAwareNearRegistryServiceTracker<OXGroupPluginInterface> gtracker = new RankingAwareNearRegistryServiceTracker<>(context, OXGroupPluginInterface.class, defaultRanking);
            rememberTracker(gtracker);

            final RankingAwareNearRegistryServiceTracker<OXResourcePluginInterface> rtracker = new RankingAwareNearRegistryServiceTracker<>(context, OXResourcePluginInterface.class, defaultRanking);
            rememberTracker(rtracker);

            final PluginInterfaces.Builder builder = new PluginInterfaces.Builder().basicAuthenticatorPlugins(batracker).contextPlugins(ctracker).groupPlugins(gtracker).resourcePlugins(rtracker).userPlugins(utracker);

            PluginInterfaces.setInstance(builder.build());
        }

        // Open trackers
        openTrackers();

        this.starter = new PluginStarter();
        try {
            this.starter.start(context);
        } catch (Exception e) {
            LOG.error("", e);
            throw e;
        }
    }

    @Override
    public void stopBundle() throws Exception {
        final PluginStarter starter = this.starter;
        if (starter != null) {
            starter.stop();
        }
        super.stopBundle();
        PluginInterfaces.setInstance(null);
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { ConfigurationService.class, AdminDaemonService.class, ConfigViewFactory.class };
    }
    
}
