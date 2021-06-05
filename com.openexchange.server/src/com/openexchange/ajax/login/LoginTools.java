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

import static com.openexchange.ajax.AJAXServlet.CONTENTTYPE_HTML;
import static com.openexchange.ajax.AJAXServlet.PARAMETER_SESSION;
import static com.openexchange.ajax.fields.LoginFields.APPSECRET;
import static com.openexchange.ajax.fields.LoginFields.AUTHID_PARAM;
import static com.openexchange.ajax.fields.LoginFields.CLIENT_IP_PARAM;
import static com.openexchange.ajax.fields.LoginFields.CLIENT_PARAM;
import static com.openexchange.ajax.fields.LoginFields.PASSWORD_PARAM;
import static com.openexchange.ajax.fields.LoginFields.REDIRECT_URL;
import static com.openexchange.ajax.fields.LoginFields.SHARE_TOKEN;
import static com.openexchange.ajax.fields.LoginFields.TOKEN;
import static com.openexchange.ajax.fields.LoginFields.VERSION_PARAM;
import static com.openexchange.login.Interface.HTTP_JSON;
import static com.openexchange.tools.servlet.http.Tools.copyHeaders;
import static com.openexchange.tools.servlet.http.Tools.filter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import com.openexchange.ajax.AJAXUtility;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.fields.Header;
import com.openexchange.ajax.fields.LoginFields;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.java.util.UUIDs;
import com.openexchange.log.LogProperties;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.DefaultSessionAttributes;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.ShareService;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.servlet.http.Tools;

/**
 * Shared methods for login operations.
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public final class LoginTools {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LoginTools.class);

    private LoginTools() {
        super();
    }

    /**
     * URL encodes given string.
     * <p>
     * Using <code>org.apache.commons.codec.net.URLCodec</code>.
     */
    public static String encodeUrl(final String s, final boolean forAnchor) {
        return AJAXUtility.encodeUrl(s, forAnchor);
    }

    private static final Pattern PATTERN_CRLF = Pattern.compile("\r?\n|(?:%0[aA])?%0[dD]");
    private static final Pattern PATTERN_DSLASH = Pattern.compile("(?:/|%2[fF]){2}");

    public static String generateRedirectURL(String uiWebPathParam, String sessionId, String uiWebPath) {
        String retval = uiWebPathParam;
        if (null == retval) {
            retval = uiWebPath;
        }
        // Prevent HTTP response splitting.
        retval = PATTERN_CRLF.matcher(retval).replaceAll("");
        // All double slash strings ("//") should be replaced by a single slash ("/")
        // since it is interpreted by the Browser as "http://".
        retval = PATTERN_DSLASH.matcher(retval).replaceAll("/");
        retval = addFragmentParameter(retval, PARAMETER_SESSION, sessionId);
        return retval;
    }

    public static String addFragmentParameter(String usedUIWebPath, String param, String value) {
        String retval = usedUIWebPath;
        final int fragIndex = retval.indexOf('#');
        // First get rid off the query String, so we can re-append it later
        final int questionMarkIndex = retval.indexOf('?', fragIndex);
        String query = "";
        if (questionMarkIndex > 0) {
            query = retval.substring(questionMarkIndex);
            retval = retval.substring(0, questionMarkIndex);
        }
        // Now let's see, if this URL already contains a fragment
        if (retval.indexOf('#') < 0) {
            // Apparently it didn't, so we can append our own
            return retval + '#' + AJAXUtility.encodeUrl(param) + '=' + AJAXUtility.encodeUrl(value) + query;
        }
        // Ok, we already have a fragment, let's append a new parameter
        return retval + '&' + AJAXUtility.encodeUrl(param) + '=' + AJAXUtility.encodeUrl(value) + query;
    }

    public static String parseAuthId(HttpServletRequest req, boolean strict) throws OXException {
        return parseParameter(req, AUTHID_PARAM, strict, UUIDs.getUnformattedString(UUID.randomUUID()));
    }

    public static String parseClient(HttpServletRequest req, boolean strict, String defaultClient) throws OXException {
        return parseParameter(req, CLIENT_PARAM, strict, defaultClient);
    }

    public static String parseToken(HttpServletRequest req) throws OXException {
        return parseParameter(req, TOKEN);
    }

    public static String parseAppSecret(HttpServletRequest req) throws OXException {
        return parseParameter(req, APPSECRET);
    }

    public static String parseRedirectUrl(HttpServletRequest req) {
        return parseParameter(req, REDIRECT_URL, "");
    }

    public static String parseAutoLogin(HttpServletRequest req, String defaultAutoLogin) {
        return parseParameter(req, LoginFields.AUTOLOGIN_PARAM, defaultAutoLogin);
    }

    public static String parseLanguage(HttpServletRequest req) {
        return parseParameter(req, LoginFields.LANGUAGE_PARAM, parseLocale(req));
    }

    public static boolean parseStoreLanguage(HttpServletRequest req) {
        String value = req.getParameter(LoginFields.STORE_LANGUAGE);
        return Strings.isEmpty(value) ? parseStoreLocale(req) : AJAXRequestDataTools.parseBoolParameter(value);
    }

    public static String parseLocale(HttpServletRequest req) {
        return parseParameter(req, LoginFields.LOCALE_PARAM, "");
    }

    public static boolean parseStoreLocale(HttpServletRequest req) {
        String value = req.getParameter(LoginFields.STORE_LOCALE);
        return AJAXRequestDataTools.parseBoolParameter(value);
    }

    public static boolean parseTransient(HttpServletRequest req) {
        String value = req.getParameter(LoginFields.TRANSIENT);
        return AJAXRequestDataTools.parseBoolParameter(value);
    }

    public static String parseParameter(HttpServletRequest req, String paramName, boolean strict, String fallback) throws OXException {
        final String value = req.getParameter(paramName);
        if (null == value) {
            if (strict) {
                throw AjaxExceptionCodes.MISSING_PARAMETER.create(paramName);
            }
            return fallback;
        }
        return value;
    }

    public static String parseParameter(HttpServletRequest req, String paramName, String fallback) {
        String value = req.getParameter(paramName);
        return null == value ? fallback : value;
    }

    public static String parseParameter(HttpServletRequest req, String paramName) throws OXException {
        final String value = req.getParameter(paramName);
        if (null == value) {
            throw AjaxExceptionCodes.MISSING_PARAMETER.create(paramName);
        }
        return value;
    }

    public static String parseClientIP(HttpServletRequest req) {
        return parseParameter(req, CLIENT_IP_PARAM, req.getRemoteAddr());
    }

    public static String parseUserAgent(HttpServletRequest req) {
        return parseParameter(req, LoginFields.USER_AGENT, req.getHeader(Header.USER_AGENT));
    }

    /**
     * Parses a login request based on the supplied servlet request and credentials.
     *
     * @param req The underlying servlet request
     * @param login The provided login name
     * @param password The provided password
     * @param strict <code>true</code> to fail on missing version- or client-parameter in the request, <code>false</code>, otherwise
     * @param defaultClient The client identifier to use as fallback if the request does provide contain the "client" parameter
     * @param forceHTTPS
     * @param requiredAuthId <code>true</code> to fail on missing authId-parameter in the request, <code>false</code>, otherwise
     * @return The parsed login request
     * @throws OXException
     */
    public static LoginRequestImpl parseLogin(HttpServletRequest req, String login, String password, boolean strict, String defaultClient, boolean forceHTTPS, boolean requiredAuthId) throws OXException {
        return parseLogin(req, login, password, strict, defaultClient, forceHTTPS, requiredAuthId, (String[]) null);
    }

    /**
     * Parses a login request based on the underlying servlet request and provided user credentials.
     *
     * @param req The underlying servlet request
     * @param login The provided login name
     * @param password The provided password
     * @param strict <code>true</code> to fail on missing version- or client-parameter in the request, <code>false</code>, otherwise
     * @param defaultClient The client identifier to use as fallback if the request does provide contain the "client" parameter
     * @param forceHTTPS
     * @param requiredAuthId <code>true</code> to fail on missing authId-parameter in the request, <code>false</code>, otherwise
     * @param additionalsForHash Additional values to include when calculating the client-specific hash for the cookie names, or
     *            <code>null</code> if not needed
     * @return The parsed login request
     * @throws OXException
     */
    public static LoginRequestImpl parseLogin(HttpServletRequest req, String login, String password, boolean strict, String defaultClient, boolean forceHTTPS, boolean requiredAuthId, String... additionalsForHash) throws OXException {
        final String authId = parseAuthId(req, requiredAuthId);
        final String client = parseClient(req, strict, defaultClient);
        final String version;
        if (null == req.getParameter(VERSION_PARAM)) {
            if (strict) {
                throw AjaxExceptionCodes.MISSING_PARAMETER.create(VERSION_PARAM);
            }
            version = null;
        } else {
            version = req.getParameter(VERSION_PARAM);
        }
        final String clientIP = parseClientIP(req);
        final String userAgent = parseUserAgent(req);
        final Map<String, List<String>> headers = copyHeaders(req);
        final com.openexchange.authentication.Cookie[] cookies = Tools.getCookieFromHeader(req);
        final HttpSession httpSession = req.getSession(true);
        // Add properties
        {
            LogProperties.putProperty(LogProperties.Name.LOGIN_LOGIN, Strings.abbreviate(login, 256));
            LogProperties.putProperty(LogProperties.Name.LOGIN_CLIENT_IP, clientIP);
            LogProperties.putProperty(LogProperties.Name.LOGIN_USER_AGENT, userAgent);
            LogProperties.putProperty(LogProperties.Name.LOGIN_AUTH_ID, authId);
            LogProperties.putProperty(LogProperties.Name.LOGIN_CLIENT, client);
            LogProperties.putProperty(LogProperties.Name.LOGIN_VERSION, version);
        }
        // Return
        LoginRequestImpl.Builder b = new LoginRequestImpl.Builder().login(login).password(password).clientIP(clientIP);
        b.userAgent(userAgent).authId(authId).client(client).version(version);
        b.hash(HashCalculator.getInstance().getHash(req, userAgent, client, additionalsForHash));
        b.iface(HTTP_JSON).headers(headers).requestParameter(req.getParameterMap());
        b.cookies(cookies).secure(Tools.considerSecure(req, forceHTTPS));
        b.serverName(req.getServerName()).serverPort(req.getServerPort()).httpSession(httpSession);
        b.language(parseLanguage(req)).storeLanguage(parseStoreLanguage(req)).tranzient(parseTransient(req));
        b.staySignedIn(parseStaySignedIn(req));
        return b.build();
    }

    public static LoginRequestImpl parseLogin(HttpServletRequest req, String loginParamName, boolean strict, String defaultClient, boolean forceHTTPS, boolean disableTrimLogin, boolean requiredAuthId) throws OXException {
        String login = req.getParameter(loginParamName);
        if (null == login) {
            throw AjaxExceptionCodes.MISSING_PARAMETER.create(loginParamName);
        }
        if (!disableTrimLogin) {
            login = login.trim();
        }
        String password = req.getParameter(PASSWORD_PARAM);
        if (null == password) {
            throw AjaxExceptionCodes.MISSING_PARAMETER.create(PASSWORD_PARAM);
        }
        return parseLogin(req, login, password, strict, defaultClient, forceHTTPS, requiredAuthId);
    }

    /**
     * Updates session's IP address if different to specified IP address. This is only possible if the server is configured to be IP wise
     * insecure. See configuration property <code>"com.openexchange.ajax.login.insecure"</code>.
     *
     * @param conf The login configuration to determine if insecure IP change is enabled
     * @param newIP The possibly new IP address
     * @param session The session to update if IP addresses differ
     */
    public static void updateIPAddress(LoginConfiguration conf, String newIP, Session session) {
        if (conf.isInsecure()) {
            String oldIP = session.getLocalIp();
            if (null != newIP && !newIP.equals(oldIP)) {
                LOG.info("Updating session's IP address. authID: {}, sessionID: {}, old IP address: {}, new IP address: {}", session.getAuthId(), session.getSessionID(), oldIP, newIP);
                SessiondService service = ServerServiceRegistry.getInstance().getService(SessiondService.class);
                if (null != service) {
                    try {
                        service.setSessionAttributes(session.getSessionID(), DefaultSessionAttributes.builder().withLocalIp(newIP).build());
                    } catch (OXException e) {
                        LOG.info("Failed to update session's IP address. authID: {}, sessionID: {}, old IP address: {}, new IP address: {}", session.getAuthId(), session.getSessionID(), oldIP, newIP, e);
                    }
                }
            }
        }
    }

    /**
     * Parses possibly available share information (more precisely the value for <code>"share"</code> query parameter) from given HTTP request.
     *
     * @param req The HTTP request to parse from
     * @return The parsed share information or <code>null</code>
     * @throws OXException If parsing share information fails; e.g. guest information cannot be obtained from available share token
     */
    public static String[] parseShareInformation(HttpServletRequest req) throws OXException {
        String token = req.getParameter(SHARE_TOKEN);
        if (Strings.isEmpty(token)) {
            return null;
        }

        ShareService shareService = ServerServiceRegistry.getInstance().getService(ShareService.class);
        if (null == shareService) {
            return null;
        }

        GuestInfo guest = shareService.resolveGuest(token);
        if (null == guest) {
            LOG.warn("No guest could be determined for share token: {}", token);
            return new String[0];
        }

        int contextId = guest.getContextID();
        int guestId = guest.getGuestID();
        return new String[] { Integer.toString(contextId), Integer.toString(guestId) };
    }

    /**
     * Either uses given error page template or simply logs & sends given (login) error to the client.
     * <p>
     * In case error page template appears to redirect to referrer's URL, the referrer's URL is checked for validity against target host.
     *
     * @param e The (login) error
     * @param errorPageTemplate The error page template
     * @param req The HTTP request
     * @param resp The HTTP response
     * @throws IOException If an I/O error occurs
     */
    public static void useErrorPageTemplateOrSendException(OXException e, String errorPageTemplate, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (Strings.isEmpty(errorPageTemplate)) {
            // No valid error page template...
            LoginServlet.logAndSendException(resp, e);
            return;
        }

        // Check referrer's URL validity in case template appears to redirect to referrer's URL
        if (errorPageTemplate.indexOf("document.referrer") < 0) {
            // Template appears not to redirect to referrer's URL.
            String errorPage = errorPageTemplate.replace("ERROR_MESSAGE", filter(e.getMessage()));
            resp.setContentType(CONTENTTYPE_HTML);
            resp.getWriter().write(errorPage);
        }

        // Client is supposed to be redirected to referrer's URL
        String sReferrer = req.getHeader("Referer");
        if (Strings.isEmpty(sReferrer)) {
            LoginServlet.logAndSendException(resp, e);
            return;
        }

        try {
            URI referrerUri = new URI(sReferrer);
            String targetHost = req.getServerName();
            if (false == Tools.isSubdomainOfTheOther(targetHost, referrerUri) && false == containedInAllowedRedirectPaths(referrerUri, true)) {
                // Neither in the same domain nor contained in white-list. Deny redirecting to referrer's URL
                LoginServlet.logAndSendException(resp, e);
                return;
            }
        } catch (URISyntaxException x) {
            // Referrer is invalid
            LoginServlet.logAndSendException(resp, e);
            return;
        }

        // Either in the same domain or contained in white-list. Allow redirecting to referrer's URL
        String errorPage = errorPageTemplate.replace("ERROR_MESSAGE", filter(e.getMessage()));
        resp.setContentType(CONTENTTYPE_HTML);
        resp.getWriter().write(errorPage);
    }

    private static boolean containedInAllowedRedirectPaths(URI referrerUri, boolean defaultValue) {
        AllowedRedirectUris allowedRedirectUris = AllowedRedirectUris.getInstance();
        return allowedRedirectUris.isEmpty() ? defaultValue : allowedRedirectUris.isAllowed(referrerUri);
    }

    /**
     * Parses staySignedIn parameter from login request
     *
     * @param req The request
     * @return <code>true</code> if 'staySignedIn' parameter was set in login request,
     *         <code>false</code> if not
     */
    public static boolean parseStaySignedIn(HttpServletRequest req) {
        String staySignedIn = req.getParameter(LoginFields.STAY_SIGNED_IN);
        if (Strings.isNotEmpty(staySignedIn)) {
            return AJAXRequestDataTools.parseBoolParameter(staySignedIn);
        }
        String autologin = req.getParameter(LoginFields.AUTOLOGIN_PARAM);
        if (Strings.isNotEmpty(autologin)) {
            return AJAXRequestDataTools.parseBoolParameter(autologin);
        }
        return false;
    }

}
