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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.openexchange.exception.OXException;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessionExceptionCodes;
import com.openexchange.sessiond.SessionFilter;
import com.openexchange.sessiond.SessionMatcher;
import com.openexchange.sessiond.impl.container.LongTermSessionControl;
import com.openexchange.sessiond.impl.container.SessionControl;
import com.openexchange.sessiond.impl.container.ShortTermSessionControl;
import com.openexchange.sessiond.impl.container.UserRefCounter;
import com.openexchange.sessiond.impl.util.RotatableCopyOnWriteArrayList;
import com.openexchange.sessiond.impl.util.RotateShortResult;
import com.openexchange.sessiond.impl.util.SessionContainer;
import com.openexchange.sessiond.impl.util.SessionMap;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Object handling the multi threaded access to session container. Excessive locking is used to secure container data structures.
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
final class SessionData {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SessionData.class);

    private final int maxSessions;
    private final long randomTokenTimeout;
    private final Map<String, String> randoms;

    /** Plain array+direct indexing is the fastest technique of iterating. So, use CopyOnWriteArrayList since 'sessionList' is seldom modified (see rotateShort()) */
    private final RotatableCopyOnWriteArrayList<SessionContainer> sessionList;

    /**
     * The LongTermUserGuardian contains an entry for a given UserKey if the longTermList contains a session for the user
     * <p>
     * This is used to guard against potentially slow serial searches of the long term sessions
     */
    private final UserRefCounter longTermUserGuardian = new UserRefCounter();

    /** Plain array+direct indexing is the fastest technique of iterating. So, use CopyOnWriteArrayList since 'longTermList' is seldom modified (see rotateLongTerm()) */
    private final RotatableCopyOnWriteArrayList<SessionMap<LongTermSessionControl>> longTermList;

    /**
     * Map to remember if there is already a task that should move the session to the first container.
     */
    private final ConcurrentMap<String, Move2FirstContainerTask> tasks = new ConcurrentHashMap<String, Move2FirstContainerTask>();

    private final AtomicReference<ThreadPoolService> threadPoolService;
    private final AtomicReference<TimerService> timerService;

    protected final Map<String, ScheduledTimerTask> removers = new ConcurrentHashMap<String, ScheduledTimerTask>();

    /**
     * Initializes a new {@link SessionData}.
     *
     * @param containerCount The container count for short-term sessions
     * @param maxSessions The max. number of total sessions
     * @param randomTokenTimeout The timeout for random tokens
     * @param longTermContainerCount The container count for long-term sessions
     */
    SessionData(int containerCount, int maxSessions, long randomTokenTimeout, int longTermContainerCount) {
        super();
        threadPoolService = new AtomicReference<ThreadPoolService>();
        timerService = new AtomicReference<TimerService>();
        this.maxSessions = maxSessions;
        this.randomTokenTimeout = randomTokenTimeout;

        randoms = new ConcurrentHashMap<String, String>(1024, 0.75f, 1);

        SessionContainer[] shortTermInit = new SessionContainer[containerCount];
        for (int i = containerCount; i-- > 0;) {
            shortTermInit[i] = new SessionContainer();
        }
        sessionList = new RotatableCopyOnWriteArrayList<SessionContainer>(shortTermInit);

        List<SessionMap<LongTermSessionControl>> longTermInit = new ArrayList<SessionMap<LongTermSessionControl>>(longTermContainerCount);
        for (int i = longTermContainerCount; i-- > 0;) {
            longTermInit.add(new SessionMap<LongTermSessionControl>(256));
        }
        longTermList = new RotatableCopyOnWriteArrayList<SessionMap<LongTermSessionControl>>(longTermInit);
    }

    void clear() {
        sessionList.clear();
        randoms.clear();
        longTermUserGuardian.clear();
        longTermList.clear();
    }

    /**
     * Rotates the session containers. A new slot is added to head of each queue, while the last one is removed.
     *
     * @return The removed sessions
     */
    RotateShortResult rotateShort() {
        // This is the only location which alters 'sessionList' during runtime
        List<SessionControl> movedToLongTerm = null;
        List<SessionControl> removed = null;
        Collection<ShortTermSessionControl> droppedSessions = sessionList.rotate(new SessionContainer()).getSessionControls();
        if (false == droppedSessions.isEmpty()) {
            List<SessionControl> transientSessions = null;

            try {
                SessionMap<LongTermSessionControl> first = longTermList.get(0);
                for (ShortTermSessionControl control : droppedSessions) {
                    SessionImpl session = control.getSession();
                    if (session.isTransient()) {
                        // A transient session -- do not move to long-term container
                        if (null == transientSessions) {
                            transientSessions = new ArrayList<SessionControl>();
                        }
                        transientSessions.add(control);
                    } else {
                        // A (non-transient) regular session
                        if (session.isStaySignedIn()) {
                            // Has "stay signed in" flag
                            first.putBySessionId(session.getSessionID(), new LongTermSessionControl(control));
                            longTermUserGuardian.incrementCounter(session.getUserId(), session.getContextId());
                            if (movedToLongTerm == null) {
                                movedToLongTerm = new ArrayList<SessionControl>();
                            }
                            movedToLongTerm.add(control);
                        } else {
                            // No "stay signed in" flag; let session time out
                            if (removed == null) {
                                removed = new ArrayList<SessionControl>();
                            }
                            removed.add(control);
                        }
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                // About to shut-down
                LOG.error("First long-term session container does not exist. Likely SessionD is shutting down...", e);
            }

            if (null != transientSessions) {
                SessionHandler.postContainerRemoval(transientSessions, true);
            }
        }

        return new RotateShortResult(movedToLongTerm, removed);
    }

    List<LongTermSessionControl> rotateLongTerm() {
        // This is the only location which alters 'longTermList' during runtime
        List<LongTermSessionControl> removedSessions = new ArrayList<LongTermSessionControl>(longTermList.rotate(new SessionMap<LongTermSessionControl>(256)).values());
        for (LongTermSessionControl sessionControl : removedSessions) {
            longTermUserGuardian.decrementCounter(sessionControl.getUserId(), sessionControl.getContextId());
        }
        return removedSessions;
    }

    /**
     * Checks if given user in specified context has an active session kept in session container(s)
     *
     * @param userId The user identifier
     * @param contextId The user's context identifier
     * @param includeLongTerm <code>true</code> to also lookup the long term sessions, <code>false</code>, otherwise
     * @return <code>true</code> if given user in specified context has an active session; otherwise <code>false</code>
     */
    boolean isUserActive(int userId, int contextId, boolean includeLongTerm) {
        // A read-only access to session list
        for (final SessionContainer container : sessionList) {
            if (container.containsUser(userId, contextId)) {
                return true;
            }
        }

        // No need to check long-term container
        if (!includeLongTerm) {
            return false;
        }

        // Check long-term container, too
        return hasLongTermSession(userId, contextId);
    }

    private final boolean hasLongTermSession(final int userId, final int contextId) {
        return this.longTermUserGuardian.contains(userId, contextId);
    }

    private final boolean hasLongTermSession(final int contextId) {
        return this.longTermUserGuardian.contains(contextId);
    }

    SessionControl[] removeUserSessions(final int userId, final int contextId) {
        // Removing sessions is a write operation.
        final List<SessionControl> retval = new LinkedList<SessionControl>();
        for (final SessionContainer container : sessionList) {
            retval.addAll(container.removeSessionsByUser(userId, contextId));
        }
        for (final SessionControl control : retval) {
            unscheduleTask2MoveSession2FirstContainer(control.getSession().getSessionID(), true);
        }
        if (!hasLongTermSession(userId, contextId)) {
            return retval.toArray(new SessionControl[retval.size()]);
        }
        for (SessionMap<LongTermSessionControl> longTerm : longTermList) {
            for (LongTermSessionControl control : longTerm.values()) {
                if (control.equalsUserAndContext(userId, contextId)) {
                    if (longTerm.removeBySessionId(control.getSessionID()) != null) {
                        longTermUserGuardian.decrementCounter(userId, contextId);
                    }
                    retval.add(control);
                }
            }
        }

        return retval.toArray(new SessionControl[retval.size()]);
    }

    List<SessionControl> removeContextSessions(final int contextId) {
        // Removing sessions is a write operation.
        final List<SessionControl> list = new LinkedList<SessionControl>();
        for (final SessionContainer container : sessionList) {
            list.addAll(container.removeSessionsByContext(contextId));
        }
        for (final SessionControl control : list) {
            unscheduleTask2MoveSession2FirstContainer(control.getSession().getSessionID(), true);
        }

        if (!hasLongTermSession(contextId)) {
            return list;
        }
        for (SessionMap<LongTermSessionControl> longTerm : longTermList) {
            for (LongTermSessionControl control : longTerm.values()) {
                if (control.equalsContext(contextId)) {
                    if (longTerm.removeBySessionId(control.getSessionID()) != null) {
                        longTermUserGuardian.decrementCounter(control.getUserId(), contextId);
                    }
                    list.add(control);
                }
            }
        }
        return list;
    }

    /**
     * Removes all sessions belonging to given contexts from long-term and short-term container.
     *
     * @param contextIds - Set with the context identifiers to remove sessions for
     * @return List of {@link SessionControl} objects for each handled session
     */
    List<SessionControl> removeContextSessions(final Set<Integer> contextIds) {
        // Removing sessions is a write operation.
        final List<SessionControl> list = new ArrayList<SessionControl>();
        for (final SessionContainer container : sessionList) {
            list.addAll(container.removeSessionsByContexts(contextIds));
        }
        for (final SessionControl control : list) {
            unscheduleTask2MoveSession2FirstContainer(control.getSession().getSessionID(), true);
        }
        TIntSet contextIdsToCheck = new TIntHashSet(contextIds.size());
        for (int contextId : contextIds) {
            if (hasLongTermSession(contextId)) {
                contextIdsToCheck.add(contextId);
            }
        }
        for (final SessionMap<LongTermSessionControl> longTerm : longTermList) {
            for (LongTermSessionControl control : longTerm.values()) {
                Session session = control.getSession();
                int contextId = session.getContextId();
                if (contextIdsToCheck.contains(contextId)) {
                    if (longTerm.removeBySessionId(session.getSessionID()) != null) {
                        longTermUserGuardian.decrementCounter(session.getUserId(), contextId);
                    }
                    list.add(control);
                }
            }
        }

        return list;
    }

    boolean hasForContext(final int contextId) {
        for (SessionContainer container : sessionList) {
            if (container.hasForContext(contextId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the first session for given user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param includeLongTerm Whether long-term container should be considered or not
     * @return The first matching session or <code>null</code>
     */
    public SessionControl getAnyActiveSessionForUser(final int userId, final int contextId, final boolean includeLongTerm) {
        for (SessionContainer container : sessionList) {
            ShortTermSessionControl control = container.getAnySessionByUser(userId, contextId);
            if (control != null) {
                return control;
            }
        }

        if (includeLongTerm) {
            if (!hasLongTermSession(userId, contextId)) {
                return null;
            }
            for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
                for (LongTermSessionControl control : longTermMap.values()) {
                    if (control.equalsUserAndContext(userId, contextId)) {
                        return control;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the first session for given user that satisfies given matcher.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param matcher The matcher to satisfy
     * @param ignoreShortTerm Whether short-term container should be considered or not
     * @param ignoreLongTerm Whether long-term container should be considered or not
     * @return The first matching session or <code>null</code>
     */
    public Session findFirstSessionForUser(int userId, int contextId, SessionMatcher matcher, boolean ignoreShortTerm, boolean ignoreLongTerm) {
        if (false == ignoreShortTerm) {
            for (SessionContainer container : sessionList) {
                ShortTermSessionControl control = container.getAnySessionByUser(userId, contextId);
                if ((control != null) && matcher.accepts(control.getSession())) {
                    return control.getSession();
                }
            }
        }

        if (false == ignoreLongTerm) {
            if (!hasLongTermSession(userId, contextId)) {
                return null;
            }
            for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
                for (LongTermSessionControl control : longTermMap.values()) {
                    if (control.equalsUserAndContext(userId, contextId) && matcher.accepts(control.getSession())) {
                        return control.getSession();
                    }
                }
            }
        }

        return null;
    }

    public List<Session> filterSessions(SessionFilter filter) {
        List<Session> sessions = new LinkedList<Session>();
        for (SessionContainer container : sessionList) {
            collectSessions(filter, container.getSessionControls(), sessions);
        }

        for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
            collectSessions(filter, longTermMap.values(), sessions);
        }
        return sessions;
    }

    private static void collectSessions(SessionFilter filter, Collection<? extends SessionControl> sessionControls, List<Session> sessions) {
        for (SessionControl sessionControl : sessionControls) {
            SessionImpl session = sessionControl.getSession();
            if (filter.apply(session)) {
                sessions.add(session);
            }
        }
    }

    /**
     * Gets the <b>local-only</b> active (short-term-only) sessions associated with specified user in given context.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The <b>local-only</b> active sessions or an empty list
     */
    List<ShortTermSessionControl> getUserActiveSessions(int userId, int contextId) {
        // A read-only access to session list
        List<ShortTermSessionControl> retval = null;

        // Short term ones
        for (SessionContainer container : sessionList) {
            List<ShortTermSessionControl> sessionsByUser = container.getSessionsByUser(userId, contextId);
            if (!sessionsByUser.isEmpty()) {
                if (retval == null) {
                    retval = new ArrayList<ShortTermSessionControl>();
                }
                retval.addAll(sessionsByUser);
            }
        }
        return retval == null ? Collections.emptyList() : retval;
    }

    /**
     * Gets the <b>local-only</b> sessions associated with specified user in given context.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The <b>local-only</b> sessions or an empty list
     */
    List<SessionControl> getUserSessions(int userId, int contextId) {
        // A read-only access to session list
        List<SessionControl> retval = new LinkedList<SessionControl>();

        // Short term ones
        for (SessionContainer container : sessionList) {
            retval.addAll(container.getSessionsByUser(userId, contextId));
        }

        // Long term ones
        if (!hasLongTermSession(userId, contextId)) {
            return retval;
        }

        for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
            for (LongTermSessionControl control : longTermMap.values()) {
                if (control.equalsUserAndContext(userId, contextId)) {
                    retval.add(control);
                }
            }
        }
        return retval;
    }

    /**
     * Gets the number of <b>local-only</b> sessions associated with specified user in given context.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param considerLongTerm <code>true</code> to also consider long-term sessions; otherwise <code>false</code>
     * @return The number of sessions
     */
    int getNumOfUserSessions(int userId, int contextId, boolean considerLongTerm) {
        // A read-only access to session list
        int count = 0;
        for (SessionContainer container : sessionList) {
            count += container.numOfUserSessions(userId, contextId);
        }

        if (considerLongTerm) {
            if (!hasLongTermSession(userId, contextId)) {
                return count;
            }
            for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
                for (LongTermSessionControl control : longTermMap.values()) {
                    if (control.equalsUserAndContext(userId, contextId)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Checks validity/uniqueness of specified authentication identifier for given login
     *
     * @param login The login
     * @param authId The authentication identifier
     * @throws OXException If authentication identifier is invalid/non-unique
     */
    void checkAuthId(String login, String authId) throws OXException {
        if (null != authId) {
            for (SessionContainer container : sessionList) {
                for (ShortTermSessionControl sc : container.getSessionControls()) {
                    if (authId.equals(sc.getSession().getAuthId())) {
                        throw SessionExceptionCodes.DUPLICATE_AUTHID.create(sc.getSession().getLogin(), login);
                    }
                }
            }

            for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
                for (LongTermSessionControl control : longTermMap.values()) {
                    if (authId.equals(control.getSession().getAuthId())) {
                        throw SessionExceptionCodes.DUPLICATE_AUTHID.create(control.getSession().getLogin(), login);
                    }
                }
            }
        }
    }

    /**
     * Adds specified session.
     *
     * @param session The session to add
     * @param noLimit <code>true</code> to add without respect to limitation; otherwise <code>false</code> to honor limitation
     * @return The associated {@link SessionControl} instance
     * @throws OXException If add operation fails
     */
    protected SessionControl addSession(final SessionImpl session, final boolean noLimit) throws OXException {
        return addSession(session, noLimit, false);
    }

    /**
     * Adds specified session.
     *
     * @param session The session to add
     * @param noLimit <code>true</code> to add without respect to limitation; otherwise <code>false</code> to honor limitation
     * @param addIfAbsent <code>true</code> to perform an add-if-absent operation; otherwise <code>false</code> to fail on duplicate session
     * @return The associated {@link SessionControl} instance
     * @throws OXException If add operation fails
     */
    protected SessionControl addSession(final SessionImpl session, final boolean noLimit, final boolean addIfAbsent) throws OXException {
        if (!noLimit && countSessions() > maxSessions) {
            throw SessionExceptionCodes.MAX_SESSION_EXCEPTION.create();
        }

        // Add session
        try {
            ShortTermSessionControl control = sessionList.get(0).put(session, addIfAbsent);
            randoms.put(session.getRandomToken(), session.getSessionID());
            scheduleRandomTokenRemover(session.getRandomToken());
            return control;
        } catch (IndexOutOfBoundsException e) {
            // About to shut-down
            throw SessionExceptionCodes.NOT_INITIALIZED.create();
        }
    }

    /**
     * Gets the max. number of total sessions
     *
     * @return the max. number of total sessions
     */
    int getMaxSessions() {
        return this.maxSessions;
    }

    int countSessions() {
        // A read-only access to session list
        int count = 0;
        for (SessionContainer container : sessionList) {
            count += container.size();
        }

        for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
            count += longTermMap.size();
        }
        return count;
    }

    int[] getShortTermSessionsPerContainer() {
        // read-only access to short term sessions.
        TIntList counts = new TIntArrayList(10);
        for (SessionContainer container : sessionList) {
            counts.add(container.size());
        }
        return counts.toArray();
    }

    int[] getLongTermSessionsPerContainer() {
        // read-only access to long term sessions.
        TIntList counts = new TIntArrayList(10);
        for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
            counts.add(longTermMap.size());
        }
        return counts.toArray();
    }

    SessionControl getSessionByAlternativeId(final String altId) {
        SessionControl control = null;

        boolean first = true;
        for (SessionContainer container : sessionList) {
            control = container.getSessionByAlternativeId(altId);
            if (null != control) {
                if (false == first) {
                    // Schedule task to put session into first container and remove from latter one.
                    scheduleTask2MoveSession2FirstContainer(control.getSession().getSessionID(), false);
                }
                return control;
            }
            first = false;
        }

        for (Iterator<SessionMap<LongTermSessionControl>> iterator = longTermList.iterator(); null == control && iterator.hasNext();) {
            control = iterator.next().getByAlternativeId(altId);
            if (null != control) {
                scheduleTask2MoveSession2FirstContainer(control.getSession().getSessionID(), true);
            }
        }
        return control;
    }

    SessionControl getSession(final String sessionId, boolean peek) {
        SessionControl control = null;
        boolean first = true;
        for (SessionContainer container : sessionList) {
            control = container.getSessionById(sessionId);
            if (control != null) {
                if (!peek && false == first) {
                    // Schedule task to put session into first container and remove from latter one.
                    scheduleTask2MoveSession2FirstContainer(sessionId, false);
                }
                return control;
            }
            first = false;
        }

        for (Iterator<SessionMap<LongTermSessionControl>> iterator = longTermList.iterator(); null == control && iterator.hasNext();) {
            control = iterator.next().getBySessionId(sessionId);
            if (!peek && null != control) {
                scheduleTask2MoveSession2FirstContainer(sessionId, true);
            }
        }
        return control;
    }

    ShortTermSessionControl optShortTermSession(final String sessionId) {
        ShortTermSessionControl control = null;
        for (SessionContainer container : sessionList) {
            if ((control = container.getSessionById(sessionId)) != null) {
                return control;
            }
        }

        return control;
    }

    SessionControl getSessionByRandomToken(final String randomToken) {
        // A read-only access to session and a write access to random list
        final String sessionId = randoms.remove(randomToken);
        if (null == sessionId) {
            return null;
        }

        final SessionControl sessionControl = getSession(sessionId, false);
        if (null == sessionControl) {
            LOG.error("Unable to get session for sessionId: {}.", sessionId);
            SessionHandler.clearSession(sessionId, true);
            return null;
        }
        final SessionImpl session = sessionControl.getSession();
        if (!randomToken.equals(session.getRandomToken())) {
            final OXException e = SessionExceptionCodes.WRONG_BY_RANDOM.create(session.getSessionID(), session.getRandomToken(), randomToken, sessionId);
            LOG.error("", e);
            SessionHandler.clearSession(sessionId, true);
            return null;
        }
        session.removeRandomToken();
        if (sessionControl.getCreationTime() + randomTokenTimeout < System.currentTimeMillis()) {
            SessionHandler.clearSession(sessionId, true);
            return null;
        }
        return sessionControl;
    }

    SessionControl clearSession(final String sessionId) {
        // Look-up in short-term list
        for (SessionContainer container : sessionList) {
            ShortTermSessionControl sessionControl = container.removeSessionById(sessionId);
            if (null != sessionControl) {
                Session session = sessionControl.getSession();

                String random = session.getRandomToken();
                if (null != random) {
                    // If session is accessed through random token, random token is removed in the session.
                    randoms.remove(random);
                }

                unscheduleTask2MoveSession2FirstContainer(sessionId, true);
                return sessionControl;
            }
        }

        // Look-up in long-term list
        for (SessionMap<LongTermSessionControl> longTermMap : longTermList) {
            LongTermSessionControl sessionControl = longTermMap.removeBySessionId(sessionId);
            if (null != sessionControl) {
                Session session = sessionControl.getSession();

                String random = session.getRandomToken();
                if (null != random) {
                    // If session is accessed through random token, random token is removed in the session.
                    randoms.remove(random);
                }

                unscheduleTask2MoveSession2FirstContainer(sessionId, true);
                return sessionControl;
            }
        }

        // No such session...
        return null;
    }

    List<ShortTermSessionControl> getShortTermSessions() {
        // A read-only access
        Iterator<SessionContainer> it = sessionList.iterator();
        if (!it.hasNext()) {
            return Collections.emptyList();
        }

        List<ShortTermSessionControl> retval = new ArrayList<ShortTermSessionControl>(it.next().getSessionControls());
        while (it.hasNext()) {
            retval.addAll(it.next().getSessionControls());
        }
        return retval;
    }

    List<String> getShortTermSessionIDs() {
        // A read-only access
        Iterator<SessionContainer> it = sessionList.iterator();
        if (!it.hasNext()) {
            return Collections.emptyList();
        }

        List<String> retval = new ArrayList<String>(it.next().getSessionIDs());
        while (it.hasNext()) {
            retval.addAll(it.next().getSessionIDs());
        }
        return retval;
    }

    List<LongTermSessionControl> getLongTermSessions() {
        // A read-only access
        Iterator<SessionMap<LongTermSessionControl>> it = longTermList.iterator();
        if (!it.hasNext()) {
            return Collections.emptyList();
        }

        List<LongTermSessionControl> retval = new ArrayList<LongTermSessionControl>(it.next().values());
        while (it.hasNext()) {
            retval.addAll(it.next().values());
        }
        return retval;
    }

    void move2FirstContainer(final String sessionId) {
        Iterator<SessionContainer> iterator = sessionList.iterator();
        SessionContainer firstContainer = iterator.next(); // Skip first container

        // Look for associated session in successor containers
        ShortTermSessionControl control = null;
        try {
            while (null == control && iterator.hasNext()) {
                // Remove from current container & put into first one
                control = iterator.next().removeSessionById(sessionId);
                if (null != control) {
                    firstContainer.putSessionControl(control);
                }
            }

            if (null == control) {
                if (firstContainer.containsSessionId(sessionId)) {
                    LOG.warn("Somebody else moved session to most up-to-date container.");
                } else {
                    LOG.debug("Was not able to move the session {} into the most up-to-date container since it has already been removed in the meantime", sessionId);
                }
            }
        } catch (OXException e) {
            LOG.error("", e);
        } catch (IndexOutOfBoundsException e) {
            // About to shut-down
            LOG.error("First session container does not exist. Likely SessionD is shutting down...", e);
        }

        unscheduleTask2MoveSession2FirstContainer(sessionId, false);
        if (null != control) {
            SessionHandler.postSessionTouched(control.getSession());
        }
    }

    void move2FirstContainerLongTerm(final String sessionId) {
        LongTermSessionControl control = null;
        try {
            SessionContainer firstContainer = sessionList.get(0);
            boolean movedSession = false;
            for (Iterator<SessionMap<LongTermSessionControl>> iterator = longTermList.iterator(); !movedSession && iterator.hasNext();) {
                SessionMap<LongTermSessionControl> longTermMap = iterator.next();
                control = longTermMap.removeBySessionId(sessionId);
                if (null != control) {
                    firstContainer.putSessionControl(new ShortTermSessionControl(control));
                    longTermUserGuardian.decrementCounter(control.getUserId(), control.getContextId());
                    LOG.trace("Moved from long term container to first one.");
                    movedSession = true;
                }
            }
            if (!movedSession) {
                if (firstContainer.containsSessionId(sessionId)) {
                    LOG.warn("Somebody else moved session to most actual container.");
                } else {
                    LOG.warn("Was not able to move the session into the most actual container.");
                }
            }
        } catch (OXException e) {
            LOG.error("", e);
        } catch (IndexOutOfBoundsException e) {
            // About to shut-down
            LOG.error("First session container does not exist. Likely SessionD is shutting down...", e);
        }

        unscheduleTask2MoveSession2FirstContainer(sessionId, false);
        if (null != control) {
            SessionHandler.postSessionReactivation(control.getSession());
        }
    }

    void removeRandomToken(final String randomToken) {
        randoms.remove(randomToken);
    }

    public void addThreadPoolService(final ThreadPoolService service) {
        threadPoolService.set(service);
    }

    public void removeThreadPoolService() {
        threadPoolService.set(null);
    }

    private void scheduleTask2MoveSession2FirstContainer(final String sessionId, final boolean longTerm) {
        Move2FirstContainerTask task = tasks.get(sessionId);
        if (null != task) {
            LOG.trace("Found an already existing task to move session to first container.");
            return;
        }
        {
            final Move2FirstContainerTask ntask = new Move2FirstContainerTask(sessionId, longTerm);
            task = tasks.putIfAbsent(sessionId, ntask);
            if (null != task) {
                LOG.trace("Found an already existing task to move session to first container.");
                return;
            }
            task = ntask;
        }
        final ThreadPoolService threadPoolService = this.threadPoolService.get();
        if (null == threadPoolService) {
            final Move2FirstContainerTask tmp = task;
            new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        tmp.call();
                    } catch (Exception e) {
                        LOG.error("Moving session to first container failed.", e);
                    }
                }
            }, "Move2FirstContainer").start();
        } else {
            threadPoolService.submit(task);
        }
    }

    private void unscheduleTask2MoveSession2FirstContainer(String sessionId, boolean deactivateIfPresent) {
        final Move2FirstContainerTask task = tasks.remove(sessionId);
        if (deactivateIfPresent && null != task) {
            task.deactivate();
        }
    }

    private class Move2FirstContainerTask extends AbstractTask<Void> {

        private final String sessionId;
        private final boolean longTerm;
        private volatile boolean deactivated = false;

        Move2FirstContainerTask(final String sessionId, final boolean longTerm) {
            super();
            this.sessionId = sessionId;
            this.longTerm = longTerm;
        }

        public void deactivate() {
            deactivated = true;
        }

        @Override
        public Void call() {
            if (deactivated) {
                return null;
            }
            if (longTerm) {
                move2FirstContainerLongTerm(sessionId);
            } else {
                move2FirstContainer(sessionId);
            }
            return null;
        }
    }

    /**
     * Adds the specified timer service.
     *
     * @param service The timer service
     */
    public void addTimerService(final TimerService service) {
        timerService.set(service);
    }

    /**
     * Removes the timer service
     */
    public void removeTimerService() {
        for (final ScheduledTimerTask timerTask : removers.values()) {
            timerTask.cancel();
        }
        timerService.set(null);
    }

    private void scheduleRandomTokenRemover(final String randomToken) {
        final RandomTokenRemover remover = new RandomTokenRemover(randomToken);
        final TimerService timerService = this.timerService.get();
        if (null == timerService) {
            remover.run();
        } else {
            final ScheduledTimerTask timerTask = timerService.schedule(remover, randomTokenTimeout, TimeUnit.MILLISECONDS);
            removers.put(randomToken, timerTask);
        }
    }

    private class RandomTokenRemover implements Runnable {

        private final String randomToken;

        RandomTokenRemover(final String randomToken) {
            super();
            this.randomToken = randomToken;
        }

        @Override
        public void run() {
            try {
                removers.remove(randomToken);
                removeRandomToken(randomToken);
            } catch (Throwable t) {
                LOG.error("", t);
            }
        }
    }

    /**
     * Gets the number of sessions in the short term container
     *
     * @return The number of sessions in the short term container
     */
    public int getNumShortTerm() {
        int result = 0;
        for (final SessionContainer container : sessionList) {
            result += container.size();
        }
        return result;
    }

    /**
     * Gets the number of sessions in the long term container
     *
     * @return the number of sessions in the long term container
     */
    public int getNumLongTerm() {
        int result = 0;
        for (final SessionMap<LongTermSessionControl> container : longTermList) {
            result += container.size();
        }
        return result;
    }

}
