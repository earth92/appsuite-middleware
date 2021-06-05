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

package com.openexchange.oidc.spi;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.ResourceOwnerPasswordCredentialsGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.openexchange.authentication.Authenticated;
import com.openexchange.authentication.AuthenticationService;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.authentication.LoginInfo;
import com.openexchange.authentication.NamePart;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Strings;
import com.openexchange.login.LoginRequest;
import com.openexchange.login.LoginResult;
import com.openexchange.login.listener.LoginListener;
import com.openexchange.nimbusds.oauth2.sdk.http.send.HTTPSender;
import com.openexchange.oidc.AuthenticationInfo;
import com.openexchange.oidc.OIDCBackend;
import com.openexchange.oidc.http.outbound.OIDCHttpClientConfig;
import com.openexchange.oidc.impl.OIDCPasswordGrantAuthentication;
import com.openexchange.oidc.osgi.OIDCBackendRegistry;
import com.openexchange.oidc.osgi.Services;
import com.openexchange.oidc.tools.OIDCTools;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.serverconfig.ServerConfigService;
import com.openexchange.session.Session;
import com.openexchange.session.SessionDescription;
import com.openexchange.session.reservation.EnhancedAuthenticated;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link OIDCPasswordGrantAuthentication}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.10.3
 */
public abstract class AbstractOIDCPasswordGrantAuthentication implements AuthenticationService, LoginListener {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOIDCPasswordGrantAuthentication.class);

    protected static final String PROPERTY_OIDC_PATH = "oidc.oidc_path";

    protected static final String PROPERTY_SERVER_NAME = "oidc.server_name";

    protected final OIDCBackendRegistry backends;

    protected final ServerConfigService serverConfigService;

    protected AbstractOIDCPasswordGrantAuthentication(OIDCBackendRegistry backends, ServerConfigService serverConfigService) {
        this.backends = backends;
        this.serverConfigService = serverConfigService;
    }

    @Override
    public Authenticated handleLoginInfo(LoginInfo loginInfo) throws OXException {
        if (Strings.isEmpty(loginInfo.getUsername()) || Strings.isEmpty(loginInfo.getPassword())) {
            throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
        }

        OIDCBackend backend = getBackend(loginInfo);
        if (backend == null) {
            LOG.debug("No suitable OIDC backend was found for username/password login of user '{}'", loginInfo.getUsername());
            throw LoginExceptionCodes.NOT_SUPPORTED.create(OIDCPasswordGrantAuthentication.class.getName());
        }

        NamePart passwordGrantUserNamePart = backend.getBackendConfig().getPasswordGrantUserNamePart();
        String username = passwordGrantUserNamePart.getFrom(loginInfo.getUsername(), loginInfo.getUsername());
        TokenRequest request = buildTokenRequest(backend, username, loginInfo.getPassword());
        LOG.debug("Sending password grant token request for user '{}' as '{}'", loginInfo.getUsername(), username);

        TokenResponse response = sendTokenRequest(backend, request);
        AuthenticationInfo authInfo = validateResponse(backend, loginInfo, response, loginInfo.getUsername());
        return authenticate(backend, authInfo);
    }

    @Override
    public Authenticated handleAutoLoginInfo(LoginInfo loginInfo) throws OXException {
        throw LoginExceptionCodes.NOT_SUPPORTED.create(OIDCPasswordGrantAuthentication.class.getName());
    }

    protected TokenResponse sendTokenRequest(OIDCBackend backend, TokenRequest request) throws OXException {
        try {
            HTTPRequest httpRequest = backend.getHttpRequest(request.toHTTPRequest());
            return OIDCTokenResponseParser.parse(HTTPSender.send(httpRequest, () -> {
                HttpClientService httpClientService = Services.getOptionalService(HttpClientService.class);
                if (httpClientService == null) {
                    throw new IllegalStateException("Missing service " + HttpClientService.class.getName());
                }
                return httpClientService.getHttpClient(OIDCHttpClientConfig.getClientIdOidc());
            }));
        } catch (com.nimbusds.oauth2.sdk.ParseException | IOException e) {
            throw LoginExceptionCodes.UNKNOWN.create(e, e.getMessage());
        }
    }

    protected TokenRequest buildTokenRequest(OIDCBackend backend, String username, String password) throws OXException {
        AuthorizationGrant authzGrant = new ResourceOwnerPasswordCredentialsGrant(username, new Secret(password));
        ClientAuthentication clientAuth = backend.getClientAuthentication();
        URI tokenEndpoint = OIDCTools.getURIFromPath(backend.getBackendConfig().getOpTokenEndpoint());
        Scope scope = backend.getScope();

        return backend.getTokenRequest(new TokenRequest(tokenEndpoint, clientAuth, authzGrant, scope));
    }

    protected AuthenticationInfo validateResponse(OIDCBackend backend, LoginInfo loginInfo, TokenResponse response, String username) throws OXException {
        if (!response.indicatesSuccess()) {
            TokenErrorResponse errorResponse = (TokenErrorResponse) response;
            LOG.debug("Got token error response to password grant request for user '{}'", username);

            ErrorObject error = errorResponse.getErrorObject();
            if (OAuth2Error.INVALID_GRANT.equals(error)) {
                throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
            }

            throw LoginExceptionCodes.UNKNOWN.create(error.getCode() + " - " + error.getDescription());
        }

        OIDCTokenResponse oidcTokenResponse = (OIDCTokenResponse) response;
        JWT idToken = oidcTokenResponse.getOIDCTokens().getIDToken();
        if (idToken == null) {
            LOG.error("Got token response without ID token for user '{}'", username);
            throw LoginExceptionCodes.LOGIN_DENIED.create();
        }

        try {
            IDTokenClaimsSet validTokenResponse = backend.validateIdToken(idToken, null);
            if (validTokenResponse == null) {
                LOG.error("Got ID token without proper claim set for user '{}': {}", username, idToken.serialize());
                throw LoginExceptionCodes.LOGIN_DENIED.create();
            }
        } catch (OXException e) {
            LOG.error("Invalid ID token for user '{}': {}", username, idToken.serialize(), e);
            throw LoginExceptionCodes.LOGIN_DENIED.create();
        }

        BearerAccessToken bearerAccessToken = oidcTokenResponse.getTokens().getBearerAccessToken();
        if (bearerAccessToken == null) {
            LOG.error("Missing OAuth bearer access token in token response for user '{}': {}", username, oidcTokenResponse.toJSONObject().toJSONString());
            throw LoginExceptionCodes.LOGIN_DENIED.create();
        }

        AuthenticationInfo authInfo = backend.resolveAuthenticationResponse(loginInfo, oidcTokenResponse);
        authInfo.setProperty(OIDCTools.IDTOKEN, oidcTokenResponse.getOIDCTokens().getIDTokenString());
        authInfo.setProperty(OIDCTools.ACCESS_TOKEN, bearerAccessToken.getValue());
        long expiresIn = bearerAccessToken.getLifetime();
        if (expiresIn > 0) {
            authInfo.setProperty(OIDCTools.ACCESS_TOKEN_EXPIRY, String.valueOf(OIDCTools.expiresInToDate(expiresIn).getTime()));
        }

        RefreshToken refreshToken = oidcTokenResponse.getTokens().getRefreshToken();
        if (refreshToken != null) {
            authInfo.setProperty(OIDCTools.REFRESH_TOKEN, refreshToken.getValue());
        }

        authInfo.setProperty(OIDCTools.BACKEND_PATH, backend.getPath());

        LOG.debug("Got success token response to password grant request for user '{}'", username);
        return authInfo;
    }

    protected EnhancedAuthenticated authenticate(final OIDCBackend backend, AuthenticationInfo authInfo) throws OXException {
        ContextService contextService = Services.getService(ContextService.class);
        UserService userService = Services.getService(UserService.class);
        Context context = contextService.getContext(authInfo.getContextId());
        User user = userService.getUser(authInfo.getUserId(), context);

        final ImmutableMap<String, String> state = authInfo.getProperties();
        Authenticated authenticated = backend.enhanceAuthenticated(OIDCTools.getDefaultAuthenticated(context, user, state), state);
        EnhancedAuthenticated enhancedAuthenticated = new EnhancedAuthenticated(authenticated) {
            @Override
            protected void doEnhanceSession(Session session) {
                OIDCTools.setSessionParameters(session, state);

                // try to remove password
                if (session instanceof SessionDescription) {
                    ((SessionDescription) session).setPassword(null);
                }
            }
        };

        return enhancedAuthenticated;
    }

    protected OIDCBackend getBackend(LoginInfo loginInfo) throws OXException {
        // This works in multi-tenant environments where backend paths are defined
        // per hostname. In case where even multiple backends might exist for a single
        // hostname, the client needs to specify the backend via the "oidcPath"
        // request parameter. Both are provided as login info properties via the
        // LoginListener.onBeforeAuthentication() implementation.
        Map<String, Object> properties = loginInfo.getProperties();
        String path = (String) properties.get(PROPERTY_OIDC_PATH);
        String source = "request parameter";
        String serverName = (String) properties.get(PROPERTY_SERVER_NAME);
        if (path == null && serverName != null) {
            List<Map<String,Object>> customHostConfigurations = serverConfigService.getCustomHostConfigurations(serverName, -1, -1);
            if (customHostConfigurations != null) {
                for (Map<String, Object> configMap : customHostConfigurations) {
                    for (Entry<String, Object> configEntry : configMap.entrySet()) {
                        if (OIDCBackendRegistry.OIDC_PATH.equals(configEntry.getKey())) {
                            path = configEntry.getValue().toString();
                            source = "server name [" + serverName + "]";
                            break;
                        }
                    }
                }
            }
        }

        if (path == null) {
            // assume default backend
            path = "";
            source = "default";
        }

        String pathPrefix = "/" + OIDCTools.DEFAULT_BACKEND_PATH;
        if (path.startsWith(pathPrefix)) {
            int startIdx = pathPrefix.length();
            if (path.charAt(pathPrefix.length()) == '/') {
                ++startIdx;
            }
            path = path.substring(startIdx);
        }

        LOG.debug("Determined backend path '{}' from source: {}", path, source);
        for (OIDCBackend backend : backends.getAllRegisteredBackends()) {
            if (path.equals(backend.getPath())) {
                return backend;
            }
        }

        return null;
    }

    @Override
    public void onBeforeAuthentication(LoginRequest request, Map<String, Object> properties) throws OXException {
        // remember the server name and optional "oidcPath" parameter
        // in properties to use it for backend lookup later on
        Map<String, Object> addedProperties = new HashMap<String, Object>(2, 1.0f);
        addedProperties.put(PROPERTY_SERVER_NAME, request.getServerName());
        Map<String, String[]> requestParameter = request.getRequestParameter();
        for (final Entry<String, String[]> parameter : requestParameter.entrySet()) {
            if (OIDCBackendRegistry.OIDC_PATH.equals(parameter.getKey())) {
                String value = parameter.getValue().length > 0 ? parameter.getValue()[0] : null;
                if (Strings.isNotEmpty(value)) {
                    addedProperties.put(PROPERTY_OIDC_PATH, parameter.getValue());
                }
                break;
            }
        }

        LOG.debug("Preparing login info properties for potential password grant flow: {}", addedProperties);
        properties.putAll(addedProperties);
    }

    @Override
    public void onSucceededAuthentication(LoginResult result) throws OXException {
        // noop
    }

    @Override
    public void onFailedAuthentication(LoginRequest request, Map<String, Object> properties, OXException e) throws OXException {
        // noop
    }

    @Override
    public void onRedirectedAuthentication(LoginRequest request, Map<String, Object> properties, OXException e) throws OXException {
        // noop
    }

}
