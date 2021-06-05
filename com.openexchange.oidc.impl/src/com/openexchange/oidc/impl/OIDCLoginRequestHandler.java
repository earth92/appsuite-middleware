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

package com.openexchange.oidc.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.SessionUtility;import com.openexchange.ajax.login.LoginRequestContext;
import com.openexchange.ajax.login.LoginRequestHandler;
import com.openexchange.ajax.writer.ResponseWriter;
import com.openexchange.authentication.Authenticated;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.login.LoginRequest;
import com.openexchange.login.LoginResult;
import com.openexchange.login.internal.LoginMethodClosure;
import com.openexchange.login.internal.LoginPerformer;
import com.openexchange.login.internal.LoginResultImpl;
import com.openexchange.oidc.OIDCBackend;
import com.openexchange.oidc.OIDCBackendConfig;
import com.openexchange.oidc.OIDCBackendConfig.AutologinMode;
import com.openexchange.oidc.OIDCBackendProperty;
import com.openexchange.oidc.osgi.Services;
import com.openexchange.oidc.tools.OIDCTools;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.session.Session;
import com.openexchange.session.SessionDescription;
import com.openexchange.session.reservation.EnhancedAuthenticated;
import com.openexchange.session.reservation.Reservation;
import com.openexchange.session.reservation.SessionReservationService;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.user.User;
import com.openexchange.user.UserService;
import static com.openexchange.java.Autoboxing.I;

/**
 * {@link OIDCLoginRequestHandler} Performs a login with a valid {@link Reservation} and
 * creates a {@link Session} in the process. Also tries to login the user into a valid
 * {@link Session} directly, if the {@link OIDCBackendProperty}.autologinCookieMode indicates
 * an enabled auto-login.
 *
 * @author <a href="mailto:vitali.sjablow@open-xchange.com">Vitali Sjablow</a>
 * @since v7.10.0
 */
public class OIDCLoginRequestHandler implements LoginRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OIDCLoginRequestHandler.class);
    private OIDCHandler handler;

    /**
     * Initializes a new {@link OIDCLoginRequestHandler}.
     *
     * @param backend The back-end to use
     */
    public OIDCLoginRequestHandler(OIDCBackend backend) {
        super();
        this.handler = new OIDCHandler(backend);
    }

    /**
     * Sets the {@link OIDCHandler}
     *
     * @param handler The {@link OIDCHandler} to set
     */
    public void setOIDCHandler(OIDCHandler handler) {
       this.handler = handler;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response, LoginRequestContext requestContext) throws IOException {
        boolean respondWithJson = false;
        try {
            respondWithJson = respondWithJson(request);
            handler.performLogin(request, response, respondWithJson, requestContext);
            if(requestContext.getMetricProvider().isStateUnknown()) {
                requestContext.getMetricProvider().recordSuccess();
            }
        } catch (OXException e) {
            LOG.error(e.getLocalizedMessage(), e);
            if (respondWithJson && response.getWriter() != null) {
                try {
                    requestContext.getMetricProvider().recordException(e);
                    ResponseWriter.writeException(e, new JSONWriter(
                        response.getWriter()).object());
                } catch (JSONException jsonError) {
                    LOG.error(e.getLocalizedMessage(), jsonError);
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (JSONException e) {
            LOG.error(e.getLocalizedMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Returns whether the response should be JSON / the client accepts JSON
     *
     * @param request The {@link HttpServletRequest}
     * @return True, if the client accepts "application/json", false otherwise
     */
    private boolean respondWithJson(HttpServletRequest request) {
        LOG.trace("respondWithJson (HttpServletRequest request: {})", request.getRequestURI());
        String acceptHeader = request.getHeader("Accept");
        return (null != acceptHeader && acceptHeader.equalsIgnoreCase("application/json"));
    }

    /**
     * {@link OIDCHandler} Handles the actual OIDC Login
     *
     * @author <a href="mailto:vitali.sjablow@open-xchange.com">Vitali Sjablow</a>
     * @since v7.10.0
     */
    public static class OIDCHandler {

        private final OIDCBackend backend;

        @SuppressWarnings("hiding")
        static final Logger LOG = LoggerFactory.getLogger(OIDCLoginRequestHandler.OIDCHandler.class);

        /**
         * Initializes a new {@link OIDCHandler}.
         * @param backend The {@link OIDCBackend} to use
         */
        public OIDCHandler(OIDCBackend backend) {
            this.backend = backend;
        }

        /**
         * Internal method to handle an error. Either logs and throws the given exception, or logs and sends the status code to the given response.
         *
         * @param response The response
         * @param respondWithJson  True to log and throw the given exception, false to log and send the given status code
         * @param oxException The exception
         * @param sc The status code
         * @param requestContext The login request context
         * @throws OXException
         * @throws IOException
         */
        private void handleException(HttpServletResponse response, boolean respondWithJson, OXException oxException, int sc, LoginRequestContext requestContext) throws OXException, IOException {
            LOG.trace("handleException (HttpServletResponse response, boolean respondWithJson {}, OXException oxException {}, int sc {})", respondWithJson ? Boolean.TRUE : Boolean.FALSE, oxException, I(sc));
            if (respondWithJson) {
                throw oxException;
            }
            requestContext.getMetricProvider().recordHTTPStatus(sc);
            response.sendError(sc);
        }

        /**
         * Gets the redirect location for the given session
         *
         * @param request The request
         * @param session The session to get the redirect location for
         * @param reservation The reservation
         * @return The relative redirect location of the web frontend.
         * @throws OXException
         */
        private String getRedirectLocationForSession(HttpServletRequest request, Session session, Reservation reservation) throws OXException {
            LOG.trace("getRedirectLocationForSession(HttpServletRequest request: {}, Session session: {}, Reservation reservation: {})", request.getRequestURI(), session.getSessionID(), reservation.getToken());
            OIDCTools.validateSession(session, request);
            return OIDCTools.buildFrontendRedirectLocation(session, OIDCTools.getUIWebPath(backend.getLoginConfiguration(), backend.getBackendConfig()), request.getParameter(OIDCTools.PARAM_DEEP_LINK));
        }

        /**
         * Fetches the session from the given cookie and updates it with the state of the given reservation.
         *
         * @param request The request
         * @param response The response
         * @param reservation The reservation
         * @param sessionCookie The session cookie
         * @param respondWithJson True to response with JSON data, false to send a redirect
         * @param requestContext The login request context
         * @return true on success, false otherwise
         * @throws IOException
         */
        private boolean getAutologinByCookieURL(HttpServletRequest request, HttpServletResponse response, Reservation reservation, Cookie sessionCookie, boolean respondWithJson, LoginRequestContext requestContext) throws IOException {
            LOG.trace("getAutologinByCookieURL(HttpServletRequest request: {}, HttpServletResponse response, Reservation reservation.token: {}, Cookie open-xchange-session Cookie: {}, boolean respondWithJson)", request.getRequestURI(), reservation.getToken(), sessionCookie != null ? sessionCookie.getValue() : "null", respondWithJson ? Boolean.TRUE : Boolean.FALSE);
            if (sessionCookie != null) {
                try {
                    Session session = OIDCTools.getSessionFromSessionCookie(sessionCookie, request);
                    if (session != null) {
                        int expectedContextId = reservation.getContextId();
                        int expectedUserId = reservation.getUserId();
                        if (expectedContextId != session.getContextId() || expectedUserId != session.getUserId()) {
                            // wrong session
                            LOG.debug("Session {} does not match expected session for reservation {}.", session.getSessionID(), reservation.getToken());
                            handleException(null, true, LoginExceptionCodes.LOGIN_DENIED.create(), 0, requestContext);
                            return false;
                        }
                        backend.updateSession(session, reservation.getState());
                        // the getRedirectLocationForSession does also the validation check of the session
                        if (respondWithJson) {
                            this.writeSessionDataAsJson(session, response);
                        } else {
                            String redirectLocationForSession = getRedirectLocationForSession(request, session, reservation);
                            response.sendRedirect(redirectLocationForSession);
                        }
                        return true;
                    }
                    //No session found, log that
                    LOG.debug("No session found for OIDC Cookie with value: {}", sessionCookie.getValue());
                } catch (OXException | JSONException e) {
                    LOG.debug("Ignoring OIDC auto-login attempt due to failed IP or secret check", e);
                }

                Cookie toRemove = (Cookie) sessionCookie.clone();
                toRemove.setMaxAge(0);
                response.addCookie(toRemove);
            }
            return false;
        }

        /**
         * Perform an Appsuite login. If autologin is enabled, the user will be logged in into his session. The
         * autologin mechanism will check if a valid open-xchange-session cookie is available with all needed information.
         * The method will redirect the user to the Appsuite UI afterwards. If <code>respondWithJson</code> is set true,
         * the redirect location will be wrapped into a JSON Object.
         *
         * @param request The {@link HttpServletRequest}
         * @param response The {@link HttpServletResponse}
         * @param respondWithJson Should the UI location should be wrapped into a JSON Object or not
         * @param requestContext The login request context
         * @throws IOException
         * @throws OXException
         * @throws JSONException
         */
        @SuppressWarnings("deprecation")
        private boolean performCookieLogin(HttpServletRequest request, HttpServletResponse response, Reservation reservation, boolean respondWithJson, LoginRequestContext requestContext) throws IOException {
            LOG.trace("performCookieLogin(HttpServletRequest request: {}, HttpServletResponse response, Reservation reservation.token: {}, boolean respondWithJson)", request.getRequestURI(), reservation.getToken(), respondWithJson ? Boolean.TRUE : Boolean.FALSE);
            AutologinMode autologinMode = OIDCBackendConfig.AutologinMode.get(backend.getBackendConfig().autologinCookieMode());

            if (autologinMode.equals(OIDCBackendConfig.AutologinMode.SSO_REDIRECT)) {
                LOG.debug("Deprecated OIDC auto-login with a cookie called by session token {}.", reservation.getToken());
                response.setHeader("Deprecation", "version=\"v7.10.5\"");
            }

            try {
                Cookie autologinCookie = OIDCTools.loadSessionCookie(request, backend.getLoginConfiguration());
                if (null != autologinCookie) {
                    return getAutologinByCookieURL(request, response, reservation, autologinCookie, respondWithJson, requestContext);
                }
            } catch (OXException e) {
                LOG.debug("Ignoring OIDC auto-login attempt due to failed IP or secret check", e);
            }
            return false;
        }

        /**
         * Performs a login
         *
         * @param request The request
         * @param response The response
         * @param respondWithJson True to respond with JSON, false otherwise
         * @param requestContext The context
         * @throws IOException
         * @throws OXException
         * @throws JSONException
         */
        void performLogin(HttpServletRequest request, HttpServletResponse response, boolean respondWithJson, LoginRequestContext requestContext) throws IOException, OXException, JSONException {
            LOG.trace("performLogin(HttpServletRequest request: {}, HttpServletResponse response)", request.getRequestURI());
            String sessionToken = request.getParameter(OIDCTools.SESSION_TOKEN);
            LOG.trace("Login user with session token: {}", sessionToken);
            if (Strings.isEmpty(sessionToken)) {
                handleException(response, respondWithJson, AjaxExceptionCodes.BAD_REQUEST.create(), HttpServletResponse.SC_BAD_REQUEST, requestContext);
                return;
            }

            SessionReservationService sessionReservationService = Services.getServiceSafe(SessionReservationService.class);
            if (sessionReservationService == null) {
                throw ServiceExceptionCode.absentService(SessionReservationService.class);
            }
            Reservation reservation = sessionReservationService.removeReservation(sessionToken);
            if (null == reservation) {
                handleException(response, respondWithJson, LoginExceptionCodes.INVALID_CREDENTIALS.create(), HttpServletResponse.SC_FORBIDDEN, requestContext);
                return;
            }

            String idToken = reservation.getState().get(OIDCTools.IDTOKEN);
            if (Strings.isEmpty(idToken)) {
                handleException(response, respondWithJson, AjaxExceptionCodes.BAD_REQUEST.create(), HttpServletResponse.SC_BAD_REQUEST, requestContext);
                return;
            }

            ContextService contextService = Services.getServiceSafe(ContextService.class);
            Context context = contextService.getContext(reservation.getContextId());
            if (!context.isEnabled()) {
                handleException(response, respondWithJson, LoginExceptionCodes.INVALID_CREDENTIALS.create(), HttpServletResponse.SC_FORBIDDEN, requestContext);
                return;
            }

            UserService userService = Services.getServiceSafe(UserService.class);
            User user = userService.getUser(reservation.getUserId(), context);
            if (!user.isMailEnabled()) {
                handleException(response, respondWithJson, LoginExceptionCodes.INVALID_CREDENTIALS.create(), HttpServletResponse.SC_FORBIDDEN, requestContext);
                return;
            }

            LOG.trace("Try OIDC auto-login with a cookie");
            if (performCookieLogin(request, response, reservation, respondWithJson, requestContext)) {
                return;
            }

            LoginResult result = loginUser(request, context, user, reservation.getState());
            Session session = performSessionAdditions(result, request, response, idToken);

            sendRedirect(session, request, response, respondWithJson);
        }

        /**
         * Performs session additions
         *
         * @param loginResult The login result
         * @param request The request object
         * @param response The response object
         * @param idToken The OIDC ID token
         * @return The session
         * @throws OXException
         */
        private Session performSessionAdditions(LoginResult loginResult, HttpServletRequest request, HttpServletResponse response, String idToken) throws OXException {
            LOG.trace("performSessionAdditions(LoginResult loginResult.sessionID: {}, HttpServletRequest request: {}, HttpServletResponse response, String idToken: {})", loginResult.getSession().getSessionID(), request.getRequestURI(), idToken);
            Session session = loginResult.getSession();

            LoginServlet.addHeadersAndCookies(loginResult, response);

            SessionUtility.rememberSession(request, new ServerSessionAdapter(session));

            LoginServlet.writeSecretCookie(request, response, session, session.getHash(), request.isSecure(), request.getServerName(), backend.getLoginConfiguration());

            LoginServlet.writeSessionCookie(response, session, session.getHash(), request.isSecure(), request.getServerName());

            return session;
        }

        /**
         * Serializes the session data as JSON and writes it to a given response object
         *
         * @param session The session to serialize
         * @param response The response to write the data to
         * @throws JSONException
         * @throws IOException
         */
        private void writeSessionDataAsJson(Session session, HttpServletResponse response) throws JSONException, IOException {
            LOG.trace("writeSessionDataAsJson(Session session {}, HttpServletResponse response)", session.getSessionID());
            JSONObject json = new JSONObject();
            json.putSafe("session", session.getSessionID());
            json.putSafe("user_id", I(session.getUserId()));
            json.putSafe("context_id", I(session.getContextId()));
            response.setStatus(HttpServletResponse.SC_OK);
            response.setCharacterEncoding(Charsets.UTF_8_NAME);
            response.setContentType("application/json");
            PrintWriter writer = response.getWriter();
            json.write(writer);
            writer.flush();
        }

        /**
         * Sets a redirect to the given response object, either as HTTP redirect or JSON response
         *
         * @param session The session
         * @param request The request
         * @param response The response
         * @param respondWithJson Whether to send a JSON response or send a HTTP redirect
         * @throws IOException
         * @throws JSONException
         */
        private void sendRedirect(Session session, HttpServletRequest request, HttpServletResponse response, boolean respondWithJson) throws IOException, JSONException {
            LOG.trace("sendRedirect(Session session: {}, HttpServletRequest request: {}, HttpServletResponse response, boolean respondWithJson {})", session.getSessionID(), request.getRequestURI(), respondWithJson ? Boolean.TRUE : Boolean.FALSE);
            if (respondWithJson) {
                this.writeSessionDataAsJson(session, response);
            } else {
                String uiWebPath = OIDCTools.getUIWebPath(backend.getLoginConfiguration(), backend.getBackendConfig());
                // get possible deeplink
                String frontendRedirectLocation = OIDCTools.buildFrontendRedirectLocation(session, uiWebPath, request.getParameter(OIDCTools.PARAM_DEEP_LINK));
                response.sendRedirect(frontendRedirectLocation);
            }
        }

        /**
         * Performs the login
         *
         * @param request The request
         * @param context The context
         * @param user The user
         * @param state The state
         * @return The login result
         * @throws OXException
         */
        private LoginResult loginUser(HttpServletRequest request, final Context context, final User user, final Map<String, String> state) throws OXException {
            LOG.trace("loginUser(HttpServletRequest request: {}, final Context context: {}, final User user: {}, final Map<String, String> state.size: {})", request.getRequestURI(), I(context.getContextId()), I(user.getId()), I(state.size()));
            final LoginRequest loginRequest = backend.getLoginRequest(request, user.getId(), context.getContextId(), backend.getLoginConfiguration());

            final OIDCBackend colosureBackend = backend;
            return LoginPerformer.getInstance().doLogin(loginRequest, new HashMap<String, Object>(), new LoginMethodClosure() {

                @Override
                public Authenticated doAuthentication(LoginResultImpl loginResult) throws OXException {
                    Authenticated authenticated = colosureBackend.enhanceAuthenticated(OIDCTools.getDefaultAuthenticated(context, user, state), state);

                    return new EnhancedAuthenticated(authenticated) {

                        @Override
                        protected void doEnhanceSession(Session ses) {
                            LOG.trace("doEnhanceSession(Session session: {})", ses.getSessionID());
                            SessionDescription session = (SessionDescription) ses;
                            session.setStaySignedIn(false);

                            Map<String, String> params = new HashMap<>(state);
                            params.put(OIDCTools.BACKEND_PATH, colosureBackend.getPath());
                            OIDCTools.setSessionParameters(session, params);
                        }
                    };
                }
            });
        }
    }
}
