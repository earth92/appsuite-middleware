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

package com.openexchange.dav.push.gcm;

import static com.openexchange.dav.push.http.DavHttpClientConfiguration.HTTP_CLIENT_ID;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.dav.push.DAVPushUtility;
import com.openexchange.dav.push.osgi.Services;
import com.openexchange.dav.push.subscribe.PushSubscribeFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.Streams;
import com.openexchange.pns.PushExceptionCodes;
import com.openexchange.pns.PushMatch;
import com.openexchange.pns.PushNotification;
import com.openexchange.pns.PushNotificationTransport;
import com.openexchange.pns.PushSubscriptionRegistry;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.server.ServiceExceptionCode;

/**
 * {@link DavPushGateway}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.8.4
 */
public class DavPushGateway implements PushNotificationTransport {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DavPushGateway.class);

    private final PushTransportOptions transportOptions;
    private final PushSubscribeFactory factory;


    /**
     * Initializes a new {@link DavPushGateway}.
     *
     * @param factory The factory
     * @param transportOptions The transport options to use
     */
    public DavPushGateway(PushSubscribeFactory factory, PushTransportOptions transportOptions) {
        super();
        this.factory = factory;
        this.transportOptions = transportOptions;
    }

    /**
     * Gets a value indicating whether a specific transport URI is supported by the gateway or not.
     *
     * @param transportUri The transport URI to check
     * @return <code>true</code> if the transport URI is supported, <code>false</code>, ohterwise
     */
    public boolean supports(String transportUri) {
        try {
            List<PushTransport> pushTransports = getPushTransports();
            if (null != pushTransports && 0 < pushTransports.size()) {
                for (PushTransport pushTransport : pushTransports) {
                    if (pushTransport.getTransportUri().equals(transportUri)) {
                        return true;
                    }
                }
            }
        } catch (OXException e) {
            LOG.error("Error checking if push transport is supported", e);
        }
        return false;
    }

    @Override
    public boolean isEnabled(String topic, String client, int userId, int contextId) throws OXException {
        return servesClient(client);
    }

    @Override
    public String getId() {
        return transportOptions.getTransportID();
    }

    @Override
    public boolean servesClient(String client) throws OXException {
        return transportOptions.getClientID().equals(client);
    }

    @Override
    public void transport(Map<PushNotification, List<PushMatch>> notifications) throws OXException {
        if (null == notifications || 0 == notifications.size()) {
            return;
        }
        try {
            /*
             * create JSON representation for push messages
             */
            List<JSONObject> jsonMessages = new ArrayList<JSONObject>();
            for (Entry<PushNotification, List<PushMatch>> entry : notifications.entrySet()) {
                jsonMessages.addAll(createPushMessageData(entry.getKey().getMessageData(), entry.getValue()));
            }
            /*
             * transport to gateway in chunks & handle each response
             */
            int transportChunkSize = transportOptions.getTransportChunkSize();
            String uri = transportOptions.getGatewayUrl() + transportOptions.getApplicationID();
            for (int i = 0; i < jsonMessages.size(); i += transportChunkSize) {
                int length = Math.min(jsonMessages.size(), i + transportChunkSize) - i;
                JSONObject responseObject = doPost(uri, createPushData(jsonMessages.subList(i, i + length)));
                if (null != responseObject) {
                    handlePushResponse(responseObject);
                }
            }
        } catch (JSONException e) {
            throw PushExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public void transport(PushNotification notification, Collection<PushMatch> matches) throws OXException {
        JSONObject pushData = createPushData(notification, matches);
        if (null != pushData) {
            JSONObject responseObject = doPost(transportOptions.getGatewayUrl() + transportOptions.getApplicationID(), pushData);
            if (null != responseObject) {
                handlePushResponse(responseObject);
            }
        }
    }

    /**
     * Gets a list of available push transports on the geateway.
     *
     * @return The push transports
     */
    public List<PushTransport> getPushTransports() throws OXException {
        JSONObject data;
        try {
            data = new JSONObject().put("push-transports", new JSONArray(0));
        } catch (JSONException e) {
            throw PushExceptionCodes.JSON_ERROR.create(e);
        }
        return parsePushTransportsResponse(doPost(transportOptions.getGatewayUrl(), data));
    }

    /**
     * Gets the used transport options.
     *
     * @return The transport options
     */
    public PushTransportOptions getOptions() {
        return transportOptions;
    }

    /**
     * Subscribes a client for one or more push topics at the gateway.
     *
     * @param topics The topics to subscribe for
     * @param clientData The optional client data
     * @param expires The expiration date as indicated by the client
     * @return The subscription token
     */
    public String subscribe(List<String> topics, Object clientData, Date expires) throws OXException {
        JSONObject subscribeData = createSubscribeData(topics, clientData, expires);
        if (null != subscribeData) {
            doPost(transportOptions.getGatewayUrl() + transportOptions.getApplicationID(), subscribeData);
            return transportOptions.getApplicationID();
//            TODO: extract token from or use response's "push-url"?
//            JSONObject responseObject = doPost(transportOptions.getGatewayUrl() + transportOptions.getApplicationID(), subscribeData);
//            if (null != responseObject) {
//                String pushUrl = responseObject.optString("push-url");
//            }
        }
        return null;
    }

    /**
     * Unsubscribes a client at the gateway.
     *
     * @param clientData The client data to pass
     * @return The subscription token
     */
    public String unsubscribe(Object clientData) {
        //TODO necessary?
        return transportOptions.getApplicationID(); //TODO: extract from or use response's "push-url"?
    }

    private List<PushTransport> parsePushTransportsResponse(JSONObject responseObject) {
        if (null == responseObject) {
            return null;
        }
        JSONArray pushTransportsArray = responseObject.optJSONArray("push-transports");
        if (null == pushTransportsArray || 0 == pushTransportsArray.length()) {
            return null;
        }
        List<PushTransport> pushTransports = new ArrayList<PushTransport>(pushTransportsArray.length());
        for (int i = 0; i < pushTransportsArray.length(); i++) {
            try {
                pushTransports.add(parsePushTransport(pushTransportsArray.getJSONObject(i)));
            } catch (Exception e) {
                LOG.warn("Error parsing push transports response", e);
            }
        }
        return pushTransports;
    }

    private void handlePushResponse(JSONObject responseObject) {
        if (null == responseObject) {
            return;
        }
        JSONObject pushResponseObject = responseObject.optJSONObject("push-response");
        if (null == pushResponseObject) {
            return;
        }
        JSONArray noSubscribersArray = pushResponseObject.optJSONArray("no-subscribers");
        if (null != noSubscribersArray && 0 < noSubscribersArray.length()) {
            List<String> unsubscribedTopics = new ArrayList<String>(noSubscribersArray.length());
            try {
                for (int i = 0; i < noSubscribersArray.length(); i++) {
                    JSONObject noSubscribersObject = noSubscribersArray.getJSONObject(i);
                    unsubscribedTopics.add(noSubscribersObject.getString("topic"));
                }
            } catch (JSONException e) {
                LOG.warn("Error parsing push response", e);
            }
            if (0 < unsubscribedTopics.size()) {
                PushSubscriptionRegistry subscriptionRegistry = factory.getOptionalService(PushSubscriptionRegistry.class);
                if (null == subscriptionRegistry) {
                    LOG.warn("Unable to remove unsubscribed topics", ServiceExceptionCode.absentService(PushSubscriptionRegistry.class));
                } else {
                    for (String unsubscribedTopic : unsubscribedTopics) {
                        try {
                            subscriptionRegistry.unregisterSubscription(unsubscribedTopic, transportOptions.getTransportID());
                        } catch (OXException e) {
                            LOG.error("Error unregistering subscriptions for {}", unsubscribedTopic, e);
                        }
                    }
                }
            }
        }
    }

    private JSONObject createSubscribeData(List<String> topics, Object clientData, Date expires) throws OXException {
        try {
            JSONObject subscribeData = new JSONObject();
            JSONObject pushSubscribeObject = new JSONObject();
            pushSubscribeObject.put("topics", topics);
            JSONObject transportObject = new JSONObject();
            transportObject.put("transport-uri", transportOptions.getTransportURI());
            transportObject.put("client-data", clientData);
            pushSubscribeObject.put("selected-transport", transportObject);
            pushSubscribeObject.put("expires", DAVPushUtility.UTC_DATE_FORMAT.get().format(expires));
            subscribeData.put("push-subscribe", pushSubscribeObject);
            return subscribeData;
        } catch (JSONException e) {
            throw PushExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    private static JSONObject createPushData(List<JSONObject> jsonMessages) throws OXException {
        try {
            JSONObject pushObject = new JSONObject();
            pushObject.put("messages", jsonMessages);
            return new JSONObject().put("push", pushObject);
        } catch (JSONException e) {
            throw PushExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    private static PushTransport parsePushTransport(JSONObject pushTransportObject) throws JSONException {
        JSONObject transportObject = pushTransportObject.getJSONObject("transport");
        String transportUri = transportObject.getString("transport-uri");
        int refreshInterval = transportObject.getInt("refresh-interval");
        String transportData = transportObject.getString("transport-data");
        return new PushTransport(transportUri, refreshInterval, transportData);
    }

    private static JSONObject createPushData(PushNotification notification, Collection<PushMatch> matches) throws OXException {
        if (null == matches || 0 == matches.size()) {
            return null;
        }
        try {
            JSONObject pushData = new JSONObject();
            JSONObject pushObject = new JSONObject();
            pushData.put("push", pushObject);
            pushObject.put("messages", createPushMessageData(notification.getMessageData(), matches));
            return pushData;
        } catch (JSONException e) {
            throw PushExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    private static List<JSONObject> createPushMessageData(Map<String, Object> messageData, Collection<PushMatch> matches) throws JSONException {
        List<JSONObject> jsonMessages = new ArrayList<JSONObject>(matches.size());
        for (PushMatch match : matches) {
            JSONObject messageObject = new JSONObject();
            messageObject.put("topic", DAVPushUtility.getPushKey(match.getTopic(), match.getContextId(), match.getUserId()));
            Integer priority = (Integer) messageData.get(DAVPushUtility.PARAMETER_PRIORITY);
            messageObject.put("priority", null == priority ? 50 : priority.intValue());
            Long timestamp = (Long) messageData.get(DAVPushUtility.PARAMETER_TIMESTAMP);
            messageObject.put("timestamp", DAVPushUtility.UTC_DATE_FORMAT.get().format(null != timestamp ? new Date(timestamp.longValue()) : new Date()));
            messageObject.putOpt("client-id", messageData.get(DAVPushUtility.PARAMETER_CLIENTTOKEN));
            jsonMessages.add(messageObject);
        }
        return jsonMessages;
    }

    /**
     * Posts an <code>application/json</code> request body to a specific URI.
     *
     * @param uri The target URI
     * @param body The request body
     * @return The JSON response body, or <code>null</code> if there was none
     */
    private JSONObject doPost(String uri, JSONObject body) throws OXException {
        HttpPost post = null;
        HttpResponse response = null;
        try {
            post = new HttpPost(uri);
            post.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
            LOG.trace(">>> POST {}{}    {}", uri, System.lineSeparator(), body);
            response = getHttpClient().execute(post);
            StatusLine statusLine = response.getStatusLine();
            if (null != statusLine && HttpServletResponse.SC_OK == statusLine.getStatusCode()) {
                JSONObject responseBody = parseJSONObject(response.getEntity());
                LOG.trace("<<< {}{}    {}", response.getStatusLine(), System.lineSeparator(), responseBody);
                return responseBody;
            }
            throw PushExceptionCodes.UNEXPECTED_ERROR.create(String.valueOf(statusLine));
        } catch (IOException e) {
            throw PushExceptionCodes.IO_ERROR.create(e, e.getMessage());
        } finally {
            close(post, response);
        }
    }

    private static JSONObject parseJSONObject(HttpEntity entity) throws OXException {
        if (null != entity && ContentType.APPLICATION_JSON.equals(ContentType.get(entity))) {
            InputStream inputStream = null;
            try {
                inputStream = entity.getContent();
                try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                    return new JSONObject(reader);
                }
            } catch (UnsupportedOperationException | IOException e) {
                throw PushExceptionCodes.IO_ERROR.create(e, e.getMessage());
            } catch (JSONException e) {
                throw PushExceptionCodes.JSON_ERROR.create(e, e.getMessage());
            } finally {
                Streams.close(inputStream);
            }
        }
        return null;
    }


    private HttpClient getHttpClient() throws OXException {
        HttpClientService httpClientService = Services.requireService(HttpClientService.class);
        if (httpClientService == null) {
            throw ServiceExceptionCode.absentService(HttpClientService.class);
        }
        return httpClientService.getHttpClient(HTTP_CLIENT_ID);
    }

    /**
     * Closes the supplied HTTP request / response resources silently.
     *
     * @param request The HTTP request to reset
     * @param response The HTTP response to consume and close
     */
    private static void close(HttpRequestBase request, HttpResponse response) {
        if (null != response) {
            HttpEntity entity = response.getEntity();
            if (null != entity) {
                try {
                    EntityUtils.consume(entity);
                } catch (Exception e) {
                    LOG.debug("Error consuming HTTP response entity", e);
                }
            }
            if (response instanceof CloseableHttpResponse) {
                try {
                    ((CloseableHttpResponse) response).close();
                } catch (Exception e) {
                    LOG.debug("Error closing HTTP response", e);
                }
            }
        }
        if (null != request) {
            try {
                request.reset();
            } catch (Exception e) {
                LOG.debug("Error resetting HTTP request", e);
            }
        }
    }
}
