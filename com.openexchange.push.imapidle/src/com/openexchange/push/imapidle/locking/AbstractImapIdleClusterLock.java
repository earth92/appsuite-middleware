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

package com.openexchange.push.imapidle.locking;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.openexchange.hazelcast.Hazelcasts;
import com.openexchange.server.ServiceLookup;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.sessionstorage.hazelcast.serialization.PortableSessionExistenceCheck;

/**
 * {@link AbstractImapIdleClusterLock}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class AbstractImapIdleClusterLock implements ImapIdleClusterLock {

    /** The time-to-live in cache; cluster lock timeout minus 5 minutes */
    private static final long CACHE_EXPIRATION = ImapIdleClusterLock.TIMEOUT_MILLIS - 300000L;

    static enum Validity {
        VALID, TIMED_OUT, NO_SUCH_SESSION;
    }

    /** The arguments to control validation of session existence */
    static class SessionValidationArgs {

        static class Builder {

            private boolean validateSessionExistence;
            private boolean tranzient;
            private HazelcastInstance hzInstance;

            Builder() {
                super();
            }

            Builder withValidateSessionExistence(boolean validateSessionExistence) {
                this.validateSessionExistence = validateSessionExistence;
                return this;
            }

            Builder withTranzient(boolean tranzient) {
                this.tranzient = tranzient;
                return this;
            }

            Builder withHzInstance(HazelcastInstance hzInstance) {
                this.hzInstance = hzInstance;
                return this;
            }

            SessionValidationArgs build() {
                return new SessionValidationArgs(validateSessionExistence, tranzient, hzInstance);
            }
        }

        /** The constant for <b>no(!)</b> validation of session existence */
        static final SessionValidationArgs NO_SESSION_EXISTENCE_VALIDATION = new Builder().withValidateSessionExistence(false).build();

        /** <code>true</code> to perform validation of session existence; otherwise <code>false</code> to discard validation */
        final boolean validateSessionExistence;

        /** <code>true</code> if session is not supposed to be held in session storage; otherwise <code>false</code> */
        final boolean tranzient;

        /** The Hazelcast instance */
        final HazelcastInstance hzInstance;

        /**
         * Initializes a new {@link SessionValidationArds}.
         */
        SessionValidationArgs(boolean validateSessionExistence, boolean tranzient, HazelcastInstance hzInstance) {
            super();
            this.validateSessionExistence = validateSessionExistence;
            this.tranzient = tranzient;
            this.hzInstance = hzInstance;
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    /** The service look-up */
    protected final ServiceLookup services;

    /** <code>true</code> to perform validation of session existence; otherwise <code>false</code> to discard validation */
    protected final boolean validateSessionExistence;

    /**
     * A volatile cache for remote look-ups of transient sessions
     * <p>
     * This should prevent from hammering Hazelcast service with many subsequent logins
     */
    private final Cache<String, Boolean> remoteLookUps;

    /**
     * Initializes a new {@link AbstractImapIdleClusterLock}.
     */
    protected AbstractImapIdleClusterLock(boolean validateSessionExistence, ServiceLookup services) {
        super();
        this.validateSessionExistence = validateSessionExistence;
        this.services = services;
        remoteLookUps = CacheBuilder.newBuilder().initialCapacity(512).expireAfterWrite(CACHE_EXPIRATION, TimeUnit.MILLISECONDS).build();
    }

    /**
     * Gets the appropriate validation arguments.
     *
     * @param sessionInfo The session info
     * @param hzInstance The Hazelcast instance to use
     * @return The validation arguments
     */
    protected SessionValidationArgs getValidationArgs(SessionInfo sessionInfo, HazelcastInstance hzInstance) {
        return validateSessionExistence ? new SessionValidationArgs.Builder().withValidateSessionExistence(true).withTranzient(sessionInfo.isTransient()).withHzInstance(hzInstance).build() : SessionValidationArgs.NO_SESSION_EXISTENCE_VALIDATION;
    }

    /**
     * Generate an appropriate value for given time stamp and session identifier pair
     *
     * @param millis The time stamp
     * @param sessionInfo The session info
     * @return The value
     */
    protected String generateValue(long millis, SessionInfo sessionInfo) {
        if (sessionInfo.isPermanent()) {
            return Long.toString(millis);
        }
        return new StringBuilder(32).append(millis).append('?').append(sessionInfo.getSessionId()).toString();
    }

    /**
     * Checks validity of passed value in comparison to given time stamp (and session).
     *
     * @param value The value to check
     * @param now The current time stamp
     * @param validationArguments The arguments to control validation of session existence
     * @return <code>true</code> if valid; otherwise <code>false</code>
     */
    protected Validity validateValue(String value, long now, SessionValidationArgs validationArguments) {
        if (now - parseMillisFromValue(value) > TIMEOUT_MILLIS) {
            return Validity.TIMED_OUT;
        }

        if (validationArguments.validateSessionExistence && (false == existsSessionFromValue(value, validationArguments.tranzient, validationArguments.hzInstance))) {
            // Session could not be found
            return Validity.NO_SUCH_SESSION;
        }

        return Validity.VALID;
    }

    /**
     * Parses the time stamp milliseconds from given value
     *
     * @param value The value
     * @return The milliseconds
     */
    private long parseMillisFromValue(String value) {
        int pos = value.indexOf('?');
        return Long.parseLong(pos > 0 ? value.substring(0, pos) : value);
    }

    /**
     * Checks if the session referenced by given value does still exists
     *
     * @param value The value
     * @param tranzient <code>true</code> if session is not supposed to be held in session storage; otherwise <code>false</code>
     * @param hzInstance The Hazelcast instance
     * @return <code>true</code> if session still exists; otherwise <code>false</code>
     */
    private boolean existsSessionFromValue(String value, boolean tranzient, HazelcastInstance hzInstance) {
        int pos = value.indexOf('?');
        if (pos < 0) {
            // Value from a permanent listener - Always true
            return true;
        }

        // Check regular session service (but might yield negative result in case of a "transient" session)
        String sessionId = value.substring(pos + 1);
        {
            SessiondService sessiondService = services.getService(SessiondService.class);
            if (null != sessiondService) {
                if (tranzient) {
                    // Can only "live" node-local; either on this node or a remote one. Thus checking session storage makes no sense.
                    if (sessiondService.peekSession(sessionId, false) != null) {
                        // On this node
                        return true;
                    }

                    // Need to check remote ones
                    Boolean existsRemotely = remoteLookUps.getIfPresent(sessionId);
                    if (null != existsRemotely) {
                        return existsRemotely.booleanValue();
                    }

                    if (null == hzInstance) {
                        // Cannot check remotely. Assume it does exist
                        return true;
                    }

                    // Perform the remote look-up
                    existsRemotely = Boolean.valueOf(checkRemoteMembersIfSessionExists(sessionId, hzInstance));

                    // Only cache positive look-up result as value will be replaced in consequence of failed validity check
                    if (existsRemotely.booleanValue()) {
                        remoteLookUps.put(sessionId, existsRemotely);
                    }
                    return existsRemotely.booleanValue();
                }

                // A non-transient session (which should be available in session storage)
                if (sessiondService.getSession(sessionId) != null) {
                    return true;
                }
            }
        }

        // As last resort: check each member for session existence
        if (null == hzInstance) {
            return false;
        }
        return checkRemoteMembersIfSessionExists(sessionId, hzInstance);
    }

    private boolean checkRemoteMembersIfSessionExists(String sessionId, HazelcastInstance hzInstance) {
        // Determine other cluster members
        Set<Member> otherMembers = Hazelcasts.getRemoteMembers(hzInstance);

        if (!otherMembers.isEmpty()) {
            Hazelcasts.Filter<Boolean, Boolean> filter = new Hazelcasts.Filter<Boolean, Boolean>() {

                @Override
                public Boolean accept(Boolean result) {
                    return result.booleanValue() ? Boolean.TRUE : null;
                }
            };
            IExecutorService executor = hzInstance.getExecutorService("default");
            try {
                Boolean exists = Hazelcasts.executeByMembersAndFilter(new PortableSessionExistenceCheck(sessionId), otherMembers, executor, filter);
                return null != exists && exists.booleanValue();
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

        return false;
    }

}
