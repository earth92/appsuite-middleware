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

package com.openexchange.ajax;

import static com.openexchange.ajax.ConfigMenu.convert2JS;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.tools.servlet.http.Cookies.getDomainValue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.fields.LoginFields;
import com.openexchange.ajax.helper.Send;
import com.openexchange.ajax.ipcheck.IPCheckService;
import com.openexchange.ajax.login.AnonymousLogin;
import com.openexchange.ajax.login.AutoLogin;
import com.openexchange.ajax.login.FormLogin;
import com.openexchange.ajax.login.GuestLogin;
import com.openexchange.ajax.login.HTTPAuthLogin;
import com.openexchange.ajax.login.HasAutoLogin;
import com.openexchange.ajax.login.HashCalculator;
import com.openexchange.ajax.login.Login;
import com.openexchange.ajax.login.LoginConfiguration;
import com.openexchange.ajax.login.LoginRequestContext;
import com.openexchange.ajax.login.LoginRequestHandler;
import com.openexchange.ajax.login.LoginTools;
import com.openexchange.ajax.login.RampUp;
import com.openexchange.ajax.login.RedeemToken;
import com.openexchange.ajax.login.ShareLoginConfiguration;
import com.openexchange.ajax.login.ShareLoginConfiguration.ShareLoginProperty;
import com.openexchange.ajax.login.TokenLogin;
import com.openexchange.ajax.login.Tokens;
import com.openexchange.ajax.writer.LoginWriter;
import com.openexchange.ajax.writer.ResponseWriter;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.config.ConfigTools;
import com.openexchange.configuration.CookieHashSource;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.configuration.ServerConfig.Property;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.settings.Setting;
import com.openexchange.groupware.settings.impl.ConfigTree;
import com.openexchange.groupware.settings.impl.SettingStorage;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.login.ConfigurationProperty;
import com.openexchange.login.LoginRampUpService;
import com.openexchange.login.LoginResult;
import com.openexchange.login.internal.LoginPerformer;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.DefaultSessionAttributes;
import com.openexchange.session.Reply;
import com.openexchange.session.Session;
import com.openexchange.session.SessionResult;
import com.openexchange.sessiond.ExpirationReason;
import com.openexchange.sessiond.SessionExceptionCodes;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.tools.io.IOTools;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.servlet.http.Cookies;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.servlet.ratelimit.RateLimitedException;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * Servlet doing the login and logout stuff.
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public class LoginServlet extends AJAXServlet {

    private static final long serialVersionUID = 7680745138705836499L;

    protected static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LoginServlet.class);

    /**
     * The path appendix for login servlet.
     */
    public static final String SERVLET_PATH_APPENDIX = "login";

    /** The log properties for login-related information. */
    protected static final Set<LogProperties.Name> LOG_PROPERTIES;

    static {
        final Set<LogProperties.Name> set = EnumSet.noneOf(LogProperties.Name.class);
        set.add(LogProperties.Name.LOGIN_AUTH_ID);
        set.add(LogProperties.Name.LOGIN_CLIENT);
        set.add(LogProperties.Name.LOGIN_CLIENT_IP);
        set.add(LogProperties.Name.LOGIN_LOGIN);
        set.add(LogProperties.Name.LOGIN_RESOLVED_LOGIN);
        set.add(LogProperties.Name.LOGIN_USER_AGENT);
        set.add(LogProperties.Name.LOGIN_VERSION);
        set.add(LogProperties.Name.SESSION_AUTH_ID);
        set.add(LogProperties.Name.SESSION_SESSION_ID);
        set.add(LogProperties.Name.SESSION_USER_ID);
        set.add(LogProperties.Name.SESSION_USER_NAME);
        set.add(LogProperties.Name.SESSION_CONTEXT_ID);
        set.add(LogProperties.Name.SESSION_CLIENT_ID);
        set.add(LogProperties.Name.SESSION_SESSION);
        LOG_PROPERTIES = Collections.unmodifiableSet(set);
    }

    /**
     * The default error page template
     */
    private static final String ERROR_PAGE_TEMPLATE =
        // @formatter:off
            "<html>\n" +
            "<script type=\"text/javascript\">\n" +
            "// Display normal HTML for 5 seconds, then redirect via referrer.\n" +
            "setTimeout(redirect,5000);\n" +
            "function redirect(){\n" +
            " var referrer=document.referrer;\n" +
            " var redirect_url;\n" +
            " // If referrer already contains failed parameter, we don't add a 2nd one.\n" +
            " if (referrer.indexOf(\"login=failed\")>=0){\n" +
            "  redirect_url=referrer;\n" + " }else{\n" +
            "  // Check if referrer contains multiple parameter\n" +
            "  if (referrer.indexOf(\"?\")<0){\n" +
            "   redirect_url=referrer+\"?login=failed\";\n" +
            "  }else{\n" +
            "   redirect_url=referrer+\"&login=failed\";\n" +
            "  }\n" + " }\n" + " // Redirect to referrer\n" +
            " window.location.href=redirect_url;\n" +
            "}\n" + "</script>\n" +
            "<body>\n" +
            "<h1>ERROR_MESSAGE</h1>\n" +
            "</body>\n" +
            "</html>\n";
        // @formatter:on

    /**
     * <code>"open-xchange-session-"</code>
     */
    public static final String SESSION_PREFIX = "open-xchange-session-".intern();

    /**
     * <code>"open-xchange-secret-"</code>
     */
    public static final String SECRET_PREFIX = "open-xchange-secret-".intern();

    /**
     * <code>"open-xchange-share-"</code>
     */
    public static final String SHARE_PREFIX = "open-xchange-share-".intern();

    /**
     * <code>"open-xchange-public-session-"</code>
     */
    public static final String PUBLIC_SESSION_PREFIX = "open-xchange-public-session-".intern();

    /**
     * <code>"open-xchange-shard"</code>
     */
    public static final String SHARD_COOKIE_NAME = "open-xchange-shard".intern();

    public static final String ACTION_FORMLOGIN = "formlogin";

    public static final String ACTION_TOKENLOGIN = "tokenLogin";

    public static final String ACTION_TOKENS = "tokens";

    public static final String ACTION_REDEEM_TOKEN = "redeemToken";

    /**
     * <code>"changeip"</code>
     */
    public static final String ACTION_CHANGEIP = "changeip".intern();

    private static enum CookieType {
        SESSION, SECRET;
    }

    /** The ramp-up services reference */
    private static final AtomicReference<Set<LoginRampUpService>> RAMP_UP_REF = new AtomicReference<Set<LoginRampUpService>>();

    /**
     * Sets the ramp-up services.
     *
     * @param services The ramp-up services or <code>null</code> to clear
     */
    public static void setRampUpServices(final Set<LoginRampUpService> services) {
        RAMP_UP_REF.set(services);
    }

    /** The login configuration reference */
    static final AtomicReference<LoginConfiguration> confReference = new AtomicReference<LoginConfiguration>();

    /** The login configuration reference */
    static final AtomicReference<ShareLoginConfiguration> shareConfReference = new AtomicReference<ShareLoginConfiguration>();

    /**
     * Gets the login configuration.
     *
     * @return The login configuration or <code>null</code> if not yet initialized
     */
    public static LoginConfiguration getLoginConfiguration() {
        return confReference.get();
    }

    /**
     * Gets the share login configuration.
     *
     * @return The share login configuration or <code>null</code> if not yet initialized
     */
    public static ShareLoginConfiguration getShareLoginConfiguration() {
        return shareConfReference.get();
    }

    /**
     * Gets the login configuration suitable for the supplied session.
     *
     * @param session The session to get the login configuration for
     * @return The login configuration
     */
    public static LoginConfiguration getLoginConfiguration(Session session) {
        LoginConfiguration defaultLoginConfig = getLoginConfiguration();
        if (null != session && Boolean.TRUE.equals(session.getParameter(Session.PARAM_GUEST))) {
            return getShareLoginConfiguration().getLoginConfig(defaultLoginConfig);
        }
        return defaultLoginConfig;
    }

    /**
     * Gets the name of the public session cookie for specified HTTP request.
     *
     * <pre>
     * "open-xchange-public-session-" + &lt;hash(req.userAgent)&gt;
     * </pre>
     *
     * @param req The HTTP request
     * @return The name of the public session cookie
     */
    public static String getPublicSessionCookieName(final HttpServletRequest req, String[] additionals) {
        return new StringBuilder(PUBLIC_SESSION_PREFIX).append(HashCalculator.getInstance().getHash(req, HashCalculator.getUserAgent(req), null, additionals)).toString();
    }

    /**
     * Gets the name of the share cookie for specified HTTP request.
     * <pre>
     * "open-xchange-share-" + &lt;hash(req.userAgent)&gt;
     * </pre>
     *
     * @param request The HTTP request
     * @return The name of the public session cookie
     */
    public static String getShareCookieName(HttpServletRequest request) {
        return new StringBuilder(SHARE_PREFIX).append(HashCalculator.getInstance().getUserAgentHash(request)).toString();
    }

    // --------------------------------------------------------------------------------------- //

    private final ConcurrentMap<String, LoginRequestHandler> handlerMap;

    public LoginServlet() {
        super();
        ConcurrentMap<String, LoginRequestHandler> handlerMap = new ConcurrentHashMap<String, LoginRequestHandler>(16, 0.9f, 1);
        handlerMap.put(ACTION_STORE, new LoginRequestHandler() {

            @Override
            public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
                LOG.debug("Deprecated action 'store' called by {}.", req.getParameter(PARAMETER_SESSION));
                resp.setHeader("Deprecation", "version=\"v7.10.3\"");
                resp.setStatus(200);
                resp.flushBuffer();
                if(requestContext != null) {
                    requestContext.getMetricProvider().recordSuccess();
                }
            }
        });
        handlerMap.put(ACTION_REFRESH_SECRET, new LoginRequestHandler() {

            @Override
            public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
                try {
                    doRefreshSecret(req, resp);
                } catch (OXException e) {
                    logAndSendException(resp, e);
                    requestContext.getMetricProvider().recordException(e);
                } catch (JSONException e) {
                    log(RESPONSE_ERROR, e);
                    sendError(resp);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        });
        handlerMap.put(ACTION_LOGOUT, new LoginRequestHandler() {

            @Override
            public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
                // The magic spell to disable caching
                Tools.disableCaching(resp);
                setDefaultContentType(resp);
                final String sessionId = req.getParameter(PARAMETER_SESSION);
                if (sessionId == null) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                try {
                    Session session = LoginPerformer.getInstance().lookupSession(sessionId);
                    if (session == null) {
                        LOG.info("Status code 403 (FORBIDDEN): No such session.");
                        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                        requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }

                    SessionUtility.checkIP(session, req.getRemoteAddr());

                    String[] additionalsForHash;
                    if (Boolean.TRUE.equals(session.getParameter(Session.PARAM_GUEST))) {
                        /*
                         * inject context- and user-id to allow parallel guest sessions
                         */
                        additionalsForHash = new String[] { Integer.toString(session.getContextId()), Integer.toString(session.getUserId()) };
                    } else {
                        additionalsForHash = null;
                    }

                    LoginConfiguration conf = getLoginConfiguration(session);
                    String secret = SessionUtility.extractSecret(conf.getHashSource(), req, session.getHash(), session.getClient(), null, additionalsForHash);
                    if (secret == null || !session.getSecret().equals(secret)) {
                        LOG.info("Status code 403 (FORBIDDEN): Missing or non-matching secret.");
                        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                        requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }

                    // Do the logout
                    LoginPerformer.getInstance().doLogout(sessionId);

                    // Drop relevant cookies
                    SessionUtility.removeOXCookies(session, req, resp);
                    SessionUtility.removeJSESSIONID(req, resp);

                    requestContext.getMetricProvider().recordSuccess();
                } catch (OXException e) {
                    requestContext.getMetricProvider().recordException(e);
                    if (SessionUtility.isSessionExpiredError(e)) {
                        LOG.info("Status code 403 (FORBIDDEN): Session expired.");
                        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                        requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                    LOG.error("Logout failed", e);
                } catch (RuntimeException e) {
                    LOG.error("Logout failed", e);
                    requestContext.getMetricProvider().recordUnknown();
                }
            }
        });
        handlerMap.put(ACTION_REDIRECT, new LoginRequestHandler() {

            @Override
            public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
                final LoginConfiguration conf = confReference.get();
                // The magic spell to disable caching
                Tools.disableCaching(resp);
                setDefaultContentType(resp);
                String randomToken = null;
                if (conf.isRandomTokenEnabled()) {
                    randomToken = req.getParameter(LoginFields.RANDOM_PARAM);
                }
                if (randomToken == null) {
                    final String msg = "Random token is disable (as per default since considered as insecure). See \"com.openexchange.ajax.login.randomToken\" in 'login.properties' file.";
                    LOG.warn(msg, new Throwable(msg));
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                final SessiondService sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class);
                if (sessiondService == null) {
                    final OXException se = ServiceExceptionCode.SERVICE_UNAVAILABLE.create(SessiondService.class.getName());
                    LOG.error("", se);
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                final Session session;
                if (conf.isInsecure()) {
                    if (conf.isRedirectIPChangeAllowed()) {
                        session = sessiondService.getSessionByRandomToken(randomToken, req.getRemoteAddr());
                    } else {
                        session = sessiondService.getSessionByRandomToken(randomToken);
                        if (null != session) {
                            try {
                                String oldIP = session.getLocalIp();

                                boolean changeIp = false;
                                if (null == oldIP) {
                                    changeIp = true;
                                } else {
                                    IPCheckService ipCheckService = ServerServiceRegistry.getInstance().getService(IPCheckService.class);
                                    if (SessionUtility.isWhitelistedFromIPCheck(oldIP,  ipCheckService.getConfigurationFor(session).getRanges())) {
                                        changeIp = true;
                                    }
                                }

                                if (changeIp) {
                                    String newIP = req.getRemoteAddr();
                                    if (!newIP.equals(oldIP)) {
                                        LOG.info("Changing IP of session {} with authID: {} from {} to {}.", session.getSessionID(), session.getAuthId(), oldIP, newIP);
                                        sessiondService.setSessionAttributes(session.getSessionID(), DefaultSessionAttributes.builder().withLocalIp(newIP).build());
                                    }
                                }
                            } catch (OXException e) {
                                LOG.error("", e);
                                requestContext.getMetricProvider().recordException(e);
                                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                                return;
                            }
                        }
                    }
                } else {
                    // No IP change.
                    session = sessiondService.getSessionByRandomToken(randomToken);
                }
                if (session == null) {
                    // Unknown random token; throw error
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("No session could be found for random token: {}", randomToken, new Throwable());
                    } else {
                        LOG.info("No session could be found for random token: {}", randomToken);
                    }
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                // Add session log properties
                LogProperties.putSessionProperties(session);
                // Remove old cookies to prevent usage of the old autologin cookie
                if (conf.isInsecure()) {
                    SessionUtility.removeOXCookies(session, req, resp);
                }
                try {
                    final Context context = ContextStorage.getInstance().getContext(session.getContextId());
                    final User user = UserStorage.getInstance().getUser(session.getUserId(), context);
                    if (!context.isEnabled() || !user.isMailEnabled()) {
                        LOG.info("Status code 403 (FORBIDDEN): Either context {} or user {} not enabled", I(context.getContextId()), I(user.getId()));
                        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                        requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                } catch (UndeclaredThrowableException e) {
                    LOG.info("Status code 403 (FORBIDDEN): Unexpected error occurred during login: {}", e.getMessage());
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                } catch (OXException e) {
                    LOG.info("Status code 403 (FORBIDDEN): Couldn't resolve context/user by identifier: {}/{}", I(session.getContextId()), I(session.getUserId()));
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordException(e);
                    return;
                }

                String client = req.getParameter(LoginFields.CLIENT_PARAM);
                final String hash;
                if (!conf.isInsecure()) {
                    hash = session.getHash();
                } else {
                    if (null == client) {
                        client = session.getClient();
                    } else {
                        session.setClient(client);
                    }
                    hash = HashCalculator.getInstance().getHash(req, client);
                    session.setHash(hash);
                }
                writeSecretCookie(req, resp, session, hash, req.isSecure(), req.getServerName(), conf);
                resp.sendRedirect(LoginTools.generateRedirectURL(req.getParameter(LoginFields.UI_WEB_PATH_PARAM), session.getSessionID(), conf.getUiWebPath()));
                requestContext.getMetricProvider().recordSuccess();
            }
        });
        handlerMap.put(ACTION_CHANGEIP, new LoginRequestHandler() {

            @Override
            public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
                final Response response = new Response();
                Session session = null;
                try {
                    final String sessionId = req.getParameter(PARAMETER_SESSION);
                    if (null == sessionId) {
                        LOG.info("Parameter \"{}\" not found for action {}", PARAMETER_SESSION, ACTION_CHANGEIP);
                        requestContext.getMetricProvider().recordErrorCode(AjaxExceptionCodes.MISSING_PARAMETER);
                        throw AjaxExceptionCodes.MISSING_PARAMETER.create(PARAMETER_SESSION);
                    }
                    final String newIP = req.getParameter(LoginFields.CLIENT_IP_PARAM);
                    if (null == newIP) {
                        LOG.info("Parameter \"{}\" not found for action {}", LoginFields.CLIENT_IP_PARAM, ACTION_CHANGEIP);
                        requestContext.getMetricProvider().recordErrorCode(AjaxExceptionCodes.MISSING_PARAMETER);
                        throw AjaxExceptionCodes.MISSING_PARAMETER.create(LoginFields.CLIENT_IP_PARAM);
                    }
                    final SessiondService sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class, true);
                    session = sessiondService.getSession(sessionId);
                    if (session != null) {
                        // Add session log properties
                        LogProperties.putSessionProperties(session);
                        // Check
                        SessionUtility.checkIP(session, req.getRemoteAddr());
                        LoginConfiguration conf = confReference.get();
                        String secret = SessionUtility.extractSecret(conf.getHashSource(), req, session.getHash(), session.getClient());
                        if (secret == null) {
                            requestContext.getMetricProvider().recordErrorCode(SessionExceptionCodes.SESSION_EXPIRED);
                            OXException oxe = SessionExceptionCodes.SESSION_EXPIRED.create(session.getSessionID());
                            oxe.setProperty(SessionExceptionCodes.OXEXCEPTION_PROPERTY_SESSION_EXPIRATION_REASON, ExpirationReason.NO_EXPECTED_SECRET_COOKIE.getIdentifier());
                            throw oxe;
                        } else if (!session.getSecret().equals(secret)) {
                            LOG.info("Session secret is different. Given secret \"{}\" differs from secret in session \"{}\".", secret, session.getSecret());
                            requestContext.getMetricProvider().recordErrorCode(SessionExceptionCodes.SESSION_EXPIRED);
                            OXException oxe = SessionExceptionCodes.SESSION_EXPIRED.create(session.getSessionID());
                            oxe.setProperty(SessionExceptionCodes.OXEXCEPTION_PROPERTY_SESSION_EXPIRATION_REASON, ExpirationReason.SECRET_MISMATCH.getIdentifier());
                            throw oxe;
                        }
                        final String oldIP = session.getLocalIp();
                        if (!newIP.equals(oldIP)) {
                            // In case changing IP is intentionally requested by client, log it only if DEBUG aka FINE log level is enabled
                            LOG.info("Changing IP of session {} with authID: {} from {} to {}", session.getSessionID(), session.getAuthId(), oldIP, newIP);
                            session.setLocalIp(newIP);
                        }
                        response.setData("1");
                    } else {
                        LOG.info("There is no session associated with session identifier: {}", sessionId);
                        requestContext.getMetricProvider().recordErrorCode(SessionExceptionCodes.SESSION_EXPIRED);
                        OXException oxe = SessionExceptionCodes.SESSION_EXPIRED.create(sessionId);
                        oxe.setProperty(SessionExceptionCodes.OXEXCEPTION_PROPERTY_SESSION_EXPIRATION_REASON, ExpirationReason.NO_SUCH_SESSION.getIdentifier());
                        throw oxe;
                    }
                } catch (OXException e) {
                    LOG.debug("", e);
                    response.setException(e);
                }
                Tools.disableCaching(resp);
                setDefaultContentType(resp);
                resp.setStatus(HttpServletResponse.SC_OK);
                try {
                    ResponseWriter.write(response, resp.getWriter(), localeFrom(session));
                } catch (JSONException e) {
                    log(RESPONSE_ERROR, e);
                    sendError(resp);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }

                if(requestContext.getMetricProvider().isStateUnknown()) {
                    requestContext.getMetricProvider().recordSuccess();
                }
            }
        });
        handlerMap.put(ACTION_REDEEM, new LoginRequestHandler() {

            @Override
            public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
                final LoginConfiguration conf = confReference.get();
                // The magic spell to disable caching
                Tools.disableCaching(resp);
                setDefaultContentType(resp);
                String randomToken = null;
                if (conf.isRandomTokenEnabled()) {
                    randomToken = req.getParameter(LoginFields.RANDOM_PARAM);
                }
                if (randomToken == null) {
                    final String msg = "Random token is disable (as per default since considered as insecure). See \"com.openexchange.ajax.login.randomToken\" in 'login.properties' file.";
                    LOG.warn(msg, new Throwable(msg));
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                final SessiondService sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class);
                if (sessiondService == null) {
                    final OXException se = ServiceExceptionCode.SERVICE_UNAVAILABLE.create(SessiondService.class.getName());
                    LOG.error("", se);
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                final Session session;
                if (conf.isInsecure()) {
                    if (conf.isRedirectIPChangeAllowed()) {
                        session = sessiondService.getSessionByRandomToken(randomToken, req.getRemoteAddr());
                    } else {
                        session = sessiondService.getSessionByRandomToken(randomToken);
                        if (null != session) {
                            try {
                                String oldIP = session.getLocalIp();

                                boolean changeIp = false;
                                if (null == oldIP) {
                                    changeIp = true;
                                } else {
                                    IPCheckService ipCheckService = ServerServiceRegistry.getInstance().getService(IPCheckService.class);
                                    if (SessionUtility.isWhitelistedFromIPCheck(oldIP,  ipCheckService.getConfigurationFor(session).getRanges())) {
                                        changeIp = true;
                                    }
                                }

                                if (changeIp) {
                                    String newIP = req.getRemoteAddr();
                                    if (!newIP.equals(oldIP)) {
                                        LOG.info("Changing IP of session {} with authID: {} from {} to {}.", session.getSessionID(), session.getAuthId(), oldIP, newIP);
                                        sessiondService.setSessionAttributes(session.getSessionID(), DefaultSessionAttributes.builder().withLocalIp(newIP).build());
                                    }
                                }
                            } catch (OXException e) {
                                LOG.error("", e);
                                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                                requestContext.getMetricProvider().recordException(e);
                                return;
                            }
                        }
                    }
                } else {
                    // No IP change.
                    session = sessiondService.getSessionByRandomToken(randomToken);
                }
                if (session == null) {
                    // Unknown random token; throw error
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("No session could be found for random token: {}", randomToken, new Throwable());
                    } else {
                        LOG.info("No session could be found for random token: {}", randomToken);
                    }
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                // Add session log properties
                LogProperties.putSessionProperties(session);
                // Remove old cookies to prevent usage of the old autologin cookie
                if (conf.isInsecure()) {
                    SessionUtility.removeOXCookies(session, req, resp);
                }
                try {
                    final Context context = ContextStorage.getInstance().getContext(session.getContextId());
                    final User user = UserStorage.getInstance().getUser(session.getUserId(), context);
                    if (!context.isEnabled() || !user.isMailEnabled()) {
                        LOG.info("Status code 403 (FORBIDDEN): Either context {} or user {} not enabled", I(context.getContextId()), I(user.getId()));
                        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                        requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                } catch (UndeclaredThrowableException e) {
                    LOG.info("Status code 403 (FORBIDDEN): Unexpected error occurred during login: {}", e.getMessage());
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                } catch (OXException e) {
                    LOG.info("Status code 403 (FORBIDDEN): Couldn't resolve context/user by identifier: {}/{}", I(session.getContextId()), I(session.getUserId()));
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    requestContext.getMetricProvider().recordException(e);
                    return;
                }

                String client = req.getParameter(LoginFields.CLIENT_PARAM);
                final String hash;
                if (!conf.isInsecure()) {
                    hash = session.getHash();
                } else {
                    if (null == client) {
                        client = session.getClient();
                    } else {
                        session.setClient(client);
                    }
                    hash = HashCalculator.getInstance().getHash(req, client);
                    session.setHash(hash);
                }
                writeSecretCookie(req, resp, session, hash, req.isSecure(), req.getServerName(), conf);

                try {
                    final JSONObject json = new JSONObject();
                    LoginWriter.write(session, json);
                    // Append "config/modules"
                    appendModules(session, json, req);
                    json.write(resp.getWriter());
                    if(requestContext.getMetricProvider().isStateUnknown()) {
                        requestContext.getMetricProvider().recordSuccess();
                    }
                } catch (JSONException e) {
                    log(RESPONSE_ERROR, e);
                    sendError(resp);
                }
            }
        });
        this.handlerMap = handlerMap;
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        String uiWebPath = config.getInitParameter(ServerConfig.Property.UI_WEB_PATH.getPropertyName());
        CookieHashSource hashSource = CookieHashSource.parse(config.getInitParameter(Property.COOKIE_HASH.getPropertyName()));
        String httpAuthAutoLogin = config.getInitParameter(ConfigurationProperty.HTTP_AUTH_AUTOLOGIN.getPropertyName());
        String defaultClient = config.getInitParameter(ConfigurationProperty.HTTP_AUTH_CLIENT.getPropertyName());
        String clientVersion = config.getInitParameter(ConfigurationProperty.HTTP_AUTH_VERSION.getPropertyName());
        String templateFileLocation = config.getInitParameter(ConfigurationProperty.ERROR_PAGE_TEMPLATE.getPropertyName());
        String errorPageTemplate;
        if (null == templateFileLocation) {
            errorPageTemplate = ERROR_PAGE_TEMPLATE;
        } else {
            final File templateFile = new File(templateFileLocation);
            try {
                errorPageTemplate = IOTools.getFileContents(templateFile);
                LOG.info("Found an error page template at {}", templateFileLocation);
            } catch (FileNotFoundException e) {
                LOG.error("Could not find an error page template at {}, using default.", templateFileLocation);
                errorPageTemplate = ERROR_PAGE_TEMPLATE;
            }
        }
        int cookieExpiry = ConfigTools.parseTimespanSecs(config.getInitParameter(ServerConfig.Property.COOKIE_TTL.getPropertyName()));
        boolean cookieForceHTTPS = Boolean.parseBoolean(config.getInitParameter(ServerConfig.Property.COOKIE_FORCE_HTTPS.getPropertyName())) || Boolean.parseBoolean(config.getInitParameter(ServerConfig.Property.FORCE_HTTPS.getPropertyName()));
        boolean insecure = Boolean.parseBoolean(config.getInitParameter(ConfigurationProperty.INSECURE.getPropertyName()));
        boolean redirectIPChangeAllowed = Boolean.parseBoolean(config.getInitParameter(ConfigurationProperty.REDIRECT_IP_CHANGE_ALLOWED.getPropertyName()));
        boolean disableTrimLogin = Boolean.parseBoolean(config.getInitParameter(ConfigurationProperty.DISABLE_TRIM_LOGIN.getPropertyName()));
        boolean formLoginWithoutAuthId = Boolean.parseBoolean(config.getInitParameter(ConfigurationProperty.FORM_LOGIN_WITHOUT_AUTHID.getPropertyName()));
        boolean isRandomTokenEnabled = Boolean.parseBoolean(config.getInitParameter(ConfigurationProperty.RANDOM_TOKEN.getPropertyName()));
        boolean checkPunyCodeLoginString = Boolean.parseBoolean(config.getInitParameter(ConfigurationProperty.CHECK_PUNY_CODE_LOGIN.getPropertyName()));
        LoginConfiguration conf = new LoginConfiguration(uiWebPath, hashSource, httpAuthAutoLogin, defaultClient, clientVersion, errorPageTemplate, cookieExpiry, cookieForceHTTPS, insecure, redirectIPChangeAllowed, disableTrimLogin, formLoginWithoutAuthId, isRandomTokenEnabled, checkPunyCodeLoginString);
        confReference.set(conf);
        ShareLoginConfiguration shareConf = initShareLoginConfig(config);
        shareConfReference.set(shareConf);
        handlerMap.put(ACTION_FORMLOGIN, new FormLogin(conf));
        handlerMap.put(ACTION_TOKENLOGIN, new TokenLogin(conf));
        handlerMap.put(ACTION_TOKENS, new Tokens(conf));
        handlerMap.put(ACTION_REDEEM_TOKEN, new RedeemToken(conf));
        final Set<LoginRampUpService> rampUpServices = RAMP_UP_REF.get();
        handlerMap.put(ACTION_AUTOLOGIN, new AutoLogin(conf, shareConf, rampUpServices));
        handlerMap.put(ACTION_LOGIN, new Login(conf, rampUpServices));
        handlerMap.put(ACTION_RAMPUP, new RampUp(rampUpServices));
        handlerMap.put("hasAutologin", HasAutoLogin.getInstance());
        handlerMap.put("/httpAuth", new HTTPAuthLogin(conf));
        handlerMap.put(ACTION_GUEST, new GuestLogin(shareConf, rampUpServices));
        handlerMap.put(ACTION_ANONYMOUS, new AnonymousLogin(shareConf, rampUpServices));
    }

    public LoginRequestHandler addRequestHandler(String action, LoginRequestHandler handler) {
        LoginRequestHandler prev = handlerMap.putIfAbsent(action, handler);
        return null == prev ? handler : prev;
    }

    public LoginRequestHandler removeRequestHandler(String action) {
        return handlerMap.remove(action);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doService(req, resp, false);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final long startTime = System.currentTimeMillis();
        try {
            Tools.checkNonExistence(req, PARAMETER_PASSWORD);
        } catch (OXException oxException) {
            logAndSendException(resp, oxException);
            return;
        }

        try {
            final String action = req.getParameter(PARAMETER_ACTION);
            final String subPath = getServletSpecificURI(req);
            final LoginRequestContext requestContext = new LoginRequestContext();
            if (null != subPath && subPath.startsWith("/httpAuth")) {
                try {
                    handlerMap.get("/httpAuth").handleRequest(req, resp, requestContext);
                } catch (IOException e) {
                    requestContext.getMetricProvider().recordErrorCode(LoginExceptionCodes.UNKNOWN);
                    throw e;
                }
            } else {
                // Regular login handling
                try {
                    doJSONAuth(req, resp, action, requestContext);
                } catch (IOException e) {
                    requestContext.getMetricProvider().recordErrorCode(LoginExceptionCodes.UNKNOWN);
                    throw e;
                }
            }
            recordLoginRequest(action, requestContext.getMetricProvider().getStatus(), System.currentTimeMillis() - startTime);
        } catch (RateLimitedException e) {
            e.send(resp);
        } finally {
            LogProperties.removeProperties(LOG_PROPERTIES);
        }
    }

    /**
     * Records a login request
     *
     * @param action The action to record
     * @param status The status of the login request
     * @param durationMillis The duration of the request in milliseconds
     */
    private static void recordLoginRequest(String action, String status, long durationMillis) {
        if(action != null && status != null) {
            Timer timer = Timer.builder("appsuite.httpapi.requests")
                .tags("module", "login", "action", action, "status", status)
                .description("HTTP API request times")
                .serviceLevelObjectives(
                    Duration.ofMillis(50),
                    Duration.ofMillis(100),
                    Duration.ofMillis(150),
                    Duration.ofMillis(200),
                    Duration.ofMillis(250),
                    Duration.ofMillis(300),
                    Duration.ofMillis(400),
                    Duration.ofMillis(500),
                    Duration.ofMillis(750),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(1))
                .register(Metrics.globalRegistry);
            timer.record(durationMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void doJSONAuth(final HttpServletRequest req, final HttpServletResponse resp, final String action, LoginRequestContext requestContext) throws IOException {
        if (null == action) {
            final OXException exp = AjaxExceptionCodes.MISSING_PARAMETER.create(PARAMETER_ACTION);
            logAndSendException(resp, exp);
            requestContext.getMetricProvider().recordException(exp);
            return;
        }

        LoginRequestHandler handler = handlerMap.get(action);
        if (null == handler) {
            final OXException exp = AjaxExceptionCodes.UNKNOWN_ACTION.create(action);
            logAndSendException(resp, exp);
            requestContext.getMetricProvider().recordException(exp);
            return;
        }
        handler.handleRequest(req, resp, requestContext);
    }

    /**
     * Writes or rewrites a cookie
     */
    private void doCookieReWrite(final HttpServletRequest req, final HttpServletResponse resp, final CookieType type) throws OXException, JSONException, IOException {
        final SessiondService sessiond = ServerServiceRegistry.getInstance().getService(SessiondService.class);
        if (null == sessiond) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(SessiondService.class.getName());
        }
        final String sessionId = req.getParameter(PARAMETER_SESSION);
        if (null == sessionId) {
            throw AjaxExceptionCodes.MISSING_PARAMETER.create(PARAMETER_SESSION);
        }
        SessionResult<ServerSession> result = SessionUtility.getSession(req, resp, sessionId, sessiond);
        if (Reply.STOP == result.getReply()) {
            return;
        }
        Session session = result.getSession();
        if (null == session) {
            // Should not occur
            OXException oxe = SessionExceptionCodes.SESSION_EXPIRED.create(sessionId);
            oxe.setProperty(SessionExceptionCodes.OXEXCEPTION_PROPERTY_SESSION_EXPIRATION_REASON, ExpirationReason.NO_SUCH_SESSION.getIdentifier());
            throw oxe;
        }
        final LoginConfiguration conf = getLoginConfiguration(session);
        try {
            SessionUtility.checkIP(session, req.getRemoteAddr());
            if (type == CookieType.SESSION) {
                writeSessionCookie(resp, session, session.getHash(), req.isSecure(), req.getServerName());
            } else {
                writeSecretCookie(req, resp, session, session.getHash(), req.isSecure(), req.getServerName(), conf);
            }
            // Refresh HTTP session, too
            req.getSession();
            final Response response = new Response();
            response.setData("1");
            ResponseWriter.write(response, resp.getWriter(), localeFrom(session));
        } finally {
            LogProperties.removeSessionProperties();
        }
    }

    protected void doRefreshSecret(final HttpServletRequest req, final HttpServletResponse resp) throws OXException, JSONException, IOException {
        Tools.disableCaching(resp);
        setDefaultContentType(resp);
        doCookieReWrite(req, resp, CookieType.SECRET);
    }

    /**
     * Logs specified exceptions and responds an appropriate error to the client.
     *
     * @param resp The HTTP response
     * @param e The exception
     * @throws IOException If exception cannot be send due to an I/O error
     */
    public static void logAndSendException(HttpServletResponse resp, OXException e) throws IOException {
        LOG.debug("", e);

        if (AjaxExceptionCodes.BAD_REQUEST.equals(e)) {
            SessionServlet.sendErrorAndPage(HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), resp);
            return;
        }

        if (AjaxExceptionCodes.HTTP_ERROR.equals(e)) {
            Object[] logArgs = e.getLogArgs();
            Object statusMsg = logArgs.length > 1 ? logArgs[1] : null;
            int sc = ((Integer) logArgs[0]).intValue();
            SessionServlet.sendErrorAndPage(sc, null == statusMsg ? null : statusMsg.toString(), resp);
            return;
        }

        Send.sendResponse(new Response().setException(e), resp);
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }

    public static void addHeadersAndCookies(final LoginResult result, final HttpServletResponse resp) {
        final com.openexchange.authentication.Cookie[] cookies = result.getCookies();
        if (null != cookies) {
            for (final com.openexchange.authentication.Cookie cookie : cookies) {
                resp.addCookie(wrapCookie(cookie, result));
            }
        }
        final com.openexchange.authentication.Header[] headers = result.getHeaders();
        if (null != headers) {
            for (final com.openexchange.authentication.Header header : headers) {
                resp.addHeader(header.getName(), header.getValue());
            }
        }
    }

    private static Cookie wrapCookie(final com.openexchange.authentication.Cookie cookie, LoginResult result) {
        Cookie servletCookie = new Cookie(cookie.getName(), cookie.getValue());
        LoginConfiguration loginConfig = getLoginConfiguration(result.getSession());
        Session session = result.getSession();
        configureCookie(servletCookie, result.getRequest().isSecure(), result.getRequest().getServerName(), loginConfig, null != session && session.isStaySignedIn());
        return servletCookie;
    }

    /**
     * Appends the modules to given JSON object.
     *
     * @param session The associated session
     * @param json The JSON object to append to
     * @param req The request
     */
    protected static void appendModules(final Session session, final JSONObject json, final HttpServletRequest req) {
        final String modules = "modules";
        if (parseBoolean(req.getParameter(modules))) {
            try {
                final Setting setting = ConfigTree.getInstance().getSettingByPath(modules);
                SettingStorage.getInstance(session).readValues(setting);
                json.put(modules, convert2JS(setting));
            } catch (Exception e) {
                LOG.warn("Modules could not be added to login JSON response", e);
            }
        }
    }

    /**
     * Parses the specified parameter to a <code>boolean</code> value.
     *
     * @param parameter The parameter value
     * @return <code>true</code> if parameter is <b>not</b> <code>null</code> and is (ignore-case) one of the values <code>"true"</code>,
     *         <code>"1"</code>, <code>"yes"</code> or <code>"on"</code>; otherwise <code>false</code>
     */
    public static boolean parseBoolean(final String parameter) {
        return "true".equalsIgnoreCase(parameter) || "1".equals(parameter) || "yes".equalsIgnoreCase(parameter) || "y".equalsIgnoreCase(parameter) || "on".equalsIgnoreCase(parameter);
    }

    /**
     * Writes the (groupware's) session cookie to specified HTTP servlet response whose name is composed by cookie prefix
     * <code>"open-xchange-session-"</code> and a secret cookie identifier.
     *
     * @param resp The HTTP servlet response
     * @param session The session providing the secret cookie identifier
     * @param hash The hash string used for composing cookie name
     * @param secure <code>true</code> to set cookie's secure flag; otherwise <code>false</code>
     * @param serverName The HTTP request's server name
     */
    public static void writeSessionCookie(final HttpServletResponse resp, final Session session, final String hash, final boolean secure, final String serverName) throws OXException {
        resp.addCookie(configureCookie(new Cookie(SESSION_PREFIX + hash, session.getSessionID()), secure, serverName, getLoginConfiguration(session), session.isStaySignedIn()));
    }

    /**
     * Writes the (groupware's) shard cookie to specified HTTP servlet response whose name is
     * <code>"open-xchange-shard"</code>.
     *
     * @param resp The HTTP servlet response
     * @param session The session providing the secret cookie identifier
     * @param secure <code>true</code> to set cookie's secure flag; otherwise <code>false</code>
     * @param serverName The HTTP request's server name
     */
    public static void writeShardCookie(final HttpServletResponse resp, final Session session, final boolean secure, final String serverName) {
        resp.addCookie(configureCookie(new Cookie(SHARD_COOKIE_NAME, SessionUtility.getShardCookieValue()), secure, serverName, getLoginConfiguration(session), null != session && session.isStaySignedIn()));
    }

    /**
     * Writes the (groupware's) secret cookie to specified HTTP servlet response whose name is composed by cookie prefix
     * <code>"open-xchange-secret-"</code> and a secret cookie identifier.
     *
     * @param req The HTTP request
     * @param resp The HTTP response
     * @param session The session providing the secret cookie identifier
     * @param hash The hash string used for composing cookie name
     * @param secure <code>true</code> to set cookie's secure flag; otherwise <code>false</code>
     * @param serverName The HTTP request's server name
     * @param conf The login configuration
     */
    public static void writeSecretCookie(HttpServletRequest req, HttpServletResponse resp, Session session, String hash, boolean secure, String serverName, LoginConfiguration conf) {
        resp.addCookie(configureCookie(new Cookie(SECRET_PREFIX + hash, session.getSecret()), secure, serverName, conf, session.isStaySignedIn()));
        writePublicSessionCookie(req, resp, session, secure, serverName, conf);
        writeShardCookie(resp, session, secure, serverName);
        session.setParameter(Session.PARAM_COOKIE_REFRESH_TIMESTAMP, Long.valueOf(System.currentTimeMillis()));
    }

    /**
     * Writes the (groupware's) secret cookie to specified HTTP servlet response whose name is composed by cookie prefix
     * <code>"open-xchange-secret-"</code> and a secret cookie identifier.
     *
     * @param req The HTTP request
     * @param resp The HTTP response
     * @param session The session providing the secret cookie identifier
     * @param hash The hash string used for composing cookie name
     * @param secure <code>true</code> to set cookie's secure flag; otherwise <code>false</code>
     * @param serverName The HTTP request's server name
     * @throws OXException
     */
    public static void writeSecretCookie(HttpServletRequest req, HttpServletResponse resp, Session session, String hash, boolean secure, String serverName) throws OXException {
        writeSecretCookie(req, resp, session, hash, secure, serverName, getLoginConfiguration(session));
    }

    /**
     * Writes the (groupware's) public session cookie <code>"open-xchange-public-session"</code> to specified HTTP servlet response.
     *
     * @param req The HTTP request
     * @param resp The HTTP response
     * @param session The session providing the public session cookie identifier
     * @param secure <code>true</code> to set cookie's secure flag; otherwise <code>false</code>
     * @param serverName The HTTP request's server name
     * @return <code>true</code> if successfully added to HTTP servlet response; otherwise <code>false</code>
     */
    public static boolean writePublicSessionCookie(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final boolean secure, final String serverName) {
        return writePublicSessionCookie(req, resp, session, secure, serverName, getLoginConfiguration(session));
    }

    /**
     * Writes the (groupware's) public session cookie <code>"open-xchange-public-session"</code> to specified HTTP servlet response.
     *
     * @param req The HTTP request
     * @param resp The HTTP response
     * @param session The session providing the public session cookie identifier
     * @param secure <code>true</code> to set cookie's secure flag; otherwise <code>false</code>
     * @param serverName The HTTP request's server name
     * @param conf The login configuration
     * @return <code>true</code> if successfully added to HTTP servlet response; otherwise <code>false</code>
     */
    private static boolean writePublicSessionCookie(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final boolean secure, final String serverName, final LoginConfiguration conf) {
        final String altId = (String) session.getParameter(Session.PARAM_ALTERNATIVE_ID);
        if (null != altId) {
            resp.addCookie(configureCookie(new Cookie(getPublicSessionCookieName(req, new String[] { String.valueOf(session.getContextId()), String.valueOf(session.getUserId()) }), altId), secure, serverName, conf, session.isStaySignedIn()));
            return true;
        }
        return false;
    }

    /**
     * Configures specific cookie properties, which includes setting the cookie path to <code>/</code>, applying the <code>secure</code>
     * flag and domain setting the max-age and cookie domain.
     *
     * @param cookie The cookie to configure
     * @param secure <code>true</code> to enforce the cookie's <code>secure</code>-flag, <code>false</code> to set the flag based on configuration
     * @param serverName The server name as extracted from the request
     * @param conf the login configuration
     * @param staySignedIn <code>true</code> to persist cookie, <code>false</code> to delete cookie when web browser is closed
     * @return The cookie
     */
    public static Cookie configureCookie(final Cookie cookie, final boolean secure, final String serverName, final LoginConfiguration conf, boolean staySignedIn) {
        cookie.setPath("/");
        if (secure || (conf.isCookieForceHTTPS() && !Cookies.isLocalLan(serverName))) {
            cookie.setSecure(true);
        }
        if (staySignedIn && 0 < conf.getCookieExpiry()) {
            /*
             * A negative value means that the cookie is not stored persistently and will be deleted when the Web browser exits. A zero
             * value causes the cookie to be deleted.
             */
            cookie.setMaxAge(conf.getCookieExpiry());
        }
        final String domain = getDomainValue(null == serverName ? determineServerNameByLogProperty() : serverName);
        if (null != domain) {
            cookie.setDomain(domain);
        }
        return cookie;
    }

    /**
     * Configures specific cookie properties based on configuration and the incoming request, which includes setting the cookie path
     * to <code>/</code>, applying the <code>secure</code> flag and domain setting the max-age and cookie domain.
     *
     * @param cookie The cookie to configure
     * @param request The underlying servlet request
     * @param loginConfig the login configuration
     * @param staySignedIn <code>true</code> to persist cookie, <code>false</code> to delete cookie when web browser is closed
     * @return The cookie
     */
    public static Cookie configureCookie(Cookie cookie, HttpServletRequest request, LoginConfiguration loginConfig, boolean staySignedIn) {
        return configureCookie(cookie, request.isSecure(), request.getServerName(), loginConfig, staySignedIn);
    }

    private static String determineServerNameByLogProperty() {
        return LogProperties.getLogProperty(LogProperties.Name.GRIZZLY_SERVER_NAME);
    }

    /**
     * Initializes a share login configuration based on the initialization parameters found in the supplied servlet config reference.
     *
     * @param config The servlet configuration to use for initialization
     * @return The initialized share login configuration
     */
    private static ShareLoginConfiguration initShareLoginConfig(ServletConfig config) {
        String shareClientName = config.getInitParameter(ShareLoginProperty.CLIENT_NAME.getPropertyName());
        String  shareClientVersion = config.getInitParameter(ShareLoginProperty.CLIENT_VERSION.getPropertyName());
        String shareCookieTTLValue = config.getInitParameter(ShareLoginProperty.COOKIE_TTL.getPropertyName());
        Integer shareCookieTTL;
        if (String.valueOf(-1).equals(shareCookieTTLValue)) {
            shareCookieTTL = Integer.valueOf(-1);
        } else {
            shareCookieTTL = Strings.isEmpty(shareCookieTTLValue) ? null : Integer.valueOf(ConfigTools.parseTimespanSecs(shareCookieTTLValue));
        }
        boolean shareTransientSessions = Boolean.parseBoolean(config.getInitParameter(ShareLoginProperty.TRANSIENT_SESSIONS.getPropertyName()));
        return new ShareLoginConfiguration(shareClientName, shareClientVersion, shareCookieTTL, shareTransientSessions);
    }
}
