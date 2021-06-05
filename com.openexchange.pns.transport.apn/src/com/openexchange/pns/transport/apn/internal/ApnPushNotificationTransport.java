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

package com.openexchange.pns.transport.apn.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.config.cascade.ComposedConfigProperty;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.SortableConcurrentList;
import com.openexchange.osgi.util.RankedService;
import com.openexchange.pns.EnabledKey;
import com.openexchange.pns.KnownTransport;
import com.openexchange.pns.PushExceptionCodes;
import com.openexchange.pns.PushMatch;
import com.openexchange.pns.PushMessageGeneratorRegistry;
import com.openexchange.pns.PushNotification;
import com.openexchange.pns.PushNotificationTransport;
import com.openexchange.pns.PushSubscriptionRegistry;
import com.openexchange.pns.transport.apns_http2.DefaultApnsHttp2OptionsProvider;
import com.openexchange.pns.transport.apns_http2.util.ApnOptions;
import com.openexchange.pns.transport.apns_http2.util.ApnOptionsPerClient;
import com.openexchange.pns.transport.apns_http2.util.ApnOptionsProvider;
import com.openexchange.pns.transport.apns_http2.util.ApnsHttp2Options;
import com.openexchange.pns.transport.apns_http2.util.ApnsHttp2OptionsProvider;
import com.openexchange.pns.transport.apns_http2.util.ApnsHttp2PushPerformer;

/**
 * {@link ApnPushNotificationTransport}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class ApnPushNotificationTransport extends ServiceTracker<ApnOptionsProvider, ApnOptionsProvider> implements PushNotificationTransport {

    private static final String ID = KnownTransport.APNS.getTransportId();

    // ---------------------------------------------------------------------------------------------------------------

    private final ConfigViewFactory configViewFactory;
    private final PushSubscriptionRegistry subscriptionRegistry;
    private final PushMessageGeneratorRegistry generatorRegistry;
    private final SortableConcurrentList<RankedService<ApnsHttp2OptionsProvider>> trackedProviders;
    private ServiceRegistration<PushNotificationTransport> registration; // non-volatile, protected by synchronized blocks

    /**
     * Initializes a new {@link ApnPushNotificationTransport}.
     */
    public ApnPushNotificationTransport(PushSubscriptionRegistry subscriptionRegistry, PushMessageGeneratorRegistry generatorRegistry, ConfigViewFactory configViewFactory, BundleContext context) {
        super(context, ApnOptionsProvider.class, null);
        this.configViewFactory = configViewFactory;
        this.trackedProviders = new SortableConcurrentList<RankedService<ApnsHttp2OptionsProvider>>();
        this.generatorRegistry = generatorRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
    }

    // ---------------------------------------------------------------------------------------------------------

    @Override
    public synchronized ApnOptionsProvider addingService(ServiceReference<ApnOptionsProvider> reference) {
        int ranking = RankedService.getRanking(reference);
        ApnOptionsProvider provider = context.getService(reference);

        trackedProviders.addAndSort(new RankedService<ApnsHttp2OptionsProvider>(convertProvider(provider), ranking));

        if (null == registration) {
            registration = context.registerService(PushNotificationTransport.class, this, null);
        }

        return provider;
    }

    @Override
    public void modifiedService(ServiceReference<ApnOptionsProvider> reference, ApnOptionsProvider provider) {
        // Nothing
    }

    @Override
    public synchronized void removedService(ServiceReference<ApnOptionsProvider> reference, ApnOptionsProvider provider) {
        trackedProviders.remove(new RankedService<ApnOptionsProvider>(provider, RankedService.getRanking(reference)));

        if (trackedProviders.isEmpty() && null != registration) {
            registration.unregister();
            registration = null;
        }

        context.ungetService(reference);
    }

    // ---------------------------------------------------------------------------------------------------------

    private ApnsHttp2Options optHighestRankedApnOptionsFor(String client) {
        List<RankedService<ApnsHttp2OptionsProvider>> list = trackedProviders.getSnapshot();
        for (RankedService<ApnsHttp2OptionsProvider> rankedService : list) {
            ApnsHttp2OptionsProvider provider = rankedService.service;
            ApnsHttp2Options options = provider.getOptions(client);
            if (null != options) {
                return options;
            }
        }
        return null;
    }

    @Override
    public boolean servesClient(String client) throws OXException {
        try {
            return null != optHighestRankedApnOptionsFor(client);
        } catch (RuntimeException e) {
            throw PushExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    private static final Cache<EnabledKey, Boolean> CACHE_AVAILABILITY = CacheBuilder.newBuilder().maximumSize(65536).expireAfterWrite(30, TimeUnit.MINUTES).build();

    /**
     * Invalidates the <i>enabled cache</i>.
     */
    public static void invalidateEnabledCache() {
        CACHE_AVAILABILITY.invalidateAll();
    }

    @Override
    public boolean isEnabled(String topic, String client, int userId, int contextId) throws OXException {
        EnabledKey key = new EnabledKey(topic, client, userId, contextId);
        Boolean result = CACHE_AVAILABILITY.getIfPresent(key);
        if (null == result) {
            result = Boolean.valueOf(doCheckEnabled(topic, client, userId, contextId));
            CACHE_AVAILABILITY.put(key, result);
        }
        return result.booleanValue();
    }

    private boolean doCheckEnabled(String topic, String client, int userId, int contextId) throws OXException {
        ConfigView view = configViewFactory.getView(userId, contextId);

        String basePropertyName = "com.openexchange.pns.transport.apn.ios.enabled";

        ComposedConfigProperty<Boolean> property;
        property = null == topic || null == client ? null : view.property(basePropertyName + "." + client + "." + topic, boolean.class);
        if (null != property && property.isDefined()) {
            return property.get().booleanValue();
        }

        property = null == client ? null : view.property(basePropertyName + "." + client, boolean.class);
        if (null != property && property.isDefined()) {
            return property.get().booleanValue();
        }

        property = view.property(basePropertyName, boolean.class);
        if (null != property && property.isDefined()) {
            return property.get().booleanValue();
        }

        return false;
    }

    @Override
    public void transport(Map<PushNotification, List<PushMatch>> notifications) throws OXException {
        new ApnsHttp2PushPerformer(ID, trackedProviders.getSnapshot(), subscriptionRegistry, generatorRegistry).sendPush(notifications);
    }

    @Override
    public void transport(PushNotification notification, Collection<PushMatch> matches) throws OXException {
        if (null != notification && null != matches) {
            List<PushMatch> pushMatches = new ArrayList<PushMatch>(matches);
            transport(Collections.singletonMap(notification, pushMatches));
        }
    }

    private ApnsHttp2OptionsProvider convertProvider(ApnOptionsProvider provider) {
        if (null == provider) {
            return null;
        }
        Collection<ApnOptionsPerClient> optionsPerClient = provider.getAvailableOptions();
        HashMap<String, ApnsHttp2Options> convertedOptions = new HashMap<String, ApnsHttp2Options>(optionsPerClient.size());
        for (ApnOptionsPerClient clientOptions : optionsPerClient) {
            ApnOptions options = clientOptions.getOptions();
            ApnsHttp2Options apnsOptions = new ApnsHttp2Options(options.getClientId(), options.getKeystore(), options.getPassword(), options.isProduction(), options.getTopic()); 
            convertedOptions.put(clientOptions.getClient(), apnsOptions);
        }
        return new DefaultApnsHttp2OptionsProvider(convertedOptions);
    }

}
