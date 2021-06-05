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

package com.openexchange.oauth.json.oauthaccount.actions;

import static com.openexchange.java.Strings.isEmpty;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.Client;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.AJAXRequestResult.ResultType;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionStrings;
import com.openexchange.groupware.notify.hostname.HostnameService;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.Strings;
import com.openexchange.oauth.HostInfo;
import com.openexchange.oauth.OAuthConstants;
import com.openexchange.oauth.OAuthExceptionCodes;
import com.openexchange.oauth.OAuthInteraction;
import com.openexchange.oauth.OAuthService;
import com.openexchange.oauth.OAuthToken;
import com.openexchange.oauth.OAuthUtil;
import com.openexchange.oauth.Parameterizable;
import com.openexchange.oauth.json.Services;
import com.openexchange.oauth.json.oauthaccount.AccountField;
import com.openexchange.oauth.json.oauthaccount.AccountWriter;
import com.openexchange.oauth.scope.OAuthScope;
import com.openexchange.session.Session;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link InitAction}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public final class InitAction extends AbstractOAuthTokenAction {

    private static final Logger LOG = LoggerFactory.getLogger(InitAction.class);

    /**
     * Initializes a new {@link InitAction}.
     */
    public InitAction() {
        super();
    }

    @Override
    public AJAXRequestResult perform(AJAXRequestData request, ServerSession session) throws OXException {
        return createCallbackAction(request, session);
    }

    /**
     * Creates an <code>init?action=create</code> call-back action
     *
     * @param request The {@link AJAXRequestData}
     * @param session The server session
     * @return the {@link AJAXRequestResult} containing the {@link OAuthInteraction} as a {@link JSONObject}
     * @throws OXException if the call-back action cannot be created
     */
    private AJAXRequestResult createCallbackAction(AJAXRequestData request, ServerSession session) throws OXException {
        Locale locale = session.getUser().getLocale();
        try {
            /*
             * Parse parameters
             */
            final String serviceId = request.getParameter(AccountField.SERVICE_ID.getName());
            if (serviceId == null) {
                throw AjaxExceptionCodes.MISSING_PARAMETER.create(AccountField.SERVICE_ID.getName());
            }
            final String name = AccountField.DISPLAY_NAME.getName();
            final String displayName = request.getParameter(name);
            if (isEmpty(displayName)) {
                throw OAuthExceptionCodes.MISSING_DISPLAY_NAME.create();
            }
            // Get the scopes
            Set<OAuthScope> scopes = getScopes(request, serviceId);

            return invokeInteraction(request, session, "callback", serviceId, scopes);
        } catch (OXException e) {
            if (Client.OX6_UI.getClientId().equals(session.getClient())) {
                throw e;
            }
            throw AjaxExceptionCodes.HTTP_ERROR.create(e, Integer.valueOf(HttpServletResponse.SC_BAD_REQUEST), e.getDisplayMessage(locale));
        } catch (JSONException e) {
            if (Client.OX6_UI.getClientId().equals(session.getClient())) {
                throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
            }
            throw AjaxExceptionCodes.HTTP_ERROR.create(e, Integer.valueOf(HttpServletResponse.SC_OK), StringHelper.valueOf(locale).getString(OXExceptionStrings.MESSAGE));
        } catch (RuntimeException e) {
            if (Client.OX6_UI.getClientId().equals(session.getClient())) {
                throw AjaxExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
            }
            throw AjaxExceptionCodes.HTTP_ERROR.create(e, Integer.valueOf(HttpServletResponse.SC_OK), StringHelper.valueOf(locale).getString(OXExceptionStrings.MESSAGE));
        }
    }

    /**
     * Processes and invokes the {@link OAuthInteraction}
     *
     * @param request The {@link AJAXRequestData}
     * @param session The server {@link Session}
     * @param action The action of the <code>init</code> call
     * @param accountId The account identifier; -1 if not available (which indicates a <code>create</code> action.
     * @param serviceId The OAuth service provider identifier
     * @param scopes The {@link OAuthScope}s to enable
     * @return the {@link AJAXRequestResult} containing the {@link OAuthInteraction} as a {@link JSONObject}
     * @throws JSONException if a JSON error is occurred
     * @throws OXException if a server error is occurred
     */
    private AJAXRequestResult invokeInteraction(AJAXRequestData request, ServerSession session, String action, String serviceId, Set<OAuthScope> scopes) throws JSONException, OXException {
        /*
         * Generate UUID
         */
        final String uuid = UUID.randomUUID().toString();
        /*
         * OAuth token for session
         */
        final String oauthSessionToken = UUID.randomUUID().toString();

        String callbackUrl = composeCallbackURL(request, session, action, scopes, serviceId, uuid, oauthSessionToken);
        OAuthService oauthService = getOAuthService();
        /*
         * Invoke
         */
        final HostInfo currentHost = determineHost(request, session);
        final OAuthInteraction interaction = oauthService.initOAuth(session, serviceId, callbackUrl, currentHost, scopes);
        final OAuthToken requestToken = interaction.getRequestToken();
        /*
         * Create a container to set some state information: Request token's secret, call-back URL, whatever
         */
        final Map<String, Object> oauthState = new HashMap<>();
        if (interaction instanceof Parameterizable) {
            final Parameterizable params = (Parameterizable) interaction;
            for (final String key : params.getParamterNames()) {
                final Object value = params.getParameter(key);
                if (null != value) {
                    oauthState.put(key, value);
                }
            }
        }
        oauthState.put(OAuthConstants.ARGUMENT_SECRET, requestToken.getSecret());
        oauthState.put(OAuthConstants.ARGUMENT_CALLBACK, callbackUrl);
        oauthState.put(OAuthConstants.ARGUMENT_CURRENT_HOST, currentHost.getHost());
        oauthState.put(OAuthConstants.ARGUMENT_ROUTE, currentHost.getRoute());
        oauthState.put(OAuthConstants.ARGUMENT_AUTH_URL, interaction.getAuthorizationURL());
        session.setParameter(uuid, oauthState);
        session.setParameter(Session.PARAM_TOKEN, oauthSessionToken);

        String authorizationURL = interaction.getAuthorizationURL();
        LOG.debug("Initialised an OAuth workflow with {}", authorizationURL);
        /*
         * Check redirect parameter
         */
        if (AJAXRequestDataTools.parseBoolParameter(request.getParameter("redirect"))) {
            // Request for redirect
            HttpServletResponse response = request.optHttpServletResponse();
            if (null != response) {
                try {
                    LOG.debug("Sending redirect to the '{}' provider", serviceId);
                    response.sendRedirect(interaction.getAuthorizationURL());
                    //response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                    return new AJAXRequestResult(AJAXRequestResult.DIRECT_OBJECT, "direct").setType(ResultType.DIRECT);
                } catch (IOException e) {
                    throw OAuthExceptionCodes.IO_ERROR.create(e, e.getMessage());
                }
            }
        }
        LOG.debug("Returning the initialised OAuth interaction for '{}'", serviceId);
        /*
         * Write as JSON
         */
        final JSONObject jsonInteraction = AccountWriter.write(interaction, uuid);
        /*
         * Return appropriate result
         */
        return new AJAXRequestResult(jsonInteraction);
    }

    private HostInfo determineHost(AJAXRequestData request, ServerSession session) {
        return new HostInfo(determineHostName(request, session), request.getRoute());
    }

    /**
     * Composes the call-back URL
     *
     * @param request The {@link AJAXRequestData}
     * @param session The server {@link Session}
     * @param action The action of the <code>init</code> call
     * @param scopes The {@link OAuthScope}s to enable
     * @param accountId The account identifier
     * @param serviceId The OAuth service provider's identifier
     * @param uuid The OAuthState UUID
     * @param oauthSessionToken The OAuth token for the session
     * @return The call-back URL as a String
     */
    private String composeCallbackURL(AJAXRequestData request, Session session, String action, Set<OAuthScope> scopes, String serviceId, String uuid, String oauthSessionToken) {
        final StringBuilder callbackUrlBuilder = request.constructURL(new StringBuilder(PREFIX.get().getPrefix()).append("oauth/accounts").toString(), true);
        callbackUrlBuilder.append("?action=").append(action);
        callbackUrlBuilder.append("&respondWithHTML=true&session=").append(session.getSessionID());
        {
            final String name = AccountField.DISPLAY_NAME.getName();
            final String displayName = request.getParameter(name);
            if (displayName != null) {
                callbackUrlBuilder.append('&').append(name).append('=').append(urlEncode(displayName));
            }
        }
        callbackUrlBuilder.append('&').append(AccountField.SERVICE_ID.getName()).append('=').append(urlEncode(serviceId));
        callbackUrlBuilder.append('&').append(OAuthConstants.SESSION_PARAM_UUID).append('=').append(uuid);
        callbackUrlBuilder.append('&').append(Session.PARAM_TOKEN).append('=').append(oauthSessionToken);
        callbackUrlBuilder.append("&scopes=").append(OAuthUtil.oxScopesToString(scopes));
        callbackUrlBuilder.append('&').append(AccountField.ID.getName()).append('=').append(getAccountId(request));
        final String cb = request.getParameter("cb");
        if (Strings.isNotEmpty(cb)) {
            callbackUrlBuilder.append("&callback=").append(cb);
        }
        String actionHint = request.getParameter(OAuthConstants.URLPARAM_ACTION_HINT);
        if (Strings.isNotEmpty(actionHint)) {
            callbackUrlBuilder.append('&').append(OAuthConstants.URLPARAM_ACTION_HINT).append('=').append(actionHint);
        }

        return callbackUrlBuilder.toString();
    }

    /**
     * URL encodes the specified string
     *
     * @param s The string to URL encode
     * @return The URL encoded string
     */
    private String urlEncode(final String s) {
        try {
            return URLEncoder.encode(s, "ISO-8859-1");
        } catch (@SuppressWarnings("unused") UnsupportedEncodingException e) {
            return s;
        }
    }

    /**
     * Determines the host. Starts by the {@link HostnameService}, then from the specified {@link AJAXRequestData},
     * then Java and sets it to localhost as a last resort.
     *
     * @param requestData The {@link AJAXRequestData}
     * @param session The groupware {@link Session}
     * @return The hostname
     */
    private String determineHostName(AJAXRequestData requestData, ServerSession session) {
        String hostName = null;
        /*
         * Ask hostname service if available
         */
        {
            final HostnameService hostnameService = Services.getService(HostnameService.class);
            if (null != hostnameService) {
                if (session.getUser().isGuest()) {
                    hostName = hostnameService.getGuestHostname(session.getUserId(), session.getContextId());
                } else {
                    hostName = hostnameService.getHostname(session.getUserId(), session.getContextId());
                }
            }
        }
        /*
         * Get hostname from request
         */
        if (isEmpty(hostName)) {
            hostName = requestData.getHostname();
        }
        /*
         * Get hostname from java
         */
        if (isEmpty(hostName)) {
            try {
                hostName = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                // log and ignore
                LOG.debug("", e);
            }
        }
        /*
         * Fall back to localhost as last resort
         */
        if (isEmpty(hostName)) {
            hostName = "localhost";
        }
        return requestData.getHostname();
    }
}
