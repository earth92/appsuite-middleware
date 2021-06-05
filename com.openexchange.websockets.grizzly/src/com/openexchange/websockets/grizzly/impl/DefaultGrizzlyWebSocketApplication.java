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

package com.openexchange.websockets.grizzly.impl;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.util.Parameters;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.slf4j.Logger;
import com.openexchange.exception.OXException;
import com.openexchange.http.grizzly.GrizzlyConfig;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.UserAndContext;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.websockets.ConnectionId;
import com.openexchange.websockets.WebSocketInfo;
import com.openexchange.websockets.WebSockets;
import com.openexchange.websockets.grizzly.AbstractGrizzlyWebSocketApplication;
import com.openexchange.websockets.grizzly.GrizzlyWebSocketUtils;
import com.openexchange.websockets.grizzly.remote.RemoteWebSocketDistributor;

/**
 * {@link DefaultGrizzlyWebSocketApplication}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class DefaultGrizzlyWebSocketApplication extends AbstractGrizzlyWebSocketApplication<DefaultSessionBoundWebSocket> {

    private static final Logger WS_LOGGER = org.slf4j.LoggerFactory.getLogger("WEBSOCKET");

    private static final AtomicReference<DefaultGrizzlyWebSocketApplication> APPLICATION_REFERENCE = new AtomicReference<DefaultGrizzlyWebSocketApplication>();

    /**
     * Initializes a new {@link DefaultGrizzlyWebSocketApplication} instance (if not already performed).
     *
     * @param listenerRegistry The listern registry to use
     * @param remoteDistributor The remote distributor to manage remote Web Sockets
     * @param services The service look-up
     * @return The newly created (or existing) instance
     */
    public static DefaultGrizzlyWebSocketApplication initializeGrizzlyWebSocketApplication(WebSocketListenerRegistry listenerRegistry, RemoteWebSocketDistributor remoteDistributor, GrizzlyConfig config, ServiceLookup services) {
        DefaultGrizzlyWebSocketApplication app;
        DefaultGrizzlyWebSocketApplication newApp;
        do {
            app = APPLICATION_REFERENCE.get();
            if (null != app) {
                return app;
            }
            newApp = new DefaultGrizzlyWebSocketApplication(listenerRegistry, remoteDistributor, config, services);
        } while (!APPLICATION_REFERENCE.compareAndSet(app, newApp));
        return newApp;
    }

    /**
     * Unsets the application
     */
    public static void unsetGrizzlyWebSocketApplication() {
        APPLICATION_REFERENCE.set(null);
    }

    /**
     * Gets the application
     *
     * @return The application or <code>null</code>
     */
    public static DefaultGrizzlyWebSocketApplication getGrizzlyWebSocketApplication() {
        return APPLICATION_REFERENCE.get();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private final WebSocketListenerRegistry listenerRegistry;
    private final RemoteWebSocketDistributor remoteDistributor;

    private final GrizzlyConfig config;

    /**
     * Initializes a new {@link DefaultGrizzlyWebSocketApplication}.
     */
    private DefaultGrizzlyWebSocketApplication(WebSocketListenerRegistry listenerRegistry, RemoteWebSocketDistributor remoteDistributor, GrizzlyConfig config, ServiceLookup services) {
        super(services, DefaultSessionBoundWebSocket.class);
        this.listenerRegistry = listenerRegistry;
        this.remoteDistributor = remoteDistributor;
        this.config = config;
    }

    /**
     * Lists all currently locally available Web Sockets.
     *
     * @return Locally available Web Sockets
     */
    public List<com.openexchange.websockets.WebSocket> listLocalWebSockets() {
        List<com.openexchange.websockets.WebSocket> websockets = new LinkedList<>();
        for (ConcurrentMap<ConnectionId, DefaultSessionBoundWebSocket> userSockets : openSockets.values()) {
            for (DefaultSessionBoundWebSocket sessionBoundSocket : userSockets.values()) {
                websockets.add(sessionBoundSocket);
            }
        }
        return websockets;
    }

    /**
     * Lists all locally available Web Socket information.
     *
     * @return All available Web Socket information
     */
    public List<WebSocketInfo> listWebSocketInfo() {
        // Only locally available...
        List<WebSocketInfo> infos = new LinkedList<>();
        for (ConcurrentMap<ConnectionId, DefaultSessionBoundWebSocket> userSockets : openSockets.values()) {
            for (DefaultSessionBoundWebSocket sessionBoundSocket : userSockets.values()) {
                WebSocketInfo info = WebSocketInfo.builder()
                    .connectionId(sessionBoundSocket.getConnectionId())
                    .contextId(sessionBoundSocket.getContextId())
                    .address(getLocalHost())
                    .path(sessionBoundSocket.getPath())
                    .userId(sessionBoundSocket.getUserId())
                    .build();
                infos.add(info);
            }
        }
        return infos;
    }

    /**
     * Removes all existing Web Socket connections from given candidates and retains the non-existing ones.
     *
     * @param candidates The candidates to remove from
     * @param userId The user identifier
     * @param contextId The context identifier
     */
    public void retainNonExisting(Set<ConnectionId> candidates, int userId, int contextId) {
        if (null == candidates) {
            return;
        }

        ConcurrentMap<ConnectionId, DefaultSessionBoundWebSocket> userSockets = openSockets.get(UserAndContext.newInstance(userId, contextId));
        if (null == userSockets || userSockets.isEmpty()) {
            // None exists
            return;
        }

        for (DefaultSessionBoundWebSocket sessionBoundSocket : userSockets.values()) {
            candidates.remove(sessionBoundSocket.getConnectionId());
        }
    }

    /**
     * Checks if there is any open Web Socket associated with specified user.
     *
     * @param pathFilter The path filter expression or <code>null</code>
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if any open Web Socket exists for given user; otherwise <code>false</code>
     */
    public boolean existsAny(String pathFilter, int userId, int contextId) {
        ConcurrentMap<ConnectionId, DefaultSessionBoundWebSocket> userSockets = openSockets.get(UserAndContext.newInstance(userId, contextId));
        if (null == userSockets || userSockets.isEmpty()) {
            // No socket at all
            WS_LOGGER.debug("Found no local Web Sockets for user {} in context {}", I(userId), I(contextId));
            return false;
        }

        if (null == pathFilter) {
            // No filter given
            WS_LOGGER.debug("Found local Web Socket for user {} in context {}", I(userId), I(contextId));
            return true;
        }

        // Check if any satisfies given filter
        List<DefaultSessionBoundWebSocket> sockets = new ArrayList<>(userSockets.values());
        for (DefaultSessionBoundWebSocket sessionBoundSocket : sockets) {
            if (WebSockets.matches(pathFilter, sessionBoundSocket.getPath())) {
                WS_LOGGER.debug("Found local Web Socket for user {} in context {} matching filter \"{}\"", I(userId), I(contextId), pathFilter);
                return true;
            }
        }

        WS_LOGGER.debug("Found no local Web Socket for user {} in context {} matching filter \"{}\". Available Web Sockets: {}", I(userId), I(contextId), pathFilter, sockets);
        return false;
    }

    @Override
    protected void closeSocketSafe(DefaultSessionBoundWebSocket webSocket) {
        WebSocketInfo socketInfo = WebSocketInfo.builder().connectionId(webSocket.getConnectionId()).contextId(webSocket.getContextId()).path(webSocket.getPath()).userId(webSocket.getUserId()).build();
        ThreadPools.submitElseExecute(new RemoteWebSocketModifierTask(socketInfo, remoteDistributor, false));
        super.closeSocketSafe(webSocket);
    }

    /**
     * Asynchronously sends specified text message to all locally managed Web Socket connections.
     *
     * @param message The text message to send
     * @param sourceToken The push token of the client triggering the update, or <code>null</code> if not available
     * @param pathFilter The optional path to filter by (e.g. <code>"/websockets/push"</code>)
     * @param remote Whether the text message was remotely received; otherwise <code>false</code> for local origin
     * @param userId The user identifier
     * @param contextId The context identifier
     */
    public Future<Void> sendToUserAsync(String message, String sourceToken, String pathFilter, boolean remote, int userId, int contextId) {
        SendToUserTask task = new SendToUserTask(message, sourceToken, pathFilter, remote, userId, contextId, this);
        return ThreadPools.submitElseExecute(task);
    }

    /**
     * Sends specified text message to all locally managed Web Socket connections. Connections whose identifier matches the optional
     * source token are excluded implicitly.
     *
     * @param message The text message to send
     * @param sourceToken The push token of the client triggering the update, or <code>null</code> if not available
     * @param pathFilter The optional path to filter by (e.g. <code>"/websockets/push"</code>)
     * @param remote Whether the text message was remotely received; otherwise <code>false</code> for local origin
     * @param userId The user identifier
     * @param contextId The context identifier
     */
    public void sendToUser(String message, String sourceToken, String pathFilter, boolean remote, int userId, int contextId) {
        String info = remote ? "remotely received" : "locally created";

        ConcurrentMap<ConnectionId, DefaultSessionBoundWebSocket> userSockets = openSockets.get(UserAndContext.newInstance(userId, contextId));
        if (null == userSockets || userSockets.isEmpty()) {
            WS_LOGGER.debug("Found no local Web Sockets to send {} message \"{}\" to user {} in context {}", info, GrizzlyWebSocketUtils.abbreviateMessageArg(message), I(userId), I(contextId));
            return;
        }

        boolean any = false;
        for (DefaultSessionBoundWebSocket sessionBoundSocket : userSockets.values()) {
            if (WebSockets.matchesToken(sessionBoundSocket, sourceToken)) {
                WS_LOGGER.debug("Skipping transport of {} message to web socket with matching connection id {}", info, sessionBoundSocket.getConnectionId());
                continue;
            }
            if (WebSockets.matches(pathFilter, sessionBoundSocket.getPath())) {
                any = true;
                try {
                    sessionBoundSocket.sendMessage(message);
                    WS_LOGGER.debug("Sent {} message \"{}\" via Web Socket ({}) using path filter \"{}\" to user {} in context {}", info, GrizzlyWebSocketUtils.abbreviateMessageArg(message), sessionBoundSocket.getConnectionId(), pathFilter, I(userId), I(contextId));
                } catch (OXException e) {
                    WS_LOGGER.debug("Failed to send {} message to Web Socket: {}", info, sessionBoundSocket, e);
                }
            }
        }

        if (!any) {
            WS_LOGGER.debug("Found no matching local Web Socket to send {} message \"{}\" using path filter \"{}\" to user {} in context {}", info, GrizzlyWebSocketUtils.abbreviateMessageArg(message), pathFilter, I(userId), I(contextId));
        }
    }

    // ------------------------------------------------------ Methods from WebSocketApplication ------------------------------------------------------

    @Override
    protected DefaultSessionBoundWebSocket doCreateSocket(ConnectionId connectionId, Parameters parameters, ProtocolHandler handler, HttpRequestPacket requestPacket, WebSocketListener[] listeners) {
        // Apply initial listeners
        WebSocketListener[] effectiveListeners;
        {
            List<WebSocketListener> listenersToUse = new LinkedList<>();
            if (null != listeners) {
                for (WebSocketListener listener : listeners) {
                    if (null != listener) {
                        listenersToUse.add(listener);
                    }
                }
            }
            for (WebSocketListener grizzlyWebSocketListener : listenerRegistry.getListeners()) {
                if (grizzlyWebSocketListener instanceof IndividualWebSocketListenerAdapter) {
                    // Pass individual instance
                    listenersToUse.add(((IndividualWebSocketListenerAdapter) grizzlyWebSocketListener).newAdapter());
                } else {
                    listenersToUse.add(grizzlyWebSocketListener);
                }
            }
            effectiveListeners = listenersToUse.toArray(new WebSocketListener[listenersToUse.size()]);
        }

        // Create & return new session-bound Web Socket
        String path = requestPacket.getRequestURI();
        return new DefaultSessionBoundWebSocket(connectionId, path, parameters, handler, requestPacket, config, effectiveListeners);
    }

    private static final int MAX_SIZE = 8;

    @Override
    protected int getMaxSize() {
        return MAX_SIZE;
    }

    @Override
    protected void onConnectedSocket(DefaultSessionBoundWebSocket sessionBoundSocket) {
        sessionBoundSocket.addListenersIfAbsent(listenerRegistry.getListeners());

        WebSocketInfo socketInfo = WebSocketInfo.builder().connectionId(sessionBoundSocket.getConnectionId()).contextId(sessionBoundSocket.getContextId()).path(sessionBoundSocket.getPath()).userId(sessionBoundSocket.getUserId()).build();
        ThreadPools.submitElseExecute(new RemoteWebSocketModifierTask(socketInfo, remoteDistributor, true));
    }

    @Override
    protected void onClosedSocket(DefaultSessionBoundWebSocket sessionBoundSocket) {
        // Nothing
    }

    // ----------------------------------------------- Listener management -------------------------------------------------

    /**
     * Adds specified listener to existing Web Sockets
     *
     * @param grizzlyWebSocketListener The listener to add
     */
    public void addWebSocketListener(WebSocketListener grizzlyWebSocketListener) {
        for (ConcurrentMap<ConnectionId, DefaultSessionBoundWebSocket> userSockets : openSockets.values()) {
            for (DefaultSessionBoundWebSocket sessionBoundSocket : userSockets.values()) {
                sessionBoundSocket.addListenerIfAbsent(grizzlyWebSocketListener);
            }
        }
    }

    /**
     * Removes specified listener from existing Web Sockets
     *
     * @param grizzlyWebSocketListener The listener to remove
     */
    public void removeWebSocketListener(WebSocketListener grizzlyWebSocketListener) {
        for (ConcurrentMap<ConnectionId, DefaultSessionBoundWebSocket> userSockets : openSockets.values()) {
            for (DefaultSessionBoundWebSocket sessionBoundSocket : userSockets.values()) {
                sessionBoundSocket.removeListener(grizzlyWebSocketListener);
            }
        }
    }

    // -------------------------------------------- Helper classes ----------------------------------------------------------

    private static final class RemoteWebSocketModifierTask extends AbstractTask<Void> {

        private final WebSocketInfo socketInfo;
        private final RemoteWebSocketDistributor remoteDistributor;
        private final boolean add;

        RemoteWebSocketModifierTask(WebSocketInfo socketInfo, RemoteWebSocketDistributor remoteDistributor, boolean add) {
            super();
            this.socketInfo = socketInfo;
            this.remoteDistributor = remoteDistributor;
            this.add = add;
        }

        @Override
        public Void call() throws Exception {
            if (add) {
                remoteDistributor.addWebSocket(socketInfo);
            } else {
                remoteDistributor.removeWebSocket(socketInfo);
            }
            return null;
        }
    }

}
