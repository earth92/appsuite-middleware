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

package com.openexchange.sessiond.impl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import com.openexchange.session.DefaultSessionAttributes;
import com.openexchange.session.Origin;
import com.openexchange.session.PutIfAbsent;
import com.openexchange.session.Session;
import com.openexchange.session.SessionDescription;
import com.openexchange.sessiond.osgi.Services;
import com.openexchange.sessionstorage.SessionStorageService;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.Task;

/**
 * {@link SessionImpl} - Implements interface {@link Session} (and {@link PutIfAbsent}).
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class SessionImpl implements PutIfAbsent {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SessionImpl.class);

    private final String loginName;
    private volatile String password;
    private final int contextId;
    private final int userId;
    private final String sessionId;
    private final String secret;
    private final String login;
    private volatile String randomToken;
    private volatile String localIp;
    private final String authId;
    private volatile String hash;
    private volatile String client;
    private volatile boolean tranzient;
    private final boolean staySignedIn;
    private final Origin origin;
    private final ConcurrentMap<String, Object> parameters;

    /**
     * Initializes a new {@link SessionImpl}
     *
     * @param userId The user ID
     * @param loginName The login name
     * @param password The password
     * @param contextId The context ID
     * @param sessionId The session ID
     * @param secret The secret (cookie identifier)
     * @param randomToken The random token
     * @param localIp The local IP
     * @param login The full user's login; e.g. <i>test@foo.bar</i>
     * @param authId The authentication identifier that is used to trace the login request across different systems
     * @param hash The hash identifier
     * @param client The client type
     * @param tranzient <code>true</code> if the session should be transient, <code>false</code>, otherwise
     * @param staySignedIn Whether session is supposed to be annotated with "stay signed in"; otherwise <code>false</code>
     */
    public SessionImpl(int userId, String loginName, String password, int contextId, String sessionId,
        String secret, String randomToken, String localIp, String login, String authId, String hash,
        String client, boolean tranzient, boolean staySignedIn, Origin origin) {
        this(userId, loginName, password, contextId, sessionId, secret, randomToken, localIp, login, authId, hash, client, tranzient,
            staySignedIn, UUIDSessionIdGenerator.randomUUID(), origin, null);
    }

    /**
     * Initializes a new {@link SessionImpl}.
     *
     * @param sessionDescription The session description
     */
    public SessionImpl(SessionDescription sessionDescription) {
        this(sessionDescription.getUserID(), sessionDescription.getLoginName(), sessionDescription.getPassword(),
            sessionDescription.getContextId(), sessionDescription.getSessionID(), sessionDescription.getSecret(),
            sessionDescription.getRandomToken(), sessionDescription.getLocalIp(), sessionDescription.getLogin(),
            sessionDescription.getAuthId(), sessionDescription.getHash(), sessionDescription.getClient(), sessionDescription.isTransient(),
            sessionDescription.isStaySignedIn(), sessionDescription.getAlternativeId(), sessionDescription.getOrigin(),
            sessionDescription.getParameters());
    }

    /**
     * Initializes a new {@link SessionImpl}
     *
     * @param userId The user ID
     * @param loginName The login name
     * @param password The password
     * @param contextId The context ID
     * @param sessionId The session ID
     * @param secret The secret (cookie identifier)
     * @param randomToken The random token
     * @param localIp The local IP
     * @param login The full user's login; e.g. <i>test@foo.bar</i>
     * @param authId The authentication identifier that is used to trace the login request across different systems
     * @param hash The hash identifier
     * @param client The client type
     * @param tranzient <code>true</code> if the session should be transient, <code>false</code>, otherwise
     * @param staySignedIn Whether session is supposed to be annotated with "stay signed in"; otherwise <code>false</code>
     * @param alternativeId The alternative session identifier
     */
    public SessionImpl(int userId, String loginName, String password, int contextId, String sessionId,
        String secret, String randomToken, String localIp, String login, String authId, String hash,
        String client, boolean tranzient, boolean staySignedIn, String alternativeId, Origin origin, Map<String, Object> parameters) {
        super();
        this.userId = userId;
        this.loginName = loginName;
        this.password = password;
        this.sessionId = sessionId;
        this.secret = secret;
        this.randomToken = randomToken;
        this.localIp = localIp;
        this.contextId = contextId;
        this.login = login;
        this.authId = authId;
        this.hash = hash;
        this.client = client;
        this.tranzient = tranzient;
        this.staySignedIn = staySignedIn;
        this.origin = origin;
        this.parameters = new ConcurrentHashMap<String, Object>(16, 0.9F, 1);
        if (null != parameters) {
            // Copy given parameters
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                this.parameters.put(parameter.getKey(), parameter.getValue());
            }
        }
        this.parameters.put(PARAM_LOCK, new ReentrantLock());
        this.parameters.put(PARAM_COUNTER, new AtomicInteger());
        if (null != alternativeId) {
            this.parameters.put(PARAM_ALTERNATIVE_ID, alternativeId);
        } else if (false == this.parameters.containsKey(PARAM_ALTERNATIVE_ID)) {
            this.parameters.put(PARAM_ALTERNATIVE_ID, UUIDSessionIdGenerator.randomUUID());
        }
    }

    /**
     * Initializes a new {@link SessionImpl}
     *
     * @param session The copy session
     */
    public SessionImpl(Session s) {
        super();
        this.userId = s.getUserId();
        this.loginName = s.getLoginName();
        this.password = s.getPassword();
        this.sessionId = s.getSessionID();
        this.secret = s.getSecret();
        this.randomToken = s.getRandomToken();
        this.localIp = s.getLocalIp();
        this.contextId = s.getContextId();
        this.login = s.getLogin();
        this.authId = s.getAuthId();
        this.hash = s.getHash();
        this.client = s.getClient();
        this.tranzient = false;
        this.origin = s.getOrigin();
        this.staySignedIn = s.isStaySignedIn();
        parameters = new ConcurrentHashMap<String, Object>(16, 0.9F, 1);
        for (String name : s.getParameterNames()) {
            parameters.put(name, s.getParameter(name));
        }
        parameters.put(PARAM_LOCK, new ReentrantLock());
        parameters.put(PARAM_COUNTER, new AtomicInteger());

        Object obj = s.getParameter(PARAM_ALTERNATIVE_ID);
        if (null == obj) {
            parameters.put(PARAM_ALTERNATIVE_ID, UUIDSessionIdGenerator.randomUUID());
        } else {
            parameters.put(PARAM_ALTERNATIVE_ID, obj);
        }

        obj = s.getParameter(PARAM_OAUTH_ACCESS_TOKEN);
        if (null != obj) {
            parameters.put(PARAM_OAUTH_ACCESS_TOKEN, obj);
        }

        obj = s.getParameter(PARAM_USER_AGENT);
        if (null != obj) {
            parameters.put(PARAM_USER_AGENT, obj);
        }

        obj = s.getParameter(PARAM_LOGIN_TIME);
        if (null != obj) {
            parameters.put(PARAM_LOGIN_TIME, obj);
        }
    }

    /**
     * Logs differences between this and specified session.
     *
     * @param s The session to compare with
     * @param logger The logger
     */
    public void logDiff(SessionImpl s, org.slf4j.Logger logger) {
        if (null == s || null == logger) {
            return;
        }
        StringBuilder sb = new StringBuilder(1024).append("Session Diff:\n");
        String format = "%-15s%-45s%s";
        sb.append(String.format(format, "Name", "Session #1", "Session #1"));
        sb.append(String.format(format, "User-Id", Integer.valueOf(userId), Integer.valueOf(s.userId)));
        sb.append(String.format(format, "Context-Id", Integer.valueOf(contextId), Integer.valueOf(s.contextId)));
        sb.append(String.format(format, "Session-Id", sessionId, s.sessionId));
        sb.append(String.format(format, "Secret", secret, s.secret));
        sb.append(String.format(format, "Alternative-Id", parameters.get(PARAM_ALTERNATIVE_ID), s.parameters.get(PARAM_ALTERNATIVE_ID)));
        sb.append(String.format(format, "Auth-Id", authId, s.authId));
        sb.append(String.format(format, "Login", login, s.login));
        sb.append(String.format(format, "Login-Name", loginName, s.loginName));
        sb.append(String.format(format, "Local IP", localIp, s.localIp));
        sb.append(String.format(format, "Random-Token", randomToken, s.randomToken));
        sb.append(String.format(format, "Hash", hash, s.hash));
        sb.append(String.format(format, "User-Agent", parameters.get(PARAM_USER_AGENT), s.getParameter(PARAM_USER_AGENT)));
        sb.append(String.format(format, "Login-time", parameters.get(PARAM_LOGIN_TIME), s.getParameter(PARAM_LOGIN_TIME)));
        logger.info(sb.toString());
    }

    /**
     * Whether specified session is considered equal to this one.
     *
     * @param s The other session
     * @return <code>true</code> if equal; otherwise <code>false</code>
     */
    public boolean consideredEqual(SessionImpl s) {
        if (this == s) {
            return true;
        }
        if (null == s) {
            return false;
        }
        if (userId != s.userId) {
            return false;
        }
        if (contextId != s.contextId) {
            return false;
        }
        if (null == loginName) {
            if (null != s.loginName) {
                return false;
            }
        } else if (loginName.equals(s.loginName)) {
            return false;
        }
        if (null == password) {
            if (null != s.password) {
                return false;
            }
        } else if (password.equals(s.password)) {
            return false;
        }
        if (null == sessionId) {
            if (null != s.sessionId) {
                return false;
            }
        } else if (sessionId.equals(s.sessionId)) {
            return false;
        }
        if (null == secret) {
            if (null != s.secret) {
                return false;
            }
        } else if (secret.equals(s.secret)) {
            return false;
        }
        if (null == randomToken) {
            if (null != s.randomToken) {
                return false;
            }
        } else if (randomToken.equals(s.randomToken)) {
            return false;
        }
        if (null == login) {
            if (null != s.login) {
                return false;
            }
        } else if (login.equals(s.login)) {
            return false;
        }
        if (null == localIp) {
            if (null != s.localIp) {
                return false;
            }
        } else if (localIp.equals(s.localIp)) {
            return false;
        }
        if (null == authId) {
            if (null != s.authId) {
                return false;
            }
        } else if (authId.equals(s.authId)) {
            return false;
        }
        if (null == hash) {
            if (null != s.hash) {
                return false;
            }
        } else if (hash.equals(s.hash)) {
            return false;
        }
        if (staySignedIn != s.staySignedIn) {
            return false;
        }
        Object object1 = parameters.get(PARAM_ALTERNATIVE_ID);
        Object object2 = s.parameters.get(PARAM_ALTERNATIVE_ID);
        if (null == object1) {
            if (null != object2) {
                return false;
            }
        } else if (object1.equals(object2)) {
            return false;
        }
        Object ua1 = parameters.get(PARAM_USER_AGENT);
        Object ua2 = s.getParameter(PARAM_USER_AGENT);
        if (null == ua1) {
            if (null != ua2) {
                return false;
            }
        } else if (ua1.equals(ua2)) {
            return false;
        }
        Object lt1 = parameters.get(PARAM_LOGIN_TIME);
        Object lt2 = s.getParameter(PARAM_LOGIN_TIME);
        if (null == lt1) {
            if (null != lt2) {
                return false;
            }
        } else if (lt1.equals(lt2)) {
            return false;
        }
        return true;
    }

    @Override
    public int getContextId() {
        return contextId;
    }

    @Override
    public boolean containsParameter(String name) {
        return parameters.containsKey(name);
    }

    @Override
    public Object getParameter(String name) {
        return parameters.get(name);
    }

    @Override
    public String getRandomToken() {
        return randomToken;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    @Override
    public String getSessionID() {
        return sessionId;
    }

    public int getUserID() {
        return userId;
    }

    @Override
    public void setParameter(String name, Object value) {
        if (PARAM_LOCK.equals(name)) {
            return;
        }
        if (null == value) {
            parameters.remove(name);
        } else {
            parameters.put(name, value);
        }
    }

    @Override
    public Object setParameterIfAbsent(String name, Object value) {
        if (PARAM_LOCK.equals(name)) {
            return parameters.get(PARAM_LOCK);
        }
        return parameters.putIfAbsent(name, value);
    }

    /**
     * Removes the random token
     */
    public void removeRandomToken() {
        randomToken = null;
    }

    @Override
    public String getLocalIp() {
        return localIp;
    }

    @Override
    public void setLocalIp(String localIp) {
        try {
            setLocalIp(localIp, true);
        } catch (Exception e) {
            LOG.warn("Failed to distribute change of IP address among remote nodes.", e);
        }
    }

    /**
     * Sets the local IP address
     *
     * @param localIp The local IP address
     * @param propagate Whether to propagate that IP change through {@code SessiondService}
     */
    public void setLocalIp(final String localIp, boolean propagate) {
        this.localIp = localIp;
        if (propagate) {
            final SessionStorageService storageService = Services.getService(SessionStorageService.class);
            if (storageService != null) {
                final String sessionId = this.sessionId;
                Task<Void> c = new AbstractTask<Void>() {

                    @Override
                    public Void call() throws Exception {
                        try {
                            storageService.setSessionAttributes(sessionId, DefaultSessionAttributes.builder().withLocalIp(localIp).build());
                        } catch (Exception e) {
                            // Ignore
                        }
                        return null;
                    }
                };
                TimeoutTaskWrapper.submit(c);
            }
        }
    }

    @Override
    public String getLoginName() {
        return loginName;
    }

    @Override
    public int getUserId() {
        return userId;
    }

    @Override
    public String getUserlogin() {
        return loginName;
    }

    @Override
    public String getLogin() {
        return login;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password
     *
     * @param password The password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getAuthId() {
        return authId;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public void setHash(String hash) {
        try {
            setHash(hash, true);
        } catch (Exception e) {
            LOG.error("Failed to propagate change of hash identifier.", e);
        }
    }

    /**
     * Sets the hash identifier
     *
     * @param hash The hash identifier
     * @param propagate Whether to propagate that change through {@code SessiondService}
     */
    public void setHash(final String hash, boolean propagate) {
        this.hash = hash;
        if (propagate) {
            final SessionStorageService storageService = Services.getService(SessionStorageService.class);
            if (storageService != null) {
                final String sessionId = this.sessionId;
                Task<Void> c = new AbstractTask<Void>() {

                    @Override
                    public Void call() throws Exception {
                        try {
                            storageService.setSessionAttributes(sessionId, DefaultSessionAttributes.builder().withHash(hash).build());
                        } catch (Exception e) {
                            // Ignore
                        }
                        return null;
                    }
                };
                TimeoutTaskWrapper.submit(c);
            }
        }
    }

    @Override
    public String getClient() {
        return client;
    }

    @Override
    public void setClient(String client) {
        try {
            setClient(client, true);
        } catch (Exception e) {
            LOG.error("Failed to propagate change of client identifier.", e);
        }
    }

    @Override
    public boolean isTransient() {
        return tranzient;
    }

    @Override
    public boolean isStaySignedIn() {
        return staySignedIn;
    }

    /**
     * Sets if the session is transient or not.
     *
     * @param tranzient <code>true</code> if the session is transient, <code>false</code>, otherwise
     */
    public void setTransient(boolean tranzient) {
        this.tranzient = tranzient;
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

    /**
     * Sets the client identifier
     *
     * @param client The client identifier
     * @param propagate Whether to propagate that change
     */
    public void setClient(final String client, boolean propagate) {
        this.client = client;
        if (propagate) {
            final SessionStorageService storageService = Services.getService(SessionStorageService.class);
            if (storageService != null) {
                final String sessionId = this.sessionId;
                Task<Void> c = new AbstractTask<Void>() {

                    @Override
                    public Void call() throws Exception {
                        try {
                            storageService.setSessionAttributes(sessionId, DefaultSessionAttributes.builder().withClient(client).build());
                        } catch (Exception e) {
                            // Ignore
                        }
                        return null;
                    }
                };
                TimeoutTaskWrapper.submit(c);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(128);
        builder.append('{');
        builder.append("contextId=").append(contextId).append(", userId=").append(userId).append(", ");
        if (sessionId != null) {
            builder.append("sessionId=").append(sessionId).append(", ");
        }
        if (login != null) {
            builder.append("login=").append(login).append(", ");
        }
        String localIp = this.localIp;
        if (localIp != null) {
            builder.append("localIp=").append(localIp).append(", ");
        }
        if (authId != null) {
            builder.append("authId=").append(authId).append(", ");
        }
        String hash = this.hash;
        if (hash != null) {
            builder.append("hash=").append(hash).append(", ");
        }
        String client = this.client;
        if (client != null) {
            builder.append("client=").append(client).append(", ");
        }
        builder.append("transient=").append(tranzient);
        builder.append('}');
        return builder.toString();
    }

    @Override
    public Set<String> getParameterNames() {
        return parameters.keySet();
    }
}
