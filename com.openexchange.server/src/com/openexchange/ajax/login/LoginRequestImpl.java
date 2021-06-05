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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;
import com.openexchange.authentication.Cookie;
import com.openexchange.login.Interface;
import com.openexchange.login.LoginRequest;
import com.openexchange.servlet.Constants;

/**
 * {@link LoginRequestImpl}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public class LoginRequestImpl implements LoginRequest {

    /**
     * The builder for a {@link LoginRequestImpl}.
     */
    public static final class Builder {

        protected String login, password, clientIP, userAgent, authId, client, version, hash;
        protected String clientToken;
        protected Interface iface;
        protected Map<String, List<String>> headers;
        protected Map<String, String[]> requestParameters;
        protected Cookie[] cookies;
        protected boolean secure;
        protected String serverName;
        protected int serverPort;
        protected String httpSessionID;
        protected boolean tranzient;
        protected String language;
        protected boolean storeLanguage;
        protected String locale;
        protected boolean storeLocale;
        protected boolean staySignedIn;
        protected HttpSession httpSession;

        public Builder() {
            super();
        }
        public Builder login(String login) {
            this.login = login; return this;
        }
        public Builder password(String password) {
            this.password = password; return this;
        }
        public Builder clientIP(String clientIP) {
            this.clientIP = clientIP; return this;
        }
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent; return this;
        }
        public Builder authId(String authId) {
            this.authId = authId; return this;
        }
        public Builder client(String client) {
            this.client = client; return this;
        }
        public Builder version(String version) {
            this.version = version; return this;
        }
        public Builder hash(String hash) {
            this.hash = hash; return this;
        }
        public Builder clientToken(String clientToken) {
            this.clientToken = clientToken; return this;
        }
        public Builder serverName(String serverName) {
            this.serverName = serverName; return this;
        }
        public Builder serverPort(int serverPort) {
            this.serverPort = serverPort; return this;
        }
        public Builder httpSession(HttpSession httpSession) {
            this.httpSessionID = httpSession.getId();
            this.httpSession = httpSession;
            return this;
        }
        public Builder iface(Interface iface) {
            this.iface = iface; return this;
        }
        public Builder headers(Map<String, List<String>> headers) {
            this.headers = headers; return this;
        }
        public Builder requestParameter(Map<String, String[]> reqeustParameter) {
            this.requestParameters = reqeustParameter; return this;
        }
        public Builder cookies(Cookie[] cookies) {
            this.cookies = cookies; return this;
        }
        public Builder secure(boolean secure) {
            this.secure = secure; return this;
        }
        public Builder tranzient(boolean tranzient) {
            this.tranzient = tranzient; return this;
        }
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        public Builder storeLanguage(boolean storeLanguage) {
            this.storeLanguage = storeLanguage;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder storeLocale(boolean storeLocale) {
            this.storeLocale = storeLocale;
            return this;
        }

        public Builder staySignedIn(boolean staySignedIn) {
            this.staySignedIn = staySignedIn;
            return this;
        }

        public LoginRequestImpl build() {
            return new LoginRequestImpl(this);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    private final String login, password, clientIP, userAgent, authId, client, version, hash;
    private String clientToken;
    private final Interface iface;
    private final Map<String, List<String>> headers;
    private final Map<String, String[]> requestParameters;
    private final Cookie[] cookies;
    private final boolean secure;
    private final String serverName;
    private final int serverPort;
    private final String httpSessionID;
    private final HttpSession httpSession;
    private boolean tranzient;
    private final String language;
    private final boolean storeLanguage;
    private final String locale;
    private final boolean storeLocale;
    private final boolean staySignedIn;

    /**
     * Initializes a new {@link LoginRequestImpl}.
     *
     * @param builder The builder instance
     */
    protected LoginRequestImpl(Builder builder) {
        super();
        this.login = builder.login;
        this.password = builder.password;
        this.clientIP = builder.clientIP;
        this.userAgent = builder.userAgent;
        this.authId = builder.authId;
        this.client = builder.client;
        this.version = builder.version;
        this.hash = builder.hash;
        this.iface = builder.iface;
        this.headers = builder.headers;
        this.requestParameters = null == builder.requestParameters ? Collections.<String, String[]> emptyMap() : Collections.unmodifiableMap(builder.requestParameters);
        this.cookies = builder.cookies;
        this.secure = builder.secure;
        this.serverName = builder.serverName;
        this.serverPort = builder.serverPort;
        this.httpSessionID = builder.httpSessionID;
        this.httpSession = builder.httpSession;
        this.tranzient = builder.tranzient;
        this.language = builder.language;
        this.storeLanguage = builder.storeLanguage;
        this.locale = builder.locale;
        this.storeLocale = builder.storeLocale;
        this.clientToken = builder.clientToken;
        this.staySignedIn = builder.staySignedIn;
    }

    /**
     * Initializes a new {@link LoginRequestImpl}.
     *
     * @param login The login
     * @param password The password
     * @param clientIP The client IP address
     * @param userAgent The associated User-Agent
     * @param authId The authentication identifier
     * @param client The client identifier
     * @param version The version string
     * @param hash The hash string
     * @param iface The associated interface
     * @param headers The headers
     * @param requestParameters The parameters provided by the {@link javax.servlet.http.HttpServletRequest#getParameterMap()}
     * @param cookies The cookies
     * @param secure Whether associated request is considered to use a secure connection
     * @param serverName The server name
     * @param serverPort The server port
     * @param httpSessionID The identifier of the associated HTTP session
     */
    public LoginRequestImpl(String login, String password, String clientIP, String userAgent, String authId, String client, String version, String hash, Interface iface, Map<String, List<String>> headers, Map<String, String[]> requestParameters, Cookie[] cookies, boolean secure, String serverName, int serverPort, HttpSession httpSession, String language, boolean storeLanguage, String locale, boolean storeLocale, boolean staySignedIn) {
        super();
        this.login = login;
        this.password = password;
        this.clientIP = clientIP;
        this.userAgent = userAgent;
        this.authId = authId;
        this.client = client;
        this.version = version;
        this.hash = hash;
        this.iface = iface;
        this.headers = headers;
        this.requestParameters = null == requestParameters ? Collections.<String, String[]> emptyMap() : Collections.unmodifiableMap(requestParameters);
        this.cookies = cookies;
        this.secure = secure;
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.httpSession = httpSession;
        this.httpSessionID = httpSession != null ? httpSession.getId() : null;
        this.language = language;
        this.storeLanguage = storeLanguage;
        this.locale = locale;
        this.storeLocale = storeLocale;
        this.staySignedIn = staySignedIn;
    }

    public LoginRequestImpl(String login, String password, String clientIP, String userAgent, String authId, String client, String version, String hash, Interface iface, Map<String, List<String>> headers, Map<String, String[]> requestParameters, Cookie[] cookies, boolean secure, String serverName, int serverPort, HttpSession httpSession, String language, String locale) {
        this(login, password, clientIP, userAgent, authId, client, version, hash, iface, headers, requestParameters, cookies, secure, serverName, serverPort, httpSession, language, false, locale, false, false);
    }

    public LoginRequestImpl(String login, String password, String clientIP, String userAgent, String authId, String client, String version, String hash, Interface iface, Map<String, List<String>> headers, Map<String, String[]> requestParameters, Cookie[] cookies, boolean secure, String serverName, int serverPort, HttpSession httpSession, String language) {
        this(login, password, clientIP, userAgent, authId, client, version, hash, iface, headers, requestParameters, cookies, secure, serverName, serverPort, httpSession, language, false, null, false, false);
    }

    public LoginRequestImpl(String login, String password, String clientIP, String userAgent, String authId, String client, String version, String hash, Interface iface, Map<String, List<String>> headers, Map<String, String[]> requestParameters, Cookie[] cookies, boolean secure, String serverName, int serverPort, HttpSession httpSession) {
        this(login, password, clientIP, userAgent, authId, client, version, hash, iface, headers, requestParameters, cookies, secure, serverName, serverPort, httpSession, null, null);
    }

    @Override
    public String getLogin() {
        return login;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getClientIP() {
        return clientIP;
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public String getAuthId() {
        return authId;
    }

    @Override
    public String getClient() {
        return client;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public String getClientToken() {
        return clientToken;
    }

    /**
     * Sets the client token.
     *
     * @param clientToken The client token
     */
    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    @Override
    public Interface getInterface() {
        return iface;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    @Override
    public Map<String, String[]> getRequestParameter() {
        return requestParameters;
    }

    @Override
    public Cookie[] getCookies() {
        return cookies;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    @Override
    public String getHttpSessionID() {
        return httpSessionID;
    }

    @Override
    public boolean markHttpSessionAuthenticated() {
        if (httpSession == null) {
            return false;
        }

        httpSession.setAttribute(Constants.HTTP_SESSION_ATTR_AUTHENTICATED, Boolean.TRUE);
        return true;
    }

    @Override
    public boolean isTransient() {
        return tranzient;
    }

    /**
     * Sets if whether the session should be created in a transient way or not, i.e. the session should not be distributed to other nodes
     * in the cluster or put into another persistent storage.
     *
     * @param tranzient <code>true</code> if the session should be transient, <code>false</code>, otherwise
     */
    public void setTransient(boolean tranzient) {
        this.tranzient = tranzient;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public boolean isStoreLanguage() {
        return storeLanguage;
    }

    @Override
    public boolean isStaySignedIn() {
        return staySignedIn;
    }

    @Override
    public String getLocale() {
        return locale;
    }

    @Override
    public boolean isStoreLocale() {
        return storeLocale;
    }

}
