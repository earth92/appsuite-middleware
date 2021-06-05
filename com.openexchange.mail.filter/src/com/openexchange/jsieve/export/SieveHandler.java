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

package com.openexchange.jsieve.export;

import static com.openexchange.exception.ExceptionUtils.isEitherOf;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.java.Charsets.UTF_8;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.internet.AddressException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import com.google.common.collect.ImmutableList;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.jsieve.export.exceptions.OXSieveHandlerException;
import com.openexchange.jsieve.export.exceptions.OXSieveHandlerInvalidCredentialsException;
import com.openexchange.jsieve.export.utils.FailsafeCircuitBreakerBufferedOutputStream;
import com.openexchange.jsieve.export.utils.FailsafeCircuitBreakerBufferedReader;
import com.openexchange.jsieve.export.utils.HostAndPort;
import com.openexchange.mail.mime.QuotedInternetAddress;
import com.openexchange.mailfilter.internal.CircuitBreakerInfo;
import com.openexchange.mailfilter.properties.MailFilterProperty;
import com.openexchange.mailfilter.properties.PreferredSASLMech;
import com.openexchange.mailfilter.services.Services;
import com.openexchange.tools.encoding.Base64;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;

/**
 * This class is used to deal with the communication with sieve. For a description of the communication system to sieve see
 * {@see <a href="http://www.ietf.org/internet-drafts/draft-martin-managesieve-07.txt">http://www.ietf.org/internet-drafts/draft-martin-managesieve-07.txt</a>}
 *
 * @author <a href="mailto:dennis.sieben@open-xchange.com">Dennis Sieben</a>
 */
public class SieveHandler {

    private static final Pattern LITERAL_S2C_PATTERN = Pattern.compile("^.*\\{([^\\}]*)\\}.*$");

    /**
     * The logger.
     */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SieveHandler.class);

    /**
     * The constant for CRLF (carriage-return line-feed).
     */
    protected final static String CRLF = "\r\n";

    /**
     * The SIEVE OK.
     */
    protected final static String SIEVE_OK = "OK";

    /**
     * The SIEVE NO.
     */
    protected final static String SIEVE_NO = "NO";

    /**
     * The SIEVE AUTHENTICATE.
     */
    private final static String SIEVE_AUTH = "AUTHENTICATE ";
    private final static String SIEVE_AUTH_FAILED = "NO \"Authentication Error\"";
    private final static String SIEVE_AUTH_LOGIN_USERNAME = "{12}" + CRLF + "VXNlcm5hbWU6";
    private final static String SIEVE_AUTH_LOGIN_PASSWORD = "{12}" + CRLF + "UGFzc3dvcmQ6";
    private final static String SIEVE_PUT = "PUTSCRIPT ";
    private final static String SIEVE_ACTIVE = "SETACTIVE ";
    private final static String SIEVE_DEACTIVE = "SETACTIVE \"\"" + CRLF;
    private final static String SIEVE_DELETE = "DELETESCRIPT ";
    private final static String SIEVE_LIST = "LISTSCRIPTS" + CRLF;
    private final static String SIEVE_CAPABILITY = "CAPABILITY" + CRLF;
    private final static String SIEVE_GET_SCRIPT = "GETSCRIPT ";
    private final static String SIEVE_LOGOUT = "LOGOUT" + CRLF;

    private static final int UNDEFINED = -1;

    protected static final int OK = 0;

    protected static final int NO = 1;

    /**
     * {@link WelcomeKeyword} - The server welcome keywords
     */
    private static enum WelcomeKeyword {
        STARTTLS, IMPLEMENTATION, SIEVE, SASL, MAXREDIRECTS
    }

    /**
     * Remembers timed out servers for 10 seconds. Any further attempts to connect to such
     * a server-port-pair will throw an appropriate exception.
     */
    private static final Map<HostAndPort, Long> TIMED_OUT_SERVERS = new ConcurrentHashMap<>(4, 0.9F, 1);

    /*-
     * Member section
     */

    protected boolean AUTH = false;

    private final String sieve_user;
    private final String sieve_auth;
    private final String sieve_auth_enc;
    private final String sieve_auth_passwd;
    private final boolean onlyWelcome;
    protected final String sieve_host;
    protected final int sieve_host_port;
    private final String oauthToken;
    private Capabilities capa = null;
    private boolean punycode = false;
    private Socket s_sieve = null;
    protected BufferedReader bis_sieve = null;
    protected BufferedOutputStream bos_sieve = null;
    private long mStart;
    private boolean useSIEVEResponseCodes = false;
    private Long connectTimeout = null;
    private Long readTimeout = null;
    private int userId = -1;
    private int contextId = -1;
    private final Optional<CircuitBreakerInfo> optionalCircuitBreaker;

    /**
     * Initializes a new {@link SieveHandler}.
     *
     * @param userName The optional user name to use for <code>"PLAIN"</code> SASL authentication; if <code>null</code> <code>authUserName</code> is considered
     * @param authUserName The login string to use for authentication
     * @param authUserPasswd The secret string to use for authentication
     * @param host The host name or IP address of the SIEVE end-point
     * @param port The port of the SIEVE end-point
     * @param authEnc The encoding to use when transferring credential bytes to SIEVE end-point
     * @param oauthToken The optional OAuth token; relevant in case <code>"XOAUTH2"</code> or <code>"OAUTHBEARER"</code> SASL authentication is supposed to be performed
     * @param optionalCircuitBreaker The optional circuit breaker for mail filter access
     * @param userId The user identifier
     * @param contextId The context identifier
     */
    public SieveHandler(String userName, String authUserName, String authUserPasswd, String host, int port, String authEnc, String oauthToken, Optional<CircuitBreakerInfo> optionalCircuitBreaker, int userId, int contextId) {
        super();
        sieve_user = null == userName ? authUserName : userName;
        sieve_auth = authUserName;
        sieve_auth_enc = authEnc;
        sieve_auth_passwd = authUserPasswd;
        sieve_host = host; // "127.0.0.1"
        sieve_host_port = port; // 2000
        onlyWelcome = false;
        this.oauthToken = oauthToken;
        this.userId = userId;
        this.contextId = contextId;
        this.optionalCircuitBreaker = optionalCircuitBreaker;
    }

    /**
     * Initializes a new {@link SieveHandler} only suitable for retrieving the SIEVE end-point's welcome message.
     *
     * @param host The host name or IP address of the SIEVE end-point
     * @param port The port of the SIEVE end-point
     */
    public SieveHandler(String host, int port) {
        super();
        sieve_user = null;
        sieve_auth = null;
        sieve_auth_enc = null;
        sieve_auth_passwd = null;
        sieve_host = host; // "127.0.0.1"
        sieve_host_port = port; // 2000
        onlyWelcome = true;
        this.oauthToken = null;
        optionalCircuitBreaker = Optional.empty();
    }

    /**
     * Gets the host name or IP address of the SIEVE end-point
     *
     * @return The host name or IP address of the SIEVE end-point
     */
    public String getSieveHost() {
        return sieve_host;
    }

    /**
     * gets the port of the SIEVE end-point
     *
     * @return The port of the SIEVE end-point
     */
    public int getSievePort() {
        return sieve_host_port;
    }

    /**
     * Gets the input reader from SIEVE end-point.
     *
     * @return The input reader
     */
    public BufferedReader getInput() {
        return bis_sieve;
    }

    /**
     * Gets the output stream to SIEVE end-point.
     *
     * @return The output stream
     */
    public BufferedOutputStream getOutput() {
        return bos_sieve;
    }

    /**
     * Sets the start time stamp, which is the current time in milliseconds at the time of invocation.
     */
    private void measureStart() {
        this.mStart = System.currentTimeMillis();
    }

    /**
     * Sets the end time stamp, which is the current time in milliseconds at the time of invocation, and logs the duration since previously set start time stamp for given method.
     *
     * @param method The method to use when generating the <code>DEBUG</code> log message
     */
    private void measureEnd(final String method) {
        long end = System.currentTimeMillis();
        log.debug("SieveHandler.{}() took {}ms to perform", method, L(end - this.mStart));
    }

    /**
     * Sets the connect timeout in milliseconds, which is used when connecting the socket to the server. A timeout of zero is interpreted as an infinite timeout.
     * A value of less than zero lets <code>"com.openexchange.mail.filter.connectionTimeout"</code> kick in.
     * <p>
     * If not set the configured value from property <code>"com.openexchange.mail.filter.connectionTimeout"</code> is used.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">Note: Timeout is required to be set prior to {@link #initializeConnection()} is invoked to become effective</div>
     *
     * @param connectTimeout The connect timeout to set
     * @return This SIEVE handler with new behavior applied
     */
    public SieveHandler setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout < 0 ? null : Long.valueOf(connectTimeout);
        return this;
    }

    /**
     * Sets the read timeout in milliseconds, which enables/disables SO_TIMEOUT. A timeout of zero is interpreted as an infinite timeout.
     * A value of less than zero lets <code>"com.openexchange.mail.filter.connectionTimeout"</code> kick in.
     * <p>
     * If not set the configured value from property <code>"com.openexchange.mail.filter.connectionTimeout"</code> is used.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">Note: Timeout is required to be set prior to {@link #initializeConnection()} is invoked to become effective</div>
     *
     * @param readTimeout The read timeout to set
     * @return This SIEVE handler with new behavior applied
     */
    public SieveHandler setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout < 0 ? null : Long.valueOf(readTimeout);
        return this;
    }

    /**
     * Gets the connect timeout in milliseconds to use, which is used when connecting the socket to the server.
     *
     * @param configuredTimeout The configured timeout through property <code>"com.openexchange.mail.filter.connectionTimeout"</code>
     * @return The connect timeout
     */
    private int getEffectiveConnectTimeout(int configuredTimeout) {
        Long connectTimeout = this.connectTimeout;
        return null == connectTimeout ? configuredTimeout : connectTimeout.intValue();
    }

    /**
     * Gets the read timeout in milliseconds used to enable/disable SO_TIMEOUT.
     *
     * @param configuredTimeout The configured timeout through property <code>"com.openexchange.mail.filter.connectionTimeout"</code>
     * @return The read timeout
     */
    private int getEffectiveReadTimeout(int configuredTimeout) {
        Long readTimeout = this.readTimeout;
        return null == readTimeout ? configuredTimeout : readTimeout.intValue();
    }

    /**
     * Use this function to initialize the connection. It will get the welcome messages from the server, parse the capabilities and login
     * the user.
     *
     * @throws IOException
     * @throws UnsupportedEncodingException
     * @throws OXSieveHandlerException
     * @throws OXSieveHandlerInvalidCredentialsException
     */
    public void initializeConnection() throws IOException, OXSieveHandlerException, UnsupportedEncodingException, OXSieveHandlerInvalidCredentialsException {
        final LeanConfigurationService mailFilterConfig = Services.getService(LeanConfigurationService.class);

        /*
         * Check if still marked as temporary down
         */
        HostAndPort hostAndPort = new HostAndPort(sieve_host, sieve_host_port);
        int tmpDownTimeout = mailFilterConfig.getIntProperty(userId, contextId, MailFilterProperty.tempDownTimeout);
        if (tmpDownTimeout > 0) {
            Long range = TIMED_OUT_SERVERS.get(hostAndPort);
            if (range != null) {
                long duration = System.currentTimeMillis() - range.longValue();
                if (duration <= tmpDownTimeout) {
                    /*
                     * Still considered as being temporary broken
                     */
                    throw new java.net.SocketTimeoutException("Sieve server still considered as down since " + duration + "msec");
                }
                TIMED_OUT_SERVERS.remove(hostAndPort);
            }
        }

        measureStart();

        useSIEVEResponseCodes = mailFilterConfig.getBooleanProperty(userId, contextId, MailFilterProperty.useSIEVEResponseCodes);

        s_sieve = new Socket();
        /*
         * Connect with the connect-timeout of the config file or the one which was explicitly set
         */
        int configuredTimeout = mailFilterConfig.getIntProperty(userId, contextId, MailFilterProperty.connectionTimeout);
        {
            int effectiveConnectTimeout = getEffectiveConnectTimeout(configuredTimeout);
            try {
                connectSocket(effectiveConnectTimeout);
            } catch (java.net.ConnectException e) {
                // Connection refused remotely
                throw new OXSieveHandlerException("Sieve server not reachable. Please disable Sieve service if not supported by mail backend.", sieve_host, sieve_host_port, null, e);
            } catch (java.net.SocketTimeoutException e) {
                // Connection attempt timed out
                if (tmpDownTimeout > 0 && effectiveConnectTimeout >= configuredTimeout) {
                    TIMED_OUT_SERVERS.put(hostAndPort, Long.valueOf(System.currentTimeMillis()));
                }
                throw e;
            }
        }
        /*
         * Set timeout to the one specified in the config file or the one which was explicitly set
         */
        s_sieve.setSoTimeout(getEffectiveReadTimeout(configuredTimeout));
        bis_sieve = optionalCircuitBreaker.isPresent() ? new FailsafeCircuitBreakerBufferedReader(new InputStreamReader(s_sieve.getInputStream(), UTF_8), optionalCircuitBreaker.get()) : new BufferedReader(new InputStreamReader(s_sieve.getInputStream(), UTF_8));
        bos_sieve = optionalCircuitBreaker.isPresent() ? new FailsafeCircuitBreakerBufferedOutputStream(s_sieve.getOutputStream(), optionalCircuitBreaker.get()) : new BufferedOutputStream(s_sieve.getOutputStream());

        if (!getServerWelcome()) {
            throw new OXSieveHandlerException("No welcome from server", sieve_host, sieve_host_port, null);
        }
        log.debug("Got welcome from sieve");
        measureEnd("getServerWelcome");
        /*
         * Capabilities read
         */
        if (false == onlyWelcome) {
            /*
             * Further communication dependent on capabilities
             */
            measureStart();
            List<String> sasl = capa.getSasl();
            measureEnd("capa.getSasl");

            final boolean tlsenabled = mailFilterConfig.getBooleanProperty(userId, contextId, MailFilterProperty.tls);

            final boolean issueTLS = tlsenabled && capa.getStarttls().booleanValue();

            punycode = mailFilterConfig.getBooleanProperty(userId, contextId, MailFilterProperty.punycode);

            final StringBuilder commandBuilder = new StringBuilder(64);

            if (issueTLS) {
                /*-
                 * Switch to TLS and re-fetch capabilities
                 *
                 *
                 * Send STARTTLS
                 *
                 * C: STARTTLS
                 * S: OK
                 * <TLS negotiation, further commands are under TLS layer>
                 * S: "IMPLEMENTATION" "Example1 ManageSieved v001"
                 * S: "SASL" "PLAIN"
                 * S: "SIEVE" "fileinto vacation"
                 * S: OK
                 */
                measureStart();
                bos_sieve.write(commandBuilder.append("STARTTLS").append(CRLF).toString().getBytes(UTF_8));
                bos_sieve.flush();
                measureEnd("startTLS");
                commandBuilder.setLength(0);
                /*
                 * Expect OK
                 */
                while (true) {
                    final String temp = readResponseLine(null);
                    if (null == temp) {
                        throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
                    } else if (temp.startsWith(SIEVE_OK)) {
                        break;
                    } else if (temp.startsWith(SIEVE_AUTH_FAILED)) {
                        throw new OXSieveHandlerException("can't auth to SIEVE ", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
                    }
                }
                /*
                 * Switch to TLS
                 */
                String[] protocols = Strings.splitByComma(MailFilterProperty.protocols.getDefaultValue().toString());
                {
                    String sProtocols = mailFilterConfig.getProperty(userId, contextId, MailFilterProperty.protocols);
                    if (Strings.isNotEmpty(sProtocols)) {
                        protocols = Strings.splitByComma(sProtocols.trim());
                    }
                }
                s_sieve = SocketFetcher.startTLS(s_sieve, sieve_host, protocols);
                bis_sieve = optionalCircuitBreaker.isPresent() ? new FailsafeCircuitBreakerBufferedReader(new InputStreamReader(s_sieve.getInputStream(), UTF_8), optionalCircuitBreaker.get()) : new BufferedReader(new InputStreamReader(s_sieve.getInputStream(), UTF_8));
                bos_sieve = optionalCircuitBreaker.isPresent() ? new FailsafeCircuitBreakerBufferedOutputStream(s_sieve.getOutputStream(), optionalCircuitBreaker.get()) : new BufferedOutputStream(s_sieve.getOutputStream());
                /*
                 * Fire CAPABILITY command but only for cyrus that is not sieve draft conform to sent CAPABILITY response again
                 * directly as response for the STARTTLS command.
                 */
                final String implementation = capa.getImplementation();
                if (implementation.matches(mailFilterConfig.getProperty(userId, contextId, MailFilterProperty.nonRFCCompliantTLSRegex))) {
                    measureStart();
                    bos_sieve.write(commandBuilder.append("CAPABILITY").append(CRLF).toString().getBytes(UTF_8));
                    bos_sieve.flush();
                    measureEnd("capability");
                    commandBuilder.setLength(0);
                }
                /*
                 * Read capabilities
                 */
                measureStart();
                if (!getServerWelcome()) {
                    throw new OXSieveHandlerException("No TLS negotiation from server", sieve_host, sieve_host_port, null);
                }
                measureEnd("tlsNegotiation");
                sasl = capa.getSasl();
            }

            /*
             * Check for supported authentication support
             */
            if (null == sasl) {
                String message = new StringBuilder(64).append("The server doesn't support any SASL authentication mechanism over a ").append(issueTLS ? "TLS" : "plain-text").append(" connection.").toString();
                throw new OXSieveHandlerException(message, sieve_host, sieve_host_port, null);
            }
            measureStart();
            PreferredSASLMech saslMech = getPreferredSASLMechanism(mailFilterConfig, sasl);

            if (!sasl.contains(saslMech.name())) {
                String message = new StringBuilder(64).append("The server doesn't support ").append(saslMech.name()).append(" authentication over a ").append(issueTLS ? "TLS" : "plain-text").append(" connection.").toString();
                throw new OXSieveHandlerException(message, sieve_host, sieve_host_port, null);
            }

            int configuredAuthTimeout = mailFilterConfig.getIntProperty(userId, contextId, MailFilterProperty.authTimeout);
            if (!selectAuth(saslMech, commandBuilder, configuredAuthTimeout)) {
                throw new OXSieveHandlerInvalidCredentialsException("Authentication failed");
            }

            /*
             * Fetch capabilities again
             */
            fetchCapabilities();

            log.debug("Authentication to sieve successful");
            measureEnd("selectAuth");
        }
    }

    private void connectSocket(int connectTimeout) throws IOException {
        if (optionalCircuitBreaker.isPresent()) {
            CircuitBreakerInfo circuitBreakerInfo = optionalCircuitBreaker.get();
            try {
                Failsafe.with(circuitBreakerInfo.getCircuitBreaker()).get(new CircuitBreakerConnectCallable(s_sieve, new InetSocketAddress(sieve_host, sieve_host_port), connectTimeout));
            } catch (CircuitBreakerOpenException e) {
                // Circuit breaker is open
                circuitBreakerInfo.incrementDenials();
                throw new IOException("Denied connect attempt to SIEVE server since circuit breaker is open.", e);
            } catch (FailsafeException e) {
                // Runnable failed with a checked exception
                Throwable failure = e.getCause();
                if (failure instanceof IOException) {
                    throw (IOException) failure;
                }
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                throw new IOException(failure);
            }
        } else {
            s_sieve.connect(new InetSocketAddress(sieve_host, sieve_host_port), connectTimeout);
        }
    }

    /**
     * Creates a helper for measuring metrics
     *
     * @return The {@link MetricHelper}
     */
    public MetricHelper createMetricHelper() {
        return new MetricHelper(this);
    }

    private static final List<Class<? extends Exception>> NETWORK_COMMUNICATION_ERRORS = ImmutableList.of(
        java.net.SocketTimeoutException.class,
        java.io.EOFException.class);

    public String readResponseLine(MetricHelper helper) throws IOException {
        if (helper == null || helper.dontMeasureRead()) {
            return bis_sieve.readLine();
        }

        long start = System.nanoTime();
        try {
            String responseLine = bis_sieve.readLine();
            if (responseLine != null) {
                helper.updateRequestTimer(System.nanoTime() - start, TimeUnit.NANOSECONDS, "OK");
            }
            return responseLine;
        } catch (IOException e) {
            String status = "UNKNOWN_ERROR";
            if (isEitherOf(e, NETWORK_COMMUNICATION_ERRORS)) {
                status = "COMMUNICATION_ERROR";
            }
            helper.updateRequestTimer(System.nanoTime() - start, TimeUnit.NANOSECONDS, status);
            throw e;
        }
    }

    private int readResponseCharacter(MetricHelper helper) throws IOException {
        return readResponseCharacter(bis_sieve, helper);
    }

    private int readResponseCharacter(Reader reader, MetricHelper helper) throws IOException {
        if (helper == null || helper.dontMeasureRead()) {
            return reader.read();
        }

        long start = System.nanoTime();
        try {
            int responseCharacter = reader.read();
            if (responseCharacter >= 0) {
                helper.updateRequestTimer(System.nanoTime() - start, TimeUnit.NANOSECONDS, "OK");
            }
            return responseCharacter;
        } catch (IOException e) {
            String status = "UNKNOWN_ERROR";
            if (isEitherOf(e, NETWORK_COMMUNICATION_ERRORS)) {
                status = "COMMUNICATION_ERROR";
            }
            helper.updateRequestTimer(System.nanoTime() - start, TimeUnit.NANOSECONDS, status);
            throw e;
        }
    }

    private int readResponseCharacters(char[] buf, int len, MetricHelper helper) throws IOException {
        if (helper == null || helper.dontMeasureRead()) {
            return bis_sieve.read(buf, 0, len);
        }

        long start = System.nanoTime();
        try {
            int read = bis_sieve.read(buf, 0, len);
            if (read >= 0) {
                helper.updateRequestTimer(System.nanoTime() - start, TimeUnit.NANOSECONDS, "OK");
            }
            return read;
        } catch (IOException e) {
            String status = "UNKNOWN_ERROR";
            if (isEitherOf(e, NETWORK_COMMUNICATION_ERRORS)) {
                status = "COMMUNICATION_ERROR";
            }
            helper.updateRequestTimer(System.nanoTime() - start, TimeUnit.NANOSECONDS, status);
            throw e;
        }
    }

    /**
     * @throws OXSieveHandlerException
     * @throws IOException
     *
     */
    private void fetchCapabilities() throws OXSieveHandlerException, IOException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("Capability not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        MetricHelper helper = createMetricHelper();

        final String capability = SIEVE_CAPABILITY;
        bos_sieve.write(capability.getBytes(UTF_8));
        bos_sieve.flush();

        // Forget previous capabilities
        capa = new Capabilities();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.startsWith(SIEVE_OK)) {
                return;
            }
            if (temp.startsWith(SIEVE_NO)) {
                throw new OXSieveHandlerException("Unable to retrieve sieve capability", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }
            parseCapabilities(temp);
        }
    }

    /**
     * Returns the {@link PreferredSASLMech}
     *
     * @param leanConfigService The {@link LeanConfigurationService}
     * @param sasl The server SASL
     * @return The {@link PreferredSASLMech}
     */
    private PreferredSASLMech getPreferredSASLMechanism(final LeanConfigurationService leanConfigService, List<String> sasl) {
        PreferredSASLMech preferredSASLMechanism = PreferredSASLMech.PLAIN;
        PreferredSASLMech configuredPreferredSASLMechanism = null;
        String psm = null;
        {
            psm = leanConfigService.getProperty(userId, contextId, MailFilterProperty.preferredSaslMech);
            if (Strings.isNotEmpty(psm)) {
                try {
                    configuredPreferredSASLMechanism = PreferredSASLMech.valueOf(psm);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid property '{}' for '{}' found in mailfilter.properties.", psm, MailFilterProperty.preferredSaslMech.getFQPropertyName(), e);
                }
            }
            if (null == configuredPreferredSASLMechanism) {
                // Check old property to keep compatibility
                ConfigurationService service = Services.getService(ConfigurationService.class);
                if (null != service) {
                    boolean preferGSSAPI = service.getBoolProperty("com.openexchange.mail.filter.preferGSSAPI", false);
                    if (preferGSSAPI) {
                        preferredSASLMechanism = PreferredSASLMech.GSSAPI;
                    }
                }
            }
        }
        if (PreferredSASLMech.GSSAPI.equals(configuredPreferredSASLMechanism) && sasl.contains(PreferredSASLMech.GSSAPI.name())) {
            preferredSASLMechanism = PreferredSASLMech.GSSAPI;
        }
        if (PreferredSASLMech.XOAUTH2.equals(configuredPreferredSASLMechanism) && sasl.contains(PreferredSASLMech.XOAUTH2.name())) {
            preferredSASLMechanism = PreferredSASLMech.XOAUTH2;
        }
        if (PreferredSASLMech.OAUTHBEARER.equals(configuredPreferredSASLMechanism) && sasl.contains(PreferredSASLMech.OAUTHBEARER.name())) {
            preferredSASLMechanism = PreferredSASLMech.OAUTHBEARER;
        }
        return preferredSASLMechanism;
    }

    /**
     * Upload this byte[] as sieve script
     *
     * @param script_name
     * @param script
     * @param commandBuilder
     * @throws OXSieveHandlerException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void setScript(final String script_name, final byte[] script, final StringBuilder commandBuilder) throws OXSieveHandlerException, IOException, UnsupportedEncodingException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("Script upload not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        if (script == null) {
            throw new OXSieveHandlerException("Script upload not possible. No Script", sieve_host, sieve_host_port, null);
        }

        String put = commandBuilder.append(SIEVE_PUT).append('\"').append(script_name).append("\" {").append(script.length).append("+}").append(CRLF).toString();
        commandBuilder.setLength(0);

        MetricHelper helper = createMetricHelper();

        bos_sieve.write(put.getBytes(UTF_8));
        bos_sieve.write(script);

        bos_sieve.write(CRLF.getBytes(UTF_8));
        bos_sieve.flush();

        String currentLine = readResponseLine(helper);
        if (null != currentLine && currentLine.startsWith(SIEVE_OK)) {
            return;
        } else if (null != currentLine && currentLine.startsWith("NO ")) {
            final String errorMessage = parseError(currentLine, helper).replaceAll(CRLF, "\n");
            throw new OXSieveHandlerException(errorMessage, sieve_host, sieve_host_port, parseSIEVEResponse(currentLine, errorMessage)).setParseError(true);
        } else {
            throw new OXSieveHandlerException("Unknown response code", sieve_host, sieve_host_port, parseSIEVEResponse(currentLine, null));
        }
    }

    /**
     * Activate/Deactivate sieve script. Is status is true, activate this script.
     *
     * @param script_name
     * @param status
     * @param commandBuilder
     * @throws IOException
     * @throws UnsupportedEncodingException
     * @throws OXSieveHandlerException
     */
    public void setScriptStatus(final String script_name, final boolean status, final StringBuilder commandBuilder) throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (status) {
            activate(script_name, commandBuilder);
        } else {
            deactivate(script_name);
        }
    }

    /**
     * Get the sieveScript, if a script doesn't exists a byte[] with a size of 0 is returned
     *
     * @param script_name
     * @return the read script
     * @throws OXSieveHandlerException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public String getScript(final String script_name) throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (!AUTH) {
            throw new OXSieveHandlerException("Get script not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        MetricHelper helper = createMetricHelper();

        final StringBuilder sb = new StringBuilder(32);
        bos_sieve.write(sb.append(SIEVE_GET_SCRIPT).append('"').append(script_name).append('"').append(CRLF).toString().getBytes(UTF_8));
        bos_sieve.flush();
        /*-
         * If the script does not exist the server MUST reply with a NO response. Upon success a string with the contents of the script is
         * returned followed by a OK response.
         *
         * Example:
         *
         * C: GETSCRIPT "myscript"
         * S: {54+}
         * S: #this is my wonderful script
         * S: reject "I reject all";
         * S:
         * S: OK
         */
        {
            final String firstLine = readResponseLine(helper);
            if (null == firstLine) {
                // End of the stream reached
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            final int[] parsed = parseFirstLine(firstLine);
            final int respCode = parsed[0];
            if (OK == respCode) {
                return "";
            } else if (NO == respCode) {
                final String errorMessage = parseError(firstLine, helper).replaceAll(CRLF, "\n");
                throw new OXSieveHandlerException(errorMessage, sieve_host, sieve_host_port, parseSIEVEResponse(firstLine, errorMessage));
            }
            sb.setLength(0);
            sb.ensureCapacity(parsed[1]);
        }
        boolean inQuote = false;
        boolean okStart = false;
        boolean inComment = false;
        while (true) {
            int ch = readResponseCharacter(helper);
            switch (ch) {
                case -1:
                    // End of stream
                    throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
                case '\\': {
                    okStart = false;
                    sb.append((char) ch);
                    final StringBuilder octetBuilder = new StringBuilder();
                    int limit = 0;
                    int index = 0;
                    do {
                        ch = readResponseCharacter(helper);
                        if (ch == -1) {
                            // End of stream
                            throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
                        } else if (ch >= 48 && ch <= 55) {
                            octetBuilder.append((char) ch);
                            limit = 3;
                            index++;
                        } else {
                            sb.append((char) ch);
                        }
                    } while (index < limit);
                    if (octetBuilder.length() > 1) {
                        sb.setLength(sb.length() - 1);
                        sb.append((char) Integer.parseInt(octetBuilder.toString()));
                    }
                }
                    break;
                case '"': {
                    if (!inComment) {
                        if (inQuote) {
                            inQuote = false;
                        } else {
                            inQuote = true;
                        }
                    }
                    okStart = false;
                    sb.append((char) ch);
                }
                    break;
                case 'O': // OK\r\n
                {
                    if (!inQuote) {
                        okStart = true;
                    }
                    sb.append((char) ch);
                }
                    break;
                case 'K': // OK\r\n
                {
                    if (!inQuote && okStart && !inComment) {
                        sb.setLength(sb.length() - 1);
                        consumeUntilCRLF(helper); // OK "Getscript completed."\r\n
                        return returnScript(sb);
                    }
                    okStart = false;
                    sb.append((char) ch);
                }
                    break;
                case '#': {
                    if (!inQuote) {
                        inComment = true;
                    }
                    sb.append((char) ch);
                }
                    break;
                case '\n': {
                    if (inComment) {
                        inComment = false;
                    }
                    sb.append((char) ch);
                }
                    break;
                default:
                    okStart = false;
                    sb.append((char) ch);
                    break;
            }
        }
        /*-
         *
         *
        boolean firstread = true;
        while (true) {
            final String temp = bis_sieve.readLine();
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port);
            }
            if (temp.startsWith(SIEVE_OK)) {
                // We have to strip off the last trailing CRLF...
                return sb.substring(0, sb.length() - 2);
            } else if (temp.startsWith(SIEVE_NO)) {
                return "";
            }
            // The first line contains the length of the following byte set, we don't need this
            // information here and so strip it off...
            if (firstread) {
                firstread = false;
            } else {
                sb.append(temp);
                sb.append(CRLF);
            }
        }
         */
    }

    private static String returnScript(final StringBuilder sb) {
        int length = sb.length();
        if (length >= 2 && sb.charAt(length - 2) == '\r' && sb.charAt(length - 1) == '\n') {
            // We have to strip off the last trailing CRLF...
            return sb.substring(0, length - 2);
        }
        return sb.toString();
    }

    private void consumeUntilCRLF(MetricHelper helper) throws IOException, OXSieveHandlerException {
        Reader in = bis_sieve;
        boolean doRead = true;
        int c1 = -1;

        while (doRead && (c1 = readResponseCharacter(in, helper)) >= 0) {
            if (c1 == '\n') {
                doRead = false;
            } else if (c1 == '\r') {
                // Got CR, is the next char LF?
                boolean twoCRs = false;
                if (in.markSupported()) {
                    in.mark(2);
                }
                int c2 = readResponseCharacter(in, helper);
                if (c2 == '\r') {
                    // Discard extraneous CR
                    twoCRs = true;
                    c2 = readResponseCharacter(in, helper);
                }
                if (c2 != '\n') {
                    // If the reader supports it (which we hope will always be the case), reset to after the first CR.
                    // Otherwise, we wrap a PushbackReader around the stream so we can unread the characters we don't need.
                    if (in.markSupported()) { // Always true for BufferedReader
                        in.reset();
                    } else {
                        if (!(in instanceof PushbackReader)) {
                            in = new PushbackReader(in, 2);
                        }
                        if (c2 != -1) {
                            ((PushbackReader) in).unread(c2);
                        }
                        if (twoCRs) {
                            ((PushbackReader) in).unread('\r');
                        }
                    }
                }
                doRead = false;
            }
        }
        if (c1 < 0) {
            // End of stream
            throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
        }
    }

    /**
     * Get the list of sieveScripts
     *
     * @return List of scripts
     * @throws IOException
     * @throws UnsupportedEncodingException
     * @throws OXSieveHandlerException
     */
    public List<String> getScriptList() throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("List scripts not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        MetricHelper helper = createMetricHelper();
        List<String> list = null;

        final String active = SIEVE_LIST;
        bos_sieve.write(active.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.startsWith(SIEVE_OK)) {
                return list == null ? Collections.emptyList() : list;
            }
            if (temp.startsWith(SIEVE_NO)) {
                throw new OXSieveHandlerException("Sieve has no script list", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }
            // Here we strip off the leading and trailing " and the ACTIVE at the
            // end if it occurs. We want a list of the script names only
            final String scriptname = temp.substring(temp.indexOf('\"') + 1, temp.lastIndexOf('\"'));
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(scriptname);
        }

    }

    /**
     * Gets the name of the currently active sieve script.
     *
     * @return The name of the active script, or <code>null</code> if no script is active
     * @throws IOException
     * @throws UnsupportedEncodingException
     * @throws OXSieveHandlerException
     */
    public String getActiveScript() throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        return getActiveScript(null);
    }

    /**
     * Gets the name of the currently active sieve script.
     *
     * @param optExpectedName The optional expected name or <code>null</code>
     * @return The name of the active script, or <code>null</code> if no script is active
     * @throws IOException
     * @throws UnsupportedEncodingException
     * @throws OXSieveHandlerException
     */
    public String getActiveScript(String optExpectedName) throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("List scripts not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        MetricHelper helper = createMetricHelper();
        String scriptname = null;

        final String active = SIEVE_LIST;
        bos_sieve.write(active.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.startsWith(SIEVE_OK)) {
                return scriptname;
            }
            if (temp.startsWith(SIEVE_NO)) {
                throw new OXSieveHandlerException("Sieve has no script list", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }

            int count = -1;
            if (temp.startsWith("{")) {
                count = Integer.parseInt(temp.substring(1, temp.lastIndexOf('}')));
                temp = readResponseLine(helper);
                if (null == temp) {
                    throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
                }
            }

            if (temp.endsWith(" ACTIVE")) {
                if (count >= 0) {
                    // Literal
                    scriptname = temp.substring(0, count);
                } else if (temp.startsWith("\"")) {
                    // QuotedString
                    scriptname = temp.substring(1, temp.lastIndexOf('\"'));
                } else {
                    // Atom
                    scriptname = readAtom(temp);
                }
                if (optExpectedName != null && !scriptname.equals(optExpectedName)) {
                    throw new OXSieveHandlerException("Currently active script \"" + scriptname + "\" is not the expected one \"" + optExpectedName + '\"', sieve_host, sieve_host_port, null);
                }
            }
        }
    }

    /**
     * Lists the scripts the user has on the server.
     *
     * @return The listed scripts
     * @throws OXSieveHandlerException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public List<SieveScript> listScripts() throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("List scripts not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        MetricHelper helper = createMetricHelper();
        List<SieveScript> scrips = null;

        bos_sieve.write(SIEVE_LIST.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.startsWith(SIEVE_OK)) {
                return scrips == null ? Collections.emptyList() : scrips;
            }
            if (temp.startsWith(SIEVE_NO)) {
                throw new OXSieveHandlerException("Sieve has no script list", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }

            String scriptName;
            if (temp.startsWith("\"")) {
                // QuotedString
                scriptName = temp.substring(1, temp.lastIndexOf('\"'));
            } else if (temp.startsWith("{")) {
                // Literal
                int count = Integer.parseInt(temp.substring(1, temp.lastIndexOf('}')));
                temp = readResponseLine(helper);
                if (null == temp) {
                    throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
                }
                scriptName = temp.substring(0, count);
            } else {
                // Atom
                scriptName = readAtom(temp);
            }

            boolean active = temp.endsWith(" ACTIVE");
            if (scrips == null) {
                scrips = new ArrayList<>(4);
            }
            scrips.add(new SieveScript(scriptName, active));
        }
    }

    private static String ASTRING_CHAR_DELIM = " (){%*\"\\";

    private static String readAtom(String line) {
        int end = 0;
        char c;
        while (end < line.length() && (c = line.charAt(end)) >= ' ' && ASTRING_CHAR_DELIM.indexOf(c) < 0 && c != 0x7F) {
            end++;
        }
        return line.substring(0, end);
    }

    /**
     * Remove the sieve script. If the script is active it is deactivated before removing
     *
     * @param script_name
     * @throws IOException
     * @throws UnsupportedEncodingException
     * @throws OXSieveHandlerException
     */
    public void remove(final String script_name) throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("Delete a script not possible. Auth first.", sieve_host, sieve_host_port, null);
        }
        if (null == script_name) {
            throw new OXSieveHandlerException("Script can't be removed", sieve_host, sieve_host_port, null);
        }

        final StringBuilder commandBuilder = new StringBuilder(64);

        setScriptStatus(script_name, false, commandBuilder);

        final String delete = commandBuilder.append(SIEVE_DELETE).append('"').append(script_name).append('"').append(CRLF).toString();
        commandBuilder.setLength(0);

        MetricHelper helper = createMetricHelper();

        bos_sieve.write(delete.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.startsWith(SIEVE_OK)) {
                return;
            } else if (temp.startsWith(SIEVE_NO)) {
                throw new OXSieveHandlerException("Script can't be removed", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }
        }
    }

    /**
     * Close socket-connection to sieve
     *
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    public void close() throws IOException, UnsupportedEncodingException {
        if (null != bos_sieve) {
            bos_sieve.write(SIEVE_LOGOUT.getBytes(UTF_8));
            bos_sieve.flush();
        }
        if (null != s_sieve) {
            s_sieve.close();
        }
    }

    private boolean getServerWelcome() throws UnknownHostException, IOException, OXSieveHandlerException {
        capa = new Capabilities();

        MetricHelper helper = createMetricHelper();
        while (true) {
            final String test = readResponseLine(helper);
            if (null == test) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (test.startsWith(SIEVE_OK)) {
                return true;
            } else if (test.startsWith(SIEVE_NO)) {
                AUTH = false;
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, parseSIEVEResponse(test, null));
            } else {
                parseCapabilities(test);
            }
        }
    }

    private boolean authXOAUTH2(final StringBuilder commandBuilder) throws IOException, UnsupportedEncodingException {
        if (Strings.isEmpty(oauthToken)) {
            return false;
        }

        String resp = "user=" + sieve_user + "\001auth=Bearer " + oauthToken + "\001\001";
        String irs = Base64.encode(Charsets.toAsciiBytes(resp));

        {
            String auth_mech_string = commandBuilder.append(SIEVE_AUTH).append("\"XOAUTH2\" {").append(irs.length()).append("+}").append(CRLF).toString();
            commandBuilder.setLength(0);
            bos_sieve.write(auth_mech_string.getBytes(UTF_8));
        }

        MetricHelper helper = createMetricHelper();

        bos_sieve.write(irs.getBytes(UTF_8));
        bos_sieve.write(CRLF.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null != temp) {
                if (temp.startsWith(SIEVE_OK)) {
                    AUTH = true;
                    return true;
                } else if (temp.startsWith(SIEVE_NO)) {
                    AUTH = false;
                    return false;
                }
            } else {
                AUTH = false;
                return false;
            }
        }
    }

    private boolean authOAUTHBEARER(final StringBuilder commandBuilder) throws IOException, UnsupportedEncodingException {
        if (Strings.isEmpty(oauthToken)) {
            return false;
        }

        String resp = "n,a=" + sieve_user + ",\001host=" + sieve_host + "\001port=" + sieve_host_port + "\001auth=Bearer " + oauthToken + "\001\001";
        String irs = Base64.encode(Charsets.toAsciiBytes(resp));

        {
            String auth_mech_string = commandBuilder.append(SIEVE_AUTH).append("\"OAUTHBEARER\" {").append(irs.length()).append("+}").append(CRLF).toString();
            commandBuilder.setLength(0);
            bos_sieve.write(auth_mech_string.getBytes());
        }

        MetricHelper helper = createMetricHelper();

        bos_sieve.write(irs.getBytes());
        bos_sieve.write(CRLF.getBytes());
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null != temp) {
                if (temp.startsWith(SIEVE_OK)) {
                    AUTH = true;
                    return true;
                } else if (temp.startsWith(SIEVE_NO)) {
                    AUTH = false;
                    return false;
                }
            } else {
                AUTH = false;
                return false;
            }
        }
    }

    private boolean authGSSAPI() throws IOException, UnsupportedEncodingException, OXSieveHandlerException {
        final String authname = getRightEncodedString(sieve_auth, "authname");

        final HashMap<String, String> saslProps = new HashMap<>();

        // Mutual authentication
        saslProps.put("javax.security.sasl.server.authentication", "true");
        /**
         * TODO: do we want encrypted transfer after auth without ssl?
         * if yes, we need to wrap the whole rest of the communication with sc.wrap/sc.unwrap
         * and qop to auth-int or auth-conf
         */
        saslProps.put("javax.security.sasl.qop", "auth");

        SaslClient sc = null;
        try {
            sc = Sasl.createSaslClient(new String[] { "GSSAPI" }, authname, "sieve", sieve_host, saslProps, null);
            if (sc == null) {
                log.error("Unable to crate a SaslClient");
                return false;
            }
            byte[] response = sc.evaluateChallenge(new byte[0]);
            String b64resp = com.openexchange.tools.encoding.Base64.encode(response);

            MetricHelper helper = createMetricHelper();

            bos_sieve.write(new String(SIEVE_AUTH + "\"GSSAPI\" {" + b64resp.length() + "+}").getBytes(UTF_8));
            bos_sieve.write(CRLF.getBytes(UTF_8));
            bos_sieve.flush();
            bos_sieve.write(b64resp.getBytes(UTF_8));
            bos_sieve.write(CRLF.getBytes(UTF_8));
            bos_sieve.flush();

            while (true) {
                String temp = readResponseLine(helper);
                if (null != temp) {
                    if (temp.startsWith(SIEVE_OK)) {
                        AUTH = true;
                        return true;
                    } else if (temp.startsWith(SIEVE_NO)) {
                        AUTH = false;
                        return false;
                    } else if (temp.length() == 0) {
                        // cyrus managesieve sends empty answers and it looks like these have to be ignored?!?
                        continue;
                    } else {
                        // continuation
                        // -> https://tools.ietf.org/html/rfc5804#section-1.2
                        byte[] cont;
                        // some implementations such as cyrus timsieved always use literals
                        if (temp.startsWith("{")) {
                            int cnt = Integer.parseInt(temp.substring(1, temp.length() - 1));
                            char[] buf = new char[cnt];
                            int read = readResponseCharacters(buf, cnt, helper);
                            cont = com.openexchange.tools.encoding.Base64.decode(new String(buf, 0, read));
                        } else {
                            // dovecot managesieve sends quoted strings
                            cont = com.openexchange.tools.encoding.Base64.decode(temp.replaceAll("\"", ""));
                        }
                        if (sc.isComplete()) {
                            AUTH = true;
                            return true;
                        }
                        response = sc.evaluateChallenge(cont);
                        String respLiteral;
                        if (null == response || response.length == 0) {
                            respLiteral = "{0+}";
                        } else {
                            b64resp = com.openexchange.tools.encoding.Base64.encode(response);
                            respLiteral = "{" + b64resp.length() + "+}";
                        }
                        bos_sieve.write(new String(respLiteral + CRLF).getBytes(UTF_8));
                        if (null != response && response.length > 0) {
                            bos_sieve.write(new String(b64resp + CRLF).getBytes(UTF_8));
                        } else {
                            bos_sieve.write(CRLF.getBytes(UTF_8));
                        }
                        bos_sieve.flush();
                    }
                } else {
                    AUTH = false;
                    return false;
                }
            }
        } catch (SaslException e) {
            log.error("SASL challenge failed", e);
            throw e;
        } finally {
            if (null != sc) {
                sc.dispose();
            }
        }
    }

    private boolean authPLAIN(final StringBuilder commandBuilder) throws IOException, UnsupportedEncodingException, OXSieveHandlerException {
        final String username = getRightEncodedString(sieve_user, "username");
        final String authname = getRightEncodedString(sieve_auth, "authname");
        final String to64 = commandBuilder.append(username).append('\0').append(authname).append('\0').append(sieve_auth_passwd).toString();
        commandBuilder.setLength(0);

        final String user_auth_pass_64 = commandBuilder.append(convertStringToBase64(to64, sieve_auth_enc)).append(CRLF).toString();
        commandBuilder.setLength(0);

        final String auth_mech_string = commandBuilder.append(SIEVE_AUTH).append("\"PLAIN\" ").toString();
        commandBuilder.setLength(0);

        final String user_size = commandBuilder.append('{').append((user_auth_pass_64.length() - 2)).append("+}").append(CRLF).toString();
        commandBuilder.setLength(0);

        MetricHelper helper = createMetricHelper();

        // We don't need to specify an encoding here because all strings contain only ASCII Text
        bos_sieve.write(auth_mech_string.getBytes(UTF_8));
        bos_sieve.write(user_size.getBytes(UTF_8));
        bos_sieve.write(user_auth_pass_64.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null != temp) {
                if (temp.startsWith(SIEVE_OK)) {
                    AUTH = true;
                    return true;
                } else if (temp.startsWith(SIEVE_NO)) {
                    AUTH = false;
                    return false;
                }
            } else {
                AUTH = false;
                return false;
            }
        }
    }

    // FIXME: Not tested yet
    private boolean authLOGIN(final StringBuilder commandBuilder) throws IOException, OXSieveHandlerException, UnsupportedEncodingException {

        final String auth_mech_string = commandBuilder.append(SIEVE_AUTH).append("\"LOGIN\"").append(CRLF).toString();
        commandBuilder.setLength(0);

        MetricHelper helper = createMetricHelper();

        bos_sieve.write(auth_mech_string.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.endsWith(SIEVE_AUTH_LOGIN_USERNAME)) {
                break;
            } else if (temp.endsWith(SIEVE_AUTH_FAILED)) {
                throw new OXSieveHandlerException("can't auth to SIEVE ", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }
        }

        final String user64 = commandBuilder.append(convertStringToBase64(sieve_auth, sieve_auth_enc)).append(CRLF).toString();
        commandBuilder.setLength(0);

        final String user_size = commandBuilder.append('{').append((user64.length() - 2)).append("+}").append(CRLF).toString();
        commandBuilder.setLength(0);

        bos_sieve.write(user_size.getBytes(UTF_8));
        bos_sieve.write(user64.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.endsWith(SIEVE_AUTH_LOGIN_PASSWORD)) {
                break;
            } else if (temp.endsWith(SIEVE_AUTH_FAILED)) {
                throw new OXSieveHandlerException("can't auth to SIEVE ", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }
        }

        final String pass64 = commandBuilder.append(convertStringToBase64(sieve_auth_passwd, sieve_auth_enc)).append(CRLF).toString();
        commandBuilder.setLength(0);

        final String pass_size = commandBuilder.append('{').append((pass64.length() - 2)).append("+}").append(CRLF).toString();
        commandBuilder.setLength(0);

        bos_sieve.write(pass_size.getBytes(UTF_8));
        bos_sieve.write(pass64.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.startsWith(SIEVE_OK)) {
                AUTH = true;
                return true;
            } else if (temp.startsWith(SIEVE_AUTH_FAILED)) {
                throw new OXSieveHandlerException("can't auth to SIEVE ", sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }
        }
    }

    /**
     * Parse the https://tools.ietf.org/html/rfc5804#section-1.3 Response code of a SIEVE
     * response line.
     *
     * @param multiline The multiline response
     * @param response line
     * @return null, if no response code in line, the @{SIEVEResponse.Code} otherwise.
     */
    public SieveResponse parseSIEVEResponse(final String resp, final String multiline) {
        if (!useSIEVEResponseCodes || null == resp) {
            return null;
        }

        final Pattern p = Pattern.compile("^(?:NO|OK|BYE)\\s+\\((.*?)\\)\\s+(.*$)");
        final Matcher m = p.matcher(resp);
        if (m.matches()) {
            final int gcount = m.groupCount();
            if (gcount > 1) {
                final SieveResponse.Code code = SieveResponse.Code.getCode(m.group(1));
                final String group = m.group(2);
                if (group.startsWith("{")) {
                    // Multi line, use the multiline parsed before here
                    return new SieveResponse(code, multiline);
                }
                // Single line
                return new SieveResponse(code, group);
            }
        }
        return null;
    }

    private void activate(final String sieve_script_name, final StringBuilder commandBuilder) throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("Activate a script not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        final String active = commandBuilder.append(SIEVE_ACTIVE).append('\"').append(sieve_script_name).append('\"').append(CRLF).toString();
        commandBuilder.setLength(0);

        MetricHelper helper = createMetricHelper();

        bos_sieve.write(active.getBytes(UTF_8));
        bos_sieve.flush();

        while (true) {
            final String temp = readResponseLine(helper);
            if (null == temp) {
                throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
            }
            if (temp.startsWith(SIEVE_OK)) {
                return;
            } else if (temp.startsWith(SIEVE_NO)) {
                throw new OXSieveHandlerException("Error while activating script: " + sieve_script_name, sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
            }
        }
    }

    private void deactivate(final String sieve_script_name) throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        if (!(AUTH)) {
            throw new OXSieveHandlerException("Deactivate a script not possible. Auth first.", sieve_host, sieve_host_port, null);
        }

        boolean scriptactive = false;
        if (sieve_script_name.equals(getActiveScript())) {
            scriptactive = true;
        }

        if (scriptactive) {
            MetricHelper helper = createMetricHelper();

            bos_sieve.write(SIEVE_DEACTIVE.getBytes(UTF_8));
            bos_sieve.flush();

            while (true) {
                final String temp = readResponseLine(helper);
                if (null == temp) {
                    throw new OXSieveHandlerException("Communication to SIEVE server aborted. ", sieve_host, sieve_host_port, null);
                }
                if (temp.startsWith(SIEVE_OK)) {
                    return;
                } else if (temp.startsWith(SIEVE_NO)) {
                    throw new OXSieveHandlerException("Error while deactivating script: " + sieve_script_name, sieve_host, sieve_host_port, parseSIEVEResponse(temp, null));
                }
            }
        }
    }

    private String getRightEncodedString(final String username, final String description) throws OXSieveHandlerException {
        final String retval;
        if (this.punycode) {
            try {
                retval = QuotedInternetAddress.toACE(username);
            } catch (AddressException e) {
                final OXSieveHandlerException oxSieveHandlerException = new OXSieveHandlerException("The " + description + " \"" + username + "\" could not be transformed to punycode.", this.sieve_host, this.sieve_host_port, null);
                log.error("", e);
                throw oxSieveHandlerException;
            }
        } else {
            retval = username;
        }
        return retval;
    }

    /**
     * @param auth_mech The selected SASL authentication mechanism
     * @param commandBuilder The command builder to use
     * @param timeout The special read timeout to apply for doing authentication
     * @return
     * @throws IOException
     * @throws UnsupportedEncodingException
     * @throws OXSieveHandlerException
     */
    private boolean selectAuth(PreferredSASLMech auth_mech, StringBuilder commandBuilder, int timeout) throws IOException, UnsupportedEncodingException, OXSieveHandlerException {
        // Adjust timeout if necessary
        synchronized (s_sieve) {
            int toRestore = s_sieve.getSoTimeout();
            if (toRestore > timeout) {
                s_sieve.setSoTimeout(timeout);
            } else {
                toRestore = -1;
            }
            // Perform authentication
            try {
                switch (auth_mech) {
                    case GSSAPI:
                        return authGSSAPI();
                    case LOGIN:
                        return authLOGIN(commandBuilder);
                    case OAUTHBEARER:
                        return authOAUTHBEARER(commandBuilder);
                    case PLAIN:
                        return authPLAIN(commandBuilder);
                    case XOAUTH2:
                        return authXOAUTH2(commandBuilder);
                    default:
                        return false;

                }
            } catch (SocketTimeoutException e) {
                // Read timeout while doing auth
                String message = "Exceeded timeout of " + s_sieve.getSoTimeout() + "milliseconds while performing \"" + auth_mech.name() + "\" SASL authentication for " + sieve_auth;
                throw new OXSieveHandlerException(message, sieve_host, sieve_host_port, null, e).setAuthTimeoutError(true);
            } finally {
                // Restore read timeout
                if (toRestore > 0) {
                    s_sieve.setSoTimeout(toRestore);
                }
            }
        }
    }

    /**
     * Parses the server capabilities
     *
     * @param line The server line
     */
    private void parseCapabilities(String line) {
        int index = line.indexOf(' ');
        if (index < 0) {
            index = line.length();
        }
        String key = line.substring(0, index).trim();
        String value = line.substring(index).trim();
        String token = Strings.unquote(key);
        if (null == token) {
            return;
        }
        WelcomeKeyword keyword;
        try {
            keyword = WelcomeKeyword.valueOf(token);
        } catch (@SuppressWarnings("unused") IllegalArgumentException e) {
            log.debug("Unknown keyword '{}'", token);
            capa.addExtendedProperty(token, Strings.unquote(value));
            return;
        }

        parseWelcomeKeyword(keyword, value);
    }

    /**
     * Parses the {@link WelcomeKeyword} and the specified value
     *
     * @param keyword The {@link WelcomeKeyword} to parse
     * @param value The optional value of the keyword
     */
    private void parseWelcomeKeyword(WelcomeKeyword keyword, String value) {
        String unquoted = Strings.unquote(value);
        switch (keyword) {
            case IMPLEMENTATION:
                capa.setImplementation(unquoted);
                return;
            case MAXREDIRECTS:
                try {
                    capa.addExtendedProperty(keyword.name(), Integer.valueOf(unquoted));
                } catch (NumberFormatException ex) {
                    log.error("Unable to parse '{}' capability value: {}", keyword, unquoted, ex);
                }
                return;
            case SASL: {
                StringTokenizer st = new StringTokenizer(unquoted);
                while (st.hasMoreTokens()) {
                    capa.addSasl(st.nextToken().toUpperCase());
                }
                return;
            }
            case SIEVE: {
                StringTokenizer st = new StringTokenizer(unquoted);
                while (st.hasMoreTokens()) {
                    capa.addSieve(st.nextToken());
                }
                return;
            }
            case STARTTLS:
                capa.setStarttls(Boolean.TRUE);
                return;
            default:
                return;
        }
    }

    /**
     * Parses and gets the error text. Note this will be CRLF terminated.
     *
     * @param actualline
     * @param helper
     * @return
     * @throws IOException
     */
    private String parseError(final String actualline, MetricHelper helper) throws IOException {
        final StringBuilder sb = new StringBuilder();
        final String answer = actualline.substring(3);
        final Matcher matcher = LITERAL_S2C_PATTERN.matcher(answer);
        if (matcher.matches()) {
            final String group = matcher.group(1);
            final int octetsToRead = Integer.parseInt(group);
            final char[] buf = new char[octetsToRead];
            final int octetsRead = readResponseCharacters(buf, octetsToRead, helper);
            if (octetsRead == octetsToRead) {
                sb.append(buf);
            } else {
                sb.append(buf, 0, octetsRead);
            }
            return sb.toString();
        }
        return parseQuotedErrorMessage(answer, helper);
    }

    private String parseQuotedErrorMessage(final String answer, MetricHelper helper) throws IOException {
        StringBuilder inputBuilder = new StringBuilder();
        String line = answer;
        while (line != null) {
            inputBuilder.append("\n").append(line);
            line = readResponseLine(helper);
        }

        char[] msgChars = inputBuilder.toString().toCharArray();
        boolean inQuotes = false;
        boolean inEscape = false;
        StringBuilder errMsgBuilder = new StringBuilder();
        loop: for (char c : msgChars) {
            switch (c) {
                case '"':
                    if (inQuotes) {
                        if (inEscape) {
                            errMsgBuilder.append(c);
                            inEscape = false;
                        } else {
                            inQuotes = false;
                            break loop;
                        }
                    } else {
                        inQuotes = true;
                    }
                    break;

                case '\\':
                    if (inEscape) {
                        errMsgBuilder.append(c);
                        inEscape = false;
                    } else {
                        inEscape = true;
                    }
                    break;

                default:
                    if (inEscape) {
                        inEscape = false;
                    }

                    if (inQuotes) {
                        errMsgBuilder.append(c);
                    }
                    break;
            }
        }

        return errMsgBuilder.toString();
    }

    /**
     * Converts given string to Base64 using given charset encoding.
     *
     * @param toConvert The string to convert to Base64
     * @param charset The charset encoding to use when retrieving bytes from passed string
     * @return The Base64 string
     * @throws UnsupportedCharsetException If charset encoding is unknown
     */
    private static String convertStringToBase64(final String toConvert, final String charset) throws UnsupportedCharsetException {
        final String converted = com.openexchange.tools.encoding.Base64.encode(toConvert.getBytes(Charsets.forName(charset)));
        return converted.replaceAll("(\\r)?\\n", "");
    }

    /**
     * Parses the first line of a SIEVE response.
     * <p>
     * Examples:<br>
     * &nbsp;<code>{54+}</code><br>
     * &nbsp;<code>No {31+}</code><br>
     *
     * @param firstLine The first line
     * @return An array of <code>int</code> with length 2. The first position holds the response code if any available ({@link #NO} or
     *         {@link #OK}), otherwise {@link #UNDEFINED}. The second position holds the number of octets of a following literal or
     *         {@link #UNDEFINED} if no literal is present.
     */
    protected static int[] parseFirstLine(final String firstLine) {
        if (null == firstLine) {
            return null;
        }
        final int[] retval = new int[2];
        retval[0] = UNDEFINED;
        retval[1] = UNDEFINED;
        // Check for starting "NO" or "OK"
        final int length = firstLine.length();
        int index = 0;
        if ('N' == firstLine.charAt(index) && 'O' == firstLine.charAt(index + 1)) {
            retval[0] = NO;
            index += 2;
        } else if ('O' == firstLine.charAt(index) && 'K' == firstLine.charAt(index + 1)) {
            retval[0] = OK;
            index += 2;
        }
        // Check for a literal
        if (index < length) {
            char c;
            while ((index < length) && (((c = firstLine.charAt(index)) == ' ') || (c == '\t'))) {
                index++;
            }
            if (index < length && '{' == firstLine.charAt(index)) {
                // A literal
                retval[1] = parseLiteralLength(readString(index, firstLine));
            }
        }

        return retval;
    }

    private static final Pattern PAT_LIT_LEN = Pattern.compile("\\{([0-9]+)(\\+?)\\}");

    private static int parseLiteralLength(final String respLen) {
        if (null != respLen) {
            final Matcher matcher = PAT_LIT_LEN.matcher(respLen);
            if (matcher.matches()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.error("", e);
                    return -1;
                }
            }
        }
        return -1;
    }

    private static String readString(final int index, final String chars) {
        final int size = chars.length();
        if (index >= size) {
            // already at end of response
            return null;
        }
        // Read until delimiter reached
        final int start = index;
        int i = index;
        char c;
        while ((i < size) && ((c = chars.charAt(i)) != ' ') && (c != '\r') && (c != '\n') && (c != '\t')) {
            i++;
        }
        return toString(chars, start, i);
    }

    /**
     * Convert the chars within the specified range of the given byte array into a {@link String}. The range extends from <code>start</code>
     * till, but not including <code>end</code>.
     */
    private static String toString(final String chars, final int start, final int end) {
        final int size = end - start;
        final StringBuilder theChars = new StringBuilder(size);
        for (int i = 0, j = start; i < size; i++) {
            theChars.append(chars.charAt(j++));
        }
        return theChars.toString();
    }

    /**
     * Gets the capabilities.
     *
     * @return The capabilities
     */
    public Capabilities getCapabilities() {
        return this.capa;
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    public static class MetricHelper {

        private final String host;

        private boolean firstRead;

        MetricHelper(SieveHandler sieveHandler) {
            this(sieveHandler, false);
        }

        /**
         * Initializes a new {@link SieveHandler.MetricHelper}.
         */
        MetricHelper(SieveHandler sieveHandler, boolean groupByEndpoints) {
            super();

            firstRead = true;

            String host = sieveHandler.sieve_host;
            if (groupByEndpoints) {
                String hostAddress;
                try {
                    hostAddress = InetAddress.getByName(sieveHandler.sieve_host).getHostAddress();
                    host = hostAddress + ':' + sieveHandler.sieve_host_port;
                } catch (@SuppressWarnings("unused") UnknownHostException e) {
                    // ignore;
                }
            }
            this.host = host;
        }

        public boolean measureRead() {
            if (firstRead) {
                firstRead = false;
                return true;
            }
            return false;
        }

        public boolean dontMeasureRead() {
            return !measureRead();
        }

        /**
         * Updates the request timer
         *
         * @param duration The duration
         * @param timeUnit The time unit
         * @param status The status
         */
        public void updateRequestTimer(long duration, TimeUnit timeUnit, String status) {
            Timer.builder("appsuite.mailfilter.commands")
            .description("Mail filter commands per host")
            .tags("host", host, "status", status)
            .register(Metrics.globalRegistry);
        }
    }

    private static class CircuitBreakerConnectCallable implements Callable<Void> {

        private final Socket socket;
        private final InetSocketAddress socketAddress;
        private final int connectTimeout;

        CircuitBreakerConnectCallable(Socket socket, InetSocketAddress socketAddress,int connectTimeout) {
            super();
            this.socket = socket;
            this.socketAddress = socketAddress;
            this.connectTimeout = connectTimeout;
        }

        @Override
        public Void call() throws Exception {
            if (connectTimeout >= 0) {
                socket.connect(socketAddress, connectTimeout);
            } else {
                socket.connect(socketAddress);
            }
            return null;
        }
    }

}
