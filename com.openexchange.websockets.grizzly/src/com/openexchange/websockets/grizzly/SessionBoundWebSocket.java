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

package com.openexchange.websockets.grizzly;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.http.grizzly.GrizzlyConfig;
import com.openexchange.net.IPTools;
import com.openexchange.session.Session;
import com.openexchange.websockets.ConnectionId;
import com.openexchange.websockets.IndividualWebSocketListener;
import com.openexchange.websockets.grizzly.http.WebsocketServletRequestWrapper;
import com.openexchange.websockets.grizzly.impl.IndividualWebSocketListenerAdapter;
import com.openexchange.websockets.grizzly.impl.WebSocketListenerAdapter;

/**
 * {@link SessionBoundWebSocket} - The Web Socket bound to a certain session.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class SessionBoundWebSocket extends DefaultWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(SessionBoundWebSocket.class);

    private final AtomicReference<SessionInfo> sessionInfoReference;
    private final ConnectionId connectionId;
    private final String path;
    private final HttpServletRequest wrappedRequest;
    private final GrizzlyConfig config;
    private final Map<Class<? extends IndividualWebSocketListener>, WebSocketListenerAdapter> individualWebSocketListeners;

    /**
     * Initializes a new {@link SessionBoundWebSocket}.
     */
    public SessionBoundWebSocket(ConnectionId connectionId, String path, ProtocolHandler protocolHandler, HttpRequestPacket request, GrizzlyConfig config, WebSocketListener... listeners) {
        super(protocolHandler, request, listeners);
        this.sessionInfoReference = new AtomicReference<SessionInfo>(null);
        this.connectionId = connectionId;
        this.path = path;
        this.config = config;
        this.wrappedRequest = buildHttpServletRequestWrapper(this.servletRequest);
        individualWebSocketListeners = initIndividualListenerState(getListeners());
        LOG.debug("Initialized SessionBoundWebSocket '{}' with listeners: {}", connectionId, getListeners());
    }

    /**
     * Checks all initial listeners whether they are {@link IndividualWebSocketListenerAdapter}s and if so,
     * remembers them within a map by their {@link com.openexchange.websockets.WebSocketListener} runtime class.
     *
     * @return The map
     */
    private static Map<Class<? extends IndividualWebSocketListener>, WebSocketListenerAdapter> initIndividualListenerState(Collection<WebSocketListener> listeners) {
        Map<Class<? extends IndividualWebSocketListener>, WebSocketListenerAdapter> map = new HashMap<>(8);
        for (WebSocketListener l : listeners) {
            if (l instanceof IndividualWebSocketListenerAdapter) {
                IndividualWebSocketListenerAdapter il = (IndividualWebSocketListenerAdapter) l;
                map.put(il.getListenerClass(), il);
            } else if (l instanceof WebSocketListenerAdapter) {
                WebSocketListenerAdapter la = (WebSocketListenerAdapter) l;
                if (la.isIndividualInstance()) {
                    map.put(la.getIndividualAdapter().getListenerClass(), la);
                }
            }
        }

        return map;
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Gets the associated protocol handler on socket creation
     *
     * @return The protocol handler
     */
    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    /**
     * Gets the associated HTTP request on socket creation
     *
     * @return The HTTP request
     */
    public HttpServletRequest getHttpRequest() {
        return wrappedRequest;
    }

    /**
     * Adds specified listener to this Web Socket if no such listener is present.
     *
     * @param listenerToAdd The listener to add
     */
    public void addListenerIfAbsent(WebSocketListener listenerToAdd) {
        if (listenerToAdd != null) {
            addListenersIfAbsent(Collections.singletonList(listenerToAdd));
        }
    }

    /**
     * Adds specified listeners to this Web Socket. Each listener is only added if no such listener is present.
     *
     * @param listenersToAdd The listeners to add
     */
    public synchronized void addListenersIfAbsent(Collection<WebSocketListener> listenersToAdd) {
        if (listenersToAdd == null) {
            return;
        }

        Collection<WebSocketListener> listeners = getListeners();
        for (WebSocketListener grizzlyWebSocketListener : listenersToAdd) {
            if (grizzlyWebSocketListener instanceof IndividualWebSocketListenerAdapter) {
                IndividualWebSocketListenerAdapter individualWebSocketListener = (IndividualWebSocketListenerAdapter) grizzlyWebSocketListener;
                Class<? extends IndividualWebSocketListener> clazz = individualWebSocketListener.getListenerClass();
                if (!individualWebSocketListeners.containsKey(clazz)) {
                    // Pass individual instance
                    WebSocketListenerAdapter listener = individualWebSocketListener.newAdapter();
                    listeners.add(listener);
                    individualWebSocketListeners.put(clazz, listener);
                    LOG.debug("Added new instance for IndividualWebSocketListener to '{}': {}", connectionId, clazz);
                }
            } else {
                if (!listeners.contains(grizzlyWebSocketListener)) {
                    listeners.add(grizzlyWebSocketListener);
                    LOG.debug("Added new WebSocketListener to '{}': {}", connectionId, grizzlyWebSocketListener);
                }
            }
        }
    }

    /**
     * Removes specified listener from this Web Socket.
     *
     * @param listenerToRemove The listener to remove
     * @return <code>true</code> if listener was removed; otherwise <code>false</code>
     */
    public synchronized boolean removeListener(WebSocketListener listenerToRemove) {
        if (listenerToRemove == null) {
            return false;
        }

        if (!(listenerToRemove instanceof IndividualWebSocketListenerAdapter)) {
            LOG.debug("Removing WebSocketListener from '{}': {}", connectionId, listenerToRemove);
            return super.remove(listenerToRemove);
        }

        IndividualWebSocketListenerAdapter individualWebSocketListener = (IndividualWebSocketListenerAdapter) listenerToRemove;
        Class<? extends IndividualWebSocketListener> clazz = individualWebSocketListener.getListenerClass();
        WebSocketListener removedListener = individualWebSocketListeners.remove(clazz);
        if (removedListener == null) {
            return false;
        }
        LOG.debug("Removing instance for IndividualWebSocketListener from '{}': {}", connectionId, removedListener);
        return super.remove(removedListener);
    }

    private WebsocketServletRequestWrapper buildHttpServletRequestWrapper(HttpServletRequest httpRequest) {
        if (!config.isConsiderXForwards()) {
            return new WebsocketServletRequestWrapper(httpRequest);
        }

        // Determine remote IP address
        String forHeaderValue = httpRequest.getHeader(config.getForHeader());
        String remoteAddress = IPTools.getRemoteIP(forHeaderValue, config.getKnownProxies());
        if (null == remoteAddress) {
            LOG.debug("Could not detect a valid remote IP address in {}: [{}], falling back to default", config.getForHeader(), forHeaderValue == null ? "" : forHeaderValue);
            remoteAddress = httpRequest.getRemoteAddr();
        }

        // Determine protocol/scheme of the incoming request
        String protocol = httpRequest.getHeader(config.getProtocolHeader());
        if (!isValidProtocol(protocol)) {
            LOG.debug("Could not detect a valid protocol header value in {}, falling back to default", protocol);
            protocol = httpRequest.getScheme();
        }

        return new WebsocketServletRequestWrapper(protocol, remoteAddress, httpRequest.getServerPort(), httpRequest);
    }

    private boolean isValidProtocol(String protocolHeaderValue) {
        return WebsocketServletRequestWrapper.WS_SCHEME.equals(protocolHeaderValue) || WebsocketServletRequestWrapper.WSS_SCHEME.equals(protocolHeaderValue);
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Gets the connection identifier
     *
     * @return The connection identifier
     */
    public ConnectionId getConnectionId() {
        return connectionId;
    }

    /**
     * Sets the basic information extracted from given session.
     *
     * @param session The session to extract from
     */
    public void setSessionInfo(Session session) {
        this.sessionInfoReference.set(SessionInfo.newInstance(session));
    }

    /**
     * Sets the basic information for the session currently associated with this Web Socket.
     *
     * @param sessionInfo The session information to set
     */
    public void setSessionInfo(SessionInfo sessionInfo) {
        this.sessionInfoReference.set(sessionInfo);
    }

    /**
     * Gets the basic information for the session currently associated with this Web Socket.
     *
     * @return The session information
     */
    public SessionInfo getSessionInfo() {
        return sessionInfoReference.get();
    }

    /**
     * Gets the identifier of the session currently associated with this Web Socket.
     *
     * @return The session identifier
     */
    public String getSessionId() {
        SessionInfo sessionInfo = sessionInfoReference.get();
        return sessionInfo.getSessionId();
    }

    /**
     * Gets the user identifier
     *
     * @return The user identifier
     */
    public int getUserId() {
        SessionInfo sessionInfo = sessionInfoReference.get();
        return sessionInfo.getUserId();
    }

    /**
     * Gets the context identifier
     *
     * @return The context identifier
     */
    public int getContextId() {
        SessionInfo sessionInfo = sessionInfoReference.get();
        return sessionInfo.getContextId();
    }

    /**
     * Gets the path that was used while this Web Socket was created; e.g. <code>"/websockets/foo/bar"</code>.
     *
     * @return The path
     */
    public String getPath() {
        return path;
    }

    @Override
    public void onConnect() {
        try {
            super.onConnect();
        } catch (HandshakeException e) {
            throw e;
        } catch (Exception e) {
            HandshakeException hndshkExc = new HandshakeException(e.getMessage());
            hndshkExc.initCause(e);
            throw hndshkExc;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(128);
        builder.append("{");
        SessionInfo sessionInfo = sessionInfoReference.get();
        builder.append("userId=").append(sessionInfo.getUserId());
        builder.append(", contextId=").append(sessionInfo.getContextId());
        String sessionId = sessionInfo.getSessionId();
        if (sessionId != null) {
            builder.append(", sessionId=").append(sessionId);
        }
        if (connectionId != null) {
            builder.append(", connectionId=").append(connectionId);
        }
        builder.append("}");
        return builder.toString();
    }

}
