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

package com.openexchange.login.internal;

import static com.openexchange.java.Autoboxing.I;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.auth.login.LoginException;
import com.openexchange.ajax.fields.LoginFields;
import com.openexchange.authentication.Authenticated;
import com.openexchange.authentication.Cookie;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.authentication.ResolvedAuthenticated;
import com.openexchange.authentication.ResponseEnhancement;
import com.openexchange.authentication.ResultCode;
import com.openexchange.authentication.SessionEnhancement;
import com.openexchange.authentication.application.RestrictedAuthentication;
import com.openexchange.authorization.Authorization;
import com.openexchange.authorization.AuthorizationService;
import com.openexchange.configuration.ServerProperty;
import com.openexchange.database.DBPoolingExceptionCodes;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextExceptionCodes;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.UserImpl;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.upgrade.SegmentedUpdateService;
import com.openexchange.groupware.userconfiguration.UserConfiguration;
import com.openexchange.groupware.userconfiguration.UserConfigurationStorage;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.log.audit.AuditLogService;
import com.openexchange.log.audit.DefaultAttribute;
import com.openexchange.log.audit.DefaultAttribute.Name;
import com.openexchange.login.Blocking;
import com.openexchange.login.LoginHandlerService;
import com.openexchange.login.LoginRequest;
import com.openexchange.login.LoginResult;
import com.openexchange.login.NonTransient;
import com.openexchange.login.internal.format.DefaultLoginFormatter;
import com.openexchange.login.internal.format.LoginFormatter;
import com.openexchange.login.listener.LoginListener;
import com.openexchange.login.listener.internal.LoginListenerRegistryImpl;
import com.openexchange.login.multifactor.MultifactorChecker;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.threadpool.ThreadPoolCompletionService;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.threadpool.behavior.CallerRunsBehavior;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link LoginPerformer} - Performs a login for specified credentials.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class LoginPerformer {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LoginPerformer.class);

    private static final LoginPerformer SINGLETON = new LoginPerformer();

    /**
     * Gets the {@link LoginPerformer} instance.
     *
     * @return The instance
     */
    public static LoginPerformer getInstance() {
        return SINGLETON;
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Initializes a new {@link LoginPerformer}.
     */
    private LoginPerformer() {
        super();
    }

    /**
     * Performs the login for specified login request.
     *
     * @param request The login request
     * @return The result providing login information
     * @throws LoginException If login fails
     */
    public LoginResult doLogin(final LoginRequest request) throws OXException {
        return doLogin(request, new HashMap<String, Object>(1));
    }

    /**
     * Performs the login for specified login request passing arbitrary properties.
     *
     * @param request The login request
     * @param properties The arbitrary properties; e.g. <code>"headers"</code> or <code>{@link com.openexchange.authentication.Cookie "cookies"}</code>
     * @return The result providing login information
     * @throws LoginException If login fails
     */
    public LoginResult doLogin(final LoginRequest request, final Map<String, Object> properties) throws OXException {
        return doLogin(request, properties, new NormalLoginMethod(request, properties));
    }

    /**
     * Performs the auto login for the specified login request and with the specified properties
     *
     * @param request The login request
     * @param properties The properties
     * @return The result providing login information
     * @throws OXException If login fails
     */
    public LoginResult doAutoLogin(LoginRequest request, Map<String, Object> properties) throws OXException {
        return doLogin(request, properties, new AutoLoginMethod(request, properties));
    }

    /**
     * Performs the login for specified login request.
     *
     * @param request The login request
     * @return The result providing login information
     * @throws OXException If login fails
     */
    public LoginResult doAutoLogin(final LoginRequest request) throws OXException {
        final Map<String, Object> properties = new HashMap<String, Object>(1);
        return doLogin(request, properties, new AutoLoginMethod(request, properties));
    }

    /**
     * Performs the login for specified login request.
     *
     * @param request The login request
     * @param properties The properties to decorate; e.g. <code>"headers"</code> or <code>{@link com.openexchange.authentication.Cookie "cookies"}</code>
     * @param loginMethod The actual login method that performs authentication
     * @return The result providing login information
     * @throws OXException If login fails
     */
    public LoginResult doLogin(LoginRequest request, Map<String, Object> properties, LoginMethodClosure loginMethod) throws OXException {
        // Sanity check for given login request
        sanityChecks(request);

        // Check needed service
        SessiondService sessiondService = SessiondService.SERVICE_REFERENCE.get();
        if (null == sessiondService) {
            sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class);
            if (null == sessiondService) {
                // Giving up...
                throw ServiceExceptionCode.absentService(SessiondService.class);
            }
        }

        // Look-up possible login listeners
        List<LoginListener> listeners = LoginListenerRegistryImpl.getInstance().getLoginListeners();

        // Start login...
        LoginResultImpl retval = new LoginResultImpl();
        retval.setRequest(request);
        Cookie[] cookies = null;
        try {
            // Call onBeforeAuthentication
            for (LoginListener listener : listeners) {
                listener.onBeforeAuthentication(request, properties);
            }

            // Proceed...
            Map<String, List<String>> headers = request.getHeaders();
            if (headers != null) {
                properties.put("headers", headers);
            }
            cookies = request.getCookies();
            if (null != cookies) {
                properties.put("cookies", cookies);
            }
            final Authenticated authed = loginMethod.doAuthentication(retval);
            if (null == authed) {
                return null;
            }
            if (authed instanceof ResponseEnhancement) {
                final ResponseEnhancement responseEnhancement = (ResponseEnhancement) authed;
                retval.setHeaders(responseEnhancement.getHeaders());
                retval.setCookies(responseEnhancement.getCookies());
                retval.setRedirect(responseEnhancement.getRedirect());
                final ResultCode code = responseEnhancement.getCode();
                retval.setCode(code);
                if (ResultCode.REDIRECT.equals(code) || ResultCode.FAILED.equals(code)) {
                    if (ResultCode.FAILED.equals(code)) {
                        // Call onFailedAuthentication
                        for (LoginListener listener : listeners) {
                            listener.onFailedAuthentication(request, properties, null);
                        }
                    } else if (ResultCode.REDIRECT.equals(code)) {
                        // Call onRedirectedAuthentication
                        for (LoginListener listener : listeners) {
                            listener.onRedirectedAuthentication(request, properties, null);
                        }
                    }
                    return retval;
                }
            }

            // Get user & context
            Context ctx;
            User user;
            if (ResolvedAuthenticated.class.isInstance(authed)) {
                // use already resolved user / context
                ResolvedAuthenticated guestAuthenticated = (ResolvedAuthenticated) authed;
                ctx = getContext(guestAuthenticated.getContextID());
                user = getUser(ctx, guestAuthenticated.getUserID());
            } else {
                // Perform user / context lookup
                ctx = findContext(authed.getContextInfo());
                user = findUser(ctx, authed.getUserInfo());
            }

            // Checks if something is deactivated.
            AuthorizationService authService = Authorization.getService();
            if (null == authService) {
                final OXException e = ServiceExceptionCode.SERVICE_UNAVAILABLE.create(AuthorizationService.class.getName());
                LOG.error("unable to find AuthorizationService", e);
                throw e;
            }

            // Authorize
            authService.authorizeUser(ctx, user);

            // Store locale if requested by client during login request
            user = LoginPerformer.storeLanguageIfNeeded(request, user, ctx);
            retval.setContext(ctx);
            retval.setUser(user);

            // Check if indicated client is allowed to perform a login
            checkClient(request, user, ctx);

            // Perform multi-factor authentication if enabled for the user and mark session
            SessionEnhancement multifactorSessionEnhancement = null;
            {
                MultifactorChecker multifactorCheck = ServerServiceRegistry.getInstance().getService(MultifactorChecker.class);
                if (multifactorCheck != null) {
                    Optional<SessionEnhancement> optionalEnhancement = multifactorCheck.checkMultiFactorAuthentication(request, ctx, user);
                    if (optionalEnhancement.isPresent()) {
                        multifactorSessionEnhancement = optionalEnhancement.get();
                    }
                }
            }

            // Compile parameters for adding a session
            AddSessionParameterImpl addSessionParam;

            if (authed instanceof RestrictedAuthentication) {
                addSessionParam = new AddSessionParameterImpl(authed.getUserInfo(), request, user, ctx, ((RestrictedAuthentication) authed).getPassword());
                addSessionParam.addEnhancement(((RestrictedAuthentication) authed).getSessionEnhancement());
                multifactorSessionEnhancement = null;  // Bypass Multifactor
            } else {
                addSessionParam = new AddSessionParameterImpl(authed.getUserInfo(), request, user, ctx);
            }

            if (SessionEnhancement.class.isInstance(authed)) {
                addSessionParam.addEnhancement((SessionEnhancement) authed);
            }

            if (multifactorSessionEnhancement != null) {
                addSessionParam.addEnhancement(multifactorSessionEnhancement);
            }

            // Finally, add the session
            Session session = sessiondService.addSession(addSessionParam);
            if (null == session) {
                // Session could not be created
                throw LoginExceptionCodes.UNKNOWN.create("Session could not be created.");
            }

            LogProperties.putSessionProperties(session);
            retval.setServerToken((String) session.getParameter(LoginFields.SERVER_TOKEN));
            retval.setSession(session);

            // Trigger registered login handlers
            triggerLoginHandlers(retval);

            // Call onSucceededAuthentication
            for (LoginListener listener : listeners) {
                listener.onSucceededAuthentication(retval);
            }

            // Mark HTTP session
            request.markHttpSessionAuthenticated();

            return retval;
        } catch (OXException e) {
            if (DBPoolingExceptionCodes.PREFIX.equals(e.getPrefix())) {
                LOG.error(e.getLogMessage(), e);
            }
            // Redirect
            if (ContextExceptionCodes.LOCATED_IN_ANOTHER_SERVER.equals(e)) {
                SegmentedUpdateService segmentedUpdateService = ServerServiceRegistry.getInstance().getService(SegmentedUpdateService.class);
                String migrationRedirectURL = segmentedUpdateService == null ? null : segmentedUpdateService.getMigrationRedirectURL(request.getServerName());
                if (Strings.isEmpty(migrationRedirectURL)) {
                    LOG.error("Cannot redirect. The property '{}' is not set.", ServerProperty.migrationRedirectURL.getFQPropertyName());
                } else {
                    OXException redirectExc = LoginExceptionCodes.REDIRECT.create(migrationRedirectURL);
                    // Call onRedirectedAuthentication
                    for (LoginListener listener : listeners) {
                        listener.onRedirectedAuthentication(request, properties, redirectExc);
                    }
                    throw redirectExc;
                }
            }
            if (LoginExceptionCodes.REDIRECT.equals(e)) {
                // Call onRedirectedAuthentication
                for (LoginListener listener : listeners) {
                    listener.onRedirectedAuthentication(request, properties, e);
                }
            } else {
                // Call onFailedAuthentication
                for (LoginListener listener : listeners) {
                    listener.onFailedAuthentication(request, properties, e);
                }
            }
            throw e;
        } catch (RuntimeException e) {
            OXException oxe = LoginExceptionCodes.UNKNOWN.create(e, e.getMessage());
            // Call onFailedAuthentication
            for (LoginListener listener : listeners) {
                listener.onFailedAuthentication(request, properties, oxe);
            }
            throw oxe;
        } finally {
            logLoginRequest(request, retval);
        }
    }

    /**
     * Performs sanity checks
     *
     * @param request The request to check
     * @throws OXException If a sanity check fails
     */
    public static void sanityChecks(LoginRequest request) throws OXException {
        // Check if somebody is using the User-Agent as client parameter
        String client = request.getClient();
        if (null != client && client.equals(request.getUserAgent())) {
            throw LoginExceptionCodes.DONT_USER_AGENT.create();
        }
    }

    /**
     * Checks given request's client
     *
     * @param request The request
     * @param user The resolved user
     * @param ctx The resolved context
     * @throws OXException If check fails
     */
    public static void checkClient(final LoginRequest request, final User user, final Context ctx) throws OXException {
        final String client = request.getClient();
        // Check for OLOX v2.0
        if ("USM-JSON".equalsIgnoreCase(client)) {
            final UserConfigurationStorage ucs = UserConfigurationStorage.getInstance();
            final UserConfiguration userConfiguration = ucs.getUserConfiguration(user.getId(), user.getGroups(), ctx);
            if (!userConfiguration.hasOLOX20()) {
                // Deny login for OLOX v2.0 client since disabled as per user configuration
                throw LoginExceptionCodes.CLIENT_DENIED.create(client);
            }
        }
    }

    /**
     * Stores the user language supplied by the client during the login if explicitly requested via
     * {@link LoginFields#STORE_LANGUAGE} / {@link LoginFields#STORE_LOCALE}.
     *
     * @param request The login request to get the parameters controlling the desired language from
     * @param user The logged in user
     * @param ctx The context
     * @return The (possibly modified) user, or the passed user reference if not changed
     */
    public static User storeLanguageIfNeeded(LoginRequest request, User user, Context ctx) throws OXException {
        if (false == request.isStoreLanguage()) {
            return user;
        }
        String userLoginLanguage = request.getLanguage();
        if (Strings.isNotEmpty(userLoginLanguage) && !userLoginLanguage.equals(user.getPreferredLanguage())) {
            UserImpl impl = new UserImpl(user);
            impl.setPreferredLanguage(userLoginLanguage);
            UserService userService = ServerServiceRegistry.getInstance().getService(UserService.class);
            if (null != userService) {
                userService.updateUser(impl, ctx);
            } else {
                LOG.warn("Unable to access user service, updating directly via storage.", ServiceExceptionCode.absentService(UserService.class));
                UserStorage.getInstance().updateUser(impl, ctx);
            }
            return impl;
        }
        return user;
    }

    /**
     * Looks up the context for the supplied context info, throwing appropriate exceptions if not found.
     *
     * @param contextInfo The context info (as usually supplied in the login name)
     * @return The context
     * @throws OXException If context look-up fails
     */
    public static Context findContext(String contextInfo) throws OXException {
        ContextStorage contextStor = ContextStorage.getInstance();

        int contextId = contextStor.getContextId(contextInfo);
        if (ContextStorage.NOT_FOUND == contextId) {
            throw ContextExceptionCodes.NO_MAPPING.create(contextInfo);
        }

        Context context = contextStor.getContext(contextId);
        if (null == context) {
            throw ContextExceptionCodes.NOT_FOUND.create(I(contextId));
        }

        return context;
    }

    /**
     * Looks up the user for the supplied user info in a context, throwing appropriate exceptions if not found.
     *
     * @param ctx The context
     * @param userInfo The user info (as usually supplied in the login name)
     * @return The user
     * @throws OXException If user look-up fails
     */
    public static User findUser(Context ctx, String userInfo) throws OXException {
        String proxyDelimiter = MailProperties.getInstance().getAuthProxyDelimiter();
        UserStorage us = UserStorage.getInstance();

        int userId;
        if (null != proxyDelimiter && userInfo.contains(proxyDelimiter)) {
            userId = us.getUserId(userInfo.substring(userInfo.indexOf(proxyDelimiter) + proxyDelimiter.length(), userInfo.length()), ctx);
        } else {
            userId = us.getUserId(userInfo, ctx);
        }

        return us.getUser(userId, ctx);
    }

    /**
     * Gets a context by it's identifier from the context storage.
     *
     * @param contextID The context ID
     * @return The context
     * @throws OXException
     */
    private static Context getContext(int contextID) throws OXException {
        final Context context = ContextStorage.getInstance().getContext(contextID);
        if (null == context) {
            throw ContextExceptionCodes.NOT_FOUND.create(I(contextID));
        }
        return context;
    }

    /**
     * Gets a user by it's identifier from the user storage.
     *
     * @param ctx The context
     * @param userID The user ID
     * @return The user
     * @throws OXException
     */
    private static User getUser(Context ctx, int userID) throws OXException {
        return UserStorage.getInstance().getUser(userID, ctx);
    }

    /**
     * Performs the logout for specified session ID.
     *
     * @param sessionId The session ID
     * @throws OXException If logout fails
     */
    public Session doLogout(String sessionId) throws OXException {
        // Drop the session
        SessiondService sessiondService = SessiondService.SERVICE_REFERENCE.get();
        if (null == sessiondService) {
            sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class);
            if (null == sessiondService) {
                // Giving up...
                throw ServiceExceptionCode.absentService(SessiondService.class);
            }
        }

        // Obtain session
        Session session = sessiondService.getSession(sessionId);
        if (null == session) {
            LOG.debug("No session found for ID: {}", sessionId);
            return null;
        }

        // Get context
        Context context;
        {
            ContextStorage contextStor = ContextStorage.getInstance();
            context = contextStor.getContext(session.getContextId());
            if (null == context) {
                throw ContextExceptionCodes.NOT_FOUND.create(Integer.valueOf(session.getContextId()));
            }
        }

        // Get user
        User user;
        {
            final UserStorage us = UserStorage.getInstance();
            user = us.getUser(session.getUserId(), context);
        }

        // Remove session
        sessiondService.removeSession(sessionId);

        // Log logout performed
        LoginResultImpl logout = new LoginResultImpl(session, context, user);
        logLogout(logout);

        // Trigger registered logout handlers
        triggerLogoutHandlers(logout);
        return session;
    }

    /**
     * Triggers the login handlers
     *
     * @param login The login
     */
    public static void triggerLoginHandlers(final LoginResult login) {
        final ThreadPoolService executor = ThreadPools.getThreadPool();
        if (null == executor) {
            for (final Iterator<LoginHandlerService> it = LoginHandlerRegistry.getInstance().getLoginHandlers(); it.hasNext();) {
                final LoginHandlerService handler = it.next();
                handleSafely(login, handler, true);
            }
        } else {
            ThreadPoolCompletionService<Void> completionService = null;
            int blocking = 0;
            final boolean tranzient = login.getSession().isTransient();
            for (final Iterator<LoginHandlerService> it = LoginHandlerRegistry.getInstance().getLoginHandlers(); it.hasNext();) {
                final LoginHandlerService handler = it.next();
                if (tranzient && NonTransient.class.isInstance(handler)) {
                    // skip
                    continue;
                }
                if (handler instanceof Blocking) {
                    // Current LoginHandlerService must not be invoked concurrently
                    if (null == completionService) {
                        completionService = new ThreadPoolCompletionService<Void>(executor);
                    }
                    final Callable<Void> callable = new Callable<Void>() {

                        @Override
                        public Void call() {
                            handleSafely(login, handler, true);
                            return null;
                        }
                    };
                    completionService.submit(callable);
                    blocking++;
                } else {
                    executor.submit(new LoginPerformerTask() {

                        @Override
                        public Object call() {
                            handleSafely(login, handler, true);
                            return null;
                        }
                    }, CallerRunsBehavior.getInstance());
                }
            }
            // Await completion of blocking LoginHandlerServices
            if (blocking > 0 && null != completionService) {
                for (int i = 0; i < blocking; i++) {
                    try {
                        completionService.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private static void triggerLogoutHandlers(final LoginResult logout) {
        final ThreadPoolService executor = ThreadPools.getThreadPool();
        if (null == executor) {
            for (final Iterator<LoginHandlerService> it = LoginHandlerRegistry.getInstance().getLoginHandlers(); it.hasNext();) {
                handleSafely(logout, it.next(), false);
            }
        } else {
            ThreadPoolCompletionService<Void> completionService = null;
            int blocking = 0;
            for (final Iterator<LoginHandlerService> it = LoginHandlerRegistry.getInstance().getLoginHandlers(); it.hasNext();) {
                final LoginHandlerService handler = it.next();
                if (handler instanceof Blocking) {
                    // Current LoginHandlerService must not be invoked concurrently
                    if (null == completionService) {
                        completionService = new ThreadPoolCompletionService<Void>(executor);
                    }
                    final Callable<Void> callable = new Callable<Void>() {

                        @Override
                        public Void call() {
                            handleSafely(logout, handler, false);
                            return null;
                        }
                    };
                    completionService.submit(callable);
                    blocking++;
                } else {
                    executor.submit(new LoginPerformerTask() {

                        @Override
                        public Object call() {
                            handleSafely(logout, handler, false);
                            return null;
                        }
                    }, CallerRunsBehavior.getInstance());
                }
            }
            // Await completion of blocking LoginHandlerServices
            if (blocking > 0 && null != completionService) {
                for (int i = 0; i < blocking; i++) {
                    try {
                        completionService.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Handles given {@code LoginResult} safely.
     *
     * @param login The login result to handle
     * @param handler The handler
     * @param isLogin <code>true</code> to signal specified {@code LoginResult} refers to a login operation; otherwise it refers to a logout operation
     */
    protected static void handleSafely(final LoginResult login, final LoginHandlerService handler, final boolean isLogin) {
        if ((null == login) || (null == handler)) {
            return;
        }
        try {
            if (isLogin) {
                handler.handleLogin(login);
            } else {
                handler.handleLogout(login);
            }
        } catch (OXException e) {
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
        } catch (RuntimeException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static final AtomicReference<LoginFormatter> FORMATTER_REF = new AtomicReference<LoginFormatter>();

    /**
     * Sets the applicable formatter.
     *
     * @param formatter The formatter or <code>null</code> to remove
     */
    public static void setLoginFormatter(final LoginFormatter formatter) {
        FORMATTER_REF.set(formatter);
    }

    private static void logLoginRequest(final LoginRequest request, final LoginResult result) {
        final LoginFormatter formatter = FORMATTER_REF.get();
        final StringBuilder sb = new StringBuilder(1024);
        if (null == formatter) {
            DefaultLoginFormatter.getInstance().formatLogin(request, result, sb);
        } else {
            formatter.formatLogin(request, result, sb);
        }
        LOG.info(sb.toString());

        AuditLogService auditLogService = ServerServiceRegistry.getInstance().getService(AuditLogService.class);
        if (null != auditLogService) {
            Session session = result.getSession();
            String login = null == session ? request.getLogin() : session.getLoginName();
            if (null != login) {
                String client = request.getClient();
                String sessionId = null == session ? null : session.getSessionID();
                auditLogService.log("ox.login", DefaultAttribute.valueFor(Name.LOGIN, login, 256), DefaultAttribute.valueFor(Name.IP_ADDRESS, request.getClientIP()), DefaultAttribute.timestampFor(new Date()),
                    DefaultAttribute.valueFor(Name.CLIENT, null == client ? "<none>" : client), DefaultAttribute.valueFor(Name.SESSION_ID, null == sessionId ? "<none>" : sessionId));
            }
        }
    }

    private static void logLogout(final LoginResult result) {
        final LoginFormatter formatter = FORMATTER_REF.get();
        final StringBuilder sb = new StringBuilder(512);
        if (null == formatter) {
            DefaultLoginFormatter.getInstance().formatLogout(result, sb);
        } else {
            formatter.formatLogout(result, sb);
        }
        LOG.info(sb.toString());

        AuditLogService auditLogService = ServerServiceRegistry.getInstance().getService(AuditLogService.class);
        if (null != auditLogService) {
            Session session = result.getSession();
            auditLogService.log("ox.logout", DefaultAttribute.valueFor(Name.LOGIN, session.getLoginName(), 256), DefaultAttribute.valueFor(Name.IP_ADDRESS, session.getLocalIp()), DefaultAttribute.timestampFor(new Date()));
        }
    }

    public Session lookupSession(final String sessionId) throws OXException {
        return ServerServiceRegistry.getInstance().getService(SessiondService.class, true).getSession(sessionId);
    }

    public Session lookupSessionWithTokens(String clientToken, String serverToken) throws OXException {
        return ServerServiceRegistry.getInstance().getService(SessiondService.class, true).getSessionWithTokens(clientToken, serverToken);
    }
}
