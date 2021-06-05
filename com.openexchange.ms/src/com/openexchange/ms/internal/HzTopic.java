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

package com.openexchange.ms.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.topic.ITopic;
import com.openexchange.ms.Message;
import com.openexchange.ms.MessageListener;

/**
 * {@link HzTopic}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class HzTopic<E> extends AbstractHzTopic<E> {

    private final ITopic<Map<String, Object>> hzTopic;

    /**
     * Initializes a new {@link HzTopic}.
     *
     * @param name The topic's name
     * @param hz The hazelcast instance
     */
    public HzTopic(final String name, final HazelcastInstance hz) {
        super(name, hz);
        this.hzTopic = hz.getTopic(name);
    }

    @Override
    protected UUID registerListener(MessageListener<E> listener, String senderID) {
        try {
            return hzTopic.addMessageListener(new HzMessageListener<E>(listener, senderID));
        } catch (HazelcastInstanceNotActiveException e) {
            throw handleNotActiveException(e);
        }
    }

    @Override
    protected boolean unregisterListener(UUID registrationID) {
        try {
            return hzTopic.removeMessageListener(registrationID);
        } catch (HazelcastInstanceNotActiveException e) {
            throw handleNotActiveException(e);
        }
    }

    @Override
    protected void publish(String senderId, E message) {
        try {
            hzTopic.publish(HzDataUtility.generateMapFor(message, senderId));
        } catch (HazelcastInstanceNotActiveException e) {
            throw handleNotActiveException(e);
        }
    }

    @Override
    protected void publish(String senderId, List<E> messages) {
        // Create map carrying multiple messages
        final StringBuilder sb = new StringBuilder(HzDataUtility.MULTIPLE_PREFIX);
        final int reset = HzDataUtility.MULTIPLE_PREFIX.length();
        final Map<String, Object> multiple = new LinkedHashMap<String, Object>(messages.size() + 1);
        multiple.put(HzDataUtility.MULTIPLE_MARKER, Boolean.TRUE);
        for (int i = 0; i < messages.size(); i++) {
            sb.setLength(reset);
            multiple.put(sb.append(i+1).toString(), HzDataUtility.generateMapFor(messages.get(i), senderId));
        }
        // Publish
        try {
            hzTopic.publish(multiple);
        } catch (HazelcastInstanceNotActiveException e) {
            throw handleNotActiveException(e);
        }
    }

    // ------------------------------------------------------------------------ //

    private static final class HzMessageListener<E> implements com.hazelcast.topic.MessageListener<Map<String, Object>> {

        private final MessageListener<E> listener;
        private final String senderId;

        /**
         * Initializes a new {@link HzMessageListener}.
         */
        protected HzMessageListener(final MessageListener<E> listener, final String senderId) {
            super();
            this.listener = listener;
            this.senderId = senderId;
        }

        @Override
        public void onMessage(final com.hazelcast.topic.Message<Map<String, Object>> message) {
            final Map<String, Object> messageData = message.getMessageObject();
            if (null == messageData) {
                return;
            }
            if (messageData.containsKey(HzDataUtility.MULTIPLE_MARKER)) {
                final String name = message.getSource().toString();
                for (final Entry<String, Object> entry : messageData.entrySet()) {
                    if (entry.getKey().startsWith(HzDataUtility.MULTIPLE_PREFIX)) {
                        onMessageReceived(name, (Map<String, Object>) entry.getValue());
                    }
                }
            } else {
                onMessageReceived(message.getSource().toString(), messageData);
            }
        }

        private void onMessageReceived(final String name, final Map<String, Object> messageData) {
            final String messageSender = (String) messageData.get(HzDataUtility.MESSAGE_DATA_SENDER_ID);
            listener.onMessage(new Message<E>(name, messageSender, (E) messageData.get(HzDataUtility.MESSAGE_DATA_OBJECT), !senderId.equals(messageSender)));
        }
    }
}
