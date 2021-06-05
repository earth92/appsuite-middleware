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

package com.openexchange.pns.transport.apns_http2.util;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.osgi.util.RankedService;
import com.openexchange.pns.DefaultPushSubscription;
import com.openexchange.pns.KnownTransport;
import com.openexchange.pns.Message;
import com.openexchange.pns.PushExceptionCodes;
import com.openexchange.pns.PushMatch;
import com.openexchange.pns.PushMessageGenerator;
import com.openexchange.pns.PushMessageGeneratorRegistry;
import com.openexchange.pns.PushNotification;
import com.openexchange.pns.PushSubscriptionRegistry;
import com.turo.pushy.apns.ApnsClient;
import com.turo.pushy.apns.ApnsPushNotification;
import com.turo.pushy.apns.PushNotificationResponse;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
import com.turo.pushy.apns.util.concurrent.PushNotificationFuture;

/**
 * {@link ApnsHttp2PushPerformer} - Sends the push notifications to the Apple Push Notification System via HTTP/2
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @since v7.10.4
 */
public class ApnsHttp2PushPerformer {

    private static final Logger LOG = LoggerFactory.getLogger(ApnsHttp2PushPerformer.class);

    private final String ID;
    private final List<RankedService<ApnsHttp2OptionsProvider>> providers;
    private final PushSubscriptionRegistry subscriptionRegistry;
    private final PushMessageGeneratorRegistry generatorRegistry;

    /**
     * Initializes a new {@link ApnsHttp2PushPerformer}.
     *
     * @param ID The ID of the transport mechanism, see {@link KnownTransport}
     * @param providers A prior sorted list of {@link ApnsHttp2OptionsProvider}
     * @param subscriptionRegistry The PNS subscription registry
     * @param generatorRegistry The message generator registry
     */
    public ApnsHttp2PushPerformer(String ID, List<RankedService<ApnsHttp2OptionsProvider>> providers, PushSubscriptionRegistry subscriptionRegistry, PushMessageGeneratorRegistry generatorRegistry) {
        super();
        this.ID = ID;
        this.providers = providers;
        this.subscriptionRegistry = subscriptionRegistry;
        this.generatorRegistry = generatorRegistry;
    }

    /**
     * Sends the push notification to the Apple Push Notification System
     *
     * @param notifications The raw push notifications
     * @throws OXException
     */
    public void sendPush(Map<PushNotification, List<PushMatch>> notifications) throws OXException {
        if (null == notifications || notifications.isEmpty()) {
            return;
        }
        /*
         * generate message payloads for all matches of all notifications, associated to targeted client
         */
        Map<String, List<Entry<PushMatch, ApnsPushNotification>>> payloadsPerClient = new HashMap<String, List<Entry<PushMatch, ApnsPushNotification>>>();
        Map<String, ApnsHttp2Options> optionsPerClient = new HashMap<String, ApnsHttp2Options>();
        for (Map.Entry<PushNotification, List<PushMatch>> entry : notifications.entrySet()) {
            PushNotification notification = entry.getKey();
            for (Map.Entry<PushMatch, ApnsPushNotification> payload : getPayloadsPerDevice(notification, entry.getValue(), optionsPerClient).entrySet()) {
                String client = payload.getKey().getClient();
                com.openexchange.tools.arrays.Collections.put(payloadsPerClient, client, payload);
            }
        }
        /*
         * perform transport to devices of each client
         */
        for (Map.Entry<String, List<Map.Entry<PushMatch, ApnsPushNotification>>> entry : payloadsPerClient.entrySet()) {
            transport(entry.getKey(), entry.getValue(), optionsPerClient);
        }
    }

    private Map<PushMatch, ApnsPushNotification> getPayloadsPerDevice(PushNotification notification, Collection<PushMatch> matches, Map<String, ApnsHttp2Options> optionsPerClient) throws OXException {
        Map<PushMatch, ApnsPushNotification> payloadsPerDevice = new HashMap<PushMatch, ApnsPushNotification>(matches.size());
        for (PushMatch match : matches) {
            ApnsPushNotification payloadPerDevice = getPayloadPerDevice(notification, match, optionsPerClient);
            if (null != payloadPerDevice) {
                payloadsPerDevice.put(match, payloadPerDevice);
                LOG.debug("Going to send notification \"{}\" via transport '{}' for user {} in context {} to device token {}", notification.getTopic(), ID, I(notification.getUserId()), I(notification.getContextId()), payloadPerDevice.getToken());
            }
        }
        return payloadsPerDevice;
    }

    private ApnsPushNotification getPayloadPerDevice(PushNotification notification, PushMatch match, Map<String, ApnsHttp2Options> optionsPerClient) throws OXException {
        PushMessageGenerator generator = generatorRegistry.getGenerator(match.getClient());
        if (null == generator) {
            throw PushExceptionCodes.NO_SUCH_GENERATOR.create(match.getClient());
        }
        ApnsHttp2Options options = optionsPerClient.get(match.getClient());
        if (null == options) {
            options = getHighestRankedApnOptionsFor(match.getClient());
            optionsPerClient.put(match.getClient(), options);
        }
        Message<?> message = generator.generateMessageFor(ID, notification);
        return getPayload(message.getMessage(), match.getToken(), options.getTopic());
    }

    private ApnsPushNotification getPayload(Object messageObject, String deviceToken, String topic) throws OXException {
        if (messageObject == null) {
            throw PushExceptionCodes.UNSUPPORTED_MESSAGE_CLASS.create("null");
        }
        if (messageObject instanceof ApnsPushNotification) {
            return (ApnsPushNotification) messageObject;
        }
        if (messageObject instanceof Map) {
            return toPayload((Map<String, Object>) messageObject, deviceToken, topic);
        }
        if (messageObject instanceof JSONObject) {
            return toPayload((JSONObject) messageObject, deviceToken, topic);
        }
        if (messageObject instanceof String) {
            return new SimpleApnsPushNotification(deviceToken, topic, (String) messageObject);
        }
        throw PushExceptionCodes.UNSUPPORTED_MESSAGE_CLASS.create(messageObject.getClass().getName());
    }

    private ApnsPushNotification toPayload(Map<String, Object> message, String deviceToken, String topic) {
        ApnsHttp2Notification.Builder builder = new ApnsHttp2Notification.Builder(deviceToken, topic);

        Map<String, Object> source = new HashMap<>(message);
        {
            String sSound = (String) source.remove("sound");
            if (null != sSound) {
                builder.sound(sSound);
            }
        }

        {
            Integer iBadge = (Integer) source.remove("badge");
            if (null != iBadge) {
                builder.badge(iBadge.intValue());
            }
        }

        {
            String sAlert = (String) source.remove("alert");
            if (null != sAlert) {
                builder.alertBody(sAlert);
            }
        }

        {
            String sCategory = (String) source.remove("category");
            if (null != sCategory) {
                builder.category(sCategory);
            }
        }

        // Put remaining as custom dictionary
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (null == value) {
                LOG.warn("Ignoring unsupported null value");
            } else {
                if (value instanceof Number) {
                    builder.customField(entry.getKey(), value);
                } else if (value instanceof String) {
                    builder.customField(entry.getKey(), value);
                } else {
                    LOG.warn("Ignoring usupported value of type {}: {}", value.getClass().getName(), value.toString());
                }
            }
        }

        return builder.build();
    }

    private ApnsPushNotification toPayload(JSONObject message, String deviceToken, String topic) {
        ApnsHttp2Notification.Builder builder = new ApnsHttp2Notification.Builder(deviceToken, topic);
        builder.contentAvailable(true);
        try {
            JSONArray array = message.getJSONArray("args");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                for (String key : obj.keySet()) {
                    builder.withCustomField(key, obj.get(key));
                }
            }
        } catch (JSONException e) {
            // will not happen
        }
        return builder.build();
    }

    private void transport(String client, List<Map.Entry<PushMatch, ApnsPushNotification>> payloads, Map<String, ApnsHttp2Options> optionsPerClient) throws OXException {
        List<NotificationResponsePerDevice> notifications = transport(optionsPerClient.get(client), payloads);
        processNotificationResults(notifications, payloads);
    }

    private List<NotificationResponsePerDevice> transport(ApnsHttp2Options options, List<Map.Entry<PushMatch, ApnsPushNotification>> payloads) throws OXException {
        ApnsClient client = options.getApnsClient();
        List<NotificationResponsePerDevice> results = new ArrayList<NotificationResponsePerDevice>(payloads.size());
        for (Map.Entry<PushMatch, ApnsPushNotification> payload : payloads) {
            // Send notification
            ApnsPushNotification notification = payload.getValue();
            PushNotificationFuture<ApnsPushNotification, PushNotificationResponse<ApnsPushNotification>> sendNotificationFuture = client.sendNotification(notification);
            results.add(new NotificationResponsePerDevice(sendNotificationFuture, notification.getToken()));
        }
        return results;
    }

    private void processNotificationResults(List<NotificationResponsePerDevice> notifications, List<Map.Entry<PushMatch, ApnsPushNotification>> payloads) {
        if (null == notifications || notifications.isEmpty()) {
            return;
        }

        // Create mapping for device token to push match
        Map<String, Set<PushMatch>> deviceToken2PushMatch = new HashMap<>(payloads.size());
        for (Map.Entry<PushMatch, ApnsPushNotification> payload : payloads) {
            String token = payload.getValue().getToken();
            Set<PushMatch> matches = deviceToken2PushMatch.get(token);
            if (matches == null) {
                matches = new LinkedHashSet<>(2);
                deviceToken2PushMatch.put(token, matches);
            }
            matches.add(payload.getKey());
        }

        // Process results
        NextNotification: for (NotificationResponsePerDevice notificationPerDevice : notifications) {
            PushNotificationFuture<ApnsPushNotification, PushNotificationResponse<ApnsPushNotification>> sendNotificationFuture = notificationPerDevice.sendNotificationFuture;
            String deviceToken = notificationPerDevice.deviceToken;
            if (deviceToken == null) {
                // Missing device token
                continue NextNotification;
            }

            Set<PushMatch> pushMatches = deviceToken2PushMatch.get(deviceToken);
            if (pushMatches == null) {
                // No push matches available for current device token
                continue NextNotification;
            }

            // Process push matches
            for (PushMatch pushMatch : pushMatches) {
                try {
                    PushNotificationResponse<ApnsPushNotification> pushNotificationResponse = sendNotificationFuture.get();
                    if (pushNotificationResponse.isAccepted()) {
                        LOG.debug("Push notification accepted by APNs gateway for device token: {}", deviceToken);
                        if (pushMatch != null) {
                            LOG.info("Sent notification \"{}\" via transport '{}' for user {} in context {} to device token: {}", pushMatch.getTopic(), ID, I(pushMatch.getUserId()), I(pushMatch.getContextId()), deviceToken);
                        }
                    } else {
                        if (pushNotificationResponse.getTokenInvalidationTimestamp() != null || isInvalidToken(pushNotificationResponse.getRejectionReason())) {
                            LOG.warn("Unsuccessful push notification due to inactive or invalid device token: {}", deviceToken);
                            if (null != pushMatch) {
                                boolean removed = removeSubscription(pushMatch);
                                if (removed) {
                                    LOG.info("Removed subscription for device with token: {}.", pushMatch.getToken());
                                } else {
                                    LOG.debug("Could not remove subscriptions for device with token: {}.", pushMatch.getToken());
                                }
                            } else {
                                int removed = removeSubscriptions(deviceToken);
                                if (0 < removed) {
                                    LOG.info("Removed {} subscriptions for device with token: {}.", Integer.valueOf(removed), deviceToken);
                                }
                            }
                        } else {
                            LOG.warn("Unsuccessful push notification for device with token: {}", deviceToken);
                        }
                    }
                } catch (ExecutionException e) {
                    LOG.warn("Failed to send push notification for device token {}", deviceToken, e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Interrupted while sending push notification for device token {}", deviceToken, e.getCause());
                    return;
                }
            }
        }
    }

    // Checks if rejectionReason is because of invalid token
    // see section 'Understand Error Codes' https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/handling_notification_responses_from_apns
    private boolean isInvalidToken(String rejectionReason) {
        if (Strings.isEmpty(rejectionReason)) {
            return false;
        }
        return "BadDeviceToken".equals(rejectionReason) || "Unregistered".equals(rejectionReason);
    }

    private int removeSubscriptions(String deviceToken) {
        if (null == deviceToken) {
            LOG.warn("Unsufficient device information to remove subscriptions for: {}", deviceToken);
            return 0;
        }

        try {
            return subscriptionRegistry.unregisterSubscription(deviceToken, ID);
        } catch (OXException e) {
            LOG.error("Error removing subscription", e);
        }
        return 0;
    }

    private boolean removeSubscription(PushMatch match) {
        try {
            DefaultPushSubscription subscription = DefaultPushSubscription.builder()
                .contextId(match.getContextId())
                .token(match.getToken())
                .transportId(ID)
                .userId(match.getUserId())
            .build();
            return subscriptionRegistry.unregisterSubscription(subscription);
        } catch (OXException e) {
            LOG.error("Error removing subscription", e);
        }
        return false;
    }

    private ApnsHttp2Options getHighestRankedApnOptionsFor(String client) throws OXException {
        ApnsHttp2Options options = optHighestRankedApnOptionsFor(client);
        if (null == options) {
            throw PushExceptionCodes.UNEXPECTED_ERROR.create("No options found for client: " + client);
        }
        return options;
    }

    private ApnsHttp2Options optHighestRankedApnOptionsFor(String client) {
        for (RankedService<ApnsHttp2OptionsProvider> rankedService : providers) {
            ApnsHttp2OptionsProvider service = rankedService.service;
            ApnsHttp2Options options = service.getOptions(client);
            if (null != options) {
                return options;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    private static class NotificationResponsePerDevice {

        final PushNotificationFuture<ApnsPushNotification, PushNotificationResponse<ApnsPushNotification>> sendNotificationFuture;
        final String deviceToken;

        NotificationResponsePerDevice(PushNotificationFuture<ApnsPushNotification, PushNotificationResponse<ApnsPushNotification>> sendNotificationFuture, String deviceToken) {
            super();
            this.sendNotificationFuture = sendNotificationFuture;
            this.deviceToken = deviceToken;
        }
    }

}
