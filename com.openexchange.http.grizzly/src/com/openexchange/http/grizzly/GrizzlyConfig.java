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

package com.openexchange.http.grizzly;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import com.google.common.collect.ImmutableList;
import com.openexchange.config.ConfigTools;
import com.openexchange.config.ConfigurationService;
import com.openexchange.java.Strings;
import com.openexchange.net.IPRange;
import com.openexchange.net.IPTools;

/**
 * {@link GrizzlyConfig} Collects and exposes configuration parameters needed by GrizzlOX
 *
 * @author <a href="mailto:marc	.arens@open-xchange.com">Marc Arens</a>
 */
public class GrizzlyConfig {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GrizzlyConfig.class);

    /**
     * Creates a new builder instance.
     *
     * @return The builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Create an appropriate instance of <code>GrizzlyConfig</code> */
    public static final class Builder {

        private String httpHost = "0.0.0.0";
        private int httpPort = 8009;
        private int httpsPort = 8010;
        private int livenessPort = 8016;
        private boolean isJMXEnabled = false;
        private GrizzlyAccessLogConfig accessLogConfig = GrizzlyAccessLogConfig.NOT_ENABLED_CONFIG;
        private boolean isWebsocketsEnabled = false;
        private boolean isCometEnabled = false;
        private int maxRequestParameters = 1000;
        private String backendRoute = "OX0";
        private boolean isAbsoluteRedirect = false;
        private boolean shutdownFast = false;
        private int awaitShutDownSeconds = 90;
        private int maxHttpHeaderSize = 8192;
        private boolean isSslEnabled = false;
        private String keystorePath = "";
        private String keystorePassword = "";
        private int cookieMaxAge = 604800;
        private int cookieMaxInactivityInterval = 1800;
        private boolean isForceHttps = false;
        private boolean isCookieHttpOnly = true;
        private String contentSecurityPolicy = null;
        private String defaultEncoding = "UTF-8";
        private boolean isConsiderXForwards = false;
        private List<IPRange> knownProxies = Collections.emptyList();
        private String forHeader = "X-Forwarded-For";
        private String protocolHeader = "X-Forwarded-Proto";
        private String httpsProtoValue = "https";
        private int httpProtoPort = 80;
        private int httpsProtoPort = 443;
        private String echoHeader = "X-Echo-Header";
        private String robotsMetaTag = "none";
        private int maxBodySize = 104857600;
        private int maxNumberOfHttpSessions = 250000;
        private List<String> enabledCiphers = null;
        private long wsTimeoutMillis;
        private int sessionExpiryCheckInterval = 60;
        private int sessionUnjoinedThreshold = 120;
        private boolean removeNonAuthenticatedSessions = true;
        private boolean checkTrackingIdInRequestParameters = false;
        private int maxNumberOfConcurrentRequests = 0;
        private boolean supportHierachicalLookupOnNotFound = false;

        /**
         * Initializes a new {@link GrizzlyConfig.Builder}.
         */
        Builder() {
            super();
        }

        /**
         * (Re-)Initializes this builder using specified service.
         *
         * @param configService The service
         */
        public Builder initializeFrom(ConfigurationService configService) {
            // Grizzly properties
            this.isJMXEnabled = configService.getBoolProperty("com.openexchange.http.grizzly.hasJMXEnabled", false);
            this.isWebsocketsEnabled = configService.getBoolProperty("com.openexchange.http.grizzly.hasWebSocketsEnabled", false);
            this.isCometEnabled = configService.getBoolProperty("com.openexchange.http.grizzly.hasCometEnabled", false);
            this.isAbsoluteRedirect = configService.getBoolProperty("com.openexchange.http.grizzly.doAbsoluteRedirect", false);
            this.maxHttpHeaderSize = configService.getIntProperty("com.openexchange.http.grizzly.maxHttpHeaderSize", 8192);
            this.wsTimeoutMillis = configService.getIntProperty("com.openexchange.http.grizzly.wsTimeoutMillis", 900000);
            this.isSslEnabled = configService.getBoolProperty("com.openexchange.http.grizzly.hasSSLEnabled", false);
            this.keystorePath = configService.getProperty("com.openexchange.http.grizzly.keystorePath", "");
            this.keystorePassword = configService.getProperty("com.openexchange.http.grizzly.keystorePassword", "");
            this.sessionExpiryCheckInterval = configService.getIntProperty("com.openexchange.http.grizzly.sessionExpiryCheckInterval", 60);
            this.sessionUnjoinedThreshold = configService.getIntProperty("com.openexchange.http.grizzly.sessionUnjoinedThreshold", 120);
            this.maxNumberOfConcurrentRequests = configService.getIntProperty("com.openexchange.http.grizzly.maxNumberOfConcurrentRequests", 0);
            this.removeNonAuthenticatedSessions = configService.getBoolProperty("com.openexchange.http.grizzly.removeNonAuthenticatedSessions", true);

            // server properties
            this.cookieMaxAge = Integer.valueOf(ConfigTools.parseTimespanSecs(configService.getProperty("com.openexchange.cookie.ttl", "1W"))).intValue();
            this.cookieMaxInactivityInterval = configService.getIntProperty("com.openexchange.servlet.maxInactiveInterval", 1800);
            this.isForceHttps = configService.getBoolProperty("com.openexchange.forceHTTPS", false);
            this.isCookieHttpOnly = configService.getBoolProperty("com.openexchange.cookie.httpOnly", true);
            {
                String csp = configService.getProperty("com.openexchange.servlet.contentSecurityPolicy", "").trim();
                csp = Strings.unquote(csp);
                this.contentSecurityPolicy = csp.trim();
            }
            this.defaultEncoding = configService.getProperty("DefaultEncoding", "UTF-8");
            this.isConsiderXForwards = configService.getBoolProperty("com.openexchange.server.considerXForwards", true);
            String proxyCandidates = configService.getProperty("com.openexchange.server.knownProxies", "");
            this.knownProxies = IPTools.filterIP(proxyCandidates);
            this.forHeader = configService.getProperty("com.openexchange.server.forHeader", "X-Forwarded-For");
            this.protocolHeader = configService.getProperty("com.openexchange.server.protocolHeader", "X-Forwarded-Proto");
            this.httpsProtoValue = configService.getProperty("com.openexchange.server.httpsProtoValue", "https");
            this.httpProtoPort = configService.getIntProperty("com.openexchange.server.httpProtoPort", 80);
            this.httpsProtoPort = configService.getIntProperty("com.openexchange.server.httpsProtoPort", 443);
            this.checkTrackingIdInRequestParameters = configService.getBoolProperty("com.openexchange.server.checkTrackingIdInRequestParameters", false);
            final int configuredMaxBodySize = configService.getIntProperty("com.openexchange.servlet.maxBodySize", 104857600);
            this.maxBodySize = configuredMaxBodySize <= 0 ? Integer.MAX_VALUE : configuredMaxBodySize;
            final int configuredMaxNumberOfHttpSessions = configService.getIntProperty("com.openexchange.servlet.maxActiveSessions", 250000);
            this.maxNumberOfHttpSessions = configuredMaxNumberOfHttpSessions <= 0 ? 0 : configuredMaxNumberOfHttpSessions;
            this.shutdownFast = configService.getBoolProperty("com.openexchange.connector.shutdownFast", false);
            this.awaitShutDownSeconds = configService.getIntProperty("com.openexchange.connector.awaitShutDownSeconds", 90);

            this.httpHost = configService.getProperty("com.openexchange.connector.networkListenerHost", "127.0.0.1");
            // keep backwards compatibility with AJP configuration
            if (httpHost.equals("*")) {
                this.httpHost="0.0.0.0";
            }
            this.httpPort = configService.getIntProperty("com.openexchange.connector.networkListenerPort", 8009);
            this.httpsPort = configService.getIntProperty("com.openexchange.connector.networkSslListenerPort", 8010);
            this.livenessPort = configService.getIntProperty("com.openexchange.connector.livenessPort", 8016);
            this.maxRequestParameters = configService.getIntProperty("com.openexchange.connector.maxRequestParameters", 1000);
            this.backendRoute = configService.getProperty("com.openexchange.server.backendRoute", "OX0");
            this.echoHeader = configService.getProperty("com.openexchange.servlet.echoHeaderName","X-Echo-Header");
            if (configService.getBoolProperty("com.openexchange.servlet.useRobotsMetaTag", true)) {
                String robotsMetaTag = configService.getProperty("com.openexchange.servlet.robotsMetaTag", "none").trim();
                this.robotsMetaTag = robotsMetaTag;
            } else {
                this.robotsMetaTag = null;
            }

            this.enabledCiphers = configService.getProperty("com.openexchange.http.grizzly.enabledCipherSuites", "", ",");

            this.supportHierachicalLookupOnNotFound = configService.getBoolProperty("com.openexchange.http.grizzly.supportHierachicalLookupOnNotFound", false);

            // access log
            {
                boolean accessLogEnabled = configService.getBoolProperty("com.openexchange.http.grizzly.hasAccessLogEnabled", false);
                if (accessLogEnabled) {
                    String tmp = configService.getProperty("com.openexchange.http.grizzly.accesslog.file");
                    if (Strings.isEmpty(tmp)) {
                        LOG.warn("Missing file for access log. Please set property \"{}\" accordingly. Access log will be disabled!", "com.openexchange.http.grizzly.accesslog.file");
                        this.accessLogConfig = GrizzlyAccessLogConfig.NOT_ENABLED_CONFIG;
                    } else {
                        GrizzlyAccessLogConfig.Builder accessLogConfigBuilder = GrizzlyAccessLogConfig.builder(new File(tmp.trim()));

                        {
                            tmp = configService.getProperty("com.openexchange.http.grizzly.accesslog.format", "combined").trim();
                            GrizzlyAccessLogConfig.Format format = GrizzlyAccessLogConfig.Format.formatFor(tmp);
                            if (null == format) {
                                format = GrizzlyAccessLogConfig.Format.COMBINED;
                                LOG.warn("Invalid or unknow format for access log: {}. Using {} as fall-back instead", tmp, format.getId());
                            }
                            accessLogConfigBuilder.withFormat(format);
                        }

                        {
                            tmp = configService.getProperty("com.openexchange.http.grizzly.accesslog.rotate", "none").trim();
                            GrizzlyAccessLogConfig.RotatePolicy rotatePolicy = GrizzlyAccessLogConfig.RotatePolicy.rotatePolicyFor(tmp);
                            if (null == rotatePolicy) {
                                rotatePolicy = GrizzlyAccessLogConfig.RotatePolicy.NONE;
                                LOG.warn("Invalid or unknow rotate policy for access log: {}. Using {} as fall-back instead", tmp, rotatePolicy.getId());
                            }
                            accessLogConfigBuilder.withRotatePolicy(rotatePolicy);
                        }

                        {
                            tmp = configService.getProperty("com.openexchange.http.grizzly.accesslog.synchronous", "false").trim();
                            boolean synchronous = Boolean.parseBoolean(tmp);
                            accessLogConfigBuilder.withSynchronous(synchronous);
                        }

                        {
                            tmp = configService.getProperty("com.openexchange.http.grizzly.accesslog.statusThreshold", "0").trim();
                            try {
                                int statusThreshol = Integer.parseInt(tmp);
                                accessLogConfigBuilder.withStatusThreshold(statusThreshol);
                            } catch (NumberFormatException e) {
                                LOG.warn("Invalid value for property \"{}\". Using no status threshold.", "com.openexchange.http.grizzly.accesslog.statusThreshold");
                            }
                        }

                        {
                            tmp = configService.getProperty("com.openexchange.http.grizzly.accesslog.timezone", "").trim();
                            TimeZone timeZone = Strings.isEmpty(tmp) ? TimeZone.getDefault() : TimeZone.getTimeZone(tmp);
                            accessLogConfigBuilder.withTimeZone(timeZone);
                        }

                        this.accessLogConfig = accessLogConfigBuilder.build();
                    }
                } else {
                    this.accessLogConfig = GrizzlyAccessLogConfig.NOT_ENABLED_CONFIG;
                }
            }

            return this;
        }

        public Builder setHttpHost(String httpHost) {
            this.httpHost = httpHost;
            return this;
        }

        public Builder setHttpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder setHttpsPort(int httpsPort) {
            this.httpsPort = httpsPort;
            return this;
        }

        public Builder setLivenessPort(int livenessPort) {
            this.livenessPort = livenessPort;
            return this;
        }

        public Builder setJMXEnabled(boolean isJMXEnabled) {
            this.isJMXEnabled = isJMXEnabled;
            return this;
        }

        public Builder setAccessLogConfig(GrizzlyAccessLogConfig accessLogConfig) {
            this.accessLogConfig = accessLogConfig;
            return this;
        }

        public Builder setWebsocketsEnabled(boolean isWebsocketsEnabled) {
            this.isWebsocketsEnabled = isWebsocketsEnabled;
            return this;
        }

        public Builder setCometEnabled(boolean isCometEnabled) {
            this.isCometEnabled = isCometEnabled;
            return this;
        }

        public Builder setMaxRequestParameters(int maxRequestParameters) {
            this.maxRequestParameters = maxRequestParameters;
            return this;
        }

        public Builder setBackendRoute(String backendRoute) {
            this.backendRoute = backendRoute;
            return this;
        }

        public Builder setAbsoluteRedirect(boolean isAbsoluteRedirect) {
            this.isAbsoluteRedirect = isAbsoluteRedirect;
            return this;
        }

        public Builder setShutdownFast(boolean shutdownFast) {
            this.shutdownFast = shutdownFast;
            return this;
        }

        public Builder setAwaitShutDownSeconds(int awaitShutDownSeconds) {
            this.awaitShutDownSeconds = awaitShutDownSeconds;
            return this;
        }

        public Builder setMaxHttpHeaderSize(int maxHttpHeaderSize) {
            this.maxHttpHeaderSize = maxHttpHeaderSize;
            return this;
        }

        public Builder setSslEnabled(boolean isSslEnabled) {
            this.isSslEnabled = isSslEnabled;
            return this;
        }

        public Builder setKeystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
            return this;
        }

        public Builder setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        public Builder setCookieMaxAge(int cookieMaxAge) {
            this.cookieMaxAge = cookieMaxAge;
            return this;
        }

        public Builder setCookieMaxInactivityInterval(int cookieMaxInactivityInterval) {
            this.cookieMaxInactivityInterval = cookieMaxInactivityInterval;
            return this;
        }

        public Builder setForceHttps(boolean isForceHttps) {
            this.isForceHttps = isForceHttps;
            return this;
        }

        public Builder setCookieHttpOnly(boolean isCookieHttpOnly) {
            this.isCookieHttpOnly = isCookieHttpOnly;
            return this;
        }

        public Builder setContentSecurityPolicy(String contentSecurityPolicy) {
            this.contentSecurityPolicy = contentSecurityPolicy;
            return this;
        }

        public Builder setDefaultEncoding(String defaultEncoding) {
            this.defaultEncoding = defaultEncoding;
            return this;
        }

        public Builder checkTrackingIdInRequestParameters(boolean checkTrackingIdInRequestParameters) {
            this.checkTrackingIdInRequestParameters = checkTrackingIdInRequestParameters;
            return this;
        }

        public Builder setConsiderXForwards(boolean isConsiderXForwards) {
            this.isConsiderXForwards = isConsiderXForwards;
            return this;
        }

        public Builder setKnownProxies(List<IPRange> knownProxies) {
            this.knownProxies = knownProxies;
            return this;
        }

        public Builder setForHeader(String forHeader) {
            this.forHeader = forHeader;
            return this;
        }

        public Builder setProtocolHeader(String protocolHeader) {
            this.protocolHeader = protocolHeader;
            return this;
        }

        public Builder setHttpsProtoValue(String httpsProtoValue) {
            this.httpsProtoValue = httpsProtoValue;
            return this;
        }

        public Builder setHttpProtoPort(int httpProtoPort) {
            this.httpProtoPort = httpProtoPort;
            return this;
        }

        public Builder setHttpsProtoPort(int httpsProtoPort) {
            this.httpsProtoPort = httpsProtoPort;
            return this;
        }

        public Builder setEchoHeader(String echoHeader) {
            this.echoHeader = echoHeader;
            return this;
        }

        public Builder setRobotsMetaTag(String robotsMetaTag) {
            this.robotsMetaTag = robotsMetaTag;
            return this;
        }

        public Builder setMaxBodySize(int maxBodySize) {
            this.maxBodySize = maxBodySize;
            return this;
        }

        public Builder setMaxNumberOfHttpSessions(int maxNumberOfHttpSessions) {
            this.maxNumberOfHttpSessions = maxNumberOfHttpSessions;
            return this;
        }

        public Builder setEnabledCiphers(List<String> enabledCiphers) {
            this.enabledCiphers = enabledCiphers;
            return this;
        }

        public Builder setWsTimeoutMillis(long wsTimeoutMillis) {
            this.wsTimeoutMillis = wsTimeoutMillis;
            return this;
        }

        public Builder setSessionExpiryCheckInterval(int sessionExpiryCheckInterval) {
            this.sessionExpiryCheckInterval = sessionExpiryCheckInterval;
            return this;
        }

        public Builder setMaxNumberOfConcurrentRequests(int maxNumberOfConcurrentRequests) {
            this.maxNumberOfConcurrentRequests = maxNumberOfConcurrentRequests;
            return this;
        }

        public Builder setSupportHierachicalLookupOnNotFound(boolean supportHierachicalLookupOnNotFound) {
            this.supportHierachicalLookupOnNotFound = supportHierachicalLookupOnNotFound;
            return this;
        }

        public GrizzlyConfig build() {
            return new GrizzlyConfig(httpHost, httpPort, httpsPort, livenessPort, isJMXEnabled, accessLogConfig, isWebsocketsEnabled, isCometEnabled, maxRequestParameters, backendRoute, isAbsoluteRedirect, shutdownFast, awaitShutDownSeconds, maxHttpHeaderSize, isSslEnabled, keystorePath, keystorePassword, sessionExpiryCheckInterval, sessionUnjoinedThreshold, removeNonAuthenticatedSessions, maxNumberOfConcurrentRequests, checkTrackingIdInRequestParameters, cookieMaxAge, cookieMaxInactivityInterval, isForceHttps, isCookieHttpOnly, contentSecurityPolicy, defaultEncoding, isConsiderXForwards, knownProxies, forHeader, protocolHeader, httpsProtoValue, httpProtoPort, httpsProtoPort, echoHeader, robotsMetaTag, maxBodySize, maxNumberOfHttpSessions, enabledCiphers, wsTimeoutMillis, supportHierachicalLookupOnNotFound);
        }
    }

    // ----------------------------------------------------------------------------------------------------

    // Grizzly properties

    /** The host for the HTTP network listener. Default value: 0.0.0.0, bind to every nic of your host. */
    private final String httpHost;

    /** The default port for the HTTP network listener. */
    private final int httpPort;

    /** The default port for the HTTPS network listener. */
    private final int httpsPort;

    /** The default port for the liveness end-point. */
    private final int livenessPort;

    /** Enable Grizzly monitoring via JMX? */
    private final boolean isJMXEnabled;

    /** Enable Grizzly access.log configuration */
    private final GrizzlyAccessLogConfig accessLogConfig;

    /** Enable Bi-directional, full-duplex communications channels over a single TCP connection. */
    private final boolean isWebsocketsEnabled;

    /** Enable Technologies for pseudo real-time communication with the server */
    private final boolean isCometEnabled;

    /** The max. number of allowed request parameters */
    private final int maxRequestParameters;

    /** Unique back-end route for every single back-end behind the load balancer */
    private final String backendRoute;

    /** Do we want to send absolute or relative redirects */
    private final boolean isAbsoluteRedirect;

    /** Do we want a fast or a clean shut-down */
    private final boolean shutdownFast;

    /** The number of seconds to await the shut-down */
    private final int awaitShutDownSeconds;

    /** The maximum header size for an HTTP request in bytes. */
    private final int maxHttpHeaderSize;

    /** Enable SSL */
    private final boolean isSslEnabled;

    /** Path to key-store with X.509 certificates */
    private final String keystorePath;

    /** Keystore password */
    private final String keystorePassword;

    // server properties

    /** Maximal age of a cookie in seconds. A negative value destroys the cookie when the browser exits. A value of 0 deletes the cookie. */
    private final int cookieMaxAge;

    /** Interval between two client requests in seconds until the JSession is declared invalid */
    private final int cookieMaxInactivityInterval;

    /** Marks cookies as secure although the request is insecure e.g. when the back-end is behind a SSL terminating proxy */
    private final boolean isForceHttps;

    /** Make the cookie accessible only via HTTP methods. This prevents JavaScript access to the cookie / cross site scripting */
    private final boolean isCookieHttpOnly;

    /** The the value for the <code>Content-Security-Policy</code> header<br>Please refer to <a href="http://www.html5rocks.com/en/tutorials/security/content-security-policy/">An Introduction to Content Security Policy</a>*/
    private final String contentSecurityPolicy;

    /** Default encoding for incoming HTTP requests, this value must be equal to the web server's default encoding */
    private final String defaultEncoding;

    /** Do we want to consider X-Forward-* Headers */
    private final boolean isConsiderXForwards;

    /** A comma separated list of known proxies */
    private final List<IPRange> knownProxies;

    /**
     * The name of the protocolHeader used to identify the originating IP address of a client connecting to a web server through an HTTP
     * proxy or load balancer
     */
    private final String forHeader;

    /** The name of the protocolHeader used to decide if we are dealing with a in-/secure Request */
    private final String protocolHeader;

    /** The value indicating secure HTTP communication */
    private final String httpsProtoValue;

    /** The port used for HTTP communication */
    private final int httpProtoPort;

    /** The port used for HTTPS communication */
    private final int httpsProtoPort;

    /** The name of the echo header whose value is echoed for each request providing that header, see mod_id for Apache */
    private final String echoHeader;

    /** The value of the <code>X-Robots-Tag</code> response header to set. Default is none. */
    private final String robotsMetaTag;

    /** The maximum allowed size for PUT and POST bodies */
    private final int maxBodySize;

    /** The max. number of HTTP sessions */
    private final int maxNumberOfHttpSessions;

    private final List<String> enabledCiphers;

    /** The Web Socket timeout in milliseconds */
    private final long wsTimeoutMillis;

    /** The interval in seconds when to check for expired/invalid HTTP sessions */
    private final int sessionExpiryCheckInterval;

    /** The threshold in seconds for new/unjoined HTTP sessions */
    private final int sessionUnjoinedThreshold;

    /** The max. number of concurrent HTTP requests that are allowed being processed */
    private final int maxNumberOfConcurrentRequests;

    /** Checks if the special "trackingId" parameter is supposed to be looked-up or always newly created */
    private final boolean checkTrackingIdInRequestParameters;

    /** Checks if hierarchical look-up of "parent" Servlets should be supported. */
    private final boolean supportHierachicalLookupOnNotFound;

    /** Whether to remove non-authenticated HTTP sessions (no Open-Xchange session associated with it) */
    private final boolean removeNonAuthenticatedSessions;

    GrizzlyConfig(String httpHost, int httpPort, int httpsPort, int livenessPort, boolean isJMXEnabled, GrizzlyAccessLogConfig accessLogConfig, boolean isWebsocketsEnabled, boolean isCometEnabled, int maxRequestParameters, String backendRoute, boolean isAbsoluteRedirect, boolean shutdownFast, int awaitShutDownSeconds, int maxHttpHeaderSize, boolean isSslEnabled, String keystorePath, String keystorePassword, int sessionExpiryCheckInterval, int sessionUnjoinedThreshold, boolean removeNonAuthenticatedSessions, int maxNumberOfConcurrentRequests, boolean checkTrackingIdInRequestParameters, int cookieMaxAge, int cookieMaxInactivityInterval, boolean isForceHttps, boolean isCookieHttpOnly, String contentSecurityPolicy, String defaultEncoding, boolean isConsiderXForwards, List<IPRange> knownProxies, String forHeader, String protocolHeader, String httpsProtoValue, int httpProtoPort, int httpsProtoPort, String echoHeader, String robotsMetaTag, int maxBodySize, int maxNumberOfHttpSessions, List<String> enabledCiphers, long wsTimeoutMillis, boolean supportHierachicalLookupOnNotFound) {
        super();
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.livenessPort = livenessPort;
        this.isJMXEnabled = isJMXEnabled;
        this.accessLogConfig = null == accessLogConfig ? GrizzlyAccessLogConfig.NOT_ENABLED_CONFIG : accessLogConfig;
        this.isWebsocketsEnabled = isWebsocketsEnabled;
        this.isCometEnabled = isCometEnabled;
        this.maxRequestParameters = maxRequestParameters;
        this.backendRoute = backendRoute;
        this.isAbsoluteRedirect = isAbsoluteRedirect;
        this.shutdownFast = shutdownFast;
        this.awaitShutDownSeconds = awaitShutDownSeconds;
        this.maxHttpHeaderSize = maxHttpHeaderSize;
        this.isSslEnabled = isSslEnabled;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.sessionExpiryCheckInterval = sessionExpiryCheckInterval;
        this.sessionUnjoinedThreshold = sessionUnjoinedThreshold;
        this.removeNonAuthenticatedSessions = removeNonAuthenticatedSessions;
        this.maxNumberOfConcurrentRequests = maxNumberOfConcurrentRequests;
        this.checkTrackingIdInRequestParameters = checkTrackingIdInRequestParameters;
        this.cookieMaxAge = cookieMaxAge;
        this.cookieMaxInactivityInterval = cookieMaxInactivityInterval;
        this.isForceHttps = isForceHttps;
        this.isCookieHttpOnly = isCookieHttpOnly;
        this.contentSecurityPolicy = contentSecurityPolicy;
        this.defaultEncoding = defaultEncoding;
        this.isConsiderXForwards = isConsiderXForwards;
        this.knownProxies = null == knownProxies || knownProxies.isEmpty() ? Collections.emptyList() : ImmutableList.copyOf(knownProxies);
        this.forHeader = forHeader;
        this.protocolHeader = protocolHeader;
        this.httpsProtoValue = httpsProtoValue;
        this.httpProtoPort = httpProtoPort;
        this.httpsProtoPort = httpsProtoPort;
        this.echoHeader = echoHeader;
        this.robotsMetaTag = robotsMetaTag;
        this.maxBodySize = maxBodySize;
        this.maxNumberOfHttpSessions = maxNumberOfHttpSessions;
        this.enabledCiphers = enabledCiphers;
        this.wsTimeoutMillis = wsTimeoutMillis;
        this.supportHierachicalLookupOnNotFound = supportHierachicalLookupOnNotFound;
    }

    /**
     * Gets the interval in seconds when to check for expired/invalid HTTP sessions
     *
     * @return The interval in seconds when to check for expired/invalid HTTP sessions
     */
    public int getSessionExpiryCheckInterval() {
        return sessionExpiryCheckInterval;
    }

    /**
     * Gets the the threshold in seconds when an HTTP sessions may be safely considered as unjoined
     * <p>
     * An HTTP session is considered as new (or unjoined) if the client does not yet know about the
     * session or if the client chooses not to join the session. For example, if the server used
     * only cookie-based sessions, and the client had disabled the use of cookies, then a session
     * would be new on each request.
     *
     * @return The threshold in seconds for new/unjoined HTTP sessions
     */
    public int getSessionUnjoinedThreshold() {
        return sessionUnjoinedThreshold;
    }

    /**
     * Checks whether HTTP sessions may be considered for removal during clean-up run if there is no associated Open-Xchange session
     * associated with it.
     *
     * @return <code>true</code> to remove non-authenticated sessions; otherwise <code>false</code>
     */
    public boolean isRemoveNonAuthenticatedSessions() {
        return removeNonAuthenticatedSessions;
    }

    /**
     * Gets the max. number of concurrent HTTP requests that are allowed being processed
     *
     * @return The max. number of concurrent HTTP requests that are allowed being processed
     */
    public int getMaxNumberOfConcurrentRequests() {
        return maxNumberOfConcurrentRequests;
    }

    /**
     * Gets the defaultEncoding used for incoming http requests
     *
     * @return The defaultEncoding
     */
    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    /**
     * Gets the HTTP host
     *
     * @return The HTTP host
     */
    public String getHttpHost() {
        return httpHost;
    }

    /**
     * Gets the HTTP port
     *
     * @return The HTTP port
     */
    public int getHttpPort() {
        return httpPort;
    }

    /**
     * Gets the HTTPS port.
     *
     * @return The HTTPS port
     */
    public int getHttpsPort() {
        return httpsPort;
    }

    /**
     * Gets the port for liveness end-point.
     *
     * @return The liveness port
     */
    public int getLivenessPort() {
        return livenessPort;
    }

    /**
     * Gets the hasJMXEnabled
     *
     * @return The hasJMXEnabled
     */
    public boolean isJMXEnabled() {
        return isJMXEnabled;
    }

    /**
     * Gets the Grizzly access.log configuration
     *
     * @return The access.log configuration
     */
    public GrizzlyAccessLogConfig getAccessLogConfig() {
        return accessLogConfig;
    }

    /**
     * Gets the hasWebsocketsEnabled
     *
     * @return The hasWebsocketsEnabled
     */
    public boolean isWebsocketsEnabled() {
        return isWebsocketsEnabled;
    }

    /**
     * Gets the hasCometEnabled
     *
     * @return The hasCometEnabled
     */
    public boolean isCometEnabled() {
        return isCometEnabled;
    }

    /**
     * Gets the maxRequestParameters
     *
     * @return The maxRequestParameters
     */
    public int getMaxRequestParameters() {
        return maxRequestParameters;
    }

    /**
     * Gets the backendRoute
     *
     * @return The backendRoute
     */
    public String getBackendRoute() {
        return backendRoute;
    }

    /**
     * Gets the cookieMaxAge
     *
     * @return The cookieMaxAge
     */
    public int getCookieMaxAge() {
        return cookieMaxAge;
    }

    /**
     * Gets the cookieMaxInactivityInterval in seconds
     *
     * @return The cookieMaxInactivityInterval in seconds
     */
    public int getCookieMaxInactivityInterval() {
        return cookieMaxInactivityInterval;
    }

    /**
     * Gets the isForceHttps
     *
     * @return The isForceHttps
     */
    public boolean isForceHttps() {
        return isForceHttps;
    }

    /**
     * Gets the isCookieHttpOnly
     *
     * @return The isCookieHttpOnly
     */
    public boolean isCookieHttpOnly() {
        return isCookieHttpOnly;
    }

    /**
     * Gets the <code>Content-Security-Policy</code> header.
     * <p>
     * Please refer to <a href="http://www.html5rocks.com/en/tutorials/security/content-security-policy/">An Introduction to Content Security Policy</a>
     *
     * @return The <code>Content-Security-Policy</code> header; default value is <code>null</code>/empty string.
     */
    public String getContentSecurityPolicy() {
        return contentSecurityPolicy;
    }

    /**
     * Gets the known proxies as an immutable list of {@link IPRange}s
     *
     * @return The known proxies as list of {@link IPRange}s or an empty list
     */
    public List<IPRange> getKnownProxies() {
        return knownProxies;
    }

    /**
     * Gets the name of forward for header
     * @return the forwardHeader
     */
    public String getForHeader() {
        return forHeader;
    }

    /**
     * Gets the protocolHeader
     *
     * @return The protocolHeader
     */
    public String getProtocolHeader() {
        return protocolHeader;
    }

    /**
     * Gets the httpsProtoValue
     *
     * @return The httpsProtoValue
     */
    public String getHttpsProtoValue() {
        return httpsProtoValue;
    }

    /**
     * Gets the httpProtoPort
     *
     * @return The httpProtoPort
     */
    public int getHttpProtoPort() {
        return httpProtoPort;
    }

    /**
     * Gets the httpsProtoPort
     *
     * @return The httpsProtoPort
     */
    public int getHttpsProtoPort() {
        return httpsProtoPort;
    }

    /**
     * Gets the isAbsoluteRedirect
     *
     * @return The isAbsoluteRedirect
     */
    public boolean isAbsoluteRedirect() {
        return isAbsoluteRedirect;
    }

    /**
     * Gets the shutdown-fast flag
     *
     * @return The shutdown-fast flag
     */
    public boolean isShutdownFast() {
        return shutdownFast;
    }

    /**
     * Gets the awaitShutDownSeconds
     *
     * @return The awaitShutDownSeconds
     */
    public int getAwaitShutDownSeconds() {
        return awaitShutDownSeconds;
    }

    /**
     * Gets if we should consider X-Forward-Headers that reach the backend.
     * Those can be spoofed by clients so we have to make sure to consider the headers only if the proxy/proxies reliably override those
     * headers for incoming requests.
     * Disabled by default as we now use relative redirects for Grizzly.
     * @return
     */
    public boolean isConsiderXForwards() {
        return isConsiderXForwards;
    }

    /**
     * Get the name of the echo header whose value is echoed for each request providing that header when using KippData's mod_id.
     * @return The name of the echo header whose value is echoed for each request providing that header.
     */
    public String getEchoHeader() {
        return this.echoHeader;
    }

    /**
     * Gets the value of the <code>X-Robots-Tag</code> response header to set. Default is none.
     *
     * @return The value of the <code>X-Robots-Tag</code> response header
     */
    public String getRobotsMetaTag() {
        return robotsMetaTag;
    }

    /** Get the maximum allowed size for PUT and POST bodies */
    public int getMaxBodySize() {
        return maxBodySize;
    }

    /**
     * Gets the maximum number of active sessions
     *
     * @return The maximum number of active sessions
     */
    public int getMaxNumberOfHttpSessions() {
        return maxNumberOfHttpSessions;
    }

    /**
     * Get the maximum header size for an HTTP request in bytes.
     *
     * @return the maximum header size for an HTTP request in bytes.
     */
    public int getMaxHttpHeaderSize() {
        return maxHttpHeaderSize;
    }

    /**
     * Gets the Web Socket timeout in milliseconds
     *
     * @return The timeout
     */
    public long getWsTimeoutMillis() {
        return wsTimeoutMillis;
    }

    public boolean isSslEnabled() {
        return isSslEnabled;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public List<String> getEnabledCiphers() {
        return enabledCiphers;
    }

    /**
     * Checks if the special "trackingId" parameter is supposed to be looked-up or always newly created
     *
     * @return <code>true</code> to look-up; otherwise <code>false</code> to always create a new one
     */
    public boolean isCheckTrackingIdInRequestParameters() {
        return checkTrackingIdInRequestParameters;
    }

    /**
     * Checks if hierarchical look-up of "parent" servlets should be supported.
     *
     * @return <code>true</code> to support hierarchical look-up of "parent" servlets; otherwise <code>false</code>
     */
    public boolean isSupportHierachicalLookupOnNotFound() {
        return supportHierachicalLookupOnNotFound;
    }

}
