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

package com.openexchange.sessiond;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.osgi.annotation.SingletonService;
import com.openexchange.session.Session;
import com.openexchange.session.SessionAttributes;

/**
 * {@link SessiondService} - The SessionD service.
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@SingletonService
public interface SessiondService {

    /**
     * The reference to {@link SessiondService} instance.
     */
    public static final AtomicReference<SessiondService> SERVICE_REFERENCE = new AtomicReference<SessiondService>();

    /**
     * Creates a new session object in the SessionD storage with the given session parameters.
     * <p>
     *
     * @param parameterObject The parameter object describing the session to create
     * @return The session object interface of the newly created session.
     * @throws OXException If creating the session fails
     */
    Session addSession(AddSessionParameter parameterObject) throws OXException;

    /**
     * Stores the session associated with given identifier into session storage (if such a session exists).
     *
     * @param sessionId The session identifier
     * @return <code>true</code> if stored; otherwise <code>false</code>
     * @throws OXException If storing the session fails
     */
    boolean storeSession(String sessionId) throws OXException;

    /**
     * Stores the session associated with given identifier into session storage (if such a session exists).
     *
     * @param sessionId The session identifier
     * @param addIfAbsent Adds the session to storage only if absent
     * @return <code>true</code> if stored; otherwise <code>false</code>
     * @throws OXException If storing the session fails
     */
    boolean storeSession(String sessionId, boolean addIfAbsent) throws OXException;

    /**
     * Replaces the currently stored password in session identified through given session identifier with specified <code>newPassword</code>.
     *
     * @param sessionId The session identifier
     * @param newPassword The new password to apply
     * @throws OXException If new password cannot be applied or corresponding session does not exist or is expired
     */
    void changeSessionPassword(String sessionId, String newPassword) throws OXException;

    /**
     * Removes the session with the given session identifier.
     *
     * @param sessionId The Session identifier
     * @return <code>true</code> if the session was removed or <code>false</code> if the session identifier doesn't exist
     */
    boolean removeSession(String sessionId);

    /**
     * Removes all sessions belonging to given user in specified context.
     *
     * @param userId The user identifier
     * @param ctx The context
     * @return The number of removed session or zero if no session was removed
     */
    int removeUserSessions(int userId, Context ctx);

    /**
     * Removes all sessions belonging to given context.
     *
     * @param contextId The context identifier
     */
    void removeContextSessions(int contextId);

    /**
     * Removes all sessions belonging to given contexts from this and all other cluster nodes.
     *
     * @param contextIds - Set with the context identifiers to remove sessions for
     * @throws OXException - if removing session fails on one of the remote nodes
     */
    void removeContextSessionsGlobal(Set<Integer> contextIds) throws OXException;

    /**
     * Removes all sessions belonging to given contexts from this and all other cluster nodes.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @throws OXException If removing session fails on one of the remote nodes
     */
    void removeUserSessionsGlobally(int userId, int contextId) throws OXException;

    /**
     * Removes all sessions which match the given {@link SessionFilter}. The filter is matched against all sessions
     * on the local node.
     *
     * @param filter The filter
     * @return The IDs of the removed sessions, possibly empty but never <code>null</code>
     * @throws OXException If an error occurs while removing
     */
    Collection<String> removeSessions(SessionFilter filter) throws OXException;

    /**
     * Removes all sessions which match the given {@link SessionFilter}. The filter is matched against all sessions in the
     * (hazelcast-)cluster.
     *
     * @param filter The filter
     * @return The IDs of the removed sessions, possibly empty but never <code>null</code>
     * @throws OXException If an error occurs while removing
     */
    Collection<String> removeSessionsGlobally(SessionFilter filter) throws OXException;

    /**
     * Gets the number of active sessions belonging to given user in specified context.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The number of active sessions belonging to given user in specified context
     */
    int getUserSessions(int userId, int contextId);

    /**
     * Gets the <b>local-only</b> sessions associated with specified user in given context.
     * <p>
     * <b>Note</b>: Remote sessions are not considered by this method.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The <b>local-only</b> sessions associated with specified user in given context
     */
    Collection<Session> getSessions(int userId, int contextId);

    /**
     * Gets the sessions associated with specified user in given context.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param considerSessionStorage <code>true</code> to also consider session storage; otherwise <code>false</code>
     * @return The <b>local-only</b> sessions associated with specified user in given context
     */
    Collection<Session> getSessions(int userId, int contextId, boolean considerSessionStorage);

    /**
     * Finds the first session of the specified user that matches the give criterion.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param matcher The session matcher
     * @return The first matching session or <code>null</code> if none matches
     */
    Session findFirstMatchingSessionForUser(int userId, int contextId, SessionMatcher matcher);

    /**
     * Get the session object related to the given session identifier.
     *
     * @param sessionId The Session identifier
     * @return Returns the session or <code>null</code> if no session exists for the given identifier or if the session is expired
     * @see SessiondServiceExtended#getSession(String, boolean)
     */
    Session getSession(String sessionId);

    /**
     * Get the session object related to the given session identifier.
     *
     * @param sessionId The session identifier
     * @param considerSessionStorage <code>true</code> to consider session storage for possible distributed session; otherwise
     *            <code>false</code>
     * @return Returns the session or <code>null</code> if no session exists for the given identifier or if the session is expired
     */
    Session getSession(String sessionId, boolean considerSessionStorage);

    /**
     * Get the session object related to the given session identifier.
     * <p>
     * If session has been fetched from session storage, that session will not be added to local SessionD. Moreover, session will not be
     * moved to first session container.
     *
     * @param sessionId The session identifier
     * @return Returns the session or <code>null</code> if no session exists for the given identifier or if the session is expired
     * @see SessiondServiceExtended#getSession(String, boolean)
     */
    Session peekSession(String sessionId);

    /**
     * Get the session object related to the given session identifier.
     * <p>
     * If session has been fetched from session storage, that session will not be added to local SessionD.. Moreover, session will not be
     * moved to first session container.
     *
     * @param sessionId The session identifier
     * @param considerSessionStorage <code>true</code> to consider session storage for possible distributed session; otherwise
     *            <code>false</code>
     * @return Returns the session or <code>null</code> if no session exists for the given identifier or if the session is expired
     * @see SessiondServiceExtended#getSession(String, boolean)
     */
    Session peekSession(String sessionId, boolean considerSessionStorage);

    /**
     * Get the session object related to the given alternative identifier.
     *
     * @param altId The alternative identifier
     * @return Return the session object or null if no session exists for the given alternative identifier or if the session is expired
     */
    Session getSessionByAlternativeId(String altId);

    /**
     * Get the session object related to the given alternative identifier.
     *
     * @param altId The alternative identifier
     * @param lookupSessionStorage Whether to allow to look-up session storage, too
     * @return Return the session object or null if no session exists for the given alternative identifier or if the session is expired
     */
    Session getSessionByAlternativeId(String altId, boolean lookupSessionStorage);

    /**
     * Get the session object related to the given random token.
     *
     * @param randomToken The random token of the session
     * @param localIp The new local IP to apply to session; pass <code>null</code> to not replace existing IP in session
     * @return The session object or <code>null</code> if no session exists for the given random token or if the random token is already expired
     */
    Session getSessionByRandomToken(String randomToken, String localIp);

    /**
     * Get the session object related to the given random token.
     *
     * @param randomToken The random token of the session
     * @return The session object or <code>null</code> if no session exists for the given random token or if the random token is already expired
     */
    Session getSessionByRandomToken(String randomToken);

    /**
     * Picks up the session associated with the given client and server token. If a session exists for the given tokens and both tokens
     * match, the session object is put into the normal session container and into the session storage. It is removed from the session
     * container with tokens so a second request with the same tokens will fail.
     *
     * @param clientToken Client side token passed within the {@link #addSession(AddSessionParameter)} call.
     * @param serverToken Server side token returned inside the session from the {@link #addSession(AddSessionParameter)} call.
     * @return the matching session
     * @throws OXException if one of the tokens does not match.
     */
    Session getSessionWithTokens(String clientToken, String serverToken) throws OXException;

    /**
     * Returns all session IDs whose sessions match the given {@link SessionFilter}. The filter is applied to all sessions maintained in
     * <b>local</b> containers.
     *
     * @param filter The filter
     * @return The IDs of the found sessions, possibly empty but never <code>null</code>
     * @throws OXException If an error occurs while filtering
     */
    Collection<String> findSessions(SessionFilter filter) throws OXException;

    /**
     * Returns all session IDs whose sessions match the given {@link SessionFilter}. The filter is applied to all sessions in the
     * (hazelcast-)cluster.
     *
     * @param filter The filter
     * @return The IDs of the found sessions, possibly empty but never <code>null</code>
     * @throws OXException If an error occurs while filtering
     */
    Collection<String> findSessionsGlobally(SessionFilter filter) throws OXException;

    /**
     * Gets the number of active sessions.
     *
     * @return The number of active sessions
     */
    int getNumberOfActiveSessions();

    /**
     * Gets the first session that matches the given userId and contextId.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @deprecated Subject for being removed in future versions
     */
    @Deprecated
    Session getAnyActiveSessionForUser(int userId, int contextId);

    /**
     * Applies given attributes to denoted session.
     *
     * @param sessionId The session identifier
     * @param attrs The attributes to set
     * @throws OXException If arguments cannot be set
     */
    void setSessionAttributes(String sessionId, SessionAttributes attrs) throws OXException;

}
