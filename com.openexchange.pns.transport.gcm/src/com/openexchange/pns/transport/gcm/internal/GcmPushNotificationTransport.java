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

package com.openexchange.pns.transport.gcm.internal;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import com.google.android.gcm.Constants;
import com.google.android.gcm.Endpoint;
import com.google.android.gcm.Message;
import com.google.android.gcm.Message.Priority;
import com.google.android.gcm.MulticastResult;
import com.google.android.gcm.Notification;
import com.google.android.gcm.Result;
import com.google.android.gcm.Sender;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.config.cascade.ComposedConfigProperty;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.SortableConcurrentList;
import com.openexchange.java.Strings;
import com.openexchange.osgi.util.RankedService;
import com.openexchange.pns.DefaultPushSubscription;
import com.openexchange.pns.EnabledKey;
import com.openexchange.pns.KnownTransport;
import com.openexchange.pns.PushExceptionCodes;
import com.openexchange.pns.PushMatch;
import com.openexchange.pns.PushMessageGenerator;
import com.openexchange.pns.PushMessageGeneratorRegistry;
import com.openexchange.pns.PushNotification;
import com.openexchange.pns.PushNotificationTransport;
import com.openexchange.pns.PushNotifications;
import com.openexchange.pns.PushSubscriptionRegistry;
import com.openexchange.pns.transport.gcm.GcmOptions;
import com.openexchange.pns.transport.gcm.GcmOptionsProvider;


/**
 * {@link GcmPushNotificationTransport}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class GcmPushNotificationTransport extends ServiceTracker<GcmOptionsProvider, GcmOptionsProvider> implements PushNotificationTransport {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(GcmPushNotificationTransport.class);

    private static final String ID = KnownTransport.GCM.getTransportId();

    private static final int MULTICAST_LIMIT = 1000;

    private static final int MAX_PAYLOAD_SIZE = 4096;

    // ---------------------------------------------------------------------------------------------------------------

    private final ConfigViewFactory configViewFactory;
    private final PushSubscriptionRegistry subscriptionRegistry;
    private final PushMessageGeneratorRegistry generatorRegistry;
    private final SortableConcurrentList<RankedService<GcmOptionsProvider>> trackedProviders;
    private ServiceRegistration<PushNotificationTransport> registration; // non-volatile, protected by synchronized blocks

    /**
     * Initializes a new {@link ApnPushNotificationTransport}.
     */
    public GcmPushNotificationTransport(PushSubscriptionRegistry subscriptionRegistry, PushMessageGeneratorRegistry generatorRegistry, ConfigViewFactory configViewFactory, BundleContext context) {
        super(context, GcmOptionsProvider.class, null);
        this.configViewFactory = configViewFactory;
        this.trackedProviders = new SortableConcurrentList<RankedService<GcmOptionsProvider>>();
        this.subscriptionRegistry = subscriptionRegistry;
        this.generatorRegistry = generatorRegistry;
    }

    // ---------------------------------------------------------------------------------------------------------

    @Override
    public synchronized GcmOptionsProvider addingService(ServiceReference<GcmOptionsProvider> reference) {
        int ranking = RankedService.getRanking(reference);
        GcmOptionsProvider provider = context.getService(reference);

        trackedProviders.addAndSort(new RankedService<GcmOptionsProvider>(provider, ranking));

        if (null == registration) {
            registration = context.registerService(PushNotificationTransport.class, this, null);
        }

        return provider;
    }

    @Override
    public void modifiedService(ServiceReference<GcmOptionsProvider> reference, GcmOptionsProvider provider) {
        // Nothing
    }

    @Override
    public synchronized void removedService(ServiceReference<GcmOptionsProvider> reference, GcmOptionsProvider provider) {
        trackedProviders.remove(new RankedService<GcmOptionsProvider>(provider, RankedService.getRanking(reference)));

        if (trackedProviders.isEmpty() && null != registration) {
            registration.unregister();
            registration = null;
        }

        context.ungetService(reference);
    }

    // ---------------------------------------------------------------------------------------------------------

    private GcmOptions getHighestRankedGcmOptionsFor(String client) throws OXException {
        List<RankedService<GcmOptionsProvider>> list = trackedProviders.getSnapshot();
        for (RankedService<GcmOptionsProvider> rankedService : list) {
            GcmOptions options = rankedService.service.getOptions(client);
            if (null != options) {
                return options;
            }
        }
        throw PushExceptionCodes.UNEXPECTED_ERROR.create("No options found for client: " + client);
    }

    @Override
    public boolean servesClient(String client) throws OXException {
        try {
            return null != getHighestRankedGcmOptionsFor(client);
        } catch (OXException x) {
            return false;
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

        String basePropertyName = "com.openexchange.pns.transport.gcm.enabled";

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
    public void transport(PushNotification notification, Collection<PushMatch> matches) throws OXException {
        if (null != notification && null != matches) {
            Map<String, List<PushMatch>> clientMatches = categorize(matches);
            for (Map.Entry<String, List<com.openexchange.pns.PushMatch>> entry : clientMatches.entrySet()) {
                transport(entry.getKey(), notification, entry.getValue());
            }
        }
    }

    private void transport(String client, PushNotification notification, List<PushMatch> matches) throws OXException {
        if (null == notification || null == matches) {
            return;
        }

        LOG.debug("Going to send notification \"{}\" via transport '{}' for user {} in context {}", notification.getTopic(), ID, I(notification.getUserId()), I(notification.getContextId()));
        int size = matches.size();
        if (size <= 0) {
            return;
        }

        Sender sender = optSender(client);
        if (null == sender) {
            return;
        }

        final List<String> registrationIDs = new ArrayList<String>(size);
        for (int i = 0; i < size; i += MULTICAST_LIMIT) {
            // Prepare chunk
            int length = Math.min(size, i + MULTICAST_LIMIT) - i;
            registrationIDs.clear();
            for (int j = 0; j < length; j++) {
                registrationIDs.add(matches.get(i + j).getToken());
            }

            // Send chunk
            if (!registrationIDs.isEmpty()) {
                MulticastResult result = null;
                try {
                    // result = sender.sendNoRetry(getMessage(client, notification), registrationIDs, Endpoint.FCM);
                    result = sender.send(getMessage(client, notification), registrationIDs, 3, Endpoint.FCM);

                    // Log it
                    Object ostr = registrationIDs.size() == 1 ? registrationIDs.get(0) : createStringObj(registrationIDs);
                    if (null != result) {
                        LOG.info("Sent notification \"{}\" via transport '{}' for user {} in context {} to registration ID(s): {}", notification.getTopic(), ID, I(notification.getUserId()), I(notification.getContextId()), ostr);
                        LOG.debug("{}", result);
                    } else {
                        LOG.info("Failed to send notification \"{}\" via transport '{}' for user {} in context {} to registration ID(s): {}", notification.getTopic(), ID, I(notification.getUserId()), I(notification.getContextId()), ostr);
                    }
                } catch (IOException e) {
                    LOG.warn("Error publishing push notification", e);
                }
                /*
                 * process results
                 */
                processResult(notification, registrationIDs, result);
            }
        }
    }

    private Map<String, List<PushMatch>> categorize(Collection<PushMatch> matches) throws OXException {
        Map<String, List<PushMatch>> clientMatches = new LinkedHashMap<>(matches.size());
        for (PushMatch match : matches) {
            String client = match.getClient();

            // Check generator availability
            {
                PushMessageGenerator generator = generatorRegistry.getGenerator(client);
                if (null == generator) {
                    throw PushExceptionCodes.NO_SUCH_GENERATOR.create(client);
                }
            }

            List<PushMatch> l = clientMatches.get(client);
            if (null == l) {
                l = new LinkedList<>();
                clientMatches.put(client, l);
            }

            l.add(match);
        }
        return clientMatches;
    }

    private Message getMessage(String client, PushNotification notification) throws OXException {
        PushMessageGenerator generator = generatorRegistry.getGenerator(client);
        if (null == generator) {
            throw PushExceptionCodes.NO_SUCH_GENERATOR.create(client);
        }

        com.openexchange.pns.Message<?> message = generator.generateMessageFor(ID, notification);
        Object object = message.getMessage();
        if (object instanceof Message) {
            return checkMessage((Message) object);
        } else if (object instanceof Map) {
            return checkMessage(toMessage((Map<String, Object>) object));
        } else {
            throw PushExceptionCodes.UNSUPPORTED_MESSAGE_CLASS.create(null == object ? "null" : object.getClass().getName());
        }
    }

    private Message checkMessage(Message message) throws OXException {
        int currentLength = PushNotifications.getPayloadLength(message.toString());
        if (currentLength > MAX_PAYLOAD_SIZE) {
            throw PushExceptionCodes.MESSAGE_TOO_BIG.create(I(MAX_PAYLOAD_SIZE), I(currentLength));
        }
        return message;
    }

    private Message toMessage(Map<String, Object> message) throws OXException {
        Message.Builder builder = new Message.Builder();

        Map<String, Object> source = new HashMap<>(message);
        {
            String sCollapseKey = (String) source.remove("collapse_key");
            if (null != sCollapseKey) {
                builder.collapseKey(sCollapseKey);
            }
        }

        {
            Boolean bContentAvailable = (Boolean) source.remove("content_available");
            if (null != bContentAvailable) {
                builder.contentAvailable(bContentAvailable);
            }
        }

        {
            Boolean bDelayWhileIdle = (Boolean) source.remove("delay_while_idle");
            if (null != bDelayWhileIdle) {
                builder.delayWhileIdle(bDelayWhileIdle.booleanValue());
            }
        }

        {
            String sIcon = (String) source.remove("icon");
            if (Strings.isEmpty(sIcon)) {
                throw PushExceptionCodes.MESSAGE_GENERATION_FAILED.create("Missing \"icon\" element.");
            }

            Notification.Builder notificationBuilder = new Notification.Builder(sIcon);

            {
                Integer iBadge = (Integer) source.remove("badge");
                if (null != iBadge) {
                    notificationBuilder.badge(iBadge.intValue());
                }
            }

            {
                String sBody = (String) source.remove("body");
                if (null != sBody) {
                    notificationBuilder.body(sBody);
                }
            }

            {
                String sClickAction = (String) source.remove("click_action");
                if (null != sClickAction) {
                    notificationBuilder.clickAction(sClickAction);
                }
            }

            {
                String sColor = (String) source.remove("color");
                if (null != sColor) {
                    notificationBuilder.color(sColor);
                }
            }

            {
                String sSound = (String) source.remove("sound");
                if (null != sSound) {
                    notificationBuilder.sound(sSound);
                }
            }

            {
                String sTag = (String) source.remove("tag");
                if (null != sTag) {
                    notificationBuilder.tag(sTag);
                }
            }

            {
                String sTitle = (String) source.remove("title");
                if (null != sTitle) {
                    notificationBuilder.title(sTitle);
                }
            }

            builder.notification(notificationBuilder.build());
        }

        {
            String sPriority = (String) source.remove("priority");
            if (null != sPriority) {
                if ("high".equalsIgnoreCase(sPriority)) {
                    builder.priority(Priority.HIGH);
                } else if ("normal".equalsIgnoreCase(sPriority)) {
                    builder.priority(Priority.NORMAL);
                }
            }
        }

        {
            String sRestrictedPackageName = (String) source.remove("restricted_package_name");
            if (null != sRestrictedPackageName) {
                builder.restrictedPackageName(sRestrictedPackageName);
            }
        }

        {
            Integer iTimeToLive = (Integer) source.remove("time_to_live");
            if (null != iTimeToLive) {
                builder.timeToLive(iTimeToLive.intValue());
            }
        }

        return builder.build();
    }

    /*
     * http://developer.android.com/google/gcm/http.html#success
     */
    private void processResult(PushNotification notification, List<String> registrationIDs, MulticastResult multicastResult) {
        if (null == registrationIDs || null == multicastResult) {
            LOG.warn("Unable to process empty results");
            return;
        }
        /*
         * If the value of failure and canonical_ids is 0, it's not necessary to parse the remainder of the response.
         */
        if (0 == multicastResult.getFailure() && 0 == multicastResult.getCanonicalIds()) {
            return;
        }
        /*
         * Otherwise, we recommend that you iterate through the results field...
         */
        List<Result> results = multicastResult.getResults();
        if (null != results && !results.isEmpty()) {
            int numOfResults = results.size();
            if (numOfResults != registrationIDs.size()) {
                LOG.warn("Number of multicast results is different to used registration IDs; unable to process results");
            }
            /*
             * ...and do the following for each object in that list:
             */
            for (int i = 0; i < numOfResults; i++) {
                Result result = results.get(i);
                String registrationID = registrationIDs.get(i);
                if (null != result.getMessageId()) {
                    /*
                     * If message_id is set, check for registration_id:
                     */
                    String newRegistrationId = result.getCanonicalRegistrationId();
                    if (null != newRegistrationId) {
                        /*
                         * If registration_id is set, replace the original ID with the new value (canonical ID) in your server database.
                         * Note that the original ID is not part of the result, so you need to obtain it from the list of
                         * "registration_ids" passed in the request (using the same index).
                         */
                        updateRegistrationIDs(notification, registrationID, newRegistrationId);
                    }
                } else {
                    /*
                     * Otherwise, get the value of error:
                     */
                    String error = result.getErrorCodeName();
                    if (Constants.ERROR_UNAVAILABLE.equals(error)) {
                        /*
                         * If it is Unavailable, you could retry to send it in another request.
                         */
                        LOG.warn("Push message could not be sent because the GCM servers were not available.");
                    } else if (Constants.ERROR_NOT_REGISTERED.equals(error)) {
                        /*
                         * If it is NotRegistered, you should remove the registration ID from your server database because the application
                         * was uninstalled from the device or it does not have a broadcast receiver configured to receive
                         * com.google.android.c2dm.intent.RECEIVE intents.
                         */
                        removeRegistrations(notification, registrationID);
                    } else {
                        /*
                         * Otherwise, there is something wrong in the registration ID passed in the request; it is probably a non-
                         * recoverable error that will also require removing the registration from the server database. See Interpreting
                         * an error response for all possible error values.
                         */
                        LOG.warn("Received error {} when sending push message to {}, removing registration ID.", error, registrationID);
                        removeRegistrations(notification, registrationID);
                    }
                }
            }
        }
    }

    /**
     * (Optionally) Gets a GCM sender based on the configured API key for given client.
     *
     * @param client The client
     * @return The GCM sender or <code>null</code>
     */
    private Sender optSender(String client) {
        try {
            return getSender(client);
        } catch (Exception e) {
            LOG.error("Error getting GCM sender", e);
        }
        return null;
    }

    /**
     * Gets a GCM sender based on the configured API key.
     *
     * @param client The client
     * @return The GCM sender
     * @throws OXException If GCM sender cannot be returned
     */
    private Sender getSender(String client) throws OXException {
        GcmOptions gcmOptions = getHighestRankedGcmOptionsFor(client);
        return new Sender(gcmOptions.getKey());
    }

    private void updateRegistrationIDs(PushNotification notification, String oldRegistrationID, String newRegistrationID) {
        try {
            DefaultPushSubscription.Builder builder = DefaultPushSubscription.builder()
                .contextId(notification.getContextId())
                .token(oldRegistrationID)
                .transportId(ID)
                .userId(notification.getUserId());

            DefaultPushSubscription subscriptionDesc = builder.build();

            boolean success = subscriptionRegistry.updateToken(subscriptionDesc, newRegistrationID);
            if (success) {
                LOG.info("Successfully updated registration ID from {} to {}", oldRegistrationID, newRegistrationID);
            }
        } catch (OXException e) {
            LOG.error("Error updating registration IDs", e);
        }
        LOG.warn("Registration ID {} not updated.", oldRegistrationID);
    }

    private boolean removeRegistrations(PushNotification notification, String registrationID) {
        try {
            DefaultPushSubscription.Builder builder = DefaultPushSubscription.builder()
                .contextId(notification.getContextId())
                .token(registrationID)
                .transportId(ID)
                .userId(notification.getUserId());

            DefaultPushSubscription subscriptionDesc = builder.build();

            boolean success = subscriptionRegistry.unregisterSubscription(subscriptionDesc);
            if (success) {
                LOG.info("Successfully removed registration ID {}.", registrationID);
                return true;
            }
        } catch (OXException e) {
            LOG.error("Error removing subscription", e);
        }
        LOG.warn("Registration ID {} not removed.", registrationID);
        return false;
    }

    private Object createStringObj(List<String> registrationIDs) {
        return new Object() {

            @Override
            public String toString() {
                int size = registrationIDs.size();
                StringBuilder sb = new StringBuilder(size << 8);
                sb.append(registrationIDs.get(0));
                for (int i = 1; i < size; i++) {
                    sb.append(", ").append(registrationIDs.get(i));
                }
                return sb.toString();
            }
        };
    }

}
