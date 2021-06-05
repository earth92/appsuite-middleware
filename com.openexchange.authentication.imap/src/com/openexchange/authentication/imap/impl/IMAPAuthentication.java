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

package com.openexchange.authentication.imap.impl;

import static com.openexchange.authentication.LoginExceptionCodes.INVALID_CREDENTIALS_MISSING_CONTEXT_MAPPING;
import static com.openexchange.authentication.LoginExceptionCodes.INVALID_CREDENTIALS_MISSING_USER_MAPPING;
import static com.openexchange.authentication.LoginExceptionCodes.UNKNOWN;
import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.idn.IDNA;
import javax.security.auth.login.LoginException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.authentication.Authenticated;
import com.openexchange.authentication.AuthenticationService;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.authentication.LoginInfo;
import com.openexchange.config.ConfigurationService;
import com.openexchange.configuration.ConfigurationException;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.java.Strings;
import com.openexchange.mail.api.MailConfig.LoginSource;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.mime.MimeDefaultSession;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.MailAccounts;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.openexchange.server.ServiceLookup;
import com.openexchange.user.UserService;

public class IMAPAuthentication implements AuthenticationService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IMAPAuthentication.class);

    private static enum PropertyNames {
        IMAP_TIMEOUT("IMAP_TIMEOUT"),
        IMAP_CONNECTIONTIMEOUT("IMAP_CONNECTIONTIMEOUT"),
        USE_FULL_LOGIN_INFO("USE_FULL_LOGIN_INFO"),
        USE_FULL_LOGIN_INFO_FOR_USER_LOOKUP("USE_FULL_LOGIN_INFO_FOR_USER_LOOKUP"),
        USE_FULL_LOGIN_INFO_FOR_CONTEXT_LOOKUP("USE_FULL_LOGIN_INFO_FOR_CONTEXT_LOOKUP"),
        IMAP_SERVER("IMAP_SERVER"),
        IMAP_PORT("IMAP_PORT"),
        USE_MULTIPLE("USE_MULTIPLE"),
        IMAP_USE_SECURE("IMAP_USE_SECURE"),
        IMAPAUTHENC("com.openexchange.authentication.imap.imapAuthEnc"),
        LOWERCASE_FOR_CONTEXT_USER_LOOKUP("LOWERCASE_FOR_CONTEXT_USER_LOOKUP");

        /** The name of the property */
        public final String name;

        private PropertyNames(final String name) {
            this.name = name;
        }
    }

    /**
     * The string for <code>ISO-8859-1</code> character encoding.
     */
    @SuppressWarnings("unused")
    private static final String CHARENC_ISO8859 = "ISO-8859-1";

    // ----------------------------------------------------------------------------------------------------------------------

    private final ServiceLookup services;
    private final Cache<FailureKey, AuthenticationFailedException> failures;
    private final Properties props;

    /**
     * Default constructor.
     *
     * @throws IOException if an I/O error is occurred
     */
    public IMAPAuthentication(ServiceLookup services) throws IOException {
        super();
        this.services = services;

        // Check whether to cache failed authentication attempts to quit subsequent tries in a fast manner
        ConfigurationService configService = services.getService(ConfigurationService.class);
        int failureCacheExpirySeconds = configService.getIntProperty("com.openexchange.authentication.imap.failureCacheExpirySeconds", 0);
        if (failureCacheExpirySeconds > 0) {
            failures = CacheBuilder.newBuilder().maximumSize(1024).expireAfterWrite(failureCacheExpirySeconds, TimeUnit.SECONDS).build();
        } else {
            failures = null;
        }

        // Initialize configuration properties
        props = configService.getFile("imapauth.properties");
    }

    @Override
    public Authenticated handleLoginInfo(final LoginInfo loginInfo) throws OXException {
        try {
            SplitResult splitResult = split(loginInfo.getUsername());

            String localPart = splitResult.localPart;
            String password = loginInfo.getPassword();
            if ("".equals(localPart.trim()) || "".equals(password.trim())) {
                throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
            }

            String imaptimeout = "4000";
            {
                Object value = props.get(PropertyNames.IMAP_TIMEOUT.name);
                if (value != null) {
                    imaptimeout = (String) value;
                }
            }

            String connectiontimeout = "4000";
            {
                Object value = props.get(PropertyNames.IMAP_CONNECTIONTIMEOUT.name);
                if (value != null) {
                    connectiontimeout = (String) value;
                }
            }

            final Properties imapprops = MimeDefaultSession.getDefaultMailProperties();
            imapprops.put("mail.imap.connectiontimeout", connectiontimeout);
            imapprops.put("mail.imap.timeout", imaptimeout);

            boolean useFullLogin = true;
            {
                Object value = props.get(PropertyNames.USE_FULL_LOGIN_INFO.name);
                if (value != null) {
                    useFullLogin = Boolean.parseBoolean(value.toString().trim());
                }
            }

            boolean useFullLoginForUserLookup = false;
            {
                Object value = props.get(PropertyNames.USE_FULL_LOGIN_INFO_FOR_USER_LOOKUP.name);
                if (value != null) {
                    useFullLoginForUserLookup = Boolean.parseBoolean(value.toString().trim());
                }
            }

            boolean useFullLoginForContextLookup = false;
            {
                Object value = props.get(PropertyNames.USE_FULL_LOGIN_INFO_FOR_CONTEXT_LOOKUP.name);
                if (value != null) {
                    useFullLoginForContextLookup = Boolean.parseBoolean(value.toString().trim());
                }
            }

            boolean lowerCaseForContextUserLookup = false;
            {
                Object value = props.get(PropertyNames.LOWERCASE_FOR_CONTEXT_USER_LOOKUP.name);
                if (value != null) {
                    lowerCaseForContextUserLookup = Boolean.parseBoolean(value.toString().trim());
                }
            }

            String host = "localhost";
            {
                Object value = props.get(PropertyNames.IMAP_SERVER.name);
                if (value != null) {
                    host = IDNA.toASCII(value.toString());
                }
            }

            Integer port = Integer.valueOf(143);
            {
                Object value = props.get(PropertyNames.IMAP_PORT.name);
                if (value != null) {
                    port = Integer.valueOf(value.toString());
                }
            }

            // Set IMAP login
            String imapLogin = useFullLogin ? splitResult.fullLoginInfo : localPart;

            // Set user/context info
            String userInfo = useFullLoginForUserLookup ? splitResult.fullLoginInfo : localPart;
            String contextInfo = useFullLoginForContextLookup ? splitResult.fullLoginInfo : splitResult.domainPart;
            if (lowerCaseForContextUserLookup) {
                // Use JVM's default locale
                userInfo = userInfo.toLowerCase();
                contextInfo = contextInfo.toLowerCase();
            }

            // Support for multiple IMAP servers
            boolean isPrimary = false;
            boolean secure = false;
            if ("true".equalsIgnoreCase(props.getProperty(PropertyNames.USE_MULTIPLE.name))) {
                // Resolve user/context
                Context ctx = optContext(contextInfo).get();
                int userId = optUser(userInfo, loginInfo, ctx).get().intValue();

                // Load primary account and check its protocol to be IMAP
                MailAccount defaultMailAccount = optMailAccount(userId, ctx.getContextId()).get();
                String mailProtocol = defaultMailAccount.getMailProtocol();
                if (!mailProtocol.toLowerCase().startsWith("imap")) {
                    throw UNKNOWN.create(new StringBuilder(128).append("IMAP authentication failed: Primary account's protocol is not IMAP but ").append(mailProtocol).append(" for user ").append(userId).append(" in context ").append(ctx.getContextId()).toString());
                }

                /*
                 * Set user according to configured login source if different from LoginSource.USER_NAME
                 */
                final LoginSource loginSource = MailProperties.getInstance().getLoginSource(userId, ctx.getContextId());
                if (LoginSource.USER_IMAPLOGIN.equals(loginSource)) {
                    imapLogin = defaultMailAccount.getLogin();
                }
                if (LoginSource.PRIMARY_EMAIL.equals(loginSource)) {
                    imapLogin = defaultMailAccount.getPrimaryAddress();
                }

                /*
                 * Get IMAP server from primary account
                 */
                isPrimary = true;
                host = IDNA.toASCII(defaultMailAccount.getMailServer());
                port = Integer.valueOf(defaultMailAccount.getMailPort());
                secure = defaultMailAccount.isMailSecure();
                LOG.debug("Parsed IMAP Infos: {} {} {}  ({}@{})", (secure ? "imaps" : "imap"), host, port, Integer.valueOf(userId), Integer.valueOf(ctx.getContextId()));
            } else {
                // SSL feature for single defined IMAP server
                if ("true".equalsIgnoreCase(props.getProperty(PropertyNames.IMAP_USE_SECURE.name))) {
                    secure = true;
                }

                // Check if configured IMAP server is the primary IMAP server of the user
                Optional<Context> optionalContext = optContext(contextInfo).opt();
                if (optionalContext.isPresent()) {
                    Context ctx = optionalContext.get();
                    Optional<Integer> optionalUserId = optUser(userInfo, loginInfo, ctx).opt();
                    if (optionalUserId.isPresent()) {
                        Optional<MailAccount> optionalDefaultMailAccount = optMailAccount(optionalUserId.get().intValue(), ctx.getContextId()).opt();
                        if (optionalDefaultMailAccount.isPresent() && MailAccounts.isEqualImapAccount(optionalDefaultMailAccount.get(), host, port.intValue())) {
                            isPrimary = true;
                        }
                    }
                }
            }

            FailureKey failureKey = new FailureKey(host, port.intValue(), imapLogin, password);
            {
                AuthenticationFailedException authenticationFailed = null == failures ? null : failures.getIfPresent(failureKey);
                if (null != authenticationFailed) {
                    throw LoginExceptionCodes.INVALID_CREDENTIALS.create(authenticationFailed);
                }
            }

            LOG.debug("Using imap server: {}", host);
            LOG.debug("Using imap port: {}", port);
            LOG.debug("Using full login info: {}", Boolean.valueOf(useFullLogin));
            LOG.debug("Using full login info for user look-up: {}", Boolean.valueOf(useFullLoginForUserLookup));

            ConfigurationService configuration = services.getService(ConfigurationService.class);
            SSLSocketFactoryProvider factoryProvider = services.getService(SSLSocketFactoryProvider.class);
            final String socketFactoryClass = factoryProvider.getDefault().getClass().getName();
            final String sPort = port.toString();
            if (secure) {
                /*
                 * Enables the use of the STARTTLS command.
                 */
                // imapProps.put("mail.imap.starttls.enable", "true");
                /*
                 * Set main socket factory to a SSL socket factory
                 */
                imapprops.put("mail.imap.socketFactory.class", socketFactoryClass);
                imapprops.put("mail.imap.socketFactory.port", sPort);
                imapprops.put("mail.imap.socketFactory.fallback", "false");
                /*
                 * Needed for JavaMail >= 1.4
                 */
                // Security.setProperty("ssl.SocketFactory.provider", socketFactoryClass);
                /*
                 * Specify SSL protocols
                 */
                applySslProtocols(imapprops, configuration);
                /*
                 * Specify SSL cipher suites
                 */
                applySslCipherSuites(imapprops, configuration);
            } else {
                /*
                 * Enables the use of the STARTTLS command (if supported by the server) to switch the connection to a TLS-protected connection.
                 */
                applyEnableTls(imapprops, configuration);
                /*
                 * Specify the javax.net.ssl.SSLSocketFactory class, this class will be used to create IMAP SSL sockets if TLS handshake says
                 * so.
                 */
                imapprops.put("mail.imap.socketFactory.port", sPort);
                imapprops.put("mail.imap.ssl.socketFactory.class", socketFactoryClass);
                imapprops.put("mail.imap.ssl.socketFactory.port", sPort);
                imapprops.put("mail.imap.socketFactory.fallback", "false");
                /*
                 * Specify SSL protocols
                 */
                applySslProtocols(imapprops, configuration);
                /*
                 * Specify SSL cipher suites
                 */
                applySslCipherSuites(imapprops, configuration);
                // imapProps.put("mail.imap.ssl.enable", "true");
                /*
                 * Needed for JavaMail >= 1.4
                 */
                // Security.setProperty("ssl.SocketFactory.provider", socketFactoryClass);
            }

            if (isPrimary) {
                imapprops.put("mail.imap.primary", "true");
            }

            {
                Object value = props.get(PropertyNames.IMAPAUTHENC.name);
                if (value != null) {
                    String authenc = (String) value;
                    if (Strings.isNotEmpty(authenc)) {
                        imapprops.put("mail.imap.login.encoding", authenc.trim());
                    }
                }
            }

            Store imapconnection = null;
            try {
                Session session = Session.getInstance(imapprops, null);
                session.setDebug(false);

                imapconnection = session.getStore("imap");
                // try to connect with the credentials set above
                imapconnection.connect(host, port.intValue(), imapLogin, password);
                LOG.info("Imap authentication for user {} successful on host {}:{}", imapLogin, host, port);
            } catch (NoSuchProviderException e) {
                LOG.error("Error setup initial imap envorinment!", e);
                throw LoginExceptionCodes.COMMUNICATION.create(e);
            } catch (AuthenticationFailedException e) {
                Cache<FailureKey, AuthenticationFailedException> failures = this.failures;
                if (null != failures) {
                    failures.put(failureKey, e);
                }
                LOG.info("Authentication error on host {}:{} for user {}", host, port, imapLogin, e);
                LOG.debug("Debug imap authentication", e);
                throw LoginExceptionCodes.INVALID_CREDENTIALS.create(e);
            } catch (MessagingException e) {
                LOG.info("Messaging error on host {}:{} for user {}", host, port, imapLogin, e);
                LOG.debug("Debug imap error", e);
                throw LoginExceptionCodes.UNKNOWN.create(e, e.getMessage());
            } finally {
                if (imapconnection != null) {
                    try {
                        imapconnection.close();
                    } catch (Exception e) {
                        LOG.error("Error closing imap connection!", e);
                        throw LoginExceptionCodes.COMMUNICATION.create(e);
                    }
                }
            }

            /*
             * Set the context of the user, If full login was configured, we use the domain part as the context name/mapping entry. If NO
             * full login was configured, we assume that only 1 context is in the system which is named "defaultcontext".
             */
            if (useFullLogin) {
                LOG.debug("Using domain: {} as context name", splitResult.domainPart);
                return new AuthenticatedImpl(userInfo, contextInfo);
            }

            LOG.debug("Using \"defaultcontext\" as context name");
            return new AuthenticatedImpl(userInfo, "defaultcontext");
        } catch (ConfigurationException e) {
            LOG.error("Error reading auth plugin config!", e);
            throw LoginExceptionCodes.COMMUNICATION.create(e);
        }
    }

    private void applyEnableTls(Properties imapprops, ConfigurationService configuration) {
        String sEnableTls = configuration.getProperty("com.openexchange.imap.primary.enableTls", "").trim();
        if (Strings.isNotEmpty(sEnableTls)) {
            imapprops.put("mail.imap.starttls.enable", Boolean.parseBoolean(sEnableTls) ? "true" : "false");
        }

        boolean enableTls = configuration.getBoolProperty("com.openexchange.imap.enableTls", true);
        if (enableTls) {
            imapprops.put("mail.imap.starttls.enable", "true");
        }
    }

    private void applySslProtocols(Properties imapprops, ConfigurationService configuration) {
        String sslProtocols = configuration.getProperty("com.openexchange.imap.primary.ssl.protocols", "").trim();
        if (Strings.isNotEmpty(sslProtocols)) {
            imapprops.put("mail.imap.ssl.protocols", sslProtocols);
        }

        sslProtocols = configuration.getProperty("com.openexchange.imap.ssl.protocols", "SSLv3 TLSv1").trim();
        imapprops.put("mail.imap.ssl.protocols", sslProtocols);
    }

    private void applySslCipherSuites(Properties imapprops, ConfigurationService configuration) {
        String cipherSuites = configuration.getProperty("com.openexchange.imap.primary.ssl.ciphersuites", "").trim();
        if (Strings.isNotEmpty(cipherSuites)) {
            imapprops.put("mail.imap.ssl.ciphersuites", cipherSuites);
        }

        cipherSuites = configuration.getProperty("com.openexchange.imap.ssl.ciphersuites", "").trim();
        if (Strings.isNotEmpty(cipherSuites)) {
            imapprops.put("mail.imap.ssl.ciphersuites", cipherSuites);
        }
    }

    private Result<Context> optContext(String contextInfo) {
        try {
            ContextService contextService = services.getService(ContextService.class);
            int ctxId = contextService.getContextId(contextInfo);
            if (ContextStorage.NOT_FOUND == ctxId) {
                throw INVALID_CREDENTIALS_MISSING_CONTEXT_MAPPING.create(contextInfo);
            }
            return new Result<Context>(contextService.getContext(ctxId));
        } catch (OXException e) {
            return new Result<Context>(e);
        }
    }

    private Result<Integer> optUser(String userInfo, LoginInfo loginInfo, Context ctx) {
        UserService userService = services.getService(UserService.class);
        try {
            return new Result<Integer>(I(userService.getUserId(userInfo, ctx)));
        } catch (OXException e) {
            return new Result<Integer>(INVALID_CREDENTIALS_MISSING_USER_MAPPING.create(loginInfo.getUsername()));
        }
    }

    private Result<MailAccount> optMailAccount(int userId, int contextId) {
        try {
            MailAccountStorageService storageService = services.getServiceSafe(MailAccountStorageService.class);
            return new Result<MailAccount>(storageService.getDefaultMailAccount(userId, contextId));
        } catch (OXException e) {
            return new Result<MailAccount>(e);
        }
    }

    @Override
    public Authenticated handleAutoLoginInfo(final LoginInfo loginInfo) throws OXException {
        throw LoginExceptionCodes.NOT_SUPPORTED.create(IMAPAuthentication.class.getName());
    }

    /**
     * Splits user name and context.
     *
     * @param loginInfo The composite information separated by an <code>'@'</code> sign
     * @return The split login info
     * @throws LoginException if no separator is found.
     */
    private SplitResult split(String loginInfo) {
        int pos = loginInfo.lastIndexOf('@');
        if (pos <= 0) {
            return new SplitResult(loginInfo, loginInfo, "defaultcontext");
        }

        // Split by '@' character
        return new SplitResult(loginInfo, loginInfo.substring(0, pos), loginInfo.substring(pos + 1));
    }

    // ------------------------------------------------ Helper classes ------------------------------------------------

    private static final class SplitResult {

        /** The local part; e.g. <code>"jane@somewhere.com"</code> */
        final String fullLoginInfo;

        /** The local part; e.g. <code>"jane"</code> from <code>"jane@somewhere.com"</code> */
        final String localPart;

        /** The domain part; e.g. <code>"somewhere.com"</code> from <code>"jane@somewhere.com"</code> */
        final String domainPart;

        SplitResult(String fullLoginInfo, String localPart, String domainPart) {
            super();
            this.fullLoginInfo = fullLoginInfo;
            this.localPart = localPart;
            this.domainPart = domainPart;
        }

    }

    private static final class FailureKey {

        private final String host;
        private final int port;
        private final String user;
        private final String password;
        private final int hash;

        FailureKey(String host, int port, String user, String password) {
            super();
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;

            int prime = 31;
            int result = 1;
            result = prime * result + ((host == null) ? 0 : host.hashCode());
            result = prime * result + ((password == null) ? 0 : password.hashCode());
            result = prime * result + port;
            result = prime * result + ((user == null) ? 0 : user.hashCode());
            this.hash = result;
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
            if (!(obj instanceof FailureKey)) {
                return false;
            }
            FailureKey other = (FailureKey) obj;
            if (port != other.port) {
                return false;
            }
            if (host == null) {
                if (other.host != null) {
                    return false;
                }
            } else if (!host.equals(other.host)) {
                return false;
            }
            if (password == null) {
                if (other.password != null) {
                    return false;
                }
            } else if (!password.equals(other.password)) {
                return false;
            }
            if (user == null) {
                if (other.user != null) {
                    return false;
                }
            } else if (!user.equals(other.user)) {
                return false;
            }
            return true;
        }

    }

    private static final class AuthenticatedImpl implements Authenticated {

        private final String contextInfo;
        private final String userInfo;

        AuthenticatedImpl(String userInfo, String contextInfo) {
            super();
            this.userInfo = userInfo;
            this.contextInfo = contextInfo;
        }

        @Override
        public String getContextInfo() {
            return contextInfo;
        }

        @Override
        public String getUserInfo() {
            return userInfo;
        }
    }

    private static final class Result<T> {

        private final T result;
        private final OXException error;

        Result(T result) {
            super();
            this.result = result;
            this.error = null;
        }

        Result(OXException error) {
            super();
            this.result = null;
            this.error = error;
        }

        T get() throws OXException {
            if (error != null) {
                throw error;
            }
            return result;
        }

        Optional<T> opt() {
            return Optional.ofNullable(result);
        }
    }

}
