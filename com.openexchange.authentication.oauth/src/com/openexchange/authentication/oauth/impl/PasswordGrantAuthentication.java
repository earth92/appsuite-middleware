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

package com.openexchange.authentication.oauth.impl;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.ResourceOwnerPasswordCredentialsGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.token.Tokens;
import com.openexchange.authentication.Authenticated;
import com.openexchange.authentication.AuthenticationService;
import com.openexchange.authentication.DefaultAuthenticated;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.authentication.LoginInfo;
import com.openexchange.authentication.oauth.http.OAuthAuthenticationHttpClientConfig;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.ldap.LdapExceptionCode;
import com.openexchange.java.Strings;
import com.openexchange.nimbusds.oauth2.sdk.http.send.HTTPSender;
import com.openexchange.osgi.Tools;
import com.openexchange.rest.client.httpclient.HttpClientService;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.session.SessionDescription;
import com.openexchange.session.oauth.SessionOAuthTokenService;
import com.openexchange.session.reservation.EnhancedAuthenticated;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.user.UserService;

/**
 * {@link PasswordGrantAuthentication}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.10.3
 */
public class PasswordGrantAuthentication extends OAuthRequestIssuer implements AuthenticationService {

    protected static final Logger LOG = LoggerFactory.getLogger(PasswordGrantAuthentication.class);

    private final ServiceLookup services;

    /**
     * Initializes a new {@link PasswordGrantAuthentication}.
     *
     * @param config The configuration
     * @param services The service lookup
     */
    public PasswordGrantAuthentication(final OAuthAuthenticationConfig config, final ServiceLookup services) {
        super(config);
        this.services = services;
    }

    @Override
    public Authenticated handleLoginInfo(LoginInfo loginInfo) throws OXException {
        if (Strings.isEmpty(loginInfo.getUsername()) || Strings.isEmpty(loginInfo.getPassword())) {
            throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
        }

        //@formatter:off
        ResourceOwnerPasswordCredentialsGrant authorizationGrant = getAuthorizationGrant(loginInfo);
        TokenRequest request = new TokenRequest(config.getTokenEndpoint(),
                                                getClientAuthentication(),
                                                authorizationGrant,
                                                Scope.parse(config.getScope()));
        //@formatter:on
        TokenResponse response;
        try {
            LOG.debug("Sending password grant token request for user '{}' as '{}'", loginInfo.getUsername(), authorizationGrant.getUsername());
            response = TokenResponse.parse(HTTPSender.send(request.toHTTPRequest(), () -> {
                HttpClientService httpClientService = services.getOptionalService(HttpClientService.class);
                if (httpClientService == null) {
                    throw new IllegalStateException("Missing service " + HttpClientService.class.getName());
                }
                return httpClientService.getHttpClient(OAuthAuthenticationHttpClientConfig.getClientIdOAuthAuthentication());
            }));
        } catch (com.nimbusds.oauth2.sdk.ParseException | IOException e) {
            throw LoginExceptionCodes.UNKNOWN.create(e, e.getMessage());
        }

        AccessTokenResponse accessTokenResponse = validateResponse(request, response, loginInfo.getUsername());
        return authenticate(loginInfo, accessTokenResponse);
    }

    @Override
    public Authenticated handleAutoLoginInfo(LoginInfo loginInfo) throws OXException {
        throw LoginExceptionCodes.NOT_SUPPORTED.create(PasswordGrantAuthentication.class.getName());
    }

    private ResourceOwnerPasswordCredentialsGrant getAuthorizationGrant(LoginInfo loginInfo) {
        String username = config.getPasswordGrantUserNamePart().getFrom(loginInfo.getUsername(), loginInfo.getUsername());
        return new ResourceOwnerPasswordCredentialsGrant(username, new Secret(loginInfo.getPassword()));
    }

    private AccessTokenResponse validateResponse(TokenRequest request, TokenResponse response, String username) throws OXException {
        AuthorizationGrant grant = request.getAuthorizationGrant();
        if (!response.indicatesSuccess()) {
            TokenErrorResponse errorResponse = (TokenErrorResponse) response;
            LOG.debug("Got token error response to grant request '{}' for user '{}'", grant.getType().getValue(), username);

            ErrorObject error = errorResponse.getErrorObject();
            if (OAuth2Error.INVALID_GRANT.equals(error)) {
                throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
            }

            throw LoginExceptionCodes.UNKNOWN.create(error.getCode() + " - " + error.getDescription());
        }

        AccessTokenResponse tokenResponse = (AccessTokenResponse) response;
        LOG.debug("Got success token response to grant request '{}' for user '{}'", grant.getType().getValue(), username);

        return tokenResponse;
    }

    private EnhancedAuthenticated authenticate(LoginInfo loginInfo, AccessTokenResponse tokenResponse) throws OXException {
        ContextWrapper context = resolveContext(loginInfo, tokenResponse);
        String userInfo = resolveUser(context.getContext(), loginInfo, tokenResponse);
        return new EnhancedAuthenticated(new DefaultAuthenticated(context.getContextInfo(), userInfo)) {

            @Override
            protected void doEnhanceSession(Session session) {
                Tokens tokens = tokenResponse.getTokens();
                try {
                    setTokensInSession(tokens, session, false);
                } catch (OXException e) {
                    LOG.error("Unable to store oauth tokens in session '{}'", session.getSessionID(), e);
                }

                if (!config.keepPasswordInSession() && session instanceof SessionDescription) {
                    // try to remove password
                    ((SessionDescription) session).setPassword(null);
                }
            }
        };
    }

    void setTokensInSession(Tokens tokens, Session session, boolean inCentralStorage) throws OXException {
        SessionOAuthTokenService tokenService = Tools.requireService(SessionOAuthTokenService.class, services);
        tokenService.setInSession(session, convertNimbusTokens(tokens)); // atomicity not needed during session creation

        session.setParameter(SessionParameters.PASSWORD_GRANT_MARKER, Boolean.TRUE);
        LOG.debug("Session for user '{}' was enhanced by oauth tokens", session.getLogin());

        if (inCentralStorage) {
            LOG.info("Updating oauth tokens for session '{}' in central storage", session.getSessionID());
            try {
                SessiondService sessiondService = Tools.requireService(SessiondService.class, services);
                sessiondService.storeSession(session.getSessionID(), false);
            } catch (OXException e) {
                LOG.warn("Could not update oauth tokens for session '{}' in central storage", session.getSessionID(), e);
            }
        }
    }

    private ContextWrapper resolveContext(LoginInfo loginInfo, AccessTokenResponse tokenResponse) throws OXException, IllegalArgumentException {
        String contextLookup = null;
        switch (config.getContextLookupSource()) {
            case LOGIN_NAME:
                contextLookup = loginInfo.getUsername();
                break;
            case RESPONSE_PARAMETER:
                contextLookup = extractContextLookup(tokenResponse);
                break;
            default:
                throw new IllegalArgumentException("Invalid context lookup source: " + config.getContextLookupSource().name());
        }

        String contextInfo = config.getContextLookupNamePart().getFrom(contextLookup, Authenticated.DEFAULT_CONTEXT_INFO);
        ContextService contextService = Tools.requireService(ContextService.class, services);
        int contextId = contextService.getContextId(contextInfo);
        if (contextId < 0) {
            LOG.debug("Unknown context for login mapping '{}' ('{}')", contextInfo, contextLookup);
            throw LoginExceptionCodes.INVALID_CREDENTIALS_MISSING_CONTEXT_MAPPING.create(contextInfo);
        }

        LOG.debug("Resolved context {} for login mapping '{}' ('{}')", I(contextId), contextInfo, contextLookup);
        return new ContextWrapper(contextInfo, contextService.getContext(contextId));
    }

    private String extractContextLookup(AccessTokenResponse tokenResponse) throws OXException {
        String contextLookupParameter = config.getContextLookupParameter();
        Object object = tokenResponse.getCustomParameters().get(contextLookupParameter);
        if (null != object) {
            String contextLookup = object.toString();
            if (Strings.isNotEmpty(contextLookup)) {
                return contextLookup;
            }
        }
        LOG.debug("Missing or empty context lookup parameter '{}' in access token response", contextLookupParameter);
        throw LoginExceptionCodes.MISSING_PROPERTY.create(contextLookupParameter);
    }

    private String resolveUser(Context context, LoginInfo loginInfo, AccessTokenResponse tokenResponse) throws OXException, IllegalArgumentException {
        String userLookup;
        switch (config.getUserLookupSource()) {
            case LOGIN_NAME:
                userLookup = loginInfo.getUsername();
                break;
            case RESPONSE_PARAMETER:
                String userLookupParameter = config.getUserLookupParameter();
                userLookup = tokenResponse.getCustomParameters().get(userLookupParameter).toString();
                if (Strings.isEmpty(userLookup)) {
                    LOG.debug("Missing or empty user lookup parameter '{}' in access token response", userLookupParameter);
                    throw LoginExceptionCodes.MISSING_PROPERTY.create(userLookupParameter);
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid user lookup source: " + config.getUserLookupSource().name());
        }

        String userInfo = config.getUserLookupNamePart().getFrom(userLookup, userLookup);
        UserService userService = Tools.requireService(UserService.class, services);
        try {
            int userId = userService.getUserId(userInfo, context);
            LOG.debug("Resolved user {} in context {} for '{}' ('{}')", I(userId), I(context.getContextId()), userInfo, userLookup);
            return userInfo;
        } catch (OXException e) {
            if (LdapExceptionCode.USER_NOT_FOUND.equals(e)) {
                LOG.debug("Unknown user in context {} for '{}' ('{}')", I(context.getContextId()), userInfo, userLookup);
                throw LoginExceptionCodes.INVALID_CREDENTIALS_MISSING_USER_MAPPING.create(userInfo);
            }

            throw e;
        }
    }

    private static final class ContextWrapper {

        private final String contextInfo;
        private final Context context;

        public ContextWrapper(String contextInfo, Context context) {
            super();
            this.contextInfo = contextInfo;
            this.context = context;
        }

        public String getContextInfo() {
            return contextInfo;
        }

        public Context getContext() {
            return context;
        }
    }

}
