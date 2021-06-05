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

package com.openexchange.rest.services.osgi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.eclipsesource.jaxrs.publisher.ApplicationConfiguration;
import com.openexchange.config.ConfigurationService;
import com.openexchange.dispatcher.DispatcherPrefixService;
import com.openexchange.rest.services.RequestTool;
import com.openexchange.rest.services.jersey.AJAXFilter;
import com.openexchange.rest.services.jersey.JSONReaderWriter;
import com.openexchange.rest.services.jersey.JerseyConfiguration;
import com.openexchange.rest.services.jersey.OXExceptionMapper;
import com.openexchange.rest.services.jersey.ProblemJSONWriter;
import com.openexchange.rest.services.metrics.MetricsListener;
import com.openexchange.rest.services.security.AuthenticationFilter;

/**
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.6.1
 */
public class Activator implements BundleActivator {

    private List<ServiceTracker<?, ?>> trackers;
    private List<ServiceRegistration<?>> registrations;
    final AtomicBoolean authRegistered = new AtomicBoolean();
    ServiceRegistration<AJAXFilter> ajaxFilterRegistration;

    /**
     * Initializes a new {@link Activator}.
     */
    public Activator() {
        super();
    }

    @Override
    public synchronized void start(final BundleContext context) throws Exception {
        final Logger logger = LoggerFactory.getLogger(Activator.class);

        List<ServiceTracker<?, ?>> trackers = new ArrayList<>(6);
        List<ServiceRegistration<?>> registrations = new ArrayList<>(6);
        try {
            trackers.add(new ServiceTracker<ConfigurationAdmin, ConfigurationAdmin>(context, ConfigurationAdmin.class, null) {

                @Override
                public ConfigurationAdmin addingService(ServiceReference<ConfigurationAdmin> reference) {
                    ConfigurationAdmin service = super.addingService(reference);
                    if (service != null) {
                        try {
                            Configuration configuration = service.getConfiguration("com.eclipsesource.jaxrs.connector", null);
                            Dictionary<String, Object> properties = configuration.getProperties();
                            if (properties == null) {
                                properties = new Hashtable<String, Object>(1);
                            }
                            properties.put("root", "/");
                            configuration.update(properties);
                        } catch (IOException e) {
                            logger.error("Could not set root path for jersey servlet. REST API will not be available!", e);
                        }
                    }
                    return service;
                }
            });

            trackers.add(new ServiceTracker<ConfigurationService, ConfigurationService>(context, ConfigurationService.class, null) {

                @Override
                public ConfigurationService addingService(ServiceReference<ConfigurationService> reference) {
                    ConfigurationService service = super.addingService(reference);
                    if (service != null && authRegistered.compareAndSet(false, true)) {
                        context.registerService(AuthenticationFilter.class, new AuthenticationFilter(service), null);
                    }
                    return service;
                }
            });

            trackers.add(new ServiceTracker<DispatcherPrefixService, DispatcherPrefixService>(context, DispatcherPrefixService.class, null) {

                @Override
                public DispatcherPrefixService addingService(ServiceReference<DispatcherPrefixService> reference) {
                    DispatcherPrefixService service = super.addingService(reference);
                    if (service != null) {
                        RequestTool.setDispatcherPrefixService(service);
                        Activator.this.ajaxFilterRegistration = context.registerService(AJAXFilter.class, new AJAXFilter(service.getPrefix()), null);
                    }
                    return service;
                }

                @Override
                public void removedService(ServiceReference<DispatcherPrefixService> reference, DispatcherPrefixService service) {
                    RequestTool.setDispatcherPrefixService(null);
                    ServiceRegistration<AJAXFilter> ajaxFilterRegistration = Activator.this.ajaxFilterRegistration;
                    if (null != ajaxFilterRegistration) {
                        Activator.this.ajaxFilterRegistration = null;
                        ajaxFilterRegistration.unregister();
                    }
                    super.removedService(reference, service);
                }
            });
            for (ServiceTracker<?,?> tracker : trackers) {
                tracker.open();
            }

            registrations.add(context.registerService(JSONReaderWriter.class, new JSONReaderWriter(), null));
            registrations.add(context.registerService(ProblemJSONWriter.class, new ProblemJSONWriter(), null));
            registrations.add(context.registerService(OXExceptionMapper.class, new OXExceptionMapper(), null));
            registrations.add(context.registerService(ApplicationConfiguration.class, new JerseyConfiguration(), null));
            registrations.add(context.registerService(MetricsListener.class, new MetricsListener(), null));

            /*-
             * From now on all instances of registerable classes are handled/added in:
             *  com.eclipsesource.jaxrs.publisher.internal.ResourceTracker.addingService(ResourceTracker.java:45)
             *
             * A registerable instance is defined as:
             *
             *   private boolean isRegisterableAnnotationPresent( Class<?> type ) {
             *    return type.isAnnotationPresent( javax.ws.rs.Path.class ) || type.isAnnotationPresent( javax.ws.rs.ext.Provider.class );
             *   }
             */

            // All went fine
            this.trackers = trackers;
            trackers = null;

            this.registrations = registrations;
            registrations = null;
        } finally {
            if (null != registrations) {
                for (ServiceRegistration<?> serviceRegistration : registrations) {
                    serviceRegistration.unregister();
                }
            }
            if (null != trackers) {
                for (ServiceTracker<?, ?> serviceTracker : trackers) {
                    serviceTracker.close();
                }
            }
        }
    }

    @Override
    public synchronized void stop(BundleContext context) throws Exception {
        List<ServiceRegistration<?>> registrations = this.registrations;
        if (null != registrations) {
            this.registrations = null;
            for (ServiceRegistration<?> serviceRegistration : registrations) {
                serviceRegistration.unregister();
            }
        }

        List<ServiceTracker<?, ?>> trackers = this.trackers;
        if (null != trackers) {
            this.trackers = null;
            for (ServiceTracker<?, ?> serviceTracker : trackers) {
                serviceTracker.close();
            }
        }
    }

}
