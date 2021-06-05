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

package com.openexchange.tools.ssl;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.slf4j.Logger;
import com.openexchange.logging.Constants;
import com.openexchange.monitoring.sockets.SocketLoggerUtil;
import com.openexchange.net.osgi.NetSSLActivator;
import com.openexchange.net.utils.Strings;

/**
 * {@link DelegatingSSLSocket} - A sub-class of <code>javax.net.ssl.SSLSocket</code> that delegates to an existing instance of <code>javax.net.ssl.SSLSocket</code>.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class DelegatingSSLSocket extends SSLSocket {

    private final SSLSocket delegate;
    private final HostnameVerifier optHostnameVerifier;
    private volatile SocketAddress endpoint;

    /**
     * Initializes a new {@link DelegatingSSLSocket} with disabled host-name verification.
     *
     * @param delegate The SSL socket to delegate to
     * @param endpoint The end-point to which the socket was initially connected or <code>null</code> if not yet connected
     * @param optHostnameVerifier The host-name verifier to use
     */
    public DelegatingSSLSocket(javax.net.ssl.SSLSocket delegate) {
        this(delegate, null, null);
    }

    /**
     * Initializes a new {@link DelegatingSSLSocket}.
     *
     * @param delegate The SSL socket to delegate to
     * @param endpoint The end-point to which the socket was connected (for host-name verification); otherwise <code>null</code> if not yet connected or not of interest for the host-name verifier
     * @param hostnameVerifier The optional host-name verifier to use or <code>null</code> to disable host-name verification
     */
    public DelegatingSSLSocket(javax.net.ssl.SSLSocket delegate, SocketAddress endpoint, HostnameVerifier hostnameVerifier) {
        super();
        this.delegate = delegate;
        this.endpoint = endpoint;
        this.optHostnameVerifier = null == hostnameVerifier ? null : hostnameVerifier;
    }

    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        delegate.addHandshakeCompletedListener(listener);
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        this.endpoint = endpoint;
        delegate.connect(endpoint);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        this.endpoint = endpoint;
        delegate.connect(endpoint, timeout);
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        delegate.bind(bindpoint);
    }

    @Override
    public InetAddress getInetAddress() {
        return delegate.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    @Override
    public int getPort() {
        return delegate.getPort();
    }

    @Override
    public int getLocalPort() {
        return delegate.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return delegate.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return delegate.getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
        return delegate.getChannel();
    }

    @Override
    public SSLParameters getSSLParameters() {
        return delegate.getSSLParameters();
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return delegate.getTcpNoDelay();
    }

    @Override
    public int getSoLinger() throws SocketException {
        return delegate.getSoLinger();
    }

    @Override
    public int getSoTimeout() throws SocketException {
        return delegate.getSoTimeout();
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return delegate.getKeepAlive();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public boolean getEnableSessionCreation() {
        return delegate.getEnableSessionCreation();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return delegate.getEnabledCipherSuites();
    }

    @Override
    public String[] getEnabledProtocols() {
        return delegate.getEnabledProtocols();
    }

    @Override
    public SSLSession getHandshakeSession() {
        return delegate.getHandshakeSession();
    }

    @Override
    public boolean getNeedClientAuth() {
        return delegate.getNeedClientAuth();
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        return delegate.getOOBInline();
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        return delegate.getSendBufferSize();
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        return delegate.getReceiveBufferSize();
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return delegate.getReuseAddress();
    }

    @Override
    public SSLSession getSession() {
        return delegate.getSession();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public String[] getSupportedProtocols() {
        return delegate.getSupportedProtocols();
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return delegate.getTrafficClass();
    }

    @Override
    public boolean getUseClientMode() {
        return delegate.getUseClientMode();
    }

    @Override
    public boolean getWantClientAuth() {
        return delegate.getWantClientAuth();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        delegate.setTcpNoDelay(on);
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        delegate.setSoLinger(on, linger);
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        delegate.sendUrgentData(data);
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        delegate.setOOBInline(on);
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        delegate.setSoTimeout(timeout);
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        delegate.setSendBufferSize(size);
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
        delegate.setReceiveBufferSize(size);
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        delegate.setKeepAlive(on);
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        delegate.setTrafficClass(tc);
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        delegate.setReuseAddress(on);
    }

    @Override
    public void shutdownInput() throws IOException {
        delegate.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        delegate.shutdownOutput();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public boolean isBound() {
        return delegate.isBound();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public boolean isInputShutdown() {
        return delegate.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return delegate.isOutputShutdown();
    }

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        delegate.removeHandshakeCompletedListener(listener);
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        delegate.setEnableSessionCreation(flag);
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
        delegate.setEnabledCipherSuites(suites);
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        delegate.setEnabledProtocols(protocols);
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        delegate.setNeedClientAuth(need);
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    @Override
    public void setSSLParameters(SSLParameters params) {
        delegate.setSSLParameters(params);
    }

    @Override
    public void setUseClientMode(boolean mode) {
        delegate.setUseClientMode(mode);
    }

    @Override
    public void setWantClientAuth(boolean want) {
        delegate.setWantClientAuth(want);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new LoggingOutputStream(delegate.getOutputStream());
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new LoggingInputStream(delegate.getInputStream());
    }

    private static final MessageMatcher MATCHER_TRUST_ANCHORS_ERROR = new EqualsMatcher("the trustAnchors parameter must be non-empty", true);
    private static final MessageMatcher MATCHER_INCORRECT_SHUTDOWN_ERROR = new EqualsMatcher("ssl peer shut down incorrectly", true);
    private static final MessageMatcher MATCHER_HANDSHAKE_FAILURE_ERROR = new ContainsMatcher("handshake_failure", false);

    @Override
    public void startHandshake() throws IOException {
        try {
            delegate.startHandshake();

            // Verify host name after hand-shake (if desired)
            if (null != optHostnameVerifier) {
                SSLSession session = delegate.getSession();
                SocketAddress endpoint = this.endpoint;
                String hostname = InetSocketAddress.class.isInstance(endpoint) ? ((InetSocketAddress) endpoint).getHostName() : session.getPeerHost();
                if (hostname != null && false == optHostnameVerifier.verify(hostname, session)) {
                    throw new SSLPeerUnverifiedException("SSL peer failed hostname validation for name: " + hostname);
                }
            }
        } catch (SSLPeerUnverifiedException e) {
            throw e;
        } catch (SSLException e) {
            if (matchesException(e, java.security.InvalidAlgorithmParameterException.class, MATCHER_TRUST_ANCHORS_ERROR)) {
                //@formatter:off
                throw new javax.net.ssl.SSLException("The JVM cannot find the truststore required for SSL, or it does not contain the required certificates."
                    + " Please check JVM's truststore configuration; e.g. specified via \"javax.net.ssl.trustStore\" JVM argument (if \"com.openexchange.net.ssl.default.truststore.enabled\" is true)"
                    + " or \"com.openexchange.net.ssl.custom.truststore.path\" property (if \"com.openexchange.net.ssl.custom.truststore.enabled\" is true)", e);
                //@formatter:on
            }

            // See http://stackoverflow.com/a/6353956

            if (matchesException(e, java.io.EOFException.class, MATCHER_INCORRECT_SHUTDOWN_ERROR)) {
                // E.g. determine supported protocols/cipher suites using https://www.ssllabs.com/ssltest/index.html
                SocketAddress endpoint = this.endpoint;
                String endpointInfo = null == endpoint ? "" : (" \"" + endpoint + "\"");
                Set<String> protocols = new LinkedHashSet<String>(Arrays.asList(getEnabledProtocols()));
                //@formatter:off
                throw new javax.net.ssl.SSLException("The remote end-point" + endpointInfo + " closed connection during handshake unexpectedly. Most likey because the end-point expects a protocol,"
                    + " that is not enabled/available for the JVM. Enabled protocols are: " + protocols + ". You may want to let supported protocols/cipher suite be determined by using https://www.ssllabs.com/ssltest/index.html", e);
                //@formatter:on
            }

            if (MATCHER_HANDSHAKE_FAILURE_ERROR.matches(e.getMessage())) {
                // E.g. determine supported protocols/cipher suites using https://www.ssllabs.com/ssltest/index.html
                SocketAddress endpoint = this.endpoint;
                String endpointInfo = null == endpoint ? "" : (" \"" + endpoint + "\"");
                Set<String> protocols = new LinkedHashSet<String>(Arrays.asList(getEnabledProtocols()));
                // Set<String> ciperSuites = new LinkedHashSet<String>(Arrays.asList(getEnabledCipherSuites()));
                //@formatter:off
                throw new javax.net.ssl.SSLException("The SSL hand-shake with remote end-point" + endpointInfo + " failed. Most likey because the end-point expects a protocol or cipher suite,"
                    + " that is not enabled/available for the JVM. Enabled protocols are: " + protocols + ". You may want to let supported protocols/cipher suite be determined by using https://www.ssllabs.com/ssltest/index.html", e);
                //@formatter:on
            }

            throw e;
        }
    }

    private static boolean matchesException(Throwable error, Class<? extends Exception> expectedClass, MessageMatcher messageMatcher) {
        if (expectedClass.isInstance(error)) {
            return messageMatcher.matches(error.getMessage());
        }

        Throwable cause = error.getCause();
        return null == cause ? false : matchesException(cause, expectedClass, messageMatcher);
    }

    private static interface MessageMatcher {

        boolean matches(String message);
    }

    private static final class EqualsMatcher implements MessageMatcher {

        private final String expectedMessage;
        private final boolean ignoreCase;

        EqualsMatcher(String expectedMessage, boolean ignoreCase) {
            super();
            this.expectedMessage = expectedMessage;
            this.ignoreCase = ignoreCase;
        }

        @Override
        public boolean matches(String message) {
            return ignoreCase ? expectedMessage.equalsIgnoreCase(message) : expectedMessage.equals(message);
        }
    }

    private static final class ContainsMatcher implements MessageMatcher {

        private final String containedSequence;
        private final boolean ignoreCase;

        ContainsMatcher(String containedSequence, boolean ignoreCase) {
            super();
            this.containedSequence = containedSequence;
            this.ignoreCase = ignoreCase;
        }

        @Override
        public boolean matches(String message) {
            return null != message && (ignoreCase ? Strings.asciiLowerCase(message) : message).indexOf(containedSequence) >= 0;
        }
    }

    /**
     * {@link LoggingOutputStream} - Logs output traffic
     */
    private static class LoggingOutputStream extends FilterOutputStream {

        /**
         * Initialises a new {@link LoggingOutputStream}.
         *
         * @param out the output stream
         */
        public LoggingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            Optional<Logger> logger = SocketLoggerUtil.getLoggerForSSLSocket(NetSSLActivator.getServiceLookup());
            logger.ifPresent((l) -> l.trace(Constants.DROP_MDC_MARKER, SocketLoggerUtil.prepareForLogging(b, off, len)));
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            Optional<Logger> logger = SocketLoggerUtil.getLoggerForSSLSocket(NetSSLActivator.getServiceLookup());
            logger.ifPresent((l) -> l.trace(Constants.DROP_MDC_MARKER, new String(new char[] { (char) b })));
        }
    }

    /**
     * {@link LoggingInputStream} - Logs input traffic
     */
    private static class LoggingInputStream extends FilterInputStream {

        /**
         * Initialises a new {@link LoggingInputStream}.
         *
         * @param in the input stream
         */
        protected LoggingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = in.read();
            Optional<Logger> logger = SocketLoggerUtil.getLoggerForSSLSocket(NetSSLActivator.getServiceLookup());
            logger.ifPresent((l) -> l.trace(Constants.DROP_MDC_MARKER, new String(new char[] { (char) result })));
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int length = in.read(b, off, len);
            Optional<Logger> logger = SocketLoggerUtil.getLoggerForSSLSocket(NetSSLActivator.getServiceLookup());
            logger.ifPresent((l) -> l.trace(Constants.DROP_MDC_MARKER, SocketLoggerUtil.prepareForLogging(b, off, len)));
            return length;
        }
    }
}
