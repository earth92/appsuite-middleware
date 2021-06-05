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

package com.openexchange.websockets.grizzly.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import com.google.common.collect.ImmutableMap;
import com.openexchange.dispatcher.Parameterizable;

/**
 *
 * {@link WebsocketServletRequestWrapper}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.1
 */
public class WebsocketServletRequestWrapper implements HttpServletRequest, Parameterizable {

    private static final String ABSENT = new String(new char[] {'_','_','a', 'b', 's', 'e', 'n', 't'});

    /** The protocol scheme for HTTP */
    public final static String WS_SCHEME = "ws";

    /** The protocol scheme for HTTPS */
    public final static String WSS_SCHEME = "wss";

    private final HttpServletRequest delegate;
    private final String requestScheme;
    private final int serverPort;
    private final boolean isSecure;
    private final String remoteAddress;
    private final ConcurrentMap<String, String> additionalParams;

    /**
     * Initializes a new {@link WebsocketServletRequestWrapper}.
     *
     * @param requestScheme The scheme of the request: http or https
     * @param remoteAddress The remote IP address of the request
     * @param serverPort The server port of the request
     * @param httpServletRequest The request (to delegate to)
     * @throws IllegalArgumentException If the port is smaller than 1
     */
    public WebsocketServletRequestWrapper(String requestScheme, String remoteAddress, int serverPort, HttpServletRequest httpServletRequest) {
        if (serverPort < 1) {
            throw new IllegalArgumentException("Port is out of valid range: " + serverPort);
        }
        if (requestScheme.equalsIgnoreCase(WSS_SCHEME)) {
            this.requestScheme = WSS_SCHEME;
            this.isSecure = true;
        } else {
            this.requestScheme = WS_SCHEME;
            this.isSecure = false;
        }
        this.remoteAddress = remoteAddress;
        this.serverPort = serverPort;
        this.delegate = httpServletRequest;
        this.additionalParams = new ConcurrentHashMap<>(6, 0.9F, 1);
    }

    /**
     * Initializes a new {@link WebsocketServletRequestWrapper}.
     *
     * @param httpServletRequest the delegate to use with this wrapper
     */
    public WebsocketServletRequestWrapper(HttpServletRequest httpServletRequest) {
        this.delegate = httpServletRequest;
        if (WSS_SCHEME.equals(httpServletRequest.getScheme())) {
            this.requestScheme = WSS_SCHEME;
            this.isSecure = true;
        } else {
            this.requestScheme = WS_SCHEME;
            this.isSecure = false;
        }
        this.serverPort = httpServletRequest.getServerPort();
        this.remoteAddress = httpServletRequest.getRemoteAddr();
        this.additionalParams = new ConcurrentHashMap<>(6, 0.9F, 1);
    }

    @Override
    public Object getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return delegate.getAttributeNames();
    }

    @Override
    public String getAuthType() {
        return delegate.getAuthType();
    }

    @Override
    public String getCharacterEncoding() {
        return delegate.getCharacterEncoding();
    }

    @Override
    public int getContentLength() {
        return delegate.getContentLength();
    }

    @Override
    public String getContentType() {
        return delegate.getContentType();
    }

    @Override
    public String getContextPath() {
        return delegate.getContextPath();
    }

    @Override
    public Cookie[] getCookies() {
        return delegate.getCookies();
    }

    @Override
    public long getDateHeader(String name) {
        return null == name || 0 == name.length() ? -1L : delegate.getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return null == name || 0 == name.length() ? null : delegate.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return delegate.getHeaderNames();
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return delegate.getHeaders(name);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    @Override
    public int getIntHeader(String name) {
        return null == name || 0 == name.length() ? -1 : delegate.getIntHeader(name);
    }

    @Override
    public String getLocalAddr() {
        return delegate.getLocalAddr();
    }

    @Override
    public String getLocalName() {
        return delegate.getLocalName();
    }

    @Override
    public int getLocalPort() {
        return delegate.getLocalPort();
    }

    @Override
    public Locale getLocale() {
        return delegate.getLocale();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return delegate.getLocales();
    }

    @Override
    public String getMethod() {
        return delegate.getMethod();
    }

    @Override
    public String getParameter(String name) {
        String value = additionalParams.get(name);
        if (null != value) {
            return ABSENT == value ? null /*Explicitly removed*/ : value;
        }

        return delegate.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (additionalParams.isEmpty()) {
            return delegate.getParameterMap();
        }

        // Create copy from request's parameters
        Map<String, String[]> parameters = delegate.getParameterMap();
        if (null != parameters && !parameters.isEmpty()) {
            parameters = new LinkedHashMap<>(parameters);
        } else {
            parameters = new LinkedHashMap<>(4, 0.9F);
        }

        // Overwrite/clean by the ones from additional parameters
        for (Map.Entry<String,String> entry : additionalParams.entrySet()) {
            String value = entry.getValue();
            if (ABSENT == value) {
                parameters.remove(entry.getKey());
            } else {
                parameters.put(entry.getKey(), new String[] { value });
            }
        }

        return ImmutableMap.copyOf(parameters);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        if (additionalParams.isEmpty()) {
            return delegate.getParameterNames();
        }

        // Create copy from request's parameters
        Set<String> names = new LinkedHashSet<>(6);
        for (Enumeration<String> e = delegate.getParameterNames(); e.hasMoreElements();) {
            names.add(e.nextElement());
        }

        // Enhance/clean by the ones from additional parameters
        for (Map.Entry<String,String> entry : additionalParams.entrySet()) {
            String value = entry.getValue();
            if (ABSENT == value) {
                names.remove(entry.getKey());
            } else {
                names.add(entry.getKey());
            }
        }

        return new IteratorEnumeration<String>(names.iterator());
    }

    @Override
    public String[] getParameterValues(String name) {
        String value = additionalParams.get(name);
        if (null != value) {
            return ABSENT == value ? null /*Explicitly removed*/ : new String[] { value };
        }

        return delegate.getParameterValues(name);
    }

    @Override
    public String getPathInfo() {
        return delegate.getPathInfo();
    }

    @Override
    public String getPathTranslated() {
        return delegate.getPathTranslated();
    }

    @Override
    public String getProtocol() {
        return delegate.getProtocol();
    }

    @Override
    public String getQueryString() {
        return delegate.getQueryString();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return delegate.getReader();
    }

    @Override
    @Deprecated
    public String getRealPath(String arg0) {
        return delegate.getRealPath(arg0);
    }

    @Override
    public String getRemoteAddr() {
        return remoteAddress;
    }

    @Override
    public String getRemoteHost() {
        return delegate.getRemoteHost();
    }

    @Override
    public int getRemotePort() {
        return delegate.getRemotePort();
    }

    @Override
    public String getRemoteUser() {
        return delegate.getRemoteUser();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String arg0) {
        return delegate.getRequestDispatcher(arg0);
    }

    @Override
    public String getRequestURI() {
        return delegate.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
        return delegate.getRequestURL();
    }

    @Override
    public String getRequestedSessionId() {
        return delegate.getRequestedSessionId();
    }

    @Override
    public String getScheme() {
        return requestScheme;
    }

    @Override
    public String getServerName() {
        return delegate.getServerName();
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    @Override
    public String getServletPath() {
        return delegate.getServletPath();
    }

    @Override
    public HttpSession getSession() {
        return delegate.getSession();
    }

    @Override
    public HttpSession getSession(boolean arg0) {
        return delegate.getSession(arg0);
    }

    @Override
    public Principal getUserPrincipal() {
        return delegate.getUserPrincipal();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return delegate.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return delegate.isRequestedSessionIdFromURL();
    }

    @Override
    @Deprecated
    public boolean isRequestedSessionIdFromUrl() {
        return delegate.isRequestedSessionIdFromUrl();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return delegate.isRequestedSessionIdValid();
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public boolean isUserInRole(String arg0) {
        return delegate.isUserInRole(arg0);
    }

    /**
     * com.openexchange.ajax.Multiple accesses request objects concurrently
     * so we have to prevent concurrent modifications
     */
    @Override
    public synchronized void removeAttribute(String name) {
        delegate.removeAttribute(name);
    }

    @Override
    public synchronized void setAttribute(String name, Object o) {
        delegate.setAttribute(name, o);
    }

    @Override
    public synchronized void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        delegate.setCharacterEncoding(env);
    }

    @Override
    public void putParameter(String name, String value) {
        if (null == name) {
           throw new NullPointerException("name is null");
        }

        additionalParams.put(name, null == value ? ABSENT : value);
    }

    @Override
    public long getContentLengthLong() {
        return delegate.getContentLengthLong();
    }

    @Override
    public ServletContext getServletContext() {
        return delegate.getServletContext();
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return delegate.startAsync();
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        return delegate.startAsync(servletRequest, servletResponse);
    }

    @Override
    public boolean isAsyncStarted() {
        return delegate.isAsyncStarted();
    }

    @Override
    public boolean isAsyncSupported() {
        return delegate.isAsyncSupported();
    }

    @Override
    public AsyncContext getAsyncContext() {
        return delegate.getAsyncContext();
    }

    @Override
    public DispatcherType getDispatcherType() {
        return delegate.getDispatcherType();
    }

    @Override
    public String changeSessionId() {
        return delegate.changeSessionId();
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        return delegate.authenticate(response);
    }

    @Override
    public void login(String username, String password) throws ServletException {
        delegate.login(username, password);
    }

    @Override
    public void logout() throws ServletException {
        delegate.logout();
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return delegate.getParts();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        return delegate.getPart(name);
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        return delegate.upgrade(handlerClass);
    }

    // ----------------------------------------------------------------------------------------------------------------------------

    private static class IteratorEnumeration<E> implements Enumeration<E> {

        /** The iterator being decorated. */
        private final Iterator<E> iterator;

        /**
         * Constructs a new <code>IteratorEnumeration</code> that will use the given iterator.
         *
         * @param iterator  the iterator to use
         */
        public IteratorEnumeration(Iterator<E> iterator ) {
            super();
            this.iterator = iterator;
        }

        /**
         *  Returns true if the underlying iterator has more elements.
         *
         *  @return true if the underlying iterator has more elements
         */
        @Override
        public boolean hasMoreElements() {
            return iterator.hasNext();
        }

        /**
         *  Returns the next element from the underlying iterator.
         *
         *  @return the next element from the underlying iterator.
         *  @throws java.util.NoSuchElementException  if the underlying iterator has no
         *    more elements
         */
        @Override
        public E nextElement() {
            return iterator.next();
        }

    }

}
