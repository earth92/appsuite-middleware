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

package com.openexchange.push.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.database.DatabaseService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.UpdateStatus;
import com.openexchange.groupware.update.Updater;
import com.openexchange.groupware.userconfiguration.UserConfigurationCodes;
import com.openexchange.groupware.userconfiguration.UserPermissionBitsStorage;
import com.openexchange.log.LogProperties;
import com.openexchange.mail.api.MailConfig.PasswordSource;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.osgi.ShutDownRuntimeException;
import com.openexchange.push.PushExceptionCodes;
import com.openexchange.push.PushListener;
import com.openexchange.push.PushListenerService;
import com.openexchange.push.PushManagerExtendedService;
import com.openexchange.push.PushManagerService;
import com.openexchange.push.PushUser;
import com.openexchange.push.PushUserClient;
import com.openexchange.push.PushUserInfo;
import com.openexchange.push.PushUtility;
import com.openexchange.push.credstorage.CredentialStorage;
import com.openexchange.push.credstorage.CredentialStorageProvider;
import com.openexchange.push.credstorage.Credentials;
import com.openexchange.push.credstorage.DefaultCredentials;
import com.openexchange.push.impl.PushDbUtils.DeleteResult;
import com.openexchange.push.impl.balancing.reschedulerpolicy.PermanentListenerRescheduler;
import com.openexchange.push.impl.jobqueue.PermanentListenerJob;
import com.openexchange.push.impl.jobqueue.PermanentListenerJobQueue;
import com.openexchange.push.impl.osgi.Services;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.session.Sessions;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.UserExceptionCode;
import com.openexchange.user.UserService;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link PushManagerRegistry} - The push manager registry.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class PushManagerRegistry implements PushListenerService {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(PushManagerRegistry.class);

    /** The <code>PushManagerRegistry</code> instance */
    private static volatile PushManagerRegistry instance;

    /**
     * Initializes push manager registry.
     */
    public static synchronized void init(ServiceLookup services) {
        if (null == instance) {
            instance = new PushManagerRegistry(services);
        }
    }

    /**
     * Shuts down push manager registry.
     */
    public static synchronized void shutdown() {
        instance = null;
    }

    /**
     * Gets the push manager registry.
     *
     * @return The push manager registry
     */
    public static PushManagerRegistry getInstance() {
        return instance;
    }

    /*-
     * --------------------------------------------------------- Member section ----------------------------------------------------------
     */

    private final ConcurrentMap<Class<? extends PushManagerService>, PushManagerService> map;
    private final ServiceLookup services;
    private final Set<PushUser> initialPushUsers;
    private final AtomicBoolean allUsersStarted;
    private final AtomicReference<PermanentListenerRescheduler> reschedulerRef;

    /**
     * Initializes a new {@link PushManagerRegistry}.
     *
     * @param services
     */
    private PushManagerRegistry(ServiceLookup services) {
        super();
        this.services = services;
        initialPushUsers = new HashSet<PushUser>(256); // Always wrapped by surrounding synchronized block
        allUsersStarted = new AtomicBoolean(false);
        map = new ConcurrentHashMap<Class<? extends PushManagerService>, PushManagerService>();
        reschedulerRef = new AtomicReference<PermanentListenerRescheduler>();
    }

    private boolean hasWebMailAndIsActive(Session session) {
        if (session == null) {
            return false;
        }

        if (!(session instanceof ServerSession)) {
            return hasWebMailAndIsActive(session.getUserId(), session.getContextId());
        }

        ServerSession serverSession = (ServerSession) session;
        int contextId = session.getContextId();
        int userId = session.getUserId();
        try {
            if (false == serverSession.getUserPermissionBits().hasWebMail()) {
                LOG.info("User {} in context {} has no 'WebMail' permission. Hence, no listener will be started.", I(userId), I(contextId));
            }
            if (false == serverSession.getUser().isMailEnabled()) {
                LOG.info("User {} in context {} is deactivated. Hence, no listener will be started.", I(userId), I(contextId));
            }
            return true;
        } catch (RuntimeException e) {
            // Thrown by ServerSession if unable to load resource (User, permission bits, etc.)
            Throwable cause = e.getCause();
            if (cause instanceof OXException) {
                OXException oxe = (OXException) cause;
                if (UserConfigurationCodes.NOT_FOUND.equals(oxe) || UserExceptionCode.USER_NOT_FOUND.equals(oxe)) {
                    // Apparently, no such user exists
                    return false;
                }

                LOG.error("Failed to check 'WebMail' permission and activation for user {} in context {}", I(userId), I(contextId), oxe);
                return false;
            }

            LOG.error("Failed to check 'WebMail' permission and activation for user {} in context {}", I(userId), I(contextId), e);
            return false;
        }
    }

    private boolean hasWebMailAndIsActive(int userId, int contextId) {
        try {
            if (false == UserPermissionBitsStorage.getInstance().getUserPermissionBits(userId, contextId).hasWebMail()) {
                LOG.info("User {} in context {} has no 'WebMail' permission. Hence, no listener will be started.", I(userId), I(contextId));
            }
            if (false == services.getServiceSafe(UserService.class).getUser(userId, contextId).isMailEnabled()) {
                LOG.info("User {} in context {} is deactivated. Hence, no listener will be started.", I(userId), I(contextId));
            }
            return true;
        } catch (OXException e) {
            if (UserConfigurationCodes.NOT_FOUND.equals(e) || UserExceptionCode.USER_NOT_FOUND.equals(e)) {
                // Apparently, no such user exists
                return false;
            }

            LOG.error("Failed to check 'WebMail' permission and activation for user {} in context {}", I(userId), I(contextId), e);
            return false;
        }
    }

    private CredentialStorage optCredentialStorage() throws OXException {
        CredentialStorageProvider storageProvider = services.getOptionalService(CredentialStorageProvider.class);
        return null == storageProvider ? null : storageProvider.getCredentialStorage();
    }

    private Credentials optCredentials(int userId, int contextId) throws OXException {
        CredentialStorage storage = optCredentialStorage();
        return null == storage ? null : storage.getCredentials(userId, contextId);
    }

    /**
     * Gets the rescheduler instance
     *
     * @return The rescheduler instance or <code>null</code>
     */
    public PermanentListenerRescheduler getRescheduler() {
        return reschedulerRef.get();
    }

    /**
     * Sets the rescheduler instance
     *
     * @param rescheduler The rescheduler instance
     */
    public void setRescheduler(PermanentListenerRescheduler rescheduler) {
        reschedulerRef.set(rescheduler);
    }

    /**
     * Checks if permanent push is allowed as per configuration and if there is at least one push service supporting resource-acquiring
     * permanent listeners.
     *
     * @return <code>true</code> if allowed; otherwise <code>false</code> if disabled
     */
    public boolean isPermanentPushAllowed() {
        if (isPermanentPushAllowedPerConfig() == false) {
            return false;
        }

        for (PushManagerExtendedService extendedService : getExtendedPushManagers()) {
            if (extendedService.supportsPermanentListeners() && extendedService.listenersRequireResources()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if permanent push is allowed as per configuration.
     *
     * @return <code>true</code> if allowed; otherwise <code>false</code> if disabled
     */
    public boolean isPermanentPushAllowedPerConfig() {
        boolean defaultValue = true;
        ConfigurationService service = Services.optService(ConfigurationService.class);
        return null == service ? defaultValue : service.getBoolProperty("com.openexchange.push.allowPermanentPush", defaultValue);
    }

    /**
     * Checks if this registry contains at least one <i>{@link PushManagerExtendedService}</i> instance at the time of invocation.
     *
     * @return <code>true</code> if this registry contains at least one <code>PushManagerExtendedService</code> instance; otherwise <code>false</code>
     */
    public boolean hasExtendedService() {
        for (Iterator<PushManagerService> pushManagersIterator = map.values().iterator(); pushManagersIterator.hasNext();) {
            if (pushManagersIterator.next() instanceof PushManagerExtendedService) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lists currently running push users.
     *
     * @return The push users
     */
    public List<PushUserInfo> listPushUsers() {
        List<PushUserInfo> list = new ArrayList<PushUserInfo>(listPushUsers0());
        Collections.sort(list);
        return list;
    }

    /**
     * Lists currently running permanent push users.
     *
     * @return The push users
     */
    private Set<PushUserInfo> listPushUsers0() {
        Set<PushUserInfo> pushUsers = new HashSet<PushUserInfo>(256);

        for (PushManagerExtendedService extendedService : getExtendedPushManagers()) {
            try {
                pushUsers.addAll(extendedService.getAvailablePushUsers());
            } catch (Exception e) {
                LOG.error("Failed to determine available push users from push manager \"{}\".", extendedService, e);
            }
        }

        return pushUsers;
    }

    /**
     * Lists registered push users.
     *
     * @return The push users
     * @throws OXException If registered push users cannot be returned
     */
    public List<PushUserClient> listRegisteredPushUsers() throws OXException {
        return PushDbUtils.getPushClientRegistrations();
    }


    // --------------------------------- The central start & stop routines for permanent listeners --------------------------------------

    /**
     * Starts a permanent listener for given push users.
     *
     * @param pushUsers The push users
     * @param extendedService The associated extended push manager
     * @param allowPermanentPush Whether permanent push is allowed at all
     * @return The actually started ones
     */
    private List<PermanentListenerJob> startPermanentListenersFor(Collection<PushUser> pushUsers, PushManagerExtendedService extendedService, boolean allowPermanentPush) {
        // Always called when holding synchronized lock
        if (!allowPermanentPush || !extendedService.supportsPermanentListeners()) {
            return Collections.emptyList();
        }

        PermanentListenerJobQueue jobQueue = PermanentListenerJobQueue.getInstance();
        TIntSet blockedContexts = new TIntHashSet(pushUsers.size());
        List<PermanentListenerJob> startedOnes = new ArrayList<PermanentListenerJob>(pushUsers.size());
        NextPushUser: for (PushUser pushUser : pushUsers) {
            // Schedule to start permanent listener for current push user
            int contextId = pushUser.getContextId();
            int userId = pushUser.getUserId();

            try {
                if (schemaBeingLockedOrNeedsUpdate(contextId, blockedContexts)) {
                    LOG.info("Database schema is locked or needs update. Denied start-up of permanent push listener for user {} in context {} by push manager \"{}\"", I(userId), I(contextId), extendedService);
                } else {
                    if (hasWebMailAndIsActive(userId, contextId)) {
                        PermanentListenerJob job = jobQueue.scheduleJob(pushUser, extendedService);
                        if (null != job) {
                            startedOnes.add(job);
                            LOG.debug("Scheduled to start permanent push listener for user {} in context {} by push manager \"{}\"", I(userId), I(contextId), extendedService, new Throwable("Start permanent push listener trace"));
                        }
                    } else {
                        LOG.debug("Denied start of a permanent push listener for user {} in context {}: Missing \"webmail\" permission or user is disabled.", I(userId), I(contextId));
                    }
                }
            } catch (OXException e) {
                LOG.error("Error while starting permanent push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), extendedService, e);
            } catch (ShutDownRuntimeException shutDown) {
                LOG.error("Server shut-down while starting permanent push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), extendedService, shutDown);
                break NextPushUser;
            } catch (RuntimeException e) {
                LOG.error("Runtime error while starting permanent push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), extendedService, e);
            }
        }
        Collections.sort(startedOnes);
        return startedOnes;
    }

    /**
     * Handles invalid credentials error.
     *
     * @param pushUser The affected user
     * @param tryRestore Whether possibly stored credentials should be updated
     * @param e The error signaling invalid credentials
     */
    public void handleInvalidCredentials(PushUser pushUser, boolean tryRestore, OXException e) {
        CredentialStorage credentialStorage;
        try {
            credentialStorage = optCredentialStorage();
        } catch (Exception x) {
            LOG.debug("Failed to acquire credentials storage for push user {} in context {}.", I(pushUser.getUserId()), I(pushUser.getContextId()), e);
            return;
        }
        if (null != credentialStorage) {
            try {
                Session session = tryRestore ? new SessionLookUpUtility(this, services).lookUpSessionFor(pushUser, false, true) : null;
                if (null == session) {
                    credentialStorage.deleteCredentials(pushUser.getUserId(), pushUser.getContextId());
                } else {
                    credentialStorage.storeCredentials(new DefaultCredentials(session));
                }
            } catch (Exception x) {
                LOG.warn("Failed to {} credentials for push user {} in context {}.", tryRestore ? "restore" : "delete", I(pushUser.getUserId()), I(pushUser.getContextId()), e);
            }
        }
    }

    private boolean schemaBeingLockedOrNeedsUpdate(int contextId, TIntSet blockedContexts) throws OXException {
        if (blockedContexts.contains(contextId)) {
            return true;
        }

        Updater updater;
        try {
            updater = Updater.getInstance();
            UpdateStatus status = updater.getStatus(contextId);
            if (status.blockingUpdatesRunning() || status.needsBlockingUpdates()) {
                addContextsInTheSameSchema(contextId, blockedContexts);
                return true;
            }
            return false;
        } catch (OXException e) {
            if (e.getCode() == 102) {
                // NOTE: this situation should not happen!
                // it can only happen, when a schema has not been initialized correctly!
                LOG.debug("FATAL: this error must not happen",e);
            }
            LOG.error("Error in checking/updating schema",e);
            throw e;
        }
    }

    private void addContextsInTheSameSchema(int contextId, TIntSet blockedContexts) {
        DatabaseService databaseService = services.getOptionalService(DatabaseService.class);
        if (databaseService != null) {
            try {
                blockedContexts.addAll(databaseService.getContextsInSameSchema(contextId));
            } catch (Exception e) {
                LOG.warn("Failed to retrieve contexts in the same schema for {}", I(contextId), e);
            }
        }
    }

    /**
     * Stops a permanent listener for given push user.
     *
     * @param pushUser The push user to stop
     * @param extendedService The associated extended push manager
     * @param tryToReconnect Whether a reconnect attempt is supposed to be performed
     * @return <code>true</code> if permanent listener has been successfully stopped; otherwise <code>false</code>
     * @throws OXException If stop attempt fails
     */
    private StopResult stopPermanentListenerFor(PushUser pushUser, PushManagerExtendedService extendedService, boolean tryToReconnect) throws OXException {
        boolean canceled = PermanentListenerJobQueue.getInstance().cancelJob(pushUser);
        if (canceled) {
            return StopResult.CANCELED;
        }

        try {
            return extendedService.stopPermanentListener(pushUser, tryToReconnect) ? StopResult.STOPPED : StopResult.NONE;
        } catch (OXException e) {
            if (PushExceptionCodes.AUTHENTICATION_ERROR.equals(e)) {
                handleInvalidCredentials(pushUser, false, e);
            }
            throw e;
        }
    }

    private List<PushManagerExtendedService> getExtendedPushManagers() {
        List<PushManagerExtendedService> managers = null;
        for (PushManagerService pushManager : map.values()) {
            if (pushManager instanceof PushManagerExtendedService) {
                if (null == managers) {
                    managers = new ArrayList<>(2);
                }
                managers.add((PushManagerExtendedService) pushManager);
            }
        }
        return null == managers ? Collections.emptyList() : managers;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    /**
     * Checks if this push manager registry has initially started all permanent listeners.
     *
     * @return <code>true</code> when all were started; otherwise <code>false</code>
     */
    public boolean wereAllUsersStarted() {
        return allUsersStarted.get();
    }

    /**
     * Starts the permanent listeners for given push users.
     *
     * @param pushUsers The push users
     * @param all <code>true</code> if given list of push users represent all available push users (see {@link #getUsersWithPermanentListeners()}); otherwise <code>false</code>
     * @param parkNanos The number of nanoseconds to wait prior to starting listeners
     * @return The actually started ones
     */
    public List<PermanentListenerJob> applyInitialListeners(List<PushUser> pushUsers, boolean all, long parkNanos) {
        if (pushUsers == null || pushUsers.isEmpty()) {
            return Collections.emptyList();
        }

        Collection<PushUser> toStop;
        Collection<PushUser> toStart;

        synchronized (this) {
            if (initialPushUsers.isEmpty()) {
                toStop = Collections.emptySet();
            } else {
                Set<PushUser> current = new HashSet<PushUser>(initialPushUsers);
                current.removeAll(pushUsers);
                toStop = current;
            }

            toStart = new ArrayList<PushUser>(pushUsers.size());
            for (PushUser pushUser : pushUsers) {
                if (initialPushUsers.add(pushUser)) {
                    toStart.add(pushUser);
                }
            }

            initialPushUsers.removeAll(toStop);
        }

        boolean nothingToStop = toStop.isEmpty();
        if (nothingToStop && toStart.isEmpty()) {
            // Nothing to do
            return Collections.emptyList();
        }

        // Determine currently available push managers
        List<PushManagerExtendedService> managers = getExtendedPushManagers();

        // Stop permanent candidates (release acquired resources, etc.)
        if (false == nothingToStop) {
            for (PushUser pushUser : toStop) {
                for (PushManagerExtendedService pushManager : managers) {
                    try {
                        StopResult stopped = stopPermanentListenerFor(pushUser, pushManager, false);
                        if (stopped != StopResult.NONE) {
                            LOG.debug("{} permanent push listener for user {} in context {} by push manager \"{}\"", stopped.getWord(), I(pushUser.getUserId()), I(pushUser.getContextId()), pushManager);
                        }
                    } catch (OXException e) {
                        if (PushExceptionCodes.AUTHENTICATION_ERROR.equals(e)) {
                            handleInvalidCredentials(pushUser, true, e);
                        }
                        LOG.error("Error while stopping permanent push listener for user {} in context {} by push manager \"{}\".", I(pushUser.getUserId()), I(pushUser.getContextId()), pushManager, e);
                    } catch (RuntimeException e) {
                        LOG.error("Runtime error while stopping permanent push listener for user {} in context {} by push manager \"{}\".", I(pushUser.getUserId()), I(pushUser.getContextId()), pushManager, e);
                    }
                }
            }
        }

        // Park a while
        if (parkNanos > 0L) {
            LockSupport.parkNanos(parkNanos);
        }

        // Start permanent candidates
        List<PermanentListenerJob> startedOnes = new ArrayList<PermanentListenerJob>(toStart.size());
        boolean allowPermanentPush = isPermanentPushAllowedPerConfig();
        for (PushManagerExtendedService pushManager : managers) {
            List<PermanentListenerJob> started = startPermanentListenersFor(toStart, pushManager, allowPermanentPush);
            startedOnes.addAll(started);
        }
        Collections.sort(startedOnes);
        if (all) {
            allUsersStarted.set(true);
        }
        return startedOnes;
    }

    @Override
    public boolean registerPermanentListenerFor(Session session, String clientId) throws OXException {
        if (false == hasWebMailAndIsActive(session)) {
            /*
             * No "webmail" permission granted
             */
            LOG.info("Denied registration of a permanent push listener for client {} from user {} in context {}: Missing \"webmail\" permission or user is disabled.", clientId, I(session.getUserId()), I(session.getContextId()));
            return false;
        }
        if (Sessions.isGuest(session)) {
            /*
             * It's a guest
             */
            LOG.debug("Denied registration of a permanent push listener for client {} from user {} in context {}: Guest user.", session.getClient(), I(session.getUserId()), I(session.getContextId()));
            return false;
        }
        if (false == PushUtility.allowedClient(clientId, null, true)) {
            /*
             * No permanent push listener for the client.
             */
            LOG.info("Denied registration of a permanent push listener for client {} from user {} in context {}: Not allowed for specified client.", clientId, I(session.getUserId()), I(session.getContextId()));
            return false;
        }

        PermanentListenerRescheduler useThisInstanceToReschedule = null;

        int contextId = session.getContextId();
        int userId = session.getUserId();

        boolean inserted = PushDbUtils.insertPushRegistration(userId, contextId, clientId);

        // Store/update credentials
        {
            CredentialStorage credentialStorage = optCredentialStorage();
            if (null != credentialStorage) {
                if (inserted || (null == credentialStorage.getCredentials(userId, contextId))) {
                    try {
                        credentialStorage.storeCredentials(new DefaultCredentials(session));
                        LOG.info("Successfully stored/updated credentials for push user {} in context {}.", I(userId), I(contextId));
                    } catch (Exception e) {
                        LOG.error("Failed to store credentials for push user {} in context {}.", I(userId), I(contextId), e);
                    }
                }
            }
        }

        if (inserted) {
            // Start for push user
            boolean rescheduleOnRegistration = isRescheduleOnRegistration(userId, contextId);
            Optional<PermanentListenerRescheduler> optionalRescheduler = rescheduleOnRegistration ? Optional.ofNullable(reschedulerRef.get()) : Optional.empty();
            boolean allowPermanentPush = isPermanentPushAllowedPerConfig();
            Collection<PushUser> toStart = Collections.singletonList(new PushUser(userId, contextId));
            if (optionalRescheduler.isPresent()) {
                for (Iterator<PushManagerExtendedService> it = getExtendedPushManagers().iterator(); useThisInstanceToReschedule == null && it.hasNext();) {
                    PushManagerExtendedService extendedService = it.next();
                    if (extendedService.supportsPermanentListeners() && extendedService.listenersRequireResources()) {
                        useThisInstanceToReschedule = optionalRescheduler.get();
                    }
                }

                if (null != useThisInstanceToReschedule) {
                    try {
                        useThisInstanceToReschedule.planReschedule(true, new StringBuilder("Permanent listener registered for client ").append(clientId).append(" from user ").append(userId).append(" in context ").append(contextId).toString());
                    } catch (OXException e) {
                        LOG.error("Failed to plan rescheduling", e);
                    }
                } else {
                    for (PushManagerExtendedService extendedService : getExtendedPushManagers()) {
                        startPermanentListenersFor(toStart, extendedService, allowPermanentPush);
                    }
                }
            } else {
                for (PushManagerExtendedService extendedService : getExtendedPushManagers()) {
                    startPermanentListenersFor(toStart, extendedService, allowPermanentPush);
                }
            }
        }

        return inserted;
    }

    private boolean isRescheduleOnRegistration(int userId, int contextId) throws OXException {
        boolean defaultValue = false;

        ConfigViewFactory configViewFactory = services.getOptionalService(ConfigViewFactory.class);
        if (configViewFactory == null) {
            return defaultValue;
        }

        return ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.push.rescheduleOnRegistration", defaultValue, configViewFactory.getView(userId, contextId));
    }

    @Override
    public boolean unregisterPermanentListenerFor(Session session, String clientId) throws OXException {
        return unregisterPermanentListenerFor(new PushUser(session.getUserId(), session.getContextId(), Optional.of(session.getSessionID())), clientId);
    }

    @Override
    public boolean unregisterPermanentListenerFor(PushUser pushUser, String clientId) throws OXException {
        int userId = pushUser.getUserId();
        int contextId = pushUser.getContextId();
        if (!PushUtility.allowedClient(clientId, null, false)) {
            /*
             * No permanent push listener for the client.
             */
            LOG.info("Denied unregistration of a permanent push listener for client {} from user {} in context {}: Not allowed for specified client.", clientId, I(userId), I(contextId));
            return false;
        }

        DeleteResult deleteResult = PushDbUtils.deletePushRegistration(userId, contextId, clientId);

        if (DeleteResult.DELETED_COMPLETELY == deleteResult || DeleteResult.DELETED_ALL_IN_CONTEXT == deleteResult) {
            CredentialStorage credentialStorage = optCredentialStorage();
            if (null != credentialStorage) {
                try {
                    credentialStorage.deleteCredentials(userId, contextId);
                    LOG.info("Successfully deleted credentials for push user {} in context {}.", I(userId), I(contextId));
                } catch (Exception e) {
                    LOG.error("Failed to delete credentials for push user {} in context {}.", I(userId), I(contextId), e);
                }
            }

            for (PushManagerExtendedService extendedService : getExtendedPushManagers()) {
                try {
                    // Stop listener for session
                    boolean tryToReconnect = "true".equals(LogProperties.get(LogProperties.Name.PNS_NO_RECONNECT)) ? false : true;
                    StopResult stopped = stopPermanentListenerFor(pushUser, extendedService, tryToReconnect);
                    if (stopped != StopResult.NONE) {
                        LOG.debug("{} push listener for user {} in context {} by push manager \"{}\"", stopped.getWord(), I(userId), I(contextId), extendedService);
                    }
                } catch (OXException e) {
                    LOG.error("Error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), extendedService, e);
                } catch (RuntimeException e) {
                    LOG.error("Runtime error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), extendedService, e);
                }
            }
        }
        return (DeleteResult.NOT_DELETED != deleteResult);
    }

    /**
     * Unregisters all permanent listeners for specified push user
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> on successful unregistration; otherwise <code>false</code>
     * @throws OXException If unregistration fails
     */
    public boolean unregisterAllPermanentListenersFor(int userId, int contextId) throws OXException {
        DeleteResult deleteResult = PushDbUtils.deleteAllPushRegistrations(userId, contextId);
        if (DeleteResult.DELETED_COMPLETELY == deleteResult || DeleteResult.DELETED_ALL_IN_CONTEXT == deleteResult) {
            CredentialStorage credentialStorage = optCredentialStorage();
            if (null != credentialStorage) {
                try {
                    credentialStorage.deleteCredentials(userId, contextId);
                    LOG.info("Successfully deleted credentials for push user {} in context {}.", I(userId), I(contextId));
                } catch (Exception e) {
                    LOG.error("Failed to delete credentials for push user {} in context {}.", I(userId), I(contextId), e);
                }
            }

        }

        PushUser pushUser = new PushUser(userId, contextId);
        for (PushManagerExtendedService pushManager : getExtendedPushManagers()) {
            try {
                // Stop listener for specified push user
                StopResult stopped = stopPermanentListenerFor(pushUser, pushManager, true);
                if (stopped != StopResult.NONE) {
                    LOG.debug("{} push listener for user {} in context {} by push manager \"{}\"", stopped.getWord(), I(userId), I(contextId), pushManager);
                }
            } catch (OXException e) {
                LOG.error("Error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), pushManager, e);
            } catch (RuntimeException e) {
                LOG.error("Runtime error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), pushManager, e);
            }
        }
        return (DeleteResult.NOT_DELETED != deleteResult);
    }

    /**
     * Stops the permanent listener for specified push users
     *
     * @param pushUsers The push users
     */
    public void stopPermanentListenerFor(Collection<PushUser> pushUsers) {
        for (PushUser pushUser : pushUsers) {
            int userId = pushUser.getUserId();
            int contextId = pushUser.getContextId();
            for (PushManagerExtendedService pushManager : getExtendedPushManagers()) {
                try {
                    // Stop listener for specified push user
                    StopResult stopped = stopPermanentListenerFor(pushUser, pushManager, true);
                    if (stopped != StopResult.NONE) {
                        LOG.debug("{} push listener for user {} in context {} by push manager \"{}\"", stopped.getWord(), I(userId), I(contextId), pushManager);
                    }
                } catch (OXException e) {
                    LOG.error("Error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), pushManager, e);
                } catch (RuntimeException e) {
                    LOG.error("Runtime error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), pushManager, e);
                }
            }
        }
    }

    /**
     * Stops all permanent listeners.
     */
    public void stopAllPermanentListenerForReschedule() {
        for (PushManagerExtendedService pushManager : getExtendedPushManagers()) {
            PushManagerExtendedService extendedService = pushManager;
            if (extendedService.supportsPermanentListeners() && extendedService.listenersRequireResources()) {
                // Determine current push manager's listeners
                List<PushUserInfo> availablePushUsers;
                try {
                    availablePushUsers = extendedService.getAvailablePushUsers();
                } catch (OXException e) {
                    LOG.error("Error while determining available push users by push manager \"{}\".", pushManager, e);
                    availablePushUsers = Collections.emptyList();
                }

                // Stop the permanent ones
                for (PushUserInfo pushUserInfo : availablePushUsers) {
                    if (pushUserInfo.isPermanent()) {
                        int userId = pushUserInfo.getUserId();
                        int contextId = pushUserInfo.getContextId();
                        try {
                            // Stop listener for session
                            StopResult stopped = stopPermanentListenerFor(pushUserInfo.getPushUser(), extendedService, false);
                            if (stopped != StopResult.NONE) {
                                LOG.debug("{} push listener for user {} in context {} by push manager \"{}\"", stopped.getWord(), I(userId), I(contextId), pushManager);
                            }
                        } catch (OXException e) {
                            LOG.error("Error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), pushManager, e);
                        } catch (RuntimeException e) {
                            LOG.error("Runtime error while stopping push listener for user {} in context {} by push manager \"{}\".", I(userId), I(contextId), pushManager, e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<PushUser> getUsersWithPermanentListeners() throws OXException {
        return PushDbUtils.getPushRegistrations();
    }

    @Override
    public boolean hasRegistration(PushUser pushUser) throws OXException {
        return PushDbUtils.hasPushRegistration(pushUser);
    }

    @Override
    public Session generateSessionFor(PushUser pushUser) throws OXException {
        int contextId = pushUser.getContextId();
        int userId = pushUser.getUserId();

        // Generate session instance
        GeneratedSession session = new GeneratedSession(userId, contextId);

        // Get credentials
        {
            Credentials credentials = optCredentials(userId, contextId);
            if (null != credentials) {
                session.setPassword(credentials.getPassword());
                session.setLoginName(credentials.getLogin());
            }
        }

        // Password
        {
            PasswordSource passwordSource = MailProperties.getInstance().getPasswordSource(userId, contextId);
            switch (passwordSource) {
                case GLOBAL: {
                    // Just for convenience
                    String masterPassword = MailProperties.getInstance().getMasterPassword(userId, contextId);
                    if (null == masterPassword) {
                        throw PushExceptionCodes.MISSING_MASTER_PASSWORD.create();
                    }
                    session.setPassword(masterPassword);
                    break;
                }
                case SESSION:
                    // Fall-through
                default: {
                    if (null == session.getPassword()) {
                        throw PushExceptionCodes.MISSING_PASSWORD.create();
                    }
                    break;
                }
            }
        }

        // Login
        {
            String proxyDelimiter = MailProperties.getInstance().getAuthProxyDelimiter();
            if (null != proxyDelimiter && null == session.getLoginName()) {
                // Login cannot be determined
                throw PushExceptionCodes.MISSING_LOGIN_STRING.create();
            }
        }

        return session;
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    @Override
    public PushListener startListenerFor(Session session) {
        /*
         * Check session
         */
        if (false == hasWebMailAndIsActive(session)) {
            /*
             * No "webmail" permission granted
             */
            LOG.debug("Skipping registration of a mail push listener for client {} from user {} in context {}: Missing \"webmail\" permission or user is disabled.", session.getClient(), I(session.getUserId()), I(session.getContextId()));
            return null;
        }
        if (Sessions.isGuest(session)) {
            /*
             * It's a guest
             */
            LOG.debug("Skipping registration of a mail push listener for client {} from user {} in context {}: Guest user.", session.getClient(), I(session.getUserId()), I(session.getContextId()));
            return null;
        }
        if (false == PushUtility.allowedClient(session.getClient(), session, true)) {
            /*
             * No push listener for the client associated with current session.
             */
            LOG.debug("Skipping registration of a mail push listener for client {} from user {} in context {}: Not allowed for specified client.", session.getClient(), I(session.getUserId()), I(session.getContextId()));
            return null;
        }
        /*
         * Iterate push managers
         */
        for (Iterator<PushManagerService> pushManagersIterator = map.values().iterator(); pushManagersIterator.hasNext();) {
            try {
                PushManagerService pushManager = pushManagersIterator.next();

                // Initialize a new push listener for session
                PushListener pl = pushManager.startListener(session);
                if (null != pl) {
                    LOG.debug("Started mail push listener for user {} in context {} by push manager \"{}\"", I(session.getUserId()), I(session.getContextId()), pushManager);
                    return pl;
                }
            } catch (OXException e) {
                LOG.error("Error while starting mail push listener.", e);
            } catch (RuntimeException e) {
                LOG.error("Runtime error while starting mail push listener.", e);
            }
        }
        return null;
    }

    @Override
    public boolean stopListenerFor(Session session) {
        if (!PushUtility.allowedClient(session.getClient(), session, false)) {
            /*
             * No push listener for the client associated with current session.
             */
            LOG.debug("Denied unregistration of a push listener for client {} from user {} in context {}: Not allowed for specified client.", session.getClient(), I(session.getUserId()), I(session.getContextId()));
            return false;
        }
        /*
         * Iterate push managers
         */
        for (Iterator<PushManagerService> pushManagersIterator = map.values().iterator(); pushManagersIterator.hasNext();) {
            try {
                PushManagerService pushManager = pushManagersIterator.next();
                // Stop listener for session
                boolean stopped = pushManager.stopListener(session);
                if (stopped) {
                    LOG.debug("Stopped push listener for user {} in context {} by push manager \"{}\"", I(session.getUserId()), I(session.getContextId()), pushManager);
                    return true;
                }
            } catch (OXException e) {
                LOG.error("Error while stopping push listener.", e);
            } catch (RuntimeException e) {
                LOG.error("Runtime error while stopping push listener.", e);
            }
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    /**
     * Adds specified push manager service.
     *
     * @param pushManager The push manager service to add
     * @return <code>true</code> if push manager service could be successfully added; otherwise <code>false</code>
     */
    public boolean addPushManager(PushManagerService pushManager) {
        boolean added = (null == map.putIfAbsent(pushManager.getClass(), pushManager));

        if (added && (pushManager instanceof PushManagerExtendedService)) {
            synchronized (this) {
                startPermanentListenersFor(initialPushUsers, (PushManagerExtendedService) pushManager, isPermanentPushAllowedPerConfig());
            }
        }

        return added;
    }

    /**
     * Removes specified push manager service.
     *
     * @param pushManager The push manager service to remove
     */
    public void removePushManager(PushManagerService pushManager) {
        map.remove(pushManager.getClass());
    }

    /**
     * Gets a read-only {@link Iterator iterator} over the push managers in this registry.
     * <p>
     * Invoking {@link Iterator#remove() remove} will throw an {@link UnsupportedOperationException}.
     *
     * @return A read-only {@link Iterator iterator} over the push managers in this registry.
     */
    public Iterator<PushManagerService> getPushManagers() {
        return unmodifiableIterator(map.values().iterator());
    }

    // -----------------------------------------------------------------------------------------------------------------------------

    /**
     * Strips the <tt>remove()</tt> functionality from an existing iterator.
     * <p>
     * Wraps the supplied iterator into a new one that will always throw an <tt>UnsupportedOperationException</tt> if its <tt>remove()</tt>
     * method is called.
     *
     * @param iterator The iterator to turn into an unmodifiable iterator.
     * @return An iterator with no remove functionality.
     */
    private static <T> Iterator<T> unmodifiableIterator(final Iterator<T> iterator) {
        if (iterator == null) {
            @SuppressWarnings("unchecked") final Iterator<T> empty = EMPTY_ITER;
            return empty;
        }

        return new Iterator<T>() {

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() throws NoSuchElementException {
                return iterator.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @SuppressWarnings("rawtypes")
    private static final Iterator EMPTY_ITER = new Iterator() {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    };

    private static enum StopResult {
        NONE("None"),
        STOPPED("Stopped"),
        CANCELED("Canceled");

        private final String word;

        StopResult(String word) {
            this.word = word;
        }

        String getWord() {
            return word;
        }
    }

    private static Integer I(int i) {
        return Integer.valueOf(i);
    }

}
