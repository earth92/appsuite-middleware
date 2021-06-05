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

import static com.openexchange.ajax.LoginServlet.SECRET_PREFIX;
import static com.openexchange.ajax.LoginServlet.SESSION_PREFIX;
import static com.openexchange.ajax.LoginServlet.configureCookie;
import static com.openexchange.ajax.LoginServlet.getPublicSessionCookieName;
import static com.openexchange.ajax.LoginServlet.getShareCookieName;
import static com.openexchange.ajax.LoginServlet.logAndSendException;
import static com.openexchange.authentication.LoginExceptionCodes.INVALID_CREDENTIALS;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.fields.LoginFields;
import com.openexchange.authentication.Authenticated;
import com.openexchange.authentication.BasicAuthenticationService;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.authentication.LoginInfo;
import com.openexchange.authentication.ResponseEnhancement;
import com.openexchange.authentication.ResultCode;
import com.openexchange.authentication.SessionEnhancement;
import com.openexchange.authentication.service.Authentication;
import com.openexchange.authorization.Authorization;
import com.openexchange.authorization.AuthorizationService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.log.LogProperties;
import com.openexchange.login.LoginRampUpService;
import com.openexchange.login.LoginResult;
import com.openexchange.login.internal.AbstractJsonEnhancingLoginResult;
import com.openexchange.login.internal.AddSessionParameterImpl;
import com.openexchange.login.internal.LoginPerformer;
import com.openexchange.login.internal.LoginResultImpl;
import com.openexchange.login.listener.LoginListener;
import com.openexchange.login.listener.internal.LoginListenerRegistryImpl;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.share.AuthenticationMode;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.ShareExceptionCodes;
import com.openexchange.share.ShareService;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.ShareTargetPath;
import com.openexchange.share.groupware.ModuleSupport;
import com.openexchange.share.groupware.TargetProxy;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.servlet.http.Cookies;
import com.openexchange.user.User;


/**
 * {@link AbstractShareBasedLoginRequestHandler} - The abstract login request handler for share-based login requests.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.0
 */
public abstract class AbstractShareBasedLoginRequestHandler extends AbstractLoginRequestHandler {

    /**
     * {@link ShareLoginClosure}
     *
     * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
     * @since v7.8.0
     */
    private final class ShareLoginClosure implements LoginClosure {

        private final GuestInfo guest;
        private final ShareTarget target;
        private final LoginConfiguration loginConfiguration;
        private final HttpServletRequest httpRequest;
        private final ShareTargetPath targetPath;

        /**
         * Initializes a new {@link ShareLoginClosure}.
         *
         * @param guest
         * @param target
         * @param targetPath The share target path
         * @param conf
         * @param httpRequest
         * @param enhancement The session enhancement, or <code>null</code> if not applicable
         */
        ShareLoginClosure(GuestInfo guest, ShareTarget target, ShareTargetPath targetPath, LoginConfiguration conf, HttpServletRequest httpRequest) {
            this.guest = guest;
            this.target = target;
            this.loginConfiguration = conf;
            this.httpRequest = httpRequest;
            this.targetPath = targetPath;
        }

        @Override
        public LoginResult doLogin(final HttpServletRequest req) throws OXException {
            List<LoginListener> listeners = LoginListenerRegistryImpl.getInstance().getLoginListeners();
            HashMap<String, Object> properties = new HashMap<String, Object>(1);
            LoginRequestImpl request = null;
            try {
                // Check for matching authentication mode
                if (false == checkAuthenticationMode(guest.getAuthentication())) {
                    throw INVALID_CREDENTIALS.create();
                }

                BasicAuthenticationService basicService = Authentication.getBasicService();
                if (null == basicService) {
                    throw ServiceExceptionCode.absentService(BasicAuthenticationService.class);
                }

                // Get the login info from HTTP request
                LoginInfo loginInfo = getLoginInfoFrom(httpRequest, guest);

                // Resolve context
                Context context;
                {
                    ContextService contextService = ServerServiceRegistry.getInstance().getService(ContextService.class);
                    if (null == contextService) {
                        throw ServiceExceptionCode.absentService(ContextService.class);
                    }

                    context = contextService.getContext(guest.getContextID());
                }

                // Resolve user
                User user = resolveUser(guest, context);

                // Parse & check the HTTP request
                String[] additionalsForHash = new String[] { String.valueOf(context.getContextId()), String.valueOf(user.getId()) };
                String client = LoginTools.parseClient(httpRequest, false, loginConfiguration.getDefaultClient());
                request = LoginTools.parseLogin(httpRequest, loginInfo.getUsername(), loginInfo.getPassword(), false, client, loginConfiguration.isCookieForceHTTPS(), false, additionalsForHash);
                LoginPerformer.sanityChecks(request);
                LoginPerformer.checkClient(request, user, context);

                // Call onBeforeAuthentication
                for (LoginListener listener : listeners) {
                    listener.onBeforeAuthentication(request, properties);
                }

                // Authenticate user
                authenticateUser(loginInfo, user, context);

                // Pass to basic authentication service in case more handling needed
                Authenticated  authenticated = basicService.handleLoginInfo(guest.getGuestID(), guest.getContextID());
                if (null == authenticated) {
                    return null;
                }

                // Checks if something is deactivated.
                AuthorizationService authService = Authorization.getService();
                if (null == authService) {
                    throw ServiceExceptionCode.absentService(AuthorizationService.class);
                }
                authService.authorizeUser(context, user);

                // Store locale if requested by client during login request
                user = LoginPerformer.storeLanguageIfNeeded(request, user, context);

                // Create session
                Session session;
                {
                    SessiondService sessiondService = SessiondService.SERVICE_REFERENCE.get();
                    if (null == sessiondService) {
                        sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class);
                        if (null == sessiondService) {
                            // Giving up...
                            throw ServiceExceptionCode.absentService(SessiondService.class);
                        }
                    }
                    {
                        ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                        boolean tranzient = null == service || service.getBoolProperty("com.openexchange.share.transientSessions", true);
                        request.setTransient(tranzient);
                    }
                    AddSessionParameterImpl sessionToAdd = new AddSessionParameterImpl(loginInfo.getUsername(), request, user, context);
                    final Map<String, String> additionals = null != targetPath ? targetPath.getAdditionals() : null;
                    final SessionEnhancement sessionEnhancement = SessionEnhancement.class.isInstance(authenticated) ? (SessionEnhancement) authenticated : null;
                    {
                        SessionEnhancement effectiveEnhancement = new SessionEnhancement() {

                            @Override
                            public void enhanceSession(Session session) {
                                // Add optional additionals (if any)
                                if (null != additionals) {
                                    for (Map.Entry<String, String> entry : additionals.entrySet()) {
                                        session.setParameter("com.openexchange.share." + entry.getKey(), entry.getValue());
                                    }
                                }

                                // Set guest marker
                                session.setParameter(Session.PARAM_GUEST, Boolean.TRUE);

                                // Apply SessionEnhancement (if not null)
                                if (null != sessionEnhancement) {
                                    sessionEnhancement.enhanceSession(session);
                                }
                            }
                        };
                        sessionToAdd.addEnhancement(effectiveEnhancement);
                    }
                    session = sessiondService.addSession(sessionToAdd);
                    if (null == session) {
                        // Session could not be created
                        throw LoginExceptionCodes.UNKNOWN.create("Session could not be created.");
                    }
                    LogProperties.putSessionProperties(session);
                    request.markHttpSessionAuthenticated();
                }

                // Generate the login result
                final ShareTarget target = this.target;
                LoginResultImpl retval = new AbstractJsonEnhancingLoginResult() {
                    @Override
                    protected void doEnhanceJson(JSONObject jLoginResult) throws OXException, JSONException {
                        if (target.getModule() > 0) {
                            String folderModule = ServerServiceRegistry.getInstance().getService(ModuleSupport.class).getShareModule(target.getModule());
                            if ("infostore".equals(folderModule)) {
                                folderModule = "files";
                            }
                            jLoginResult.put("module", folderModule);
                        }
                        jLoginResult.putOpt("folder", target.getFolder());
                        jLoginResult.putOpt("item", target.getItem());
                    }
                };
                retval.setContext(context);
                retval.setUser(user);
                retval.setRequest(request);
                retval.setServerToken((String) session.getParameter(LoginFields.SERVER_TOKEN));
                retval.setSession(session);
                if (authenticated instanceof ResponseEnhancement) {
                    final ResponseEnhancement responseEnhancement = (ResponseEnhancement) authenticated;
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

                // Trigger registered login handlers
                LoginPerformer.triggerLoginHandlers(retval);

                // Call onSucceededAuthentication
                for (LoginListener listener : listeners) {
                    listener.onSucceededAuthentication(retval);
                }

                return retval;
            } catch (OXException e) {
                if (null != request) {
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
                }
                throw e;
            } catch (RuntimeException e) {
                OXException oxe = AjaxExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
                if (null != request) {
                    // Call onFailedAuthentication
                    for (LoginListener listener : listeners) {
                        listener.onFailedAuthentication(request, properties, oxe);
                    }
                }
                throw oxe;
            }
        }
    }

    /** The login configuration */
    protected final ShareLoginConfiguration conf;

    /**
     * Initializes a new {@link AbstractShareBasedLoginRequestHandler}.
     *
     * @param conf The login configuration
     * @param rampUpServices The ramp-up services
     */
    protected AbstractShareBasedLoginRequestHandler(ShareLoginConfiguration conf, Set<LoginRampUpService> rampUpServices) {
        super(rampUpServices);
        this.conf = conf;
    }

    @Override
    public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {
        // Look-up necessary credentials
        try {
            doLogin(req, resp, requestContext);
        } catch (OXException e) {
            logAndSendException(resp, e);
            requestContext.getMetricProvider().recordException(e);
        }
    }

    /**
     * Performs the login for this share-based login request.
     *
     * @param httpRequest The HTTP request
     * @param httpResponse The HTTP response
     * @param requestContext The request context
     * @throws IOException If an I/O error occors
     * @throws OXException If an Open-Xchange Server error occurs
     */
    protected void doLogin(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse, LoginRequestContext requestContext) throws IOException, OXException {
        // Get the share's token & target
        final String token = httpRequest.getParameter("share");
        if (null == token) {
            throw AjaxExceptionCodes.MISSING_PARAMETER.create("share");
        }

        // Get the ShareService to obtain associated share
        ShareService shareService = ServerServiceRegistry.getInstance().getService(ShareService.class);
        if (null == shareService) {
            throw ServiceExceptionCode.absentService(ShareService.class);
        }

        // Get the guest
        final GuestInfo guest = shareService.resolveGuest(token);
        if (null == guest) {
            throw ShareExceptionCodes.UNKNOWN_SHARE.create(token);
        }

        String targetPathParam = httpRequest.getParameter("target");
        if (targetPathParam == null) {
            throw ShareExceptionCodes.UNKNOWN_SHARE.create(token);
        }

        ShareTargetPath targetPath = ShareTargetPath.parse(targetPathParam);
        if (targetPath == null) {
            throw ShareExceptionCodes.UNKNOWN_SHARE.create(token);
        }

        ModuleSupport moduleSupport = ServerServiceRegistry.getInstance().getService(ModuleSupport.class);
        int contextId = guest.getContextID();
        int guestId = guest.getGuestID();
        int m = targetPath.getModule();
        String f = targetPath.getFolder();
        String i = targetPath.getItem();
        ShareTarget target = null;
        if (moduleSupport.exists(m, f, i, contextId, guestId) && moduleSupport.isVisible(m, f, i, contextId, guestId)) {
            TargetProxy targetProxy = moduleSupport.resolveTarget(targetPath, contextId, guestId);
            target = targetProxy.getTarget();
        } else {
            List<TargetProxy> otherTargets = moduleSupport.listTargets(contextId, guestId);
            if (otherTargets.isEmpty()) {
                throw ShareExceptionCodes.UNKNOWN_SHARE.create(token);
            }
            target = otherTargets.get(0).getTarget();
        }

        final LoginConfiguration conf = this.conf.getLoginConfig(guest);
        LoginClosure loginClosure = new ShareLoginClosure(guest, target, targetPath, conf, httpRequest);
        LoginCookiesSetter cookiesSetter = new LoginCookiesSetter() {
            @Override
            public void setLoginCookies(Session session, HttpServletRequest request, HttpServletResponse response, LoginConfiguration loginConfig) throws OXException {
                String expectedSecretCookieName = SECRET_PREFIX + session.getHash();
                String expectedSessionCookieName = SESSION_PREFIX + session.getHash();
                /*
                 * set cookies
                 */
                boolean staySignedIn = session.isStaySignedIn();
                response.addCookie(configureCookie(new Cookie(expectedSecretCookieName, session.getSecret()), request, loginConfig, staySignedIn));
                response.addCookie(configureCookie(new Cookie(getShareCookieName(request), guest.getBaseToken()), request, loginConfig, staySignedIn));
                response.addCookie(configureCookie(new Cookie(expectedSessionCookieName, session.getSessionID()), request, loginConfig, staySignedIn));
                /*
                 * set public session cookie if not yet present
                 */
                String[] additionals = new String[] { String.valueOf(session.getContextId()), String.valueOf(session.getUserId()) };
                Map<String, Cookie> cookies = Cookies.cookieMapFor(request);
                Cookie publicSessionCookie = cookies.get(getPublicSessionCookieName(request, additionals));
                if (null == publicSessionCookie) {
                    /*
                     * missing public session cookie
                     */
                    String altId = (String) session.getParameter(Session.PARAM_ALTERNATIVE_ID);
                    if (null != altId) {
                        publicSessionCookie = new Cookie(getPublicSessionCookieName(request, additionals), altId);
                        response.addCookie(configureCookie(publicSessionCookie, request, loginConfig, staySignedIn));
                    }
                }
            }
        };

        // Do the login operation
        loginOperation(httpRequest, httpResponse, loginClosure, cookiesSetter, conf, requestContext);
    }

    /**
     * Checks the share's authentication mode against performed login
     *
     * @param authenticationMode The authentication mode to check
     * @return <code>true</code> if authentication mode matches; otherwise <code>false</code>
     * @throws OXException If check fails for any reason
     */
    protected abstract boolean checkAuthenticationMode(AuthenticationMode authenticationMode) throws OXException;

    /**
     * Gets the appropriate share's login information from given HTTP request
     *
     * @param httpRequest The HTTP request
     * @param guest The guest information
     * @return The login information
     * @throws OXException If login information cannot be returned
     */
    protected abstract LoginInfo getLoginInfoFrom(HttpServletRequest httpRequest, GuestInfo guest) throws OXException;

    /**
     * Authenticates the user associated with specified share using given login information.
     *
     * @param guest The guest
     * @param context The context associated with the share
     * @return The resolved user
     * @throws OXException If resolving user fails
     */
    protected abstract User resolveUser(GuestInfo guest, Context context) throws OXException;

    /**
     * Authenticates the user associated with specified share using given arguments.
     *
     * @param loginInfo The login information
     * @param user The previously resolved user
     * @param context The context associated with the share
     * @return The authenticated user
     * @throws OXException If authentication fails
     */
    protected abstract void authenticateUser(LoginInfo loginInfo, User user, Context context) throws OXException;

}
