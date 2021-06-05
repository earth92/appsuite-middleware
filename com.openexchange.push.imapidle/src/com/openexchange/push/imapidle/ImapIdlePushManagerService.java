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

package com.openexchange.push.imapidle;

import static com.openexchange.java.Autoboxing.I;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.exception.OXException;
import com.openexchange.hazelcast.Hazelcasts;
import com.openexchange.java.Streams;
import com.openexchange.lock.AccessControl;
import com.openexchange.lock.LockService;
import com.openexchange.lock.ReentrantLockAccessControl;
import com.openexchange.push.PushListener;
import com.openexchange.push.PushListenerService;
import com.openexchange.push.PushManagerExtendedService;
import com.openexchange.push.PushUser;
import com.openexchange.push.PushUserInfo;
import com.openexchange.push.PushUtility;
import com.openexchange.push.imapidle.ImapIdlePushListener.PushMode;
import com.openexchange.push.imapidle.control.ImapIdleControl;
import com.openexchange.push.imapidle.control.ImapIdleControlTask;
import com.openexchange.push.imapidle.control.ImapIdlePeriodicControlTask;
import com.openexchange.push.imapidle.locking.ImapIdleClusterLock;
import com.openexchange.push.imapidle.locking.ImapIdleClusterLock.AcquisitionResult;
import com.openexchange.push.imapidle.locking.SessionInfo;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.ObfuscatorService;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessionMatcher;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.sessiond.SessiondServiceExtended;
import com.openexchange.sessionstorage.hazelcast.serialization.PortableMultipleActiveSessionRemoteLookUp;
import com.openexchange.sessionstorage.hazelcast.serialization.PortableSession;
import com.openexchange.sessionstorage.hazelcast.serialization.PortableSessionCollection;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;


/**
 * {@link ImapIdlePushManagerService} - The IMAP IDLE push manager.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since 7.6.1
 */
public final class ImapIdlePushManagerService implements PushManagerExtendedService {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ImapIdlePushManagerService.class);

    private static enum StopResult {
        NONE, RECONNECTED, RECONNECTED_AS_PERMANENT, STOPPED;
    }

    private static volatile ImapIdlePushManagerService instance;

    /**
     * Gets the instance
     *
     * @return The instance or <code>null</code> if not initialized
     */
    public static ImapIdlePushManagerService getInstance() {
        return instance;
    }

    public static ImapIdlePushManagerService newInstance(ImapIdleConfiguration configuration, ServiceLookup services) throws OXException {
        ImapIdlePushManagerService tmp = new ImapIdlePushManagerService(configuration.getFullName(), configuration.getAccountId(), configuration.getPushMode(), configuration.getDelay(), configuration.getClusterLock(), configuration.isCheckPeriodic(), services);
        instance = tmp;
        return tmp;
    }

    public static boolean isTransient(Session session, ServiceLookup services) {
        SessiondService service = services.getService(SessiondService.class);
        return (service instanceof SessiondServiceExtended) ? false == ((SessiondServiceExtended) service).isApplicableForSessionStorage(session) : false;
    }

    private static interface Cancelable {

        void cancel();
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private final String name;
    private final ServiceLookup services;
    private final ImapIdleControl control;
    private final ConcurrentMap<SimpleKey, ImapIdlePushListener> listeners;
    private final String fullName;
    private final int accountId;
    private final PushMode pushMode;
    private final ImapIdleClusterLock clusterLock;
    private final long delay;
    private final Cancelable cancelable;
    private final AccessControl globalLock;

    /**
     * Initializes a new {@link ImapIdlePushManagerService}.
     */
    private ImapIdlePushManagerService(String fullName, int accountId, PushMode pushMode, long delay, ImapIdleClusterLock clusterLock, boolean checkPeriodic, ServiceLookup services) throws OXException {
        super();
        name = "IMAP-IDLE Push Manager";
        this.pushMode = pushMode;
        this.delay = delay;
        this.fullName = fullName;
        this.accountId = accountId;
        this.clusterLock = clusterLock;
        this.services = services;
        this.control = new ImapIdleControl();
        listeners = new ConcurrentHashMap<SimpleKey, ImapIdlePushListener>(512, 0.9f, 1);

        if (checkPeriodic) {
            // Initialize timer task to check for expired IMAP-IDLE push listeners for every 30 seconds
            TimerService timerService = services.getOptionalService(TimerService.class);
            if (null == timerService) {
                throw ServiceExceptionCode.absentService(TimerService.class);
            }
            ScheduledTimerTask timerTask = timerService.scheduleWithFixedDelay(new ImapIdlePeriodicControlTask(control), 30, 30, TimeUnit.SECONDS);
            cancelable = new Cancelable() {

                @Override
                public void cancel() {
                    timerTask.cancel();
                }
            };
        } else {
            // Initialize task to check for expired IMAP-IDLE push listeners
            ThreadPoolService threadPool = services.getOptionalService(ThreadPoolService.class);
            if (null == threadPool) {
                throw ServiceExceptionCode.absentService(ThreadPoolService.class);
            }
            ImapIdleControlTask controlTask = new ImapIdleControlTask(control);
            Future<Void> submitted = threadPool.submit(controlTask);
            cancelable = new Cancelable() {

                @Override
                public void cancel() {
                    controlTask.cancel();
                    submitted.cancel(true);
                }
            };
        }

        // The fall-back lock
        globalLock = new ReentrantLockAccessControl();
    }

    /**
     * Checks if IMAP-IDLE Push is enabled for given user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if enabled; otherwise <code>false</code>
     * @throws OXException If check fails
     */
    private boolean isImapIdlePushEnabledFor(int userId, int contextId) throws OXException {
        ConfigViewFactory factory = services.getOptionalService(ConfigViewFactory.class);
        if (factory == null) {
            throw ServiceExceptionCode.absentService(ConfigViewFactory.class);
        }

        ConfigView view = factory.getView(userId, contextId);
        return ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.push.imapidle.enabled", true, view);
    }

    private AccessControl getlockFor(int userId, int contextId) {
        LockService lockService = services.getOptionalService(LockService.class);
        if (null == lockService) {
            return globalLock;
        }

        try {
            return lockService.getAccessControlFor("imapidle", 1, userId, contextId);
        } catch (Exception e) {
            LOGGER.warn("Failed to acquire lock for user {} in context {}. Using global lock instead.", I(userId), I(contextId), e);
            return globalLock;
        }
    }

    /**
     * Shuts-down this IMAP-IDLE push manager.
     */
    public void shutDown() {
        cancelable.cancel();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Gets the account identifier
     *
     * @return The account identifier
     */
    public int getAccountId() {
        return accountId;
    }

    private boolean hasPermanentPush(int userId, int contextId) {
        try {
            PushListenerService pushListenerService = services.getService(PushListenerService.class);
            return pushListenerService.hasRegistration(new PushUser(userId, contextId));
        } catch (Exception e) {
            LOGGER.warn("Failed to check for push registration for user {} in context {}", I(userId), I(contextId), e);
            return false;
        }
    }

    private Session generateSessionFor(int userId, int contextId) throws OXException {
        PushListenerService pushListenerService = services.getService(PushListenerService.class);
        return pushListenerService.generateSessionFor(new PushUser(userId, contextId));
    }

    private Session generateSessionFor(PushUser pushUser) throws OXException {
        PushListenerService pushListenerService = services.getService(PushListenerService.class);
        return pushListenerService.generateSessionFor(pushUser);
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    @Override
    public List<PushUserInfo> getAvailablePushUsers() throws OXException {
        List<PushUserInfo> l = new LinkedList<PushUserInfo>();
        for (Map.Entry<SimpleKey, ImapIdlePushListener> entry : listeners.entrySet()) {
            SimpleKey key = entry.getKey();
            l.add(new PushUserInfo(new PushUser(key.userId, key.contextId), entry.getValue().isPermanent()));
        }
        return l;
    }

    @Override
    public boolean supportsPermanentListeners() {
        boolean defaultValue = false;
        ConfigurationService service = services.getOptionalService(ConfigurationService.class);
        return null == service ? defaultValue : service.getBoolProperty("com.openexchange.push.imapidle.supportsPermanentListeners", defaultValue);
    }

    @Override
    public PushListener startPermanentListener(PushUser pushUser) throws OXException {
        if (null == pushUser || !supportsPermanentListeners()) {
            return null;
        }

        Session session = generateSessionFor(pushUser);
        int contextId = session.getContextId();
        int userId = session.getUserId();

        if (false == isImapIdlePushEnabledFor(userId, contextId)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.info("Denied starting permanent IMAP-IDLE listener for user {} in context {} since disabled via configuration", I(userId), I(contextId), new Throwable("IMAP-IDLE start permanent listener trace"));
            } else {
                LOGGER.info("Denied starting permanent IMAP-IDLE listener for user {} in context {} since disabled via configuration", I(userId), I(contextId));
            }
            return null;
        }

        SessionInfo sessionInfo = new SessionInfo(session, true, false);
        AcquisitionResult acquisitionResult = clusterLock.acquireLock(sessionInfo);
        if (AcquisitionResult.NOT_ACQUIRED == acquisitionResult) {
            LOGGER.info("Could not acquire lock to start permanent IMAP-IDLE listener for user {} in context {} with session {} as there is already an associated listener", I(userId), I(contextId), session.getSessionID());
        } else {
            // Locked...
            switch (acquisitionResult) {
                case ACQUIRED_NEW:
                    LOGGER.info("Acquired lock to start permanent IMAP-IDLE listener for user {} in context {} with session {}", I(userId), I(contextId), session.getSessionID());
                    break;
                case ACQUIRED_NO_SUCH_SESSION:
                    LOGGER.info("Acquired lock to start permanent IMAP-IDLE listener for user {} in context {} with session {} since the session is gone, which is associated with existent IMAP-IDLE listener", I(userId), I(contextId), session.getSessionID());
                    break;
                case ACQUIRED_TIMED_OUT:
                    LOGGER.info("Acquired lock to start permanent IMAP-IDLE listener for user {} in context {} with session {} since lock for existent IMAP-IDLE listener timed out", I(userId), I(contextId), session.getSessionID());
                    break;
                default:
                    break;
            }

            boolean unlock = true;
            try {
                AccessControl lock = getlockFor(userId, contextId);
                try {
                    lock.acquireGrant();
                    ImapIdlePushListener listener = new ImapIdlePushListener(fullName, accountId, pushMode, delay, session, true, supportsPermanentListeners(), control, services);
                    ImapIdlePushListener current = listeners.putIfAbsent(SimpleKey.valueOf(userId, contextId), listener);
                    if (null == current) {
                        listener.start();
                        unlock = false;
                        LOGGER.info("Started permanent IMAP-IDLE listener for user {} in context {}", I(userId), I(contextId));
                        return listener;
                    } else if (!current.isPermanent()) {
                        // Cancel current & replace
                        current.cancel(false);
                        listeners.put(SimpleKey.valueOf(userId, contextId), listener);
                        listener.start();
                        unlock = false;
                        LOGGER.info("Started permanent IMAP-IDLE listener for user {} in context {}", I(userId), I(contextId));
                        return listener;
                    }

                    // Already running for session user
                    LOGGER.info("Did not start permanent IMAP-IDLE listener for user {} in context {} with session {} as there is already an associated listener", I(userId), I(contextId), session.getSessionID());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new OXException(e);
                } finally {
                    Streams.close(lock);
                }
            } finally {
                if (unlock) {
                    releaseLock(sessionInfo);
                }
            }
        }

        // No listener registered for given session
        return null;
    }

    @Override
    public boolean stopPermanentListener(PushUser pushUser, boolean tryToReconnect) throws OXException {
        if (null == pushUser) {
            return false;
        }

        StopResult stopResult = stopListener(tryToReconnect, true, pushUser.getUserId(), pushUser.getContextId());
        switch (stopResult) {
        case RECONNECTED:
            LOGGER.info("Reconnected permanent IMAP-IDLE listener for user {} in context {}", I(pushUser.getUserId()), I(pushUser.getContextId()));
            return true;
        case STOPPED:
            LOGGER.info("Stopped permanent IMAP-IDLE listener for user {} in context {}", I(pushUser.getUserId()), I(pushUser.getContextId()));
            return true;
        default:
            break;
        }

        return false;
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    @Override
    public PushListener startListener(Session session) throws OXException {
        if (null == session) {
            return null;
        }
        int contextId = session.getContextId();
        int userId = session.getUserId();

        if (false == isImapIdlePushEnabledFor(userId, contextId)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.info("Denied starting IMAP-IDLE listener for user {} in context {} with session {} since disabled via configuration", I(userId), I(contextId), session.getSessionID(), new Throwable("IMAP-IDLE start listener trace"));
            } else {
                LOGGER.info("Denied starting IMAP-IDLE listener for user {} in context {} with session {} since disabled via configuration", I(userId), I(contextId), session.getSessionID());
            }
            return null;
        }

        SessionInfo sessionInfo = new SessionInfo(session, false, isTransient(session, services));
        AcquisitionResult acquisitionResult = clusterLock.acquireLock(sessionInfo);
        if (AcquisitionResult.NOT_ACQUIRED == acquisitionResult) {
            LOGGER.info("Could not acquire lock to start IMAP-IDLE listener for user {} in context {} with session {} as there is already an associated listener", I(userId), I(contextId), session.getSessionID());
        } else {
            // Locked...
            switch (acquisitionResult) {
                case ACQUIRED_NEW:
                    LOGGER.info("Acquired lock to start IMAP-IDLE listener for user {} in context {} with session {}", I(userId), I(contextId), session.getSessionID());
                    break;
                case ACQUIRED_NO_SUCH_SESSION:
                    LOGGER.info("Acquired lock to start IMAP-IDLE listener for user {} in context {} with session {} since the session is gone, which is associated with existent IMAP-IDLE listener", I(userId), I(contextId), session.getSessionID());
                    break;
                case ACQUIRED_TIMED_OUT:
                    LOGGER.info("Acquired lock to start IMAP-IDLE listener for user {} in context {} with session {} since lock for existent IMAP-IDLE listener timed out", I(userId), I(contextId), session.getSessionID());
                    break;
                default:
                    break;
            }

            boolean unlock = true;
            try {
                AccessControl lock = getlockFor(userId, contextId);
                try {
                    lock.acquireGrant();
                    ImapIdlePushListener listener = new ImapIdlePushListener(fullName, accountId, pushMode, delay, session, false, supportsPermanentListeners(), control, services);
                    if (null == listeners.putIfAbsent(SimpleKey.valueOf(userId, contextId), listener)) {
                        listener.start();
                        unlock = false;
                        LOGGER.info("Started IMAP-IDLE listener for user {} in context {} with session {} ({})", I(userId), I(contextId), session.getSessionID(), session.getClient());
                        return listener;
                    }

                    // Already running for session user
                    LOGGER.info("Did not start IMAP-IDLE listener for user {} in context {} with session {} ({}) as there is already an associated listener", I(userId), I(contextId), session.getSessionID(), session.getClient());
                }  catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new OXException(e);
                } finally {
                    Streams.close(lock);
                }
            } finally {
                if (unlock) {
                    releaseLock(sessionInfo);
                }
            }
        }

        // No listener registered for given session
        return null;
    }

    @Override
    public boolean stopListener(Session session) throws OXException {
        if (null == session) {
            return false;
        }

        StopResult stopResult = stopListener(true, false, session.getUserId(), session.getContextId());
        switch (stopResult) {
        case RECONNECTED:
            LOGGER.info("Reconnected IMAP-IDLE listener for user {} in context {} using another session", I(session.getUserId()), I(session.getContextId()));
            return true;
        case RECONNECTED_AS_PERMANENT:
            LOGGER.info("Reconnected as permanent IMAP-IDLE listener for user {} in context {}", I(session.getUserId()), I(session.getContextId()));
            return true;
        case STOPPED:
            LOGGER.info("Stopped IMAP-IDLE listener for user {} in context {} with session {}", I(session.getUserId()), I(session.getContextId()), session.getSessionID());
            return true;
        default:
            break;
        }

        return false;
    }

    /**
     * Stops the listener associated with given user.
     *
     * @param tryToReconnect <code>true</code> to signal that a reconnect using another session should be performed; otherwise <code>false</code>
     * @param stopIfPermanent <code>true</code> to signal that current listener is supposed to be stopped even though it might be associated with a permanent push registration; otherwise <code>false</code>
     * @param userId The user identifier
     * @param contextId The corresponding context identifier
     * @return The stop result
     */
    public StopResult stopListener(boolean tryToReconnect, boolean stopIfPermanent, int userId, int contextId) {
        AccessControl lock = getlockFor(userId, contextId);
        Runnable cleanUpTask = null;
        try {
            lock.acquireGrant();
            SimpleKey key = SimpleKey.valueOf(userId, contextId);
            ImapIdlePushListener listener = listeners.get(key);
            if (null != listener) {
                if (!stopIfPermanent && listener.isPermanent()) {
                    return StopResult.NONE;
                }

                // Remove from map
                listeners.remove(key);

                boolean tryRecon = tryToReconnect || (!listener.isPermanent() && hasPermanentPush(userId, contextId));
                cleanUpTask = listener.cancel(tryRecon);
                if (null != cleanUpTask) {
                    return StopResult.STOPPED;
                }

                ImapIdlePushListener newListener = listeners.get(key);
                return (null != newListener && newListener.isPermanent()) ? StopResult.RECONNECTED_AS_PERMANENT : StopResult.RECONNECTED;
            }
            return StopResult.NONE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            Streams.close(lock);
            if (null != cleanUpTask) {
                cleanUpTask.run();
            }
        }
    }

    /**
     * Releases the possibly held lock for given user.
     *
     * @param sessionInfo The associated session
     * @throws OXException If release operation fails
     */
    public void releaseLock(SessionInfo sessionInfo) throws OXException {
        clusterLock.releaseLock(sessionInfo);
    }

    /**
     * Refreshes the lock for given user.
     *
     * @param sessionInfo The associated session
     * @throws OXException If refresh operation fails
     */
    public void refreshLock(SessionInfo sessionInfo) throws OXException {
        clusterLock.refreshLock(sessionInfo);
    }

    /**
     * Tries to look-up another valid session and injects a new listener for it (discarding the existing one bound to given <code>oldSession</code>)
     *
     * @param oldSession The expired/outdated session
     * @return The new listener or <code>null</code>
     * @throws OXException If operation fails
     */
    public ImapIdlePushListener injectAnotherListenerFor(Session oldSession) {
        int contextId = oldSession.getContextId();
        int userId = oldSession.getUserId();

        // Prefer permanent listener prior to performing look-up for another valid session
        if (supportsPermanentListeners() && hasPermanentPush(userId, contextId)) {
            try {
                Session session = generateSessionFor(userId, contextId);
                return injectAnotherListenerUsing(session, true).injectedPushListener;
            } catch (OXException e) {
                // Failed to inject a permanent listener
            }
        }

        // Look-up sessions
        SessiondService sessiondService = services.getService(SessiondService.class);
        if (null != sessiondService) {
            final String oldSessionId = oldSession.getSessionID();

            // Query local ones first
            SessionMatcher matcher = new SessionMatcher() {

                @Override
                public Set<Flag> flags() {
                    return SessionMatcher.ONLY_SHORT_TERM;
                }

                @Override
                public boolean accepts(Session session) {
                    return !oldSessionId.equals(session.getSessionID()) && PushUtility.allowedClient(session.getClient(), session, true);
                }
            };
            Session anotherActiveSession = sessiondService.findFirstMatchingSessionForUser(userId, contextId, matcher);
            if (anotherActiveSession != null) {
                return injectAnotherListenerUsing(anotherActiveSession, false).injectedPushListener;
            }

            // If we're running a node-local lock, there is no need to check for sessions at other nodes
            if (ImapIdleClusterLock.Type.LOCAL != clusterLock.getType()) {
                // Look-up remote sessions, too, if possible
                Session session = lookUpRemoteSessionFor(oldSession);
                if (null != session) {
                    Session ses = sessiondService.getSession(session.getSessionID());
                    if (ses != null) {
                        return injectAnotherListenerUsing(ses, false).injectedPushListener;
                    }
                }
            }
        }

        return null;
    }

    private Session lookUpRemoteSessionFor(Session oldSession) {
        HazelcastInstance hzInstance = services.getOptionalService(HazelcastInstance.class);
        final ObfuscatorService obfuscatorService = services.getOptionalService(ObfuscatorService.class);
        if (null == hzInstance || null == obfuscatorService) {
            return null;
        }

        // Determine other cluster members
        Set<Member> otherMembers = Hazelcasts.getRemoteMembers(hzInstance);
        if (otherMembers.isEmpty()) {
            return null;
        }

        int contextId = oldSession.getContextId();
        int userId = oldSession.getUserId();
        final String oldSessionId = oldSession.getSessionID();
        Hazelcasts.Filter<PortableSessionCollection, PortableSession> filter = new Hazelcasts.Filter<PortableSessionCollection, PortableSession>() {

            @Override
            public PortableSession accept(PortableSessionCollection portableSessionCollection) {
                PortableSession[] portableSessions = portableSessionCollection.getSessions();
                if (null != portableSessions) {
                    for (PortableSession portableSession : portableSessions) {
                        if ((null == oldSessionId || false == oldSessionId.equals(portableSession.getSessionID())) && PushUtility.allowedClient(portableSession.getClient(), portableSession, true)) {
                            portableSession.setPassword(obfuscatorService.unobfuscate(portableSession.getPassword()));
                            return portableSession;
                        }
                    }
                }
                return null;
            }
        };
        try {
            return Hazelcasts.executeByMembersAndFilter(new PortableMultipleActiveSessionRemoteLookUp(userId, contextId), otherMembers, hzInstance.getExecutorService("default"), filter);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Not unchecked", cause);
        }
    }

    /**
     * Tries to look-up another valid session and injects a new listener for it (discarding the existing one bound to given <code>oldSession</code>)
     *
     * @param newSession The new session to use
     * @param permanent <code>true</code> if permanent; otherwise <code>false</code>
     * @return The new listener or <code>null</code>
     * @throws OXException If operation fails
     */
    public InjectedImapIdlePushListener injectAnotherListenerUsing(Session newSession, boolean permanent) {
        ImapIdlePushListener listener = new ImapIdlePushListener(fullName, accountId, pushMode, delay, newSession, permanent, supportsPermanentListeners(), control, services);
        // Replace old/existing one
        ImapIdlePushListener prev = listeners.put(SimpleKey.valueOf(newSession), listener);
        return new InjectedImapIdlePushListener(listener, prev);
    }

    /**
     * Stops all listeners.
     */
    public void stopAllListeners() {
        for (Iterator<ImapIdlePushListener> it = listeners.values().iterator(); it.hasNext();) {
            ImapIdlePushListener listener = it.next();
            try {
                listener.cancel(false);
            } catch (Exception e) {
                // Ignore
            }
            it.remove();
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private static class InjectedImapIdlePushListener {

        final ImapIdlePushListener injectedPushListener;
        @SuppressWarnings("unused")
        final ImapIdlePushListener replacedPushListener;

        InjectedImapIdlePushListener(ImapIdlePushListener injectedPushListener, ImapIdlePushListener replacedPushListener) {
            super();
            this.injectedPushListener = injectedPushListener;
            this.replacedPushListener = replacedPushListener;
        }
    }

}
