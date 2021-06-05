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

package com.openexchange.osgi;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.openexchange.server.ServiceLookup;

/**
 * A {@link HousekeepingActivator} helps with housekeeping tasks like remembering service trackers or service registrations and cleaning
 * them up later.
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class HousekeepingActivator extends DeferredActivator {

    /**
     * Puts/removes tracked service from activator's service look-up as the appear/disappear.
     */
    private static final class ServiceLookupTrackerCustomizer<S> implements ServiceTrackerCustomizer<S, S> {

        private final Class<S> clazz;
        private final HousekeepingActivator activator;
        private final BundleContext context;

        /**
         * Initializes a new {@link ServiceLookupTrackerCustomizer}.
         *
         * @param clazz The service's class to look-up
         * @param activator The activator
         * @param context The bundle context
         */
        protected ServiceLookupTrackerCustomizer(final Class<S> clazz, final HousekeepingActivator activator, final BundleContext context) {
            super();
            this.clazz = clazz;
            this.activator = activator;
            this.context = context;
        }

        @Override
        public S addingService(final ServiceReference<S> reference) {
            final S service = context.getService(reference);
            if (activator.addService(clazz, service)) {
                return service;
            }

            // Such a service already available
            LOG.error("Duplicate service instance for singleton service \"{}\" detected in bundle \"{}\". Please review active/started bundles.", clazz.getName(), context.getBundle().getSymbolicName());
            context.ungetService(reference);
            return null;
        }

        @Override
        public void modifiedService(final ServiceReference<S> reference, final S service) {
            // Ignore
        }

        @Override
        public void removedService(final ServiceReference<S> reference, final S service) {
            if (null != service) {
                activator.removeService(clazz);
                context.ungetService(reference);
            }
        }
    }

    /**
     * Delegates tracker events to specified {@link SimpleRegistryListener} instance.
     */
    private static final class SimpleRegistryListenerTrackerCustomizer<S> implements ServiceTrackerCustomizer<S, S> {

        private final SimpleRegistryListener<S> listener;
        private final BundleContext context;

        /**
         * Initializes a new {@link SimpleRegistryListenerTrackerCustomizer}.
         *
         * @param listener The {@link SimpleRegistryListener} instance to delegate to
         * @param context The bundle context
         */
        protected SimpleRegistryListenerTrackerCustomizer(final SimpleRegistryListener<S> listener, final BundleContext context) {
            super();
            this.listener = listener;
            this.context = context;
        }

        @Override
        public S addingService(final ServiceReference<S> serviceReference) {
            final S service = context.getService(serviceReference);
            try {
                listener.added(serviceReference, service);
                return service;
            } catch (Exception e) {
                context.ungetService(serviceReference);
                LOG.warn("Adding service ({}) to listener failed. Service released.", service.getClass().getName(), e);
                return null;
            }
        }

        @Override
        public void modifiedService(final ServiceReference<S> serviceReference, final S service) {
            // Don't care
        }

        @Override
        public void removedService(final ServiceReference<S> serviceReference, final S service) {
            try {
                listener.removed(serviceReference, service);
            } finally {
                context.ungetService(serviceReference);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------- //

    private final List<ServiceTracker<?, ?>> serviceTrackers;
    private final SetMultimap<Object,ServiceRegistration<?>> serviceRegistrations;

    /**
     * Initializes a new {@link HousekeepingActivator}.
     */
    protected HousekeepingActivator() {
        super();
        serviceTrackers = new CopyOnWriteArrayList<ServiceTracker<?, ?>>();
        serviceRegistrations = Multimaps.synchronizedSetMultimap(HashMultimap.create(6,2));
    }

    @Override
    protected void handleAvailability(final Class<?> clazz) {
        // Override if needed
    }

    @Override
    protected void handleUnavailability(final Class<?> clazz) {
        // Override if needed
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        context.addFrameworkListener(new FrameworkListener() {

            @Override
            public void frameworkEvent(FrameworkEvent event) {
                if (event.getBundle().getSymbolicName().equalsIgnoreCase(context.getBundle().getSymbolicName())) {
                    int eventType = event.getType();
                    if (eventType == FrameworkEvent.ERROR) {
                        LOG.error(event.toString(), event.getThrowable());
                    } else {
                        LOG.info(event.toString(), event.getThrowable());
                    }
                }
            }
        });

        // Check for possible optional services
        Class<?>[] optionalServices = getOptionalServices();
        if (null != optionalServices) {
            for (final Class<?> clazz : optionalServices) {
                trackService(clazz);
            }
        }

        // Invoking ServiceTracker.open() more than once is a no-op, therefore it can be safely called from here.
        if (!serviceTrackers.isEmpty()) {
            openTrackers();
        }
    }

    @Override
    protected void stopBundle() throws Exception {
        cleanUp();
    }

    /**
     * Checks if this activator has at least one service registered.
     *
     * @return <code>true</code> if this activator has at least one service registered; otherwise <code>false</code>
     */
    protected boolean hasRegisteredServices() {
        return !serviceRegistrations.isEmpty();
    }

    /**
     * Gets the {@link ServiceRegistration} instance for specified service.
     *
     * @param clazz The service's class
     * @return The {@link ServiceRegistration} instance or <code>null</code> if no such service has been registered
     */
    @SuppressWarnings("unchecked")
    protected <S> ServiceRegistration<S> getServiceRegistrationFor(Class<S> clazz) {
        for (Map.Entry<Object, ServiceRegistration<?>> entry : serviceRegistrations.entries()) {
            if (clazz.isInstance(entry.getKey())) {
                return (ServiceRegistration<S>) entry.getValue();
            }
        }
        return null;
    }

    /**
     * Gets the {@link ServiceRegistration} instance for specified service.
     *
     * @param service The previously registered service
     * @return The {@link ServiceRegistration} instance or <code>null</code> if no such service has been registered
     */
    @SuppressWarnings("unchecked")
    protected <S> ServiceRegistration<S> getServiceRegistrationFor(S service) {
        return (ServiceRegistration<S>) serviceRegistrations.get(service);
    }

    /**
     * Registers specified service with the specified properties under the specified class.
     *
     * @param clazz The service's class
     * @param service The service reference
     * @param properties The service's properties
     */
    protected <S> void registerService(final Class<S> clazz, final S service, final Dictionary<String, ?> properties) {
        LOG.debug("Registering service {} with class {}", service, clazz);
        serviceRegistrations.put(service, context.registerService(clazz, service, properties));
    }

    /**
     * Registers specified service under the specified class.
     *
     * @param clazz The service's class
     * @param service The service reference
     */
    protected <S> void registerService(final Class<S> clazz, final S service) {
        registerService(clazz, service, null);
    }

    /**
     * Registers specified service under the specified class, using the supplied service ranking.
     *
     * @param clazz The service's class
     * @param service The service reference
     * @param serviceRanking The value to configure the {@link Constants#SERVICE_RANKING} to
     */
    protected <S> void registerService(final Class<S> clazz, final S service, int serviceRanking) {
        registerService(clazz, service, Tools.withRanking(serviceRanking));
    }

    /**
     * Registers specified Service or {@link org.osgi.framework.ServiceFactory} with the specified properties under the specified classname
     * @param className The service's class name
     * @param service The service reference
     * @param properties The service's properties
     */
    protected void registerService(final String className, final Object service, final Dictionary<String, ?> properties) {
        serviceRegistrations.put(service, context.registerService(className, service, properties));
    }

    /**
     * Registers specified Service or {@link org.osgi.framework.ServiceFactory} under the specified class name
     * @param className The service's class name
     * @param service The service reference
     */
    protected void registerService(final String className, final Object service) {
        registerService(className, service, null);
    }

    /**
     * Adds specified service tracker to this activator. Thus it is automatically closed and removed by {@link #cleanUp()}.
     * <br>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Please {@link #openTrackers() open} trackers.
     * </p>
     * </div>
     *
     * @param tracker The service tracker
     */
    protected void rememberTracker(final ServiceTracker<?, ?> tracker) {
        serviceTrackers.add(tracker);
    }

    /**
     * Removes specified service tracker from this activator.
     * <br>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Please {@link ServiceTracker#close() close} tracker if it has already been started.
     * </p>
     * </div>
     *
     * @param tracker The service tracker
     */
    protected void forgetTracker(final ServiceTracker<?, ?> tracker) {
        serviceTrackers.remove(tracker);
    }

    /**
     * Gets the classes of the optional services.
     * <p>
     * They appear when available in activator's service collection.
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     * </div>
     *
     * @return The array of {@link Class} instances of optional services
     */
    protected Class<?>[] getOptionalServices() {
        return EMPTY_CLASSES;
    }

    /**
     * Creates and remembers a new {@link ServiceTracker}. The tracked service is automatically {@link #addService(Class, Object) added to}/
     * {@link #removeService(Class) removed} from tracked services and thus available/disappearing when using this activator as
     * {@link ServiceLookup service look-up}.
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     * </div>
     *
     * @param clazz The class of the tracked service
     * @return The newly created {@link ServiceTracker} instance
     */
    protected <S> ServiceTracker<S, S> trackService(final Class<S> clazz) {
        final ServiceTracker<S, S> tracker = new ServiceTracker<S, S>(context, clazz, new ServiceLookupTrackerCustomizer<S>(clazz, this, context));
        rememberTracker(tracker);
        return tracker;
    }

    /**
     * Creates and remembers a new {@link ServiceTracker} instance parameterized with given customizer.
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     * </div>
     *
     * @param clazz The class of the tracked service
     * @param customizer The customizer applied to newly created {@link ServiceTracker} instance
     * @return The newly created {@link ServiceTracker} instance
     */
    protected <S> ServiceTracker<S, S> track(final Class<S> clazz, final ServiceTrackerCustomizer<S, S> customizer) {
        final ServiceTracker<S, S> tracker = new ServiceTracker<S, S>(context, clazz, customizer);
        rememberTracker(tracker);
        return tracker;
    }

    /**
     * Creates and remembers a new {@link ServiceTracker} instance parameterized with given customizer.
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     * </div>
     *
     * @param filter The tracker's filter
     * @param customizer The customizer applied to newly created {@link ServiceTracker} instance
     * @return The newly created {@link ServiceTracker} instance
     */
    protected <S> ServiceTracker<S, S> track(final Filter filter, final ServiceTrackerCustomizer<S, S> customizer) {
        final ServiceTracker<S, S> tracker = new ServiceTracker<S, S>(context, filter, customizer);
        rememberTracker(tracker);
        return tracker;
    }

    /**
     * Creates and remembers a new {@link ServiceTracker} instance for specified service's class.
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     * </div>
     *
     * @param clazz The service's class
     * @return The newly created {@link ServiceTracker} instance
     */
    protected <S> ServiceTracker<S, S> track(final Class<S> clazz) {
        if (clazz.isAssignableFrom(ServiceTrackerCustomizer.class)) {
            LOG.warn("ServiceTracker/ServiceTrackerCustomizer \"{}\" is tracked as a service! You probably wanted to call rememberTracker() and open it afterwards.", clazz.getName());
        }
        return track(clazz, (ServiceTrackerCustomizer<S, S>) null);
    }

    /**
     * Creates and remembers a new {@link ServiceTracker} instance for specified filter.
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     *
     * @param filter The filter to apply
     * @return The newly created {@link ServiceTracker} instance
     */
    protected <S> ServiceTracker<S, S> track(final Filter filter) {
        return track(filter, (ServiceTrackerCustomizer<S, S>) null);
    }

    /**
     * Creates and remembers a new {@link ServiceTracker} instance with given listener applied.
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     * </div>
     *
     * @param clazz The service's class
     * @param listener The service's listener triggered on {@link ServiceTracker#addingService(ServiceReference)} and so on
     * @return The newly created {@link ServiceTracker} instance
     */
    protected <S> ServiceTracker<S, S> track(final Class<S> clazz, final SimpleRegistryListener<S> listener) {
        return track(clazz, new SimpleRegistryListenerTrackerCustomizer<S>(listener, context));
    }

    /**
     * Creates and remembers a new {@link ServiceTracker} instance with given listener applied.
     * <div style="margin-left: 0.1in; margin-right: 0.5in; background-color:#FFDDDD;">
     * <p>
     * <b>NOTE</b>: Don't forget to open tracker(s) with {@link #openTrackers()}.
     * </div>
     *
     * @param filter The service filter
     * @param listener The service's listener triggered on {@link ServiceTracker#addingService(ServiceReference)} and so on
     * @return The newly created {@link ServiceTracker} instance
     */
    protected <S> ServiceTracker<S, S> track(final Filter filter, final SimpleRegistryListener<S> listener) {
        return track(filter, new SimpleRegistryListenerTrackerCustomizer<S>(listener, context));
    }

    /**
     * Opens all trackers.
     */
    protected void openTrackers() {
        for (final ServiceTracker<?, ?> tracker : serviceTrackers) {
            tracker.open();
        }
    }

    /**
     * Closes all trackers.
     */
    protected void closeTrackers() {
        for (final ServiceTracker<?, ?> tracker : serviceTrackers) {
            tracker.close();
        }
    }

    /**
     * Drops all trackers kept by this activator.
     */
    protected void clearTrackers() {
        serviceTrackers.clear();
    }

    /**
     * Unregisters all services.
     */
    protected void unregisterServices() {
        for (final ServiceRegistration<?> registration : serviceRegistrations.values()) {
            LOG.debug("Unregistering {}", registration);
            registration.unregister();
        }
        serviceRegistrations.clear();
    }

    /**
     * Unregisters all registrations associated with specified service.
     *
     * @param service The service to unregister
     */
    protected <S> void unregisterService(final S service) {
        for(ServiceRegistration<?> registration : serviceRegistrations.removeAll(service)) {
            LOG.debug("Unregistering {}", registration);
            registration.unregister();
        }
    }

    /**
     * Unregisters all registrations associated with specified service class.
     *
     * @param service The class of the service to unregister
     */
    protected <S> void unregisterService(final Class<S> clazz) {
        for (Iterator<Entry<Object, ServiceRegistration<?>>> it = serviceRegistrations.entries().iterator(); it.hasNext();) {
            Map.Entry<Object, ServiceRegistration<?>> entry = it.next();
            if (clazz.isInstance(entry.getKey())) {
                entry.getValue().unregister();
                it.remove();
            }
        }
    }

    /**
     * Performs whole clean-up:
     * <ul>
     * <li>Close all trackers</li>
     * <li>Clear all trackers</li>
     * <li>Unregister all services</li>
     * </ul>
     */
    protected void cleanUp() {
        closeTrackers();
        clearTrackers();
        unregisterServices();
    }

    /**
     * Initializes a new dictionary and inserts a specific key/value pair, ready to use when registering a service with a custom property
     * via {@link #registerService(Class, Object, Dictionary)}.
     *
     * @param key The key to insert into the returned dictionary
     * @param value The value to associate with the key in the returned dictionary
     * @return A new dictionary holding a single mapping from <code>key</code> to <code>value</code>
     */
    protected static Dictionary<String, Object> singletonDictionary(String key, Object value) {
        Dictionary<String, Object> dictionary = new Hashtable<String, Object>(1);
        dictionary.put(key, value);
        return dictionary;
    }

}
