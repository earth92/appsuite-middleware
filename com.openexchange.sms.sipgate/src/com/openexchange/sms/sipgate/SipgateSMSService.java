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

package com.openexchange.sms.sipgate;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import static com.openexchange.rest.client.httpclient.util.HttpContextUtils.addAuthCache;
import static com.openexchange.rest.client.httpclient.util.HttpContextUtils.addCredentialProvider;
import java.io.IOException;
import java.util.Locale;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONInputStream;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.rest.client.httpclient.HttpClients;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.sms.PhoneNumberParserService;
import com.openexchange.sms.SMSExceptionCode;
import com.openexchange.sms.SMSServiceSPI;

/**
 * {@link SipgateSMSService}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @since v7.8.1
 */
public class SipgateSMSService implements SMSServiceSPI {

    private static final String HOST_ADDRESS = "api.sipgate.com";
    private static final String USERNAME = "com.openexchange.sms.sipgate.username";
    private static final String PASSWORD = "com.openexchange.sms.sipgate.password";

    private static final Logger LOG = LoggerFactory.getLogger(SipgateSMSService.class);
    private static final String URI = "https://api.sipgate.com/v2/sessions/sms";

    private final ServiceLookup services;

    /**
     * Initializes a new {@link SipgateSMSService}.
     * 
     * @param services The {@link ServiceLookup}
     */
    public SipgateSMSService(ServiceLookup services) {
        this.services = services;
    }

    @Override
    public void sendMessage(String[] recipients, String message, int userId, int contextId) throws OXException {
        ConfigViewFactory configViewFactory = services.getService(ConfigViewFactory.class);
        if (null == configViewFactory) {
            throw ServiceExceptionCode.absentService(ConfigViewFactory.class);
        }
        ConfigView view = configViewFactory.getView(userId, contextId);
        String sipgateUsername = view.get(USERNAME, String.class);
        String sipgatePassword = view.get(PASSWORD, String.class);
        Integer maxLength = view.get("com.openexchange.sms.sipgate.maxlength", Integer.class);
        if (Strings.isEmpty(sipgateUsername) || Strings.isEmpty(sipgatePassword)) {
            throw SipgateSMSExceptionCode.NOT_CONFIGURED.create(I(userId), I(contextId));
        }
        if (null == maxLength) {
            LOG.debug("Property \"com.openexchange.sms.sipgate.maxlength\" is not set, using default value 460.");
            maxLength = I(460);
        }
        if (i(maxLength) > 0 && message.length() > i(maxLength)) {
            throw SMSExceptionCode.MESSAGE_TOO_LONG.create(I(message.length()), maxLength);
        }

        try {
            HttpClient client = getHttpClient();
            for (int i = 0; i < recipients.length; i++) {
                JSONObject jsonObject = new JSONObject(3);
                jsonObject.put("smsId", "s0");
                jsonObject.put("recipient", checkAndFormatPhoneNumber(recipients[i], null));
                jsonObject.put("message", message);
                sendMessage(client, sipgateUsername, sipgatePassword, jsonObject);
            }
        } catch (JSONException e) {
            // will not happen
        }
    }

    private String checkAndFormatPhoneNumber(String phoneNumber, Locale locale) throws OXException {
        PhoneNumberParserService parser = services.getService(PhoneNumberParserService.class);
        String parsedNumber = parser.parsePhoneNumber(phoneNumber, locale);
        StringBuilder sb = new StringBuilder(30);
        sb.append("+").append(parsedNumber);
        return sb.toString();
    }

    private void sendMessage(HttpClient client, String username, String password, JSONObject message) throws OXException {
        HttpPost request = new HttpPost(URI);
        request.setEntity(new InputStreamEntity(new JSONInputStream(message, Charsets.UTF_8_NAME), -1L, ContentType.APPLICATION_JSON));
        HttpResponse response = null;

        /*
         * Configure HTTP context
         */
        HttpHost targetHost = new HttpHost(HOST_ADDRESS, AuthScope.ANY_PORT, "https");
        HttpContext context = new BasicHttpContext();
        addCredentialProvider(context, username, password, targetHost);
        addAuthCache(context, targetHost);

        try {
            response = client.execute(request, context);
            StatusLine statusLine = response.getStatusLine();
            if (HttpStatus.SC_NO_CONTENT != statusLine.getStatusCode()) {
                HttpEntity entity = response.getEntity();
                String body = null;
                if (null != entity && entity.getContentLength() > 0) {
                    body = EntityUtils.toString(entity, Charsets.UTF_8);
                }
                throw SipgateSMSExceptionCode.HTTP_ERROR.create(String.valueOf(statusLine.getStatusCode()), Strings.isEmpty(body) ? statusLine.getReasonPhrase() : body);
            }
        } catch (IOException e) {
            throw SipgateSMSExceptionCode.UNKNOWN_ERROR.create(e, e.getMessage());
        } finally {
            HttpClients.close(request, response);
        }
    }

    private HttpClient getHttpClient() throws OXException {
        return services.getServiceSafe(HttpClientService.class).getHttpClient("sipgate");
    }

}
