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
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turo.pushy.apns.DeliveryPriority;
import com.turo.pushy.apns.PushType;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;


/**
 * {@link ApnsHttp2Notification}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.1
 */
public class ApnsHttp2Notification extends SimpleApnsPushNotification {

    private static String maptoString(Map<String, Object> root) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // Should not happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the size for given root map.
     *
     * @param root The root map
     * @return The size
     */
    public static int sizeFor(Map<String, Object> root) {
        try {
            return maptoString(root).getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds a notification to be sent to APNS.
     */
    public static class Builder {

        private final HashMap<String, Object> root, aps, alert;
        private final String token;
        private final String topic;
        private String collapseId = null;
        private long expiration;
        private DeliveryPriority priority;
        private PushType pushType;
        private UUID uuid;

        /**
         * Creates a new notification builder.
         *
         * @param token The device token
         * @param topic The topic to which this notification should be sent
         */
        public Builder(String token, String topic) {
            this.token = token;
            this.topic  = topic;
            root = new HashMap<>();
            aps = new HashMap<>();
            alert = new HashMap<>();
            priority = DeliveryPriority.IMMEDIATE;
            expiration = DEFAULT_EXPIRATION_PERIOD_MILLIS;
        }

        public Builder mutableContent(boolean mutable) {
            if (mutable) {
                aps.put("mutable-content", I(1));
            } else {
                aps.remove("mutable-content");
            }

            return this;
        }

        public Builder mutableContent() {
            return this.mutableContent(true);
        }

        public Builder contentAvailable(boolean contentAvailable) {
            if (contentAvailable) {
                aps.put("content-available", I(1));
            } else {
                aps.remove("content-available");
            }

            return this;
        }

        public Builder contentAvailable() {
            return this.contentAvailable(true);
        }

        public Builder alertBody(String body) {
            alert.put("body", body);
            return this;
        }

        public Builder alertTitle(String title) {
            alert.put("title", title);
            return this;
        }

        public Builder sound(String sound) {
            if (sound != null) {
                aps.put("sound", sound);
            } else {
                aps.remove("sound");
            }

            return this;
        }

        public Builder category(String category) {
            if (category != null) {
                aps.put("category", category);
            } else {
                aps.remove("category");
            }
            return this;
        }

        public Builder badge(int badge) {
            aps.put("badge", I(badge));
            return this;
        }

        public Builder customField(String key, Object value) {
            root.put(key, value);
            return this;
        }

        public Builder collapseId(String collapseId) {
            this.collapseId = collapseId;
            return this;
        }

        public Builder expiration(long expiration) {
            this.expiration = expiration;
            return this;
        }

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder priority(DeliveryPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder pushType(PushType pushType) {
            this.pushType = pushType;
            return this;
        }

        public int size() {
            try {
                return build().getPayload().getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public Builder withCustomAlertLocKey(String locKey) {
            alert.put("loc-key", locKey);
            return this;
        }

        public Builder withCustomAlertActionLocKey(String actionLocKey) {
            alert.put("action-loc-key", actionLocKey);
            return this;
        }

        public Builder withSound(String sound) {
            if (sound != null) {
                aps.put("sound", sound);
            } else {
                aps.remove("sound");
            }

            return this;
        }

        public Builder withCategory(String category) {
            if (category != null) {
                aps.put("category", category);
            } else {
                aps.remove("category");
            }
            return this;
        }

        public Builder withBadge(int badge) {
            aps.put("badge", Integer.valueOf(badge));
            return this;
        }

        public Builder withCustomField(String key, Object value) {
            root.put(key, value);
            return this;
        }

        public Builder withCollapseId(String collapseId) {
            this.collapseId = collapseId;
            return this;
        }

        public Builder withExpiration(long expiration) {
            this.expiration = expiration;
            return this;
        }

        public Builder withUuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder withPriority(DeliveryPriority priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Builds the notification.
         *
         * @return The notification
         */
        public ApnsHttp2Notification build() {
            root.put("aps", aps);
            aps.put("alert", alert);
            
            return new ApnsHttp2Notification(token, topic, root, expiration < 0 ? null : new Date(System.currentTimeMillis() + expiration), priority, pushType, collapseId, uuid);
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Constructs a new push notification with the given token, topic, payload, delivery expiration time, delivery
     * priority, "collapse identifier," and unique push notification identifier.
     *
     * @param token The device token to which this push notification should be delivered; must not be {@code null}
     * @param topic The topic to which this notification should be sent; must not be {@code null}
     * @param payload The payload to include in this push notification; must not be {@code null}
     * @param invalidationTime The time at which Apple's servers should stop trying to deliver this message; if
     * {@code null}, no delivery attempts beyond the first will be made
     * @param priority The priority with which this notification should be delivered to the receiving device
     * @param collapseId The "collapse identifier" for this notification, which allows it to supersede or be superseded
     * by other notifications with the same collapse identifier
     * @param apnsId The unique identifier for this notification; may be {@code null}, in which case the APNs server
     * will assign a unique identifier automatically
     */
    public ApnsHttp2Notification(String token, String topic, Map<String, Object> payload, Date invalidationTime, DeliveryPriority priority, PushType pushType, String collapseId, UUID apnsId) {
        super(token, topic, maptoString(payload), invalidationTime, priority, pushType, collapseId, apnsId);
    }

}
