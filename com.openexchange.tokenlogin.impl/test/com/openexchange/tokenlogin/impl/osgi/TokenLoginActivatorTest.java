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

package com.openexchange.tokenlogin.impl.osgi;

import static com.openexchange.java.Autoboxing.B;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.util.tracker.ServiceTracker;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.openexchange.config.ConfigurationService;
import com.openexchange.hazelcast.configuration.HazelcastConfigurationService;
import com.openexchange.osgi.ServiceProvider;
import com.openexchange.osgi.SimpleServiceProvider;
import com.openexchange.test.mock.InjectionFieldConstants;
import com.openexchange.test.mock.MockUtils;
import com.openexchange.test.mock.assertion.ServiceMockActivatorAsserter;
import com.openexchange.tokenlogin.TokenLoginService;
import com.openexchange.tokenlogin.impl.Services;

/**
 * Unit tests for {@link TokenLoginActivator}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.4
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Services.class })
public class TokenLoginActivatorTest {

    /**
     * Class under test
     */
    @InjectMocks
    private final TokenLoginActivator tokenLoginActivator = null;

    /**
     * {@link BundleContext} mock
     */
    @Mock
    private BundleContext bundleContext;

    /**
     * {@link Bundle} mock
     */
    @Mock
    private Bundle bundle;

    /**
     * {@link ConfigurationService} mock
     */
    @Mock
    private ConfigurationService configurationService;

    /**
     * {@link HazelcastConfigurationService} mock
     */
    @Mock
    private HazelcastConfigurationService hazelcastConfigurationService;

    /**
     * {@link Properties} mock
     */
    @Mock
    private Properties properties;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // SERVICES
        PowerMockito.when(this.configurationService.getProperty(ArgumentMatchers.anyString())).thenReturn("theStringPropertyValue");
        PowerMockito.when(B(this.configurationService.getBoolProperty("com.openexchange.tokenlogin", true))).thenReturn(Boolean.TRUE);
        PowerMockito.when(this.configurationService.getPropertiesInFolder(ArgumentMatchers.anyString())).thenReturn(this.properties);
        PowerMockito.when(this.hazelcastConfigurationService.getConfig()).thenReturn(new com.hazelcast.config.Config());
        PowerMockito.when(B(this.hazelcastConfigurationService.isEnabled())).thenReturn(Boolean.TRUE);

        ConcurrentMap<Class<?>, ServiceProvider<?>> services = new ConcurrentHashMap<Class<?>, ServiceProvider<?>>();
        services.putIfAbsent(ConfigurationService.class, new SimpleServiceProvider<Object>(configurationService));
        services.putIfAbsent(HazelcastConfigurationService.class, new SimpleServiceProvider<Object>(hazelcastConfigurationService));
        MockUtils.injectValueIntoPrivateField(this.tokenLoginActivator, InjectionFieldConstants.SERVICES, services);

        // CONTEXT
        Mockito.when(this.bundleContext.getBundle()).thenReturn(this.bundle);
        Mockito.when(this.bundle.getVersion()).thenReturn(new Version(1, 1, 1));
        MockUtils.injectValueIntoPrivateField(this.tokenLoginActivator, InjectionFieldConstants.CONTEXT, bundleContext);
    }

     @Test
     public void testStartBundle_EverythingFine_TwoServicesRegistered() throws Exception {
        PowerMockito.when(B(hazelcastConfigurationService.isEnabled())).thenReturn(Boolean.FALSE);

        this.tokenLoginActivator.startBundle();

        ServiceMockActivatorAsserter.verifyAllServicesRegistered(this.tokenLoginActivator, 2);
        Mockito.verify(hazelcastConfigurationService, Mockito.times(1)).isEnabled();
    }

     @Test
     public void testStartBundle_HazelcastDisabled_NoTrackerRegistered() throws Exception {
        PowerMockito.when(B(hazelcastConfigurationService.isEnabled())).thenReturn(Boolean.FALSE);

        this.tokenLoginActivator.startBundle();

        ServiceMockActivatorAsserter.verifyAllServiceTrackersClosed(this.tokenLoginActivator);
        Mockito.verify(hazelcastConfigurationService, Mockito.times(1)).isEnabled();
    }

     @Test
     public void testStartBundle_HazelcastEnabled_OneTrackerRegistered() throws Exception {
        this.tokenLoginActivator.startBundle();

        ServiceMockActivatorAsserter.verifyAllServiceTrackersRegistered(this.tokenLoginActivator, 1);
    }

     @Test
     public void testStartBundle_TokenLoginDisabled_NoTrackerRegistered() throws Exception {
        PowerMockito.when(B(this.configurationService.getBoolProperty("com.openexchange.tokenlogin", true))).thenReturn(Boolean.FALSE);

        this.tokenLoginActivator.startBundle();

        ServiceMockActivatorAsserter.verifyAllServiceTrackersRegistered(this.tokenLoginActivator, 0);
    }

     @Test
     public void testStartBundle_TokenLoginDisabled_NoServiceRegistered() throws Exception {
        PowerMockito.when(B(this.configurationService.getBoolProperty("com.openexchange.tokenlogin", true))).thenReturn(Boolean.FALSE);

        this.tokenLoginActivator.startBundle();

        ServiceMockActivatorAsserter.verifyAllServicesRegistered(this.tokenLoginActivator, 0);
        Mockito.verify(hazelcastConfigurationService, Mockito.times(0)).isEnabled();
    }

     @Test
     public void testStopBundle_EverythingFine_AllTrackersClosed() throws Exception {
        final List<ServiceTracker<?, ?>> serviceTrackers = new LinkedList<ServiceTracker<?, ?>>();
        ServiceTracker<?, ?> serviceTracker = PowerMockito.mock(ServiceTracker.class);
        serviceTrackers.add(serviceTracker);
        MockUtils.injectValueIntoPrivateField(this.tokenLoginActivator, InjectionFieldConstants.SERVICE_TRACKERS, serviceTrackers);

        this.tokenLoginActivator.stopBundle();

        ServiceMockActivatorAsserter.verifyAllServiceTrackersClosed(this.tokenLoginActivator);
    }

     @Test
     public void testStopBundle_EverythingFine_AllServicesUnregistered() throws Exception {
        final Multimap<Object, ServiceRegistration<?>> serviceRegistrations = HashMultimap.create(6,2);
        ServiceRegistration<?> serviceRegistration = PowerMockito.mock(ServiceRegistration.class);
        serviceRegistrations.put(TokenLoginService.class, serviceRegistration);
        MockUtils.injectValueIntoPrivateField(this.tokenLoginActivator, InjectionFieldConstants.SERVICE_REGISTRATIONS, serviceRegistrations);

        this.tokenLoginActivator.stopBundle();

        ServiceMockActivatorAsserter.verifyAllServiceTrackersClosed(this.tokenLoginActivator);
    }
}
