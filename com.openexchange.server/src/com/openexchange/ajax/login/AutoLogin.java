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

import static com.openexchange.login.Interface.HTTP_JSON;
import static com.openexchange.tools.servlet.http.Tools.copyHeaders;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.SessionUtility;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.writer.LoginWriter;
import com.openexchange.ajax.writer.ResponseWriter;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.exception.OXException;
import com.openexchange.login.LoginRampUpService;
import com.openexchange.login.LoginRequest;
import com.openexchange.login.LoginResult;
import com.openexchange.login.internal.LoginPerformer;
import com.openexchange.login.listener.AutoLoginAwareLoginListener;
import com.openexchange.login.listener.LoginListener;
import com.openexchange.login.listener.internal.LoginListenerRegistryImpl;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.server.services.SessionInspector;
import com.openexchange.session.Reply;
import com.openexchange.session.Session;
import com.openexchange.session.SessionSsoService;
import com.openexchange.session.inspector.Reason;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.servlet.OXJSONExceptionCodes;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link AutoLogin}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 */
public class AutoLogin extends AbstractLoginRequestHandler {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AutoLogin.class);

    private final LoginConfiguration conf;
    private final ShareLoginConfiguration shareConf;

    /**
     * Initializes a new {@link AutoLogin}.
     *
     * @param conf A reference to the login configuration
     * @param shareConf A reference to the share login configuration
     */
    public AutoLogin(LoginConfiguration conf, ShareLoginConfiguration shareConf, Set<LoginRampUpService> rampUp) {
        super(rampUp);
        this.conf = conf;
        this.shareConf = shareConf;
    }

    @Override
    public void handleRequest(final HttpServletRequest req, final HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
        Tools.disableCaching(resp);
        AJAXServlet.setDefaultContentType(resp);
        Response response = new Response();
        Session session = null;
        try {
            /*
             * try guest auto-login first
             */
            LoginResult loginResult = AutoLoginTools.tryGuestAutologin(shareConf.getLoginConfig(), req, resp);
            if (null == loginResult) {
                if (skipAutoLoginForSsoSession(req, resp)) {
                    // Auto-login disabled per configuration.
                    // Try to perform a login using HTTP request/response to see if invocation signals that an auto-login should proceed afterwards
                    if (doAutoLogin(req, resp, requestContext)) {
                        if (Reply.STOP == SessionInspector.getInstance().getChain().onAutoLoginFailed(Reason.AUTO_LOGIN_DISABLED, req, resp)) {
                            requestContext.getMetricProvider().recordErrorCode(AjaxExceptionCodes.DISABLED_ACTION);
                            return;
                        }
                        throw AjaxExceptionCodes.DISABLED_ACTION.create("autologin");
                    }
                    requestContext.getMetricProvider().recordSuccess();
                    return;
                }

                /*
                 * try auto-login for regular user
                 */
                String hash = HashCalculator.getInstance().getHash(req, LoginTools.parseUserAgent(req), LoginTools.parseClient(req, false, conf.getDefaultClient()), LoginTools.parseShareInformation(req));
                loginResult = AutoLoginTools.tryAutologin(conf, req, resp, hash);
                if (null == loginResult) {
                    /*
                     * auto-login failed
                     */
                    SessionUtility.removeOXCookies(hash, req, resp);
                    if (doAutoLogin(req, resp, requestContext)) {
                        SessionUtility.removeJSESSIONID(req, resp);
                        if (Reply.STOP == SessionInspector.getInstance().getChain().onAutoLoginFailed(Reason.AUTO_LOGIN_FAILED, req, resp)) {
                            requestContext.getMetricProvider().recordErrorCode(OXJSONExceptionCodes.INVALID_COOKIE);
                            return;
                        }
                        throw OXJSONExceptionCodes.INVALID_COOKIE.create();
                    }
                    requestContext.getMetricProvider().recordSuccess();
                    return;
                }
            }
            /*
             * auto-login successful, prepare result
             */
            {
                List<LoginListener> listeners = LoginListenerRegistryImpl.getInstance().getLoginListeners();
                for (LoginListener loginListener : listeners) {
                    if (loginListener instanceof AutoLoginAwareLoginListener) {
                        ((AutoLoginAwareLoginListener) loginListener).onSucceededAutoLogin(loginResult);
                    }
                }
            }
            ServerSession serverSession = ServerSessionAdapter.valueOf(loginResult.getSession(), loginResult.getContext(), loginResult.getUser());
            session = serverSession;

            // Trigger client-specific ramp-up
            Future<JSONObject> optRampUp = rampUpAsync(serverSession, req);

            // Request modules
            Future<Object> optModules = getModulesAsync(session, req);

            // Create JSON object
            final JSONObject json = new JSONObject(8);
            LoginWriter.write(session, json);

            // Append "config/modules"
            if (null != optModules) {
                try {
                    final Object oModules = optModules.get();
                    if (null != oModules) {
                        json.put("modules", oModules);
                    }
                } catch (InterruptedException e) {
                    // Keep interrupted state
                    Thread.currentThread().interrupt();
                    throw LoginExceptionCodes.UNKNOWN.create(e, "Thread interrupted.");
                } catch (ExecutionException e) {
                    // Cannot occur
                    final Throwable cause = e.getCause();
                    LOG.warn("Modules could not be added to login JSON response", cause);
                }
            }

            // Await client-specific ramp-up and add to JSON object
            if (null != optRampUp) {
                try {
                    JSONObject jsonObject = optRampUp.get();
                    for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                        json.put(entry.getKey(), entry.getValue());
                    }
                } catch (InterruptedException e) {
                    // Keep interrupted state
                    Thread.currentThread().interrupt();
                    throw LoginExceptionCodes.UNKNOWN.create(e, "Thread interrupted.");
                } catch (ExecutionException e) {
                    // Cannot occur
                    final Throwable cause = e.getCause();
                    LOG.warn("Ramp-up information could not be added to login JSON response", cause);
                }
            }

            // Set data
            response.setData(json);
            requestContext.getMetricProvider().recordSuccess();

            /*-
             * Ensure appropriate public-session-cookie is set
             */
            LoginServlet.writePublicSessionCookie(req, resp, session, req.isSecure(), req.getServerName());

        } catch (OXException e) {
            if (AjaxExceptionCodes.DISABLED_ACTION.equals(e)) {
                LOG.debug("", e);
            } else {
                switch (e.getCategories().get(0).getLogLevel()) {
                    case TRACE:
                        LOG.trace("", e);
                        break;
                    case DEBUG:
                        LOG.debug("", e);
                        break;
                    case INFO:
                        LOG.info("", e);
                        break;
                    case WARNING:
                        LOG.warn("", e);
                        break;
                    case ERROR:
                        LOG.error("", e);
                        break;
                    default:
                        break;
                }
            }
            if (SessionUtility.isSessionExpiredError(e) && null != session) {
                try {
                    // Drop Open-Xchange cookies
                    final SessiondService sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class);
                    SessionUtility.removeOXCookies(session.getHash(), req, resp);
                    SessionUtility.removeJSESSIONID(req, resp);
                    sessiondService.removeSession(session.getSessionID());
                } catch (Exception e2) {
                    LOG.error("Cookies could not be removed.", e2);
                }
            }
            response.setException(e);
        } catch (JSONException e) {
            final OXException oje = OXJSONExceptionCodes.JSON_WRITE_ERROR.create(e);
            LOG.error("", oje);
            response.setException(oje);
        }
        // The magic spell to disable caching
        Tools.disableCaching(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
        AJAXServlet.setDefaultContentType(resp);
        try {
            if (response.hasError()) {
                ResponseWriter.write(response, resp.getWriter(), LoginServlet.localeFrom(session));
                requestContext.getMetricProvider().recordException(response.getException());
            } else {
                ((JSONObject) response.getData()).write(resp.getWriter());
            }
        } catch (JSONException e) {
            requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            LOG.error(LoginServlet.RESPONSE_ERROR, e);
            LoginServlet.sendError(resp);
        }
    }

    /**
     * Performs a login while providing the auto-login {@link LoginClosure closure}.
     *
     * @param req The associated HTTP request
     * @param resp The associated HTTP response
     * @param requestContext The request's context
     * @return <code>true</code> if an auto login should proceed afterwards; otherwise <code>false</code>
     */
    private boolean doAutoLogin(final HttpServletRequest req, final HttpServletResponse resp, LoginRequestContext requestContext) throws IOException, OXException {
        return loginOperation(req, resp, new LoginClosure() {

            @Override
            public LoginResult doLogin(final HttpServletRequest req2) throws OXException {
                final LoginRequest request = parseAutoLoginRequest(req2);
                return LoginPerformer.getInstance().doAutoLogin(request);
            }
        }, conf, requestContext);
    }

    /**
     * Parses the given HTTP request into an appropriate {@link LoginRequest} instance.
     *
     * @param req The HTTP request to parse
     * @return The resulting {@link LoginRequest} instance
     * @throws OXException If parse operation fails
     */
    LoginRequest parseAutoLoginRequest(final HttpServletRequest req) throws OXException {
        final String authId = LoginTools.parseAuthId(req, false);
        final String client = LoginTools.parseClient(req, false, conf.getDefaultClient());
        final String clientIP = LoginTools.parseClientIP(req);
        final String userAgent = LoginTools.parseUserAgent(req);
        final Map<String, List<String>> headers = copyHeaders(req);
        final com.openexchange.authentication.Cookie[] cookies = Tools.getCookieFromHeader(req);
        final HttpSession httpSession = req.getSession(true);

        LoginRequestImpl.Builder b = new LoginRequestImpl.Builder().login(null).password(null).clientIP(clientIP);
        b.userAgent(userAgent).authId(authId).client(client).version(null);
        b.hash(HashCalculator.getInstance().getHash(req, client));
        b.iface(HTTP_JSON).headers(headers).requestParameter(req.getParameterMap());
        b.cookies(cookies).secure(Tools.considerSecure(req, conf.isCookieForceHTTPS()));
        b.serverName(req.getServerName()).serverPort(req.getServerPort()).httpSession(httpSession);
        return b.build();
    }

    /**
     * Checks whether auto login attempt shall be skipped due to SSO requirements.
     *
     * @param req The associated HTTP request
     * @param resp The associated HTTP response
     * @return <code>true</code> if auto login should abort afterwards; otherwise <code>false</code>
     */
    private static boolean skipAutoLoginForSsoSession(HttpServletRequest req, HttpServletResponse resp) {
        SessionSsoService ssoService = ServerServiceRegistry.getInstance().getService(SessionSsoService.class);
        if (ssoService != null) {
            try {
                return ssoService.skipAutoLoginAttempt(req, resp);
            } catch (OXException e) {
                LOG.warn("Error while checking if autologin shall be skipped due to SSO requirements", e);
            }
        }

        return false;
    }

}
