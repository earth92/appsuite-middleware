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

package com.openexchange.imap.config;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.exception.OXException;
import com.openexchange.imap.HostExtractingGreetingListener;
import com.openexchange.imap.IMAPProtocol;
import com.openexchange.imap.commandexecutor.AbstractFailsafeCircuitBreakerCommandExecutor;
import com.openexchange.imap.commandexecutor.FailsafeCircuitBreakerCommandExecutor;
import com.openexchange.imap.commandexecutor.GenericFailsafeCircuitBreakerCommandExecutor;
import com.openexchange.imap.commandexecutor.MonitoringCommandExecutor;
import com.openexchange.imap.commandexecutor.PrimaryFailsafeCircuitBreakerCommandExecutor;
import com.openexchange.imap.entity2acl.Entity2ACL;
import com.openexchange.imap.services.Services;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.CharsetDetector;
import com.openexchange.java.Strings;
import com.openexchange.mail.api.AbstractProtocolProperties;
import com.openexchange.mail.api.IMailProperties;
import com.openexchange.mail.api.MailConfig.BoolCapVal;
import com.openexchange.mail.config.MailConfigException;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.net.HostList;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.session.UserAndContext;
import com.openexchange.spamhandler.SpamHandler;
import com.sun.mail.imap.CommandExecutor;
import com.sun.mail.imap.IMAPStore;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import net.jodah.failsafe.util.Ratio;

/**
 * {@link IMAPProperties}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class IMAPProperties extends AbstractProtocolProperties implements IIMAPProperties {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IMAPProperties.class);

    private static final IMAPProperties instance = new IMAPProperties();

    /**
     * Gets the singleton instance of {@link IMAPProperties}
     *
     * @return The singleton instance of {@link IMAPProperties}
     */
    public static IMAPProperties getInstance() {
        return instance;
    }

    private static final class PrimaryIMAPProperties {

        private static class Params {

            HostExtractingGreetingListener hostExtractingGreetingListener;
            Boolean rootSubfoldersAllowed;
            boolean namespacePerUser;
            int umlautFilterThreshold;
            int maxMailboxNameLength;
            TIntSet invalidChars;
            boolean allowESORT;
            boolean allowSORTDISPLAY;
            boolean fallbackOnFailedSORT;
            boolean useMultipleAddresses;
            boolean useMultipleAddressesUserHash;
            int useMultipleAddressesMaxRetryAttempts;
            boolean ignoreDeletedMails;
            boolean includeSharedInboxExplicitly;

            Params() {
                super();
                useMultipleAddressesMaxRetryAttempts = -1;
            }
        }

        // -------------------------------------------------------------------------------------------------------

        final HostExtractingGreetingListener hostExtractingGreetingListener;
        final Boolean rootSubfoldersAllowed;
        final boolean namespacePerUser;
        final int umlautFilterThreshold;
        final int maxMailboxNameLength;
        final TIntSet invalidChars;
        final boolean allowESORT;
        final boolean allowSORTDISPLAY;
        final boolean fallbackOnFailedSORT;
        final boolean useMultipleAddresses;
        final boolean useMultipleAddressesUserHash;
        final int useMultipleAddressesMaxRetryAttempts;
        final boolean ignoreDeletedMails;
        final boolean includeSharedInboxExplicitly;

        PrimaryIMAPProperties(Params params) {
            super();
            this.hostExtractingGreetingListener = params.hostExtractingGreetingListener;
            this.rootSubfoldersAllowed = params.rootSubfoldersAllowed;
            this.namespacePerUser = params.namespacePerUser;
            this.umlautFilterThreshold = params.umlautFilterThreshold;
            this.maxMailboxNameLength = params.maxMailboxNameLength;
            this.invalidChars = params.invalidChars;
            this.allowESORT = params.allowESORT;
            this.allowSORTDISPLAY = params.allowSORTDISPLAY;
            this.fallbackOnFailedSORT = params.fallbackOnFailedSORT;
            this.useMultipleAddresses = params.useMultipleAddresses;
            this.useMultipleAddressesUserHash = params.useMultipleAddressesUserHash;
            this.useMultipleAddressesMaxRetryAttempts = params.useMultipleAddressesMaxRetryAttempts;
            this.ignoreDeletedMails = params.ignoreDeletedMails;
            this.includeSharedInboxExplicitly = params.includeSharedInboxExplicitly;
        }
    }

    private static final Cache<UserAndContext, PrimaryIMAPProperties> CACHE_PRIMARY_PROPS = CacheBuilder.newBuilder().maximumSize(65536).expireAfterAccess(30, TimeUnit.MINUTES).build();

    /**
     * Clears the cache.
     */
    public static void invalidateCache() {
        CACHE_PRIMARY_PROPS.invalidateAll();
    }

    private static PrimaryIMAPProperties getPrimaryIMAPProps(int userId, int contextId) throws OXException {
        UserAndContext key = UserAndContext.newInstance(userId, contextId);
        PrimaryIMAPProperties primaryMailProps = CACHE_PRIMARY_PROPS.getIfPresent(key);
        if (null != primaryMailProps) {
            return primaryMailProps;
        }

        Callable<PrimaryIMAPProperties> loader = new Callable<PrimaryIMAPProperties>() {

            @Override
            public PrimaryIMAPProperties call() throws Exception {
                return doGetPrimaryIMAPProps(userId, contextId);
            }
        };

        try {
            return CACHE_PRIMARY_PROPS.get(key, loader);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof OXException ? (OXException) cause : new OXException(cause);
        }
    }

    static PrimaryIMAPProperties doGetPrimaryIMAPProps(int userId, int contextId) throws OXException {
        ConfigViewFactory viewFactory = Services.getService(ConfigViewFactory.class);
        if (null == viewFactory) {
            throw ServiceExceptionCode.absentService(ConfigViewFactory.class);
        }

        ConfigView view = viewFactory.getView(userId, contextId);

        PrimaryIMAPProperties.Params params = new PrimaryIMAPProperties.Params();

        StringBuilder logMessageBuilder = new StringBuilder(1024);
        List<Object> args = new ArrayList<>(16);

        logMessageBuilder.append("Primary IMAP properties successfully loaded for user {} in context {}{}");
        args.add(Integer.valueOf(userId));
        args.add(Integer.valueOf(contextId));
        args.add(Strings.getLineSeparator());

        {
            String tmp = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.imap.greeting.host.regex", view);
            tmp = Strings.isEmpty(tmp) ? null : tmp;
            if (null != tmp) {
                try {
                    Pattern pattern = Pattern.compile(tmp);
                    params.hostExtractingGreetingListener = new HostExtractingGreetingListener(pattern);

                    logMessageBuilder.append("  Host name regular expression: {}{}");
                    args.add(tmp);
                    args.add(Strings.getLineSeparator());
                } catch (PatternSyntaxException e) {
                    LOG.warn("Invalid expression for host name", e);
                    logMessageBuilder.append("  Host name regular expression: {}{}");
                    args.add("<none>");
                    args.add(Strings.getLineSeparator());
                }
            }
        }

        {
            String tmp = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.imap.rootSubfoldersAllowed", view);
            if (null == tmp) {
                params.rootSubfoldersAllowed = null;
            } else {
                params.rootSubfoldersAllowed = Boolean.valueOf(tmp);

                logMessageBuilder.append("  Root sub-folders allowed: {}{}");
                args.add(params.rootSubfoldersAllowed);
                args.add(Strings.getLineSeparator());
            }
        }

        {
            params.namespacePerUser = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.namespacePerUser", false, view);

            logMessageBuilder.append("  Namespace per User: {}{}");
            args.add(Autoboxing.valueOf(params.namespacePerUser));
            args.add(Strings.getLineSeparator());
        }

        {
            params.umlautFilterThreshold = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.imap.umlautFilterThreshold", 50, view);

            logMessageBuilder.append("  Umlaut filter threshold: {}{}");
            args.add(Autoboxing.valueOf(params.umlautFilterThreshold));
            args.add(Strings.getLineSeparator());
        }

        {
            params.maxMailboxNameLength = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.imap.maxMailboxNameLength", 60, view);

            logMessageBuilder.append("  Max. Mailbox Name Length: {}{}");
            args.add(Autoboxing.valueOf(params.maxMailboxNameLength));
            args.add(Strings.getLineSeparator());
        }

        {
            String invalids = ConfigViews.getNonEmptyPropertyFrom("com.openexchange.imap.invalidMailboxNameCharacters", view);
            if (Strings.isEmpty(invalids)) {
                params.invalidChars = new TIntHashSet(0);
            } else {
                final String[] sa = Strings.splitByWhitespaces(Strings.unquote(invalids));
                final int length = sa.length;
                TIntSet invalidChars = new TIntHashSet(length);
                for (int i = 0; i < length; i++) {
                    invalidChars.add(sa[i].charAt(0));
                }

                params.invalidChars = invalidChars;

                logMessageBuilder.append("  Invalid Mailbox Characters: {}{}");
                args.add(invalids);
                args.add(Strings.getLineSeparator());
            }

        }

        {
            params.allowESORT = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.allowESORT", true, view);

            logMessageBuilder.append("  Allow ESORT: {}{}");
            args.add(Autoboxing.valueOf(params.allowESORT));
            args.add(Strings.getLineSeparator());
        }

        {
            params.allowSORTDISPLAY = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.allowSORTDISPLAY", false, view);

            logMessageBuilder.append("  Allow SORT-DSIPLAY: {}{}");
            args.add(Autoboxing.valueOf(params.allowSORTDISPLAY));
            args.add(Strings.getLineSeparator());
        }

        {
            params.fallbackOnFailedSORT = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.fallbackOnFailedSORT", false, view);

            logMessageBuilder.append("  Fallback On Failed SORT: {}{}");
            args.add(Autoboxing.valueOf(params.fallbackOnFailedSORT));
            args.add(Strings.getLineSeparator());
        }

        {
            params.useMultipleAddresses = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.useMultipleAddresses", false, view);

            logMessageBuilder.append("  Use Multiple IP addresses: {}{}");
            args.add(Autoboxing.valueOf(params.useMultipleAddresses));
            args.add(Strings.getLineSeparator());
        }

        if (params.useMultipleAddresses) {
            params.useMultipleAddressesUserHash = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.useMultipleAddressesUserHash", false, view);

            logMessageBuilder.append("  Use User Hash for Multiple IP addresses: {}{}");
            args.add(Autoboxing.valueOf(params.useMultipleAddressesUserHash));
            args.add(Strings.getLineSeparator());
        }

        if (params.useMultipleAddresses) {
            params.useMultipleAddressesMaxRetryAttempts = ConfigViews.getDefinedIntPropertyFrom("com.openexchange.imap.useMultipleAddressesMaxRetries", 3, view);

            logMessageBuilder.append("  Use max. retry attempts for Multiple IP addresses: {}{}");
            args.add(Autoboxing.valueOf(params.useMultipleAddressesMaxRetryAttempts));
            args.add(Strings.getLineSeparator());
        }

        {
            params.ignoreDeletedMails = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.ignoreDeleted", false, view);

            logMessageBuilder.append("  Ignore deleted mails: {}{}");
            args.add(Autoboxing.valueOf(params.ignoreDeletedMails));
            args.add(Strings.getLineSeparator());
        }

        {
            params.includeSharedInboxExplicitly = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.imap.includeSharedInboxExplicitly", false, view);

            logMessageBuilder.append("  Include shared inbox explicitly: {}{}");
            args.add(Autoboxing.valueOf(params.includeSharedInboxExplicitly));
            args.add(Strings.getLineSeparator());
        }

        PrimaryIMAPProperties primaryIMAPProps = new PrimaryIMAPProperties(params);
        LOG.debug(logMessageBuilder.toString(), args.toArray(new Object[args.size()]));
        return primaryIMAPProps;
    }

    // --------------------------------------------------------------------------------------------------------------

    /*-
     * Fields for global properties
     */

    private final IMailProperties mailProperties;

    private boolean imapSort;

    private boolean imapSearch;

    private boolean forceImapSearch;

    private boolean fastFetch;

    private BoolCapVal supportsACLs;

    private int imapTimeout;

    private int imapConnectionTimeout;

    private int imapTemporaryDown;

    private int imapFailedAuthTimeout;

    private String imapAuthEnc;

    private String entity2AclImpl;

    private int blockSize;

    private int maxNumConnection;

    private final Map<String, Boolean> newACLExtMap;

    private String spamHandlerName;

    private boolean propagateClientIPAddress;

    private boolean enableTls;

    private boolean auditLogEnabled;

    private boolean debugLogEnabled;

    private boolean overwritePreLoginCapabilities;

    private Set<String> propagateHostNames;

    private boolean allowFolderCaches;

    private boolean allowFetchSingleHeaders;

    private String sContainerType;

    private String sslProtocols;

    private String cipherSuites;

    private HostExtractingGreetingListener hostExtractingGreetingListener;

    private boolean enableAttachmentSearch;

    private List<CommandExecutor> commandExecutors;

    /**
     * Initializes a new {@link IMAPProperties}
     */
    private IMAPProperties() {
        super();
        sContainerType = "boundary-aware";
        enableTls = true;
        auditLogEnabled = false;
        debugLogEnabled = false;
        overwritePreLoginCapabilities = false;
        maxNumConnection = -1;
        newACLExtMap = new NonBlockingHashMap<String, Boolean>();
        mailProperties = MailProperties.getInstance();
        propagateHostNames = Collections.emptySet();
        allowFetchSingleHeaders = true;
        allowFolderCaches = true;
        hostExtractingGreetingListener = null;
        enableAttachmentSearch = false;
        commandExecutors = null;
    }

    @Override
    protected void loadProperties0() throws OXException {
        StringBuilder logBuilder = new StringBuilder(1024);
        List<Object> args = new ArrayList<Object>(32);
        String lineSeparator = Strings.getLineSeparator();

        logBuilder.append("{}Loading global IMAP properties...{}");
        args.add(lineSeparator);
        args.add(lineSeparator);

        final ConfigurationService configuration = Services.getService(ConfigurationService.class);
        {
            final String allowFolderCachesStr = configuration.getProperty("com.openexchange.imap.allowFolderCaches", "true").trim();
            allowFolderCaches = "true".equalsIgnoreCase(allowFolderCachesStr);
            logBuilder.append("    IMAP allow folder caches: {}{}");
            args.add(Boolean.valueOf(allowFolderCaches));
            args.add(lineSeparator);
        }

        {
            final String str = configuration.getProperty("com.openexchange.imap.allowFetchSingleHeaders", "true").trim();
            allowFetchSingleHeaders = "true".equalsIgnoreCase(str);
            logBuilder.append("    IMAP allow FETCH single headers: {}{}");
            args.add(Boolean.valueOf(allowFetchSingleHeaders));
            args.add(lineSeparator);
        }

        {
            final String imapSortStr = configuration.getProperty("com.openexchange.imap.imapSort", "application").trim();
            imapSort = "imap".equalsIgnoreCase(imapSortStr);
            logBuilder.append("    IMAP-Sort: {}{}");
            args.add(Boolean.valueOf(imapSort));
            args.add(lineSeparator);
        }

        {
            final String imapSearchStr = configuration.getProperty("com.openexchange.imap.imapSearch", "force-imap").trim();
            forceImapSearch = "force-imap".equalsIgnoreCase(imapSearchStr);
            imapSearch = forceImapSearch || "imap".equalsIgnoreCase(imapSearchStr);
            logBuilder.append("    IMAP-Search: {}{}{}");
            args.add(Boolean.valueOf(imapSearch));
            args.add(forceImapSearch ? " (forced)" : "");
            args.add(lineSeparator);

        }

        {
            final String fastFetchStr = configuration.getProperty("com.openexchange.imap.imapFastFetch", STR_TRUE).trim();
            fastFetch = Boolean.parseBoolean(fastFetchStr);
            logBuilder.append("    Fast Fetch Enabled: {}{}");
            args.add(Boolean.valueOf(fastFetch));
            args.add(lineSeparator);
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.imap.propagateClientIPAddress", STR_FALSE).trim();
            propagateClientIPAddress = Boolean.parseBoolean(tmp);
            logBuilder.append("    Propagate Client IP Address: {}{}");
            args.add(Boolean.valueOf(propagateClientIPAddress));
            args.add(lineSeparator);
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.imap.enableTls", STR_TRUE).trim();
            enableTls = Boolean.parseBoolean(tmp);
            logBuilder.append("    Enable TLS: {}{}");
            args.add(Boolean.valueOf(enableTls));
            args.add(lineSeparator);
        }

        {
            String tmp = configuration.getProperty("com.openexchange.imap.auditLog.enabled", STR_FALSE).trim();
            auditLogEnabled = Boolean.parseBoolean(tmp);
            logBuilder.append("    Audit Log Enabled: {}{}");
            args.add(Boolean.valueOf(auditLogEnabled));
            args.add(lineSeparator);
        }

        {
            String tmp = configuration.getProperty("com.openexchange.imap.debugLog.enabled", STR_FALSE).trim();
            debugLogEnabled = Boolean.parseBoolean(tmp);
            logBuilder.append("    Debug Log Enabled: {}{}");
            args.add(Boolean.valueOf(debugLogEnabled));
            args.add(lineSeparator);
        }

        {
            String tmp = configuration.getProperty("com.openexchange.imap.overwritePreLoginCapabilities", STR_FALSE).trim();
            overwritePreLoginCapabilities = Boolean.parseBoolean(tmp);
            logBuilder.append("    Overwrite Pre-Login Capabilities: {}{}");
            args.add(Boolean.valueOf(overwritePreLoginCapabilities));
            args.add(lineSeparator);
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.imap.propagateHostNames", "").trim();
            if (tmp.length() > 0) {
                propagateHostNames = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(tmp.split(" *, *"))));
            } else {
                propagateHostNames = Collections.emptySet();
            }
            logBuilder.append("    Propagate Host Names: {}{}");
            args.add(propagateHostNames.isEmpty() ? "<none>" : propagateHostNames.toString());
            args.add(lineSeparator);
        }

        {
            final String supportsACLsStr = configuration.getProperty("com.openexchange.imap.imapSupportsACL", STR_FALSE).trim();
            supportsACLs = BoolCapVal.parseBoolCapVal(supportsACLsStr);
            logBuilder.append("    Support ACLs: {}{}");
            args.add(supportsACLs);
            args.add(lineSeparator);
        }

        {
            final String imapTimeoutStr = configuration.getProperty("com.openexchange.imap.imapTimeout", "0").trim();
            imapTimeout = parsePositiveInt(imapTimeoutStr);
            if (imapTimeout < 0) {
                imapTimeout = 0;
                logBuilder.append("    IMAP Timeout: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(imapTimeoutStr);
                args.add(Integer.valueOf(imapTimeout));
                args.add(lineSeparator);
            } else {
                logBuilder.append("    IMAP Timeout: {}{}");
                args.add(Integer.valueOf(imapTimeout));
                args.add(lineSeparator);
            }
        }

        {
            final String imapConTimeoutStr = configuration.getProperty("com.openexchange.imap.imapConnectionTimeout", "0").trim();
            imapConnectionTimeout = parsePositiveInt(imapConTimeoutStr);
            if (imapConnectionTimeout < 0) {
                imapConnectionTimeout = 0;
                logBuilder.append("    IMAP Connection Timeout: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(imapConTimeoutStr);
                args.add(Integer.valueOf(imapConnectionTimeout));
                args.add(lineSeparator);
            } else {
                logBuilder.append("    IMAP Connection Timeout: {}{}");
                args.add(Integer.valueOf(imapConnectionTimeout));
                args.add(lineSeparator);
            }
        }

        {
            final String imapTempDownStr = configuration.getProperty("com.openexchange.imap.imapTemporaryDown", "0").trim();
            imapTemporaryDown = parsePositiveInt(imapTempDownStr);
            if (imapTemporaryDown < 0) {
                imapTemporaryDown = 0;
                logBuilder.append("    IMAP Temporary Down: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(imapTempDownStr);
                args.add(Integer.valueOf(imapTemporaryDown));
                args.add(lineSeparator);
            } else {
                logBuilder.append("    IMAP Temporary Down: {}{}");
                args.add(Integer.valueOf(imapTemporaryDown));
                args.add(lineSeparator);
            }
        }

        {
            final String imapFailedAuthTimeoutStr = configuration.getProperty("com.openexchange.imap.failedAuthTimeout", "10000").trim();
            imapFailedAuthTimeout = parsePositiveInt(imapFailedAuthTimeoutStr);
            if (imapFailedAuthTimeout < 0) {
                imapFailedAuthTimeout = 10000;
                logBuilder.append("    IMAP Failed Auth Timeout: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(imapFailedAuthTimeoutStr);
                args.add(Integer.valueOf(imapFailedAuthTimeout));
                args.add(lineSeparator);
            } else {
                logBuilder.append("    IMAP Failed Auth Timeout: {}{}");
                args.add(Integer.valueOf(imapFailedAuthTimeout));
                args.add(lineSeparator);
            }
        }

        {
            final String imapAuthEncStr = configuration.getProperty("com.openexchange.imap.imapAuthEnc", "UTF-8").trim();
            if (CharsetDetector.isValid(imapAuthEncStr)) {
                imapAuthEnc = imapAuthEncStr;
                logBuilder.append("    Authentication Encoding: {}{}");
                args.add(imapAuthEnc);
                args.add(lineSeparator);
            } else {
                imapAuthEnc = "UTF-8";
                logBuilder.append("    Authentication Encoding: Unsupported charset \"{}\". Setting to fallback: {}{}");
                args.add(imapAuthEncStr);
                args.add(imapAuthEnc);
                args.add(lineSeparator);
            }
        }

        {
            entity2AclImpl = configuration.getProperty("com.openexchange.imap.User2ACLImpl");
            if (null == entity2AclImpl) {
                throw MailConfigException.create("Missing IMAP property \"com.openexchange.imap.User2ACLImpl\"");
            }
            entity2AclImpl = entity2AclImpl.trim();
        }

        {
            final String blockSizeStr = configuration.getProperty("com.openexchange.imap.blockSize", "1000").trim();
            blockSize = parsePositiveInt(blockSizeStr);
            if (blockSize < 0) {
                blockSize = 1000;
                logBuilder.append("    Block Size: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(blockSizeStr);
                args.add(Integer.valueOf(blockSize));
                args.add(lineSeparator);
            } else {
                logBuilder.append("    Block Size: {}{}");
                args.add(Integer.valueOf(blockSize));
                args.add(lineSeparator);
            }
        }

        {
            String tmp = configuration.getProperty("com.openexchange.imap.maxNumExternalConnections");
            if (null != tmp) {
                tmp = tmp.trim();
                if (0 == tmp.length()) {
                    IMAPProtocol.getInstance().setOverallExternalMaxCount(-1);
                    logBuilder.append("    Max. Number of External Connections: No restrictions{}");
                    args.add(lineSeparator);
                } else if (tmp.indexOf(':') > 0) {
                    // Expect a comma-separated list
                    final String[] sa = tmp.split(" *, *");
                    if (sa.length > 0) {
                        try {
                            final IMAPProtocol imapProtocol = IMAPProtocol.getInstance();
                            imapProtocol.initExtMaxCountMap();
                            logBuilder.append("    Max. Number of External Connections: ");
                            boolean first = true;
                            for (String desc : sa) {
                                final int pos = desc.indexOf(':');
                                if (pos > 0) {
                                    try {
                                        imapProtocol.putIfAbsent(desc.substring(0, pos), Integer.parseInt(desc.substring(pos + 1).trim()));
                                        if (first) {
                                            first = false;
                                        } else {
                                            logBuilder.append(", ");
                                        }
                                        logBuilder.append("{}");
                                        args.add(desc);
                                    } catch (RuntimeException e) {
                                        LOG.warn("Max. Number of External Connections: Invalid entry: {}", desc, e);
                                    }
                                }
                            }
                            logBuilder.append("{}");
                            args.add(lineSeparator);
                        } catch (@SuppressWarnings("unused") final Exception e) {
                            IMAPProtocol.getInstance().setOverallExternalMaxCount(-1);
                            logBuilder.append("    Max. Number of External Connections: Invalid value \"{}\". Setting to fallback: No restrictions{}");
                            args.add(tmp);
                            args.add(lineSeparator);
                        }
                    }
                } else {
                    // Expect a single integer value
                    int maxCount = parsePositiveInt(tmp);
                    if (maxCount < 0) {
                        IMAPProtocol.getInstance().setOverallExternalMaxCount(-1);
                        logBuilder.append("    Max. Number of External Connections: Invalid value \"{}\". Setting to fallback: No restrictions{}");
                        args.add(tmp);
                        args.add(lineSeparator);
                    } else {
                        IMAPProtocol.getInstance().setOverallExternalMaxCount(maxCount);
                        logBuilder.append("    Max. Number of External Connections: {} (applied to all external IMAP accounts){}");
                        args.add(tmp);
                        args.add(lineSeparator);
                    }
                }
            }
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.imap.maxNumConnections", "-1").trim();
            maxNumConnection = parsePositiveInt(tmp);
            if (maxNumConnection < 0) {
                maxNumConnection = -1;
                logBuilder.append("    Max. Number of connections: Invalid value \"{}\". Setting to fallback: {}{}");
                args.add(tmp);
                args.add(Integer.valueOf(maxNumConnection));
                args.add(lineSeparator);
            } else {
                logBuilder.append("    Max. Number of connections: {}{}");
                args.add(Integer.valueOf(maxNumConnection));
                args.add(lineSeparator);
                if (maxNumConnection > 0) {
                    IMAPProtocol.getInstance().setMaxCount(maxNumConnection);
                }
            }
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.imap.storeContainerType", "boundary-aware").trim();
            sContainerType = tmp;
            logBuilder.append("    Store container type: {}{}");
            args.add(sContainerType);
            args.add(lineSeparator);
        }

        spamHandlerName = configuration.getProperty("com.openexchange.imap.spamHandler", SpamHandler.SPAM_HANDLER_FALLBACK).trim();
        logBuilder.append("    Spam Handler: {}{}");
        args.add(spamHandlerName);
        args.add(lineSeparator);

        sslProtocols = configuration.getProperty("com.openexchange.imap.ssl.protocols", "SSLv3 TLSv1").trim();
        logBuilder.append("    Supported SSL protocols: {}{}");
        args.add(sslProtocols);
        args.add(lineSeparator);

        {
            final String tmp = configuration.getProperty("com.openexchange.imap.ssl.ciphersuites", "").trim();
            this.cipherSuites = Strings.isEmpty(tmp) ? null : tmp;
            logBuilder.append("    Supported SSL cipher suites: {}{}");
            args.add(null == this.cipherSuites ? "<default>" : cipherSuites);
            args.add(lineSeparator);
        }

        {
            String tmp = configuration.getProperty("com.openexchange.imap.greeting.host.regex", "").trim();
            tmp = Strings.isEmpty(tmp) ? null : tmp;
            if (null != tmp) {
                try {
                    Pattern pattern = Pattern.compile(tmp);
                    hostExtractingGreetingListener = new HostExtractingGreetingListener(pattern);
                    logBuilder.append("    Host name regular expression: {}{}");
                    args.add(tmp);
                    args.add(lineSeparator);
                } catch (@SuppressWarnings("unused") PatternSyntaxException e) {
                    logBuilder.append("    Host name regular expression: Invalid value \"{}\". Using no host name extraction{}");
                    args.add(tmp);
                    args.add(lineSeparator);
                }
            }
        }

        {
            String tmp = configuration.getProperty("com.openexchange.imap.attachmentMarker.enabled", STR_FALSE).trim();
            enableAttachmentSearch = Boolean.parseBoolean(tmp);
            logBuilder.append("    Enable attachment search: {}{}");
            args.add(Boolean.valueOf(enableAttachmentSearch));
            args.add(lineSeparator);
        }

        {

            MonitoringCommandExecutor monitoringCommandExecutor = initMonitoringCommandExecutor(configuration);
            List<CommandExecutor> commandExecutorList = initCircuitBreakers(configuration, logBuilder, args, monitoringCommandExecutor);
            commandExecutorList.add(monitoringCommandExecutor);
            commandExecutors = commandExecutorList;
            for (CommandExecutor commandExecutor : commandExecutorList) {
                IMAPStore.addCommandExecutor(commandExecutor);
            }

            IMAPReloadable.getInstance().addReloadable(new Reloadable() {

                @Override
                public Interests getInterests() {
                    return Reloadables.interestsForProperties("com.openexchange.imap.breaker.*", "com.openexchange.imap.metrics.*");
                }

                @SuppressWarnings("synthetic-access")
                @Override
                public void reloadConfiguration(ConfigurationService configService) {
                    List<CommandExecutor> commandExecutorList = commandExecutors;
                    if (null != commandExecutorList) {
                        for (CommandExecutor commandExecutor : commandExecutorList) {
                            IMAPStore.removeCommandExecutor(commandExecutor);
                        }
                    }
                    commandExecutors = null;

                    MonitoringCommandExecutor monitoringCommandExecutor = initMonitoringCommandExecutor(configuration);
                    commandExecutorList = initCircuitBreakers(configuration, logBuilder, args, monitoringCommandExecutor);
                    commandExecutorList.add(monitoringCommandExecutor);
                    commandExecutors = commandExecutorList;
                    for (CommandExecutor commandExecutor : commandExecutorList) {
                        IMAPStore.addCommandExecutor(commandExecutor);
                    }
                }

            });
        }

        logBuilder.append("Global IMAP properties successfully loaded!");

        LOG.info(logBuilder.toString(), args.toArray(new Object[args.size()]));
    }

    /**
     * Initializes and configures the command executor that wraps all performed IMAP commands with proper monitoring.
     *
     * @param configuration Current {@link ConfigurationService} instance
     * @return The command executor
     */
    private static MonitoringCommandExecutor initMonitoringCommandExecutor(ConfigurationService configuration) {
        MonitoringCommandExecutor.Config monitoringConfig = new MonitoringCommandExecutor.Config();
        monitoringConfig.setEnabled(configuration.getBoolProperty("com.openexchange.imap.metrics.enabled", true));
        monitoringConfig.setGroupByPrimaryHosts(configuration.getBoolProperty("com.openexchange.imap.metrics.groupByPrimaryHosts", false));
        monitoringConfig.setGroupByPrimaryEndpoints(configuration.getBoolProperty("com.openexchange.imap.metrics.groupByPrimaryEndpoints", false));
        monitoringConfig.setMeasureExternalAccounts(configuration.getBoolProperty("com.openexchange.imap.metrics.measureExternalAccounts", true));
        monitoringConfig.setGroupByExternalHosts(configuration.getBoolProperty("com.openexchange.imap.metrics.groupByExternalHosts", false));
        monitoringConfig.setGroupByCommands(configuration.getBoolProperty("com.openexchange.imap.metrics.groupByCommands", false));
        monitoringConfig.setCommandWhitelist(configuration.getProperty("com.openexchange.imap.metrics.commandWhitelist", MonitoringCommandExecutor.Config.DEFAULT_COMMAND_WHITELIST_STRING, ","));


        MonitoringCommandExecutor monitoringCommandExecutor = new MonitoringCommandExecutor(monitoringConfig);
        return monitoringCommandExecutor;
    }

    /**
     * Initializes IMAP circuit breakers and returns their respective command executors.
     *
     * @param configuration Current {@link ConfigurationService} instance
     * @param logBuilder Log output {@link StringBuilder}
     * @param args Log output format arguments
     * @param monitoringCommandExecutor The monitoring command executor as delegate for the actual IMAP commands
     * @return List of command executors in expected evaluation order
     */
    private static List<CommandExecutor> initCircuitBreakers(ConfigurationService configuration, StringBuilder logBuilder,
            List<Object> args, MonitoringCommandExecutor monitoringCommandExecutor) {
        // Load generic breaker
        GenericFailsafeCircuitBreakerCommandExecutor genericBreaker = null;
        Optional<AbstractFailsafeCircuitBreakerCommandExecutor> optionalGenericBreaker = initGenericCircuitBreaker(configuration, monitoringCommandExecutor);
        if (optionalGenericBreaker.isPresent()) {
            genericBreaker = (GenericFailsafeCircuitBreakerCommandExecutor) optionalGenericBreaker.get();
            logBuilder.append("    Added generic circuit breaker{}");
            args.add(Strings.getLineSeparator());
        }

        // Collect names of other breaker-associated properties
        Set<String> names = null;
        String prefix = "com.openexchange.imap.breaker.";
        for (Iterator<String> it = configuration.propertyNames(); it.hasNext();) {
            String propName = it.next();
            if (propName.startsWith(prefix, 0)) {
                int pos = propName.indexOf('.', prefix.length());
                if (pos > 0) {
                    if (names == null) {
                        names = new HashSet<>();
                    }
                    names.add(propName.substring(prefix.length(), pos));
                }
            }
        }

        // Any collected?
        if (names == null) {
            return genericBreaker == null ? new ArrayList<>(0) : new ArrayList<>(Collections.singletonList(genericBreaker));
        }

        // Iterate them
        List<CommandExecutor> breakerList = new ArrayList<>(names.size() + 1);
        if (genericBreaker != null) {
            breakerList.add(genericBreaker);
        }
        for (String name : names) {
            Optional<AbstractFailsafeCircuitBreakerCommandExecutor> optBreaker = initCircuitBreakerForName(Optional.of(name), configuration, genericBreaker, monitoringCommandExecutor);
            if (optBreaker.isPresent()) {
                AbstractFailsafeCircuitBreakerCommandExecutor circuitBreaker = optBreaker.get();
                breakerList.add(circuitBreaker);
                if ("primary".equals(name)) {
                    logBuilder.append("    Added circuit breaker for primary account{}");
                    args.add(Strings.getLineSeparator());
                } else {
                    logBuilder.append("    Added \"{}\"  circuit breaker for hosts: {}{}");
                    args.add(name);
                    args.add(circuitBreaker.getHostList().get().getHostList());
                    args.add(Strings.getLineSeparator());
                }
            }
        }

        return breakerList;
    }

    private static Optional<AbstractFailsafeCircuitBreakerCommandExecutor> initGenericCircuitBreaker(ConfigurationService configuration,
            MonitoringCommandExecutor monitoringCommandExecutor) {
        return initCircuitBreakerForName(Optional.empty(), configuration, null, monitoringCommandExecutor);
    }

    /**
     * Initializes the circuit breaker with the optional infix.
     *
     * @param optionalInfix The optional circuit breaker name
     * @param configuration The configuration service to use
     * @param genericBreaker The already initialized generic circuit breaker or <code>null</code>
     * @param monitoringCommandExecutor The delegate executor
     * @return An optional {@link AbstractFailsafeCircuitBreakerCommandExecutor}
     */
    private static Optional<AbstractFailsafeCircuitBreakerCommandExecutor> initCircuitBreakerForName(Optional<String> optionalInfix, ConfigurationService configuration,
            GenericFailsafeCircuitBreakerCommandExecutor genericBreaker, MonitoringCommandExecutor monitoringCommandExecutor) {
        if (!optionalInfix.isPresent()) {
            return initGenericCircuitBreakerInternal(configuration, monitoringCommandExecutor);
        } // End of generic

        String infix = optionalInfix.get();

        if ("generic".equals(infix)) {
            return initGenericCircuitBreakerInternal(configuration, monitoringCommandExecutor);
        } // End of generic

        if ("primary".equals(infix)) {
            return initPrimaryCircuitBreaker(configuration, genericBreaker, monitoringCommandExecutor);
        } // End of primary

        return initSpecificCircuitBreaker(configuration, genericBreaker, infix, monitoringCommandExecutor);
    }

    /**
     * Initializes the generic circuit breaker.
     *
     * @param configuration The configuration service to use
     * @param monitoringCommandExecutor
     * @return An optional generic circuit breaker
     */
    private static Optional<AbstractFailsafeCircuitBreakerCommandExecutor> initGenericCircuitBreakerInternal(ConfigurationService configuration,
            MonitoringCommandExecutor monitoringCommandExecutor) {
        // The generic IMAP circuit breaker
        String propertyName = "com.openexchange.imap.breaker.enabled";
        boolean enabled = configuration.getBoolProperty(propertyName, false);
        if (!enabled) {
            return Optional.empty();
        }

        int failures;
        {
            propertyName = "com.openexchange.imap.breaker.failureThreshold";
            String sFailures = configuration.getProperty(propertyName, "5").trim();
            if (Strings.isEmpty(sFailures)) {
                LOG.warn("Missing value for property {}. Skipping generic breaker configuration", propertyName);
                return Optional.empty();
            }
            try {
                failures = Integer.parseInt(sFailures.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping generic breaker configuration", propertyName);
                return Optional.empty();
            }
        }
        int failureExecutions = failures;
        {
            propertyName = "com.openexchange.imap.breaker.failureExecutions";
            String sFailures = configuration.getProperty(propertyName, "").trim();
            if (Strings.isNotEmpty(sFailures)) {
                try {
                    failureExecutions = Integer.parseInt(sFailures.trim());
                    if (failureExecutions == 0) {
                        LOG.warn("Invalid value for property {}, value must not be '0' to prevent division by zero", propertyName);
                        return Optional.empty();
                    }
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    LOG.warn("Invalid value for property {}. Not a number. Skipping generic breaker configuration", propertyName);
                    return Optional.empty();
                }
            }
        }
        if (failureExecutions < failures) {
            failureExecutions = failures;
        }

        int success;
        {
            propertyName = "com.openexchange.imap.breaker.successThreshold";
            String sSuccess = configuration.getProperty(propertyName, "2").trim();
            if (Strings.isEmpty(sSuccess)) {
                LOG.warn("Missing value for property {}. Skipping generic breaker configuration", propertyName);
                return Optional.empty();
            }
            try {
                success = Integer.parseInt(sSuccess.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping generic breaker configuration", propertyName);
                return Optional.empty();
            }
        }
        int successExecutions = success;
        {
            propertyName = "com.openexchange.imap.breaker.successExecutions";
            String sSuccess = configuration.getProperty(propertyName, "").trim();
            if (Strings.isNotEmpty(sSuccess)) {
                try {
                    successExecutions = Integer.parseInt(sSuccess.trim());
                    if (successExecutions == 0) {
                        LOG.warn("Invalid value for property {}, value must not be '0' to prevent division by zero", propertyName);
                        return Optional.empty();
                    }
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    LOG.warn("Invalid value for property {}. Not a number. Skipping generic breaker configuration", propertyName);
                    return Optional.empty();
                }
            }
        }
        if (successExecutions < success) {
            successExecutions = success;
        }

        long delayMillis;
        {
            propertyName = "com.openexchange.imap.breaker.delayMillis";
            String sDelayMillis = configuration.getProperty(propertyName, "60000").trim();
            if (Strings.isEmpty(sDelayMillis)) {
                LOG.warn("Missing value for property {}. Skipping generic breaker configuration", propertyName);
                return Optional.empty();
            }
            try {
                delayMillis = Long.parseLong(sDelayMillis.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping generic breaker configuration", propertyName);
                return Optional.empty();
            }
        }

        return Optional.of(new GenericFailsafeCircuitBreakerCommandExecutor(ratioOf(failures, failureExecutions), ratioOf(success, successExecutions), delayMillis, monitoringCommandExecutor));
    }

    /**
     * Initializes a specific circuit breaker.
     *
     * @param configuration The configuration service to use
     * @param genericBreaker The already initialized generic circuit breaker or <code>null</code>
     * @param infix The name of the circuit breaker
     * @param monitoringCommandExecutor
     * @return An optional specific circuit breaker
     */
    private static Optional<AbstractFailsafeCircuitBreakerCommandExecutor> initSpecificCircuitBreaker(ConfigurationService configuration,
            GenericFailsafeCircuitBreakerCommandExecutor genericBreaker, String infix, MonitoringCommandExecutor monitoringCommandExecutor) {
        // Specific
        String propertyName = "com.openexchange.imap.breaker." + infix + ".hosts";
        String hosts = configuration.getProperty(propertyName, "");
        if (Strings.isEmpty(hosts)) {
            LOG.warn("Missing value for property {}. Skipping breaker configuration for {}", propertyName, infix);
            return Optional.empty();
        }

        propertyName = "com.openexchange.imap.breaker." + infix + ".enabled";
        boolean enabled = configuration.getBoolProperty(propertyName, false);
        if (!enabled) {
            if (genericBreaker != null) {
                genericBreaker.addHostsToExclude(hosts);
            }
            return Optional.empty();
        }

        propertyName = "com.openexchange.imap.breaker." + infix + ".ports";
        String ports = configuration.getProperty(propertyName, "");
        if (Strings.isEmpty(ports)) {
            ports = null;
        }

        int failures;
        {
            propertyName = "com.openexchange.imap.breaker." + infix + ".failureThreshold";
            String sFailures = configuration.getProperty(propertyName, "5").trim();
            if (Strings.isEmpty(sFailures)) {
                LOG.warn("Missing value for property {}. Skipping breaker configuration for {}", propertyName, infix);
                return Optional.empty();
            }
            try {
                failures = Integer.parseInt(sFailures.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for {}", propertyName, infix);
                return Optional.empty();
            }
        }
        int failureExecutions = failures;
        {
            propertyName = "com.openexchange.imap.breaker." + infix + ".failureExecutions";
            String sFailures = configuration.getProperty(propertyName, "").trim();
            if (Strings.isNotEmpty(sFailures)) {
                try {
                    failureExecutions = Integer.parseInt(sFailures.trim());
                    if (failureExecutions == 0) {
                        LOG.warn("Invalid value for property {}, value must not be '0' to prevent division by zero", propertyName);
                        return Optional.empty();
                    }
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for {}", propertyName, infix);
                    return Optional.empty();
                }
            }
        }
        if (failureExecutions < failures) {
            failureExecutions = failures;
        }

        int success;
        {
            propertyName = "com.openexchange.imap.breaker." + infix + ".successThreshold";
            String sSuccess = configuration.getProperty(propertyName, "2").trim();
            if (Strings.isEmpty(sSuccess)) {
                LOG.warn("Missing value for property {}. Skipping breaker configuration for {}", propertyName, infix);
                return Optional.empty();
            }
            try {
                success = Integer.parseInt(sSuccess.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for {}", propertyName, infix);
                return Optional.empty();
            }
        }
        int successExecutions = success;
        {
            propertyName = "com.openexchange.imap.breaker." + infix + ".successExecutions";
            String sSuccess = configuration.getProperty(propertyName, "").trim();
            if (Strings.isNotEmpty(sSuccess)) {
                try {
                    successExecutions = Integer.parseInt(sSuccess.trim());
                    if (successExecutions == 0) {
                        LOG.warn("Invalid value for property {}, value must not be '0' to prevent division by zero", propertyName);
                        return Optional.empty();
                    }
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for {}", propertyName, infix);
                    return Optional.empty();
                }
            }
        }
        if (successExecutions < success) {
            successExecutions = success;
        }

        long delayMillis;
        {
            propertyName = "com.openexchange.imap.breaker." + infix + ".delayMillis";
            String sDelayMillis = configuration.getProperty(propertyName, "60000").trim();
            if (Strings.isEmpty(sDelayMillis)) {
                LOG.warn("Missing value for property {}. Skipping breaker configuration for {}", propertyName, infix);
                return Optional.empty();
            }
            try {
                delayMillis = Long.parseLong(sDelayMillis.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for {}", propertyName, infix);
                return Optional.empty();
            }
        }

        HostList hostList = HostList.valueOf(hosts);
        Set<Integer> portSet;
        if (null == ports) {
            portSet = null;
        } else {
            portSet = new HashSet<>(6);
            for (String sPort : Strings.splitByComma(ports)) {
                try {
                    portSet.add(Integer.valueOf(sPort.trim()));
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    LOG.warn("Invalid value for port. Not a number. Skipping breaker configuration for {}", infix);
                    return Optional.empty();
                }
            }
        }

        return Optional.of(new FailsafeCircuitBreakerCommandExecutor(infix, hostList, portSet, ratioOf(failures, failureExecutions), ratioOf(success, successExecutions), delayMillis, 100, monitoringCommandExecutor));
    }

    /**
     * Initializes the primary account circuit breaker.
     *
     * @param configuration The configuration service to use
     * @param genericBreaker The already initialized generic circuit breaker or <code>null</code>
     * @param monitoringCommandExecutor
     * @return An optional primary account circuit breaker
     */
    private static Optional<AbstractFailsafeCircuitBreakerCommandExecutor> initPrimaryCircuitBreaker(ConfigurationService configuration,
            GenericFailsafeCircuitBreakerCommandExecutor genericBreaker, MonitoringCommandExecutor monitoringCommandExecutor) {
        // The IMAP circuit breaker form primary account
        String propertyName = "com.openexchange.imap.breaker.primary.enabled";
        boolean enabled = configuration.getBoolProperty(propertyName, false);
        if (!enabled) {
            if (genericBreaker != null) {
                genericBreaker.excludePrimaryAccount();
            }
            return Optional.empty();
        }

        boolean applyPerEndpoint;
        {
            propertyName = "com.openexchange.imap.breaker.primary.applyPerEndpoint";
            applyPerEndpoint = configuration.getBoolProperty(propertyName, true);
        }

        int failures;
        {
            propertyName = "com.openexchange.imap.breaker.primary.failureThreshold";
            String sFailures = configuration.getProperty(propertyName, "5").trim();
            if (Strings.isEmpty(sFailures)) {
                LOG.warn("Missing value for property {}. Skipping breaker configuration for primary account", propertyName);
                return Optional.empty();
            }
            try {
                failures = Integer.parseInt(sFailures.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for primary account", propertyName);
                return Optional.empty();
            }
        }
        int failureExecutions = failures;
        {
            propertyName = "com.openexchange.imap.breaker.primary.failureExecutions";
            String sFailures = configuration.getProperty(propertyName, "").trim();
            if (Strings.isNotEmpty(sFailures)) {
                try {
                    failureExecutions = Integer.parseInt(sFailures.trim());
                    if (failureExecutions == 0) {
                        LOG.warn("Invalid value for property {}, value must not be '0' to prevent division by zero", propertyName);
                        return Optional.empty();
                    }
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for primary account", propertyName);
                    return Optional.empty();
                }
            }
        }
        if (failureExecutions < failures) {
            failureExecutions = failures;
        }

        int success;
        {
            propertyName = "com.openexchange.imap.breaker.primary.successThreshold";
            String sSuccess = configuration.getProperty(propertyName, "2").trim();
            if (Strings.isEmpty(sSuccess)) {
                LOG.warn("Missing value for property {}. Skipping breaker configuration for primary account", propertyName);
                return Optional.empty();
            }
            try {
                success = Integer.parseInt(sSuccess.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for primary account", propertyName);
                return Optional.empty();
            }
        }
        int successExecutions = success;
        {
            propertyName = "com.openexchange.imap.breaker.primary.successExecutions";
            String sSuccess = configuration.getProperty(propertyName, "").trim();
            if (Strings.isNotEmpty(sSuccess)) {
                try {
                    successExecutions = Integer.parseInt(sSuccess.trim());
                    if (successExecutions == 0) {
                        LOG.warn("Invalid value for property {}, value must not be '0' to prevent division by zero", propertyName);
                        return Optional.empty();
                    }
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for primary account", propertyName);
                    return Optional.empty();
                }
            }
        }
        if (successExecutions < success) {
            successExecutions = success;
        }

        long delayMillis;
        {
            propertyName = "com.openexchange.imap.breaker.primary.delayMillis";
            String sDelayMillis = configuration.getProperty(propertyName, "60000").trim();
            if (Strings.isEmpty(sDelayMillis)) {
                LOG.warn("Missing value for property {}. Skipping breaker configuration for primary account", propertyName);
                return Optional.empty();
            }
            try {
                delayMillis = Long.parseLong(sDelayMillis.trim());
            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                LOG.warn("Invalid value for property {}. Not a number. Skipping breaker configuration for primary account", propertyName);
                return Optional.empty();
            }
        }

        return Optional.of(new PrimaryFailsafeCircuitBreakerCommandExecutor(ratioOf(failures, failureExecutions), ratioOf(success, successExecutions), delayMillis, applyPerEndpoint, monitoringCommandExecutor));
    }

    /**
     * Creates a new {@link Ratio} object avoiding division by zero. If the
     * denominator is zero then a new {@link Ratio} object will be created
     * with numerator equals to 0 and denominator equals to 1.
     *
     * @param numerator The numerator
     * @param denominator The denominator
     * @return The new {@link Ratio} object
     */
    private static Ratio ratioOf(int numerator, int denominator) {
        return denominator == 0 ? new Ratio(0, 1) : new Ratio(numerator, denominator);
    }

    @Override
    protected void resetFields() {
        imapSort = false;
        imapSearch = false;
        forceImapSearch = false;
        fastFetch = true;
        propagateClientIPAddress = false;
        enableTls = true;
        auditLogEnabled = false;
        debugLogEnabled = false;
        overwritePreLoginCapabilities = false;
        propagateHostNames = Collections.emptySet();
        supportsACLs = null;
        imapTimeout = 0;
        imapConnectionTimeout = 0;
        imapTemporaryDown = 0;
        imapFailedAuthTimeout = 10000;
        imapAuthEnc = null;
        entity2AclImpl = null;
        blockSize = 0;
        maxNumConnection = -1;
        sContainerType = "boundary-aware";
        spamHandlerName = null;
        sslProtocols = "SSLv3 TLSv1";
        cipherSuites = null;
        hostExtractingGreetingListener = null;
        enableAttachmentSearch = false;
        List<CommandExecutor> commandExecutorsList = commandExecutors;
        commandExecutors = null;
        if (null != commandExecutorsList) {
            for (CommandExecutor commandExecutor : commandExecutorsList) {
                IMAPStore.removeCommandExecutor(commandExecutor);
            }
        }
    }

    /**
     * Gets the container type.
     *
     * @return The container type
     */
    public String getsContainerType() {
        return sContainerType;
    }

    /**
     * Gets the {@link Entity2ACL}.
     *
     * @return The {@link Entity2ACL}
     */
    public String getEntity2AclImpl() {
        return entity2AclImpl;
    }

    /**
     * Gets the spam handler name.
     *
     * @return The spam handler name
     */
    public String getSpamHandlerName() {
        return spamHandlerName;
    }

    /**
     * Gets the command executors
     *
     * @return The command executors
     */
    public List<CommandExecutor> getCommandExecutors() {
        return commandExecutors;
    }

    /**
     * Gets the greeting listener to parse the host name information from <b><i>primary</i></b> IMAP server's greeting string.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The host name extractor or <code>null</code>
     */
    public HostExtractingGreetingListener getHostNameRegex(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.hostExtractingGreetingListener;
        } catch (Exception e) {
            LOG.error("Failed to get host name expression for user {} in context {}. Using default {} instead.", I(userId), I(contextId), hostExtractingGreetingListener, e);
            return hostExtractingGreetingListener;
        }
    }

    /**
     * Checks whether possible multiple IP addresses for a host name are supposed to be considered.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to use multiple IP addresses; otherwise <code>false</code>
     */
    public boolean isUseMultipleAddresses(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.useMultipleAddresses;
        } catch (Exception e) {
            LOG.error("Failed to check for usage of multiple addresses for user {} in context {}. Using default default {} instead.", I(userId), I(contextId), Boolean.FALSE.toString(), e);
            return false;
        }
    }

    /**
     * Checks whether a user hash should be used for selecting one of possible multiple IP addresses for a host name.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * Only effective if {@link #isUseMultipleAddresses(int, int)} returns <code>true</code>!
     * </div>
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to use multiple IP addresses; otherwise <code>false</code>
     */
    public boolean isUseMultipleAddressesUserHash(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.useMultipleAddressesUserHash;
        } catch (Exception e) {
            LOG.error("Failed to get hash for multiple addresses for user {} in context {}. Using default default {} instead.", I(userId), I(contextId), Boolean.FALSE.toString(), e);
            return false;
        }
    }

    /**
     * Gets the max. number of retry attempts when failing over to another IP address-
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * Only effective if {@link #isUseMultipleAddresses(int, int)} returns <code>true</code>!
     * </div>
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The max. wait timeout or <code>-1</code>
     */
    public int getMultipleAddressesMaxRetryAttempts(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.useMultipleAddressesMaxRetryAttempts;
        } catch (Exception e) {
            LOG.error("Failed to get max. retry attempts for multiple addresses for user {} in context {}. Using default default {} instead.", I(userId), I(contextId), Integer.valueOf(-1), e);
            return -1;
        }
    }

    /**
     * Checks whether deleted mails should be ignored.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to ignore deleted mails; otherwise <code>false</code>
     */
    public boolean isIgnoreDeletedMails(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.ignoreDeletedMails;
        } catch (Exception e) {
            LOG.error("Failed to get ignoreDeleted for user {} in context {}. Using default default {} instead.", I(userId), I(contextId), Boolean.FALSE.toString(), e);
            return false;
        }
    }

    /**
     * Checks whether shared INBOX should be visible as "shared/user" or "shared/user/INBOX"
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to explicitly include "INBOX"; otherwise <code>false</code>
     */
    public boolean includeSharedInboxExplicitly(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.includeSharedInboxExplicitly;
        } catch (Exception e) {
            LOG.error("Failed to get includeSharedInboxExplicitly for user {} in context {}. Using default default {} instead.", I(userId), I(contextId), Boolean.FALSE.toString(), e);
            return false;
        }
    }

    /**
     * Checks whether root sub-folders are allowed for primary IMAP server.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if allowed; otherwise <code>false</code>
     */
    public Boolean areRootSubfoldersAllowed(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.rootSubfoldersAllowed;
        } catch (Exception e) {
            LOG.error("Failed to get rootSubfoldersAllowed for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return null;
        }
    }

    /**
     * Checks whether to assume a namespace per user for primary IMAP server.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> to assume a namespace per user; otherwise <code>false</code>
     */
    public boolean isNamespacePerUser(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.namespacePerUser;
        } catch (Exception e) {
            LOG.error("Failed to get namespacePerUser for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return true;
        }
    }

    /**
     * Gets the threshold when to manually search with respect to umlauts.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The threshold
     */
    public int getUmlautFilterThreshold(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.umlautFilterThreshold;
        } catch (Exception e) {
            LOG.error("Failed to get umlautFilterThreshold for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return 50;
        }
    }

    /**
     * Gets max. length for a mailbox name.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The length
     */
    public int getMaxMailboxNameLength(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.maxMailboxNameLength;
        } catch (Exception e) {
            LOG.error("Failed to get maxMailboxNameLength for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return 60;
        }
    }

    /**
     * Gets the threshold when to manually search with respect to umlauts.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The threshold
     */
    public TIntSet getInvalidChars(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.invalidChars;
        } catch (Exception e) {
            LOG.error("Failed to get invalidChars for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return new TIntHashSet(0);
        }
    }

    /**
     * Whether ESORT is allowed to be utilized
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if allowed; otherwise <code>false</code>
     */
    public boolean allowESORT(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.allowESORT;
        } catch (Exception e) {
            LOG.error("Failed to get allowESORT for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return true;
        }
    }

    /**
     * Whether ESORT is allowed to be utilized
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if allowed; otherwise <code>false</code>
     */
    public boolean allowSORTDISPLAY(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.allowSORTDISPLAY;
        } catch (Exception e) {
            LOG.error("Failed to get allowSORTDISPLAY for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return false;
        }
    }

    /**
     * Whether in-app sort is supposed to be utilized if IMAP-side SORT fails with a "NO" response
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if allowed; otherwise <code>false</code>
     */
    public boolean fallbackOnFailedSORT(int userId, int contextId) {
        try {
            PrimaryIMAPProperties primaryIMAPProps = getPrimaryIMAPProps(userId, contextId);
            return primaryIMAPProps.fallbackOnFailedSORT;
        } catch (Exception e) {
            LOG.error("Failed to get fallbackOnFailedSORT for user {} in context {}. Using default default instead.", I(userId), I(contextId), e);
            return false;
        }
    }

    // -----------------------------------------------------------------------------------------------------------

    @Override
    public boolean isFastFetch() {
        return fastFetch;
    }

    @Override
    public boolean isPropagateClientIPAddress() {
        return propagateClientIPAddress;
    }

    @Override
    public boolean isEnableTls() {
        return enableTls;
    }

    @Override
    public boolean isAuditLogEnabled() {
        return auditLogEnabled;
    }

    @Override
    public boolean isDebugLogEnabled() {
        return debugLogEnabled;
    }

    @Override
    public boolean isOverwritePreLoginCapabilities() {
        return overwritePreLoginCapabilities;
    }

    @Override
    public Set<String> getPropagateHostNames() {
        return propagateHostNames;
    }

    @Override
    public String getImapAuthEnc() {
        return imapAuthEnc;
    }

    @Override
    public int getImapConnectionTimeout() {
        return imapConnectionTimeout;
    }

    @Override
    public int getImapTemporaryDown() {
        return imapTemporaryDown;
    }

    @Override
    public int getImapFailedAuthTimeout() {
        return imapFailedAuthTimeout;
    }

    @Override
    public boolean isImapSearch() {
        return imapSearch;
    }

    @Override
    public boolean forceImapSearch() {
        return forceImapSearch;
    }

    @Override
    public boolean isImapSort() {
        return imapSort;
    }

    @Override
    public int getImapTimeout() {
        return imapTimeout;
    }

    @Override
    public BoolCapVal getSupportsACLs() {
        return supportsACLs;
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public int getMaxNumConnection() {
        return maxNumConnection;
    }

    @Override
    public Map<String, Boolean> getNewACLExtMap() {
        return newACLExtMap;
    }

    @Override
    public int getMailFetchLimit() {
        return mailProperties.getMailFetchLimit();
    }

    @Override
    public boolean hideInlineImages() {
        return mailProperties.hideInlineImages();
    }

    @Override
    public boolean isAllowNestedDefaultFolderOnAltNamespace() {
        return mailProperties.isAllowNestedDefaultFolderOnAltNamespace();
    }

    @Override
    public boolean isIgnoreSubscription() {
        return mailProperties.isIgnoreSubscription();
    }

    @Override
    public boolean isSupportSubscription() {
        return mailProperties.isSupportSubscription();
    }

    @Override
    public boolean isUserFlagsEnabled() {
        return mailProperties.isUserFlagsEnabled();
    }

    @Override
    public boolean allowFolderCaches() {
        return allowFolderCaches;
    }

    @Override
    public boolean allowFetchSingleHeaders() {
        return allowFetchSingleHeaders;
    }

    @Override
    public String getSSLProtocols() {
        return sslProtocols;
    }

    @Override
    public String getSSLCipherSuites() {
        return cipherSuites;
    }

    @Override
    public boolean isAttachmentMarkerEnabled() {
        return isUserFlagsEnabled() ? enableAttachmentSearch : false;
    }

    private static int parsePositiveInt(String s) {
        try {
            int i = Integer.parseInt(s);
            return i < 0 ? -1 : i;
        } catch (NumberFormatException e) {
            LOG.trace("", e);
            return -1;
        }
    }

}
