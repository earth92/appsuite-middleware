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

package com.openexchange.saml.oauth;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.rest.client.httpclient.HttpClients;
import com.openexchange.saml.oauth.service.OAuthAccessToken;
import com.openexchange.saml.oauth.service.SAMLOAuthExceptionCodes;
import com.openexchange.server.ServiceLookup;

/**
 * {@link AbstractOAuthAccessTokenRequest}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.4
 */
public abstract class AbstractOAuthAccessTokenRequest {

    private final ServiceLookup services;

    private final String clientId;

    /**
     * Initializes a new {@link AbstractOAuthAccessTokenRequest}.
     *
     * @param services The service lookup to get the {@link ConfigViewFactory} and the {@link HttpClientService} from
     * @param clientId The identifier of the HTTP client
     */
    protected AbstractOAuthAccessTokenRequest(ServiceLookup services, String clientId) {
        super();
        this.services = services;
        this.clientId = clientId;
    }

    private static final String TOKEN_TYPE = "token_type";
    private static final String EXPIRE = "expires_in";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ERROR_DESCRIPTION = "error_description";
    private static final String ERROR = "error";
    private static final String SCOPE = "scope";

    private static final String INVALID_GRANT = "invalid_grant";


    /**
     * Requests an OAuth access token.
     *
     * @param accessInfo The access info; e.g. base64-encoded SAML response or refresh token
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param scope An optional scope
     * @return The OAuth access token
     * @throws OXException If OAuth access token cannot be returned
     */
    public OAuthAccessToken requestAccessToken(String accessInfo, int userId, int contextId, String scope) throws OXException {
        HttpPost requestAccessToken = null;
        HttpResponse validationResp = null;
        try {
            OAuthConfiguration oAuthConfiguration = SAMLOAuthConfig.getConfig(userId, contextId, services.getServiceSafe(ConfigViewFactory.class));
            if (oAuthConfiguration == null) {
                throw SAMLOAuthExceptionCodes.OAUTH_NOT_CONFIGURED.create(I(userId), I(contextId));
            }

            // Initialize POST request
            requestAccessToken = new HttpPost(oAuthConfiguration.getTokenEndpoint());
            requestAccessToken.addHeader("Content-Type", "application/x-www-form-urlencoded");

            // Build base64(<client-id> + ":" + <client-secret>) "Authorization" header
            if (oAuthConfiguration.getClientId()!=null && oAuthConfiguration.getClientSecret()!=null){
                String authString = new StringBuilder(oAuthConfiguration.getClientId()).append(':').append(oAuthConfiguration.getClientSecret()).toString();
                String auth = "Basic " + Base64.encodeBase64String(authString.getBytes(Charsets.UTF_8));
                requestAccessToken.addHeader("Authorization", auth);
            }

            // Build the url-encoded pairs for the POST request
            List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            nvps.add(new BasicNameValuePair("grant_type", getGrantType()));
            if (Strings.isNotEmpty(scope)){
                nvps.add(new BasicNameValuePair(SCOPE, scope));
            }
            addAccessInfo(accessInfo, nvps);
            requestAccessToken.setEntity(new UrlEncodedFormEntity(nvps, Charsets.UTF_8));

            // Execute POST
            validationResp = getHttpClient().execute(requestAccessToken);

            // Get & parse response body
            HttpEntity entity = validationResp.getEntity();
            if (null != entity) {
                String responseStr = EntityUtils.toString(entity, Charsets.UTF_8);
                if (responseStr != null) {
                    JSONObject jsonResponse = new JSONObject(responseStr);
                    if (jsonResponse.has(ERROR)) {
                        if (jsonResponse.getString(ERROR).equals(INVALID_GRANT)) {
                            if (jsonResponse.has(ERROR_DESCRIPTION)) {
                                throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create("Invalid grant error: " + jsonResponse.getString(ERROR_DESCRIPTION));
                            }
                            throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create("Invalid grant error.");
                        }
                        if (jsonResponse.has(ERROR_DESCRIPTION)) {
                            OXException e = SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create(jsonResponse.getString(ERROR) + " error: " + jsonResponse.getString(ERROR_DESCRIPTION));
                            throw e;
                        }
                        throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create(jsonResponse.getString(ERROR) + " error for user {} in context {}.", I(userId), I(contextId));
                    }
                    String accessToken = jsonResponse.optString(ACCESS_TOKEN, null);
                    if (null == accessToken) {
                        throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create("Token response doesn't contain the access token.");
                    }

                    String refreshToken = jsonResponse.has(REFRESH_TOKEN) ? jsonResponse.getString(REFRESH_TOKEN) : null;
                    int expires = jsonResponse.has(EXPIRE) ? jsonResponse.getInt(EXPIRE) : -1;
                    String tokenType = jsonResponse.has(TOKEN_TYPE) ? jsonResponse.getString(TOKEN_TYPE) : null;
                    return new OAuthAccessToken(accessToken, refreshToken, tokenType, expires);
                }
            }

            throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create("Unable to parse token response.");
        } catch (ClientProtocolException e) {
            throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create(e, e.getMessage());
        } catch (IOException e) {
            throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create(e, e.getMessage());
        } catch (JSONException e) {
            throw SAMLOAuthExceptionCodes.NO_ACCESS_TOKEN.create(e, e.getMessage());
        } finally {
            HttpClients.close(requestAccessToken, validationResp);
        }
    }

    private HttpClient getHttpClient() throws IllegalStateException, OXException {
        return services.getServiceSafe(HttpClientService.class).getHttpClient(clientId);
    }

    /**
     * Gets the grant type.
     *
     * @return The grant type
     */
    protected abstract String getGrantType();

    /**
     * Adds the access info.
     *
     * @param accessInfo The access info to add
     * @param nvps The list to add to
     */
    protected abstract void addAccessInfo(String accessInfo, List<NameValuePair> nvps);
}
