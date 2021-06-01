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

package com.openexchange.imap.ping;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import com.openexchange.config.ConfigurationService;
import com.openexchange.imap.config.IIMAPProperties;
import com.openexchange.imap.services.Services;
import com.openexchange.imap.util.HostAndPort;
import com.openexchange.java.BoundaryExceededException;
import com.openexchange.java.BoundedStringBuilder;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.MimeDefaultSession;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.sun.mail.util.SocketFetcher;

/**
 * {@link IMAPCapabilityAndGreetingCache} - A cache for CAPABILITY and greeting from IMAP servers.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class IMAPCapabilityAndGreetingCache {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IMAPCapabilityAndGreetingCache.class);

    private static volatile Integer capabiltiesCacheIdleTime;
    private static int capabiltiesCacheIdleTime() {
        Integer tmp = capabiltiesCacheIdleTime;
        if (null == tmp) {
            synchronized (IMAPCapabilityAndGreetingCache.class) {
                tmp = capabiltiesCacheIdleTime;
                if (null == tmp) {
                    int defaultValue = 0; // Do not check again
                    ConfigurationService service = Services.getService(ConfigurationService.class);
                    if (null == service) {
                        return defaultValue;
                    }
                    tmp = Integer.valueOf(service.getIntProperty("com.openexchange.imap.capabiltiesCacheIdleTime", defaultValue));
                    capabiltiesCacheIdleTime = tmp;
                }
            }
        }
        return tmp.intValue();
    }

    private static volatile ConcurrentMap<Key, Future<CapabilityAndGreeting>> MAP;

    /**
     * Initializes a new {@link IMAPCapabilityAndGreetingCache}.
     */
    private IMAPCapabilityAndGreetingCache() {
        super();
    }

    /**
     * Initializes this cache.
     */
    public static void init() {
        if (MAP == null) {
            synchronized (IMAPCapabilityAndGreetingCache.class) {
                if (MAP == null) {
                    MAP = new NonBlockingHashMap<Key, Future<CapabilityAndGreeting>>();
                    // TODO: Probably pre-load CAPABILITY and greeting from common IMAP servers like GMail, etc.
                }
            }
        }
    }

    /**
     * Tear-down for this cache.
     */
    public static void tearDown() {
        if (MAP != null) {
            synchronized (IMAPCapabilityAndGreetingCache.class) {
                if (MAP != null) {
                    clear();
                    MAP = null;
                }
            }
        }
    }

    /**
     * Clears this cache.
     */
    public static void clear() {
        MAP.clear();
    }

    /**
     * Gets the cached greeting from IMAP server denoted by specified parameters.
     *
     * @param endpoint The IMAP server's end-point
     * @param isSecure Whether to establish a secure connection
     * @param imapProperties The IMAP properties
     * @param primary Whether considered IMAP end-point is the primary one or not
     * @return The greeting from IMAP server denoted by specified parameters
     * @throws IOException If an I/O error occurs
     */
    public static String getGreeting(HostAndPort endpoint, boolean isSecure, IIMAPProperties imapProperties, boolean primary) throws IOException {
        return getCapabilityAndGreeting(endpoint, isSecure, imapProperties, primary).getGreeting();
    }

    /**
     * Gets the cached capabilities from IMAP server denoted by specified parameters.
     *
     * @param endpoint The IMAP server's end-point
     * @param isSecure Whether to establish a secure connection
     * @param imapProperties The IMAP properties
     * @param primary Whether considered IMAP end-point is the primary one or not
     * @return The capabilities from IMAP server denoted by specified parameters
     * @throws IOException If an I/O error occurs
     */
    public static Map<String, String> getCapabilities(HostAndPort endpoint, boolean isSecure, IIMAPProperties imapProperties, boolean primary) throws IOException {
        return getCapabilityAndGreeting(endpoint, isSecure, imapProperties, primary).getCapability();
    }

    /**
     * Gets the cached capabilities & greeting from IMAP server denoted by specified parameters.
     *
     * @param endpoint The IMAP server's end-point
     * @param isSecure Whether to establish a secure connection
     * @param imapProperties The IMAP properties
     * @param primary Whether considered IMAP end-point is the primary one or not
     * @return The capabilities & greeting
     * @throws IOException If an I/O error occurs
     */
    public static CapabilityAndGreeting getCapabilityAndGreeting(HostAndPort endpoint, boolean isSecure, IIMAPProperties imapProperties, boolean primary) throws IOException {
        int idleTime = capabiltiesCacheIdleTime();
        if (idleTime < 0) {
            // Never cache
            FutureTask<CapabilityAndGreeting> ft = new FutureTask<CapabilityAndGreeting>(new CapabilityAndGreetingCallable(endpoint, isSecure, imapProperties, primary));
            ft.run();
            return getFrom(ft);
        }

        ConcurrentMap<Key, Future<CapabilityAndGreeting>> map = MAP;
        if (null == map) {
            init();
            map = MAP;
        }

        Key key = new Key(endpoint.getHost(), endpoint.getPort(), isSecure);
        Future<CapabilityAndGreeting> f = map.get(key);
        if (null == f) {
            FutureTask<CapabilityAndGreeting> ft = new FutureTask<CapabilityAndGreeting>(new CapabilityAndGreetingCallable(endpoint, isSecure, imapProperties, primary));
            f = map.putIfAbsent(key, ft);
            if (null == f) {
                f = ft;
                ft.run();
            }
        }

        CapabilityAndGreeting cag = getFrom(f);
        if (isElapsed(cag, idleTime)) {
            FutureTask<CapabilityAndGreeting> ft = new FutureTask<CapabilityAndGreeting>(new CapabilityAndGreetingCallable(endpoint, isSecure, imapProperties, primary));
            if (map.replace(key, f, ft)) {
                f = ft;
                ft.run();
            } else {
                f = map.get(key);
            }
            cag = getFrom(f);
        }

        return cag;
    }

    private static boolean isElapsed(CapabilityAndGreeting cag, int idleTime) {
        if (idleTime == 0) {
            return false; // never
        }
        // Check if elapsed
        return ((System.currentTimeMillis() - cag.getStamp()) > idleTime);
    }

    private static CapabilityAndGreeting getFrom(Future<CapabilityAndGreeting> f) throws IOException {
        try {
            return f.get();
        } catch (InterruptedException e) {
            // Keep interrupted status
            Thread.currentThread().interrupt();
            throw new IOException(e.getMessage());
        } catch (CancellationException e) {
            throw new IOException(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw ((IOException) cause);
            }
            if (cause instanceof RuntimeException) {
                throw new IOException(e.getMessage());
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Not unchecked", cause);
        }
    }

    private static final class CapabilityAndGreetingCallable implements Callable<CapabilityAndGreeting> {

        private static final Pattern SPLIT = Pattern.compile("\r?\n");

        private final HostAndPort endpoint;
        private final boolean isSecure;
        private final IIMAPProperties imapProperties;
        private final boolean primary;

        CapabilityAndGreetingCallable(HostAndPort endpoint, boolean isSecure, IIMAPProperties imapProperties, boolean primary) {
            super();
            this.endpoint = endpoint;
            this.isSecure = isSecure;
            this.imapProperties = imapProperties;
            this.primary = primary;
        }

        @Override
        public CapabilityAndGreeting call() throws IOException {
            BoundedStringBuilder sb = new BoundedStringBuilder(512, 2048);
            String greeting = null;
            String capabilities = null;

            Socket s = null;
            try {
                // Establish socket connection
                s = SocketFetcher.getSocket(endpoint.getHost(), endpoint.getPort(), createImapProps(), "mail.imap", false);

                // State variables
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                boolean skipLF = false;
                boolean eol = false;

                // Read IMAP server greeting on connect
                if (in.available() > 0) {
                    for (int i; !eol && ((i = in.read()) != -1);) {
                        char c = (char) i;
                        switch (c) {
                            case '\r':
                                eol = true;
                                skipLF = true;
                                break;
                            case '\n':
                                eol = true;
                                skipLF = false;
                                break;
                            default:
                                sb.append(c);
                                break;
                        }
                    }
                }
                greeting = sb.toString();
                sb.setLength(0);

                if (skipLF) {
                    // Consume final LF
                    in.read();
                    skipLF = false;
                }

                // Request capabilities through CAPABILITY command
                out.write("A1 CAPABILITY\r\n".getBytes());
                out.flush();

                // Read CAPABILITY response
                {
                    boolean hasNextLine = true;
                    while (hasNextLine) {
                        hasNextLine = false;
                        eol = false;

                        if (skipLF) {
                            // Consume final LF
                            in.read();
                            skipLF = false;
                        }

                        int i = in.read();
                        if (i != -1) {
                            // Character '*' (42) indicates an un-tagged response; meaning subsequent response lines will follow
                            hasNextLine = (i == 42);

                            do {
                                char c = (char) i;
                                switch (c) {
                                    case '\r':
                                        eol = true;
                                        skipLF = true;
                                        break;
                                    case '\n':
                                        eol = true;
                                        skipLF = false;
                                        break;
                                    default:
                                        sb.append(c);
                                        break;
                                }
                            } while (!eol && ((i = in.read()) != -1));

                            if (sb.length() >= 5 && sb.indexOf(" BYE ", 0) >= 0) {
                                // Received "BYE" response
                                sb.insert(0, "Received BYE response from IMAP server: ");
                                throw new IOException(sb.toString());
                            }

                            // Append LF if a next line is expected
                            if (hasNextLine) {
                                sb.append('\n');
                            }

                        }
                    }

                    String[] lines = SPLIT.split(sb.toString());
                    sb.setLength(0);
                    for (String line : lines) {
                        if (line.startsWith("* CAPABILITY ")) {
                            sb.append(line.substring(12));
                        } else if (!line.startsWith("A1 ")) {
                            sb.append(' ').append(line);
                        }
                    }
                    capabilities = sb.toString();
                }

                if (skipLF) {
                    // Consume final LF
                    in.read();
                    skipLF = false;
                }

                // Close connection through LOGOUT command
                out.write("A2 LOGOUT\r\n".getBytes());
                out.flush();

                // Create & return new CapabilityAndGreeting instance
                LOG.debug("Successfully fetched capabilities and greeting from IMAP server \"{}\":{}{}{}{}", endpoint.getHost(), Strings.getLineSeparator(), greeting, Strings.getLineSeparator(), capabilities);
                return new CapabilityAndGreeting(capabilities, greeting);
            } catch (BoundaryExceededException e) {
                if (null == greeting) {
                    // Exceeded while reading greeting
                    throw e;
                }

                if (null == capabilities) {
                    // Exceeded while reading greeting
                    capabilities = sb.toString().trim();
                }
                return new CapabilityAndGreeting(capabilities, greeting);
            } finally {
                if (null != s) {
                    try {
                        s.close();
                    } catch (@SuppressWarnings("unused") Exception e) {
                        // ignore
                    }
                }
            }
        }

        private Properties createImapProps() {
            Properties imapProps = MimeDefaultSession.getDefaultMailProperties();
            {
                int connectionTimeout = imapProperties.getImapConnectionTimeout();
                if (connectionTimeout > 0) {
                    imapProps.put("mail.imap.connectiontimeout", Integer.toString(connectionTimeout));
                }
            }
            {
                int timeout = imapProperties.getImapTimeout();
                if (timeout > 0) {
                    imapProps.put("mail.imap.timeout", Integer.toString(timeout));
                }
            }
            SSLSocketFactoryProvider factoryProvider = Services.getService(SSLSocketFactoryProvider.class);
            final String socketFactoryClass = factoryProvider.getDefault().getClass().getName();
            final String sPort = Integer.toString(endpoint.getPort());
            if (isSecure) {
                imapProps.put("mail.imap.socketFactory.class", socketFactoryClass);
                imapProps.put("mail.imap.socketFactory.port", sPort);
                imapProps.put("mail.imap.socketFactory.fallback", "false");
                applySslProtocols(imapProps);
                applySslCipherSuites(imapProps);
            } else {
                applyEnableTls(imapProps);
                imapProps.put("mail.imap.socketFactory.port", sPort);
                imapProps.put("mail.imap.ssl.socketFactory.class", socketFactoryClass);
                imapProps.put("mail.imap.ssl.socketFactory.port", sPort);
                imapProps.put("mail.imap.socketFactory.fallback", "false");
                applySslProtocols(imapProps);
                applySslCipherSuites(imapProps);
            }
            if (primary) {
                imapProps.put("mail.imap.primary", "true");
            }
            {
                String authenc = imapProperties.getImapAuthEnc();
                if (Strings.isNotEmpty(authenc)) {
                    imapProps.put("mail.imap.login.encoding", authenc);
                }
            }
            return imapProps;
        }

        private void applyEnableTls(Properties imapprops) {
            boolean enableTls = imapProperties.isEnableTls();
            if (enableTls) {
                imapprops.put("mail.imap.starttls.enable", "true");
            }
        }

        private void applySslProtocols(Properties imapprops) {
            String sslProtocols = imapProperties.getSSLProtocols();
            if (Strings.isNotEmpty(sslProtocols)) {
                imapprops.put("mail.imap.ssl.protocols", sslProtocols);
            }
        }

        private void applySslCipherSuites(Properties imapprops) {
            String cipherSuites = imapProperties.getSSLCipherSuites();
            if (Strings.isNotEmpty(cipherSuites)) {
                imapprops.put("mail.imap.ssl.ciphersuites", cipherSuites);
            }
        }
    }

    static InetSocketAddress toSocketAddress(HostAndPort endpoint) {
        if (null == endpoint) {
            return null;
        }
        int port = endpoint.getPort();
        if (port <= 0) {
            port = 143;
        }
        return new InetSocketAddress(endpoint.getHost(), port);
    }

    /**
     * The capabilities & greeting information for an IMAP server (URL).
     */
    public static final class CapabilityAndGreeting {

        private static final Pattern SPLIT = Pattern.compile(" +");

        private final Map<String, String> capabilities;
        private final String greeting;
        private final long stamp;

        CapabilityAndGreeting(String capability, String greeting) {
            super();
            if (null == capability) {
                capabilities = Collections.emptyMap();
            } else {
                String[] caps = SPLIT.split(capability);
                Map<String, String> capabilities = new LinkedHashMap<String, String>(caps.length);
                for (String cap : caps) {
                    if (Strings.isNotEmpty(cap)) {
                        capabilities.put(Strings.toUpperCase(cap), cap);
                    }
                }
                this.capabilities = Collections.unmodifiableMap(capabilities);
            }
            this.greeting = greeting;
            this.stamp = System.currentTimeMillis();
        }

        long getStamp() {
            return stamp;
        }

        /**
         * Gets the capabilities
         *
         * @return The capabilities
         */
        public Map<String, String> getCapability() {
            return capabilities;
        }

        /**
         * Gets the greeting
         *
         * @return The greeting
         */
        public String getGreeting() {
            return greeting;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((capabilities == null) ? 0 : capabilities.hashCode());
            result = prime * result + ((greeting == null) ? 0 : greeting.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CapabilityAndGreeting)) {
                return false;
            }
            CapabilityAndGreeting other = (CapabilityAndGreeting) obj;
            if (capabilities == null) {
                if (other.capabilities != null) {
                    return false;
                }
            } else if (!capabilities.equals(other.capabilities)) {
                return false;
            }
            if (greeting == null) {
                if (other.greeting != null) {
                    return false;
                }
            } else if (!greeting.equals(other.greeting)) {
                return false;
            }
            return true;
        }
    }

    private static final class Key {

        final String host;
        final int port;
        final boolean secure;
        private final int hash;

        Key(String host, int port, boolean secure) {
            super();
            this.host = host;
            this.port = port;
            this.secure = secure;

            int prime = 31;
            int result = 1;
            result = prime * result + port;
            result = prime * result + (secure ? 1231 : 1237);
            hash = prime * result + ((host == null) ? 0 : host.hashCode());
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Key other = (Key) obj;
            if (port != other.port) {
                return false;
            }
            if (secure != other.secure) {
                return false;
            }
            if (host == null) {
                if (other.host != null) {
                    return false;
                }
            } else if (!host.equals(other.host)) {
                return false;
            }
            return true;
        }


    }

}
