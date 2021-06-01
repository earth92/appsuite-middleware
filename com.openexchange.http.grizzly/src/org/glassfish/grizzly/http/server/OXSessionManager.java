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

package org.glassfish.grizzly.http.server;

import static com.openexchange.servlet.Constants.HTTP_SESSION_ATTR_AUTHENTICATED;
import static com.openexchange.servlet.Constants.HTTP_SESSION_ATTR_RATE_LIMITED;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.server.util.Globals;
import org.slf4j.Logger;
import com.openexchange.http.grizzly.GrizzlyConfig;
import com.openexchange.log.LogProperties;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;
import com.openexchange.tools.servlet.http.Cookies;


/**
 * {@link OXSessionManager} - Open-Xchange HTTP session manager.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.4
 */
public class OXSessionManager implements SessionManager {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OXSessionManager.class);

    private static final int MIN_PERIOD_SECONDS = 5;

    // -----------------------------------------------------------------------------------------------

    private final GrizzlyConfig grizzlyConfig;
    private final ConcurrentMap<String, Session> sessions;
    private final Random rnd;
    private final ScheduledTimerTask sessionExpirer;
    private final int max;
    private final boolean considerSessionCount;
    private final Lock lock;
    private final AtomicReference<String> sessionCookieName = new AtomicReference<String>(Globals.SESSION_COOKIE_NAME);
    private int sessionsCount;  // protected by lock
    private long lastCleanUp;   // protected by lock

    /**
     * Initializes a new {@link OXSessionManager}.
     */
    public OXSessionManager(GrizzlyConfig grizzlyConfig, TimerService timerService) {
        super();
        this.grizzlyConfig = grizzlyConfig;
        int max = grizzlyConfig.getMaxNumberOfHttpSessions();
        this.max = max;
        this.considerSessionCount = max > 0;
        this.sessions = new ConcurrentHashMap<>();
        this.rnd = new Random();
        this.sessionsCount = 0;
        final ReentrantLock lock = new ReentrantLock();
        this.lock = lock;
        this.lastCleanUp = 0;

        int configuredSessionTimeout = grizzlyConfig.getCookieMaxInactivityInterval();
        int periodSeconds = grizzlyConfig.getSessionExpiryCheckInterval();
        if (periodSeconds > configuredSessionTimeout) {
            periodSeconds = configuredSessionTimeout >> 2;
        }
        if (periodSeconds < MIN_PERIOD_SECONDS) {
            periodSeconds = MIN_PERIOD_SECONDS;
        }

        this.sessionExpirer = timerService.scheduleAtFixedRate(newTaskForPeriodicChecks(lock), periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    /**
     * Cleans-up the session collection. Drops invalid/expired sessions.
     * <p>
     * <b>Must only be called when holding lock.</b>
     *
     * @param currentTime The current time stamp
     * @return <code>true</code> if any session has been removed; otherwise <code>false</code>
     */
    boolean cleanUp(long currentTime) {
        boolean anyRemoved = false;
        Session session;
        for (Iterator<Session> it = sessions.values().iterator(); it.hasNext();) {
            session = it.next();
            if (isInvalid(session) || isNotAuthenticatedAndUnusedAttributes(session) || isRateLimited(session) || isTimedOut(currentTime, session) || isUnusedSession(currentTime, session)) {
                session.setValid(false);
                it.remove();
                sessionsCount--;
                anyRemoved = true;
            }
        }

        lastCleanUp = System.currentTimeMillis();
        return anyRemoved;
    }

    /**
     * Checks if given session has been marked as invalid.
     *
     * @param session The session to check
     * @return <code>true</code> if invalid; otherwise <code>false</code>
     */
    private static boolean isInvalid(Session session) {
        return !session.isValid();
    }

    /**
     * Checks if given session has no "authenticated" marker set.
     *
     * @param session The session to check
     * @return <code>true</code> if "authenticated" marker is absent; otherwise <code>false</code>
     */
    private boolean isNotAuthenticatedAndUnusedAttributes(Session session) {
        if (!grizzlyConfig.isRemoveNonAuthenticatedSessions()) {
            return false;
        }
        ConcurrentMap<String, Object> attributes = session.attributes();
        return !Boolean.TRUE.equals(attributes.get(HTTP_SESSION_ATTR_AUTHENTICATED)) && attributes.isEmpty();
    }

    /**
     * Checks if given session has no "authenticated" marker set.
     *
     * @param session The session to check
     * @return <code>true</code> if "authenticated" marker is absent; otherwise <code>false</code>
     */
    private boolean isRateLimited(Session session) {
        return Boolean.TRUE.equals(session.getAttribute(HTTP_SESSION_ATTR_RATE_LIMITED));
    }

    /**
     * Checks whether the session ran into possible set session timeout or not.
     *
     * @param currentTime The current time to compare to
     * @param session The session to check
     * @return <code>true</code> if the session ran into the timeout, otherwise <code>false</code>
     */
    private static boolean isTimedOut(long currentTime, Session session) {
        long timeout = session.getSessionTimeout();
        return (timeout > 0) && ((currentTime - session.getTimestamp()) > timeout);
    }

    /**
     * Gets a value indicating whether the session can be seen as unused until now or not
     * <p>
     * Using {@link GrizzlyConfig#getSessionUnjoinedThreshold()} to measure elapsed time
     *
     * @param currentTime The current time to compare to
     * @param session The session to check
     * @return <code>true</code> if the session can be seen as unused, <code>false</code> if not.
     */
    private boolean isUnusedSession(long currentTime, Session session) {
        if (!session.isNew()) {
            // Client already joined the session
            return false;
        }

        int unjoinedThresholdSeconds = grizzlyConfig.getSessionUnjoinedThreshold();
        return (unjoinedThresholdSeconds > 0) && ((currentTime - session.getCreationTime()) > (unjoinedThresholdSeconds * 1000l));
    }

    /**
     * Disposes this instance
     */
    public void destroy() {
        lock.lock();
        try {
            sessionExpirer.cancel();
            sessions.clear();
            sessionsCount = 0;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setSessionCookieName(final String name) {
        if (name != null && !name.isEmpty()) {
            sessionCookieName.set(name);
        }
    }

    @Override
    public String getSessionCookieName() {
        return sessionCookieName.get();
    }

    @Override
    public Session getSession(Request request, String requestedSessionId) {
        if (requestedSessionId == null) {
            return null;
        }

        Session session = sessions.get(requestedSessionId);
        if (session == null) {
            removeInvalidSessionCookie(request, requestedSessionId);
            return null;
        }

        if (session.isValid()) {
            if (request.isRequestedSessionIdFromURL() && !hasSessionCookie(request, requestedSessionId)) {
                Response response = request.response;
                response.addCookie(createSessionCookie(request, session.getIdInternal()));
            }
            return session;
        }

        removeInvalidSessionCookie(request, requestedSessionId);
        return null;
    }

    @Override
    public Session createSession(Request request) {
        lock.lock();
        try {
            if (considerSessionCount) {
                while (sessionsCount >= max) {
                    boolean anyRemoved = false;

                    long currentTime = System.currentTimeMillis();
                    if ((currentTime - lastCleanUp) >= (MIN_PERIOD_SECONDS * 1000)) {
                        anyRemoved = cleanUp(currentTime);
                    }

                    if (!anyRemoved) {
                        throw onMaxSessionCountExceeded();
                    }
                }
            }

            Session session = new Session();

            String requestedSessionId;
            do {
                requestedSessionId = createSessionID();
                session.setIdInternal(requestedSessionId);
            } while (sessions.putIfAbsent(requestedSessionId, session) != null);
            sessionsCount++;

            LogProperties.put(LogProperties.Name.GRIZZLY_HTTP_SESSION, requestedSessionId);

            return session;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invoked when max. session count is exceeded.
     *
     * @return The appropriate instance of <code>IllegalStateException</code> reflecting the exceeded count
     */
    protected IllegalStateException onMaxSessionCountExceeded() {
        String message = "Max. number of HTTP sessions (" + max + ") exceeded.";
        LOG.warn(message);
        return new IllegalStateException(message);
    }

    @Override
    public String changeSessionId(Request request, Session session) {
        lock.lock();
        try {
            final String oldSessionId = session.getIdInternal();
            final String newSessionId = String.valueOf(generateRandomLong());

            session.setIdInternal(newSessionId);

            sessions.remove(oldSessionId);
            sessions.put(newSessionId, session);
            return oldSessionId;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void configureSessionCookie(Request request, Cookie cookie) {
        cookie.setPath("/");

        String serverName  = request.getServerName();
        String domain = Cookies.getDomainValue(null == serverName ? determineServerNameByLogProperty() : serverName);
        if (domain != null) {
            cookie.setDomain(domain);
        }

        /*
         * Toggle the security of the cookie on when we are dealing with a https request or the forceHttps config option is true e.g. when A
         * proxy in front of apache terminates ssl. The exception from forced https is a request from the local LAN.
         */
        boolean isCookieSecure = request.isSecure() || (grizzlyConfig.isForceHttps() && !Cookies.isLocalLan(request.getServerName()));
        cookie.setSecure(isCookieSecure);

        /*
         * Always set cookie max age to configured maximum value, otherwise it may break 'stay signed in' sessions
         */
        cookie.setMaxAge(grizzlyConfig.getCookieMaxAge());
    }

    private static String determineServerNameByLogProperty() {
        return LogProperties.getLogProperty(LogProperties.Name.GRIZZLY_SERVER_NAME);
    }

    /**
     * Create a new JSessioID String that consists of a:
     * <pre>
     *  &lt;random&gt; + ("-" + &lt;the urlencoded domain of this server&gt;)? + "." + &lt;backendRoute&gt;
     * </pre>
     * Example:
     * <pre>
     *  367190855121044669-open%2Dxchange%2Ecom.OX0
     * </pre>
     *
     * @return A new JSessionId value as String
     */
    private String createSessionID() {
        String backendRoute = grizzlyConfig.getBackendRoute();
        StringBuilder idBuilder = new StringBuilder(String.valueOf(generateRandomLong()));
        idBuilder.append('.').append(backendRoute);
        return idBuilder.toString();
    }

    /**
     * Creates a new JSessionIdCookie based on a sessionID and the server configuration.
     *
     * @param sessionID The sessionId to use for cookie generation
     * @return The new JSessionId Cookie
     */
    private Cookie createSessionCookie(Request request, String sessionID) {
        Cookie jSessionIdCookie = new Cookie(request.obtainSessionCookieName(), sessionID);
        configureSessionCookie(request, jSessionIdCookie);
        return jSessionIdCookie;
    }

    /**
     * Checks if this request has a session cookie associated with specified session identifier.
     *
     * @param sessionID The session identifier to use for look-up
     * @return <code>true</code> if this request contains such a session cookie; otherwise <code>false</code>
     */
    private boolean hasSessionCookie(Request request, String sessionID) {
        Cookie[] cookies = request.getCookies();
        if (null == cookies || cookies.length <= 0) {
            return false;
        }

        String sessionCookieName = request.obtainSessionCookieName();
        for (Cookie cookie : cookies) {
            if (sessionCookieName.equals(cookie.getName()) && sessionID.equals(cookie.getValue())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remove invalid JSession cookie used in the Request. Cookies are invalid when:
     *
     * @param invalidSessionId The invalid sessionId requested by the browser/cookie
     */
    private void removeInvalidSessionCookie(Request request, String invalidSessionId) {
        Cookie[] cookies = request.getCookies();
        if (null == cookies || cookies.length <= 0) {
            return;
        }

        String sessionCookieName = request.obtainSessionCookieName();
        Response response = request.response;
        for (Cookie cookie : cookies) {
            if (cookie.getName().startsWith(sessionCookieName)) {
                if (cookie.getValue().equals(invalidSessionId)) {
                    response.addCookie(createinvalidationCookie(cookie));

                    String domain = Cookies.getDomainValue(request.getServerName());
                    response.addCookie(createinvalidationCookie(cookie, null == domain ? request.getServerName() : domain));
                    break;
                }
            }
        }
    }

    /**
     * Generate a invalidation Cookie that can be added to the response to prompt the browser to remove that cookie.
     *
     * @param invalidCookie The invalid Cookie from the incoming request
     * @return an invalidation Cookie that can be added to the response to prompt the browser to remove that cookie.
     */
    private Cookie createinvalidationCookie(Cookie invalidCookie) {
        Cookie invalidationCookie = new Cookie(invalidCookie.getName(), invalidCookie.getValue());
        invalidationCookie.setPath("/");
        invalidationCookie.setMaxAge(0);
        return invalidationCookie;
    }

    /**
     * Generate a invalidation Cookie with domain that can be added to the response to prompt the browser to remove that cookie. The domain
     * is needed for IE to change/remove cookies.
     *
     * @param invalidCookie The invalid Cookie from the incoming request
     * @param domain The domain to set in the invalidation cookie
     * @return an invalidation Cookie that can be added to the response to prompt the browser to remove that cookie.
     */
    private Cookie createinvalidationCookie(Cookie invalidCookie, String domain) {
        Cookie invalidationCookieWithDomain = createinvalidationCookie(invalidCookie);
        invalidationCookieWithDomain.setDomain(domain);
        return invalidationCookieWithDomain;
    }

    /**
     * Returns pseudorandom positive long value.
     */
    private long generateRandomLong() {
        return (rnd.nextLong() & 0x7FFFFFFFFFFFFFFFL);
    }

    // ---------------------------------------------------------------------------------------------------------------------

    private ExpirerTask newTaskForPeriodicChecks(Lock lock) {
        return new ExpirerTask(lock);
    }

    private final class ExpirerTask implements Runnable {

        private final Lock tlock;

        ExpirerTask(Lock lock) {
            super();
            this.tlock = lock;
        }

        @Override
        public void run() {
            tlock.lock();
            try {
                cleanUp(System.currentTimeMillis());
            } finally {
                tlock.unlock();
            }
        }
    } // End of class ExpirerTask

}
