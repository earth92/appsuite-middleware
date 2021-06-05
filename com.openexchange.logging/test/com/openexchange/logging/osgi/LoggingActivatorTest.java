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

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.LoggerFactory;
import com.openexchange.test.mock.MockUtils;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.TurboFilterList;

/**
 * Unit tests for {@link com.openexchange.logging.osgi.LoggingActivator}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.4.2
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ LoggerFactory.class, LoggerContext.class, Logger.class })
public class LoggingActivatorTest {

    @InjectMocks
    private LoggingActivator activator;

    @Mock
    private LoggerContext loggerContext;

    @Mock
    private Logger logger;

    @Mock
    private Logger activatorLogger;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(LoggerFactory.class);

        Mockito.when(loggerContext.getLogger(Mockito.anyString())).thenReturn(logger);
        PowerMockito.when(loggerContext.getProperty(ArgumentMatchers.anyString())).thenReturn(null);

        Mockito.when(LoggerFactory.getILoggerFactory()).thenReturn(loggerContext);

        MockUtils.injectValueIntoPrivateField(LoggingActivator.class, "LOGGER", activatorLogger);
    }

    @Test
    public void testOverrideLoggerLevels_loggerNotAvailable_logWarning() {
        PowerMockito.when(loggerContext.getLogger(ArgumentMatchers.anyString())).thenReturn(null);

        Mockito.mock(LoggerContext.class);

        activator.overrideLoggerLevels(loggerContext);

        Mockito.verify(loggerContext, Mockito.atLeast(1)).getLogger(ArgumentMatchers.anyString());
        Mockito.verify(activatorLogger, Mockito.atLeast(1)).warn(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    @Test
    public void testOverrideLoggerLevels_configuredLevelWARNTooCoarse_setNewLogLevel() {
        Mockito.when(logger.getLevel()).thenReturn(Level.WARN);

        activator.overrideLoggerLevels(loggerContext);

        Mockito.verify(logger, Mockito.atLeast(1)).setLevel(Level.INFO);
        Mockito.verify(loggerContext, Mockito.atLeast(1)).getLogger(ArgumentMatchers.anyString());
        Mockito.verify(activatorLogger, Mockito.never()).warn(ArgumentMatchers.anyString());
    }

    @Test
    public void testOverrideLoggerLevels_configuredLevelNull_setNewLogLevel() {
        Mockito.when(logger.getLevel()).thenReturn(null);

        activator.overrideLoggerLevels(loggerContext);

        Mockito.verify(logger, Mockito.atLeast(1)).setLevel(Level.INFO);
        Mockito.verify(loggerContext, Mockito.atLeast(1)).getLogger(ArgumentMatchers.anyString());
        Mockito.verify(activatorLogger, Mockito.never()).warn(ArgumentMatchers.anyString());
    }

    @Test
    public void testOverrideLoggerLevels_configuredLevelOFFTooCoarse_setNewLogLevel() {
        Mockito.when(logger.getLevel()).thenReturn(Level.OFF);

        activator.overrideLoggerLevels(loggerContext);

        Mockito.verify(logger, Mockito.atLeast(1)).setLevel(Level.INFO);
        Mockito.verify(loggerContext, Mockito.atLeast(1)).getLogger(ArgumentMatchers.anyString());
        Mockito.verify(activatorLogger, Mockito.never()).warn(ArgumentMatchers.anyString());
    }

    @Test
    public void testOverrideLoggerLevels_configuredLevelINFOAdequate_doNotSetNewLogLevel() {
        Mockito.when(logger.getLevel()).thenReturn(Level.INFO);

        activator.overrideLoggerLevels(loggerContext);

        Mockito.verify(logger, Mockito.never()).setLevel(Level.INFO);
        Mockito.verify(loggerContext, Mockito.atLeast(1)).getLogger(ArgumentMatchers.anyString());
        Mockito.verify(activatorLogger, Mockito.never()).warn(ArgumentMatchers.anyString());
    }

    @Test
    public void testOverrideLoggerLevels_configuredLevelALLAdequate_doNotSetNewLogLevel() {
        Mockito.when(logger.getLevel()).thenReturn(Level.ALL);

        activator.overrideLoggerLevels(loggerContext);

        Mockito.verify(logger, Mockito.never()).setLevel(Level.INFO);
        Mockito.verify(loggerContext, Mockito.atLeast(1)).getLogger(ArgumentMatchers.anyString());
        Mockito.verify(activatorLogger, Mockito.never()).warn(ArgumentMatchers.anyString());
    }

    @Test
    public void testOverrideLoggerLevels_disableOverrideLogLevels_returnWithOverriding() {
        Mockito.when(logger.getLevel()).thenReturn(Level.OFF);
        PowerMockito.when(loggerContext.getProperty(ArgumentMatchers.anyString())).thenReturn("true");

        activator.overrideLoggerLevels(loggerContext);

        Mockito.verify(logger, Mockito.never()).setLevel(Level.INFO);
        Mockito.verify(loggerContext, Mockito.never()).getLogger(ArgumentMatchers.anyString());
        Mockito.verify(activatorLogger, Mockito.never()).warn(ArgumentMatchers.anyString());
    }

    @Test
    public void testStartBundle_ensureOverrideLoggerLevelsCalled_successfull() throws Exception {
        BundleContext bundleContext = PowerMockito.mock(BundleContext.class);
        Bundle bundle = PowerMockito.mock(Bundle.class);
        Mockito.when(bundleContext.getBundle()).thenReturn(bundle);
        PowerMockito.when(loggerContext.getTurboFilterList()).thenReturn(new TurboFilterList());

        LoggingActivator activatorSpy = Mockito.spy(activator);
        Mockito.doNothing().when(activatorSpy).overrideLoggerLevels(loggerContext);
        Mockito.doNothing().when(activatorSpy).configureJavaUtilLogging();
        Mockito.doNothing().when(activatorSpy).installJulLevelChangePropagator(loggerContext);
        Mockito.doNothing().when(activatorSpy).registerDeprecatedLogstashAppenderMBeanTracker(ArgumentMatchers.eq(bundleContext));

        activatorSpy.start(bundleContext);

        Mockito.verify(activatorSpy, Mockito.times(1)).overrideLoggerLevels(loggerContext);
    }

    @Test
    public void testStartBundle_ensureInstallJulLevelChangePropagatorCalled_successfull() throws Exception {
        BundleContext bundleContext = PowerMockito.mock(BundleContext.class);
        Bundle bundle = PowerMockito.mock(Bundle.class);
        Mockito.when(bundleContext.getBundle()).thenReturn(bundle);
        PowerMockito.when(loggerContext.getTurboFilterList()).thenReturn(new TurboFilterList());

        LoggingActivator activatorSpy = Mockito.spy(activator);
        Mockito.doNothing().when(activatorSpy).overrideLoggerLevels(loggerContext);
        Mockito.doNothing().when(activatorSpy).configureJavaUtilLogging();
        Mockito.doNothing().when(activatorSpy).installJulLevelChangePropagator(loggerContext);
        Mockito.doNothing().when(activatorSpy).registerDeprecatedLogstashAppenderMBeanTracker(ArgumentMatchers.eq(bundleContext));

        activatorSpy.start(bundleContext);

        Mockito.verify(activatorSpy, Mockito.times(1)).installJulLevelChangePropagator(loggerContext);
    }

    @Test
    public void testInstallJulLevelChangePropagator_propagatorNotAvailable_addPropagator() {
        LoggingActivator activatorSpy = Mockito.spy(activator);
        Mockito.doReturn(Boolean.FALSE).when(activatorSpy).hasInstanceOf(ArgumentMatchers.anyCollection(), ArgumentMatchers.any(Class.class));

        activatorSpy.installJulLevelChangePropagator(loggerContext);

        Mockito.verify(loggerContext, Mockito.atLeast(1)).addListener((LoggerContextListener) ArgumentMatchers.any());
    }

    @Test
    public void testInstallJulLevelChangePropagator_propagatorAvailable_DoNothing() {
        LoggingActivator activatorSpy = Mockito.spy(activator);
        Mockito.doReturn(Boolean.TRUE).when(activatorSpy).hasInstanceOf(ArgumentMatchers.anyCollection(), ArgumentMatchers.any(Class.class));

        activatorSpy.installJulLevelChangePropagator(loggerContext);

        Mockito.verify(loggerContext, Mockito.never()).addListener((LoggerContextListener) ArgumentMatchers.any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInstallJulLevelChangePropagator_collectionNull_throwException() {
        activator.hasInstanceOf(null, LevelChangePropagator.class);
    }

    @Test
    public void testInstallJulLevelChangePropagator_classNullCollectionEmpty_returnFalse() {
        boolean hasInstanceOf = activator.hasInstanceOf(new ArrayList<LoggerContextListener>(), null);

        Assert.assertFalse(hasInstanceOf);
    }

    @Test
    public void testInstallJulLevelChangePropagator_classNullCollectionIncludesLogger_returnFalse() {
        LoggerContextListener listener = new LevelChangePropagator();
        List<LoggerContextListener> collection = new ArrayList<LoggerContextListener>();
        collection.add(listener);

        boolean hasInstanceOf = activator.hasInstanceOf(collection, null);

        Assert.assertFalse(hasInstanceOf);
    }

    @Test
    public void testInstallJulLevelChangePropagator_everythingFine_returnTrue() {
        LoggerContextListener listener = new LevelChangePropagator();
        List<LoggerContextListener> collection = new ArrayList<LoggerContextListener>();
        collection.add(listener);

        boolean hasInstanceOf = activator.hasInstanceOf(collection, LevelChangePropagator.class);

        Assert.assertTrue(hasInstanceOf);
    }

}
