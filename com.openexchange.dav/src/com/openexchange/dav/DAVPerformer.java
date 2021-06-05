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

package com.openexchange.dav;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;
import com.openexchange.session.SessionHolder;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import com.openexchange.webdav.action.AbstractAction;
import com.openexchange.webdav.action.ServletWebdavRequest;
import com.openexchange.webdav.action.ServletWebdavResponse;
import com.openexchange.webdav.action.WebdavAction;
import com.openexchange.webdav.action.WebdavDefaultHeaderAction;
import com.openexchange.webdav.action.WebdavExistsAction;
import com.openexchange.webdav.action.WebdavIfMatchAction;
import com.openexchange.webdav.action.WebdavLogAction;
import com.openexchange.webdav.action.WebdavRequest;
import com.openexchange.webdav.action.WebdavRequestCycleAction;
import com.openexchange.webdav.loader.BulkLoader;
import com.openexchange.webdav.protocol.WebdavMethod;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;
import com.openexchange.webdav.protocol.helpers.PropertyMixin;

/**
 * {@link DAVPerformer}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.8.1
 */
public abstract class DAVPerformer implements SessionHolder {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DAVPerformer.class);

    private final ThreadLocal<ServerSession> sessionHolder;

    /**
     * Initializes a new {@link DAVPerformer}.
     */
    protected DAVPerformer() {
        super();
        this.sessionHolder = new ThreadLocal<ServerSession>();
    }

    /**
     * Gets the URL prefix to apply for each incoming WebDAV request.
     *
     * @return The URL prefix
     */
    protected abstract String getURLPrefix();

    /**
     * Gets the underlying WebDAV factory.
     *
     * @return The WebDAV factory
     */
    public abstract DAVFactory getFactory();

    /**
     * Gets the WebDAV action that handles a specific WebDAV method.
     *
     * @param method The WebDAV method to get the action for
     * @return The action, or <code>null</code> if no action is mapped
     */
    protected abstract WebdavAction getAction(WebdavMethod method);

    /**
     * Prepares a new {@link WebdavRequestCycleAction} for a concrete WebDAV action, injecting appropriate actions for logging, default
     * response headers and if-header matching implicitly. Further actions are added as needed, as well as finally the action itself.
     *
     * @param action The action to prepare
     * @param logBody <code>true</code> to log the request body, <code>false</code>, otherwise
     * @param logResponse <code>true</code> to log the response, <code>false</code>, otherwise
     * @param additionals Additional actions to include
     * @return The prepared WebDAV action
     */
    protected WebdavAction prepare(AbstractAction action, boolean logBody, boolean logResponse, AbstractAction... additionals) {
        return prepare(action, logBody, logResponse, null, additionals);
    }

    /**
     * Prepares a new {@link WebdavRequestCycleAction} for a concrete WebDAV action, injecting appropriate actions for logging, default
     * response headers and if-header matching implicitly. Further actions are added as needed, as well as finally the action itself.
     *
     * @param action The action to prepare
     * @param logBody <code>true</code> to log the request body, <code>false</code>, otherwise
     * @param logResponse <code>true</code> to log the response, <code>false</code>, otherwise
     * @param bulkLoader The bulk loader to apply for each action, or <code>null</code> if not needed
     * @param additionals Additional actions to include
     * @return The prepared WebDAV action
     */
    protected WebdavAction prepare(AbstractAction action, boolean logBody, boolean logResponse, BulkLoader bulkLoader, AbstractAction... additionals) {
        return prepare(action, logBody, logResponse, true, bulkLoader, additionals);
    }

    /**
     * Prepares a new {@link WebdavRequestCycleAction} for a concrete WebDAV action, injecting appropriate actions for logging, default
     * response headers and if-header matching implicitly. Further actions are added as needed, as well as finally the action itself.
     *
     * @param action The action to prepare
     * @param logBody <code>true</code> to log the request body, <code>false</code>, otherwise
     * @param logResponse <code>true</code> to log the response, <code>false</code>, otherwise
     * @param ifMatch <code>true</code> to do <code>"If-Match"</code> / <code>If-None-Match</code> header validation, <code>false</code>, otherwise
     * @param bulkLoader The bulk loader to apply for each action, or <code>null</code> if not needed
     * @param additionals Additional actions to include
     * @return The prepared WebDAV action
     */
    protected WebdavAction prepare(AbstractAction action, boolean logBody, boolean logResponse, boolean ifMatch, BulkLoader bulkLoader, AbstractAction... additionals) {
        /*
         * initialize surrounding request lifecycle
         */
        WebdavRequestCycleAction lifeCycle = new WebdavRequestCycleAction();
        lifeCycle.setBulkLoader(bulkLoader);
        /*
         * add log action
         */
        WebdavLogAction logAction = new WebdavLogAction(logBody, logResponse);
        lifeCycle.setNext(logAction);
        /*
         * add default header action
         */
        WebdavDefaultHeaderAction defaultHeader = new WebdavDefaultHeaderAction();
        defaultHeader.setBulkLoader(bulkLoader);
        logAction.setNext(defaultHeader);
        AbstractAction previousAction = defaultHeader;
        /*
         * add if-match action
         */
        if (ifMatch) {
            WebdavIfMatchAction ifMatchAction = new WebdavIfMatchAction();
            ifMatchAction.setBulkLoader(bulkLoader);
            previousAction.setNext(ifMatchAction);
            previousAction = ifMatchAction;
        }
        /*
         * add additional actions
         */
        for (AbstractAction nextAction : additionals) {
            nextAction.setBulkLoader(bulkLoader);
            previousAction.setNext(nextAction);
            previousAction = nextAction;
        }
        /*
         * add action itself
         */
        action.setBulkLoader(bulkLoader);
        previousAction.setNext(action);
        return lifeCycle;
    }

    public void setGlobalMixins(PropertyMixin... mixins) {
        getFactory().setGlobalMixins(mixins);
    }

    public void doIt(HttpServletRequest request, HttpServletResponse response, WebdavMethod method, ServerSession session) {
        ServletWebdavRequest webdavRequest = new ServletWebdavRequest(getFactory(), request);
        webdavRequest.setUrlPrefix(getURLPrefix());
        sessionHolder.set(decorateSession(session, webdavRequest));
        try {
            ServletWebdavResponse webdavResponse = new ServletWebdavResponse(response);
            getAction(method).perform(webdavRequest, webdavResponse);
        } catch (WebdavProtocolException e) {
            sendErrorResponse(e, request, response);
        } finally {
            sessionHolder.set(null);
        }
    }

    @Override
    public Session getSessionObject() {
        return sessionHolder.get();
    }

    @Override
    public Context getContext() {
        return sessionHolder.get().getContext();
    }

    @Override
    public User getUser() {
        return sessionHolder.get().getUser();
    }

    /**
     * Marks specific actions as supported by so-called "lock-null" resources, i.e. locked placeholder resources to reserve a name.
     * <p/>
     * @see <a href=http://www.webdav.org/specs/rfc2518.html#rfc.section.7.4>http://www.webdav.org/specs/rfc2518.html#rfc.section.7.4</a>
     *
     * @param actions The actions to mark if applicable
     */
    protected static void makeLockNullTolerant(Map<WebdavMethod, WebdavAction> actions) {
        WebdavMethod[] nullTolerantActions = { WebdavMethod.OPTIONS, WebdavMethod.LOCK, WebdavMethod.MKCOL, WebdavMethod.PUT };
        for (WebdavMethod action : nullTolerantActions) {
            WebdavAction webdavAction = actions.get(action);
            while (null != webdavAction) {
                if (webdavAction instanceof WebdavExistsAction) {
                    ((WebdavExistsAction) webdavAction).setTolerateLockNull(true);
                    webdavAction = null;
                } else if (webdavAction instanceof AbstractAction) {
                    webdavAction = ((AbstractAction) webdavAction).getNext();
                } else {
                    webdavAction = null;
                }
            }
        }
    }

    /**
     * Optionally decorates the session based on the actual WebDAV request.
     *
     * @param session The server session
     * @param request The request
     * @return The decorated server session
     */
    protected ServerSession decorateSession(ServerSession session, WebdavRequest request) {
        String userAgent = request.getHeader("user-agent");
        if (Strings.isNotEmpty(userAgent)) {
            session.setParameter("user-agent", userAgent);
        }
        String pushClientToken = request.getHeader("X-Apple-DAV-Pushtoken");
        if (Strings.isEmpty(pushClientToken)) {
            pushClientToken = request.getHeader("Push-Client-Id");
        }
        if (Strings.isNotEmpty(pushClientToken)) {
            session.setParameter("com.openexchange.dav.push.clientToken", pushClientToken);
        }
        return session;
    }

    /**
     * Sends an appropriate error response for a WebDAV protocol exception that occurred during processing a request.
     *
     * @param e The WebDAV protocol exception to send the error response for
     * @param request The WebDAV request
     * @param response The WebDAV response
     */
    private void sendErrorResponse(WebdavProtocolException e, HttpServletRequest request, HttpServletResponse response) {
        if (null == response || response.isCommitted()) {
            LOG.debug("Unable to send error response (HTTP {}) after protocol exception", Integer.valueOf(e.getStatus()), e);
            return;
        }
        if (PreconditionException.class.isInstance(e)) {
            ((PreconditionException) e).sendError(response);
        } else if (HttpServletResponse.SC_CONFLICT == e.getStatus() && isETagMismatch(request)) {
            LOG.debug("Sending HTTP 412 for HTTP 409 response due to mismatching ETag in \"If-Match\" header: {}", request.getHeader("If-Match"), e);
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
        } else {
            response.setStatus(e.getStatus());
        }
    }

    /**
     * Gets a value indicating whether an <code>If-Match</code>-header in the supplied HTTP request matches the targeted resource's
     * current ETag or not.
     *
     * @param request The request to check
     * @return <code>true</code> if the value in the request's <code>If-Match</code>-header doesn't match the resource ETag, <code>false</code>, otherwise
     */
    private boolean isETagMismatch(HttpServletRequest request) {
        String ifMatchHeader = request.getHeader("If-Match");
        if (Strings.isEmpty(ifMatchHeader)) {
            return false;
        }
        try {
            ServletWebdavRequest webdavRequest = new ServletWebdavRequest(getFactory(), request);
            WebdavResource resource = webdavRequest.getResource();
            if (null == resource || false == resource.exists()) {
                return false;
            }
            String eTag = resource.getETag();
            for (String value : Strings.splitByComma(ifMatchHeader)) {
                if (false == "*".equals(value) && false == eTag.equals(Strings.unquote(value))) {
                    return true;
                }
            }
        } catch (WebdavProtocolException e) {
            org.slf4j.LoggerFactory.getLogger(DAVPerformer.class).warn("Error re-checking ETag", e);
        }
        return false;
    }

}
