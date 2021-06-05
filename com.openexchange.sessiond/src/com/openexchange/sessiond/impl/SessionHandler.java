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

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.sessiond.impl.TimeoutTaskWrapper.submit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.openexchange.authentication.SessionEnhancement;
import com.openexchange.config.ConfigurationService;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.session.Origin;
import com.openexchange.session.Session;
import com.openexchange.session.SessionAttributes;
import com.openexchange.session.SessionDescription;
import com.openexchange.session.SessionSerializationInterceptor;
import com.openexchange.session.UserAndContext;
import com.openexchange.sessiond.SessionCounter;
import com.openexchange.sessiond.SessionExceptionCodes;
import com.openexchange.sessiond.SessionFilter;
import com.openexchange.sessiond.SessionMatcher;
import com.openexchange.sessiond.SessiondEventConstants;
import com.openexchange.sessiond.impl.container.LongTermSessionControl;
import com.openexchange.sessiond.impl.container.SessionControl;
import com.openexchange.sessiond.impl.container.ShortTermSessionControl;
import com.openexchange.sessiond.impl.container.TokenSessionContainer;
import com.openexchange.sessiond.impl.container.TokenSessionControl;
import com.openexchange.sessiond.impl.container.SessionControl.ContainerType;
import com.openexchange.sessiond.impl.usertype.UserTypeSessiondConfigInterface;
import com.openexchange.sessiond.impl.usertype.UserTypeSessiondConfigRegistry;
import com.openexchange.sessiond.impl.util.RotateShortResult;
import com.openexchange.sessiond.osgi.Services;
import com.openexchange.sessiond.serialization.PortableContextSessionsCleaner;
import com.openexchange.sessiond.serialization.PortableSessionFilterApplier;
import com.openexchange.sessiond.serialization.PortableSessionFilterApplier.Action;
import com.openexchange.sessiond.serialization.PortableUserSessionsCleaner;
import com.openexchange.sessionstorage.SessionStorageConfiguration;
import com.openexchange.sessionstorage.SessionStorageExceptionCodes;
import com.openexchange.sessionstorage.SessionStorageService;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.Task;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;

/**
 * {@link SessionHandler} - Provides access to sessions
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class SessionHandler {

    public static final SessionCounter SESSION_COUNTER = new SessionCounter() {

        @Override
        public int getNumberOfSessions(int userId, final int contextId) {
            return SESSION_DATA_REF.get().getNumOfUserSessions(userId, contextId, true);
        }
    };

    /** The applied configuration */
    static volatile SessiondConfigInterface config;

    /** The applied user type specific configuration */
    static volatile UserTypeSessiondConfigRegistry userConfigRegistry;

    /** The {@link SessionData} reference */
    protected static final AtomicReference<SessionData> SESSION_DATA_REF = new AtomicReference<SessionData>();

    /** Whether there is no limit when adding a new session */
    private static volatile boolean noLimit;

    /** Whether to put session s to central session storage asynchronously (default) or synchronously */
    private static volatile boolean asyncPutToSessionStorage;

    /** The obfuscator */
    protected static volatile Obfuscator obfuscator;

    /** Logger */
    protected static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SessionHandler.class);

    private static volatile ScheduledTimerTask shortSessionContainerRotator;

    private static volatile ScheduledTimerTask longSessionContainerRotator;

    private static List<SessionSerializationInterceptor> interceptors = Collections.synchronizedList(new LinkedList<SessionSerializationInterceptor>());

    /**
     * Initializes a new {@link SessionHandler session handler}
     */
    private SessionHandler() {
        super();
    }

    /**
     * Initializes the {@link SessionHandler session handler}
     *
     * @param config The appropriate configuration
     */
    public static synchronized void init(SessiondConfigInterface config, UserTypeSessiondConfigRegistry userConfigRegistry) {
        SessionHandler.config = config;
        SessionHandler.userConfigRegistry = userConfigRegistry;
        // @formatter:off
        SessionData sessionData = new SessionData(  config.getNumberOfSessionContainers(),
                                                    config.getMaxSessions(),
                                                    config.getRandomTokenTimeout(),
                                                    config.getNumberOfLongTermSessionContainers()
                                                    );
        // @formatter:on
        SESSION_DATA_REF.set(sessionData);
        noLimit = (config.getMaxSessions() == 0);
        asyncPutToSessionStorage = config.isAsyncPutToSessionStorage();
        obfuscator = new Obfuscator(config.getObfuscationKey().toCharArray());
    }

    /**
     * Shuts-down session handling.
     */
    public static synchronized void close() {
        SessionData sd = SESSION_DATA_REF.get();
        if (null != sd) {
            postContainerRemoval(sd.getShortTermSessions(), false);
            sd.clear();
            SESSION_DATA_REF.set(null);
        } else {
            LOG.warn("\tSessionData instance is null.");
        }
        Obfuscator o = obfuscator;
        if (null != o) {
            obfuscator = null;
            o.destroy();
        }
        userConfigRegistry.clear();
        config = null;
        noLimit = false;
    }

    /**
     * Gets the session obfuscator that performs the conversion into/from a stored session
     *
     * @return The session obfuscator instance
     */
    public static Obfuscator getObfuscator() {
        return obfuscator;
    }

    /**
     * Gets the names of such parameters that are supposed to be taken over from session to stored session representation.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The parameter names
     */
    public static Collection<String> getRemoteParameterNames(int userId, int contextId) {
        SessionStorageConfiguration configuration = SessionStorageConfiguration.getInstance();
        return null == configuration ? Collections.<String> emptyList() : configuration.getRemoteParameterNames(userId, contextId);
    }

    /**
     * Removes all sessions associated with given user in specified context
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The wrapper objects for removed sessions
     */
    public static Session[] removeUserSessions(final int userId, final int contextId) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return new Session[0];
        }
        /*
         * remove from session data
         */
        SessionControl[] controls = sessionData.removeUserSessions(userId, contextId);
        List<SessionControl> removedSessions = new ArrayList<SessionControl>(controls.length);
        Session[] retval = new Session[controls.length];
        for (int i = 0; i < retval.length; i++) {
            SessionImpl removedSession = controls[i].getSession();
            retval[i] = removedSession;
            removedSessions.add(controls[i]);
        }
        if (!removedSessions.isEmpty()) {
            postContainerRemoval(removedSessions, true);
        }
        /*
         * remove local sessions from storage (if available), too
         */
        final SessionStorageService storageService = Services.optService(SessionStorageService.class);
        if (null == storageService) {
            LOG.info("Local removal of user sessions: User={}, Context={}", I(userId), I(contextId));
            return retval;
        }
        Session[] retval2 = null;
        try {
            Task<Session[]> c = new AbstractTask<Session[]>() {

                @Override
                public Session[] call() throws Exception {
                    return storageService.removeLocalUserSessions(userId, contextId);
                }
            };
            retval2 = getFrom(c, new Session[0]);
        } catch (RuntimeException e) {
            LOG.error("", e);
        }
        LOG.info("Remote removal of user sessions: User={}, Context={}", I(userId), I(contextId));
        return merge(retval, retval2);
    }

    /**
     * Globally removes sessions associated to the given contexts. 'Globally' means sessions on all cluster nodes
     *
     * @param contextIds - Set with context ids to be removed
     * @throws OXException
     */
    public static void removeContextSessionsGlobal(final Set<Integer> contextIds) throws OXException {
        SessionHandler.removeRemoteContextSessions(contextIds);
        SessionHandler.removeContextSessions(contextIds);
    }

    /**
     * Globally removes sessions associated to the given contexts. 'Globally' means sessions on all cluster nodes
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     */
    public static void removeUserSessionsGlobal(int userId, int contextId) {
        SessionHandler.removeRemoteUserSessions(userId, contextId);
        SessionHandler.removeUserSessions(userId, contextId);
    }

    /**
     * Triggers removing sessions for given context ids on remote cluster nodes
     *
     * @param contextIds - Set with context ids to be removed
     */
    private static void removeRemoteContextSessions(final Set<Integer> contextIds) throws OXException {
        HazelcastInstance hazelcastInstance = Services.optService(HazelcastInstance.class);

        if (hazelcastInstance != null) {
            LOG.debug("Trying to remove sessions for context ids {} from remote nodes", Strings.concat(", ", contextIds));

            Member localMember = hazelcastInstance.getCluster().getLocalMember();
            Set<Member> clusterMembers = new HashSet<Member>(hazelcastInstance.getCluster().getMembers());
            if (!clusterMembers.remove(localMember)) {
                LOG.warn("Couldn't remove local member from cluster members.");
            }
            if (!clusterMembers.isEmpty()) {
                Map<Member, Future<Set<Integer>>> submitToMembers = hazelcastInstance.getExecutorService("default").submitToMembers(new PortableContextSessionsCleaner(contextIds), clusterMembers);
                for (Entry<Member, Future<Set<Integer>>> memberEntry : submitToMembers.entrySet()) {
                    Future<Set<Integer>> future = memberEntry.getValue();
                    int hzExecutionTimeout = getRemoteContextSessionsExecutionTimeout();

                    Member member = memberEntry.getKey();
                    try {
                        Set<Integer> contextIdsSessionsHaveBeenRemovedFor = null;
                        if (hzExecutionTimeout > 0) {
                            contextIdsSessionsHaveBeenRemovedFor = future.get(hzExecutionTimeout, TimeUnit.SECONDS);
                        } else {
                            contextIdsSessionsHaveBeenRemovedFor = future.get();
                        }
                        if ((contextIdsSessionsHaveBeenRemovedFor != null) && (future.isDone())) {
                            LOG.info("Removed sessions for context ids {} on remote node {}", Strings.concat(", ", contextIdsSessionsHaveBeenRemovedFor), member.getSocketAddress().toString());
                        } else {
                            LOG.warn("No sessions for context ids {} removed on node {}.", Strings.concat(", ", contextIds), member.getSocketAddress().toString());
                        }
                    } catch (TimeoutException e) {
                        // Wait time elapsed; enforce cancelation
                        future.cancel(true);
                        LOG.error("Removing sessions for context ids {} on remote node {} took to longer than {} seconds and was aborted!", Strings.concat(", ", contextIds), member.getSocketAddress().toString(), I(hzExecutionTimeout), e);
                    } catch (InterruptedException e) {
                        future.cancel(true);
                        LOG.error("Removing sessions for context ids {} on remote node {} took to longer than {} seconds and was aborted!", Strings.concat(", ", contextIds), member.getSocketAddress().toString(), I(hzExecutionTimeout), e);
                    } catch (ExecutionException e) {
                        future.cancel(true);
                        LOG.error("Removing sessions for context ids {} on remote node {} took to longer than {} seconds and was aborted!", Strings.concat(", ", contextIds), member.getSocketAddress().toString(), I(hzExecutionTimeout), e.getCause());
                    } catch (Exception e) {
                        LOG.error("Failed to issue remote session removal for contexts {} on remote node {}.", Strings.concat(", ", contextIds), member.getSocketAddress().toString(), e.getCause());
                        throw SessionExceptionCodes.REMOTE_SESSION_REMOVAL_FAILED.create(Strings.concat(", ", contextIds), member.getSocketAddress().toString(), e.getCause());
                    }
                }
            } else {
                LOG.debug("No other cluster members besides the local member. No further clean up necessary.");
            }
        } else {
            LOG.warn("Cannot find HazelcastInstance for remote execution of session removing for context ids {}. Only local sessions will be removed.", Strings.concat(", ", contextIds));
        }
    }

    private static void removeRemoteUserSessions(int userId, int contextId) {
        LOG.debug("Trying to remove sessions for user {} in context {} from remote nodes", I(userId), I(contextId));
        Map<Member, Integer> results = executeGlobalTask(new PortableUserSessionsCleaner(userId, contextId));
        for (Entry<Member, Integer> memberEntry : results.entrySet()) {
            Member member = memberEntry.getKey();
            Integer numOfRemovedSessions = memberEntry.getValue();
            if (numOfRemovedSessions == null) {
                LOG.warn("No sessions removed for user {} in context {} on remote node {}.", I(userId), I(contextId), member.getSocketAddress().toString());
            } else {
                LOG.info("Removed {} sessions for user {} in context {} on remote node {}", numOfRemovedSessions, I(userId), I(contextId), member.getSocketAddress().toString());
            }
        }
    }

    /**
     * Removes all sessions from remote nodes that match the given filter (excluding this node).
     *
     * @param filter The filter
     * @return The session identifiers of all removed sessions
     */
    public static List<String> removeRemoteSessions(SessionFilter filter) {
        LOG.debug("Trying to remove sessions from remote nodes by filter '{}'", filter);
        Map<Member, Collection<String>> results = executeGlobalTask(new PortableSessionFilterApplier(filter, Action.REMOVE));
        List<String> sessionIds = new ArrayList<String>();
        for (Entry<Member, Collection<String>> memberEntry : results.entrySet()) {
            Collection<String> memberSessionIds = memberEntry.getValue();
            if (memberSessionIds != null) {
                LOG.debug("Removed {} sessions on node {} for filter '{}'", I(memberSessionIds.size()), memberEntry.getKey().getSocketAddress().toString(), filter);
                sessionIds.addAll(memberSessionIds);
            }
        }

        return sessionIds;
    }

    /**
     * Finds all sessions on remote nodes that match the given filter (excluding this node).
     *
     * @param filter The filter
     * @return The session identifiers of all found sessions
     */
    public static List<String> findRemoteSessions(SessionFilter filter) {
        LOG.debug("Trying to find sessions on remote nodes by filter '{}'", filter);
        Map<Member, Collection<String>> results = executeGlobalTask(new PortableSessionFilterApplier(filter, Action.GET));
        List<String> sessionIds = new ArrayList<String>();
        for (Entry<Member, Collection<String>> memberEntry : results.entrySet()) {
            Collection<String> memberSessionIds = memberEntry.getValue();
            if (memberSessionIds != null) {
                LOG.debug("Found {} sessions on node {} for filter '{}'", I(memberSessionIds.size()), memberEntry.getKey().getSocketAddress().toString(), filter);
                sessionIds.addAll(memberSessionIds);
            }
        }

        return sessionIds;
    }

    /**
     * Finds all local sessions that match the given filter and returns their identifiers.
     *
     * @param filter The filter
     * @return The found session identifiers
     */
    public static List<String> findLocalSessions(SessionFilter filter) {
        final SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return Collections.emptyList();
        }

        List<Session> sessions = sessionData.filterSessions(filter);
        List<String> sessionIds = new ArrayList<String>(sessions.size());
        for (Session session : sessions) {
            sessionIds.add(session.getSessionID());
        }

        return sessionIds;
    }

    /**
     * Removes all local sessions that match the given filter and returns their identifiers.
     *
     * @param filter The filter
     * @return The session identifiers
     */
    public static List<String> removeLocalSessions(SessionFilter filter) {
        List<String> sessionIds = findLocalSessions(filter);
        if (null == sessionIds || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<SessionControl> removedSessions = new ArrayList<SessionControl>(sessionIds.size());
        for (Iterator<String> iterator = sessionIds.iterator(); iterator.hasNext();) {
            String sessionId = iterator.next();
            SessionControl removedSession = clearSession(sessionId, false);
            if (null == removedSession) {
                iterator.remove();
            } else {
                removedSessions.add(removedSession);
            }
        }
        if (!removedSessions.isEmpty()) {
            postContainerRemoval(removedSessions, true);
        }

        return sessionIds;
    }

    /**
     * Executes a callable on all hazelcast members but this one. The given callable must be a portable or in other ways
     * serializable by hazelcast.
     *
     * @param callable The callable
     * @return A map containing the result for each member. If execution failed or timed out, the entry for a member will be <code>null</code>!
     */
    private static <T> Map<Member, T> executeGlobalTask(Callable<T> callable) {
        HazelcastInstance hazelcastInstance = Services.optService(HazelcastInstance.class);
        if (hazelcastInstance == null) {
            LOG.warn("Cannot find HazelcastInstance for remote execution of callable {}.", callable);
            return Collections.emptyMap();
        }

        Member localMember = hazelcastInstance.getCluster().getLocalMember();
        Set<Member> clusterMembers = new HashSet<Member>(hazelcastInstance.getCluster().getMembers());
        if (!clusterMembers.remove(localMember)) {
            LOG.warn("Couldn't remove local member from cluster members.");
        }

        if (clusterMembers.isEmpty()) {
            LOG.debug("No other cluster members besides the local member. Execution of callable {} not necessary.");
            return Collections.emptyMap();
        }

        int hzExecutionTimeout = getRemoteSessionTaskTimeout();
        Map<Member, Future<T>> submitToMembers = hazelcastInstance.getExecutorService("default").submitToMembers(callable, clusterMembers);
        Map<Member, T> results = new HashMap<Member, T>(submitToMembers.size(), 1.0f);
        for (Entry<Member, Future<T>> memberEntry : submitToMembers.entrySet()) {
            Member member = memberEntry.getKey();
            Future<T> future = memberEntry.getValue();
            T result = null;
            try {
                if (hzExecutionTimeout > 0) {
                    result = future.get(hzExecutionTimeout, TimeUnit.SECONDS);
                } else {
                    result = future.get();
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                LOG.error("Executing callable {} on remote node {} took to longer than {} seconds and was aborted!", callable, member.getSocketAddress().toString(), I(hzExecutionTimeout), e);
            } catch (InterruptedException e) {
                future.cancel(true);
                LOG.error("Executing callable {} on remote node {} took to longer than {} seconds and was aborted!", callable, member.getSocketAddress().toString(), I(hzExecutionTimeout), e);
            } catch (ExecutionException e) {
                future.cancel(true);
                LOG.error("Executing callable {} on remote node {} failed!", callable, member.getSocketAddress().toString(), e.getCause());
            } finally {
                results.put(member, result);
            }
        }

        return results;
    }

    /**
     * Returns the timeout (in seconds) configured to wait for remote invalidation of context sessions. Default value 0 means "no timeout"
     *
     * @return timeout (in seconds) or 0 for no timeout
     */
    private static int getRemoteContextSessionsExecutionTimeout() {
        final ConfigurationService configurationService = Services.optService(ConfigurationService.class);
        if (configurationService == null) {
            LOG.info("ConfigurationService not available. No execution timeout for remote processing of context sessions invalidation available. Fallback to no timeout.");
            return 0;
        }
        return configurationService.getIntProperty("com.openexchange.remote.context.sessions.invalidation.timeout", 0);
    }

    /**
     * Returns the timeout (in seconds) configured to wait for remote execution of session tasks. Default value 0 means "no timeout"
     *
     * @return timeout (in seconds) or 0 for no timeout
     */
    private static int getRemoteSessionTaskTimeout() {
        int defaultValue = 300;
        ConfigurationService configurationService = Services.optService(ConfigurationService.class);
        if (configurationService == null) {
            LOG.info("ConfigurationService not available. No execution timeout for remote processing of session tasks available. Fallback to no timeout.");
            return defaultValue;
        }
        return configurationService.getIntProperty("com.openexchange.remote.session.task.timeout", defaultValue);
    }

    /**
     * Removes all sessions associated with given context.
     *
     * @param contextId The context identifier
     */
    public static void removeContextSessions(final int contextId) {
        /*
         * Check context existence
         */
        {
            ContextService cs = Services.optService(ContextService.class);
            if (null != cs) {
                try {
                    cs.loadContext(contextId);
                } catch (OXException e) {
                    if (2 == e.getCode() && "CTX".equals(e.getPrefix())) { // See com.openexchange.groupware.contexts.impl.ContextExceptionCodes.NOT_FOUND
                        LOG.info("No such context {}", I(contextId));
                        return;
                    }
                }
            }
        }
        /*
         * Continue...
         */
        removeContextSessions(Collections.singleton(I(contextId)));
    }

    /**
     * Removes all sessions associated to the given contexts.
     *
     * @param contextId Set with contextIds to remove session for.
     */
    public static Set<Integer> removeContextSessions(final Set<Integer> contextIds) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return null;
        }
        /*
         * remove from session data
         */
        List<SessionControl> removeContextSessions = sessionData.removeContextSessions(contextIds);
        postContainerRemoval(removeContextSessions, true);

        Set<Integer> processedContexts = new HashSet<Integer>(removeContextSessions.size());
        for (SessionControl control : removeContextSessions) {
            processedContexts.add(I(control.getSession().getContextId()));
        }

        LOG.info("Removed {} sessions for {} contexts", I(removeContextSessions.size()), I(processedContexts.size()));
        return processedContexts;
    }

    /**
     * Checks for any active session for specified context.
     *
     * @param contextId The context identifier
     * @return <code>true</code> if at least one active session is found; otherwise <code>false</code>
     */
    public static boolean hasForContext(final int contextId, boolean considerSessionStorage) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return false;
        }
        boolean hasForContext = sessionData.hasForContext(contextId);
        if (!hasForContext && considerSessionStorage) {
            final SessionStorageService storageService = Services.optService(SessionStorageService.class);
            if (storageService != null) {
                try {
                    Task<Boolean> c = new AbstractTask<Boolean>() {

                        @Override
                        public Boolean call() throws Exception {
                            return Boolean.valueOf(storageService.hasForContext(contextId));
                        }
                    };
                    hasForContext = getFrom(c, Boolean.FALSE).booleanValue();
                } catch (RuntimeException e) {
                    LOG.error("", e);
                }
            }
        }
        return hasForContext;
    }

    /**
     * Gets all <b>local-only</b> active (short-term-only) sessions associated with given user in specified context
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The wrapper objects for active sessions
     */
    public static List<ShortTermSessionControl> getUserActiveSessions(int userId, int contextId) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return new LinkedList<ShortTermSessionControl>();
        }

        return sessionData.getUserActiveSessions(userId, contextId);
    }

    /**
     * Gets all sessions associated with given user in specified context
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param considerSessionStorage <code>true</code> to also consider session storage; otherwise <code>false</code>
     * @return The wrapper objects for sessions
     */
    public static List<SessionControl> getUserSessions(final int userId, final int contextId, boolean considerSessionStorage) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return new LinkedList<SessionControl>();
        }
        List<SessionControl> retval = sessionData.getUserSessions(userId, contextId);
        if (considerSessionStorage) {
            final SessionStorageService storageService = Services.optService(SessionStorageService.class);
            if (storageService != null) {
                try {
                    Task<Session[]> c = new AbstractTask<Session[]>() {

                        @Override
                        public Session[] call() throws Exception {
                            Session[] userSessions = storageService.getUserSessions(userId, contextId);
                            if (null == userSessions) {
                                return new Session[0];
                            }

                            int length = userSessions.length;
                            if (length == 0) {
                                return userSessions;
                            }

                            // Unwrap
                            Session[] retval = new Session[length];
                            for (int i = length; i-- > 0;) {
                                retval[i] = getObfuscator().unwrap(userSessions[i]);
                            }
                            return retval;
                        }
                    };
                    Session[] sessions = getFrom(c, new Session[0]);
                    for (int i = 0; i < sessions.length; i++) {
                        retval.add(sessionToSessionControl(sessions[i]));
                    }
                } catch (RuntimeException e) {
                    LOG.error("", e);
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
    public static int getNumOfUserSessions(int userId, int contextId, boolean considerLongTerm) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return 0;
        }

        return sessionData.getNumOfUserSessions(userId, contextId, considerLongTerm);
    }

    /**
     * Gets an active session of an user if available.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param includeLongTerm <code>true</code> to also lookup the long term sessions, <code>false</code>, otherwise
     * @param includeStorage <code>true</code> to also lookup the distributed session storage, <code>false</code>, otherwise
     * @return
     */
    public static SessionControl getAnyActiveSessionForUser(final int userId, final int contextId, final boolean includeLongTerm, final boolean includeStorage) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return null;
        }
        SessionControl retval = sessionData.getAnyActiveSessionForUser(userId, contextId, includeLongTerm);
        if (retval == null && includeStorage) {
            final SessionStorageService storageService = Services.optService(SessionStorageService.class);
            if (storageService != null) {
                try {
                    Task<Session> c = new AbstractTask<Session>() {

                        @Override
                        public Session call() throws Exception {
                            return storageService.getAnyActiveSessionForUser(userId, contextId);
                        }
                    };
                    SessionImpl unwrappedSession = getObfuscator().unwrap(getFrom(c, null));
                    if (null != unwrappedSession) {
                        retval = new ShortTermSessionControl(unwrappedSession);
                    }
                } catch (RuntimeException e) {
                    LOG.error("", e);
                }
            }
        }
        return retval;
    }

    public static Session findFirstSessionForUser(int userId, int contextId, SessionMatcher matcher, boolean ignoreShortTerm, boolean ignoreLongTerm, boolean ignoreStorage) {
        if (ignoreShortTerm && ignoreLongTerm && ignoreStorage) {
            // Nothing allowed being looked-up
             return null;
        }

        Session retval = null;
        if (!ignoreShortTerm || !ignoreLongTerm) {
            SessionData sessionData = SESSION_DATA_REF.get();
            if (null == sessionData) {
                LOG.warn("\tSessionData instance is null.");
                return null;
            }

            retval = sessionData.findFirstSessionForUser(userId, contextId, matcher, ignoreShortTerm, ignoreLongTerm);
        }

        if (null == retval && !ignoreStorage) {
            final SessionStorageService storageService = Services.optService(SessionStorageService.class);
            if (null != storageService) {
                try {
                    Task<Session> c = new AbstractTask<Session>() {

                        @Override
                        public Session call() throws Exception {
                            return getObfuscator().unwrap(storageService.findFirstSessionForUser(userId, contextId));
                        }
                    };
                    retval = getFrom(c, null);
                } catch (RuntimeException e) {
                    LOG.error("", e);
                }
            }
        }
        return retval;
    }

    /**
     * Stores the session (if available) into session storage.
     *
     * @param sessionId The session identifier
     * @param addIfAbsent Adds the session to storage only if absent
     * @return <code>true</code> if stored; otherwise <code>false</code>
     */
    protected static boolean storeSession(String sessionId, boolean addIfAbsent) {
        if (null == sessionId) {
            return false;
        }

        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return false;
        }
        SessionControl sessionControl = sessionData.getSession(sessionId, false);
        if (null == sessionControl) {
            return false;
        }

        return putIntoSessionStorage(sessionControl.getSession(), addIfAbsent, asyncPutToSessionStorage);
    }

    /**
     * Adds a new session containing given attributes to session container(s)
     *
     * @param userId The user identifier
     * @param loginName The user's login name
     * @param password The user's password
     * @param contextId The context identifier
     * @param clientHost The client host name or IP address
     * @param login The full user's login; e.g. <i>test@foo.bar</i>
     * @param tranzient <code>true</code> if the session should be transient, <code>false</code>, otherwise
     * @param origin The session's origin
     * @param enhancements After creating the session, these call-backs will be called for extending the session.
     * @return The created session
     * @throws OXException If creating a new session fails
     */
    protected static SessionImpl addSession(int userId, String loginName, String password, int contextId, String clientHost, String login, String authId, String hash, String client, String clientToken, boolean tranzient, boolean staySignedIn, Origin origin, List<SessionEnhancement> enhancements, String userAgent) throws OXException {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            throw SessionExceptionCodes.NOT_INITIALIZED.create();
        }

        // Various checks
        checkMaxSessPerUser(userId, contextId, false);
        checkMaxSessPerClient(client, userId, contextId, false);
        checkAuthId(login, authId);

        // Create and optionally enhance new session instance
        SessionImpl newSession;
        {
            // Create session instance
            if (null == enhancements || enhancements.isEmpty()) {
                newSession = createNewSession(userId, loginName, password, contextId, clientHost, login, authId, hash, client, tranzient, staySignedIn, origin);
            } else {
                // Create intermediate SessionDescription instance to offer more flexibility to possible SessionEnhancement implementations
                SessionDescription sessionDescription = createSessionDescription(userId, loginName, password, contextId, clientHost, login, authId, hash, client, tranzient, staySignedIn, origin);
                for (SessionEnhancement enhancement: enhancements) {
                    enhancement.enhanceSession(sessionDescription);
                }
                newSession = new SessionImpl(sessionDescription);
                sessionDescription = null;
            }
        }

        if (Strings.isNotEmpty(userAgent)) {
            newSession.setParameter(Session.PARAM_USER_AGENT, userAgent);
        }

        // Set time stamp
        newSession.setParameter(Session.PARAM_LOGIN_TIME, Long.valueOf(System.currentTimeMillis()));

        // Either add session or yield short-time token for it
        SessionImpl addedSession;
        if (null == clientToken) {
            addedSession = sessionData.addSession(newSession, noLimit).getSession();

            // Store session if not marked as transient and associated client is applicable
            putIntoSessionStorage(addedSession);

            // Post event for created session
            postSessionCreation(addedSession);
        } else {
            String serverToken = SessionIdGenerator.getInstance().createRandomId();
            // TODO change return type and return an interface that allows to dynamically add additional return values.
            newSession.setParameter("serverToken", serverToken);
            TokenSessionContainer.getInstance().addSession(newSession, clientToken, serverToken);
            addedSession = newSession;
        }

        // Return added  session
        return addedSession;
    }

    /**
     * Creates a new instance of {@code SessionImpl} from specified arguments
     *
     * @param userId The user identifier
     * @param loginName The login name
     * @param password The password
     * @param contextId The context identifier
     * @param clientHost The client host name or IP address
     * @param login The login; e.g. <code>"someone@invalid.com"</code>
     * @param authId The authentication identifier
     * @param hash The hash string
     * @param client The client identifier
     * @param tranzient Whether the session is meant to be transient/volatile; typically the session gets dropped soon
     * @param staySignedIn Whether session is supposed to be annotated with "stay signed in"; otherwise <code>false</code>
     * @return The newly created {@code SessionImpl} instance
     * @throws OXException If create attempt fails
     */
    private static SessionImpl createNewSession(int userId, String loginName, String password, int contextId, String clientHost, String login, String authId, String hash, String client, boolean tranzient, boolean staySignedIn, Origin origin) throws OXException {
        // Generate identifier, secret, and random
        SessionIdGenerator sessionIdGenerator = SessionIdGenerator.getInstance();
        String sessionId = sessionIdGenerator.createSessionId(loginName);
        String secret = sessionIdGenerator.createSecretId(loginName);
        String randomToken = sessionIdGenerator.createRandomId();

        // Create the instance
        SessionImpl newSession = new SessionImpl(userId, loginName, password, contextId, sessionId, secret, randomToken, clientHost, login, authId, hash, client, tranzient, staySignedIn, origin);

        // Return...
        return newSession;
    }

    /**
     * Creates a new instance of {@code SessionDescription} from specified arguments
     *
     * @param userId The user identifier
     * @param loginName The login name
     * @param password The password
     * @param contextId The context identifier
     * @param clientHost The client host name or IP address
     * @param login The login; e.g. <code>"someone@invalid.com"</code>
     * @param authId The authentication identifier
     * @param hash The hash string
     * @param client The client identifier
     * @param tranzient Whether the session is meant to be transient/volatile; typically the session gets dropped soon
     * @param staySignedIn Whether session is supposed to be annotated with "stay signed in"; otherwise <code>false</code>
     * @return The newly created {@code SessionDescription} instance
     * @throws OXException If create attempt fails
     */
    private static SessionDescription createSessionDescription(int userId, String loginName, String password, int contextId, String clientHost, String login, String authId, String hash, String client, boolean tranzient, boolean staySignedIn, Origin origin) throws OXException {
        // Generate identifier, secret, and random
        SessionIdGenerator sessionIdGenerator = SessionIdGenerator.getInstance();
        String sessionId = sessionIdGenerator.createSessionId(loginName);
        String secret = sessionIdGenerator.createSecretId(loginName);
        String randomToken = sessionIdGenerator.createRandomId();

        // Create instance
        SessionDescription newSession = new SessionDescription(userId, contextId, login, password, sessionId, secret, UUIDSessionIdGenerator.randomUUID(), origin);
        newSession.setLoginName(loginName);
        newSession.setLocalIp(clientHost);
        newSession.setAuthId(authId);
        newSession.setTransient(tranzient);
        newSession.setStaySignedIn(staySignedIn);
        newSession.setClient(client);
        newSession.setRandomToken(randomToken);
        newSession.setHash(hash);
        return newSession;
    }

    /**
     * Puts the given session into session storage if possible
     *
     * @param session The session
     * @return <code>true</code> if put into session storage; otherwise <code>false</code>
     */
    public static boolean putIntoSessionStorage(SessionImpl session) {
        return putIntoSessionStorage(session, asyncPutToSessionStorage);
    }

    /**
     * Puts the given session into session storage if possible
     *
     * @param session The session
     * @param asyncPutToSessionStorage Whether to perform put asynchronously or not
     * @return <code>true</code> if put into session storage; otherwise <code>false</code>
     */
    public static boolean putIntoSessionStorage(SessionImpl session, boolean asyncPutToSessionStorage) {
        return putIntoSessionStorage(session, false, asyncPutToSessionStorage);
    }

    /**
     * Puts the given session into session storage if possible
     *
     * @param session The session
     * @param addIfAbsent <code>true</code> to perform add-if-absent store operation; otherwise <code>false</code> to perform a possibly replacing put
     * @param asyncPutToSessionStorage Whether to perform put asynchronously or not
     * @return <code>true</code> if put into session storage; otherwise <code>false</code>
     */
    public static boolean putIntoSessionStorage(SessionImpl session, boolean addIfAbsent, boolean asyncPutToSessionStorage) {
        if (useSessionStorage(session)) {
            SessionStorageService sessionStorageService = Services.optService(SessionStorageService.class);
            if (sessionStorageService != null) {
                for (SessionSerializationInterceptor interceptor : interceptors) {
                    interceptor.serialize(session);
                }
                if (asyncPutToSessionStorage) {
                    // Enforced asynchronous put
                    storeSessionAsync(session, sessionStorageService, addIfAbsent);
                } else {
                    storeSessionSync(session, sessionStorageService, addIfAbsent);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * (Synchronously) Stores specified session.
     *
     * @param session The session to store
     * @param sessionStorageService The storage service
     * @param addIfAbsent <code>true</code> to perform add-if-absent store operation; otherwise <code>false</code> to perform a possibly
     *            replacing put
     */
    public static void storeSessionSync(SessionImpl session, final SessionStorageService sessionStorageService, final boolean addIfAbsent) {
        storeSession(session, sessionStorageService, addIfAbsent, false);
    }

    /**
     * (Asynchronously) Stores specified session.
     *
     * @param session The session to store
     * @param sessionStorageService The storage service
     * @param addIfAbsent <code>true</code> to perform add-if-absent store operation; otherwise <code>false</code> to perform a possibly replacing put
     */
    public static void storeSessionAsync(SessionImpl session, final SessionStorageService sessionStorageService, final boolean addIfAbsent) {
        storeSession(session, sessionStorageService, addIfAbsent, true);
    }

    /**
     * Stores specified session.
     *
     * @param session The session to store
     * @param sessionStorageService The storage service
     * @param addIfAbsent <code>true</code> to perform add-if-absent store operation; otherwise <code>false</code> to perform a possibly
     *            replacing put
     * @param async Whether to perform task asynchronously or not
     */
    public static void storeSession(SessionImpl session, final SessionStorageService sessionStorageService, final boolean addIfAbsent, final boolean async) {
        if (null == session || null == sessionStorageService) {
            return;
        }
        if (async) {
            ThreadPools.getThreadPool().submit(new StoreSessionTask(session, sessionStorageService, addIfAbsent));
        } else {
            StoreSessionTask task = new StoreSessionTask(session, sessionStorageService, addIfAbsent);
            Thread thread = Thread.currentThread();
            boolean ran = false;
            task.beforeExecute(thread);
            try {
                task.call();
                ran = true;
                task.afterExecute(null);
            } catch (Exception ex) {
                if (!ran) {
                    task.afterExecute(ex);
                }
                // Else the exception occurred within
                // afterExecute itself in which case we don't
                // want to call it again.
                OXException oxe = (ex instanceof OXException ? (OXException) ex : SessionExceptionCodes.SESSIOND_EXCEPTION.create(ex, ex.getMessage()));
                LOG.warn("", oxe);
            }
        }
    }

    /**
     * Stores specified session.
     *
     * @param session The session to store
     * @param sessionStorageService The storage service
     */
    public static void storeSessions(Collection<SessionImpl> sessions, final SessionStorageService sessionStorageService) {
        if (null == sessions || sessions.isEmpty() || null == sessionStorageService) {
            return;
        }
        if (asyncPutToSessionStorage) {
            for (SessionImpl session : sessions) {
                storeSessionAsync(session, sessionStorageService, true);
            }
        } else {
            for (SessionImpl session : sessions) {
                storeSessionSync(session, sessionStorageService, true);
            }
        }
    }

    private static void checkMaxSessPerUser(final int userId, final int contextId, boolean considerSessionStorage) throws OXException {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return;
        }

        UserTypeSessiondConfigInterface userTypeConfig = userConfigRegistry.getConfigFor(userId, contextId);
        int maxSessPerUser = userTypeConfig.getMaxSessionsPerUserType();
        if (maxSessPerUser > 0) {
            int count = sessionData.getNumOfUserSessions(userId, contextId, true);
            if (count >= maxSessPerUser) {
                throw SessionExceptionCodes.MAX_SESSION_PER_USER_EXCEPTION.create(I(userId), I(contextId));
            }
            if (considerSessionStorage) {
                final SessionStorageService storageService = Services.optService(SessionStorageService.class);
                if (storageService != null) {
                    try {
                        Task<Integer> c = new AbstractTask<Integer>() {

                            @Override
                            public Integer call() throws Exception {
                                return I(storageService.getUserSessionCount(userId, contextId));
                            }
                        };
                        count = getFrom(c, I(0)).intValue();
                        if (count >= maxSessPerUser) {
                            throw SessionExceptionCodes.MAX_SESSION_PER_USER_EXCEPTION.create(I(userId), I(contextId));
                        }
                    } catch (OXException e) {
                        LOG.error("", e);
                    }
                }
            }
        }
    }

    private static void checkMaxSessPerClient(String client, final int userId, final int contextId, boolean considerSessionStorage) throws OXException {
        if (null == client) {
            // Nothing to check against
            return;
        }
        int maxSessPerClient = config.getMaxSessionsPerClient();
        if (maxSessPerClient > 0) {
            SessionData sessionData = SESSION_DATA_REF.get();
            List<SessionControl> userSessions = null == sessionData ? new LinkedList<SessionControl>() : sessionData.getUserSessions(userId, contextId);
            int cnt = 1; // We have at least one
            for (SessionControl sessionControl : userSessions) {
                if (client.equals(sessionControl.getSession().getClient()) && ++cnt > maxSessPerClient) {
                    throw SessionExceptionCodes.MAX_SESSION_PER_CLIENT_EXCEPTION.create(client, I(userId), I(contextId));
                }
            }
            if (considerSessionStorage) {
                final SessionStorageService storageService = Services.optService(SessionStorageService.class);
                if (storageService != null) {
                    if (maxSessPerClient > 0) {
                        try {
                            Task<Session[]> c = new AbstractTask<Session[]>() {

                                @Override
                                public Session[] call() throws Exception {
                                    return storageService.getUserSessions(userId, contextId);
                                }
                            };
                            Session[] storedSessions = getFrom(c, new Session[0]);
                            cnt = 0;
                            for (Session session : storedSessions) {
                                if (client.equals(session.getClient()) && ++cnt > maxSessPerClient) {
                                    throw SessionExceptionCodes.MAX_SESSION_PER_CLIENT_EXCEPTION.create(client, I(userId), I(contextId));
                                }
                            }
                        } catch (OXException e) {
                            LOG.error("", e);
                        }
                    }
                }
            }
        }
    }

    private static void checkAuthId(String login, final String authId) throws OXException {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return;
        }
        sessionData.checkAuthId(login, authId);
    }

    /**
     * Clears the session denoted by given session identifier from session container(s)
     *
     * @param sessionid The session identifier
     * @param postEvent <code>true</code> to post an event about session removal; otherwise <code>false</code> for no such event
     * @return <code>true</code> if a session could be removed; otherwise <code>false</code>
     */
    protected static SessionControl clearSession(String sessionid, boolean postEvent) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return null;
        }
        SessionControl sessionControl = sessionData.clearSession(sessionid);
        if (null == sessionControl) {
            LOG.debug("Cannot find session for given identifier to remove session <{}>", sessionid);
            return null;
        }
        if (postEvent) {
            postSessionRemoval(sessionControl);
        }
        return sessionControl;
    }

    /**
     * Changes the password stored in session denoted by given session identifier
     *
     * @param sessionid The session identifier
     * @param newPassword The new password
     * @throws OXException If changing the password fails
     */
    protected static void changeSessionPassword(final String sessionid, final String newPassword) throws OXException {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return;
        }
        LOG.debug("changeSessionPassword <{}>", sessionid);
        SessionControl sessionControl = sessionData.getSession(sessionid, true);
        if (null == sessionControl) {
            throw SessionExceptionCodes.PASSWORD_UPDATE_FAILED.create();
        }
        /*
         * Change password in current session
         */
        final SessionImpl currentSession = sessionControl.getSession();
        int userId = currentSession.getUserId();
        int contextId = currentSession.getContextId();
        currentSession.setPassword(newPassword);
        final SessionStorageService sessionStorage = Services.optService(SessionStorageService.class);
        if (null != sessionStorage && useSessionStorage(currentSession)) {
            Task<Void> c = new AbstractTask<Void>() {

                @Override
                public Void call() throws Exception {
                    Session wrappedSession = getObfuscator().wrap(currentSession);
                    sessionStorage.changePassword(sessionid, wrappedSession.getPassword());
                    return null;
                }
            };
            submitAndIgnoreRejection(c);
        }
        /*
         * Invalidate all other user sessions known by local session containers
         */
        List<SessionControl> userSessionControls = sessionData.getUserSessions(currentSession.getUserId(), currentSession.getContextId());
        if (null != userSessionControls) {
            List<SessionControl> removedSessions = new ArrayList<SessionControl>(userSessionControls.size());
            for (SessionControl userSessionControl : userSessionControls) {
                String otherSessionID = userSessionControl.getSession().getSessionID();
                if (null != otherSessionID && false == otherSessionID.equals(sessionid)) {
                    SessionControl removedSession = clearSession(otherSessionID, false);
                    if (null != removedSession) {
                        removedSessions.add(removedSession);
                    }
                }
            }
            if (!removedSessions.isEmpty()) {
                postContainerRemoval(userSessionControls, true);
            }
        }
        /*
         * Invalidate all further user sessions known on remote cluster members
         */
        removeRemoteUserSessions(userId, contextId);
    }

    protected static void setSessionAttributes(SessionImpl session, SessionAttributes attrs) throws OXException {
        if (null == session) {
            return;
        }

        try {
            boolean anySet = false;
            if (attrs.getLocalIp().isSet()) {
                session.setLocalIp(attrs.getLocalIp().get(), false);
                anySet = true;
            }
            if (attrs.getClient().isSet()) {
                session.setClient(attrs.getClient().get(), false);
                anySet = true;
            }
            if (attrs.getHash().isSet()) {
                session.setHash(attrs.getHash().get(), false);
                anySet = true;
            }
            if (attrs.getUserAgent().isSet()) {
                session.setParameter(Session.PARAM_USER_AGENT, attrs.getUserAgent().get());
                anySet = true;
            }

            if (anySet && useSessionStorage(session)) {
                SessionStorageService sessionStorageService = Services.optService(SessionStorageService.class);
                if (sessionStorageService != null) {
                    AbstractTask<Void> c = new AbstractTask<Void>() {

                        @Override
                        public Void call() throws Exception {
                            try {
                                sessionStorageService.setSessionAttributes(session.getSessionID(), attrs);
                            } catch (OXException e) {
                                if (SessionStorageExceptionCodes.NO_SESSION_FOUND.equals(e)) {
                                    // No such session held in session storage
                                    LOG.debug("Session {} not available in session storage.", session.getSessionID(), e);
                                } else {
                                    LOG.warn("Failed to set session attributes", e);
                                }
                            } catch (Exception e) {
                                if (e.getCause() instanceof InterruptedException) {
                                    // Timed out
                                    LOG.warn("Failed to set session attributes in time");
                                } else {
                                    LOG.warn("Failed to set session attributes", e);
                                }
                            }
                            return null;
                        }
                    };
                    submit(c);
                }
            }
        } catch (RuntimeException e) {
            throw SessionExceptionCodes.SESSIOND_EXCEPTION.create(e, e.getMessage());
        }
    }

    protected static Session getSessionByRandomToken(final String randomToken, final String newIP) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return null;
        }
        SessionControl sessionControl = sessionData.getSessionByRandomToken(randomToken);
        if (null == sessionControl) {
            final SessionStorageService storageService = Services.optService(SessionStorageService.class);
            if (storageService != null) {
                try {
                    Task<Session> c = new AbstractTask<Session>() {

                        @Override
                        public Session call() throws Exception {
                            return storageService.getSessionByRandomToken(randomToken, newIP);
                        }
                    };
                    Session unwrappedSession = getObfuscator().unwrap(getFrom(c, null));
                    if (null != unwrappedSession) {
                        return unwrappedSession;
                    }
                } catch (RuntimeException e) {
                    LOG.error("", e);
                }
            }
            return null;
        }
        /*
         * Check if local IP should be replaced
         */
        if (null != newIP) {
            /*
             * Set local IP
             */
            Session session = sessionControl.getSession();
            String oldIP = session.getLocalIp();
            if (!newIP.equals(oldIP)) {
                LOG.info("Changing IP of session {} with authID: {} from {} to {}.", session.getSessionID(), session.getAuthId(), oldIP, newIP);
                applyNewIP(newIP, session);
            }
        }
        return sessionControl.getSession();
    }

    @SuppressWarnings("deprecation")
    private static void applyNewIP(final String newIP, Session session) {
        session.setLocalIp(newIP);
    }

    static Session getSessionWithTokens(String clientToken, final String serverToken) throws OXException {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            throw SessionExceptionCodes.NOT_INITIALIZED.create();
        }
        // find session matching to tokens
        TokenSessionControl tokenControl = TokenSessionContainer.getInstance().getSession(clientToken, serverToken);
        SessionImpl activatedSession = tokenControl.getSession();

        // Put this session into the normal session container
        SessionControl sessionControl = sessionData.addSession(activatedSession, noLimit);
        SessionImpl addedSession = sessionControl.getSession();
        putIntoSessionStorage(addedSession);
        // Post event for created session
        postSessionCreation(addedSession);

        return activatedSession;
    }

    /**
     * Gets the session associated with given session identifier
     *
     * @param sessionId The session identifier
     * @param considerSessionStorage <code>true</code> to consider session storage for possible distributed session; otherwise
     *            <code>false</code>
     * @return The session associated with given session identifier; otherwise <code>null</code> if expired or none found
     */
    protected static SessionControl getSession(String sessionId, final boolean considerSessionStorage) {
        return getSession(sessionId, true, considerSessionStorage, false);
    }

    /**
     * Gets the session associated with given session identifier
     *
     * @param sessionId The session identifier
     * @param considerLocalStorage <code>true</code> to consider local storage; otherwise <code>false</code>
     * @param considerSessionStorage <code>true</code> to consider session storage for possible distributed session; otherwise
     *            <code>false</code>
     * @param peek <code>true</code> to only peek session from session storage but don't add it to local SessionD; otherwise <code>false</code>
     * @return The session associated with given session identifier; otherwise <code>null</code> if expired or none found
     */
    protected static SessionControl getSession(String sessionId, boolean considerLocalStorage, boolean considerSessionStorage, boolean peek) {
        LOG.debug("getSession <{}>", sessionId);

        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return null;
        }

        if (false == considerLocalStorage) {
            if (false == considerSessionStorage) {
                return null;
            }

            return optSessionFromSessionStorage(sessionId, peek, sessionData);
        }

        SessionControl sessionControl = sessionData.getSession(sessionId, peek);
        if (considerSessionStorage && null == sessionControl) {
            sessionControl = optSessionFromSessionStorage(sessionId, peek, sessionData);
        }
        return sessionControl;
    }

    private static SessionControl optSessionFromSessionStorage(String sessionId, boolean peek, SessionData sessionData) {
        SessionStorageService storageService = Services.optService(SessionStorageService.class);
        if (storageService == null) {
            // No session storage available
            return null;
        }

        try {
            SessionImpl unwrappedSession = getSessionFrom(sessionId, timeout(), storageService);
            if (null == unwrappedSession) {
                return null;
            }

            if (peek) {
                return new ShortTermSessionControl(unwrappedSession);
            }

            SessionControl sc = sessionData.addSession(unwrappedSession, noLimit, true);
            if (unwrappedSession == sc.getSession()) {
                // This thread restored the session first
                LOG.info("Restored session {} from session storage for user {} in context {}: staySignedIn={}, transient={}", sessionId, I(sc.getUserId()), I(sc.getContextId()), B(unwrappedSession.isStaySignedIn()), B(unwrappedSession.isTransient()));
                for (SessionSerializationInterceptor interceptor : interceptors) {
                    interceptor.deserialize(unwrappedSession);
                }
            }

            // Post event for restored session
            postSessionRestauration(sc.getSession());
            return sc;

        } catch (OXException e) {
            if (!SessionStorageExceptionCodes.NO_SESSION_FOUND.equals(e)) {
                LOG.warn("Session look-up for {} failed in session storage.", sessionId, e);
            }
        }

        return null;
    }

    /**
     * Checks if denoted session is <code>locally</code> available and located in short-term container.
     *
     * @param sessionId The session identifier
     * @return <code>true</code> if <code>locally</code> active; otherwise <code>false</code>
     */
    protected static boolean isActive(String sessionId) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return false;
        }
        return null != sessionData.optShortTermSession(sessionId);
    }

    protected static List<String> getActiveSessionIDs() {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return Collections.emptyList();
        }
        return sessionData.getShortTermSessionIDs();
    }

    /**
     * Gets the session associated with given alternative identifier
     *
     * @param alternative identifier The alternative identifier
     * @return The session associated with given alternative identifier; otherwise <code>null</code> if expired or none found
     */
    protected static SessionControl getSessionByAlternativeId(final String altId, boolean lookupSessionStorage) {
        LOG.debug("getSessionByAlternativeId <{}>", altId);
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return null;
        }
        SessionControl sessionControl = sessionData.getSessionByAlternativeId(altId);
        if (null == sessionControl && lookupSessionStorage) {
            final SessionStorageService storageService = Services.optService(SessionStorageService.class);
            if (storageService != null) {
                try {
                    Task<Session> c = new AbstractTask<Session>() {

                        @Override
                        public Session call() throws Exception {
                            return storageService.getSessionByAlternativeId(altId);
                        }
                    };
                    SessionImpl unwrappedSession = getObfuscator().unwrap(getFrom(c, null));
                    if (null != unwrappedSession) {
                        return new ShortTermSessionControl(unwrappedSession);
                    }
                } catch (RuntimeException e) {
                    LOG.error("", e);
                }
            }
        }
        return sessionControl;
    }

    /**
     * Gets (and removes) the session bound to given session identifier in cache.
     * <p>
     * Session is going to be added to local session containers on a cache hit.
     *
     * @param sessionId The session identifier
     * @return A wrapping instance of {@link SessionControl} or <code>null</code>
     */
    public static SessionControl getCachedSession(final String sessionId) {
        LOG.debug("getCachedSession <{}>", sessionId);
        final SessionStorageService storageService = Services.optService(SessionStorageService.class);
        if (storageService != null) {
            try {
                Task<Session> c = new AbstractTask<Session>() {

                    @Override
                    public Session call() throws Exception {
                        return storageService.getCachedSession(sessionId);
                    }
                };
                SessionImpl unwrappedSession = getObfuscator().unwrap(getFrom(c, null));
                if (null != unwrappedSession) {
                    return new ShortTermSessionControl(unwrappedSession);
                }
            } catch (RuntimeException e) {
                LOG.error("", e);
            }
        }
        return null;
    }

    /**
     * Gets all available instances of {@link SessionControl}
     *
     * @return All available instances of {@link SessionControl}
     */
    public static List<ShortTermSessionControl> getSessions() {
        LOG.debug("getSessions");
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return Collections.emptyList();
        }
        List<ShortTermSessionControl> retval = sessionData.getShortTermSessions();
        if (retval == null) {
            final SessionStorageService storageService = Services.optService(SessionStorageService.class);
            if (storageService != null) {
                Task<List<Session>> c = new AbstractTask<List<Session>>() {

                    @Override
                    public List<Session> call() throws Exception {
                        return storageService.getSessions();
                    }
                };
                List<Session> list = getFrom(c, Collections.<Session> emptyList());
                if (null != list && !list.isEmpty()) {
                    List<ShortTermSessionControl> result = new ArrayList<ShortTermSessionControl>();
                    Obfuscator obfuscator = getObfuscator();
                    for (Session s : list) {
                        SessionImpl unwrappedSession = obfuscator.unwrap(s);
                        if (null != unwrappedSession) {
                            result.add(new ShortTermSessionControl(unwrappedSession));
                        }
                    }
                    return result;
                }
            }
        }
        return retval;
    }

    protected static void cleanUp() {
        LOG.debug("session cleanup");
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return;
        }
        RotateShortResult result = sessionData.rotateShort();
        {
            List<SessionControl> movedToLongTerm = result.getMovedToLongTerm();
            if (!movedToLongTerm.isEmpty()) {
                for (SessionControl sessionControl : movedToLongTerm) {
                    LOG.info("Session is moved to long life time container. All temporary session data will be cleaned up. ID: {}", sessionControl.getSession().getSessionID());
                }
                postSessionDataRemoval(movedToLongTerm);
            }
        }
        {
            List<SessionControl> removed = result.getRemoved();
            if (!removed.isEmpty()) {
                for (SessionControl sessionControl : removed) {
                    LOG.info("Session timed out. ID: {}", sessionControl.getSession().getSessionID());
                }
                postContainerRemoval(removed, config.isRemoveFromSessionStorageOnTimeout());
            }
        }
    }

    protected static void cleanUpLongTerm() {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null == sessionData) {
            LOG.warn("\tSessionData instance is null.");
            return;
        }
        List<LongTermSessionControl> controls = sessionData.rotateLongTerm();
        if (!controls.isEmpty()) {
            for (SessionControl control : controls) {
                LOG.info("Session timed out. ID: {}", control.getSession().getSessionID());
            }
            postContainerRemoval(controls, config.isRemoveFromSessionStorageOnTimeout());
        }
    }

    public static int getNumberOfActiveSessions() {
        SessionData sessionData = SESSION_DATA_REF.get();
        return null == sessionData ? 0 : sessionData.countSessions();
    }

    /**
     * Gets the maximum number of sessions
     *
     * @return The maximum number of sessions
     */
    public static int getMaxNumberOfSessions() {
        SessionData sessionData = SESSION_DATA_REF.get();
        return null == sessionData ? 0 : sessionData.getMaxSessions();
    }

    /**
     * Gets the total number of sessions
     *
     * @return The total number of sessions
     */
    public static int getMetricTotalSessions() {
        return getNumberOfActiveSessions();
    }

    /**
     * Gets the maximum number of sessions allowed
     *
     * @return The max. number of sessions
     */
    public static int getMetricMaxSession() {
        return getMaxNumberOfSessions();
    }

    /**
     * Gets the number of active sessions (Sessions within the first two short-term containers).
     *
     * @return The number of active sessions
     */
    public static int getMetricActiveSessions() {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (sessionData == null) {
            return 0;
        }
        int[] shortTermSessionsPerContainer = sessionData.getShortTermSessionsPerContainer();
        return shortTermSessionsPerContainer.length < 2 ? 0 : shortTermSessionsPerContainer[0] + shortTermSessionsPerContainer[1];
    }

    /**
     * Gets the number of sessions in the short-term container
     *
     * @return The number of sessions in the short-term container
     */
    public static int getMetricShortSessions() {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (sessionData == null) {
            return 0;
        }
        return sessionData.getNumShortTerm();
    }

    /**
     * Gets the number of sessions in the long-term container
     *
     * @return the number of sessions in the long-term container
     */
    public static int getMetricLongSessions() {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (sessionData == null) {
            return 0;
        }
        return sessionData.getNumLongTerm();
    }

    public static int[] getNumberOfLongTermSessions() {
        SessionData sessionData = SESSION_DATA_REF.get();
        return null == sessionData ? new int[0] : sessionData.getLongTermSessionsPerContainer();
    }

    public static int[] getNumberOfShortTermSessions() {
        SessionData sessionData = SESSION_DATA_REF.get();
        return null == sessionData ? new int[0] : sessionData.getShortTermSessionsPerContainer();
    }

    /**
     * Post event that a single session has been put into {@link SessionStorageService session storage}.
     *
     * @param session The stored session
     */
    protected static void postSessionStored(Session session) {
        postSessionStored(session, null);
    }

    /**
     * Post event that a single session has been put into {@link SessionStorageService session storage}.
     *
     * @param session The stored session
     * @param optEventAdmin The optional {@link EventAdmin} instance
     */
    public static void postSessionStored(Session session, final EventAdmin optEventAdmin) {
        EventAdmin eventAdmin = optEventAdmin == null ? Services.optService(EventAdmin.class) : optEventAdmin;
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_SESSION, session);
        dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_STORED_SESSION, dic));
        LOG.debug("Posted event for added session");
    }

    private static void postSessionCreation(Session session) {
        EventAdmin eventAdmin = Services.optService(EventAdmin.class);
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_SESSION, session);
        dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_ADD_SESSION, dic));
        LOG.debug("Posted event for added session");
    }

    private static void postSessionRestauration(Session session) {
        EventAdmin eventAdmin = Services.optService(EventAdmin.class);
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_SESSION, session);
        dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_RESTORED_SESSION, dic));
        LOG.debug("Posted event for restored session");
    }

    private static void postSessionRemoval(final SessionControl sessionControl) {
        Session session = sessionControl.getSession();
        Future<Void> dropSessionFromHz = null;
        if (useSessionStorage(session)) {
            // Asynchronous remove from session storage
            final SessionStorageService sessionStorageService = Services.optService(SessionStorageService.class);
            if (sessionStorageService != null) {
                dropSessionFromHz = ThreadPools.getThreadPool().submit(new AbstractTask<Void>() {

                    @Override
                    public Void call() {
                        try {
                            sessionStorageService.removeSession(session.getSessionID());
                        } catch (OXException e) {
                            LOG.warn("Session could not be removed from session storage: {}", session.getSessionID(), e);
                        } catch (RuntimeException e) {
                            LOG.warn("Session could not be removed from session storage: {}", session.getSessionID(), e);
                        }
                        return null;
                    }
                });
            }
        }

        // Asynchronous post of event
        EventAdmin eventAdmin = Services.optService(EventAdmin.class);
        if (eventAdmin != null) {
            Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
            dic.put(SessiondEventConstants.PROP_SESSION, session);
            dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
            eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_REMOVE_SESSION, dic));
            LOG.debug("Posted event for removed session");

            if (sessionControl.geContainerType() == ContainerType.SHORT_TERM) {
                SessionData sessionData = SESSION_DATA_REF.get();
                if (null != sessionData) {
                    int contextId = session.getContextId();
                    int userId = session.getUserId();
                    if (false == sessionData.isUserActive(userId, contextId, false)) {
                        postLastSessionGone(userId, contextId, eventAdmin);
                    }
                }
            }
        }

        // Await session removal from session storage
        if (null != dropSessionFromHz) {
            int tout = 2;
            try {
                dropSessionFromHz.get(tout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while waiting for session removal from session storage", e);
            } catch (ExecutionException e) {
                // Cannot occur
                LOG.warn("Session could not be removed from session storage: {}", session.getSessionID(), e.getCause());
            } catch (TimeoutException e) {
                // Ignore...
                LOG.debug("Session could not be removed from session storage within {} seconds: {}", I(tout), session.getSessionID(), e);
            }
        }
    }

    private static void postLastSessionGone(int userId, int contextId, EventAdmin eventAdmin) {
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_USER_ID, I(userId));
        dic.put(SessiondEventConstants.PROP_CONTEXT_ID, I(contextId));
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_LAST_SESSION, dic));
        LOG.debug("Posted event for last removed session for user {} in context {}", I(userId), I(contextId));

        SessionData sessionData = SESSION_DATA_REF.get();
        if (null != sessionData) {
            if (false == sessionData.hasForContext(contextId)) {
                postContextLastSessionGone(contextId, eventAdmin);
            }
        }
    }

    private static void postContextLastSessionGone(int contextId, EventAdmin eventAdmin) {
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_CONTEXT_ID, I(contextId));
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_LAST_SESSION_CONTEXT, dic));
        LOG.debug("Posted event for last removed session for context {}", I(contextId));
    }

    protected static void postContainerRemoval(List<? extends SessionControl> sessionControls, boolean removeFromSessionStorage) {
        if (sessionControls == null || sessionControls.isEmpty()) {
            return;
        }

        if (removeFromSessionStorage) {
            // Asynchronously remove from session storage
            SessionStorageService sessionStorageService = Services.optService(SessionStorageService.class);
            if (sessionStorageService != null) {
                ThreadPools.getThreadPool().submit(new AbstractTask<Void>() {

                    @Override
                    public Void call() {
                        try {
                            List<String> sessionsToRemove = null;
                            for (SessionControl sessionControl : sessionControls) {
                                SessionImpl session = sessionControl.getSession();
                                if (useSessionStorage(session)) {
                                    if (sessionsToRemove == null) {
                                        sessionsToRemove = new ArrayList<String>();
                                    }
                                    sessionsToRemove.add(session.getSessionID());
                                }
                            }
                            if (sessionsToRemove != null) {
                                sessionStorageService.removeSessions(sessionsToRemove);
                            }
                        } catch (RuntimeException e) {
                            LOG.error("", e);
                        } catch (OXException e) {
                            LOG.error("", e);
                        }
                        return null;
                    }
                });
            }
        }

        // Asynchronous post of event
        EventAdmin eventAdmin = Services.optService(EventAdmin.class);
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Map<String, Session> eventMap = new HashMap<String, Session>(sessionControls.size());
        Set<UserAndContext> users = null;
        for (SessionControl sessionControl : sessionControls) {
            Session session = sessionControl.getSession();
            eventMap.put(session.getSessionID(), session);
            if (ContainerType.SHORT_TERM == sessionControl.geContainerType()) {
                if (users == null) {
                    users = new HashSet<UserAndContext>(sessionControls.size());
                }
                users.add(UserAndContext.newInstance(session));
            }
        }
        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_CONTAINER, eventMap);
        dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_REMOVE_CONTAINER, dic));
        LOG.debug("Posted event for removed session container");

        if (users != null) {
            SessionData sessionData = SESSION_DATA_REF.get();
            if (null != sessionData) {
                for (UserAndContext userKey : users) {
                    if (false == sessionData.isUserActive(userKey.getUserId(), userKey.getContextId(), false)) {
                        postLastSessionGone(userKey.getUserId(), userKey.getContextId(), eventAdmin);
                    }
                }
            }
        }
    }

    private static void postSessionDataRemoval(List<SessionControl> controls) {
        // Post event
        EventAdmin eventAdmin = Services.optService(EventAdmin.class);
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        Map<String, Session> eventMap = new HashMap<String, Session>(controls.size());
        Set<UserAndContext> users = null;
        for (SessionControl sessionControl : controls) {
            Session session = sessionControl.getSession();
            eventMap.put(session.getSessionID(), session);
            if (ContainerType.SHORT_TERM == sessionControl.geContainerType()) {
                if (users == null) {
                    users = new HashSet<UserAndContext>(controls.size());
                }
                users.add(UserAndContext.newInstance(session));
            }
        }
        dic.put(SessiondEventConstants.PROP_CONTAINER, eventMap);
        dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_REMOVE_DATA, dic));
        LOG.debug("Posted event for removing temporary session data.");

        if (users != null) {
            SessionData sessionData = SESSION_DATA_REF.get();
            if (null != sessionData) {
                for (UserAndContext userKey : users) {
                    if (false == sessionData.isUserActive(userKey.getUserId(), userKey.getContextId(), false)) {
                        postLastSessionGone(userKey.getUserId(), userKey.getContextId(), eventAdmin);
                    }
                }
            }
        }
    }

    static void postSessionReactivation(Session session) {
        EventAdmin eventAdmin = Services.optService(EventAdmin.class);
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_SESSION, session);
        dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_REACTIVATE_SESSION, dic));
        LOG.debug("Posted event for reactivated session");
    }

    /**
     * Broadcasts the {@link SessiondEventConstants#TOPIC_TOUCH_SESSION} event, usually after the session has been moved to the first
     * container.
     *
     * @param session The session that was touched
     */
    static void postSessionTouched(Session session) {
        EventAdmin eventAdmin = Services.optService(EventAdmin.class);
        if (eventAdmin == null) {
            // Missing EventAdmin service. Nothing to do.
            return;
        }

        Dictionary<String, Object> dic = new Hashtable<String, Object>(2);
        dic.put(SessiondEventConstants.PROP_SESSION, session);
        dic.put(SessiondEventConstants.PROP_COUNTER, SESSION_COUNTER);
        eventAdmin.postEvent(new Event(SessiondEventConstants.TOPIC_TOUCH_SESSION, dic));
        LOG.debug("Posted event for touched session");
    }

    public static void addThreadPoolService(ThreadPoolService service) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null != sessionData) {
            sessionData.addThreadPoolService(service);
        }
    }

    public static void removeThreadPoolService() {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null != sessionData) {
            sessionData.removeThreadPoolService();
        }
    }

    public static void addTimerService(TimerService service) {
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null != sessionData) {
            sessionData.addTimerService(service);
        }

        long containerTimeout = config.getSessionContainerTimeout();
        shortSessionContainerRotator = service.scheduleWithFixedDelay(new ShortSessionContainerRotator(), containerTimeout, containerTimeout);
        long longContainerTimeout = config.getLongTermSessionContainerTimeout();
        longSessionContainerRotator = service.scheduleWithFixedDelay(new LongSessionContainerRotator(), longContainerTimeout, longContainerTimeout);
    }

    public static void removeTimerService() {
        ScheduledTimerTask longSessionContainerRotator = SessionHandler.longSessionContainerRotator;
        if (longSessionContainerRotator != null) {
            longSessionContainerRotator.cancel(false);
            SessionHandler.longSessionContainerRotator = null;
        }
        ScheduledTimerTask shortSessionContainerRotator = SessionHandler.shortSessionContainerRotator;
        if (shortSessionContainerRotator != null) {
            shortSessionContainerRotator.cancel(false);
            SessionHandler.shortSessionContainerRotator = null;
        }
        SessionData sessionData = SESSION_DATA_REF.get();
        if (null != sessionData) {
            sessionData.removeTimerService();
        }
    }

    public static void addSessionSerializationInterceptor(SessionSerializationInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public static void removeSessionSerializationInterceptor(SessionSerializationInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    private static ShortTermSessionControl sessionToSessionControl(Session session) {
        if (session == null) {
            return null;
        }
        return new ShortTermSessionControl(session instanceof SessionImpl ? (SessionImpl) session : new SessionImpl(session));
    }

    private static Session[] merge(Session[] array1, final Session[] array2) {
        int lenghtArray1 = 0, lengthArray2 = 0;
        if (array1 != null) {
            lenghtArray1 = array1.length;
        }
        if (array2 != null) {
            lengthArray2 = array2.length;
        }
        Session[] retval = new Session[lenghtArray1 + lengthArray2];
        int i = 0;
        if (array1 != null) {
            for (Session s : array1) {
                retval[i++] = s;
            }
        }
        if (array2 != null) {
            for (Session s : array2) {
                retval[i++] = s;
            }
        }
        return retval;
    }

    private static final class StoreSessionTask extends AbstractTask<Void> {

        private final SessionStorageService sessionStorageService;
        private final boolean addIfAbsent;
        private final SessionImpl session;

        protected StoreSessionTask(SessionImpl session, SessionStorageService sessionStorageService, boolean addIfAbsent) {
            super();
            this.sessionStorageService = sessionStorageService;
            this.addIfAbsent = addIfAbsent;
            this.session = session;
        }

        @Override
        public Void call() throws OXException {
            try {
                if (addIfAbsent) {
                    if (sessionStorageService.addSessionIfAbsent(getObfuscator().wrap(session))) {
                        LOG.info("Put session {} with auth Id {} into session storage.", session.getSessionID(), session.getAuthId());
                        postSessionStored(session);
                    }
                } else {
                    sessionStorageService.addSession(getObfuscator().wrap(session));
                    LOG.info("Put session {} with auth Id {} into session storage.", session.getSessionID(), session.getAuthId());
                    postSessionStored(session);
                }
            } catch (Exception e) {
                LOG.warn("Failed to put session {} with Auth-Id {} into session storage (user={}, context={})", session.getSessionID(), session.getAuthId(), I(session.getUserId()), I(session.getContextId()), e);
            }
            return null;
        }
    }

    private static volatile Integer timeout;

    /**
     * Gets the default timeout for session-storage operations.
     *
     * @return The default timeout in milliseconds
     */
    public static int timeout() {
        Integer tmp = timeout;
        if (null == tmp) {
            synchronized (SessionHandler.class) {
                tmp = timeout;
                if (null == tmp) {
                    ConfigurationService service = Services.optService(ConfigurationService.class);
                    int defaultTimeout = 3000;
                    tmp = I(null == service ? defaultTimeout : service.getIntProperty("com.openexchange.sessiond.sessionstorage.timeout", defaultTimeout));
                    timeout = tmp;
                }
            }
        }
        return tmp.intValue();
    }

    /**
     * Gets the denoted session from session storage using given timeout.
     *
     * @param sessionId The session identifier
     * @param timeoutMillis The timeout in milliseconds; a value lower than or equal to zero is a synchronous call
     * @param storageService The session storage instance
     * @return The unwrapped session or <code>null</code> if timeout elapsed
     * @throws OXException If fetching session from session storage fails
     */
    private static SessionImpl getSessionFrom(String sessionId, long timeoutMillis, SessionStorageService storageService) throws OXException {
        try {
            return getObfuscator().unwrap(storageService.lookupSession(sessionId, timeoutMillis));
        } catch (OXException e) {
            if (SessionStorageExceptionCodes.INTERRUPTED.equals(e)) {
                // Expected...
                return null;
            }
            throw e;
        }
    }

    /**
     * Submits given task to thread pool while ignoring a possible {@link RejectedExecutionException} in case thread pool refuses its
     * execution.
     *
     * @param task The task to submit
     */
    private static <V> void submitAndIgnoreRejection(Task<V> task) {
        try {
            ThreadPools.getThreadPool().submit(task);
        } catch (@SuppressWarnings("unused") RejectedExecutionException e) {
            // Ignore
        }
    }

    private static <V> V getFrom(Task<V> c, V defaultValue) {
        Future<V> f;
        try {
            f = ThreadPools.getThreadPool().submit(c);
        } catch (@SuppressWarnings("unused") Exception e) {
            // Failed to submit to thread pool
            return defaultValue;
        }

        // Await task completion
        try {
            return f.get(timeout(), TimeUnit.MILLISECONDS);
        } catch (@SuppressWarnings("unused") InterruptedException e) {
            Thread.currentThread().interrupt();
            return defaultValue;
        } catch (ExecutionException e) {
            ThreadPools.launderThrowable(e, OXException.class);
            return defaultValue;
        } catch (@SuppressWarnings("unused") TimeoutException e) {
            f.cancel(true);
            return defaultValue;
        } catch (@SuppressWarnings("unused") CancellationException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a value indicating whether a session qualifies for being put in the distributed session storage or not. This includes a check
     * for the "transient" flag, as well as other relevant session properties.
     *
     * @param session The session to check
     * @return <code>true</code> if session should be put to storage, <code>false</code>, otherwise
     */
    public static boolean useSessionStorage(Session session) {
        return null != session && false == session.isTransient() && false == isUsmEas(session.getClient());
    }

    /**
     * Gets a value indicating whether the supplied client identifier indicates an USM session or not.
     *
     * @param clientId the client identifier to check
     * @return <code>true</code> if the client denotes an USM client, <code>false</code>, otherwise
     */
    private static boolean isUsmEas(String clientId) {
        if (Strings.isEmpty(clientId)) {
            return false;
        }
        String uc = Strings.toUpperCase(clientId);
        return uc.startsWith("USM-EAS") || uc.startsWith("USM-JSON");
    }

}
