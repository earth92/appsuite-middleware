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

package com.openexchange.mail.config;

import static com.openexchange.java.Autoboxing.I;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.ConfigurationServices;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.exception.ExceptionUtils;
import com.openexchange.exception.OXException;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.mail.MailListField;
import com.openexchange.mail.api.IMailProperties;
import com.openexchange.mail.api.MailConfig.LoginSource;
import com.openexchange.mail.api.MailConfig.PasswordSource;
import com.openexchange.mail.api.MailConfig.ServerSource;
import com.openexchange.mail.utils.IpAddressRenderer;
import com.openexchange.net.HostList;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.UserAndContext;
import com.openexchange.tools.net.URIDefaults;

/**
 * {@link MailProperties} - Global mail properties read from properties file.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class MailProperties implements IMailProperties {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MailProperties.class);

    private static volatile MailProperties instance;

    /**
     * Gets the singleton instance of {@link MailProperties}.
     *
     * @return The singleton instance of {@link MailProperties}
     */
    public static MailProperties getInstance() {
        MailProperties tmp = instance;
        if (null == tmp) {
            synchronized (MailProperties.class) {
                tmp = instance;
                if (null == tmp) {
                    tmp = instance = new MailProperties();
                }
            }
        }
        return tmp;
    }

    /**
     * Releases the singleton instance of {@link MailProperties}.
     */
    public static void releaseInstance() {
        if (null != instance) {
            synchronized (MailProperties.class) {
                if (null != instance) {
                    instance = null;
                }
            }
        }
    }

    private static final class PrimaryMailProps {

        static class Params {

            LoginSource loginSource;
            PasswordSource passwordSource;
            ServerSource mailServerSource;
            ServerSource transportServerSource;
            ConfiguredServer mailServer;
            ConfiguredServer transportServer;
            String masterPassword;
            boolean mailStartTls;
            boolean transportStartTls;
            int maxToCcBcc;
            int maxDriveAttachments;
            boolean rateLimitPrimaryOnly;
            int rateLimit;
            String[] phishingHeaders;
            HostList ranges;
            int defaultArchiveDays;
            boolean preferSentDate;
            boolean hidePOP3StorageFolders;
            boolean translateDefaultFolders;
            boolean deleteDraftOnTransport;
            boolean forwardUnquoted;
            long maxMailSize;
            int maxForwardCount;
            int mailFetchLimit;

            Params() {
                super();
            }
        }

        // --------------------------------------------------------------------------------

        final LoginSource loginSource;
        final PasswordSource passwordSource;
        final ServerSource mailServerSource;
        final ServerSource transportServerSource;
        final ConfiguredServer mailServer;
        final ConfiguredServer transportServer;
        final String masterPassword;
        final boolean mailStartTls;
        final boolean transportStartTls;
        final int maxToCcBcc;
        final int maxDriveAttachments;
        final boolean rateLimitPrimaryOnly;
        final int rateLimit;
        final HostList ranges;
        final String[] phishingHeaders;
        final int defaultArchiveDays;
        final boolean preferSentDate;
        final boolean hidePOP3StorageFolders;
        final boolean translateDefaultFolders;
        final boolean deleteDraftOnTransport;
        final boolean forwardUnquoted;
        final long maxMailSize;
        final int maxForwardCount;
        final int mailFetchLimit;

        PrimaryMailProps(Params params) {
            super();
            this.loginSource = params.loginSource;
            this.passwordSource = params.passwordSource;
            this.mailServerSource = params.mailServerSource;
            this.transportServerSource = params.transportServerSource;
            this.mailServer = params.mailServer;
            this.transportServer = params.transportServer;
            this.masterPassword = params.masterPassword;
            this.mailStartTls = params.mailStartTls;
            this.transportStartTls = params.transportStartTls;
            this.maxToCcBcc = params.maxToCcBcc;
            this.maxDriveAttachments = params.maxDriveAttachments;
            this.rateLimitPrimaryOnly = params.rateLimitPrimaryOnly;
            this.rateLimit = params.rateLimit;
            this.ranges = params.ranges;
            this.phishingHeaders = params.phishingHeaders;
            this.defaultArchiveDays = params.defaultArchiveDays;
            this.preferSentDate = params.preferSentDate;
            this.hidePOP3StorageFolders = params.hidePOP3StorageFolders;
            this.translateDefaultFolders = params.translateDefaultFolders;
            this.deleteDraftOnTransport = params.deleteDraftOnTransport;
            this.forwardUnquoted = params.forwardUnquoted;
            this.maxMailSize = params.maxMailSize;
            this.maxForwardCount = params.maxForwardCount;
            this.mailFetchLimit = params.mailFetchLimit;
        }

    }

    private static final Cache<UserAndContext, PrimaryMailProps> CACHE_PRIMARY_PROPS = CacheBuilder.newBuilder().maximumSize(65536).expireAfterAccess(30, TimeUnit.MINUTES).build();

    /**
     * Clears the cache.
     */
    public static void invalidateCache() {
        CACHE_PRIMARY_PROPS.invalidateAll();
    }

    private static PrimaryMailProps getPrimaryMailProps(int userId, int contextId) throws OXException {
        UserAndContext key = UserAndContext.newInstance(userId, contextId);
        PrimaryMailProps primaryMailProps = CACHE_PRIMARY_PROPS.getIfPresent(key);
        if (null != primaryMailProps) {
            return primaryMailProps;
        }

        Callable<PrimaryMailProps> loader = new Callable<MailProperties.PrimaryMailProps>() {

            @Override
            public PrimaryMailProps call() throws Exception {
                return doGetPrimaryMailProps(userId, contextId);
            }
        };

        try {
            return CACHE_PRIMARY_PROPS.get(key, loader);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof OXException ? (OXException) cause : new OXException(cause);
        }
    }

    static PrimaryMailProps doGetPrimaryMailProps(int userId, int contextId) throws OXException {
        ConfigViewFactory viewFactory = ServerServiceRegistry.getInstance().getService(ConfigViewFactory.class);
        if (null == viewFactory) {
            throw ServiceExceptionCode.absentService(ConfigViewFactory.class);
        }

        ConfigView view = viewFactory.getView(userId, contextId);

        PrimaryMailProps.Params params = new PrimaryMailProps.Params();

        StringBuilder logMessageBuilder = new StringBuilder(1024);
        List<Object> args = new ArrayList<>(16);

        logMessageBuilder.append("Primary mail properties successfully loaded for user {} in context {}{}");
        args.add(Integer.valueOf(userId));
        args.add(Integer.valueOf(contextId));
        args.add(Strings.getLineSeparator());

        {
            final String loginStr = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.loginSource", view);
            if (loginStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.loginSource\" not set");
            }
            LoginSource loginSource = LoginSource.parse(loginStr.trim());
            if (null == loginSource) {
                throw MailConfigException.create(new StringBuilder(256).append("Unknown value in property \"com.openexchange.mail.loginSource\": ").append(loginStr).toString());
            }
            params.loginSource = loginSource;

            logMessageBuilder.append("  Login Source: {}{}");
            args.add(loginSource);
            args.add(Strings.getLineSeparator());
        }

        {
            final String pwStr = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.passwordSource", view);
            if (pwStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.passwordSource\" not set");
            }
            PasswordSource passwordSource = PasswordSource.parse(pwStr.trim());
            if (null == passwordSource) {
                throw MailConfigException.create(new StringBuilder(256).append("Unknown value in property \"com.openexchange.mail.passwordSource\": ").append(pwStr).toString());
            }
            params.passwordSource = passwordSource;

            logMessageBuilder.append("  Password Source: {}{}");
            args.add(passwordSource);
            args.add(Strings.getLineSeparator());
        }

        {
            final String mailSrcStr = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.mailServerSource", view);
            if (mailSrcStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.mailServerSource\" not set");
            }
            ServerSource mailServerSource = ServerSource.parse(mailSrcStr.trim());
            if (null == mailServerSource) {
                throw MailConfigException.create(new StringBuilder(256).append("Unknown value in property \"com.openexchange.mail.mailServerSource\": ").append(mailSrcStr).toString());
            }
            params.mailServerSource = mailServerSource;

            logMessageBuilder.append("  Mail Server Source: {}{}");
            args.add(mailServerSource);
            args.add(Strings.getLineSeparator());
        }

        {
            final String transSrcStr = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.transportServerSource", view);
            if (transSrcStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.transportServerSource\" not set");
            }
            ServerSource transportServerSource = ServerSource.parse(transSrcStr.trim());
            if (null == transportServerSource) {
                throw MailConfigException.create(new StringBuilder(256).append("Unknown value in property \"com.openexchange.mail.transportServerSource\": ").append(transSrcStr).toString());
            }
            params.transportServerSource = transportServerSource;

            logMessageBuilder.append("  Transport Server Source: {}{}");
            args.add(transportServerSource);
            args.add(Strings.getLineSeparator());
        }

        {
            ConfiguredServer mailServer = null;
            String tmp = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.mailServer", view);
            if (tmp != null) {
                mailServer = ConfiguredServer.parseFrom(tmp.trim(), URIDefaults.IMAP);

                logMessageBuilder.append("  Mail Server: {}{}");
                args.add(mailServer);
                args.add(Strings.getLineSeparator());
            }
            params.mailServer = mailServer;

        }

        {
            ConfiguredServer transportServer = null;
            String tmp = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.transportServer", view);
            if (tmp != null) {
                transportServer = ConfiguredServer.parseFrom(tmp.trim(), URIDefaults.SMTP);

                logMessageBuilder.append("  Transport Server: {}{}");
                args.add(transportServer);
                args.add(Strings.getLineSeparator());
            }
            params.transportServer = transportServer;
        }

        {
            String masterPassword = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.masterPassword", view);
            if (masterPassword != null) {
                masterPassword = masterPassword.trim();

                logMessageBuilder.append("  Master Password: {}{}");
                args.add("XXXXXXX");
                args.add(Strings.getLineSeparator());
            }
            params.masterPassword = masterPassword;
        }

        params.mailStartTls = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.mailStartTls", false, view);
        params.transportStartTls = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.transportStartTls", false, view);

        {
            try {
                params.maxToCcBcc = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.mail.maxToCcBcc", 0, view);
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                params.maxToCcBcc = 0;
            }

            logMessageBuilder.append("  maxToCcBcc: {}{}");
            args.add(Integer.valueOf(params.maxToCcBcc));
            args.add(Strings.getLineSeparator());
        }

        {
            try {
                params.maxDriveAttachments = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.mail.maxDriveAttachments", 20, view);
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                params.maxDriveAttachments = 20;
            }

            logMessageBuilder.append("  maxDriveAttachments: {}{}");
            args.add(Integer.valueOf(params.maxDriveAttachments));
            args.add(Strings.getLineSeparator());
        }

        {
            String phishingHdrsStr = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.phishingHeader", view);
            if (null != phishingHdrsStr && phishingHdrsStr.length() > 0) {
                params.phishingHeaders = phishingHdrsStr.split(" *, *");

                logMessageBuilder.append("  Phishing Headers: {}{}");
                args.add(Arrays.toString(params.phishingHeaders));
                args.add(Strings.getLineSeparator());
            } else {
                params.phishingHeaders = null;
            }
        }

        {
            params.rateLimitPrimaryOnly = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.rateLimitPrimaryOnly", true, view);

            logMessageBuilder.append("  Rate limit primary only: {}{}");
            args.add(Boolean.valueOf(params.rateLimitPrimaryOnly));
            args.add(Strings.getLineSeparator());
        }

        {
            try {
                params.rateLimit = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.mail.rateLimit", 0, view);
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                params.rateLimit = 0;
            }

            logMessageBuilder.append("  Sent Rate limit: {}{}");
            args.add(Integer.valueOf(params.rateLimit));
            args.add(Strings.getLineSeparator());
        }

        {
            HostList ranges = HostList.EMPTY;
            String tmp = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.rateLimitDisabledRange", view);
            if (Strings.isNotEmpty(tmp)) {
                ranges = HostList.valueOf(tmp);
            }
            params.ranges = ranges;

            logMessageBuilder.append("  White-listed from send rate limit: {}{}");
            args.add(ranges.toString());
            args.add(Strings.getLineSeparator());
        }

        {
            try {
                params.defaultArchiveDays = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.mail.archive.defaultDays", 90, view);
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                params.defaultArchiveDays = 90;
            }

            logMessageBuilder.append("  Default archive days: {}{}");
            args.add(Integer.valueOf(params.defaultArchiveDays));
            args.add(Strings.getLineSeparator());
        }

        {
            params.preferSentDate = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.preferSentDate", false, view);

            logMessageBuilder.append("  Prefer Sent Date: {}{}");
            args.add(Boolean.valueOf(params.preferSentDate));
            args.add(Strings.getLineSeparator());
        }

        {
            params.hidePOP3StorageFolders = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.hidePOP3StorageFolders", false, view);

            logMessageBuilder.append("  Hide POP3 Storage Folder: {}{}");
            args.add(Boolean.valueOf(params.hidePOP3StorageFolders));
            args.add(Strings.getLineSeparator());
        }

        {
            params.translateDefaultFolders = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.translateDefaultFolders", true, view);

            logMessageBuilder.append("  Translate Default Folders: {}{}");
            args.add(Boolean.valueOf(params.translateDefaultFolders));
            args.add(Strings.getLineSeparator());
        }

        {
            params.deleteDraftOnTransport = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.deleteDraftOnTransport", false, view);

            logMessageBuilder.append("  Delete Draft On Transport: {}{}");
            args.add(Boolean.valueOf(params.deleteDraftOnTransport));
            args.add(Strings.getLineSeparator());
        }

        {
            params.forwardUnquoted = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.mail.forwardUnquoted", false, view);

            logMessageBuilder.append("  Forward Unquoted: {}{}");
            args.add(Boolean.valueOf(params.forwardUnquoted));
            args.add(Strings.getLineSeparator());
        }

        {
            long maxMailSize;
            String tmp = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.mail.maxMailSize", view);
            if (null == tmp) {
                maxMailSize = -1L;
            } else {
                try {
                    maxMailSize = Long.parseLong(tmp);
                } catch (NumberFormatException e) {
                    LOG.debug("", e);
                    maxMailSize = -1L;
                }
            }
            params.maxMailSize = maxMailSize;

            logMessageBuilder.append("  Max. Mail Size: {}{}");
            args.add(Long.valueOf(params.maxMailSize));
            args.add(Strings.getLineSeparator());
        }

        {
            try {
                params.maxForwardCount = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.mail.maxForwardCount", 8, view);
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                params.maxForwardCount = 8;
            }

            logMessageBuilder.append("  Max. Forward Count: {}{}");
            args.add(Integer.valueOf(params.maxForwardCount));
            args.add(Strings.getLineSeparator());
        }

        {
            try {
                params.mailFetchLimit = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.mail.mailFetchLimit", 1000, view);
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                params.mailFetchLimit = 1000;
            }

            logMessageBuilder.append("  Mail Fetch Limit: {}{}");
            args.add(Integer.valueOf(params.mailFetchLimit));
            args.add(Strings.getLineSeparator());
        }

        PrimaryMailProps primaryMailProps = new PrimaryMailProps(params);
        LOG.debug(logMessageBuilder.toString(), args.toArray(new Object[args.size()]));
        return primaryMailProps;
    }


    // -----------------------------------------------------------------------------------------------------

    private final AtomicBoolean loaded;

    /*-
     * Fields for global properties
     */

    private LoginSource loginSource;

    private PasswordSource passwordSource;

    private ServerSource mailServerSource;

    private ServerSource transportServerSource;

    private ConfiguredServer mailServer;

    private ConfiguredServer transportServer;

    private String masterPassword;

    private int mailFetchLimit;

    private int bodyDisplaySize;

    private boolean userFlagsEnabled;

    private boolean allowNestedDefaultFolderOnAltNamespace;

    private boolean hideInlineImages;

    private String defaultMimeCharset;

    private boolean ignoreSubscription;

    private boolean hidePOP3StorageFolders;

    private boolean preferSentDate;

    private char defaultSeparator;

    private String[] quoteLineColors;

    private Properties javaMailProperties;

    private boolean watcherEnabled;

    private int watcherTime;

    private int watcherFrequency;

    private boolean watcherShallClose;

    private boolean supportSubscription;

    private String[] phishingHeaders;

    private String defaultMailProvider;

    private boolean adminMailLoginEnabled;

    private int mailAccessCacheShrinkerSeconds;

    private int mailAccessCacheIdleSeconds;

    private boolean addClientIPAddress;

    private boolean appendVersionToMailerHeader;

    private IpAddressRenderer ipAddressRenderer;

    private boolean rateLimitPrimaryOnly;

    private int rateLimit;

    private int maxToCcBcc;

    private int maxDriveAttachments;

    private String authProxyDelimiter;

    /** Indicates whether MSISDN addresses should be supported or not. */
    private boolean supportMsisdnAddresses;

    private int defaultArchiveDays;

    private HostList ranges;

    private boolean mailStartTls;

    private boolean transportStartTls;

    /**
     * Initializes a new {@link MailProperties}
     */
    private MailProperties() {
        super();
        loaded = new AtomicBoolean();
        defaultSeparator = '/';
        ranges = HostList.EMPTY;
        hideInlineImages = true;
        appendVersionToMailerHeader = true;
    }

    /**
     * Exclusively loads the global mail properties
     *
     * @throws OXException If loading of global mail properties fails
     */
    public void loadProperties() throws OXException {
        if (!loaded.get()) {
            synchronized (loaded) {
                if (!loaded.get()) {
                    loadProperties0();
                    loaded.set(true);
                    loaded.notifyAll();
                }
            }
        }
    }

    /**
     * Exclusively resets the global mail properties
     */
    public void resetProperties() {
        if (loaded.get()) {
            synchronized (loaded) {
                if (loaded.get()) {
                    resetFields();
                    loaded.set(false);
                }
            }
        }
    }

    /**
     * Waits for loading this properties.
     *
     * @throws InterruptedException If another thread interrupted the current thread before or while the current thread was waiting for
     *             loading the properties.
     */
    @Override
    public void waitForLoading() throws InterruptedException {
        if (!loaded.get()) {
            synchronized (loaded) {
                while (!loaded.get()) {
                    loaded.wait();
                }
            }
        }
    }

    private void resetFields() {
        loginSource = null;
        passwordSource = null;
        mailServerSource = null;
        transportServerSource = null;
        mailServer = null;
        transportServer = null;
        masterPassword = null;
        mailFetchLimit = 0;
        bodyDisplaySize = 10485760; // 10 MB
        userFlagsEnabled = false;
        allowNestedDefaultFolderOnAltNamespace = false;
        hideInlineImages = true;
        defaultMimeCharset = null;
        ignoreSubscription = false;
        hidePOP3StorageFolders = false;
        preferSentDate = false;
        defaultSeparator = '/';
        quoteLineColors = null;
        javaMailProperties = null;
        watcherEnabled = false;
        watcherTime = 0;
        watcherFrequency = 0;
        watcherShallClose = false;
        supportSubscription = false;
        defaultMailProvider = null;
        adminMailLoginEnabled = false;
        mailAccessCacheShrinkerSeconds = 0;
        mailAccessCacheIdleSeconds = 0;
        addClientIPAddress = false;
        appendVersionToMailerHeader = true;
        ipAddressRenderer = IpAddressRenderer.simpleRenderer();
        rateLimitPrimaryOnly = true;
        rateLimit = 0;
        maxToCcBcc = 0;
        maxDriveAttachments = 20;
        authProxyDelimiter = null;
        supportMsisdnAddresses = false;
        defaultArchiveDays = 90;
        ranges = HostList.EMPTY;
        mailStartTls = false;
        transportStartTls = false;
    }

    private void loadProperties0() throws OXException {
        final StringBuilder logBuilder = new StringBuilder(1024);
        List<Object> args = new ArrayList<>(16);

        logBuilder.append("{}Loading global mail properties...{}");
        args.add(Strings.getLineSeparator());
        args.add(Strings.getLineSeparator());

        final ConfigurationService configuration = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);

        {
            final String loginStr = configuration.getProperty("com.openexchange.mail.loginSource");
            if (loginStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.loginSource\" not set");
            }
            final LoginSource loginSource = LoginSource.parse(loginStr.trim());
            if (null == loginSource) {
                throw MailConfigException.create(new StringBuilder(256).append(
                    "Unknown value in property \"com.openexchange.mail.loginSource\": ").append(loginStr).toString());
            }
            this.loginSource = loginSource;
            logBuilder.append("\tLogin Source: {}{}");
            args.add(this.loginSource.toString());
            args.add(Strings.getLineSeparator());
        }

        {
            final String pwStr = configuration.getProperty("com.openexchange.mail.passwordSource");
            if (pwStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.passwordSource\" not set");
            }
            final PasswordSource pwSource = PasswordSource.parse(pwStr.trim());
            if (null == pwSource) {
                throw MailConfigException.create(new StringBuilder(256).append(
                    "Unknown value in property \"com.openexchange.mail.passwordSource\": ").append(pwStr).toString());
            }
            passwordSource = pwSource;
            logBuilder.append("\tPassword Source: {}{}");
            args.add(this.passwordSource.toString());
            args.add(Strings.getLineSeparator());
        }

        {
            final String mailSrcStr = configuration.getProperty("com.openexchange.mail.mailServerSource");
            if (mailSrcStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.mailServerSource\" not set");
            }
            final ServerSource mailServerSource = ServerSource.parse(mailSrcStr.trim());
            if (null == mailServerSource) {
                throw MailConfigException.create(new StringBuilder(256).append(
                    "Unknown value in property \"com.openexchange.mail.mailServerSource\": ").append(mailSrcStr).toString());
            }
            this.mailServerSource = mailServerSource;
            logBuilder.append("\tMail Server Source: {}{}");
            args.add(this.mailServerSource.toString());
            args.add(Strings.getLineSeparator());
        }

        {
            final String transSrcStr = configuration.getProperty("com.openexchange.mail.transportServerSource");
            if (transSrcStr == null) {
                throw MailConfigException.create("Property \"com.openexchange.mail.transportServerSource\" not set");
            }
            final ServerSource transportServerSource = ServerSource.parse(transSrcStr.trim());
            if (null == transportServerSource) {
                throw MailConfigException.create(new StringBuilder(256).append(
                    "Unknown value in property \"com.openexchange.mail.transportServerSource\": ").append(transSrcStr).toString());
            }
            this.transportServerSource = transportServerSource;
            logBuilder.append("\tTransport Server Source: {}{}");
            args.add(this.transportServerSource.toString());
            args.add(Strings.getLineSeparator());
        }

        {
            String mailServer = configuration.getProperty("com.openexchange.mail.mailServer");
            if (mailServer != null) {
                mailServer = mailServer.trim();
                this.mailServer = ConfiguredServer.parseFrom(mailServer, URIDefaults.IMAP);
            }
        }

        {
            String transportServer = configuration.getProperty("com.openexchange.mail.transportServer");
            if (transportServer != null) {
                transportServer = transportServer.trim();
                this.transportServer = ConfiguredServer.parseFrom(transportServer, URIDefaults.SMTP);
            }
        }

        {
            masterPassword = configuration.getProperty("com.openexchange.mail.masterPassword");
            if (masterPassword != null) {
                masterPassword = masterPassword.trim();
            }
        }

        {
            final String mailFetchLimitStr = configuration.getProperty("com.openexchange.mail.mailFetchLimit", "1000").trim();
            try {
                mailFetchLimit = Integer.parseInt(mailFetchLimitStr);
                logBuilder.append("\tMail Fetch Limit: {}{}");
                args.add(Integer.valueOf(mailFetchLimit));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                mailFetchLimit = 1000;
                logBuilder.append("\tMail Fetch Limit: Non parseable value \"{}\". Setting to fallback: {}{}");
                args.add(mailFetchLimitStr);
                args.add(Integer.valueOf(mailFetchLimit));
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String bodyDisplaySizeStr = configuration.getProperty("com.openexchange.mail.bodyDisplaySizeLimit", "10485760").trim();
            try {
                bodyDisplaySize = Integer.parseInt(bodyDisplaySizeStr);
                logBuilder.append("\tBody Display Size Limit: {}{}");
                args.add(Integer.valueOf(bodyDisplaySize));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                bodyDisplaySize = 10485760;
                logBuilder.append("\tBody Display Size Limit: Non parseable value \"{}\". Setting to fallback: {}{}");
                args.add(bodyDisplaySizeStr);
                args.add(Integer.valueOf(bodyDisplaySize));
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.mail.mailAccessCacheShrinkerSeconds", "3").trim();
            try {
                mailAccessCacheShrinkerSeconds = Integer.parseInt(tmp);
                logBuilder.append("\tMail Access Cache shrinker-interval seconds: {}{}");
                args.add(Integer.valueOf(mailAccessCacheShrinkerSeconds));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                mailAccessCacheShrinkerSeconds = 3;
                logBuilder.append("\tMail Access Cache shrinker-interval seconds: Non parseable value \"{}\". Setting to fallback: {}{}");
                args.add(tmp);
                args.add(Integer.valueOf(mailAccessCacheShrinkerSeconds));
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.mail.mailAccessCacheIdleSeconds", "4").trim();
            try {
                mailAccessCacheIdleSeconds = Integer.parseInt(tmp);
                logBuilder.append("\tMail Access Cache idle seconds: {}{}");
                args.add(Integer.valueOf(mailAccessCacheIdleSeconds));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                mailAccessCacheIdleSeconds = 4;
                logBuilder.append("\tMail Access Cache idle seconds: Non parseable value \"{}\". Setting to fallback: {}{}");
                args.add(tmp);
                args.add(Integer.valueOf(mailAccessCacheIdleSeconds));
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String userFlagsStr = configuration.getProperty("com.openexchange.mail.userFlagsEnabled", "false").trim();
            userFlagsEnabled = Boolean.parseBoolean(userFlagsStr);
            logBuilder.append("\tUser Flags Enabled: {}{}");
            args.add(Boolean.valueOf(userFlagsEnabled));
            args.add(Strings.getLineSeparator());
        }

        {
            final String allowNestedStr = configuration.getProperty("com.openexchange.mail.allowNestedDefaultFolderOnAltNamespace", "false").trim();
            allowNestedDefaultFolderOnAltNamespace = Boolean.parseBoolean(allowNestedStr);
            logBuilder.append("\tAllow Nested Default Folders on AltNamespace: {}{}");
            args.add(Boolean.valueOf(allowNestedDefaultFolderOnAltNamespace));
            args.add(Strings.getLineSeparator());
        }

        {
            final String hideInlineImagesStr = configuration.getProperty("com.openexchange.mail.hideInlineImages", "true").trim();
            hideInlineImages = Boolean.parseBoolean(hideInlineImagesStr);
            logBuilder.append("\tHide Inline Images: {}{}");
            args.add(Boolean.valueOf(hideInlineImages));
            args.add(Strings.getLineSeparator());
        }

        {
            final String defaultMimeCharsetStr = configuration.getProperty("mail.mime.charset", "UTF-8").trim();
            /*
             * Check validity
             */
            try {
                Charset.forName(defaultMimeCharsetStr);
                defaultMimeCharset = defaultMimeCharsetStr;
                logBuilder.append("\tDefault MIME Charset: {}{}");
                args.add(defaultMimeCharset);
                args.add(Strings.getLineSeparator());
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                LOG.debug("", t);
                defaultMimeCharset = "UTF-8";
                logBuilder.append("\tDefault MIME Charset: Unsupported charset \"{}\". Setting to fallback: {}{}");
                args.add(defaultMimeCharsetStr);
                args.add(defaultMimeCharset);
                args.add(Strings.getLineSeparator());
            }
            /*
             * Add to system properties, too
             */
            System.getProperties().setProperty("mail.mime.charset", defaultMimeCharset);
        }

        {
            final String defaultMailProviderStr = configuration.getProperty("com.openexchange.mail.defaultMailProvider", "imap").trim();
            defaultMailProvider = defaultMailProviderStr;
            logBuilder.append("\tDefault Mail Provider: {}{}");
            args.add(defaultMailProvider);
            args.add(Strings.getLineSeparator());
        }

        {
            final String adminMailLoginEnabledStr = configuration.getProperty("com.openexchange.mail.adminMailLoginEnabled", "false").trim();
            adminMailLoginEnabled = Boolean.parseBoolean(adminMailLoginEnabledStr);
            logBuilder.append("\tAdmin Mail Login Enabled: {}{}");
            args.add(Boolean.valueOf(adminMailLoginEnabled));
            args.add(Strings.getLineSeparator());
        }

        {
            final String ignoreSubsStr = configuration.getProperty("com.openexchange.mail.ignoreSubscription", "false").trim();
            ignoreSubscription = Boolean.parseBoolean(ignoreSubsStr);
            logBuilder.append("\tIgnore Folder Subscription: {}{}");
            args.add(Boolean.valueOf(ignoreSubscription));
            args.add(Strings.getLineSeparator());
        }

        {
            final String preferSentDateStr = configuration.getProperty("com.openexchange.mail.preferSentDate", "false").trim();
            preferSentDate = Boolean.parseBoolean(preferSentDateStr);
            logBuilder.append("\tPrefer Sent Date: {}{}");
            args.add(Boolean.valueOf(preferSentDate));
            args.add(Strings.getLineSeparator());
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.mail.hidePOP3StorageFolders", "false").trim();
            hidePOP3StorageFolders = Boolean.parseBoolean(tmp);
            logBuilder.append("\tHide POP3 Storage Folders: {}{}");
            args.add(Boolean.valueOf(hidePOP3StorageFolders));
            args.add(Strings.getLineSeparator());
        }

        {
            final String supSubsStr = configuration.getProperty("com.openexchange.mail.supportSubscription", "true").trim();
            supportSubscription = Boolean.parseBoolean(supSubsStr);
            logBuilder.append("\tSupport Subscription: {}{}");
            args.add(Boolean.valueOf(supportSubscription));
            args.add(Strings.getLineSeparator());
        }

        {
            String tmp = configuration.getProperty("com.openexchange.mail.appendVersionToMailerHeader", "true").trim();
            appendVersionToMailerHeader = Boolean.parseBoolean(tmp);
            logBuilder.append("\tAppend Version To Mailer Header: {}{}");
            args.add(Boolean.valueOf(appendVersionToMailerHeader));
            args.add(Strings.getLineSeparator());
        }

        {
            String tmp = configuration.getProperty("com.openexchange.mail.addClientIPAddress", "false").trim();
            addClientIPAddress = Boolean.parseBoolean(tmp);
            logBuilder.append("\tAdd Client IP Address: {}{}");
            args.add(Boolean.valueOf(addClientIPAddress));
            args.add(Strings.getLineSeparator());

            if (addClientIPAddress) {
                tmp = configuration.getProperty("com.openexchange.mail.clientIPAddressPattern");
                if (null == tmp) {
                    ipAddressRenderer = IpAddressRenderer.simpleRenderer();
                } else {
                    tmp = tmp.trim();
                    try {
                        ipAddressRenderer = IpAddressRenderer.createRendererFor(tmp);
                        logBuilder.append("\tIP Address Pattern: Pattern syntax \"{}\" accepted.{}");
                        args.add(tmp);
                        args.add(Strings.getLineSeparator());
                    } catch (Exception e) {
                        LOG.debug("", e);
                        logBuilder.append("\tIP Address Pattern: Unsupported pattern syntax \"{}\". Using simple renderer.{}");
                        args.add(tmp);
                        args.add(Strings.getLineSeparator());
                    }
                }
            }
        }

        {
            final char defaultSep = configuration.getProperty("com.openexchange.mail.defaultSeparator", "/").trim().charAt(0);
            if (defaultSep <= 32) {
                defaultSeparator = '/';
                logBuilder.append("\tDefault Separator: Invalid separator (decimal ascii value={}). Setting to fallback: {}{}");
                args.add(Integer.valueOf(defaultSep));
                args.add(String.valueOf(defaultSeparator));
                args.add(Strings.getLineSeparator());
            } else {
                defaultSeparator = defaultSep;
                logBuilder.append("\tDefault Separator: {}{}");
                args.add(String.valueOf(defaultSeparator));
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String quoteColors = configuration.getProperty("com.openexchange.mail.quoteLineColors", "#666666").trim();
            if (Pattern.matches("((#[0-9a-fA-F&&[^,]]{6})(?:\r?\n|\\z|\\s*,\\s*))+", quoteColors)) {
                quoteLineColors = quoteColors.split("\\s*,\\s*");
                logBuilder.append("\tHTML Quote Colors: {}{}");
                args.add(quoteColors);
                args.add(Strings.getLineSeparator());
            } else {
                quoteLineColors = new String[] { "#666666" };
                logBuilder.append("\tHTML Quote Colors: Invalid sequence of colors \"{}\". Setting to fallback: #666666{}");
                args.add(quoteColors);
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String watcherEnabledStr = configuration.getProperty("com.openexchange.mail.watcherEnabled", "false").trim();
            watcherEnabled = Boolean.parseBoolean(watcherEnabledStr);
            logBuilder.append("\tWatcher Enabled: {}{}");
            args.add(Boolean.valueOf(watcherEnabled));
            args.add(Strings.getLineSeparator());
        }

        {
            final String watcherTimeStr = configuration.getProperty("com.openexchange.mail.watcherTime", "60000").trim();
            try {
                watcherTime = Integer.parseInt(watcherTimeStr);
                logBuilder.append("\tWatcher Time: {}{}");
                args.add(Integer.valueOf(watcherTime));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                watcherTime = 60000;
                logBuilder.append("\tWatcher Time: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(watcherTimeStr);
                args.add(Integer.valueOf(watcherTime));
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String watcherFeqStr = configuration.getProperty("com.openexchange.mail.watcherFrequency", "10000").trim();
            try {
                watcherFrequency = Integer.parseInt(watcherFeqStr);
                logBuilder.append("\tWatcher Frequency: {}{}");
                args.add(Integer.valueOf(watcherFrequency));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                watcherFrequency = 10000;
                logBuilder.append("\tWatcher Frequency: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(watcherFeqStr);
                args.add(Integer.valueOf(watcherFrequency));
                args.add(Strings.getLineSeparator());
            }
        }

        {
            final String watcherShallCloseStr = configuration.getProperty("com.openexchange.mail.watcherShallClose", "false").trim();
            watcherShallClose = Boolean.parseBoolean(watcherShallCloseStr);
            logBuilder.append("\tWatcher Shall Close: {}{}");
            args.add(Boolean.valueOf(watcherShallClose));
            args.add(Strings.getLineSeparator());
        }

        {
            final String phishingHdrsStr = configuration.getProperty("com.openexchange.mail.phishingHeader", "").trim();
            if (null != phishingHdrsStr && phishingHdrsStr.length() > 0) {
                phishingHeaders = phishingHdrsStr.split(" *, *");
            } else {
                phishingHeaders = null;
            }
        }

        {
            final String rateLimitPrimaryOnlyStr = configuration.getProperty("com.openexchange.mail.rateLimitPrimaryOnly", "true").trim();
            rateLimitPrimaryOnly = Boolean.parseBoolean(rateLimitPrimaryOnlyStr);
            logBuilder.append("\tRate limit primary only: {}{}");
            args.add(Boolean.valueOf(rateLimitPrimaryOnly));
            args.add(Strings.getLineSeparator());
        }

        {
            final String rateLimitStr = configuration.getProperty("com.openexchange.mail.rateLimit", "0").trim();
            try {
                rateLimit = Integer.parseInt(rateLimitStr);
                logBuilder.append("\tSent Rate limit: {}{}");
                args.add(Integer.valueOf(rateLimit));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                rateLimit = 0;
                logBuilder.append("\tSend Rate limit: Invalid value \"{}\". Setting to fallback {}{}");
                args.add(rateLimitStr);
                args.add(Integer.valueOf(rateLimit));
                args.add(Strings.getLineSeparator());

            }
        }

        {
            HostList ranges = HostList.EMPTY;
            String tmp = configuration.getProperty("com.openexchange.mail.rateLimitDisabledRange", "").trim();
            if (Strings.isNotEmpty(tmp)) {
                ranges = HostList.valueOf(tmp);
            }
            this.ranges = ranges;
            logBuilder.append("\tWhite-listed from send rate limit: {}{}");
            args.add(ranges);
            args.add(Strings.getLineSeparator());
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.mail.archive.defaultDays", "90").trim();
            try {
                defaultArchiveDays = Strings.parseInt(tmp);
                logBuilder.append("\tDefault archive days: {}{}");
                args.add(Integer.valueOf(defaultArchiveDays));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                defaultArchiveDays = 90;
                logBuilder.append("\tDefault archive days: Invalid value \"{}\". Setting to fallback {}{}");
                args.add(tmp);
                args.add(Integer.valueOf(defaultArchiveDays));
                args.add(Strings.getLineSeparator());

            }
        }

        {
            final String maxToCcBccStr = configuration.getProperty("com.openexchange.mail.maxToCcBcc", "0").trim();
            try {
                maxToCcBcc = Integer.parseInt(maxToCcBccStr);
                logBuilder.append("\tmaxToCcBcc: {}{}");
                args.add(Integer.valueOf(maxToCcBcc));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                maxToCcBcc = 0;
                logBuilder.append("\tmaxToCcBcc: Invalid value \"{}\". Setting to fallback {}{}");
                args.add(maxToCcBccStr);
                args.add(Integer.valueOf(maxToCcBcc));
                args.add(Strings.getLineSeparator());

            }
        }

        {
            final String maxDriveAttachmentsStr = configuration.getProperty("com.openexchange.mail.maxDriveAttachments", "20").trim();
            try {
                maxDriveAttachments = Integer.parseInt(maxDriveAttachmentsStr);
                logBuilder.append("\tmaxDriveAttachments: {}{}");
                args.add(Integer.valueOf(maxDriveAttachments));
                args.add(Strings.getLineSeparator());
            } catch (NumberFormatException e) {
                LOG.debug("", e);
                maxDriveAttachments = 20;
                logBuilder.append("\tmaxDriveAttachments: Invalid value \"{}\". Setting to fallback {}{}");
                args.add(maxDriveAttachmentsStr);
                args.add(Integer.valueOf(maxDriveAttachments));
                args.add(Strings.getLineSeparator());

            }
        }

        {
            String javaMailPropertiesStr = configuration.getProperty("com.openexchange.mail.JavaMailProperties");
            if (null != javaMailPropertiesStr) {
                javaMailPropertiesStr = javaMailPropertiesStr.trim();
                try {
                    Properties javaMailProps = configuration.getFile(javaMailPropertiesStr);
                    javaMailProperties = javaMailProps;
                    if (javaMailProperties.isEmpty()) {
                        javaMailProperties = null;
                    }
                } catch (Exception e) {
                    LOG.debug("", e);
                    javaMailProperties = null;
                }
            }
            logBuilder.append("\tJavaMail Properties loaded: {}{}");
            args.add(Boolean.valueOf(javaMailProperties != null));
            args.add(Strings.getLineSeparator());
        }

        {
            authProxyDelimiter = configuration.getProperty("com.openexchange.mail.authProxyDelimiter");
            if (authProxyDelimiter != null) {
                authProxyDelimiter = authProxyDelimiter.trim();
                if (authProxyDelimiter.length() == 0) {
                    authProxyDelimiter = null;
                }
            }
        }

        {
            final String supportMsisdnAddressesStr = configuration.getProperty("com.openexchange.mail.supportMsisdnAddresses", "false").trim();
            supportMsisdnAddresses = Boolean.parseBoolean(supportMsisdnAddressesStr);
            logBuilder.append("\tSupports MSISDN addresses: {}{}");
            args.add(Boolean.valueOf(supportMsisdnAddresses));
            args.add(Strings.getLineSeparator());
        }

        {
            this.mailStartTls = configuration.getBoolProperty("com.openexchange.mail.mailStartTls", false);
            this.transportStartTls = configuration.getBoolProperty("com.openexchange.mail.transportStartTls", false);
        }

        logBuilder.append("Global mail properties successfully loaded!");
        LOG.info(logBuilder.toString(), args.toArray(new Object[args.size()]));
    }

    /**
     * Reads the properties from specified property file and returns an appropriate instance of {@link Properties}
     *
     * @param propFile The property file
     * @return The appropriate instance of {@link Properties}
     * @throws OXException If reading property file fails
     */
    protected static Properties readPropertiesFromFile(String propFile) throws OXException {
        final Properties properties = new Properties();
        final FileInputStream fis;
        try {
            fis = new FileInputStream(new File(propFile));
        } catch (FileNotFoundException e) {
            throw MailConfigException.create(
                new StringBuilder(256).append("Properties not found at location: ").append(propFile).toString(),
                e);
        }
        try {
            properties.load(fis);
            return properties;
        } catch (IOException e) {
            throw MailConfigException.create(
                new StringBuilder(256).append("I/O error while reading properties from file \"").append(propFile).append(
                    "\": ").append(e.getMessage()).toString(),
                e);
        } finally {
            Streams.close(fis);
        }
    }

    /**
     * Reads the properties from specified property file and returns an appropriate instance of {@link Properties}
     *
     * @param in The property stream
     * @return The appropriate instance of {@link Properties}
     * @throws OXException If reading property file fails
     */
    protected static Properties readPropertiesFromFile(InputStream in) throws OXException {
        try {
            return ConfigurationServices.loadPropertiesFrom(in, true);
        } catch (IOException e) {
            throw MailConfigException.create(new StringBuilder(256).append("I/O error: ").append(e.getMessage()).toString(), e);
        } finally {
            Streams.close(in);
        }
    }

    @Override
    public boolean hideInlineImages() {
        return hideInlineImages;
    }

    @Override
    public boolean isAllowNestedDefaultFolderOnAltNamespace() {
        return allowNestedDefaultFolderOnAltNamespace;
    }

    /**
     * Gets the max. allowed size (in bytes) for body for being displayed.
     *
     * @return The max. allowed size (in bytes) for body for being displayed
     */
    public int getBodyDisplaySize() {
        return bodyDisplaySize;
    }

    /**
     * Gets the default MIME charset.
     *
     * @return The default MIME charset
     */
    public String getDefaultMimeCharset() {
        return defaultMimeCharset;
    }

    /**
     * Gets the default mail provider.
     *
     * @return The default mail provider
     */
    public String getDefaultMailProvider() {
        return defaultMailProvider;
    }

    /**
     * Indicates if admin mail login is enabled; meaning whether admin user's try to login to mail system is permitted or not.
     *
     * @return <code>true</code> if admin mail login is enabled; otherwise <code>false</code>
     */
    public boolean isAdminMailLoginEnabled() {
        return adminMailLoginEnabled;
    }

    /**
     * Gets the default separator character for specified user.
     *
     * @return The default separator character
     */
    public char getDefaultSeparator() {
        return defaultSeparator;
    }

    @Override
    public boolean isIgnoreSubscription() {
        return ignoreSubscription;
    }

    /**
     * Signals whether a mail's sent date (<code>"Date"</code> header) is preferred over its received date when serving the special {@link MailListField#DATE} field.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to prefer sent date; otherwise <code>false</code> for received date
     */
    public boolean isPreferSentDate(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.preferSentDate;
        } catch (Exception e) {
            LOG.error("Failed to get whether a mail's sent date (<code>\"Date\"</code> header) is preferred over its received date for user {} in context {}. Using default {} instead.", I(userId), I(contextId), Boolean.valueOf(preferSentDate), e);
            return preferSentDate;
        }
    }

    /**
     * Signals whether standard folder names are supposed to be translated.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to translate; otherwise <code>false</code>
     */
    public boolean isTranslateDefaultFolders(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.translateDefaultFolders;
        } catch (Exception e) {
            LOG.error("Failed to get whether standard folder names are supposed to be translated for user {} in context {}. Using default {} instead.", I(userId), I(contextId), Boolean.valueOf(true), e);
            return true;
        }
    }

    /**
     * Signals whether Draft messages are supposed to be deleted when sent out.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to delete; otherwise <code>false</code>
     */
    public boolean isDeleteDraftOnTransport(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.deleteDraftOnTransport;
        } catch (Exception e) {
            LOG.error("Failed to get whether Draft messages are supposed to be deleted for user {} in context {}. Using default {} instead.", I(userId), I(contextId), Boolean.valueOf(false), e);
            return false;
        }
    }

    /**
     * Signals whether to forward messages unquoted.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to forward messages unquoted; otherwise <code>false</code>
     */
    public boolean isForwardUnquoted(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.forwardUnquoted;
        } catch (Exception e) {
            LOG.error("Failed to get whether to forward messages unquoted for user {} in context {}. Using default {} instead.", I(userId), I(contextId), Boolean.valueOf(false), e);
            return false;
        }
    }

    /**
     * Gets max. mail size allowed being transported
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The max. mail size allowed being transported
     */
    public long getMaxMailSize(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.maxMailSize;
        } catch (Exception e) {
            LOG.error("Failed to get max. mail size for user {} in context {}. Using default {} instead.", I(userId), I(contextId), "-1", e);
            return -1L;
        }
    }

    /**
     * Gets max. number of message attachments that are allowed to be forwarded as attachment.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The max. number of message attachments that are allowed to be forwarded as attachment
     */
    public int getMaxForwardCount(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.maxForwardCount;
        } catch (Exception e) {
            LOG.error("Failed to get max. forward count for user {} in context {}. Using default {} instead.", I(userId), I(contextId), "8", e);
            return 8;
        }
    }

    public boolean isHidePOP3StorageFolders(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.hidePOP3StorageFolders;
        } catch (Exception e) {
            LOG.error("Failed to get hide-POP3-storage-folders flag for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return hidePOP3StorageFolders;
        }
    }

    @Override
    public boolean isSupportSubscription() {
        return supportSubscription;
    }

    /**
     * Checks if client's IP address should be added to mail headers on delivery as custom header <code>"X-Originating-IP"</code>.
     *
     * @return <code>true</code> if client's IP address should be added otherwise <code>false</code>
     */
    public boolean isAddClientIPAddress() {
        return addClientIPAddress;
    }

    /**
     * Checks whether the version string is supposed to be appended to <code>"X-Mailer"</code> header.
     *
     * @return <code>true</code> to append version string to <code>"X-Mailer"</code> header; otherwise <code>false</code>
     */
    public boolean isAppendVersionToMailerHeader() {
        return appendVersionToMailerHeader;
    }

    /**
     * Gets the IP address renderer
     * <p>
     * <i>Note</i>: Returns <code>null</code> if {@link #isAddClientIPAddress()} signals <code>false</code>
     *
     * @return The renderer instance
     */
    public IpAddressRenderer getIpAddressRenderer() {
        return ipAddressRenderer;
    }

    /**
     * Gets the JavaMail properties.
     *
     * @return The JavaMail properties
     */
    public Properties getJavaMailProperties() {
        return javaMailProperties;
    }

    /**
     * Gets the login source for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The login source
     */
    public LoginSource getLoginSource(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.loginSource;
        } catch (Exception e) {
            LOG.error("Failed to get login source for user {} in context {}. Using default {} instead.", I(userId), I(contextId), loginSource, e);
            return loginSource;
        }
    }

    /**
     * Gets the password source for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The password source
     */
    public PasswordSource getPasswordSource(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.passwordSource;
        } catch (Exception e) {
            LOG.error("Failed to get password source for user {} in context {}. Using default {} instead.", I(userId), I(contextId), passwordSource, e);
            return passwordSource;
        }
    }

    /**
     * Gets the mail server source for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param isGuest If this is a guest account
     * @return The mail server source
     */
    public ServerSource getMailServerSource(int userId, int contextId, boolean isGuest) {
        try {
            if (isGuest) {
                return ServerSource.USER;   // For moment, all guests are going to return as user to avoid conflict with Guard
            }
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.mailServerSource;
        } catch (Exception e) {
            LOG.error("Failed to get mail server source for user {} in context {}. Using default {} instead.", I(userId), I(contextId), mailServerSource, e);
            return mailServerSource;
        }
    }

    /**
     * Gets the transport server source for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param isGuest If this is a guest account
     * @return The transport server source
     */
    public ServerSource getTransportServerSource(int userId, int contextId, boolean isGuest) {
        try {
            if (isGuest) {
                return ServerSource.USER;   // For moment, all guests are going to return as user to avoid conflict with Guard
            }
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.transportServerSource;
        } catch (Exception e) {
            LOG.error("Failed to get transport server source for user {} in context {}. Using default {} instead.", I(userId), I(contextId), transportServerSource, e);
            return transportServerSource;
        }
    }

    /**
     * Gets the global mail server for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The global mail server
     */
    public ConfiguredServer getMailServer(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.mailServer;
        } catch (Exception e) {
            LOG.error("Failed to get mail server for user {} in context {}. Using default {} instead.", I(userId), I(contextId), mailServer, e);
            return mailServer;
        }
    }

    /**
     * Gets the global transport server for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The global transport server
     */
    public ConfiguredServer getTransportServer(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.transportServer;
        } catch (Exception e) {
            LOG.error("Failed to get transport server for user {} in context {}. Using default {} instead.", I(userId), I(contextId), transportServer, e);
            return transportServer;
        }
    }

    /**
     * Checks whether STARTTLS is required for mail access for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if STARTTLS is required; otherwise <code>false</code>
     */
    public boolean isMailStartTls(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.mailStartTls;
        } catch (Exception e) {
            LOG.error("Failed to get STARTTLS flag for mail access for user {} in context {}. Using default {} instead.", I(userId), I(contextId), Boolean.valueOf(mailStartTls), e);
            return mailStartTls;
        }
    }

    /**
     * Checks whether STARTTLS is required for mail transport for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if STARTTLS is required; otherwise <code>false</code>
     */
    public boolean isTransportStartTls(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.transportStartTls;
        } catch (Exception e) {
            LOG.error("Failed to get STARTTLS flag for mail transport for user {} in context {}. Using default {} instead.", I(userId), I(contextId), Boolean.valueOf(transportStartTls), e);
            return transportStartTls;
        }
    }

    /**
     * Gets the master password for specified user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The master password
     */
    public String getMasterPassword(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.masterPassword;
        } catch (Exception e) {
            LOG.error("Failed to get transport server source for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return masterPassword;
        }
    }

    /**
     * Gets the max. number of recipient addresses that can be specified for given user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The max. number of recipient addresses
     */
    public int getMaxToCcBcc(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.maxToCcBcc;
        } catch (Exception e) {
            LOG.error("Failed to get max. number of recipient addresses for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return maxToCcBcc;
        }
    }

    /**
     * Gets the max. number of Drive attachments that can be attached to a mail
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The max. number of Drive attachments
     */
    public int getMaxDriveAttachments(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.maxDriveAttachments;
        } catch (Exception e) {
            LOG.error("Failed to get max. number of Drive attachments for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return maxDriveAttachments;
        }
    }

    /**
     * Gets the quote line colors.
     *
     * @return The quote line colors
     */
    public String[] getQuoteLineColors() {
        return quoteLineColors;
    }

    /**
     * Gets the phishing headers.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The phishing headers or <code>null</code> if none defined
     */
    public String[] getPhishingHeaders(int userId, int contextId) {
        String[] phishingHeaders;
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            phishingHeaders = primaryMailProps.phishingHeaders;
        } catch (Exception e) {
            LOG.error("Failed to get phishing headers for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            phishingHeaders = this.phishingHeaders;
        }

        if (null == phishingHeaders) {
            return null;
        }
        final String[] retval = new String[phishingHeaders.length];
        System.arraycopy(phishingHeaders, 0, retval, 0, phishingHeaders.length);
        return retval;
    }

    /**
     * Gets the send mail rate limit (how many mails can be sent in
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The send rate limit
     */
    public int getRateLimit(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.rateLimit;
        } catch (Exception e) {
            LOG.error("Failed to get send rate limit for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return rateLimit;
        }
    }

    /**
     * Gets the setting if the rate limit should only affect the primary account or all accounts
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The flag
     */
    public boolean getRateLimitPrimaryOnly(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.rateLimitPrimaryOnly;
        } catch (Exception e) {
            LOG.error("Failed to get rateLimitPrimaryOnly flag for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return rateLimitPrimaryOnly;
        }
    }

    /**
     * Gets the IP ranges for which a rate limit must not be applied
     *
     * @return The IP ranges
     */
    public HostList getDisabledRateLimitRanges(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.ranges;
        } catch (Exception e) {
            LOG.error("Failed to get IP ranges for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return ranges;
        }
    }

    /**
     * Gets the proxy authentication delimiter.
     * <p>
     * <b>Note</b>: Applies only to primary mail account
     *
     * @return The proxy authentication delimiter or <code>null</code> if not set
     */
    public final String getAuthProxyDelimiter() {
        return authProxyDelimiter;
    }

    /**
     * Sets the authProxyDelimiter
     *
     * @param authProxyDelimiter The authProxyDelimiter to set
     */
    public void setAuthProxyDelimiter(String authProxyDelimiter) {
        this.authProxyDelimiter = authProxyDelimiter;
    }

    /**
     * Signals if MSISDN addresses are supported or not.
     *
     * @return <code>true</code>, if MSISDN addresses are supported; otherwise <code>false</code>
     */
    public boolean isSupportMsisdnAddresses() {
        return supportMsisdnAddresses;
    }

    /**
     * Gets the default days when archiving messages.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The default days
     */
    public int getDefaultArchiveDays(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.defaultArchiveDays;
        } catch (Exception e) {
            LOG.error("Failed to get default days when archiving messages for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return defaultArchiveDays;
        }
    }

    /**
     * Gets the mail fetch limit.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The mail fetch limit
     */
    public int getMailFetchLimit(int userId, int contextId) {
        try {
            PrimaryMailProps primaryMailProps = getPrimaryMailProps(userId, contextId);
            return primaryMailProps.mailFetchLimit;
        } catch (Exception e) {
            LOG.error("Failed to get mail fetch limit for user {} in context {}. Using default instead.", I(userId), I(contextId), e);
            return mailFetchLimit;
        }
    }

    @Override
    public int getMailFetchLimit() {
        return mailFetchLimit;
    }

    @Override
    public boolean isUserFlagsEnabled() {
        return userFlagsEnabled;
    }

    /**
     * Indicates if watcher is enabled.
     *
     * @return <code>true</code> if watcher is enabled; otherwise <code>false</code>
     */
    public boolean isWatcherEnabled() {
        return watcherEnabled;
    }

    /**
     * Gets the watcher frequency.
     *
     * @return The watcher frequency
     */
    public int getWatcherFrequency() {
        return watcherFrequency;
    }

    /**
     * Indicates if watcher is allowed to close exceeded connections.
     *
     * @return <code>true</code> if watcher is allowed to close exceeded connections; otherwise <code>false</code>
     */
    public boolean isWatcherShallClose() {
        return watcherShallClose;
    }

    /**
     * Gets the watcher time.
     *
     * @return The watcher time
     */
    public int getWatcherTime() {
        return watcherTime;
    }

    /**
     * Gets the mail access cache shrinker-interval seconds.
     *
     * @return The mail access cache shrinker-interval seconds
     */
    public int getMailAccessCacheShrinkerSeconds() {
        return mailAccessCacheShrinkerSeconds;
    }

    /**
     * Gets the mail access cache idle seconds.
     *
     * @return The mail access cache idle seconds.
     */
    public int getMailAccessCacheIdleSeconds() {
        return mailAccessCacheIdleSeconds;
    }

}
