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

package com.openexchange.ajax.login;

import static com.openexchange.ajax.AJAXServlet.PARAMETER_SESSION;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.SessionUtility;
import com.openexchange.ajax.fields.LoginFields;
import com.openexchange.authentication.Authenticated;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.login.LoginResult;
import com.openexchange.login.internal.LoginMethodClosure;
import com.openexchange.login.internal.LoginPerformer;
import com.openexchange.login.internal.LoginResultImpl;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.session.reservation.Enhancer;
import com.openexchange.session.reservation.Reservation;
import com.openexchange.session.reservation.SessionReservationService;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.user.User;


/**
 * {@link RedeemReservationLogin} - A login handler that redeems a token for a session reservation, that
 * was previously obtained via {@link SessionReservationService}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.6.1
 * @see SessionReservationService
 */
public class RedeemReservationLogin implements LoginRequestHandler {

    /** The registered instances of {@code Enhancer} */
    final Queue<Enhancer> enhancers = new ConcurrentLinkedQueue<>();

    /**
     * Initializes a new {@link RedeemReservationLogin}.
     */
    public RedeemReservationLogin() {
        super();
    }

    @Override
    public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
        try {
            doSsoLogin(req, resp, requestContext);
            if(requestContext.getMetricProvider().isStateUnknown()) {
                requestContext.getMetricProvider().recordSuccess();
            }
        } catch (OXException e) {
            LoginTools.useErrorPageTemplateOrSendException(e, getConf().getErrorPageTemplate(), req, resp);
            requestContext.getMetricProvider().recordException(e);
        }
    }

    /**
     * Adds specified instance of {@code Enhancer}
     *
     * @param enhancer The instance to add
     * @return <code>true</code> if successfully added; otherwise <code>false</code>
     */
    public boolean addEnhancer(Enhancer enhancer) {
        if (null == enhancer) {
            return false;
        }
        return enhancers.offer(enhancer);
    }

    /**
     * Removes specified instance of {@code Enhancer}
     *
     * @param enhancer The instance to remove
     * @return <code>true</code> if removed; otherwise <code>false</code>
     */
    public boolean removeEnhancer(Enhancer enhancer) {
        if (null == enhancer) {
            return false;
        }
        return enhancers.remove(enhancer);
    }

    private void doSsoLogin(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws OXException, IOException {
        LoginConfiguration conf = getConf();
        String token = LoginTools.parseToken(req);
        if (null == token) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        SessionReservationService service = ServerServiceRegistry.getInstance().getService(SessionReservationService.class);
        Reservation reservation = null == service ? null : service.removeReservation(token);
        if (null == reservation) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Context context = ContextStorage.getInstance().getContext(reservation.getContextId());
        if (!context.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        User user = UserStorage.getInstance().getUser(reservation.getUserId(), context);
        if (!user.isMailEnabled()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Do the login
        LoginResult result = login(req, context, user, reservation.getState(), conf);

        // Obtain associated session
        Session session = result.getSession();

        // Add session log properties
        LogProperties.putSessionProperties(session);

        // Add headers and cookies from login result
        LoginServlet.addHeadersAndCookies(result, resp);

        // Store session
        SessionUtility.rememberSession(req, new ServerSessionAdapter(session));
        LoginServlet.writeSessionCookie(resp, session, session.getHash(), req.isSecure(), req.getServerName());
        LoginServlet.writeSecretCookie(req, resp, session, session.getHash(), req.isSecure(), req.getServerName(), conf);

        // Send redirect
        String uiWebPath = req.getParameter(LoginFields.UI_WEB_PATH_PARAM);
        if (Strings.isEmpty(uiWebPath)) {
            uiWebPath = conf.getUiWebPath();
        }

        resp.sendRedirect(generateRedirectURL(session, uiWebPath));
    }

    private LoginResult login(HttpServletRequest httpRequest, final Context context, final User user, final Map<String, String> optState, LoginConfiguration loginConfiguration) throws OXException {
        // The properties derived from optional state
        final Map<String, Object> props = optState == null ? new HashMap<String, Object>(4) : new HashMap<String, Object>(optState);

        // The login request
        String login;
        if (null == optState) {
            login = generateDefaultLogin(user, context);
        } else {
            String tmp = optState.get("login");
            login = Strings.isEmpty(tmp) ? generateDefaultLogin(user, context) : tmp;
        }
        String password = null == optState ? null : optState.get("password");
        String defaultClient = loginConfiguration.getDefaultClient();
        final LoginRequestImpl loginRequest = LoginTools.parseLogin(httpRequest, login, password, false, defaultClient, loginConfiguration.isCookieForceHTTPS(), false);

        // Do the login
        return LoginPerformer.getInstance().doLogin(loginRequest, props, new LoginMethodClosure() {
            @Override
            public Authenticated doAuthentication(LoginResultImpl retval) throws OXException {
                Authenticated authenticated = new AuthenticatedImpl(context.getLoginInfo()[0], user.getLoginInfo());
                for (Enhancer enhancer : enhancers) {
                    authenticated = enhancer.enhance(authenticated, optState);
                }
                return authenticated;
            }
        });
    }

    /**
     * Generates the default login string from specified user and context:
     * <pre>
     *  user.getLoginInfo() + "@" + context.getLoginInfo()[0]
     * </pre>
     *
     * @param user The user
     * @param context The context
     * @return The generated default login string
     */
    private String generateDefaultLogin(User user, Context context) {
        return user.getLoginInfo() + '@' + context.getLoginInfo()[0];
    }

    private static String generateRedirectURL(Session session, String uiWebPath) {
        String retval = uiWebPath;

        // Prevent HTTP response splitting.
        retval = retval.replaceAll("[\n\r]", "");
        retval = LoginTools.addFragmentParameter(retval, PARAMETER_SESSION, session.getSessionID());
        return retval;
    }

    private LoginConfiguration getConf() {
        LoginConfiguration conf = LoginServlet.getLoginConfiguration();
        if (conf == null) {
            throw new IllegalStateException("Login action 'redeemReservation' was called but LoginServlet was not fully initialized!");
        }
        return conf;
    }

    // ------------------------------------------------------------------------------------------------------------------------

    private static final class AuthenticatedImpl implements Authenticated {

        private final String contextInfo;

        private final String userInfo;

        /**
         * Initializes a new {@link AuthenticatedImpl}.
         *
         * @param contextInfo
         * @param userInfo
         */
        AuthenticatedImpl(String contextInfo, String userInfo) {
            super();
            this.contextInfo = contextInfo;
            this.userInfo = userInfo;
        }

        @Override
        public String getContextInfo() {
            return contextInfo;
        }

        @Override
        public String getUserInfo() {
            return userInfo;
        }

    }

}
