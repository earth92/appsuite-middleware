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

package com.openexchange.logging.osgi;

import java.net.URL;
import java.rmi.Remote;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import com.openexchange.ajax.response.IncludeStackTraceService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.logging.LogConfigurationService;
import com.openexchange.logging.LogLevelService;
import com.openexchange.logging.filter.ParamsCheckingTurboFilter;
import com.openexchange.logging.filter.RankingAwareTurboFilterList;
import com.openexchange.logging.internal.IncludeStackTraceServiceImpl;
import com.openexchange.logging.internal.LogLevelServiceImpl;
import com.openexchange.logging.internal.LogbackConfigurationRMIServiceImpl;
import com.openexchange.logging.internal.LogbackLogConfigurationService;
import com.openexchange.management.ManagementService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.StatusListenerAsList;
import ch.qos.logback.core.status.StatusManager;

/**
 * {@link LoggingActivator}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class LoggingActivator implements BundleActivator, Reloadable {

    /** The logger */
    protected static Logger LOGGER = LoggerFactory.getLogger(LoggingActivator.class);

    protected static final String LOGIN_PERFORMER = "com.openexchange.login.internal.LoginPerformer";
    protected static final String SESSION_HANDLER = "com.openexchange.sessiond.impl.SessionHandler";

    // ----------------------------------------------------------------------------------- //

    private ServiceTracker<ManagementService, ManagementService> deprecatedLogstashManagementTracker;
    private ServiceTracker<ManagementService, ManagementService> logstashManagementTracker;
    private ServiceTracker<ManagementService, ManagementService> kafkaManagementTracker;

    private ServiceTracker<ConfigurationService, ConfigurationService> configurationTracker;
    private RankingAwareTurboFilterList rankingAwareTurboFilterList;
    private ServiceRegistration<IncludeStackTraceService> includeStackTraceServiceRegistration;
    private ServiceRegistration<Reloadable> reloadable;
    private ServiceRegistration<LogLevelService> logLevelService;
    private LogbackConfigurationRMIServiceImpl logbackConfigurationRMIService;
    private LogbackLogConfigurationService logbackConfigService;

    /*
     * Do not implement HousekeepingActivator, track services if you need them!
     * This bundle must start as early as possible to configure the java.util.logging bridge.
     */
    public LoggingActivator() {
        super();
    }

    protected void configureJavaUtilLogging() {
        // We configure a special j.u.l handler that routes logging to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @Override
    public synchronized void start(BundleContext context) throws Exception {
        LOGGER.info("starting bundle com.openexchange.logging");

        // Obtain logger context
        final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        // Mark first as "DEFAULT"
        if (!loggerContext.getTurboFilterList().isEmpty()) {
            final TurboFilter filter = loggerContext.getTurboFilterList().get(0);
            if (null == filter.getName()) {
                filter.setName("DEFAULT");
            }
        }

        // Initialization stuff for JUL/JCL bridges
        configureJavaUtilLogging();
        overrideLoggerLevels(loggerContext);
        installJulLevelChangePropagator(loggerContext);

        // The ranking-aware turbo filter list - itself acting as a turbo filter
        RankingAwareTurboFilterList rankingAwareTurboFilterList = initialiseRankingAwareTurboFilterList(loggerContext);

        // Add static turbo filters
        rankingAwareTurboFilterList.addTurboFilter(new ParamsCheckingTurboFilter());

        // Register services
        final IncludeStackTraceServiceImpl stackTraceService = new IncludeStackTraceServiceImpl();
        registerRemoteAppenderMBeansTrackers(context);
        registerExceptionCategoryFilter(context, rankingAwareTurboFilterList, stackTraceService);
        registerIncludeStackTraceService(stackTraceService, context);
        reloadable = context.registerService(Reloadable.class, this, null);

        logbackConfigService = new LogbackLogConfigurationService(loggerContext, rankingAwareTurboFilterList, stackTraceService);
        context.registerService(LogConfigurationService.class, logbackConfigService, null);

        // Register RMI logback config service
        logbackConfigurationRMIService = new LogbackConfigurationRMIServiceImpl(logbackConfigService);
        Dictionary<String, Object> serviceProperties = new Hashtable<String, Object>(1);
        serviceProperties.put("RMI_NAME", LogbackConfigurationRMIServiceImpl.RMI_NAME);
        context.registerService(Remote.class, logbackConfigurationRMIService, serviceProperties);

        logLevelService = context.registerService(LogLevelService.class, new LogLevelServiceImpl(), null);
    }

    @Override
    public synchronized void stop(BundleContext context) throws Exception {
        LOGGER.info("stopping bundle com.openexchange.logging");

        final ServiceTracker<ManagementService, ManagementService> kafkaManagementTracker = this.kafkaManagementTracker;
        if (null != kafkaManagementTracker) {
            kafkaManagementTracker.close();
            this.kafkaManagementTracker = null;
        }

        final ServiceTracker<ManagementService, ManagementService> logstashManagementTracker = this.logstashManagementTracker;
        if (null != logstashManagementTracker) {
            logstashManagementTracker.close();
        }

        final ServiceTracker<ManagementService, ManagementService> deprecatedLogstashManagementTracker = this.deprecatedLogstashManagementTracker;
        if (null != deprecatedLogstashManagementTracker) {
            deprecatedLogstashManagementTracker.close();
        }

        final ServiceTracker<ConfigurationService, ConfigurationService> configurationTracker = this.configurationTracker;
        if (null != configurationTracker) {
            configurationTracker.close();
            this.configurationTracker = null;
        }

        final RankingAwareTurboFilterList rankingAwareTurboFilterList = this.rankingAwareTurboFilterList;
        if (null != rankingAwareTurboFilterList) {
            // Obtain logger context
            final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getTurboFilterList().remove(rankingAwareTurboFilterList);
            rankingAwareTurboFilterList.clear();
            this.rankingAwareTurboFilterList = null;
        }

        final ServiceRegistration<IncludeStackTraceService> includeStackTraceServiceRegistration = this.includeStackTraceServiceRegistration;
        if (null != includeStackTraceServiceRegistration) {
            includeStackTraceServiceRegistration.unregister();
            this.includeStackTraceServiceRegistration = null;
        }

        if (null != reloadable) {
            reloadable.unregister();
            reloadable = null;
        }

        if (logLevelService != null) {
            logLevelService.unregister();
            logLevelService = null;
        }
        if (logbackConfigService != null) {
            logbackConfigService.dispose();
        }
    }

    /**
     * Installs the logback LevelChangePropagator if the descriptive configuration was removed by the admin.
     */
    protected synchronized void installJulLevelChangePropagator(final LoggerContext loggerContext) {
        List<LoggerContextListener> loggerContextListener = loggerContext.getCopyOfListenerList();

        if (!hasInstanceOf(loggerContextListener, LevelChangePropagator.class)) {
            LevelChangePropagator levelChangePropagator = new LevelChangePropagator();
            levelChangePropagator.setContext(loggerContext);
            levelChangePropagator.start();
            loggerContext.addListener(levelChangePropagator);
        }
    }

    /**
     * Checks, if the given class is in the given collection.
     *
     * @param collection - the collection to verify
     * @param clazz - the class to search for
     * @return true, if an object of that class is available within the collection. Otherwise false.
     */
    protected <T> boolean hasInstanceOf(Collection<?> collection, Class<T> clazz) {
        if (collection == null) {
            throw new IllegalArgumentException("The collection is null");
        }

        for (Object o : collection) {
            if (o != null && o.getClass() == clazz) {
                return true;
            }
        }
        return false;
    }

    /**
     * Overrides the log level for LoginPerformer and SessionHandler in case that the administrator removed or changed the logging level to
     * coarser settings. If the settings are finer (TRACE, DEBUG, ALL) this level will not be overridden.
     */
    protected synchronized void overrideLoggerLevels(final LoggerContext loggerContext) {
        String disableOverrideLogLevels = loggerContext.getProperty("com.openexchange.logging.disableOverrideLogLevels");
        if ("true".equalsIgnoreCase(disableOverrideLogLevels)) {
            return;
        }

        for (final String className : new String[] { LOGIN_PERFORMER, SESSION_HANDLER }) {
            ch.qos.logback.classic.Logger lLogger = loggerContext.getLogger(className);

            if (lLogger != null) {
                Level level = lLogger.getLevel();

                if (level == null || level.isGreaterOrEqual(Level.WARN)) {
                    lLogger.setLevel(Level.INFO);
                    LOGGER.info("Configured log level {} for class {} is too coarse. It is changed to INFO!", level, className);
                }
            } else {
                LOGGER.warn("Not able to check (and set) the log level to INFO for class: {}", className);
            }
        }
    }

    /**
     * Initialise the {@link RankingAwareTurboFilterList} and register itself acting as a turbo filter
     *
     * @param loggerContext The {@link LoggerContext}
     */
    private RankingAwareTurboFilterList initialiseRankingAwareTurboFilterList(LoggerContext loggerContext) {
        final RankingAwareTurboFilterList rankingAwareTurboFilterList = new RankingAwareTurboFilterList();
        this.rankingAwareTurboFilterList = rankingAwareTurboFilterList;
        loggerContext.addTurboFilter(rankingAwareTurboFilterList);
        return rankingAwareTurboFilterList;
    }

    /**
     * Register remote appender mbeans
     *
     * @param context The bundle context
     */
    protected synchronized void registerRemoteAppenderMBeansTrackers(BundleContext context) {
        this.kafkaManagementTracker = registerRemoteAppenderMBeanTracker(context, new KafkaAppenderMBeanRegisterer(context));
        this.logstashManagementTracker = registerRemoteAppenderMBeanTracker(context, new LogstashAppenderMBeanRegisterer(context));
        registerDeprecatedLogstashAppenderMBeanTracker(context);
    }

    /**
     * Registers the {@link DeprecatedLogstashSocketAppenderMBeanRegisterer}
     */
    @SuppressWarnings("deprecation")
    protected synchronized void registerDeprecatedLogstashAppenderMBeanTracker(BundleContext context) {
        try {
            DeprecatedLogstashSocketAppenderMBeanRegisterer logstashAppenderRegistration = new DeprecatedLogstashSocketAppenderMBeanRegisterer(context);
            ServiceTracker<ManagementService, ManagementService> tracker = new ServiceTracker<ManagementService, ManagementService>(context, ManagementService.class, logstashAppenderRegistration);
            this.deprecatedLogstashManagementTracker = tracker;
            tracker.open();
        } catch (Exception e) {
            LOGGER.error("Failed to track LogstashAppenderMBean registerer.", e);
        }
    }

    /**
     * Creates a tracker for the specified remote appender mbean registerer
     *
     * @param context The bundle context
     * @param registerer The registerer
     * @return The service tracker
     */
    private synchronized ServiceTracker<ManagementService, ManagementService> registerRemoteAppenderMBeanTracker(BundleContext context, AbstractRemoteAppenderMBeanRegisterer registerer) {
        ServiceTracker<ManagementService, ManagementService> tracker = new ServiceTracker<ManagementService, ManagementService>(context, ManagementService.class, registerer);
        tracker.open();
        return tracker;
    }

    /**
     * Register the exception category filter
     *
     * @param context The bundle context
     * @param turboFilterList The ranking aware turbo filter list
     * @param serviceImpl The include stack trace service
     */
    protected synchronized void registerExceptionCategoryFilter(final BundleContext context, final RankingAwareTurboFilterList turboFilterList, IncludeStackTraceServiceImpl serviceImpl) {
        ExceptionCategoryFilterRegisterer exceptionCategoryFilterRegisterer = new ExceptionCategoryFilterRegisterer(context, turboFilterList, serviceImpl);
        context.registerService(Reloadable.class, exceptionCategoryFilterRegisterer, null);
        final ServiceTracker<ConfigurationService, ConfigurationService> tracker = new ServiceTracker<ConfigurationService, ConfigurationService>(context, ConfigurationService.class, exceptionCategoryFilterRegisterer);
        configurationTracker = tracker;
        tracker.open();
    }

    /**
     * Register the include stacktrace service
     *
     * @param serviceImpl The implementation
     * @param context The bundle context
     */
    protected synchronized void registerIncludeStackTraceService(final IncludeStackTraceServiceImpl serviceImpl, final BundleContext context) {
        includeStackTraceServiceRegistration = context.registerService(IncludeStackTraceService.class, serviceImpl, null);
    }

    // ------------------------------------------------- Reloadable stuff ------------------------------------------------------------

    @Override
    public synchronized void reloadConfiguration(ConfigurationService configService) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ContextInitializer ci = new ContextInitializer(loggerContext);
        URL url = ci.findURLOfDefaultConfigurationFile(true);
        StatusListenerAsList statusListenerAsList = new StatusListenerAsList();
        StatusManager sm = loggerContext.getStatusManager();
        loggerContext.reset();
        // after a reset the statusListenerAsList gets removed as a listener
        sm.add(statusListenerAsList);
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(loggerContext);
            configurator.doConfigure(url);

            // Restore the ranking aware turbo filer list to the logger context
            loggerContext.addTurboFilter(rankingAwareTurboFilterList);
        } catch (JoranException e) {
            LOGGER.error("Error reloading logback configuration: {}", e);
        } finally {
            sm.remove(statusListenerAsList);
        }
    }

    @Override
    public Interests getInterests() {
        return Reloadables.interestsForFiles("logback.xml");
    }

}
