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

package com.openexchange.sessionstorage;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.openexchange.session.Origin;
import com.openexchange.session.PutIfAbsent;
import com.openexchange.session.Session;

/**
 * {@link StoredSession} - Represents a session held in session storage.
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class StoredSession implements PutIfAbsent, Serializable {

    private static final long serialVersionUID = -3414389910481034283L;

    /** The parameter name for the alternative session identifier */
    protected static final String PARAM_ALTERNATIVE_ID = Session.PARAM_ALTERNATIVE_ID;

    protected String loginName;
    protected String password;
    protected int contextId;
    protected int userId;
    protected String sessionId;
    protected String secret;
    protected String login;
    protected String randomToken;
    protected String localIp;
    protected String authId;
    protected String hash;
    protected String client;
    protected String userLogin;
    protected Origin origin;
    protected boolean staySignedIn;
    protected final ConcurrentMap<String, Object> parameters;

    /**
     * Initializes a new, empty {@link StoredSession}.
     */
    public StoredSession() {
        super();
        this.parameters = new ConcurrentHashMap<String, Object>(10, 0.9f, 1);
    }

    /**
     * Initializes a new {@link StoredSession}.
     */
    public StoredSession(String sessionId, String loginName, String password, int contextId, int userId, String secret, String login,
        String randomToken, String localIP, String authId, String hash, String client, boolean staySignedIn, Origin origin, Map<String, Object> parameters) {
        this();
        this.sessionId = sessionId;
        this.loginName = loginName;
        this.password = password;
        this.contextId = contextId;
        this.userId = userId;
        this.secret = secret;
        this.login = login;
        this.randomToken = randomToken;
        this.localIp = localIP;
        this.authId = authId;
        this.hash = hash;
        this.client = client;
        this.userLogin = "";
        this.origin = origin;
        this.staySignedIn = staySignedIn;
        // Take over parameters (if not null)
        if (parameters != null) {
            this.parameters.putAll(parameters);
        }
    }

    /**
     * Initializes a new {@link StoredSession}.
     */
    public StoredSession(final Session session) {
        this();
        this.authId = session.getAuthId();
        this.client = session.getClient();
        this.contextId = session.getContextId();
        this.hash = session.getHash();
        this.localIp = session.getLocalIp();
        this.login = session.getLogin();
        this.loginName = session.getLoginName();
        // Assign parameters (if any)
        for (String name : session.getParameterNames()) {
            Object value = session.getParameter(name);
            if (null != value) {
                this.parameters.put(name, value);
            }
        }
        this.password = session.getPassword();
        this.randomToken = session.getRandomToken();
        this.secret = session.getSecret();
        this.sessionId = session.getSessionID();
        this.userId = session.getUserId();
        this.userLogin = session.getUserlogin();
        this.staySignedIn = session.isStaySignedIn();
        this.origin = session.getOrigin();
    }

    @Override
    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(final String loginName) {
        this.loginName = loginName;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password
     *
     * @param password The password
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    @Override
    public int getContextId() {
        return contextId;
    }

    /**
     * Sets the context identifier
     *
     * @param contextId The context identifier
     */
    public void setContextId(final int contextId) {
        this.contextId = contextId;
    }

    @Override
    public int getUserId() {
        return userId;
    }

    /**
     * Sets the user identifier
     *
     * @param userId The user identifier
     */
    public void setUserId(final int userId) {
        this.userId = userId;
    }

    /**
     * Gets the session identifier.
     *
     * @return The session identifier
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the session identifier.
     *
     * @param sessionId The session identifier to set
     */
    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the secret identifier.
     *
     * @param secret The secret identifier
     */
    public void setSecret(final String secret) {
        this.secret = secret;
    }

    @Override
    public String getLogin() {
        return login;
    }

    /**
     * Sets the login; e.g <code>test@foo</code>.
     *
     * @param login The login
     */
    public void setLogin(final String login) {
        this.login = login;
    }

    @Override
    public String getRandomToken() {
        return randomToken;
    }

    /**
     * Sets the random token.
     *
     * @param randomToken The random token
     */
    public void setRandomToken(final String randomToken) {
        this.randomToken = randomToken;
    }

    @Override
    public String getLocalIp() {
        return localIp;
    }

    @Override
    public void setLocalIp(final String localIp) {
        this.localIp = localIp;
    }

    @Override
    public String getAuthId() {
        return authId;
    }

    /**
     * Sets the authentication identifier.
     *
     * @param authId The authentication identifier
     */
    public void setAuthId(final String authId) {
        this.authId = authId;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public void setHash(final String hash) {
        this.hash = hash;
    }

    @Override
    public String getClient() {
        return client;
    }

    @Override
    public void setClient(final String client) {
        this.client = client;
    }

    @Override
    public Object setParameterIfAbsent(String name, Object value) {
        if (PARAM_LOCK.equals(name)) {
            return parameters.get(PARAM_LOCK);
        }
        return parameters.putIfAbsent(name, value);
    }

    @Override
    public boolean containsParameter(final String name) {
        return parameters.containsKey(name);
    }

    @Override
    public Object getParameter(final String name) {
        return parameters.get(name);
    }

    @Override
    public String getSessionID() {
        return sessionId;
    }

    @Override
    public String getUserlogin() {
        return userLogin;
    }

    @Override
    public void setParameter(final String name, final Object value) {
        parameters.put(name, value);
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public boolean isStaySignedIn() {
        return staySignedIn;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(512);
        final String delim = ", ";
        builder.append('{');
        if (loginName != null) {
            builder.append("loginName=").append(loginName).append(delim);
        }
        if (password != null) {
            builder.append("password=").append("*****").append(delim);
        }
        builder.append("contextId=").append(contextId).append(", userId=").append(userId).append(delim);
        if (sessionId != null) {
            builder.append("sessionId=").append(sessionId).append(delim);
        }
        if (secret != null) {
            builder.append("secret=").append(secret).append(delim);
        }
        if (login != null) {
            builder.append("login=").append(login).append(delim);
        }
        if (randomToken != null) {
            builder.append("randomToken=").append(randomToken).append(delim);
        }
        if (localIp != null) {
            builder.append("localIp=").append(localIp).append(delim);
        }
        if (authId != null) {
            builder.append("authId=").append(authId).append(delim);
        }
        if (hash != null) {
            builder.append("hash=").append(hash).append(delim);
        }
        if (client != null) {
            builder.append("client=").append(client).append(delim);
        }
        if (userLogin != null) {
            builder.append("userLogin=").append(userLogin).append(delim);
        }
        if (parameters != null) {
            builder.append("parameters=").append(parameters);
        }
        builder.append('}');
        return builder.toString();
    }

    @Override
    public Set<String> getParameterNames() {
        return parameters.keySet();
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

}
