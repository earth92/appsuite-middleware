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

package com.openexchange.subscribe;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.openexchange.groupware.generic.TargetFolderDefinition;
import com.openexchange.session.Origin;
import com.openexchange.session.Session;
import com.openexchange.session.Sessions;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link TargetFolderSession} - A {@link Session} based on a passed {@link TargetFolderDefinition} instance.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class TargetFolderSession implements Session {

    private final int                 contextId;
    private final int                 userId;
    private final Map<String, Object> params;
    private final Session             session;

    public TargetFolderSession(final TargetFolderDefinition target) {
        super();
        contextId = target.getContext().getContextId();
        userId = target.getUserId();

        // Initialize
        Optional<Session> optionalSession = findSessionFor(target.getUserId(), target.getContext().getContextId());
        if (optionalSession.isPresent()) {
            session = ServerSessionAdapter.valueOf(optionalSession.get(), target.getContext());
            params = null;
        } else {
            session = null;
            params = new HashMap<>(8);
        }
    }

    @Override
    public int getContextId() {
        return contextId;
    }

    @Override
    public int getUserId() {
        return userId;
    }

    @Override
    public String getLocalIp() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getLocalIp()");
        }
        return session.getLocalIp();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setLocalIp(final String ip) {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.setLocalIp()");
        }
        session.setLocalIp(ip);
    }

    @Override
    public String getLoginName() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getLoginName()");
        }
        return session.getLoginName();
    }

    @Override
    public boolean containsParameter(final String name) {
        if (null != params) {
            return params.containsKey(name);
        }
        return session.containsParameter(name);
    }

    @Override
    public Object getParameter(final String name) {
        if (null != params) {
            return params.get(name);
        }
        return session.getParameter(name);
    }

    @Override
    public String getPassword() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getPassword()");
        }
        return session.getPassword();
    }

    @Override
    public String getRandomToken() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getRandomToken()");
        }
        return session.getRandomToken();
    }

    @Override
    public String getSecret() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getSecret()");
        }
        return session.getSecret();
    }

    @Override
    public String getSessionID() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getSessionID()");
        }
        return session.getSessionID();
    }

    @Override
    public String getUserlogin() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getUserlogin()");
        }
        return session.getUserlogin();
    }

    @Override
    public String getLogin() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getLogin()");
        }
        return session.getLogin();
    }

    @Override
    public void setParameter(final String name, final Object value) {
        if (null != params) {
            if (null == value) {
                params.remove(name);
            } else {
                params.put(name, value);
            }
        } else {
            if (null != session) {
                session.setParameter(name, value);
            }
        }
    }

    @Override
    public String getAuthId() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getAuthId()");
        }
        return session.getAuthId();
    }

    @Override
    public String getHash() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getHash()");
        }
        return session.getHash();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setHash(final String hash) {
        if (null != session) {
            session.setHash(hash);
        }
    }

    @Override
    public String getClient() {
        if (null == session) {
            throw new UnsupportedOperationException("TargetFolderSession.getClient()");
        }
        return session.getClient();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setClient(final String client) {
        if (null != session) {
            session.setClient(client);
        }
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public boolean isStaySignedIn() {
        return null == session ? false : session.isStaySignedIn();
    }

    @Override
    public Origin getOrigin() {
        return null == session ? null : session.getOrigin();
    }

    @Override
    public Set<String> getParameterNames() {
        Set<String> retval = new HashSet<>(8);
        if (null != params) {
            retval.addAll(params.keySet());
        } else {
            if (null != session) {
                retval.addAll(session.getParameterNames());
            }
        }
        return retval;
    }

    @Override
    public String toString() {
        StringBuilder retval = new StringBuilder();
        retval.append("Context=").append(contextId).append(",");
        retval.append("UserId=").append(userId).append(",");
        if (null != session) {
            retval.append(session.toString());
        } else {
            retval.append("parameters:[");
            for (String s : getParameterNames()) {
                retval.append(s).append("=").append(getParameter(s));
                retval.append(",");
            }
            retval.deleteCharAt(retval.length() - 1);
            retval.append("]");
        }
        return retval.toString();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    private static Optional<Session> findSessionFor(int userId, int contextId) {
        SessiondService sessiondService = SessiondService.SERVICE_REFERENCE.get();
        if (sessiondService == null) {
            return Optional.empty();
        }

        Optional<Collection<String>> optionalSessions = Sessions.getSessionsOfUser(userId, contextId, sessiondService);
        if (!optionalSessions.isPresent()) {
            return Optional.empty();
        }

        for (String sessionId : optionalSessions.get()) {
            Session session = sessiondService.getSession(sessionId);
            if (session != null && (Origin.HTTP_JSON == session.getOrigin())) {
                return Optional.of(session);
            }
        }

        // Found no suitable session
        return Optional.empty();
    }

}
