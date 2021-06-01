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

package com.openexchange.ajax.login.session;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.SessionServletInterceptor;
import com.openexchange.ajax.login.LoginConfiguration;
import com.openexchange.exception.OXException;
import com.openexchange.session.PutIfAbsent;
import com.openexchange.session.Session;


/**
 * {@link CookieRefresher} - Cares about refreshing session-associated cookies.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.0
 */
public class CookieRefresher implements SessionServletInterceptor {

    private static final String PARAM_COOKIE_REFRESH_TIMESTAMP = Session.PARAM_COOKIE_REFRESH_TIMESTAMP;
    private static final String PARAM_REFRESH_SESSION_COOKIE_FLAG = "as";

    private final LoginConfiguration conf;

    /**
     * Initializes a new {@link CookieRefresher}.
     */
    public CookieRefresher(LoginConfiguration conf) {
        super();
        this.conf = conf;
    }

    @Override
    public void intercept(Session session, HttpServletRequest req, HttpServletResponse resp) throws OXException {
        if (null == session || !session.isStaySignedIn()) {
            return;
        }
        if (needsCookieRefresh(session)) {
            String hash = session.getHash();

            // Write secret+public cookie
            LoginServlet.writeSecretCookie(req, resp, session, hash, req.isSecure(), req.getServerName(), conf);

            // Refresh HTTP session, too
            req.getSession();
        } else if (needsSessionCookieRefresh(session)) {
            String hash = session.getHash();

            // Check whether to write session cookie as well
            if (refreshSessionCookie(session.getSessionID(), LoginServlet.SESSION_PREFIX + hash, req)) {
                LoginServlet.writeSessionCookie(resp, session, hash, req.isSecure(), req.getServerName());
            }
        }
    }

    private boolean refreshSessionCookie(String sessionId, String expectedSessionCookieName, HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (null == cookies || 0 == cookies.length) {
            return false;
        }

        for (int i = cookies.length; i-- > 0;) {
            Cookie cookie = cookies[i];
            if (expectedSessionCookieName.equals(cookie.getName()) && sessionId.equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean needsCookieRefresh(Session session) {
        Long stamp = (Long) session.getParameter(PARAM_COOKIE_REFRESH_TIMESTAMP);
        if (null == stamp) {
            // No time stamp available, yet
            if (session instanceof PutIfAbsent) {
                ((PutIfAbsent) session).setParameterIfAbsent(PARAM_COOKIE_REFRESH_TIMESTAMP, createNewStamp());
            } else {
                session.setParameter(PARAM_COOKIE_REFRESH_TIMESTAMP, createNewStamp());
            }
            return false;
        }

        int intervalMillis = ((conf.getCookieExpiry() / 7) * 1000);
        long now = System.currentTimeMillis();
        if ((now - intervalMillis) > stamp.longValue()) {
            // Needs refresh
            synchronized (stamp) {
                Long check = (Long) session.getParameter(PARAM_COOKIE_REFRESH_TIMESTAMP);
                if (!stamp.equals(check)) {
                    // Concurrent update. Another thread already initiated cookie refresh
                    return false;
                }

                session.setParameter(PARAM_COOKIE_REFRESH_TIMESTAMP, createNewStamp());
                if (session.isStaySignedIn()) {
                    // Set marker for session cookie for the next request
                    session.setParameter(PARAM_REFRESH_SESSION_COOKIE_FLAG, Boolean.TRUE);
                }
                return true;
            }
        }
        return false;
    }

    private Long createNewStamp() {
        // Explicitly use "new Long()" constructor!
        return new Long(System.currentTimeMillis());
    }

    private boolean needsSessionCookieRefresh(Session session) {
        Boolean flag = (Boolean) session.getParameter(PARAM_REFRESH_SESSION_COOKIE_FLAG);
        if ((null == flag) || (false == flag.booleanValue())) {
            return false;
        }

        session.setParameter(PARAM_REFRESH_SESSION_COOKIE_FLAG, null);
        return true;
    }

}
