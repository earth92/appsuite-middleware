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

package com.openexchange.pns.impl.osgi;

import static com.openexchange.osgi.Tools.withRanking;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.DefaultInterests;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.exception.OXException;
import com.openexchange.osgi.DependentServiceRegisterer;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.pns.PushMessageGenerator;
import com.openexchange.pns.PushMessageGeneratorRegistry;
import com.openexchange.pns.PushNotificationService;
import com.openexchange.pns.PushNotificationTransport;
import com.openexchange.pns.PushSubscriptionListener;
import com.openexchange.pns.PushSubscriptionRegistry;
import com.openexchange.pns.impl.ListenerManagingSubscriptionListener;
import com.openexchange.pns.impl.PushNotificationServiceImpl;
import com.openexchange.pns.impl.SubscriptionAwarePushClientChecker;
import com.openexchange.pns.impl.event.PushEventHandler;
import com.openexchange.processing.ProcessorService;
import com.openexchange.push.PushClientChecker;
import com.openexchange.push.PushEventConstants;
import com.openexchange.push.PushListenerService;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.timer.TimerService;


/**
 * {@link PushNotificationServiceImplActivator}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class PushNotificationServiceImplActivator extends HousekeepingActivator implements Reloadable {

    private PushNotificationServiceImpl serviceImpl;
    private List<ServiceRegistration<?>> registrations;
    private PushNotificationTransportTracker transportTracker;
    private ServiceTracker<?, ?> dependentTracker;

    /**
     * Initializes a new {@link PushNotificationServiceImplActivator}.
     */
    public PushNotificationServiceImplActivator() {
        super();
    }

    @Override
    public void reloadConfiguration(ConfigurationService configService) {
        try {
            reinit(false, configService);
        } catch (Exception e) {
            Logger logger = org.slf4j.LoggerFactory.getLogger(PushNotificationServiceImplActivator.class);
            logger.error("Failed to re-initialize psuh notification service", e);
        }
    }

    @Override
    public Interests getInterests() {
        return DefaultInterests.builder()
            .propertiesOfInterest(
                "com.openexchange.pns.delayDuration",
                "com.openexchange.pns.timerFrequency",
                "com.openexchange.pns.numProcessorThreads",
                "com.openexchange.pns.maxProcessorTasks")
            .build();
    }

    @Override
    protected boolean stopOnServiceUnavailability() {
        return true;
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { PushSubscriptionRegistry.class, ConfigurationService.class, TimerService.class, ProcessorService.class };
    }

    @Override
    protected synchronized void startBundle() throws Exception {
        PushNotificationTransportTracker transportTracker = new PushNotificationTransportTracker(context);
        this.transportTracker = transportTracker;
        track(PushNotificationTransport.class, transportTracker);

        PushMessageGeneratorTracker generatorTracker = new PushMessageGeneratorTracker(context);
        track(PushMessageGenerator.class, generatorTracker);

        openTrackers();

        // Register PushNotificationService
        reinit(false, getService(ConfigurationService.class));

        // Register PushMessageGeneratorRegistry
        registerService(PushMessageGeneratorRegistry.class, generatorTracker);
    }

    private synchronized void reinit(boolean hardShutDown, ConfigurationService configService) throws OXException {
        Logger logger = org.slf4j.LoggerFactory.getLogger(PushNotificationServiceImplActivator.class);

        ServiceTracker<?, ?> tracker = this.dependentTracker;
        if (null != tracker) {
            this.dependentTracker = null;
            tracker.close();
            tracker = null;
        }

        List<ServiceRegistration<?>> registrations = this.registrations;
        if (null != registrations) {
            this.registrations = null;
            for (ServiceRegistration<?> registration : registrations) {
                registration.unregister();
            }
            registrations = null;
        }

        PushNotificationServiceImpl serviceImpl = this.serviceImpl;
        if (null != serviceImpl) {
            this.serviceImpl = null;
            PushNotificationServiceImpl.cleanseInits();
            serviceImpl.stop(false == hardShutDown);
            serviceImpl = null;
        }

        PushSubscriptionRegistry registry = getService(PushSubscriptionRegistry.class);
        TimerService timerService = getService(TimerService.class);
        ProcessorService processorService = getService(ProcessorService.class);

        serviceImpl = new PushNotificationServiceImpl(registry, configService, timerService, processorService, transportTracker);
        this.serviceImpl = serviceImpl;

        registrations = new ArrayList<>(6);
        this.registrations = registrations;
        registrations.add(context.registerService(PushNotificationService.class, serviceImpl, null));
        logger.info("Successfully started Push Notification Service (PNS)");

        // Register proxy'ing event handler
        {
            Dictionary<String, Object> props = new Hashtable<>(2);
            props.put(EventConstants.EVENT_TOPIC, PushEventConstants.TOPIC);
            registrations.add(context.registerService(EventHandler.class, new PushEventHandler(serviceImpl), props));
        }

        // Register client checker
        registrations.add(context.registerService(PushClientChecker.class, new SubscriptionAwarePushClientChecker(registry), withRanking(1)));

        // Register other listener, too
        try {
            DependentServiceRegisterer<PushSubscriptionListener> registerer = new DependentServiceRegisterer<>(context, PushSubscriptionListener.class, ListenerManagingSubscriptionListener.class, null, PushSubscriptionRegistry.class, PushListenerService.class, SessiondService.class);
            tracker = new ServiceTracker<>(context, registerer.getFilter(), registerer);
            this.dependentTracker = tracker;
            tracker.open();
        } catch (InvalidSyntaxException e) {
            logger.error("Failed to initialize dependent service tracker", e);
        }
    }

    @Override
    protected synchronized void stopBundle() throws Exception {
        Logger logger = org.slf4j.LoggerFactory.getLogger(PushNotificationServiceImplActivator.class);

        ServiceTracker<?, ?> tracker = this.dependentTracker;
        if (null != tracker) {
            this.dependentTracker = null;
            tracker.close();
            tracker = null;
        }

        List<ServiceRegistration<?>> registrations = this.registrations;
        if (null != registrations) {
            this.registrations = null;
            for (ServiceRegistration<?> registration : registrations) {
                registration.unregister();
            }
            registrations.clear();
        }

        PushNotificationServiceImpl serviceImpl = this.serviceImpl;
        if (null != serviceImpl) {
            this.serviceImpl = null;
            serviceImpl.stop(true);
            logger.info("Successfully stopped Push Notification Service (PNS)");
        }

        super.stopBundle();
    }

}
