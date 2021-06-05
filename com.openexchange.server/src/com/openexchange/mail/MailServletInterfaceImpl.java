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

package com.openexchange.mail;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.mail.utils.MailFolderUtility.prepareFullname;
import static com.openexchange.mail.utils.MailFolderUtility.prepareMailFolderParam;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.idn.IDNA;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import com.google.common.collect.ImmutableMap;
import com.openexchange.config.ConfigurationService;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.contact.ContactService;
import com.openexchange.dataretention.DataRetentionService;
import com.openexchange.dataretention.RetentionData;
import com.openexchange.exception.Category;
import com.openexchange.exception.OXException;
import com.openexchange.filemanagement.ManagedFile;
import com.openexchange.filemanagement.ManagedFileManagement;
import com.openexchange.folderstorage.cache.CacheFolderStorage;
import com.openexchange.folderstorage.virtual.osgi.Services;
import com.openexchange.group.Group;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.i18n.MailStrings;
import com.openexchange.groupware.importexport.MailImportResult;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.userconfiguration.UserConfigurationStorage;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.Collators;
import com.openexchange.java.IOs;
import com.openexchange.java.Reference;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.mail.MailFetchListenerResult.ListenerReply;
import com.openexchange.mail.api.FromAddressProvider;
import com.openexchange.mail.api.IMailFolderStorage;
import com.openexchange.mail.api.IMailFolderStorageEnhanced;
import com.openexchange.mail.api.IMailFolderStorageEnhanced2;
import com.openexchange.mail.api.IMailMessageStorage;
import com.openexchange.mail.api.IMailMessageStorageBatch;
import com.openexchange.mail.api.IMailMessageStorageBatchCopyMove;
import com.openexchange.mail.api.IMailMessageStorageExt;
import com.openexchange.mail.api.IMailMessageStorageMimeSupport;
import com.openexchange.mail.api.ISimplifiedThreadStructure;
import com.openexchange.mail.api.ISimplifiedThreadStructureEnhanced;
import com.openexchange.mail.api.MailAccess;
import com.openexchange.mail.api.MailConfig;
import com.openexchange.mail.api.crypto.CryptographicAwareMailAccessFactory;
import com.openexchange.mail.api.unified.UnifiedFullName;
import com.openexchange.mail.api.unified.UnifiedViewService;
import com.openexchange.mail.cache.MailMessageCache;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.dataobjects.MailFolder;
import com.openexchange.mail.dataobjects.MailFolderDescription;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.dataobjects.compose.ComposeType;
import com.openexchange.mail.dataobjects.compose.ComposedMailMessage;
import com.openexchange.mail.dataobjects.compose.TextBodyMailPart;
import com.openexchange.mail.event.EventPool;
import com.openexchange.mail.event.PooledEvent;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.MimeMailExceptionCode;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.mail.mime.MimeTypes;
import com.openexchange.mail.mime.QuotedInternetAddress;
import com.openexchange.mail.mime.converters.MimeMessageConverter;
import com.openexchange.mail.mime.dataobjects.MimeRawSource;
import com.openexchange.mail.mime.processing.MimeForward;
import com.openexchange.mail.mime.utils.MimeMessageUtility;
import com.openexchange.mail.mime.utils.MimeStorageUtility;
import com.openexchange.mail.parser.MailMessageParser;
import com.openexchange.mail.parser.handlers.NonInlineForwardPartHandler;
import com.openexchange.mail.permission.DefaultMailPermission;
import com.openexchange.mail.permission.MailPermission;
import com.openexchange.mail.search.ComparisonType;
import com.openexchange.mail.search.FlagTerm;
import com.openexchange.mail.search.HeaderTerm;
import com.openexchange.mail.search.ReceivedDateTerm;
import com.openexchange.mail.search.SearchTerm;
import com.openexchange.mail.search.SearchUtility;
import com.openexchange.mail.search.service.SearchTermMapper;
import com.openexchange.mail.service.EncryptedMailService;
import com.openexchange.mail.transport.MailTransport;
import com.openexchange.mail.transport.MtaStatusInfo;
import com.openexchange.mail.transport.TransportProvider;
import com.openexchange.mail.transport.TransportProviderRegistry;
import com.openexchange.mail.usersetting.UserSettingMail;
import com.openexchange.mail.usersetting.UserSettingMailStorage;
import com.openexchange.mail.utils.MailFolderUtility;
import com.openexchange.mail.utils.MessageUtility;
import com.openexchange.mail.utils.MsisdnUtility;
import com.openexchange.mail.utils.StorageUtility;
import com.openexchange.mailaccount.Attribute;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountDescription;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.UnifiedInboxManagement;
import com.openexchange.mailaccount.internal.RdbMailAccountStorage;
import com.openexchange.net.HostList;
import com.openexchange.push.PushEventConstants;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.spamhandler.SpamHandlerRegistry;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.threadpool.behavior.CallerRunsBehavior;
import com.openexchange.tools.TimeZoneUtils;
import com.openexchange.tools.iterator.ArrayIterator;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIteratorAdapter;
import com.openexchange.tools.iterator.SearchIteratorDelegator;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.sql.SearchStrings;
import com.openexchange.tools.stream.UnsynchronizedByteArrayOutputStream;
import com.openexchange.user.User;
import com.openexchange.user.UserService;
import com.sun.mail.smtp.SMTPSendFailedException;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedConsumer;

/**
 * {@link MailServletInterfaceImpl} - The mail servlet interface implementation.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
final class MailServletInterfaceImpl extends MailServletInterface {

    /** The logger constant */
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MailServletInterfaceImpl.class);

    private static final String MESSAGE_ID = "Message-ID";

    static final MailField[] FIELDS_FULL = new MailField[] { MailField.FULL };

    static final MailField[] FIELDS_ID_INFO = new MailField[] { MailField.ID, MailField.FOLDER_ID };

    static final MailField[] FIELDS_HEADERS = { MailField.ID, MailField.HEADERS };

    static final MailField[] FIELDS_TEXT_PREVIEW = { MailField.ID, MailField.TEXT_PREVIEW };

    private static final String LAST_SEND_TIME = "com.openexchange.mail.lastSendTimestamp";

    private static final String INBOX_ID = "INBOX";

    /*-
     * ++++++++++++++ Fields ++++++++++++++
     */

    private final Context ctx;
    final int contextId;
    private boolean init;
    private MailConfig mailConfig;
    private MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> mailAccess;
    int accountId;
    final Session session;
    private final UserSettingMail usm;
    private Locale locale;
    private User user;
    final Collection<OXException> warnings;
    private final ArrayList<MailImportResult> mailImportResults;
    private final MailFields folderAndId;
    private final boolean checkParameters;
    private final boolean doDecryption;
    private final String cryptoAuthentication;
    private final boolean debug;

    /**
     * Initializes a new {@link MailServletInterfaceImpl}.
     *
     * @param session The session
     * @param doDecryption True in order to perform email decryption, false to use the raw messages.
     * @param cryptoAuthentication Authentication for decrypting emails.
     * @param debug The debug flag
     * @throws OXException If user has no mail access or properties cannot be successfully loaded
     */
    MailServletInterfaceImpl(Session session, boolean doDecryption, String cryptoAuthentication, boolean debug) throws OXException {
        super();
        warnings = new ArrayList<>(2);
        mailImportResults = new ArrayList<>();
        if (session instanceof ServerSession) {
            ServerSession serverSession = (ServerSession) session;
            ctx = serverSession.getContext();
            usm = serverSession.getUserSettingMail();
            if (!serverSession.getUserPermissionBits().hasWebMail()) {
                throw MailExceptionCode.NO_MAIL_ACCESS.create();
            }
            user = serverSession.getUser();
        } else {
            ctx = ContextStorage.getInstance().getContext(session.getContextId());
            usm = UserSettingMailStorage.getInstance().getUserSettingMail(session.getUserId(), ctx);
            if (!UserConfigurationStorage.getInstance().getUserConfiguration(session.getUserId(), ctx).hasWebMail()) {
                throw MailExceptionCode.NO_MAIL_ACCESS.create();
            }
        }
        this.session = session;
        contextId = session.getContextId();
        folderAndId = new MailFields(MailField.ID, MailField.FOLDER_ID);
        checkParameters = false;
        this.doDecryption = doDecryption;
        this.cryptoAuthentication = cryptoAuthentication;
        this.debug = debug;
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public char getSeparator(int acccountId) throws OXException {
        return getSeparator(acccountId, session).charValue();
    }

    private Character getSeparator(int accountId, Session session) throws OXException {
        final MailSessionCache sessionCache = MailSessionCache.getInstance(session);
        Character sep = (Character) sessionCache.getParameter(accountId, MailSessionParameterNames.getParamSeparator());
        if (null == sep) {
            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> ma = null;
            try {
                ma = MailAccess.getInstance(session, accountId);
                ma.connect(false, debug);
                sep = Character.valueOf(ma.getFolderStorage().getFolder(INBOX_ID).getSeparator());
                sessionCache.putParameter(accountId, MailSessionParameterNames.getParamSeparator(), sep);
            } finally {
                if (null != ma) {
                    ma.close(true);
                }
            }
        }
        return sep;
    }

    User getUser() throws OXException {
        if (null == user) {
            user = UserStorage.getInstance().getUser(session.getUserId(), session.getContextId());
        }
        return user;
    }

    Locale getUserLocale() {
        if (null == locale) {
            if (session instanceof ServerSession) {
                locale = ((ServerSession) session).getUser().getLocale();
            } else {
                UserService userService = ServerServiceRegistry.getInstance().getService(UserService.class);
                if (null == userService) {
                    return Locale.ENGLISH;
                }
                try {
                    locale = userService.getUser(session.getUserId(), ctx).getLocale();
                } catch (OXException e) {
                    LOG.warn("", e);
                    return Locale.ENGLISH;
                }
            }
        }
        return locale;
    }

    @Override
    public Collection<OXException> getWarnings() {
        return Collections.unmodifiableCollection(warnings);
    }

    /**
     * The fields containing only the mail identifier.
     */
    static final MailField[] FIELDS_ID = new MailField[] { MailField.ID };

    @Override
    public boolean expungeFolder(String folder, boolean hardDelete) throws OXException {
        Callable<Boolean> expungeFolderCallable = new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                FullnameArgument fullnameArgument = prepareMailFolderParam(folder);
                int localAccountId = fullnameArgument.getAccountId();
                initConnection(localAccountId);
                String fullName = fullnameArgument.getFullname();
                IMailFolderStorage folderStorage = getMailAccess().getFolderStorage();

                IMailFolderStorageEnhanced storageEnhanced = folderStorage.supports(IMailFolderStorageEnhanced.class);
                if (null != storageEnhanced) {
                    storageEnhanced.expungeFolder(fullName, hardDelete);
                } else {
                    IMailMessageStorage messageStorage = getMailAccess().getMessageStorage();
                    MailMessage[] messages = messageStorage.searchMessages(fullName, IndexRange.NULL, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new FlagTerm(MailMessage.FLAG_DELETED, true), FIELDS_ID);
                    List<String> mailIds = new LinkedList<>();
                    for (MailMessage mailMessage : messages) {
                        if (null != mailMessage) {
                            mailIds.add(mailMessage.getMailId());
                        }
                    }
                    if (hardDelete) {
                        messageStorage.deleteMessages(fullName, mailIds.toArray(new String[mailIds.size()]), true);
                    } else {
                        String trashFolder = folderStorage.getTrashFolder();
                        if (fullName.equals(trashFolder)) {
                            // Also perform hard-delete when compacting trash folder
                            messageStorage.deleteMessages(fullName, mailIds.toArray(new String[mailIds.size()]), true);
                        } else {
                            messageStorage.moveMessages(fullName, trashFolder, mailIds.toArray(new String[mailIds.size()]), true);
                        }
                    }
                }
                postEvent(localAccountId, fullName, true);
                String trashFullname = prepareMailFolderParam(getTrashFolder(localAccountId)).getFullname();
                if (!hardDelete) {
                    postEvent(localAccountId, trashFullname, true);
                }
                return Boolean.TRUE;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(expungeFolderCallable).booleanValue();
    }

    @Override
    public boolean clearFolder(String folder) throws OXException {
        Context ctx = this.ctx;
        Callable<Boolean> clearFolderCallable = new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                FullnameArgument fullnameArgument = prepareMailFolderParam(folder);
                int localAccountId = fullnameArgument.getAccountId();
                initConnection(localAccountId);
                String fullName = fullnameArgument.getFullname();
                /*
                 * Only backup if no hard-delete is set in user's mail configuration and fullName does not denote trash (sub)folder
                 */
                boolean backup = (!UserSettingMailStorage.getInstance().getUserSettingMail(session.getUserId(), ctx).isHardDeleteMsgs() && !(fullName.startsWith(getMailAccess().getFolderStorage().getTrashFolder())));
                getMailAccess().getFolderStorage().clearFolder(fullName, !backup);
                postEvent(localAccountId, fullName, true);
                String trashFullname = prepareMailFolderParam(getTrashFolder(localAccountId)).getFullname();
                if (backup) {
                    postEvent(localAccountId, trashFullname, true);
                }
                try {
                    /*
                     * Update message cache
                     */
                    MailMessageCache.getInstance().removeFolderMessages(localAccountId, fullName, session.getUserId(), contextId);
                } catch (OXException e) {
                    LOG.error("", e);
                }
                if (fullName.startsWith(trashFullname)) {
                    // Special handling
                    MailFolder[] subf = getMailAccess().getFolderStorage().getSubfolders(fullName, true);
                    for (MailFolder element : subf) {
                        String subFullname = element.getFullname();
                        getMailAccess().getFolderStorage().deleteFolder(subFullname, true);
                        postEvent(localAccountId, subFullname, false);
                    }
                    postEvent(localAccountId, trashFullname, false);
                }
                return Boolean.TRUE;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(clearFolderCallable).booleanValue();
    }

    @Override
    public boolean clearFolder(String folder, boolean hardDelete) throws OXException {
        Context ctx = this.ctx;
        Callable<Boolean> clearFolderCallable = new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                FullnameArgument fullnameArgument = prepareMailFolderParam(folder);
                int localAccountId = fullnameArgument.getAccountId();
                initConnection(localAccountId);
                String fullName = fullnameArgument.getFullname();
                /*
                 * Only backup if no hard-delete is set in user's mail configuration and fullName does not denote trash (sub)folder
                 */
                boolean backup;
                if (hardDelete) {
                    backup = false;
                } else {
                    backup = (!UserSettingMailStorage.getInstance().getUserSettingMail(session.getUserId(), ctx).isHardDeleteMsgs() && !(fullName.startsWith(getMailAccess().getFolderStorage().getTrashFolder())));
                }
                getMailAccess().getFolderStorage().clearFolder(fullName, !backup);
                postEvent(localAccountId, fullName, true);
                String trashFullname = prepareMailFolderParam(getTrashFolder(localAccountId)).getFullname();
                if (backup) {
                    postEvent(localAccountId, trashFullname, true);
                }
                try {
                    /*
                     * Update message cache
                     */
                    MailMessageCache.getInstance().removeFolderMessages(localAccountId, fullName, session.getUserId(), contextId);
                } catch (OXException e) {
                    LOG.error("", e);
                }
                if (fullName.startsWith(trashFullname)) {
                    // Special handling
                    MailFolder[] subf = getMailAccess().getFolderStorage().getSubfolders(fullName, true);
                    for (MailFolder element : subf) {
                        String subFullname = element.getFullname();
                        getMailAccess().getFolderStorage().deleteFolder(subFullname, true);
                        postEvent(localAccountId, subFullname, false);
                    }
                    postEvent(localAccountId, trashFullname, false);
                }
                return Boolean.TRUE;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(clearFolderCallable).booleanValue();
    }

    @Override
    public void close(boolean putIntoCache) throws OXException {
        try {
            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> mailAccess = this.mailAccess;
            if (mailAccess != null) {
                this.mailAccess = null;
                mailAccess.close(putIntoCache);
            }
        } finally {
            init = false;
        }
    }

    private static final int SPAM_HAM = -1;

    private static final int SPAM_NOOP = 0;

    private static final int SPAM_SPAM = 1;

    @Override
    public String[] copyMessages(String sourceFolder, String destFolder, String[] msgUIDs, boolean move) throws OXException {
        UserSettingMail usm = this.usm;
        Callable<String[]> copyMessagesCallable = new Callable<String[]>() {

            @Override
            public String[] call() throws Exception {
                FullnameArgument source = prepareMailFolderParam(sourceFolder);
                FullnameArgument dest = prepareMailFolderParam(destFolder);
                String sourceFullname = source.getFullname();
                String destFullname = dest.getFullname();
                int sourceAccountId = source.getAccountId();
                initConnection(sourceAccountId);
                int destAccountId = dest.getAccountId();
                if (sourceAccountId == destAccountId) {
                    IMailMessageStorage messageStorage = getMailAccess().getMessageStorage();
                    MailMessage[] flagInfo = null;
                    if (move) {
                        /*
                         * Check for spam action; meaning a move/copy from/to spam folder
                         */
                        String spamFullname = getMailAccess().getFolderStorage().getSpamFolder();
                        String trashFullname = getMailAccess().getFolderStorage().getTrashFolder();
                        int spamAction;
                        if (usm.isSpamEnabled() && spamFullname != null && trashFullname != null) {
                            if (spamFullname.equals(sourceFullname)) {
                                spamAction = trashFullname.equals(destFullname) ? SPAM_NOOP : SPAM_HAM;
                            } else {
                                spamAction = (spamFullname.equals(destFullname) ? SPAM_SPAM : SPAM_NOOP);
                            }
                        } else {
                            spamAction = SPAM_NOOP;
                        }
                        if (spamAction != SPAM_NOOP) {
                            if (spamAction == SPAM_SPAM) {
                                flagInfo = messageStorage.getMessages(sourceFullname, msgUIDs, new MailField[] { MailField.FLAGS });
                                /*
                                 * Handle spam
                                 */
                                SpamHandlerRegistry.getSpamHandlerBySession(session, accountId).handleSpam(accountId, sourceFullname, msgUIDs, false, session);
                            } else {
                                flagInfo = messageStorage.getMessages(sourceFullname, msgUIDs, new MailField[] { MailField.FLAGS });
                                /*
                                 * Handle ham.
                                 */
                                SpamHandlerRegistry.getSpamHandlerBySession(session, accountId).handleHam(accountId, sourceFullname, msgUIDs, false, session);
                            }
                        }
                    }
                    String[] maildIds;
                    if (move) {
                        maildIds = messageStorage.moveMessages(sourceFullname, destFullname, msgUIDs, false);
                        postEvent(sourceAccountId, sourceFullname, true, true);
                    } else {
                        maildIds = messageStorage.copyMessages(sourceFullname, destFullname, msgUIDs, false);
                    }
                    /*
                     * Restore \Seen flags
                     */
                    if (null != flagInfo) {
                        List<String> list = new LinkedList<>();
                        for (int i = 0; i < maildIds.length; i++) {
                            MailMessage mailMessage = flagInfo[i];
                            if (null != mailMessage && !mailMessage.isSeen()) {
                                list.add(maildIds[i]);
                            }
                        }
                        messageStorage.updateMessageFlags(destFullname, list.toArray(new String[list.size()]), MailMessage.FLAG_SEEN, false);
                    }
                    postEvent(sourceAccountId, destFullname, true, true);
                    try {
                        /*
                         * Update message cache
                         */
                        if (move) {
                            MailMessageCache.getInstance().removeFolderMessages(sourceAccountId, sourceFullname, session.getUserId(), contextId);
                        }
                        MailMessageCache.getInstance().removeFolderMessages(destAccountId, destFullname, session.getUserId(), contextId);
                    } catch (OXException e) {
                        LOG.error("", e);
                    }
                    return maildIds;
                }
                /*
                 * Differing accounts...
                 */
                MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> destAccess = initMailAccess(destAccountId);
                try {
                    // Chunk wise copy
                    int chunkSize;
                    {
                        ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                        chunkSize = null == service ? 50 : service.getIntProperty("com.openexchange.mail.externalChunkSize", 50);
                    }
                    // Iterate chunks
                    int length = msgUIDs.length;
                    List<String> retval = new LinkedList<>();
                    Map<String, Integer> flagsMap = null;
                    for (int start = 0; start < length;) {
                        int end = start + chunkSize;
                        String[] ids;
                        {
                            int len;
                            if (end > length) {
                                end = length;
                                len = end - start;
                            } else {
                                len = chunkSize;
                            }
                            ids = new String[len];
                            System.arraycopy(msgUIDs, start, ids, 0, len);
                        }
                        // Fetch messages from source folder
                        MailMessage[] messages = new MailMessage[ids.length];
                        for (int j = 0; j < ids.length; j++) {
                            String mailId = ids[j];
                            messages[j] = null == mailId ? null : getMailAccess().getMessageStorage().getMessage(sourceFullname, mailId, false);
                        }
                        // Create mapping for flags
                        if (null == flagsMap) {
                            flagsMap = new HashMap<>(messages.length);
                        } else {
                            flagsMap.clear();
                        }
                        for (int i = 0; i < messages.length; i++) {
                            MailMessage message = messages[i];
                            if (null != message) {
                                int systemFlags = message.getFlags();
                                flagsMap.put(message.getMailId(), Integer.valueOf(systemFlags));
                            }
                        }
                        // Append them to destination folder
                        String[] destIds = destAccess.getMessageStorage().appendMessages(destFullname, messages);
                        if (null != destIds && destIds.length > 0) {
                            // Create ID mapping
                            Map<String, String> idMap = new HashMap<>(destIds.length);
                            for (int i = 0; i < messages.length; i++) {
                                MailMessage message = messages[i];
                                if (null != message && null != destIds[i]) {
                                    idMap.put(destIds[i], message.getMailId());
                                }
                            }
                            // Delete source messages if a move shall be performed
                            if (move) {
                                getMailAccess().getMessageStorage().deleteMessages(sourceFullname, messages2ids(messages), true);
                                postEvent(sourceAccountId, sourceFullname, true, true);
                            }
                            // Restore flags
                            {
                                for (Map.Entry<String, String> entry : idMap.entrySet()) {
                                    String sourceId = entry.getValue();
                                    Integer iFlags = flagsMap.get(sourceId);
                                    if (null != iFlags) {
                                        String[] mailIds = new String[] { entry.getKey() };
                                        if (iFlags.intValue() > 0) {
                                            destAccess.getMessageStorage().updateMessageFlags(destFullname, mailIds, iFlags.intValue(), true);
                                        }
                                        if ((iFlags.intValue() & MailMessage.FLAG_SEEN) == 0) {
                                            destAccess.getMessageStorage().updateMessageFlags(destFullname, mailIds, MailMessage.FLAG_SEEN, false);
                                        }
                                    }
                                }
                            }
                            postEvent(destAccountId, destFullname, true, true);
                            try {
                                if (move) {
                                    /*
                                     * Update message cache
                                     */
                                    MailMessageCache.getInstance().removeFolderMessages(sourceAccountId, sourceFullname, session.getUserId(), contextId);
                                }
                                MailMessageCache.getInstance().removeFolderMessages(destAccountId, destFullname, session.getUserId(), contextId);
                            } catch (OXException e) {
                                LOG.error("", e);
                            }
                            // Prepare for next iteration
                            retval.addAll(Arrays.asList(destIds));
                        }
                        start = end;
                    }
                    // Return destination identifiers
                    return retval.toArray(new String[retval.size()]);
                } finally {
                    destAccess.close(true);
                }
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(copyMessagesCallable);
    }

    @Override
    public void copyAllMessages(String sourceFolder, String destFolder, boolean move) throws OXException {
        UserSettingMail usm = this.usm;
        Callable<Void> copyAllMessagesCallable = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                FullnameArgument source = prepareMailFolderParam(sourceFolder);
                FullnameArgument dest = prepareMailFolderParam(destFolder);
                String sourceFullname = source.getFullname();
                String destFullname = dest.getFullname();
                int sourceAccountId = source.getAccountId();
                initConnection(sourceAccountId);
                int destAccountId = dest.getAccountId();
                if (sourceAccountId == destAccountId) {
                    IMailMessageStorage messageStorage = getMailAccess().getMessageStorage();
                    String[] mailIds = null;
                    MailMessage[] flagInfo = null;
                    if (move) {
                        // Check for spam action; meaning a move/copy from/to spam folder
                        String spamFullname = getMailAccess().getFolderStorage().getSpamFolder();
                        String trashFullname = getMailAccess().getFolderStorage().getTrashFolder();
                        int spamAction;
                        if (usm.isSpamEnabled()) {
                            if (spamFullname.equals(sourceFullname)) {
                                spamAction = trashFullname.equals(destFullname) ? SPAM_NOOP : SPAM_HAM;
                            } else {
                                spamAction = (spamFullname.equals(destFullname) ? SPAM_SPAM : SPAM_NOOP);
                            }
                        } else {
                            spamAction = SPAM_NOOP;
                        }
                        if (spamAction != SPAM_NOOP) {
                            if (spamAction == SPAM_SPAM) {
                                {
                                    MailMessage[] allIds = messageStorage.getAllMessages(sourceFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new MailField[] { MailField.ID });
                                    mailIds = new String[allIds.length];
                                    for (int i = allIds.length; i-- > 0;) {
                                        MailMessage idm = allIds[i];
                                        mailIds[i] = null == idm ? null : allIds[i].getMailId();
                                    }
                                }
                                flagInfo = messageStorage.getMessages(sourceFullname, mailIds, new MailField[] { MailField.FLAGS });
                                SpamHandlerRegistry.getSpamHandlerBySession(session, accountId).handleSpam(accountId, sourceFullname, mailIds, false, session);
                            } else {
                                {
                                    MailMessage[] allIds = messageStorage.getAllMessages(sourceFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new MailField[] { MailField.ID });
                                    mailIds = new String[allIds.length];
                                    for (int i = allIds.length; i-- > 0;) {
                                        MailMessage idm = allIds[i];
                                        mailIds[i] = null == idm ? null : allIds[i].getMailId();
                                    }
                                }
                                flagInfo = messageStorage.getMessages(sourceFullname, mailIds, new MailField[] { MailField.FLAGS });
                                SpamHandlerRegistry.getSpamHandlerBySession(session, accountId).handleHam(accountId, sourceFullname, mailIds, false, session);
                            }
                        }
                    }

                    IMailMessageStorageBatchCopyMove batchCopyMove = messageStorage.supports(IMailMessageStorageBatchCopyMove.class);
                    if (null != batchCopyMove) {
                        if (move) {
                            batchCopyMove.moveMessages(sourceFullname, destFullname);
                            postEvent(sourceAccountId, sourceFullname, true, true);
                        } else {
                            batchCopyMove.copyMessages(sourceFullname, destFullname);
                        }
                    } else {
                        if (null == mailIds) {
                            MailMessage[] allIds = messageStorage.getAllMessages(sourceFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new MailField[] { MailField.ID });
                            mailIds = new String[allIds.length];
                            for (int i = allIds.length; i-- > 0;) {
                                MailMessage idm = allIds[i];
                                mailIds[i] = null == idm ? null : allIds[i].getMailId();
                            }
                        }
                        if (move) {
                            messageStorage.moveMessages(sourceFullname, destFullname, mailIds, true);
                            postEvent(sourceAccountId, sourceFullname, true, true);
                        } else {
                            messageStorage.copyMessages(sourceFullname, destFullname, mailIds, true);
                        }
                    }

                    // Restore \Seen flags
                    if (null != flagInfo) {
                        if (null == mailIds) {
                            MailMessage[] allIds = messageStorage.getAllMessages(sourceFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new MailField[] { MailField.ID });
                            mailIds = new String[allIds.length];
                            for (int i = allIds.length; i-- > 0;) {
                                MailMessage idm = allIds[i];
                                mailIds[i] = null == idm ? null : allIds[i].getMailId();
                            }
                        }

                        List<String> list = new LinkedList<>();
                        for (int i = 0; i < mailIds.length; i++) {
                            MailMessage mailMessage = flagInfo[i];
                            if (null != mailMessage && !mailMessage.isSeen()) {
                                list.add(mailIds[i]);
                            }
                        }
                        messageStorage.updateMessageFlags(destFullname, list.toArray(new String[list.size()]), MailMessage.FLAG_SEEN, false);
                    }

                    postEvent(sourceAccountId, destFullname, true, true);

                    // Invalidate message cache
                    try {
                        if (move) {
                            MailMessageCache.getInstance().removeFolderMessages(sourceAccountId, sourceFullname, session.getUserId(), contextId);
                        }
                        MailMessageCache.getInstance().removeFolderMessages(destAccountId, destFullname, session.getUserId(), contextId);
                    } catch (OXException e) {
                        LOG.error("", e);
                    }
                    return null;
                }

                // Differing accounts...
                MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> destAccess = initMailAccess(destAccountId);
                try {
                    String[] mailIds = null;
                    MailMessage[] flagInfo = null;
                    if (move) {
                        /*
                         * Check for spam action; meaning a move/copy from/to spam folder
                         */
                        int spamActionSource = SPAM_NOOP;
                        int spamActionDest = SPAM_NOOP;
                        if (usm.isSpamEnabled()) {
                            if (sourceFullname.equals(getMailAccess().getFolderStorage().getSpamFolder())) {
                                spamActionSource = SPAM_HAM;
                            }
                            if (destFullname.equals(destAccess.getFolderStorage().getSpamFolder())) {
                                spamActionDest = SPAM_SPAM;
                            }
                        }
                        if (SPAM_HAM == spamActionSource) {
                            {
                                MailMessage[] allIds = getMailAccess().getMessageStorage().getAllMessages(sourceFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new MailField[] { MailField.ID });
                                mailIds = new String[allIds.length];
                                for (int i = allIds.length; i-- > 0;) {
                                    MailMessage idm = allIds[i];
                                    mailIds[i] = null == idm ? null : allIds[i].getMailId();
                                }
                            }
                            flagInfo = getMailAccess().getMessageStorage().getMessages(sourceFullname, mailIds, new MailField[] { MailField.FLAGS });
                            /*
                             * Handle ham.
                             */
                            SpamHandlerRegistry.getSpamHandlerBySession(session, accountId).handleHam(accountId, sourceFullname, mailIds, false, session);
                        }
                        if (SPAM_SPAM == spamActionDest) {
                            {
                                MailMessage[] allIds = getMailAccess().getMessageStorage().getAllMessages(sourceFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new MailField[] { MailField.ID });
                                mailIds = new String[allIds.length];
                                for (int i = allIds.length; i-- > 0;) {
                                    MailMessage idm = allIds[i];
                                    mailIds[i] = null == idm ? null : allIds[i].getMailId();
                                }
                            }
                            flagInfo = getMailAccess().getMessageStorage().getMessages(sourceFullname, mailIds, new MailField[] { MailField.FLAGS });
                            /*
                             * Handle spam
                             */
                            SpamHandlerRegistry.getSpamHandlerBySession(session, accountId).handleSpam(accountId, sourceFullname, mailIds, false, session);
                        }
                    }

                    // Chunk wise copy
                    int chunkSize;
                    {
                        ConfigurationService service = ServerServiceRegistry.getInstance().getService(ConfigurationService.class);
                        chunkSize = null == service ? 50 : service.getIntProperty("com.openexchange.mail.externalChunkSize", 50);
                    }

                    // Iterate chunks
                    if (null == mailIds) {
                        MailMessage[] allIds = getMailAccess().getMessageStorage().getAllMessages(sourceFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new MailField[] { MailField.ID });
                        mailIds = new String[allIds.length];
                        for (int i = allIds.length; i-- > 0;) {
                            MailMessage idm = allIds[i];
                            mailIds[i] = null == idm ? null : allIds[i].getMailId();
                        }
                    }

                    int total = mailIds.length;
                    List<String> retval = new LinkedList<>();
                    for (int start = 0; start < total;) {
                        int end = start + chunkSize;
                        String[] ids;
                        {
                            int len;
                            if (end > total) {
                                end = total;
                                len = end - start;
                            } else {
                                len = chunkSize;
                            }
                            ids = new String[len];
                            System.arraycopy(mailIds, start, ids, 0, len);
                        }

                        // Fetch messages from source folder
                        MailMessage[] messages = getMailAccess().getMessageStorage().getMessages(sourceFullname, ids, FIELDS_FULL);

                        // Append them to destination folder
                        String[] destIds = destAccess.getMessageStorage().appendMessages(destFullname, messages);
                        if (null == destIds || 0 == destIds.length) {
                            return null;
                        }

                        // Delete source messages if a move shall be performed
                        if (move) {
                            getMailAccess().getMessageStorage().deleteMessages(sourceFullname, messages2ids(messages), true);
                            postEvent(sourceAccountId, sourceFullname, true, true);
                        }

                        // Restore \Seen flags
                        if (null != flagInfo) {
                            List<String> list = new LinkedList<>();
                            for (int i = 0; i < destIds.length; i++) {
                                MailMessage mailMessage = flagInfo[i];
                                if (null != mailMessage && !mailMessage.isSeen()) {
                                    list.add(destIds[i]);
                                }
                            }
                            destAccess.getMessageStorage().updateMessageFlags(destFullname, list.toArray(new String[list.size()]), MailMessage.FLAG_SEEN, false);
                        }
                        postEvent(destAccountId, destFullname, true, true);

                        // Invalidate message cache
                        try {
                            if (move) {
                                MailMessageCache.getInstance().removeFolderMessages(sourceAccountId, sourceFullname, session.getUserId(), contextId);
                            }
                            MailMessageCache.getInstance().removeFolderMessages(destAccountId, destFullname, session.getUserId(), contextId);
                        } catch (OXException e) {
                            LOG.error("", e);
                        }
                        // Prepare for next iteration
                        retval.addAll(Arrays.asList(destIds));
                        start = end;
                    }
                } finally {
                    destAccess.close(true);
                }

                return null;
            }
        };

        executeWithRetryOnUnexpectedConnectionClosure(copyAllMessagesCallable);
    }

    @Override
    public String deleteFolder(String folder) throws OXException {
        Callable<String> deleteFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                int localAccountId = argument.getAccountId();
                initConnection(localAccountId);
                String fullName = argument.getFullname();
                /*
                 * Only backup if fullName does not denote trash (sub)folder
                 */
                IMailFolderStorage folderStorage = getMailAccess().getFolderStorage();
                String trashFullname = folderStorage.getTrashFolder();
                boolean hardDelete = fullName.startsWith(trashFullname);
                /*
                 * Remember subfolder tree
                 */
                Map<String, Map<?, ?>> subfolders = subfolders(fullName);
                String retval = prepareFullname(localAccountId, folderStorage.deleteFolder(fullName, hardDelete));
                postEvent(localAccountId, fullName, false, true, false);
                try {
                    /*
                     * Update message cache
                     */
                    MailMessageCache.getInstance().removeFolderMessages(localAccountId, fullName, session.getUserId(), contextId);
                } catch (OXException e) {
                    LOG.error("", e);
                }
                if (!hardDelete) {
                    // New folder in trash folder
                    postEventRemote(localAccountId, trashFullname, false);
                }
                postEvent4Subfolders(localAccountId, subfolders);
                return retval;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(deleteFolderCallable);
    }

    void postEvent4Subfolders(int accountId, Map<String, Map<?, ?>> subfolders) {
        int size = subfolders.size();
        Iterator<Entry<String, Map<?, ?>>> iter = subfolders.entrySet().iterator();
        for (int i = 0; i < size; i++) {
            Entry<String, Map<?, ?>> entry = iter.next();
            @SuppressWarnings("unchecked") Map<String, Map<?, ?>> m = (Map<String, Map<?, ?>>) entry.getValue();
            if (!m.isEmpty()) {
                postEvent4Subfolders(accountId, m);
            }
            postEventRemote(accountId, entry.getKey(), false);
        }
    }

    Map<String, Map<?, ?>> subfolders(String fullName) throws OXException {
        Map<String, Map<?, ?>> m = new HashMap<>();
        subfoldersRecursively(fullName, m);
        return m;
    }

    private void subfoldersRecursively(String parent, Map<String, Map<?, ?>> m) throws OXException {
        MailFolder[] mailFolders = mailAccess.getFolderStorage().getSubfolders(parent, true);
        if (null == mailFolders || 0 == mailFolders.length) {
            Map<String, Map<?, ?>> emptyMap = Collections.emptyMap();
            m.put(parent, emptyMap);
        } else {
            Map<String, Map<?, ?>> subMap = new HashMap<>();
            int size = mailFolders.length;
            for (int i = 0; i < size; i++) {
                String fullName = mailFolders[i].getFullname();
                subfoldersRecursively(fullName, subMap);
            }
            m.put(parent, subMap);
        }
    }

    @Override
    public boolean deleteMessages(String folder, String[] msgUIDs, boolean hardDelete) throws OXException {
        Context ctx = this.ctx;
        Callable<Boolean> deleteMessagesCallable = new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                int localAccountId = argument.getAccountId();
                initConnection(localAccountId);
                String fullName = argument.getFullname();
                /*
                 * Hard-delete if hard-delete is set in user's mail configuration or fullName denotes trash (sub)folder
                 */
                String trashFullname = getMailAccess().getFolderStorage().getTrashFolder();
                boolean hd = (hardDelete || UserSettingMailStorage.getInstance().getUserSettingMail(session.getUserId(), ctx).isHardDeleteMsgs() || (null != trashFullname && fullName.startsWith(trashFullname)));
                getMailAccess().getMessageStorage().deleteMessages(fullName, msgUIDs, hd);
                try {
                    /*
                     * Update message cache
                     */
                    MailMessageCache.getInstance().removeFolderMessages(localAccountId, fullName, session.getUserId(), contextId);
                } catch (OXException e) {
                    LOG.error("", e);
                }
                postEvent(localAccountId, fullName, true, true, false);
                if (!hd) {
                    postEvent(localAccountId, trashFullname, true, true, false);
                }
                return Boolean.TRUE;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(deleteMessagesCallable).booleanValue();
    }

    @Override
    public int[] getAllMessageCount(String folder) throws OXException {
        Callable<int[]> getAllMessageCountCallable = new Callable<int[]>() {

            @Override
            public int[] call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                IMailFolderStorage folderStorage = getMailAccess().getFolderStorage();
                MailFolder f = folderStorage.getFolder(fullName);

                IMailFolderStorageEnhanced2 storageEnhanced2 = folderStorage.supports(IMailFolderStorageEnhanced2.class);
                if (null != storageEnhanced2) {
                    int[] totalAndUnread = storageEnhanced2.getTotalAndUnreadCounter(fullName);
                    int newCounter = storageEnhanced2.getNewCounter(fullName);
                    return new int[] { totalAndUnread[0], newCounter, totalAndUnread[1], f.getDeletedMessageCount() };
                }

                IMailFolderStorageEnhanced storageEnhanced = folderStorage.supports(IMailFolderStorageEnhanced.class);
                if (null != storageEnhanced) {
                    int totalCounter = storageEnhanced.getTotalCounter(fullName);
                    int unreadCounter = storageEnhanced.getUnreadCounter(fullName);
                    int newCounter = storageEnhanced.getNewCounter(fullName);
                    return new int[] { totalCounter, newCounter, unreadCounter, f.getDeletedMessageCount() };
                }

                int totalCounter = getMailAccess().getMessageStorage().searchMessages(fullName, IndexRange.NULL, MailSortField.RECEIVED_DATE, OrderDirection.ASC, null, FIELDS_ID).length;
                int unreadCounter = getMailAccess().getMessageStorage().getUnreadMessages(fullName, MailSortField.RECEIVED_DATE, OrderDirection.DESC, FIELDS_ID, -1).length;
                return new int[] { totalCounter, f.getNewMessageCount(), unreadCounter, f.getDeletedMessageCount() };
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getAllMessageCountCallable);
    }

    @Override
    public SearchIterator<MailMessage> getAllMessages(String folder, int sortCol, int order, int[] fields, String[] headerFields, int[] fromToIndices, boolean supportsContinuation) throws OXException {
        return getMessages(folder, fromToIndices, sortCol, order, null, null, false, fields, headerFields, supportsContinuation);
    }

    @Override
    public List<List<MailMessage>> getAllSimpleThreadStructuredMessages(String folder,
                                                                        boolean includeSent,
                                                                        boolean cache,
                                                                        int sortCol,
                                                                        int order,
                                                                        int[] fields,
                                                                        String[] headerFields2,
                                                                        int[] fromToIndices,
                                                                        final long max,
                                                                        SearchTerm<?> searchTerm) throws OXException {
        Callable<List<List<MailMessage>>> getAllSimpleThreadStructuredMessagesCallable = new Callable<List<List<MailMessage>>>() {

            @Override
            public List<List<MailMessage>> call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                int localAccountId = argument.getAccountId();
                initConnection(localAccountId);
                String fullName = argument.getFullname();
                boolean mergeWithSent = includeSent && !getMailAccess().getFolderStorage().getSentFolder().equals(fullName);
                MailFields mailFields = new MailFields(MailField.getFields(fields));
                mailFields.add(MailField.FOLDER_ID);
                mailFields.add(MailField.toField(MailListField.getField(sortCol)));
                MailSortField sortField = MailSortField.getField(sortCol);
                OrderDirection orderDir = OrderDirection.getOrderDirection(order);
                String[] headerFields = headerFields2;
                checkFieldsForColorCheck(mailFields);

                MailFetchArguments fetchArguments = MailFetchArguments.builder(argument, mailFields.toArray(), headerFields).setSearchTerm(searchTerm).setSortOptions(sortField, orderDir).build();
                Map<String, Object> fetchListenerState = new HashMap<>(4);
                MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, getMailAccess(), fetchListenerState);
                boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);
                if (notEmptyChain) {
                    MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, getMailAccess(), fetchListenerState);
                    if (attributation.isApplicable()) {
                        mailFields = new MailFields(attributation.getFields());
                        headerFields = attributation.getHeaderNames();
                    }
                }

                // Check message storage
                final IMailMessageStorage messageStorage = getMailAccess().getMessageStorage();

                ISimplifiedThreadStructureEnhanced stse = messageStorage.supports(ISimplifiedThreadStructureEnhanced.class);
                if (null != stse) {
                    try {
                        List<List<MailMessage>> result = stse.getThreadSortedMessages(fullName, mergeWithSent, cache, null == fromToIndices ? IndexRange.NULL : new IndexRange(fromToIndices[0], fromToIndices[1]), max, sortField, orderDir, mailFields.toArray(), headerFields, searchTerm);

                        if (!getMailAccess().getWarnings().isEmpty()) {
                            warnings.addAll(getMailAccess().getWarnings());
                        }

                        /*
                         * Trigger fetch listener processing
                         */
                        if (notEmptyChain) {
                            List<MailMessage> l = new ArrayList<>(result.size());
                            for (List<MailMessage> mails : result) {
                                for (MailMessage mail : mails) {
                                    l.add(mail);
                                }
                            }
                            MailMessage[] mails = l.toArray(new MailMessage[l.size()]);

                            MailFetchListenerResult listenerResult = listenerChain.onAfterFetch(mails, false, getMailAccess(), fetchListenerState);
                            if (ListenerReply.DENY == listenerResult.getReply()) {
                                OXException e = listenerResult.getError();
                                if (null == e) {
                                    // Should not occur
                                    e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                                }
                                throw e;
                            }
                        }

                        checkMailsForColor(result);
                        return result;
                    } catch (OXException e) {
                        // Check for missing "THREAD=REFERENCES" capability
                        if ((2046 != e.getCode() || (!"MSG".equals(e.getPrefix()) && !"IMAP".equals(e.getPrefix()))) && !MailExceptionCode.UNSUPPORTED_OPERATION.equals(e)) {
                            throw e;
                        }
                    }
                }

                ISimplifiedThreadStructure sts = messageStorage.supports(ISimplifiedThreadStructure.class);
                if (null != sts) {
                    try {
                        List<List<MailMessage>> mails = sts.getThreadSortedMessages(fullName, mergeWithSent, cache, null == fromToIndices ? IndexRange.NULL : new IndexRange(fromToIndices[0], fromToIndices[1]), max, sortField, orderDir, mailFields.toArray(), searchTerm);

                        if (null != headerFields && headerFields.length > 0) {
                            MessageUtility.enrichWithHeaders(mails, headerFields, messageStorage);
                        }

                        if (!getMailAccess().getWarnings().isEmpty()) {
                            warnings.addAll(getMailAccess().getWarnings());
                        }

                        /*
                         * Trigger fetch listener processing
                         */
                        if (notEmptyChain) {
                            List<MailMessage> l = new ArrayList<>(mails.size());
                            for (List<MailMessage> sublist : mails) {
                                for (MailMessage mail : sublist) {
                                    l.add(mail);
                                }
                            }
                            MailMessage[] ma = l.toArray(new MailMessage[l.size()]);

                            MailFetchListenerResult listenerResult = listenerChain.onAfterFetch(ma, false, getMailAccess(), fetchListenerState);
                            if (ListenerReply.DENY == listenerResult.getReply()) {
                                OXException e = listenerResult.getError();
                                if (null == e) {
                                    // Should not occur
                                    e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                                }
                                throw e;
                            }
                        }

                        checkMailsForColor(mails);
                        return mails;
                    } catch (OXException e) {
                        // Check for missing "THREAD=REFERENCES" capability
                        if ((2046 != e.getCode() || (!"MSG".equals(e.getPrefix()) && !"IMAP".equals(e.getPrefix()))) && !MailExceptionCode.UNSUPPORTED_OPERATION.equals(e)) {
                            throw e;
                        }
                    }
                }

                throw MailExceptionCode.UNSUPPORTED_OPERATION.create();
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getAllSimpleThreadStructuredMessagesCallable);
    }

    @Override
    public SearchIterator<MailMessage> getAllThreadedMessages(String folder, int sortCol, int order, int[] fields, int[] fromToIndices) throws OXException {
        return getThreadedMessages(folder, fromToIndices, sortCol, order, null, null, false, fields);
    }

    @Override
    public SearchIterator<MailFolder> getChildFolders(String parentFolder, boolean all) throws OXException {
        Callable<SearchIterator<MailFolder>> getChildFoldersCallable = new Callable<SearchIterator<MailFolder>>() {

            @Override
            public SearchIterator<MailFolder> call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(parentFolder);
                int localAccountId = argument.getAccountId();
                initConnection(localAccountId);
                String parentFullname = argument.getFullname();
                List<MailFolder> children = new LinkedList<>(Arrays.asList(getMailAccess().getFolderStorage().getSubfolders(parentFullname, all)));
                if (children.isEmpty()) {
                    return SearchIteratorAdapter.emptyIterator();
                }
                /*
                 * Filter against possible POP3 storage folders
                 */
                if (MailAccount.DEFAULT_ID == localAccountId && MailProperties.getInstance().isHidePOP3StorageFolders(session.getUserId(), session.getContextId())) {
                    Set<String> pop3StorageFolders = RdbMailAccountStorage.getPOP3StorageFolders(session);
                    for (Iterator<MailFolder> it = children.iterator(); it.hasNext();) {
                        MailFolder mailFolder = it.next();
                        if (pop3StorageFolders.contains(mailFolder.getFullname())) {
                            it.remove();
                        }
                    }
                }
                /*
                 * Check if denoted parent can hold default folders like Trash, Sent, etc.
                 */
                if (!MailFolder.ROOT_FOLDER_ID.equals(parentFullname) && !INBOX_ID.equals(parentFullname)) {
                    /*
                     * Denoted parent is not capable to hold default folders. Therefore output as it is.
                     */
                    Collections.sort(children, new SimpleMailFolderComparator(getUserLocale()));
                    return new SearchIteratorDelegator<>(children.iterator(), children.size());
                }
                /*
                 * Ensure default folders are at first positions
                 */
                String[] names;
                if (isDefaultFoldersChecked(localAccountId)) {
                    names = getSortedDefaultMailFolders(localAccountId);
                } else {
                    List<String> tmp = new LinkedList<>();

                    FullnameArgument fa = prepareMailFolderParam(getInboxFolder(localAccountId));
                    if (null != fa) {
                        tmp.add(fa.getFullname());
                    }

                    fa = prepareMailFolderParam(getDraftsFolder(localAccountId));
                    if (null != fa) {
                        tmp.add(fa.getFullname());
                    }

                    fa = prepareMailFolderParam(getSentFolder(localAccountId));
                    if (null != fa) {
                        tmp.add(fa.getFullname());
                    }

                    fa = prepareMailFolderParam(getSpamFolder(localAccountId));
                    if (null != fa) {
                        tmp.add(fa.getFullname());
                    }

                    fa = prepareMailFolderParam(getTrashFolder(localAccountId));
                    if (null != fa) {
                        tmp.add(fa.getFullname());
                    }

                    names = tmp.toArray(new String[tmp.size()]);
                }
                /*
                 * Sort them
                 */
                Collections.sort(children, new MailFolderComparator(names, getUserLocale()));
                return new SearchIteratorDelegator<>(children.iterator(), children.size());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getChildFoldersCallable);
    }

    @Override
    public String getConfirmedHamFolder(int accountId) throws OXException {
        if (isDefaultFoldersChecked(accountId)) {
            return prepareFullname(accountId, getDefaultMailFolder(StorageUtility.INDEX_CONFIRMED_HAM, accountId));
        }

        Callable<String> getConfirmedHamFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                initConnection(accountId);
                return prepareFullname(accountId, getMailAccess().getFolderStorage().getConfirmedHamFolder());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getConfirmedHamFolderCallable);
    }

    @Override
    public String getConfirmedSpamFolder(int accountId) throws OXException {
        if (isDefaultFoldersChecked(accountId)) {
            return prepareFullname(accountId, getDefaultMailFolder(StorageUtility.INDEX_CONFIRMED_SPAM, accountId));
        }

        Callable<String> getConfirmedSpamFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                initConnection(accountId);
                return prepareFullname(accountId, getMailAccess().getFolderStorage().getConfirmedSpamFolder());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getConfirmedSpamFolderCallable);
    }

    private String getDefaultMailFolder(int index, int accountId) {
        String[] arr = MailSessionCache.getInstance(session).getParameter(accountId, MailSessionParameterNames.getParamDefaultFolderArray());
        return arr == null ? null : (index < arr.length ? arr[index] : null);
    }

    String[] getSortedDefaultMailFolders(int accountId) {
        String[] arr = MailSessionCache.getInstance(session).getParameter(accountId, MailSessionParameterNames.getParamDefaultFolderArray());
        if (arr == null) {
            return new String[0];
        }
        return new String[] { INBOX_ID, arr[StorageUtility.INDEX_DRAFTS], arr[StorageUtility.INDEX_SENT], arr[StorageUtility.INDEX_SPAM], arr[StorageUtility.INDEX_TRASH] };
    }

    @Override
    public int getDeletedMessageCount(String folder) throws OXException {
        Callable<Integer> getDeletedMessageCountCallable = new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                return I(getMailAccess().getFolderStorage().getFolder(fullName).getDeletedMessageCount());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getDeletedMessageCountCallable).intValue();
    }

    @Override
    public String getDraftsFolder(int accountId) throws OXException {
        if (isDefaultFoldersChecked(accountId)) {
            return prepareFullname(accountId, getDefaultMailFolder(StorageUtility.INDEX_DRAFTS, accountId));
        }

        Callable<String> getDraftsFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                initConnection(accountId);
                return prepareFullname(accountId, getMailAccess().getFolderStorage().getDraftsFolder());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getDraftsFolderCallable);
    }

    @Override
    public MailFolder getFolder(String folder, boolean checkFolder) throws OXException {
        Callable<MailFolder> getFolderCallable = new Callable<MailFolder>() {

            @Override
            public MailFolder call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                return getMailAccess().getFolderStorage().getFolder(fullName);
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getFolderCallable);
    }

    private static int maxForwardCount(int userId, int contextId) {
        return MailProperties.getInstance().getMaxForwardCount(userId, contextId);
    }

    @Override
    public MailMessage getForwardMessageForDisplay(String[] folders, String[] fowardMsgUIDs, UserSettingMail usm, FromAddressProvider fromAddressProvider) throws OXException {
        if ((null == folders) || (null == fowardMsgUIDs) || (folders.length != fowardMsgUIDs.length)) {
            throw new IllegalArgumentException("Illegal arguments");
        }
        int maxForwardCount = maxForwardCount(session.getUserId(), session.getContextId());
        if (maxForwardCount > 0 && folders.length > maxForwardCount) {
            throw MailExceptionCode.TOO_MANY_FORWARD_MAILS.create(Integer.valueOf(maxForwardCount));
        }
        FullnameArgument[] arguments = new FullnameArgument[folders.length];
        for (int i = 0; i < folders.length; i++) {
            arguments[i] = prepareMailFolderParam(folders[i]);
        }
        boolean sameAccount = true;
        int accountId = arguments[0].getAccountId();
        int length = arguments.length;
        for (int i = 1; sameAccount && i < length; i++) {
            sameAccount = accountId == arguments[i].getAccountId();
        }
        if (sameAccount) {
            initConnection(accountId);
            MailMessage[] originalMails = new MailMessage[folders.length];
            {
                for (int i = 0; i < length; i++) {
                    String fullName = arguments[i].getFullname();
                    MailMessage origMail = mailAccess.getMessageStorage().getMessage(fullName, fowardMsgUIDs[i], false);
                    if (null == origMail) {
                        throw MailExceptionCode.MAIL_NOT_FOUND.create(fowardMsgUIDs[i], fullName);
                    }
                    origMail.loadContent();
                    originalMails[i] = origMail;
                }
            }
            return mailAccess.getLogicTools().getFowardMessage(originalMails, usm, fromAddressProvider);
        }
        MailMessage[] originalMails = new MailMessage[folders.length];
        {
            for (int i = 0; i < length; i++) {
                MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> ma = initMailAccess(arguments[i].getAccountId());
                try {
                    MailMessage origMail = ma.getMessageStorage().getMessage(arguments[i].getFullname(), fowardMsgUIDs[i], false);
                    if (null == origMail) {
                        throw MailExceptionCode.MAIL_NOT_FOUND.create(fowardMsgUIDs[i], arguments[i].getFullname());
                    }
                    origMail.loadContent();
                    originalMails[i] = origMail;
                } finally {
                    ma.close(true);
                }
            }
        }
        int[] accountIDs = new int[originalMails.length];
        for (int i = 0; i < accountIDs.length; i++) {
            accountIDs[i] = arguments[i].getAccountId();
        }
        return MimeForward.getFowardMail(originalMails, session, accountIDs, usm, fromAddressProvider);
    }

    @Override
    public String getInboxFolder(int accountId) throws OXException {
        if (isDefaultFoldersChecked(accountId)) {
            return prepareFullname(accountId, INBOX_ID);
        }

        Callable<String> getInboxFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                initConnection(accountId);
                return prepareFullname(accountId, getMailAccess().getFolderStorage().getFolder(INBOX_ID).getFullname());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getInboxFolderCallable);
    }

    @Override
    public MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> getMailAccess() throws OXException {
        return mailAccess;
    }

    @Override
    public MailConfig getMailConfig() throws OXException {
        return mailConfig;
    }

    @Override
    public int getAccountID() {
        return accountId;
    }

    static final MailListField[] FIELDS_FLAGS = new MailListField[] { MailListField.FLAGS };

    static final transient Object[] ARGS_FLAG_SEEN_SET = new Object[] { Integer.valueOf(MailMessage.FLAG_SEEN) };

    static final transient Object[] ARGS_FLAG_SEEN_UNSET = new Object[] { Integer.valueOf(-1 * MailMessage.FLAG_SEEN) };

    @Override
    public MailMessage getMessage(String folder, String msgUID) throws OXException {
        return getMessage(folder, msgUID, true);
    }

    @Override
    public MailMessage getMessage(String folder, String msgUID, boolean markAsSeen) throws OXException {
        Callable<MailMessage> getMessageCallable = new Callable<MailMessage>() {

            @Override
            public MailMessage call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                int localAccountId = argument.getAccountId();
                initConnection(localAccountId);
                if (MailFolder.ROOT_FOLDER_ID.equals(argument.getFullname())) {
                    throw MailExceptionCode.FOLDER_DOES_NOT_HOLD_MESSAGES.create(MailFolder.ROOT_FOLDER_NAME);
                }
                String fullName = argument.getFullname();
                MailMessage mail = getMailAccess().getMessageStorage().getMessage(fullName, msgUID, markAsSeen);
                if (mail != null) {

                    if (!mail.containsAccountId() || mail.getAccountId() < 0) {
                        mail.setAccountId(localAccountId);
                    }
                    /*
                     * Post event for possibly switched \Seen flag
                     */
                    if (mail.containsPrevSeen() && !mail.isPrevSeen()) {
                        postEvent(PushEventConstants.TOPIC_ATTR, localAccountId, fullName, true, true);
                    }

                    /*
                     * Update cache since \Seen flag is possibly changed
                     */
                    try {
                        if (MailMessageCache.getInstance().containsFolderMessages(localAccountId, fullName, session.getUserId(), contextId)) {
                            /*
                             * Update cache entry
                             */
                            MailMessageCache.getInstance().updateCachedMessages(new String[] { mail.getMailId() }, localAccountId, fullName, session.getUserId(), contextId, FIELDS_FLAGS, mail.isSeen() ? ARGS_FLAG_SEEN_SET : ARGS_FLAG_SEEN_UNSET);

                        }
                    } catch (OXException e) {
                        LOG.error("", e);
                    }

                    /*
                     * Check color label vs. \Flagged flag
                     */
                    if (mail.getColorLabel() == 0) {
                        // No color label set; check if \Flagged
                        if (mail.isFlagged()) {
                            FlaggingMode mode = FlaggingMode.getFlaggingMode(session);
                            if (mode.equals(FlaggingMode.FLAGGED_IMPLICIT)) {
                                mail.setColorLabel(FlaggingMode.getFlaggingColor(session));
                            }
                        }
                    } else {
                        // Color label set. Check whether to swallow that information in case only \Flagged should be advertised
                        FlaggingMode mode = FlaggingMode.getFlaggingMode(session);
                        if (mode.equals(FlaggingMode.FLAGGED_ONLY)) {
                            mail.setColorLabel(0);
                        }
                    }

                    List<MailFetchListener> fetchListeners = MailFetchListenerRegistry.getFetchListeners();
                    if (null != fetchListeners) {
                        for (MailFetchListener listener : fetchListeners) {
                            mail = listener.onSingleMailFetch(mail, getMailAccess());
                        }
                    }
                }

                return mail;
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessageCallable);
    }

    @Override
    public MailPart getMessageAttachment(String folder, String msgUID, String attachmentPosition, boolean displayVersion) throws OXException {
        Callable<MailPart> getMessageAttachmentCallable = new Callable<MailPart>() {

            @Override
            public MailPart call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                return getMailAccess().getMessageStorage().getAttachment(fullName, msgUID, attachmentPosition);
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessageAttachmentCallable);
    }

    @Override
    public List<MailPart> getAllMessageAttachments(String folder, String msgUID) throws OXException {
        Callable<List<MailPart>> getAllMessageAttachmentsCallable = new Callable<List<MailPart>>() {

            @Override
            public List<MailPart> call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();

                MailMessage message = getMailAccess().getMessageStorage().getMessage(fullName, msgUID, false);
                if (null == message) {
                    throw MailExceptionCode.MAIL_NOT_FOUND.create(msgUID, fullName);
                }

                NonInlineForwardPartHandler handler = new NonInlineForwardPartHandler();
                new MailMessageParser().setInlineDetectorBehavior(true).parseMailMessage(message, handler);
                return handler.getNonInlineParts();
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getAllMessageAttachmentsCallable);
    }

    @Override
    public ManagedFile getMessages(String folder, String[] msgIds) throws OXException {
        Callable<ManagedFile> getMessagesCallable = new Callable<ManagedFile>() {

            @Override
            public ManagedFile call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                /*
                 * Get parts
                 */
                MailMessage[] mails = new MailMessage[msgIds.length];
                for (int i = 0; i < msgIds.length; i++) {
                    mails[i] = getMailAccess().getMessageStorage().getMessage(fullName, msgIds[i], false);
                }

                /*
                 * Store them temporary to files
                 */
                ManagedFileManagement mfm = ServerServiceRegistry.getInstance().getService(ManagedFileManagement.class, true);

                ManagedFile[] files = new ManagedFile[mails.length];
                try {
                    ByteArrayOutputStream bout = new UnsynchronizedByteArrayOutputStream(8192);
                    for (int i = 0; i < files.length; i++) {
                        MailMessage mail = mails[i];
                        if (null == mail) {
                            files[i] = null;
                        } else {
                            bout.reset();
                            mail.writeTo(bout);
                            files[i] = mfm.createManagedFile(bout.toByteArray());
                        }
                    }
                    /*
                     * ZIP them
                     */
                    try {
                        File tempFile = mfm.newTempFile();
                        ZipArchiveOutputStream zipOutput = new ZipArchiveOutputStream(new FileOutputStream(tempFile));
                        zipOutput.setEncoding("UTF8");
                        zipOutput.setUseLanguageEncodingFlag(true);
                        try {
                            byte[] buf = new byte[8192];
                            Set<String> names = new HashSet<>(files.length);
                            for (int i = 0; i < files.length; i++) {
                                ManagedFile file = files[i];
                                File tmpFile = null == file ? null : file.getFile();
                                if (null != tmpFile) {
                                    FileInputStream in = new FileInputStream(tmpFile);
                                    try {
                                        /*
                                         * Add ZIP entry to output stream
                                         */
                                        String subject = mails[i].getSubject();
                                        String ext = ".eml";
                                        String name = (com.openexchange.java.Strings.isEmpty(subject) ? "mail" + (i + 1) : saneForFileName(subject)) + ext;
                                        int reslen = name.lastIndexOf('.');
                                        int count = 1;
                                        while (false == names.add(name)) {
                                            // Name already contained
                                            name = name.substring(0, reslen);
                                            name = new StringBuilder(name).append("_(").append(count++).append(')').append(ext).toString();
                                        }
                                        ZipArchiveEntry entry;
                                        int num = 1;
                                        while (true) {
                                            try {
                                                int pos = name.indexOf(ext);
                                                String entryName = name.substring(0, pos) + (num > 1 ? "_(" + num + ")" : "") + ext;
                                                entry = new ZipArchiveEntry(entryName);
                                                zipOutput.putArchiveEntry(entry);
                                                break;
                                            } catch (java.util.zip.ZipException e) {
                                                String message = e.getMessage();
                                                if (message == null || !message.startsWith("duplicate entry")) {
                                                    throw e;
                                                }
                                                num++;
                                            }
                                        }
                                        /*
                                         * Transfer bytes from the file to the ZIP file
                                         */
                                        long size = 0;
                                        for (int len; (len = in.read(buf)) > 0;) {
                                            zipOutput.write(buf, 0, len);
                                            size += len;
                                        }
                                        entry.setSize(size);
                                        /*
                                         * Complete the entry
                                         */
                                        zipOutput.closeArchiveEntry();
                                    } finally {
                                        try {
                                            in.close();
                                        } catch (IOException e) {
                                            LOG.error("", e);
                                        }
                                    }
                                }
                            }
                        } finally {
                            // Complete the ZIP file
                            try {
                                zipOutput.close();
                            } catch (IOException e) {
                                LOG.error("", e);
                            }
                        }
                        /*
                         * Return managed file
                         */
                        return mfm.createManagedFile(tempFile);
                    } catch (IOException e) {
                        if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                            throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
                        }
                        OXException oxe = MailExceptionCode.IO_ERROR.create(e, e.getMessage());
                        if (IOs.isConnectionReset(e)) {
                            /*-
                             * A "java.io.IOException: Connection reset by peer" is thrown when the other side has abruptly aborted the connection in midst of a transaction.
                             *
                             * That can have many causes which are not controllable from the Middleware side. E.g. the end-user decided to shutdown the client or change the
                             * server abruptly while still interacting with your server, or the client program has crashed, or the enduser's Internet connection went down,
                             * or the enduser's machine crashed, etc, etc.
                             */
                            oxe.markLightWeight();
                        }
                        throw oxe;
                    }
                } catch (OXException e) {
                    throw e;
                } finally {
                    for (ManagedFile file : files) {
                        if (null != file) {
                            file.delete();
                        }
                    }
                }
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessagesCallable);
    }

    @Override
    public ManagedFile getMessageAttachments(String folder, String msgUID, String[] attachmentPositions) throws OXException {
        Callable<ManagedFile> getMessageAttachmentsCallable = new Callable<ManagedFile>() {

            @Override
            public ManagedFile call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                /*
                 * Get parts
                 */
                MailPart[] parts;
                if (null == attachmentPositions) {
                    List<MailPart> l = getAllMessageAttachments(folder, msgUID);
                    parts = l.toArray(new MailPart[l.size()]);
                } else {
                    parts = new MailPart[attachmentPositions.length];
                    for (int i = 0; i < parts.length; i++) {
                        parts[i] = getMailAccess().getMessageStorage().getAttachment(fullName, msgUID, attachmentPositions[i]);
                    }
                }
                /*
                 * Store them temporary to files
                 */
                ManagedFileManagement mfm = ServerServiceRegistry.getInstance().getService(ManagedFileManagement.class, true);
                ManagedFile[] files = new ManagedFile[parts.length];
                try {
                    for (int i = 0; i < files.length; i++) {
                        MailPart part = parts[i];
                        if (null == part) {
                            files[i] = null;
                        } else {
                            files[i] = mfm.createManagedFile(part.getInputStream());
                        }
                    }
                    /*
                     * ZIP them
                     */
                    try {
                        File tempFile = mfm.newTempFile();
                        ZipArchiveOutputStream zipOutput = new ZipArchiveOutputStream(new FileOutputStream(tempFile));
                        zipOutput.setEncoding("UTF8");
                        zipOutput.setUseLanguageEncodingFlag(true);
                        try {
                            byte[] buf = new byte[8192];
                            for (int i = 0; i < files.length; i++) {
                                ManagedFile file = files[i];
                                File tmpFile = null == file ? null : file.getFile();
                                if (null != tmpFile) {
                                    FileInputStream in = new FileInputStream(tmpFile);
                                    try {
                                        /*
                                         * Add ZIP entry to output stream
                                         */
                                        String name = parts[i].getFileName();
                                        if (null == name) {
                                            List<String> extensions = MimeType2ExtMap.getFileExtensions(parts[i].getContentType().getBaseType());
                                            name = extensions == null || extensions.isEmpty() ? "part.dat" : "part." + extensions.get(0);
                                        }
                                        int num = 1;
                                        ZipArchiveEntry entry;
                                        while (true) {
                                            try {
                                                String entryName;
                                                {
                                                    int pos = name.indexOf('.');
                                                    if (pos < 0) {
                                                        entryName = name + (num > 1 ? "_(" + num + ")" : "");
                                                    } else {
                                                        entryName = name.substring(0, pos) + (num > 1 ? "_(" + num + ")" : "") + name.substring(pos);
                                                    }
                                                }
                                                entry = new ZipArchiveEntry(entryName);
                                                zipOutput.putArchiveEntry(entry);
                                                break;
                                            } catch (java.util.zip.ZipException e) {
                                                String message = e.getMessage();
                                                if (message == null || !message.startsWith("duplicate entry")) {
                                                    throw e;
                                                }
                                                num++;
                                            }
                                        }
                                        /*
                                         * Transfer bytes from the file to the ZIP file
                                         */
                                        long size = 0;
                                        for (int len; (len = in.read(buf)) > 0;) {
                                            zipOutput.write(buf, 0, len);
                                            size += len;
                                        }
                                        entry.setSize(size);
                                        /*
                                         * Complete the entry
                                         */
                                        zipOutput.closeArchiveEntry();
                                    } finally {
                                        Streams.close(in);
                                    }
                                }
                            }
                        } finally {
                            // Complete the ZIP file
                            Streams.close(zipOutput);
                        }
                        /*
                         * Return managed file
                         */
                        return mfm.createManagedFile(tempFile);
                    } catch (IOException e) {
                        if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                            throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
                        }
                        OXException oxe = MailExceptionCode.IO_ERROR.create(e, e.getMessage());
                        if (IOs.isConnectionReset(e)) {
                            /*-
                             * A "java.io.IOException: Connection reset by peer" is thrown when the other side has abruptly aborted the connection in midst of a transaction.
                             *
                             * That can have many causes which are not controllable from the Middleware side. E.g. the end-user decided to shutdown the client or change the
                             * server abruptly while still interacting with your server, or the client program has crashed, or the enduser's Internet connection went down,
                             * or the enduser's machine crashed, etc, etc.
                             */
                            oxe.markLightWeight();
                        }
                        throw oxe;
                    }
                } finally {
                    for (ManagedFile file : files) {
                        if (null != file) {
                            file.delete();
                        }
                    }
                }
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessageAttachmentsCallable);
    }

    @Override
    public int getMessageCount(String folder) throws OXException {
        Callable<Integer> getMessageCountCallable = new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                IMailFolderStorage folderStorage = getMailAccess().getFolderStorage();

                IMailFolderStorageEnhanced storageEnhanced = folderStorage.supports(IMailFolderStorageEnhanced.class);
                if (null != storageEnhanced) {
                    return I(storageEnhanced.getTotalCounter(fullName));
                }

                return I(folderStorage.getFolder(fullName).getMessageCount());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessageCountCallable).intValue();
    }

    @Override
    public MailPart getMessageImage(String folder, String msgUID, String cid) throws OXException {
        Callable<MailPart> getMessageImageCallable = new Callable<MailPart>() {

            @Override
            public MailPart call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                return getMailAccess().getMessageStorage().getImageAttachment(fullName, msgUID, cid);
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessageImageCallable);
    }

    /**
     * Adds the configured color to colorless flagged mails in case the flagging mode is {@link FlaggingMode.FLAGGED_IMPLICIT} and
     * removes the color in case the mode is {@link FlaggingMode.FLAGGED_ONLY}.
     *
     *
     * @param threads The mail threads to check
     */
    void checkMailsForColor(List<List<MailMessage>> threads) {
        FlaggingMode mode = FlaggingMode.getFlaggingMode(session);
        for (List<MailMessage> mails : threads) {
            checkMailsForColor(mails, mode);
        }
    }

    /**
     * Adds the configured color to colorless flagged mails in case the flagging mode is {@link FlaggingMode.FLAGGED_IMPLICIT} and
     * removes the color in case the mode is {@link FlaggingMode.FLAGGED_ONLY}.
     *
     *
     * @param mails The mails to check
     */
    void checkMailsForColor(MailMessage[] mails) {
        FlaggingMode mode = FlaggingMode.getFlaggingMode(session);
        checkMailsForColor(Arrays.asList(mails), mode);
    }

    /**
     * Adds the configured color to colorless flagged mails in case the flagging mode is {@link FlaggingMode.FLAGGED_IMPLICIT} and
     * removes the color in case the mode is {@link FlaggingMode.FLAGGED_ONLY}.
     *
     *
     * @param mails The mails to check
     * @param mode The current {@link FlaggingMode} of the user
     */
    void checkMailsForColor(Iterable<MailMessage> mails, FlaggingMode mode) {
        if (mode.equals(FlaggingMode.FLAGGED_IMPLICIT)) {
            int color = FlaggingMode.getFlaggingColor(session);
            for (MailMessage mail : mails) {
                if (mail != null && mail.getColorLabel() == 0 && mail.isFlagged()) {
                    mail.setColorLabel(color);
                }
            }
            return;
        }
        if (mode.equals(FlaggingMode.FLAGGED_ONLY)) {
            for (MailMessage mail : mails) {
                if (mail != null && mail.getColorLabel() != 0) {
                    mail.setColorLabel(0);
                }
            }
            return;
        }
    }

    /**
     * Checks whether the given fields object contains all necessary fields for the color check
     *
     * @param fields The fields to check
     * @return true if fields was changed, false otherwise
     */
    boolean checkFieldsForColorCheck(MailFields fields) {
        FlaggingMode mode = FlaggingMode.getFlaggingMode(session);
        if (mode.equals(FlaggingMode.FLAGGED_IMPLICIT)) {
            if (fields.contains(MailField.COLOR_LABEL)) {
                if (!fields.contains(MailField.FLAGS)) {
                    fields.add(MailField.FLAGS);
                    return true;
                }
            } else {
                if (fields.contains(MailField.FLAGS)) {
                    fields.add(MailField.COLOR_LABEL);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public MailMessage[] getMessageList(String folder, String[] uids, int[] fields, String[] headerNames) throws OXException {
        Callable<MailMessage[]> getMessageListCallable = new Callable<MailMessage[]>() {

            @Override
            public MailMessage[] call() throws Exception {
                /*
                 * Although message cache is only used within mail implementation, we have to examine if cache already holds desired messages. If
                 * the cache holds the desired messages no connection has to be fetched/established. This avoids a lot of overhead.
                 */
                FullnameArgument argument = prepareMailFolderParam(folder);
                int localAccountId = argument.getAccountId();
                String fullName = argument.getFullname();
                String[] headers = headerNames;
                boolean loadHeaders = (null != headers && 0 < headers.length);
                MailField[] useFields = MailField.getFields(fields);
                MailFetchArguments fetchArguments = MailFetchArguments.builder(argument, useFields, headerNames).build();
                Map<String, Object> fetchListenerState = new HashMap<>(4);
                /*-
                 * Check for presence in cache
                 * TODO: Think about switching to live-fetch if loadHeaders is true. Loading all data once may be faster than
                 * first loading from cache then loading missing headers in next step
                 */
                try {
                    MailMessage[] mails = MailMessageCache.getInstance().getMessages(uids, localAccountId, fullName, session.getUserId(), contextId);
                    if (null != mails) {
                        boolean accepted = MailFetchListenerRegistry.determineAcceptance(mails, fetchArguments, session);
                        if (accepted) {
                            /*
                             * List request can be served from cache; apply proper account ID to (unconnected) mail servlet interface
                             */
                            MailServletInterfaceImpl.this.accountId = localAccountId;
                            /*
                             * Check if headers/text-preview shall be loaded
                             */
                            boolean loadTextPreview = MailField.contains(useFields, MailField.TEXT_PREVIEW);
                            if (loadHeaders || loadTextPreview) {
                                /*
                                 * Load headers/text-preview of cached mails (if not yet available)
                                 */
                                List<String> loadMe = new LinkedList<>();
                                Map<String, MailMessage> finder = new HashMap<>(mails.length);
                                boolean added;
                                for (MailMessage mail : mails) {
                                    String mailId = mail.getMailId();
                                    finder.put(mailId, mail);
                                    added = false;
                                    if (loadHeaders) {
                                        if (!mail.hasHeaders(headers)) {
                                            loadMe.add(mailId);
                                            added = true;
                                        }
                                    }
                                    if (!added && loadTextPreview) {
                                        if (null == mail.getTextPreview()) {
                                            loadMe.add(mailId);
                                        }
                                    }
                                }
                                if (!loadMe.isEmpty()) {
                                    initConnection(localAccountId);
                                    if (loadTextPreview && false == getMailAccess().getMailConfig().getCapabilities().hasTextPreview()) {
                                        loadTextPreview = false;
                                    }

                                    if (loadHeaders || loadTextPreview) {
                                        IMailMessageStorage messageStorage = getMailAccess().getMessageStorage();
                                        if (loadHeaders) {
                                            IMailMessageStorageExt messageStorageExt = messageStorage.supports(IMailMessageStorageExt.class);
                                            if (null != messageStorageExt) {
                                                MailField[] fieldsToLoad = loadTextPreview ? MailField.add(FIELDS_ID_INFO, MailField.TEXT_PREVIEW) : FIELDS_ID_INFO;
                                                for (MailMessage loaded : messageStorageExt.getMessages(fullName, loadMe.toArray(new String[loadMe.size()]), fieldsToLoad, headers)) {
                                                    if (null != loaded) {
                                                        MailMessage mailMessage = finder.get(loaded.getMailId());
                                                        if (null != mailMessage) {
                                                            mailMessage.addHeaders(loaded.getHeaders());
                                                            if (loadTextPreview) {
                                                                mailMessage.setTextPreview(loaded.getTextPreview());
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                MailField[] fieldsToLoad = loadTextPreview ? MailField.add(FIELDS_HEADERS, MailField.TEXT_PREVIEW) : FIELDS_HEADERS;
                                                for (MailMessage loaded : messageStorage.getMessages(fullName, loadMe.toArray(new String[loadMe.size()]), fieldsToLoad)) {
                                                    if (null != loaded) {
                                                        MailMessage mailMessage = finder.get(loaded.getMailId());
                                                        if (null != mailMessage) {
                                                            mailMessage.addHeaders(loaded.getHeaders());
                                                            if (loadTextPreview) {
                                                                mailMessage.setTextPreview(loaded.getTextPreview());
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            for (MailMessage withTextPreview : messageStorage.getMessages(fullName, loadMe.toArray(new String[loadMe.size()]), FIELDS_TEXT_PREVIEW)) {
                                                if (null != withTextPreview) {
                                                    MailMessage mailMessage = finder.get(withTextPreview.getMailId());
                                                    if (null != mailMessage) {
                                                        mailMessage.setTextPreview(withTextPreview.getTextPreview());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            checkMailsForColor(mails);
                            return mails;
                        }
                    }
                } catch (OXException e) {
                    LOG.error("", e);
                }
                /*
                 * Live-Fetch from mail storage
                 */
                initConnection(localAccountId);
                boolean cachable = uids.length < getMailAccess().getMailConfig().getMailProperties().getMailFetchLimit();

                if (cachable) {
                    useFields = MailFields.addIfAbsent(useFields, MimeStorageUtility.getCacheFieldsArray());
                    useFields = MailFields.addIfAbsent(useFields, MailField.ID, MailField.FOLDER_ID);
                }

                MailFields mailFieldsForCheck = new MailFields(useFields);
                if (checkFieldsForColorCheck(mailFieldsForCheck)) {
                    useFields = mailFieldsForCheck.toArray();
                }

                MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, getMailAccess(), fetchListenerState);
                boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);
                if (notEmptyChain) {
                    MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, getMailAccess(), fetchListenerState);
                    if (attributation.isApplicable()) {
                        useFields = attributation.getFields();
                        headers = attributation.getHeaderNames();
                    }
                }

                MailMessage[] mails;
                {
                    IMailMessageStorage messageStorage = getMailAccess().getMessageStorage();

                    IMailMessageStorageExt messageStorageExt = messageStorage.supports(IMailMessageStorageExt.class);
                    if (null != messageStorageExt) {
                        mails = messageStorageExt.getMessages(fullName, uids, useFields, headers);
                    } else {
                        /*
                         * Get appropriate mail fields
                         */
                        MailField[] mailFields;
                        if (loadHeaders) {
                            /*
                             * Ensure MailField.HEADERS is contained
                             */
                            MailFields col = new MailFields(useFields);
                            col.add(MailField.HEADERS);
                            mailFields = col.toArray();
                        } else {
                            mailFields = useFields;
                        }
                        mails = messageStorage.getMessages(fullName, uids, mailFields);
                    }
                }

                if (notEmptyChain) {
                    MailFetchListenerResult result = listenerChain.onAfterFetch(mails, cachable, getMailAccess(), fetchListenerState);
                    if (ListenerReply.DENY == result.getReply()) {
                        OXException e = result.getError();
                        if (null == e) {
                            // Should not occur
                            e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                        }
                        throw e;
                    }
                    mails = result.getMails();
                    if (!result.isCacheable()) {
                        cachable = false;
                    }
                }

                try {
                    if (cachable && MailMessageCache.getInstance().containsFolderMessages(localAccountId, fullName, session.getUserId(), contextId) && getMailAccess().getWarnings().isEmpty()) {
                        MailMessageCache.getInstance().putMessages(localAccountId, mails, session.getUserId(), contextId);
                    }
                } catch (OXException e) {
                    LOG.error("", e);
                }

                if (!getMailAccess().getWarnings().isEmpty()) {
                    warnings.addAll(getMailAccess().getWarnings());
                }

                checkMailsForColor(mails);
                return mails;
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessageListCallable);
    }

    @Override
    public SearchTerm<?> createSearchTermFrom(com.openexchange.search.SearchTerm<?> searchTerm) throws OXException {
        return SearchTermMapper.map(searchTerm);
    }

    @Override
    public SearchIterator<MailMessage> getMessages(String folder, int[] fromToIndices, int sortCol, int order, com.openexchange.search.SearchTerm<?> searchTerm, boolean linkSearchTermsWithOR, int[] fields, String[] headerFields, boolean supportsContinuation) throws OXException {
        Callable<SearchIterator<MailMessage>> getMessagesCallable = new Callable<SearchIterator<MailMessage>>() {

            @Override
            public SearchIterator<MailMessage> call() throws Exception {
                return getMessagesInternal(prepareMailFolderParam(folder), SearchTermMapper.map(searchTerm), fromToIndices, sortCol, order, fields, headerFields, supportsContinuation);
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessagesCallable);
    }

    @Override
    public com.openexchange.mail.search.SearchTerm<?> createSearchTermFrom(int[] searchCols, String[] searchPatterns, boolean linkSearchTermsWithOR) throws OXException {
        checkPatternLength(searchPatterns);
        return (searchCols == null) || (searchCols.length == 0) ? null : SearchUtility.parseFields(searchCols, searchPatterns, linkSearchTermsWithOR);
    }

    @Override
    public SearchIterator<MailMessage> getMessages(String folder, int[] fromToIndices, int sortCol, int order, int[] searchCols, String[] searchPatterns, boolean linkSearchTermsWithOR, int[] fields, String[] headerFields, boolean supportsContinuation) throws OXException {
        SearchTerm<?> searchTerm = createSearchTermFrom(searchCols, searchPatterns, linkSearchTermsWithOR);

        Callable<SearchIterator<MailMessage>> getMessagesCallable = new Callable<SearchIterator<MailMessage>>() {

            @Override
            public SearchIterator<MailMessage> call() throws Exception {
                return getMessagesInternal(prepareMailFolderParam(folder), searchTerm, fromToIndices, sortCol, order, fields, headerFields, supportsContinuation);
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getMessagesCallable);
    }

    SearchIterator<MailMessage> getMessagesInternal(FullnameArgument argument, SearchTerm<?> searchTerm, int[] fromToIndices, int sortCol, int order, int[] fields, String[] headerNames, boolean supportsContinuation) throws OXException {
        if (checkParameters) {
            // Check if all request looks reasonable
            MailFields mailFields = MailFields.valueOf(fields);
            if (null == fromToIndices && mailFields.retainAll(folderAndId)) {
               // More than folder an ID requested
               throw MailExceptionCode.REQUEST_NOT_PERMITTED.create("Only folder and ID are allowed to be queried without a range");
            }
        }

        // Identify and sort messages according to search term and sort criteria while only fetching their IDs
        String fullName = argument.getFullname();
        MailSortField sortField = MailSortField.getField(sortCol);
        OrderDirection orderDir = OrderDirection.getOrderDirection(order);
        String[] headers = headerNames;
        MailMessage[] mails = null;
        {
            IndexRange indexRange = null == fromToIndices ? IndexRange.NULL : new IndexRange(fromToIndices[0], fromToIndices[1]);
            if ("unified/inbox".equalsIgnoreCase(fullName)) {
                UnifiedViewService unifiedView = ServerServiceRegistry.getInstance().getService(UnifiedViewService.class);
                if (null == unifiedView) {
                    throw MailExceptionCode.FOLDER_NOT_FOUND.create(fullName);
                }
                mails = unifiedView.searchMessages(UnifiedFullName.INBOX, indexRange, sortField, orderDir, searchTerm, FIELDS_ID_INFO, session);
                initConnection(ServerServiceRegistry.getInstance().getService(UnifiedInboxManagement.class).getUnifiedINBOXAccountID(session));
                fullName = UnifiedFullName.INBOX.getFullName();
            } else {
                int localAccountId = argument.getAccountId();
                initConnection(argument.getAccountId());

                // Check if a certain range/page is requested
                if (IndexRange.NULL != indexRange) {
                    SearchIterator<MailMessage> it = getMessageRange(searchTerm, fields, headers, fullName, indexRange, sortField, orderDir, localAccountId);
                    return setAccountInformation(localAccountId, it);
                }

                mails = mailAccess.getMessageStorage().searchMessages(fullName, indexRange, sortField, orderDir, searchTerm, FIELDS_ID_INFO);
            }
        }
        /*
         * Proceed
         */
        if ((mails == null) || (mails.length == 0) || onlyNull(mails)) {
            return SearchIteratorAdapter.<MailMessage> emptyIterator();
        }
        MailField[] useFields = MailField.getFields(fields);
        boolean cachable = (mails.length < mailAccess.getMailConfig().getMailProperties().getMailFetchLimit());

        boolean onlyFolderAndID;
        if (cachable) {
            /*
             * Selection fits into cache: Prepare for caching
             */
            useFields = MailFields.addIfAbsent(useFields, MimeStorageUtility.getCacheFieldsArray());
            useFields = MailFields.addIfAbsent(useFields, MailField.ID, MailField.FOLDER_ID);
            onlyFolderAndID = false;
        } else {
            onlyFolderAndID = (null != headers && 0 < headers.length) ? false : onlyFolderAndID(useFields);
        }
        if (supportsContinuation) {
            MailFields mfs = new MailFields(useFields);
            if (!mfs.contains(MailField.SUPPORTS_CONTINUATION)) {
                mfs.add(MailField.SUPPORTS_CONTINUATION);
                useFields = mfs.toArray();
            }
        }

        MailFields mailFields = new MailFields(useFields);
        if (checkFieldsForColorCheck(mailFields)) {
            useFields = mailFields.toArray();
        }

        MailFetchArguments fetchArguments = MailFetchArguments.builder(argument, useFields, headerNames).setSearchTerm(searchTerm).setSortOptions(sortField, orderDir).build();
        Map<String, Object> fetchListenerState = new HashMap<>(4);
        MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, mailAccess, fetchListenerState);
        boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);
        if (notEmptyChain) {
            MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, mailAccess, fetchListenerState);
            if (attributation.isApplicable()) {
                useFields = attributation.getFields();
                headers = attributation.getHeaderNames();
                onlyFolderAndID = (null != headers && 0 < headers.length) ? false : onlyFolderAndID(useFields);
            }
        }

        /*-
         * More than ID and folder requested?
         *  AND
         * Messages do not already contain requested fields although only IDs were requested
         */
        if (!onlyFolderAndID && !containsAll(firstNotNull(mails), useFields)) {
            /*
             * Extract IDs
             */
            String[] mailIds = new String[mails.length];
            for (int i = 0; i < mailIds.length; i++) {
                MailMessage m = mails[i];
                if (null != m) {
                    mailIds[i] = m.getMailId();
                }
            }
            /*
             * Fetch identified messages by their IDs and pre-fill them according to specified fields
             */
            if (null != headers && 0 < headers.length) {
                IMailMessageStorage messageStorage = mailAccess.getMessageStorage();

                IMailMessageStorageExt messageStorageExt = messageStorage.supports(IMailMessageStorageExt.class);
                if (null != messageStorageExt) {
                    mails = messageStorageExt.getMessages(fullName, mailIds, useFields, headers);
                } else {
                    useFields = MailFields.addIfAbsent(useFields, MailField.ID);
                    mails = messageStorage.getMessages(fullName, mailIds, useFields);
                    MessageUtility.enrichWithHeaders(fullName, mails, headers, messageStorage);
                }
            } else {
                mails = mailAccess.getMessageStorage().getMessages(fullName, mailIds, useFields);
            }
            if ((mails == null) || (mails.length == 0) || onlyNull(mails)) {
                return SearchIteratorAdapter.emptyIterator();
            }
        }

        /*
         * Trigger fetch listener processing
         */
        if (notEmptyChain) {
            MailFetchListenerResult result = listenerChain.onAfterFetch(mails, cachable, mailAccess, fetchListenerState);
            if (ListenerReply.DENY == result.getReply()) {
                OXException e = result.getError();
                if (null == e) {
                    // Should not occur
                    e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                }
                throw e;
            }
            mails = result.getMails();
            if (!result.isCacheable()) {
                cachable = false;
            }
        }

        /*
         * Put message information into cache
         */
        try {
            /*
             * Remove old user cache entries
             */
            MailMessageCache.getInstance().removeUserMessages(session.getUserId(), contextId);
            if ((cachable) && (mails.length > 0)) {
                /*
                 * ... and put new ones
                 */
                MailMessageCache.getInstance().putMessages(accountId, mails, session.getUserId(), contextId);
            }
        } catch (OXException e) {
            LOG.error("", e);
        }

        checkMailsForColor(mails);

        /*
         * Set account information
         */
        List<MailMessage> l = new LinkedList<>();
        for (MailMessage mail : mails) {
            if (mail != null) {
                if (!mail.containsAccountId() || mail.getAccountId() < 0) {
                    mail.setAccountId(accountId);
                }
                l.add(mail);
            }
        }
        return new SearchIteratorDelegator<>(l);
    }

    private SearchIterator<MailMessage> setAccountInformation(int accountId, SearchIterator<MailMessage> mails) throws OXException {
        List<MailMessage> l = new LinkedList<>();
        for (int i = mails.size(); i-- > 0;) {
            final MailMessage mail = mails.next();
            if (mail != null) {
                if (!mail.containsAccountId() || mail.getAccountId() < 0) {
                    mail.setAccountId(accountId);
                }
                l.add(mail);
            }
        }
        return new SearchIteratorDelegator<>(l);
    }

    private SearchIterator<MailMessage> getMessageRange(SearchTerm<?> searchTerm, int[] fields, String[] headerNames, String fullName, IndexRange indexRange, MailSortField sortField, OrderDirection orderDir, int accountId) throws OXException {
        MailField[] useFields = MailField.getFields(fields);
        boolean cachable = (indexRange.end - indexRange.start) < mailAccess.getMailConfig().getMailProperties().getMailFetchLimit();
        if (cachable) {
            useFields = MailFields.addIfAbsent(useFields, MimeStorageUtility.getCacheFieldsArray());
            useFields = MailFields.addIfAbsent(useFields, MailField.ID, MailField.FOLDER_ID);
        }
        MailFields mailFields = new MailFields(useFields);
        if (checkFieldsForColorCheck(mailFields)) {
            useFields = mailFields.toArray();
        }
        String[] headers = headerNames;
        MailFetchArguments fetchArguments = MailFetchArguments.builder(new FullnameArgument(accountId, fullName), useFields, headerNames).setSearchTerm(searchTerm).setSortOptions(sortField, orderDir).build();
        Map<String, Object> fetchListenerState = new HashMap<>(4);
        MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, mailAccess, fetchListenerState);
        boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);
        if (notEmptyChain) {
            MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, mailAccess, fetchListenerState);
            if (attributation.isApplicable()) {
                useFields = attributation.getFields();
                headers = attributation.getHeaderNames();
            }
        }
        MailMessage[] mails;
        if (null != headers && 0 < headers.length) {
            IMailMessageStorage messageStorage = mailAccess.getMessageStorage();

            IMailMessageStorageExt messageStorageExt = messageStorage.supports(IMailMessageStorageExt.class);
            if (null != messageStorageExt) {
                mails = messageStorageExt.searchMessages(fullName, indexRange, sortField, orderDir, searchTerm, useFields, headers);
            } else {
                mails = mailAccess.getMessageStorage().searchMessages(fullName, indexRange, sortField, orderDir, searchTerm, useFields);
                MessageUtility.enrichWithHeaders(fullName, mails, headers, messageStorage);
            }
        } else {
            mails = mailAccess.getMessageStorage().searchMessages(fullName, indexRange, sortField, orderDir, searchTerm, useFields);
        }
        /*
         * Set account information & filter null elements
         */
        {
            List<MailMessage> l = null;
            int j = 0;

            boolean b = true;
            while (b && j < mails.length) {
                MailMessage mail = mails[j];
                if (mail == null) {
                    l = new ArrayList<>(mails.length);
                    if (j > 0) {
                        for (int k = 0; k < j; k++) {
                            l.add(mails[k]);
                        }
                    }
                    b = false;
                } else {
                    if (!mail.containsAccountId() || mail.getAccountId() < 0) {
                        mail.setAccountId(accountId);
                    }
                    j++;
                }
            }

            if (null != l && j < mails.length) {
                while (j < mails.length) {
                    MailMessage mail = mails[j];
                    if (mail != null) {
                        if (!mail.containsAccountId() || mail.getAccountId() < 0) {
                            mail.setAccountId(accountId);
                        }
                        l.add(mail);
                    }
                    j++;
                }

                mails = l.toArray(new MailMessage[l.size()]);
                l = null; // Help GC
            }
        }
        /*
         * Trigger fetch listener processing
         */
        if (notEmptyChain) {
            MailFetchListenerResult result = listenerChain.onAfterFetch(mails, cachable, mailAccess, fetchListenerState);
            if (ListenerReply.DENY == result.getReply()) {
                OXException e = result.getError();
                if (null == e) {
                    // Should not occur
                    e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                }
                throw e;
            }
            mails = result.getMails();
            if (false == result.isCacheable()) {
                cachable = false;
            }
        }
        /*
         * Put message information into cache
         */
        try {
            /*
             * Remove old user cache entries
             */
            MailMessageCache.getInstance().removeUserMessages(session.getUserId(), contextId);
            if ((cachable) && (mails.length > 0)) {
                /*
                 * ... and put new ones
                 */
                MailMessageCache.getInstance().putMessages(accountId, mails, session.getUserId(), contextId);
            }
        } catch (OXException e) {
            LOG.error("", e);
        }

        checkMailsForColor(mails);
        return new ArrayIterator<>(mails);
    }

    private static boolean onlyNull(MailMessage[] mails) {
        boolean ret = true;
        for (int i = mails.length; ret && i-- > 0;) {
            ret = (null == mails[i]);
        }
        return ret;
    }

    private static MailMessage firstNotNull(MailMessage[] mails) {
        for (int i = mails.length; i-- > 0;) {
            MailMessage m = mails[i];
            if (null != m) {
                return m;
            }
        }
        return null;
    }

    private static boolean containsAll(MailMessage candidate, MailField[] fields) {
        if (null == candidate) {
            return false;
        }
        boolean contained = true;
        int length = fields.length;
        for (int i = 0; contained && i < length; i++) {
            MailField field = fields[i];
            switch (field) {
                case ACCOUNT_NAME:
                    contained = candidate.containsAccountId() || candidate.containsAccountName();
                    break;
                case BCC:
                    contained = candidate.containsBcc();
                    break;
                case CC:
                    contained = candidate.containsCc();
                    break;
                case COLOR_LABEL:
                    contained = candidate.containsColorLabel();
                    break;
                case CONTENT_TYPE:
                    contained = candidate.containsContentType();
                    break;
                case ATTACHMENT:
                    contained = candidate.containsHasAttachment() || candidate.containsAlternativeHasAttachment();
                    break;
                case DISPOSITION_NOTIFICATION_TO:
                    contained = candidate.containsDispositionNotification();
                    break;
                case FLAGS:
                    contained = candidate.containsFlags();
                    break;
                case FOLDER_ID:
                    contained = true;
                    break;
                case FROM:
                    contained = candidate.containsFrom();
                    break;
                case ID:
                    contained = null != candidate.getMailId();
                    break;
                case PRIORITY:
                    contained = candidate.containsPriority();
                    break;
                case RECEIVED_DATE:
                    contained = candidate.containsReceivedDate();
                    break;
                case SENT_DATE:
                    contained = candidate.containsSentDate();
                    break;
                case SIZE:
                    contained = candidate.containsSize();
                    break;
                case SUBJECT:
                    contained = candidate.containsSubject();
                    break;
                case THREAD_LEVEL:
                    contained = candidate.containsThreadLevel();
                    break;
                case TO:
                    contained = candidate.containsTo();
                    break;

                default:
                    contained = false;
                    break;
            }
        }
        return contained;
    }

    /**
     * Checks if specified fields only consist of mail ID and folder ID
     *
     * @param fields The fields to check
     * @return <code>true</code> if specified fields only consist of mail ID and folder ID; otherwise <code>false</code>
     */
    static boolean onlyFolderAndID(MailField[] fields) {
        if (fields.length != 2) {
            return false;
        }
        int i = 0;
        for (MailField field : fields) {
            if (MailField.ID.equals(field)) {
                i |= 1;
            } else if (MailField.FOLDER_ID.equals(field)) {
                i |= 2;
            }
        }
        return (i == 3);
    }

    @Override
    public String[] appendMessages(String destFolder, MailMessage[] mails, boolean force) throws OXException {
        return appendMessages(destFolder, mails, force, false);
    }

    @Override
    public String[] importMessages(String destFolder, MailMessage[] mails, boolean force) throws OXException {
        return appendMessages(destFolder, mails, force, true);
    }

    public String[] appendMessages(String destFolder, MailMessage[] mails, boolean force, boolean isImport) throws OXException {
        if ((mails == null) || (mails.length == 0)) {
            return new String[0];
        }
        if (!force) {
            /*
             * Check for valid from address
             */
            try {
                Set<InternetAddress> validAddrs = new HashSet<>(4);
                if (usm.getSendAddr() != null && usm.getSendAddr().length() > 0) {
                    validAddrs.add(new QuotedInternetAddress(usm.getSendAddr()));
                }
                User localUser = getUser();
                validAddrs.add(new QuotedInternetAddress(localUser.getMail()));
                for (String alias : localUser.getAliases()) {
                    validAddrs.add(new QuotedInternetAddress(alias));
                }
                boolean supportMsisdnAddresses = MailProperties.getInstance().isSupportMsisdnAddresses();
                if (supportMsisdnAddresses) {
                    MsisdnUtility.addMsisdnAddress(validAddrs, this.session);
                }
                for (MailMessage mail : mails) {
                    InternetAddress[] from = mail.getFrom();
                    List<InternetAddress> froms = Arrays.asList(from);
                    if (supportMsisdnAddresses) {
                        for (InternetAddress internetAddress : froms) {
                            String address = internetAddress.getAddress();
                            int pos = address.indexOf('/');
                            if (pos > 0) {
                                internetAddress.setAddress(address.substring(0, pos));
                            }
                        }
                    }
                    if (!validAddrs.containsAll(froms)) {
                        throw MailExceptionCode.INVALID_SENDER.create(froms.size() == 1 ? froms.get(0).toString() : Arrays.toString(from));
                    }
                }
            } catch (AddressException e) {
                throw MimeMailException.handleMessagingException(e);
            }
        }
        FullnameArgument argument = prepareMailFolderParam(destFolder);
        initConnection(argument.getAccountId());
        String fullName = argument.getFullname();
        if (mailAccess.getFolderStorage().getDraftsFolder().equals(fullName)) {
            /*
             * Append to Drafts folder
             */
            for (MailMessage mail : mails) {
                mail.setFlag(MailMessage.FLAG_DRAFT, true);
            }
        }

        if (!isImport) {
            return mailAccess.getMessageStorage().appendMessages(fullName, mails);
        }
        IMailMessageStorage messageStorage = mailAccess.getMessageStorage();
        MailMessage[] tmp = new MailMessage[1];
        List<String> idList = new LinkedList<>();
        for (MailMessage mail : mails) {
            MailImportResult mir = new MailImportResult();
            mir.setMail(mail);
            try {
                tmp[0] = mail;
                String[] idStr = messageStorage.appendMessages(fullName, tmp);
                mir.setId(idStr[0]);
                idList.add(idStr[0]);
            } catch (OXException e) {
                mir.setException(e);
            }
            mailImportResults.add(mir);
        }

        String[] ids = new String[idList.size()];
        for (int i = 0; i < idList.size(); i++) {
            ids[i] = idList.get(i);
        }

        return ids;
    }

    @Override
    public int getNewMessageCount(String folder) throws OXException {
        Callable<Integer> getNewMessageCountCallable = new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                return I(getMailAccess().getFolderStorage().getFolder(fullName).getNewMessageCount());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getNewMessageCountCallable).intValue();
    }

    @Override
    public SearchIterator<MailMessage> getNewMessages(String folder, int sortCol, int order, int[] fields, int limit) throws OXException {
        Callable<SearchIterator<MailMessage>> getNewMessagesCallable = new Callable<SearchIterator<MailMessage>>() {

            @Override
            public SearchIterator<MailMessage> call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                return SearchIteratorAdapter.createArrayIterator(getMailAccess().getMessageStorage().getUnreadMessages(fullName, MailSortField.getField(sortCol), OrderDirection.getOrderDirection(order), MailField.toFields(MailListField.getFields(fields)), limit));
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getNewMessagesCallable);
    }

    @Override
    public SearchIterator<MailFolder> getPathToDefaultFolder(String folder) throws OXException {
        Callable<SearchIterator<MailFolder>> getPathToDefaultFoldersCallable = new Callable<SearchIterator<MailFolder>>() {

            @Override
            public SearchIterator<MailFolder> call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                return SearchIteratorAdapter.createArrayIterator(getMailAccess().getFolderStorage().getPath2DefaultFolder(fullName));
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getPathToDefaultFoldersCallable);
    }

    @Override
    public long[][] getQuotas(int[] types) throws OXException {
        initConnection(MailAccount.DEFAULT_ID);
        com.openexchange.mail.Quota.Type[] qtypes = new com.openexchange.mail.Quota.Type[types.length];
        for (int i = 0; i < qtypes.length; i++) {
            qtypes[i] = getType(types[i]);
        }
        com.openexchange.mail.Quota[] quotas = mailAccess.getFolderStorage().getQuotas(INBOX_ID, qtypes);
        long[][] retval = new long[quotas.length][];
        for (int i = 0; i < retval.length; i++) {
            retval[i] = quotas[i].toLongArray();
        }
        return retval;
    }

    @Override
    public long getQuotaLimit(int type) throws OXException {
        initConnection(MailAccount.DEFAULT_ID);
        if (QUOTA_RESOURCE_STORAGE == type) {
            return mailAccess.getFolderStorage().getStorageQuota(INBOX_ID).getLimit();
        } else if (QUOTA_RESOURCE_MESSAGE == type) {
            return mailAccess.getFolderStorage().getMessageQuota(INBOX_ID).getLimit();
        }
        throw new IllegalArgumentException("Unknown quota resource type: " + type);
    }

    @Override
    public long getQuotaUsage(int type) throws OXException {
        initConnection(MailAccount.DEFAULT_ID);
        if (QUOTA_RESOURCE_STORAGE == type) {
            return mailAccess.getFolderStorage().getStorageQuota(INBOX_ID).getUsage();
        } else if (QUOTA_RESOURCE_MESSAGE == type) {
            return mailAccess.getFolderStorage().getMessageQuota(INBOX_ID).getUsage();
        }
        throw new IllegalArgumentException("Unknown quota resource type: " + type);
    }

    private static com.openexchange.mail.Quota.Type getType(int type) {
        if (QUOTA_RESOURCE_STORAGE == type) {
            return com.openexchange.mail.Quota.Type.STORAGE;
        } else if (QUOTA_RESOURCE_MESSAGE == type) {
            return com.openexchange.mail.Quota.Type.MESSAGE;
        }
        throw new IllegalArgumentException("Unknown quota resource type: " + type);
    }

    @Override
    public MailMessage getReplyMessageForDisplay(String folder, String replyMsgUID, boolean replyToAll, UserSettingMail usm, FromAddressProvider fromAddressProvider) throws OXException {
        FullnameArgument argument = prepareMailFolderParam(folder);
        initConnection(argument.getAccountId());
        String fullName = argument.getFullname();
        MailMessage originalMail = mailAccess.getMessageStorage().getMessage(fullName, replyMsgUID, false);
        if (null == originalMail) {
            throw MailExceptionCode.MAIL_NOT_FOUND.create(replyMsgUID, fullName);
        }
        return mailAccess.getLogicTools().getReplyMessage(originalMail, replyToAll, usm, fromAddressProvider);
    }

    @Override
    public SearchIterator<MailFolder> getRootFolders() throws OXException {
        Callable<SearchIterator<MailFolder>> getRootFoldersCallable = new Callable<SearchIterator<MailFolder>>() {

            @Override
            public SearchIterator<MailFolder> call() throws Exception {
                initConnection(MailAccount.DEFAULT_ID);
                return SearchIteratorAdapter.createArrayIterator(new MailFolder[] { getMailAccess().getFolderStorage().getRootFolder() });
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getRootFoldersCallable);
    }

    @Override
    public String getSentFolder(int accountId) throws OXException {
        if (isDefaultFoldersChecked(accountId)) {
            return prepareFullname(accountId, getDefaultMailFolder(StorageUtility.INDEX_SENT, accountId));
        }

        Callable<String> getSentFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                initConnection(accountId);
                return prepareFullname(accountId, getMailAccess().getFolderStorage().getSentFolder());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getSentFolderCallable);
    }

    @Override
    public String getSpamFolder(int accountId) throws OXException {
        if (isDefaultFoldersChecked(accountId)) {
            return prepareFullname(accountId, getDefaultMailFolder(StorageUtility.INDEX_SPAM, accountId));
        }

        Callable<String> getSpamFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                initConnection(accountId);
                return prepareFullname(accountId, getMailAccess().getFolderStorage().getSpamFolder());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getSpamFolderCallable);
    }

    @Override
    public SearchIterator<MailMessage> getThreadedMessages(String folder, int[] fromToIndices, int sortCol, int order, int[] searchCols, String[] searchPatterns, boolean linkSearchTermsWithOR, int[] fields) throws OXException {
        Callable<SearchIterator<MailMessage>> getThreadedMessagesCallable = new Callable<SearchIterator<MailMessage>>() {

            @Override
            public SearchIterator<MailMessage> call() throws Exception {
                checkPatternLength(searchPatterns);
                FullnameArgument argument = prepareMailFolderParam(folder);
                int localAccountId = argument.getAccountId();
                initConnection(localAccountId);
                String fullName = argument.getFullname();
                SearchTerm<?> searchTerm = (searchCols == null) || (searchCols.length == 0) ? null : SearchUtility.parseFields(searchCols, searchPatterns, linkSearchTermsWithOR);
                MailSortField sortField = MailSortField.getField(sortCol);
                OrderDirection orderDirection = OrderDirection.getOrderDirection(order);
                /*
                 * Identify and thread-sort messages according to search term while only fetching their IDs
                 */
                MailMessage[] mails = getMailAccess().getMessageStorage().getThreadSortedMessages(fullName, fromToIndices == null ? IndexRange.NULL : new IndexRange(fromToIndices[0], fromToIndices[1]), sortField, orderDirection, searchTerm, FIELDS_ID_INFO);
                if ((mails == null) || (mails.length == 0)) {
                    return SearchIteratorAdapter.<MailMessage> emptyIterator();
                }
                MailField[] useFields = MailField.toFields(MailListField.getFields(fields));
                boolean cacheable = mails.length < getMailAccess().getMailConfig().getMailProperties().getMailFetchLimit();
                boolean onlyFolderAndID;
                if (cacheable) {
                    /*
                     * Selection fits into cache: Prepare for caching
                     */
                    useFields = MailFields.addIfAbsent(useFields, MimeStorageUtility.getCacheFieldsArray());
                    useFields = MailFields.addIfAbsent(useFields, MailField.ID, MailField.FOLDER_ID);
                    onlyFolderAndID = false;
                } else {
                    onlyFolderAndID = onlyFolderAndID(useFields);
                }

                MailFetchArguments fetchArguments = MailFetchArguments.builder(argument, useFields, null).setSearchTerm(searchTerm).setSortOptions(sortField, orderDirection).build();
                Map<String, Object> fetchListenerState = new HashMap<>(4);
                MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, getMailAccess(), fetchListenerState);
                boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);
                if (notEmptyChain) {
                    MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, getMailAccess(), fetchListenerState);
                    if (attributation.isApplicable()) {
                        useFields = attributation.getFields();
                        onlyFolderAndID = onlyFolderAndID(useFields);
                    }
                }

                MailFields mailFields = new MailFields(useFields);
                if (checkFieldsForColorCheck(mailFields)) {
                    useFields = mailFields.toArray();
                }

                if (!onlyFolderAndID) {
                    /*
                     * Extract IDs
                     */
                    String[] mailIds = new String[mails.length];
                    for (int i = 0; i < mailIds.length; i++) {
                        mailIds[i] = mails[i].getMailId();
                    }
                    /*
                     * Fetch identified messages by their IDs and pre-fill them according to specified fields
                     */
                    MailMessage[] fetchedMails = getMailAccess().getMessageStorage().getMessages(fullName, mailIds, useFields);
                    /*
                     * Apply thread level
                     */
                    for (int i = 0; i < fetchedMails.length; i++) {
                        MailMessage mailMessage = fetchedMails[i];
                        if (null != mailMessage) {
                            mailMessage.setThreadLevel(mails[i].getThreadLevel());
                        }
                    }
                    mails = fetchedMails;
                }
                /*
                 * Set account information
                 */
                for (MailMessage mail : mails) {
                    if (mail != null && (!mail.containsAccountId() || mail.getAccountId() < 0)) {
                        mail.setAccountId(localAccountId);
                    }
                }
                /*
                 * Trigger fetch listener processing
                 */
                if (notEmptyChain) {
                    MailFetchListenerResult result = listenerChain.onAfterFetch(mails, cacheable, getMailAccess(), fetchListenerState);
                    if (ListenerReply.DENY == result.getReply()) {
                        OXException e = result.getError();
                        if (null == e) {
                            // Should not occur
                            e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                        }
                        throw e;
                    }
                    mails = result.getMails();
                    if (!result.isCacheable()) {
                        cacheable = false;
                    }
                }
                try {
                    /*
                     * Remove old user cache entries
                     */
                    MailMessageCache.getInstance().removeFolderMessages(localAccountId, fullName, session.getUserId(), contextId);
                    if ((mails.length > 0) && cacheable) {
                        /*
                         * ... and put new ones
                         */
                        MailMessageCache.getInstance().putMessages(localAccountId, mails, session.getUserId(), contextId);
                    }
                } catch (OXException e) {
                    LOG.error("", e);
                }
                checkMailsForColor(mails);
                return SearchIteratorAdapter.createArrayIterator(mails);
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(getThreadedMessagesCallable);
    }

    void checkPatternLength(String[] patterns) throws OXException {
        int minimumSearchCharacters = ServerConfig.getInt(ServerConfig.Property.MINIMUM_SEARCH_CHARACTERS);
        if (0 == minimumSearchCharacters || null == patterns) {
            return;
        }
        for (String pattern : patterns) {
            if (null != pattern && SearchStrings.lengthWithoutWildcards(pattern) < minimumSearchCharacters) {
                throw MailExceptionCode.PATTERN_TOO_SHORT.create(I(minimumSearchCharacters));
            }
        }
    }

    @Override
    public String getTrashFolder(int accountId) throws OXException {
        if (isDefaultFoldersChecked(accountId)) {
            return prepareFullname(accountId, getDefaultMailFolder(StorageUtility.INDEX_TRASH, accountId));
        }

        Callable<String> getTrashFolderCallable = new Callable<String>() {

            @Override
            public String call() throws Exception {
                initConnection(accountId);
                return prepareFullname(accountId, getMailAccess().getFolderStorage().getTrashFolder());
            }
        };
        return executeWithRetryOnUnexpectedConnectionClosure(getTrashFolderCallable);
    }

    @Override
    public int getUnreadMessageCount(String folder) throws OXException {
        Callable<Integer> getUnreadMessageCountCallable = new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                String fullName = argument.getFullname();

                initConnection(argument.getAccountId());
                return I(getMailAccess().getUnreadMessagesCount(fullName));
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(getUnreadMessageCountCallable).intValue();
    }

    @Override
    public void openFor(String folder) throws OXException {
        if (null == folder) {
            // Nothing to do
            return;
        }

        FullnameArgument argument = prepareMailFolderParam(folder);
        initConnection(argument.getAccountId());
    }

    @Override
    public void applyAccess(MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> access) throws OXException {
        if (null != access) {
            if (!init) {
                mailAccess = initMailAccess(accountId, access);
                mailConfig = mailAccess.getMailConfig();
                this.accountId = access.getAccountId();
                init = true;
            } else if (this.accountId != access.getAccountId()) {
                mailAccess.close(true);
                mailAccess = initMailAccess(accountId, access);
                mailConfig = mailAccess.getMailConfig();
                this.accountId = access.getAccountId();
            }
        }
    }

    MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> initConnection(int accountId) throws OXException {
        if (MailAccount.DEFAULT_ID != accountId && session.containsParameter(Session.PARAM_IS_OAUTH)) {
            throw OAuthMailErrorCodes.NO_ACCOUNT_ACCESS.create();
        }
        try {
            if (!init) {
                mailAccess = initMailAccess(accountId);
                mailConfig = mailAccess.getMailConfig();
                this.accountId = accountId;
                init = true;
            } else if (accountId != mailAccess.getAccountId()) {
                mailAccess.close(true);
                mailAccess = initMailAccess(accountId);
                mailConfig = mailAccess.getMailConfig();
                this.accountId = accountId;
            }
        } catch (OXException e) {
            // Throw dedicated 403 oauth error in case the mail is not accessible. E.g. because master auth is not enabled
            if (MailExceptionCode.MISSING_CONNECT_PARAM.equals(e) && session.getParameter(Session.PARAM_OAUTH_ACCESS_TOKEN) != null && e.getArgument(MailConfig.MISSING_SESSION_PASSWORD) != null) {
                throw OAuthMailErrorCodes.NO_ACCOUNT_ACCESS.create(e);
            }
            throw e;
        }
        return mailAccess;
    }

    MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> initMailAccess(int accountId) throws OXException {
        return initMailAccess(accountId, null);
    }

    @SuppressWarnings("unchecked")
    private MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> initMailAccess(int accountId, MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> access) throws OXException {
        /*
         * Fetch a mail access (either from cache or a new instance)
         */
        MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> localMailAccess = null == access ? MailAccess.getInstance(session, accountId) : access;

        /**
         * Decorate the MailAccess with crypto functionality
         */
        if (doDecryption) {
            CryptographicAwareMailAccessFactory cryptoMailAccessFactory = Services.getServiceLookup().getOptionalService(CryptographicAwareMailAccessFactory.class);
            if (cryptoMailAccessFactory != null) {
                localMailAccess = cryptoMailAccessFactory.createAccess((MailAccess<IMailFolderStorage, IMailMessageStorage>) localMailAccess, session, cryptoAuthentication /* might be null in order to let the impl. obtain the authentication */);
            }
        }

        if (!localMailAccess.isConnected()) {
            /*
             * Get new mail configuration
             */
            long start = System.currentTimeMillis();
            try {
                localMailAccess.connect(true, debug);
                warnings.addAll(localMailAccess.getWarnings());
                MailServletInterface.mailInterfaceMonitor.addUseTime(System.currentTimeMillis() - start);
                MailServletInterface.mailInterfaceMonitor.changeNumSuccessfulLogins(true);
            } catch (OXException e) {
                if (MimeMailExceptionCode.LOGIN_FAILED.equals(e) || MimeMailExceptionCode.INVALID_CREDENTIALS.equals(e)) {
                    MailServletInterface.mailInterfaceMonitor.changeNumFailedLogins(true);
                }
                throw e;
            }
        }
        return localMailAccess;
    }

    boolean isDefaultFoldersChecked(int accountId) {
        Boolean b = MailSessionCache.getInstance(session).getParameter(accountId, MailSessionParameterNames.getParamDefaultFolderChecked());
        return (b != null) && b.booleanValue();
    }

    private ComposedMailMessage checkGuardEmail(ComposedMailMessage composedMailMessage) throws OXException {
        // Check if Guard email
        ComposedMailMessage composedMail = composedMailMessage;
        if (composedMail.getSecuritySettings() != null && composedMail.getSecuritySettings().anythingSet()) {
            EncryptedMailService encryptor = Services.getServiceLookup().getOptionalService(EncryptedMailService.class);
            if (encryptor != null) {
                composedMail = encryptor.encryptDraftEmail(composedMail, session, cryptoAuthentication);
            }
        }
        return (composedMail);
    }

    @Override
    public MailPath saveDraft(ComposedMailMessage draftMailMessage, boolean autosave, int accountId) throws OXException {
        if (autosave) {
            return autosaveDraft(draftMailMessage, accountId);
        }
        ComposedMailMessage draftMail = checkGuardEmail(draftMailMessage);
        initConnection(isTransportOnly(accountId) ? MailAccount.DEFAULT_ID : accountId);
        String draftFullname = mailAccess.getFolderStorage().getDraftsFolder();
        if (!draftMail.containsSentDate()) {
            draftMail.setSentDate(new Date());
        }
        MailMessage draftMessage = mailAccess.getMessageStorage().saveDraft(draftFullname, draftMail);
        if (null == draftMessage) {
            return null;
        }
        MailPath mailPath = draftMessage.getMailPath();
        if (null == mailPath) {
            return null;
        }
        postEvent(accountId, draftFullname, true);
        return mailPath;
    }

    private MailPath autosaveDraft(ComposedMailMessage draftMail, int accountId) throws OXException {
        initConnection(isTransportOnly(accountId) ? MailAccount.DEFAULT_ID : accountId);
        String draftFullname = mailAccess.getFolderStorage().getDraftsFolder();
        /*
         * Auto-save draft
         */
        if (!draftMail.isDraft()) {
            draftMail.setFlag(MailMessage.FLAG_DRAFT, true);
        }
        MailPath msgref = draftMail.getMsgref();
        MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> otherAccess = null;
        try {
            MailMessage origMail;
            if (null == msgref || !draftFullname.equals(msgref.getFolder())) {
                origMail = null;
            } else {
                if (msgref.getAccountId() == accountId) {
                    origMail = mailAccess.getMessageStorage().getMessage(msgref.getFolder(), msgref.getMailID(), false);
                } else {
                    otherAccess = MailAccess.getInstance(session, msgref.getAccountId());
                    otherAccess.connect(true, debug);
                    origMail = otherAccess.getMessageStorage().getMessage(msgref.getFolder(), msgref.getMailID(), false);
                }
                if (origMail != null) {
                    /*
                     * Check for attachments and add them
                     */
                    NonInlineForwardPartHandler handler = new NonInlineForwardPartHandler();
                    new MailMessageParser().parseMailMessage(origMail, handler);
                    List<MailPart> parts = handler.getNonInlineParts();
                    if (!parts.isEmpty()) {
                        TransportProvider tp = TransportProviderRegistry.getTransportProviderBySession(session, accountId);
                        for (MailPart mailPart : parts) {
                            /*
                             * Create and add a referenced part from original draft mail
                             */
                            draftMail.addEnclosedPart(tp.getNewReferencedPart(mailPart, session));
                        }
                    }
                }
            }
            String uid;
            {
                MailMessage filledMail = MimeMessageConverter.fillComposedMailMessage(draftMail);
                /*
                 * Encrypt the draft
                 */
                if (draftMail.getSecuritySettings() != null && draftMail.getSecuritySettings().anythingSet()) {
                    EncryptedMailService encryptor = Services.getServiceLookup().getOptionalService(EncryptedMailService.class);
                    if (encryptor != null) {
                        filledMail = encryptor.encryptAutosaveDraftEmail(filledMail, session, draftMail.getSecuritySettings());
                    }
                }

                filledMail.setFlag(MailMessage.FLAG_DRAFT, true);
                if (!filledMail.containsSentDate()) {
                    filledMail.setSentDate(new Date());
                }
                /*
                 * Append message to draft folder without invoking draftMail.cleanUp() afterwards to avoid loss of possibly uploaded images
                 */
                uid = mailAccess.getMessageStorage().appendMessages(draftFullname, new MailMessage[] { filledMail })[0];
            }
            if (null == uid) {
                return null;
            }
            /*
             * Check for draft-edit operation: Delete old version
             */
            if (origMail != null) {
                if (origMail.isDraft() && null != msgref) {
                    if (msgref.getAccountId() == accountId) {
                        mailAccess.getMessageStorage().deleteMessages(msgref.getFolder(), new String[] { msgref.getMailID() }, true);
                    } else if (null != otherAccess) {
                        otherAccess.getMessageStorage().deleteMessages(msgref.getFolder(), new String[] { msgref.getMailID() }, true);
                    }
                }
                draftMail.setMsgref(null);
            }
            /*
             * Return draft mail
             */
            MailMessage m = mailAccess.getMessageStorage().getMessage(draftFullname, uid, true);
            if (null == m) {
                throw MailExceptionCode.MAIL_NOT_FOUND.create(Long.valueOf(uid), draftFullname);
            }
            postEvent(accountId, draftFullname, true);
            return m.getMailPath();
        } finally {
            if (null != otherAccess) {
                otherAccess.close(true);
            }
        }
    }

    @Override
    public String saveFolder(MailFolderDescription mailFolder) throws OXException {
        if (!mailFolder.containsExists() && !mailFolder.containsFullname()) {
            throw MailExceptionCode.INSUFFICIENT_FOLDER_ATTR.create();
        }
        {
            String name = mailFolder.getName();
            if (null != name) {
                checkFolderName(name);
            }
        }
        if ((mailFolder.containsExists() && mailFolder.exists()) || ((mailFolder.getFullname() != null) && mailAccess.getFolderStorage().exists(mailFolder.getFullname()))) {
            /*
             * Update
             */
            int localAccountId = mailFolder.getAccountId();
            String fullName = mailFolder.getFullname();
            initConnection(localAccountId);
            char separator = mailFolder.getSeparator();
            String oldParent;
            String oldName;
            {
                int pos = fullName.lastIndexOf(separator);
                if (pos == -1) {
                    oldParent = "";
                    oldName = fullName;
                } else {
                    oldParent = fullName.substring(0, pos);
                    oldName = fullName.substring(pos + 1);
                }
            }
            boolean movePerformed = false;
            /*
             * Check if a move shall be performed
             */
            if (mailFolder.containsParentFullname()) {
                int parentAccountID = mailFolder.getParentAccountId();
                if (localAccountId == parentAccountID) {
                    String newParent = mailFolder.getParentFullname();
                    StringBuilder newFullname = new StringBuilder(newParent).append(mailFolder.getSeparator());
                    if (mailFolder.containsName()) {
                        newFullname.append(mailFolder.getName());
                    } else {
                        newFullname.append(oldName);
                    }
                    if (!newParent.equals(oldParent)) { // move & rename
                        Map<String, Map<?, ?>> subfolders = subfolders(fullName);
                        fullName = mailAccess.getFolderStorage().moveFolder(fullName, newFullname.toString());
                        movePerformed = true;
                        postEvent4Subfolders(localAccountId, subfolders);
                        postEventRemote(localAccountId, newParent, false, true);
                    }
                } else {
                    // Move to another account
                    MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> otherAccess = initMailAccess(parentAccountID);
                    try {
                        String newParent = mailFolder.getParentFullname();
                        // Check if parent mail folder exists
                        MailFolder p = otherAccess.getFolderStorage().getFolder(newParent);
                        // Check permission on new parent
                        MailPermission ownPermission = p.getOwnPermission();
                        if (!ownPermission.canCreateSubfolders()) {
                            throw MailExceptionCode.NO_CREATE_ACCESS.create(newParent);
                        }
                        // Check for duplicate
                        MailFolder[] tmp = otherAccess.getFolderStorage().getSubfolders(newParent, true);
                        String lookFor = mailFolder.containsName() ? mailFolder.getName() : oldName;
                        for (MailFolder sub : tmp) {
                            if (sub.getName().equals(lookFor)) {
                                throw MailExceptionCode.DUPLICATE_FOLDER.create(lookFor);
                            }
                        }
                        // Copy
                        String destFullname = fullCopy(mailAccess, fullName, otherAccess, newParent, p.getSeparator(), session.getUserId(), otherAccess.getMailConfig().getCapabilities().hasPermissions());
                        postEventRemote(parentAccountID, newParent, false, true);
                        // Delete source
                        Map<String, Map<?, ?>> subfolders = subfolders(fullName);
                        mailAccess.getFolderStorage().deleteFolder(fullName, true);
                        // Perform other updates
                        String prepareFullname = prepareFullname(parentAccountID, otherAccess.getFolderStorage().updateFolder(destFullname, mailFolder));
                        postEvent4Subfolders(localAccountId, subfolders);
                        return prepareFullname;
                    } finally {
                        otherAccess.close(true);
                    }
                }
            }
            /*
             * Check if a rename shall be performed
             */
            if (!movePerformed && mailFolder.containsName()) {
                String newName = mailFolder.getName();
                if (!newName.equals(oldName)) { // rename
                    fullName = mailAccess.getFolderStorage().renameFolder(fullName, newName);
                    postEventRemote(localAccountId, fullName, false, true);
                }
            }
            /*
             * Handle update of permission or subscription
             */
            String prepareFullname = prepareFullname(localAccountId, mailAccess.getFolderStorage().updateFolder(fullName, mailFolder));
            postEventRemote(localAccountId, fullName, false, true);
            return prepareFullname;
        }
        /*
         * Insert
         */
        int parentAccountId = mailFolder.getParentAccountId();
        initConnection(parentAccountId);
        String prepareFullname = prepareFullname(parentAccountId, mailAccess.getFolderStorage().createFolder(mailFolder));
        postEventRemote(parentAccountId, mailFolder.getParentFullname(), false, true);
        return prepareFullname;
    }

    private static String fullCopy(MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> srcAccess, String srcFullname, MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> destAccess, String destParent, char destSeparator, int user, boolean hasPermissions) throws OXException {
        // Create folder
        MailFolder source = srcAccess.getFolderStorage().getFolder(srcFullname);
        MailFolderDescription mfd = new MailFolderDescription();
        mfd.setName(source.getName());
        mfd.setParentFullname(destParent);
        mfd.setSeparator(destSeparator);
        mfd.setSubscribed(source.isSubscribed());
        if (hasPermissions) {
            // Copy permissions
            MailPermission[] perms = source.getPermissions();
            try {
                for (MailPermission perm : perms) {
                    mfd.addPermission((MailPermission) perm.clone());
                }
            } catch (CloneNotSupportedException e) {
                throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
            }
        }
        String destFullname = destAccess.getFolderStorage().createFolder(mfd);
        // Copy messages
        MailMessage[] msgs = srcAccess.getMessageStorage().getAllMessages(srcFullname, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, FIELDS_FULL);
        IMailMessageStorage destMessageStorage = destAccess.getMessageStorage();
        destMessageStorage.appendMessages(destFullname, msgs);
        MailFolder[] tmp = srcAccess.getFolderStorage().getSubfolders(srcFullname, true);
        for (MailFolder element : tmp) {
            fullCopy(srcAccess, element.getFullname(), destAccess, destFullname, destSeparator, user, hasPermissions);
        }
        return destFullname;
    }

    private final static String INVALID = "<>"; // "()<>@,;:\\\".[]";

    private static void checkFolderName(String name) throws OXException {
        if (com.openexchange.java.Strings.isEmpty(name)) {
            throw MailExceptionCode.INVALID_FOLDER_NAME_EMPTY.create();
        }
        int length = name.length();
        for (int i = 0; i < length; i++) {
            if (INVALID.indexOf(name.charAt(i)) >= 0) {
                throw MailExceptionCode.INVALID_FOLDER_NAME2.create(name, INVALID);
            }
        }
    }

    @Override
    public void sendFormMail(ComposedMailMessage composedMail, int groupId, int accountId) throws OXException {
        /*
         * Initialize
         */
        initConnection(accountId);
        MailTransport transport = MailTransport.getInstance(session, accountId);
        try {
            /*
             * Resolve group to users
             */
            final GroupService gs = ServerServiceRegistry.getServize(GroupService.class, true);
            Group group = gs.getGroup(ctx, groupId);
            int[] members = group.getMember();
            /*
             * Get user storage/contact interface to load user and its contact
             */
            UserStorage us = UserStorage.getInstance();
            ContactService contactService = ServerServiceRegistry.getInstance().getService(ContactService.class);
            /*
             * Needed variables
             */
            String content = (String) composedMail.getContent();
            StringBuilder builder = new StringBuilder(content.length() + 64);
            TransportProvider provider = TransportProviderRegistry.getTransportProviderBySession(session, accountId);
            Map<Locale, String> greetings = new HashMap<>(4);
            for (int userId : members) {
                User currentUser = us.getUser(userId, ctx);
                /*
                 * Get user's contact
                 */
                Contact contact = contactService.getUser(session, userId, new ContactField[] { ContactField.SUR_NAME, ContactField.GIVEN_NAME });
                /*
                 * Determine locale
                 */
                Locale userLocale = currentUser.getLocale();
                /*
                 * Compose text
                 */
                String greeting = greetings.get(userLocale);
                if (null == greeting) {
                    greeting = StringHelper.valueOf(userLocale).getString(MailStrings.GREETING);
                    greetings.put(userLocale, greeting);
                }
                builder.setLength(0);
                builder.append(greeting).append(' ');
                builder.append(contact.getGivenName()).append(' ').append(contact.getSurName());
                builder.append("<br><br>").append(content);
                TextBodyMailPart part = provider.getNewTextBodyPart(builder.toString());
                /*
                 * TODO: Clone composed mail?
                 */
                composedMail.setBodyPart(part);
                composedMail.removeTo();
                composedMail.removeBcc();
                composedMail.removeCc();
                composedMail.addTo(new QuotedInternetAddress(currentUser.getMail()));
                /*
                 * Finally send mail
                 */
                MailProperties properties = MailProperties.getInstance();
                if (isWhitelistedFromRateLimit(session.getLocalIp(), properties.getDisabledRateLimitRanges(session.getUserId(), session.getContextId()))) {
                    transport.sendMailMessage(composedMail, ComposeType.NEW);
                } else if (!properties.getRateLimitPrimaryOnly(session.getUserId(), session.getContextId()) || MailAccount.DEFAULT_ID == accountId) {
                    int rateLimit = properties.getRateLimit(session.getUserId(), session.getContextId());
                    rateLimitChecks(composedMail, rateLimit, properties.getMaxToCcBcc(session.getUserId(), session.getContextId()));
                    transport.sendMailMessage(composedMail, ComposeType.NEW);
                    setRateLimitTime(rateLimit);
                } else {
                    transport.sendMailMessage(composedMail, ComposeType.NEW);
                }
            }
        } catch (MessagingException e) {
            throw MimeMailException.handleMessagingException(e);
        } finally {
            transport.close();
        }
    }

    @Override
    public String sendMessage(ComposedMailMessage composedMail, ComposeType type, int accountId) throws OXException {
        return sendMessage(composedMail, type, accountId, UserSettingMailStorage.getInstance().getUserSettingMail(session.getUserId(), ctx));
    }

    @Override
    public String sendMessage(ComposedMailMessage composedMail, ComposeType type, int accountId, UserSettingMail optUserSetting) throws OXException {
        return sendMessage(composedMail, type, accountId, optUserSetting, null);
    }

    @Override
    public String sendMessage(ComposedMailMessage composedMail, ComposeType type, int accountId, UserSettingMail optUserSetting, MtaStatusInfo statusInfo) throws OXException {
        return sendMessage(composedMail, type, accountId, optUserSetting, statusInfo, null);
    }

    @Override
    public String sendMessage(ComposedMailMessage composedMail, ComposeType type, int accountId, UserSettingMail optUserSetting, MtaStatusInfo statusInfo, String remoteAddress) throws OXException {
        List<String> ids = sendMessages(Collections.singletonList(composedMail), null, false, type, accountId, optUserSetting, statusInfo, remoteAddress);
        return null == ids || ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public List<String> sendMessages(List<? extends ComposedMailMessage> transportMails, ComposedMailMessage mailToAppend, boolean transportEqualToSent, ComposeType type, int accountId, UserSettingMail optUserSetting, MtaStatusInfo statusInfo, String remoteAddress) throws OXException {
        // Initialize
        boolean accessAvailable = true;
        try {
            initConnection(isTransportOnly(accountId) ? MailAccount.DEFAULT_ID : accountId);
        } catch (OXException e) {
            if (!MailExceptionCode.MAIL_ACCESS_DISABLED.equals(e)) {
                throw e;
            }
            accessAvailable = false;
        }
        MailTransport transport = MailTransport.getInstance(session, accountId);
        try {
            // Invariants
            UserSettingMail currentUsm = null == optUserSetting ? UserSettingMailStorage.getInstance().getUserSettingMail(session.getUserId(), ctx) : optUserSetting;
            int numberOfTransportMails = transportMails.size();
            List<String> ids = new ArrayList<>(numberOfTransportMails);
            boolean settingsAllowAppendToSend = accessAvailable && !currentUsm.isNoCopyIntoStandardSentFolder();

            // State variables
            OXException failedAppend2Sent = null;
            OXException oxError = null;
            boolean first = true;
            String messageId = null;
            List<Set<InternetAddress>> unsentMails = new ArrayList<>(numberOfTransportMails);
            for (ComposedMailMessage composedMail : transportMails) {
                boolean mailSent = false;
                try {
                    /*
                     * Send mail
                     */
                    MailMessage sentMail;
                    Collection<InternetAddress> validRecipients = null;
                    long startTransport = System.currentTimeMillis();
                    try {
                        if (composedMail.isTransportToRecipients()) {
                            if (first) {
                                MailProperties properties = MailProperties.getInstance();
                                String remoteAddr = null == remoteAddress ? session.getLocalIp() : remoteAddress;
                                if (isWhitelistedFromRateLimit(remoteAddr, properties.getDisabledRateLimitRanges(session.getUserId(), session.getContextId()))) {
                                    sentMail = transport.sendMailMessage(composedMail, type, null, statusInfo);
                                } else if (!properties.getRateLimitPrimaryOnly(session.getUserId(), session.getContextId()) || MailAccount.DEFAULT_ID == accountId) {
                                    int rateLimit = properties.getRateLimit(session.getUserId(), session.getContextId());
                                    LOG.debug("Checking rate limit {} for request with IP {} ({}) from user {} in context {}", I(rateLimit), remoteAddr, null == remoteAddress ? "from session" : "from request", I(session.getUserId()), I(session.getContextId()));
                                    rateLimitChecks(composedMail, rateLimit, properties.getMaxToCcBcc(session.getUserId(), session.getContextId()));
                                    sentMail = transport.sendMailMessage(composedMail, type, null, statusInfo);
                                    setRateLimitTime(rateLimit);
                                } else {
                                    sentMail = transport.sendMailMessage(composedMail, type, null, statusInfo);
                                }
                                messageId = sentMail.getHeader(MESSAGE_ID, null);
                            } else {
                                composedMail.setHeader(MESSAGE_ID, messageId);
                                sentMail = transport.sendMailMessage(composedMail, type, null, statusInfo);
                            }
                            mailSent = true;
                        } else {
                            javax.mail.Address[] poison = new javax.mail.Address[] { MimeMessageUtility.POISON_ADDRESS };
                            sentMail = transport.sendMailMessage(composedMail, type, poison, statusInfo);
                        }
                    } catch (OXException e) {
                        if (!MimeMailExceptionCode.SEND_FAILED_EXT.equals(e) && !MimeMailExceptionCode.SEND_FAILED_MSG_ERROR.equals(e)) {
                            throw e;
                        }

                        MailMessage ma = (MailMessage) e.getArgument("sent_message");
                        if (null == ma) {
                            throw e;
                        }

                        sentMail = ma;
                        oxError = e;
                        mailSent = true;
                        if (e.getCause() instanceof SMTPSendFailedException) {
                            SMTPSendFailedException sendFailed = (SMTPSendFailedException) e.getCause();
                            Address[] validSentAddrs = sendFailed.getValidSentAddresses();
                            if (validSentAddrs != null && validSentAddrs.length > 0) {
                                validRecipients = new ArrayList<>(validSentAddrs.length);
                                for (Address validAddr : validSentAddrs) {
                                    validRecipients.add((InternetAddress) validAddr);
                                }
                            }
                        }
                    }

                    // Email successfully sent, trigger data retention
                    if (mailSent) {
                        DataRetentionService retentionService = ServerServiceRegistry.getInstance().getService(DataRetentionService.class);
                        if (null != retentionService) {
                            triggerDataRetention(transport, startTransport, sentMail, validRecipients, retentionService);
                        }
                    }

                    if (settingsAllowAppendToSend && composedMail.isAppendToSentFolder()) {
                        // If mail identifier and folder identifier is already available, assume it has already been stored in Sent folder
                        if (null != sentMail.getMailId() && null != sentMail.getFolder()) {
                            ids.add(new MailPath(accountId, sentMail.getFolder(), sentMail.getMailId()).toString());
                        } else {
                            ids.add(append2SentFolder(sentMail).toString());
                        }
                    }

                    // Append to Sent folder (prior to possible deletion of referenced mails)
                    if (first && settingsAllowAppendToSend && null != mailToAppend) {
                        // If mail identifier and folder identifier is already available, assume it has already been stored in Sent folder
                        if (transportEqualToSent) {
                            if (null != sentMail.getMailId() && null != sentMail.getFolder()) {
                                ids.add(new MailPath(accountId, sentMail.getFolder(), sentMail.getMailId()).toString());
                            } else {
                                ids.add(append2SentFolder(sentMail).toString());
                            }
                        } else {
                            try {
                                mailToAppend.setHeader(MESSAGE_ID, messageId);
                                ids.add(append2SentFolder(mailToAppend).toString());
                            } catch (OXException e) {
                                failedAppend2Sent = e;
                            }
                        }
                    }

                    // Check for a reply/forward
                    if (first && accessAvailable) {
                        try {
                            if (ComposeType.REPLY.equals(type)) {
                                setFlagReply(composedMail.getMsgref());
                            } else if (ComposeType.FORWARD.equals(type)) {
                                MailPath supPath = composedMail.getMsgref();
                                if (null == supPath) {
                                    int count = composedMail.getEnclosedCount();
                                    List<MailPath> paths = new LinkedList<>();
                                    for (int i = 0; i < count; i++) {
                                        MailPart part = composedMail.getEnclosedMailPart(i);
                                        MailPath path = part.getMsgref();
                                        if ((path != null) && part.getContentType().isMimeType(MimeTypes.MIME_MESSAGE_RFC822)) {
                                            paths.add(path);
                                        }
                                    }
                                    if (!paths.isEmpty()) {
                                        setFlagMultipleForward(paths);
                                    }
                                } else {
                                    setFlagForward(supPath);
                                }
                            } else if (ComposeType.DRAFT_NO_DELETE_ON_TRANSPORT.equals(type)) {
                                // Do not delete draft!
                            } else if (ComposeType.DRAFT.equals(type)) {
                                if (MailProperties.getInstance().isDeleteDraftOnTransport(session.getUserId(), session.getContextId())) {
                                    deleteDraft(composedMail.getMsgref());
                                }
                            } else if (ComposeType.DRAFT_DELETE_ON_TRANSPORT.equals(type)) {
                                try {
                                    deleteDraft(composedMail.getMsgref());
                                } catch (Exception e) {
                                    LOG.warn("Draft mail cannot be deleted.", e);
                                }
                            }
                        } catch (OXException e) {
                            mailAccess.addWarnings(Collections.singletonList(MailExceptionCode.FLAG_FAIL.create(e, new Object[0])));
                        }
                    }
                } catch (OXException e) {
                    if (!mailSent) {
                        if (numberOfTransportMails == 1) {
                            throw e;
                        }

                        Set<InternetAddress> recipients = new LinkedHashSet<>(Arrays.asList(composedMail.getAllRecipients()));
                        unsentMails.add(recipients);
                    }
                    e.setCategory(Category.CATEGORY_WARNING);
                    warnings.add(e);
                } catch (RuntimeException e) {
                    OXException oxe = MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
                    if (!mailSent) {
                        if (numberOfTransportMails == 1) {
                            throw oxe;
                        }

                        Set<InternetAddress> recipients = new LinkedHashSet<>(Arrays.asList(composedMail.getAllRecipients()));
                        unsentMails.add(recipients);
                    }
                    oxe.setCategory(Category.CATEGORY_WARNING);
                    warnings.add(oxe);
                }
                first = false;
            }

            if (numberOfTransportMails > 0 && unsentMails.size() == numberOfTransportMails) {
                Set<InternetAddress> recipients = new LinkedHashSet<>();
                for (Set<InternetAddress> addrs : unsentMails) {
                    recipients.addAll(addrs);
                }
                MimeMailExceptionCode.SEND_FAILED.create(recipients.toString());
            }

            if (!accessAvailable) {
                throw MailExceptionCode.COPY_TO_SENT_FOLDER_FAILED.create(new Object[0]);
            }

            if (null != failedAppend2Sent) {
                throw failedAppend2Sent;
            }

            if (null != oxError) {
                throw oxError;
            }

            return ids;
        } finally {
            transport.close();
        }
    }

    private void triggerDataRetention(MailTransport transport, long startTransport, MailMessage sentMail, Collection<InternetAddress> recipients, DataRetentionService retentionService) {
        /*
         * Create runnable task
         */
        final Session currentSession = this.session;
        final Logger logger = LOG;
        Runnable r = new Runnable() {

            @Override
            public void run() {
                try {
                    RetentionData retentionData = retentionService.newInstance();
                    retentionData.setStartTime(new Date(startTransport));
                    String login = transport.getTransportConfig().getLogin();
                    retentionData.setIdentifier(login);
                    retentionData.setIPAddress(currentSession.getLocalIp());
                    retentionData.setSenderAddress(IDNA.toIDN(sentMail.getFrom()[0].getAddress()));

                    Set<InternetAddress> recipientz;
                    if (null == recipients) {
                        recipientz = new HashSet<>(Arrays.asList(sentMail.getTo()));
                        recipientz.addAll(Arrays.asList(sentMail.getCc()));
                        recipientz.addAll(Arrays.asList(sentMail.getBcc()));
                    } else {
                        recipientz = new HashSet<>(recipients);
                    }

                    int size = recipientz.size();
                    String[] recipientsArr = new String[size];
                    Iterator<InternetAddress> it = recipientz.iterator();
                    for (int i = 0; i < size; i++) {
                        recipientsArr[i] = IDNA.toIDN(it.next().getAddress());
                    }
                    retentionData.setRecipientAddresses(recipientsArr);
                    /*
                     * Finally store it
                     */
                    retentionService.storeOnTransport(retentionData);
                } catch (OXException e) {
                    logger.error("", e);
                }
            }
        };
        /*
         * Check if timer service is available to delegate execution
         */
        ThreadPoolService threadPool = ThreadPools.getThreadPool();
        if (null == threadPool) {
            // Execute in this thread
            r.run();
        } else {
            // Delegate runnable to thread pool
            threadPool.submit(ThreadPools.task(r), CallerRunsBehavior.getInstance());
        }
    }

    private MailPath append2SentFolder(MailMessage sentMail) throws OXException {
        /*
         * Append to Sent folder
         */
        long start = System.currentTimeMillis();
        String sentFullname = mailAccess.getFolderStorage().getSentFolder();
        String[] uidArr;
        {
            IMailMessageStorage messageStorage = mailAccess.getMessageStorage();
            uidArr = doAppend2SentFolder(sentMail, sentFullname, messageStorage, true);
            postEventRemote(accountId, sentFullname, true, true);
            try {
                /*
                 * Update caches
                 */
                MailMessageCache.getInstance().removeFolderMessages(mailAccess.getAccountId(), sentFullname, session.getUserId(), contextId);
            } catch (OXException e) {
                LOG.error("", e);
            }
        }
        if ((uidArr != null) && (uidArr[0] != null)) {
            /*
             * Mark appended sent mail as seen
             */
            mailAccess.getMessageStorage().updateMessageFlags(sentFullname, uidArr, MailMessage.FLAG_SEEN, true);

            String[] userFlags = sentMail.getUserFlags();
            if (null != userFlags && userFlags.length > 0) {
                mailAccess.getMessageStorage().updateMessageUserFlags(sentFullname, uidArr, userFlags, true);
            }
        }
        MailPath retval = new MailPath(mailAccess.getAccountId(), sentFullname, (uidArr == null || uidArr.length == 0) ? null : uidArr[0]);
        LOG.debug("Mail copy ({}) appended in {}msec", retval, L(System.currentTimeMillis() - start));
        return retval;
    }

    private String[] doAppend2SentFolder(MailMessage sentMail, String sentFullname, IMailMessageStorage messageStorage, boolean retryOnCommunicationError) throws OXException {
        try {
            if (!(sentMail instanceof MimeRawSource)) {
                return messageStorage.appendMessages(sentFullname, new MailMessage[] { sentMail });
            }

            IMailMessageStorageMimeSupport mimeSupport = messageStorage.supports(IMailMessageStorageMimeSupport.class);
            if (null == mimeSupport) {
                return messageStorage.appendMessages(sentFullname, new MailMessage[] { sentMail });
            }

            if (mimeSupport.isMimeSupported()) {
                return mimeSupport.appendMimeMessages(sentFullname, new Message[] { (Message) ((MimeRawSource) sentMail).getPart() });
            }

            // Without MIME support...
            return messageStorage.appendMessages(sentFullname, new MailMessage[] { sentMail });
        } catch (OXException e) {
            if (retryOnCommunicationError && MimeMailException.isCommunicationException(e)) {
                close(false);
                initConnection(this.accountId);
                return doAppend2SentFolder(sentMail, sentFullname, messageStorage, false);
            }
            throw handleOXExceptionOnFailedAppend2SentFolder(sentMail, e);
        }
    }

    private OXException handleOXExceptionOnFailedAppend2SentFolder(MailMessage sentMail, OXException e) {
        if (e.getMessage().indexOf("quota") != -1) {
            return MailExceptionCode.COPY_TO_SENT_FOLDER_FAILED_QUOTA.create(e, new Object[0]);
        }
        LOG.warn("Mail with id {} in folder {} sent successfully, but a copy could not be placed in the sent folder.", sentMail.getMailId(), sentMail.getFolder(), e);
        return MailExceptionCode.COPY_TO_SENT_FOLDER_FAILED.create(e, new Object[0]);
    }

    private void setFlagForward(MailPath path) throws OXException {
        /*
         * Mark referenced mail as forwarded
         */
        String fullName = path.getFolder();
        String[] uids = new String[] { path.getMailID() };
        int pathAccount = path.getAccountId();
        if (mailAccess.getAccountId() == pathAccount) {
            mailAccess.getMessageStorage().updateMessageFlags(fullName, uids, MailMessage.FLAG_FORWARDED, true);
            try {
                if (MailMessageCache.getInstance().containsFolderMessages(mailAccess.getAccountId(), fullName, session.getUserId(), contextId)) {
                    /*
                     * Update cache entries
                     */
                    MailMessageCache.getInstance().updateCachedMessages(uids, mailAccess.getAccountId(), fullName, session.getUserId(), contextId, FIELDS_FLAGS, new Object[] { Integer.valueOf(MailMessage.FLAG_FORWARDED) });
                }
            } catch (OXException e) {
                LOG.error("", e);
            }
        } else {
            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> otherAccess = null;
            try {
                otherAccess = MailAccess.getInstance(session, pathAccount);
                otherAccess.connect(true, debug);
                otherAccess.getMessageStorage().updateMessageFlags(fullName, uids, MailMessage.FLAG_FORWARDED, true);
                try {
                    if (MailMessageCache.getInstance().containsFolderMessages(otherAccess.getAccountId(), fullName, session.getUserId(), contextId)) {
                        /*
                         * Update cache entries
                         */
                        MailMessageCache.getInstance().updateCachedMessages(uids, otherAccess.getAccountId(), fullName, session.getUserId(), contextId, FIELDS_FLAGS, new Object[] { Integer.valueOf(MailMessage.FLAG_FORWARDED) });
                    }
                } catch (OXException e) {
                    LOG.error("", e);
                }
            } finally {
                if (null != otherAccess) {
                    otherAccess.close(false);
                }
            }
        }
    }

    private void setFlagMultipleForward(List<MailPath> paths) throws OXException {
        String[] ids = new String[1];
        for (MailPath path : paths) {
            /*
             * Mark referenced mail as forwarded
             */
            ids[0] = path.getMailID();
            int pathAccount = path.getAccountId();
            if (mailAccess.getAccountId() == pathAccount) {
                mailAccess.getMessageStorage().updateMessageFlags(path.getFolder(), ids, MailMessage.FLAG_FORWARDED, true);
                try {
                    if (MailMessageCache.getInstance().containsFolderMessages(mailAccess.getAccountId(), path.getFolder(), session.getUserId(), contextId)) {
                        /*
                         * Update cache entries
                         */
                        MailMessageCache.getInstance().updateCachedMessages(ids, mailAccess.getAccountId(), path.getFolder(), session.getUserId(), contextId, FIELDS_FLAGS, new Object[] { Integer.valueOf(MailMessage.FLAG_FORWARDED) });
                    }
                } catch (OXException e) {
                    LOG.error("", e);
                }
            } else {
                MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> otherAccess = null;
                try {
                    otherAccess = MailAccess.getInstance(session, pathAccount);
                    otherAccess.connect(true, debug);
                    otherAccess.getMessageStorage().updateMessageFlags(path.getFolder(), ids, MailMessage.FLAG_FORWARDED, true);
                    try {
                        if (MailMessageCache.getInstance().containsFolderMessages(otherAccess.getAccountId(), path.getFolder(), session.getUserId(), contextId)) {
                            /*
                             * Update cache entries
                             */
                            MailMessageCache.getInstance().updateCachedMessages(ids, otherAccess.getAccountId(), path.getFolder(), session.getUserId(), contextId, FIELDS_FLAGS, new Object[] { Integer.valueOf(MailMessage.FLAG_FORWARDED) });
                        }
                    } catch (OXException e) {
                        LOG.error("", e);
                    }
                } finally {
                    if (null != otherAccess) {
                        otherAccess.close(false);
                    }
                }
            }
        }
    }

    private void deleteDraft(MailPath path) throws OXException {
        if (null == path) {
            LOG.warn("Missing msgref on draft-delete. Corresponding draft mail cannot be deleted.", new Throwable());
            return;
        }
        /*
         * Delete draft mail
         */
        String fullName = path.getFolder();
        String[] uids = new String[] { path.getMailID() };
        int pathAccount = path.getAccountId();
        if (mailAccess.getAccountId() == pathAccount) {
            mailAccess.getMessageStorage().deleteMessages(fullName, uids, true);
        } else {
            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> otherAccess = null;
            try {
                otherAccess = MailAccess.getInstance(session, pathAccount);
                otherAccess.connect(true, debug);
                otherAccess.getMessageStorage().deleteMessages(fullName, uids, true);
                try {
                    MailMessageCache.getInstance().removeMessages(uids, pathAccount, fullName, session.getUserId(), session.getContextId());
                } catch (OXException e) {
                    // Ignore
                }
            } finally {
                if (null != otherAccess) {
                    otherAccess.close(true);
                }
            }
        }
    }

    private void setFlagReply(MailPath path) throws OXException {
        if (null == path) {
            LOG.warn("Missing msgref on reply. Corresponding mail cannot be marked as answered.", new Throwable());
            return;
        }
        /*
         * Mark referenced mail as answered
         */
        String fullName = path.getFolder();
        String[] uids = new String[] { path.getMailID() };
        int pathAccount = path.getAccountId();
        if (mailAccess.getAccountId() == pathAccount) {
            mailAccess.getMessageStorage().updateMessageFlags(fullName, uids, MailMessage.FLAG_ANSWERED, true);
            try {
                /*
                 * Update JSON cache
                 */
                if (MailMessageCache.getInstance().containsFolderMessages(mailAccess.getAccountId(), fullName, session.getUserId(), contextId)) {
                    /*
                     * Update cache entries
                     */
                    MailMessageCache.getInstance().updateCachedMessages(uids, mailAccess.getAccountId(), fullName, session.getUserId(), contextId, FIELDS_FLAGS, new Object[] { Integer.valueOf(MailMessage.FLAG_ANSWERED) });
                }
            } catch (OXException e) {
                LOG.error("", e);
            }
        } else {
            /*
             * Mark as \Answered in foreign account
             */
            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> otherAccess = null;
            try {
                otherAccess = MailAccess.getInstance(session, pathAccount);
                otherAccess.connect(true, debug);
                otherAccess.getMessageStorage().updateMessageFlags(fullName, uids, MailMessage.FLAG_ANSWERED, true);
                try {
                    /*
                     * Update JSON cache
                     */
                    if (MailMessageCache.getInstance().containsFolderMessages(pathAccount, fullName, session.getUserId(), contextId)) {
                        /*
                         * Update cache entries
                         */
                        MailMessageCache.getInstance().updateCachedMessages(uids, pathAccount, fullName, session.getUserId(), contextId, FIELDS_FLAGS, new Object[] { Integer.valueOf(MailMessage.FLAG_ANSWERED) });
                    }
                } catch (OXException e) {
                    LOG.error("", e);
                }
            } finally {
                if (null != otherAccess) {
                    otherAccess.close(false);
                }
            }
        }
    }

    private void setRateLimitTime(int rateLimit) {
        if (rateLimit > 0) {
            session.setParameter(LAST_SEND_TIME, Long.valueOf(System.currentTimeMillis()));
        }
    }

    private void rateLimitChecks(MailMessage composedMail, int rateLimit, int maxToCcBcc) throws OXException {
        if (rateLimit > 0) {
            Long parameter = (Long) session.getParameter(LAST_SEND_TIME);
            if (null != parameter && (parameter.longValue() + rateLimit) >= System.currentTimeMillis()) {
                NumberFormat numberInstance = NumberFormat.getNumberInstance(getUserLocale());
                throw MailExceptionCode.SENT_QUOTA_EXCEEDED.create(numberInstance.format(((double) rateLimit) / 1000));
            }
        }
        if (maxToCcBcc > 0) {
            InternetAddress[] addrs = composedMail.getTo();
            int count = (addrs == null ? 0 : addrs.length);

            addrs = composedMail.getCc();
            count += (addrs == null ? 0 : addrs.length);

            addrs = composedMail.getBcc();
            count += (addrs == null ? 0 : addrs.length);

            if (count > maxToCcBcc) {
                throw MailExceptionCode.RECIPIENTS_EXCEEDED.create(Integer.valueOf(maxToCcBcc));
            }
        }
    }

    @Override
    public void sendReceiptAck(String folder, String msgUID, String fromAddr) throws OXException {
        FullnameArgument argument = prepareMailFolderParam(folder);
        int acc = argument.getAccountId();
        try {
            MailAccountStorageService ss = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class, true);
            MailAccount ma = ss.getMailAccount(acc, session.getUserId(), session.getContextId());
            if (ma.isDefaultAccount()) {
                /*
                 * Check for valid from address
                 */
                try {
                    Set<InternetAddress> validAddrs = new HashSet<>(4);
                    if (usm.getSendAddr() != null && usm.getSendAddr().length() > 0) {
                        validAddrs.add(new QuotedInternetAddress(usm.getSendAddr()));
                    }
                    User currentUser = getUser();
                    validAddrs.add(new QuotedInternetAddress(currentUser.getMail()));
                    for (String alias : currentUser.getAliases()) {
                        validAddrs.add(new QuotedInternetAddress(alias));
                    }
                    QuotedInternetAddress fromAddress = new QuotedInternetAddress(fromAddr);
                    if (MailProperties.getInstance().isSupportMsisdnAddresses()) {
                        MsisdnUtility.addMsisdnAddress(validAddrs, session);
                        String address = fromAddress.getAddress();
                        int pos = address.indexOf('/');
                        if (pos > 0) {
                            fromAddress.setAddress(address.substring(0, pos));
                        }
                    }
                    if (!validAddrs.contains(fromAddress)) {
                        throw MailExceptionCode.INVALID_SENDER.create(fromAddr);
                    }
                } catch (AddressException e) {
                    throw MimeMailException.handleMessagingException(e);
                }
            } else {
                if (!new QuotedInternetAddress(ma.getPrimaryAddress()).equals(new QuotedInternetAddress(fromAddr))) {
                    throw MailExceptionCode.INVALID_SENDER.create(fromAddr);
                }
            }
        } catch (AddressException e) {
            throw MimeMailException.handleMessagingException(e);
        }
        /*
         * Initialize
         */
        initConnection(acc);
        String fullName = argument.getFullname();
        MailTransport transport = MailTransport.getInstance(session);
        try {
            transport.sendReceiptAck(mailAccess.getMessageStorage().getMessage(fullName, msgUID, false), fromAddr);
        } finally {
            transport.close();
        }
        mailAccess.getMessageStorage().updateMessageFlags(fullName, new String[] { msgUID }, MailMessage.FLAG_READ_ACK, true);
    }

    private static final MailListField[] FIELDS_COLOR_LABEL = new MailListField[] { MailListField.COLOR_LABEL };

    private static final Map<String, Object> MORE_PROPS_UPDATE_LABEL;
    static {
        Map<String, Object> m = new HashMap<>(1, 1f);
        m.put("operation", "updateMessageColorLabel");
        MORE_PROPS_UPDATE_LABEL = ImmutableMap.copyOf(m);
    }

    @Override
    public void updateMessageColorLabel(String folder, String[] mailIDs, int newColorLabel) throws OXException {
        FullnameArgument argument = prepareMailFolderParam(folder);
        int folderAccountId = argument.getAccountId();
        initConnection(folderAccountId);
        String fullName = argument.getFullname();
        IMailMessageStorage messageStorage = mailAccess.getMessageStorage();
        String[] ids;
        if (null == mailIDs) {
            IMailMessageStorageBatch messageStorageBatch = messageStorage.supports(IMailMessageStorageBatch.class);
            if (null != messageStorageBatch) {
                ids = null;
                messageStorageBatch.updateMessageColorLabel(fullName, newColorLabel);
            } else {
                ids = getAllMessageIDs(argument);
                messageStorage.updateMessageColorLabel(fullName, ids, newColorLabel);
            }
        } else {
            ids = mailIDs;
            messageStorage.updateMessageColorLabel(fullName, ids, newColorLabel);
        }
        postEvent(PushEventConstants.TOPIC_ATTR, folderAccountId, fullName, true, true, false, MORE_PROPS_UPDATE_LABEL);
        /*
         * Update caches
         */
        try {
            if (MailMessageCache.getInstance().containsFolderMessages(folderAccountId, fullName, session.getUserId(), contextId)) {
                /*
                 * Update cache entries
                 */
                MailMessageCache.getInstance().updateCachedMessages(ids, folderAccountId, fullName, session.getUserId(), contextId, FIELDS_COLOR_LABEL, new Object[] { Integer.valueOf(newColorLabel) });
            }
        } catch (OXException e) {
            LOG.error("", e);
        }

        FlaggingMode mode = FlaggingMode.getFlaggingMode(session);
        if (mode.equals(FlaggingMode.FLAGGED_IMPLICIT)) {
            int flags = MailMessage.FLAG_FLAGGED;
            updateMessageFlags(folder, mailIDs, flags, newColorLabel != 0);
        }
    }

    @Override
    public String getMailIDByMessageID(String folder, String messageID) throws OXException {
        FullnameArgument argument = prepareMailFolderParam(folder);
        initConnection(argument.getAccountId());
        String fullName = argument.getFullname();
        MailMessage[] messages = mailAccess.getMessageStorage().searchMessages(fullName, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, new HeaderTerm("Message-Id", messageID), FIELDS_ID_INFO);
        if (null == messages || 1 != messages.length) {
            throw MailExceptionCode.MAIL_NOT_FOUN_BY_MESSAGE_ID.create(fullName, messageID);
        }
        return messages[0].getMailId();
    }

    private static final Map<String, Object> MORE_PROPS_UPDATE_FLAGS;
    static {
        Map<String, Object> m = new HashMap<>(1, 1f);
        m.put("operation", "updateMessageFlags");
        MORE_PROPS_UPDATE_FLAGS = ImmutableMap.copyOf(m);
    }

    @Override
    public void updateMessageFlags(String folder, String[] mailIDs, int flagBits, boolean flagVal) throws OXException {
        updateMessageFlags(folder, mailIDs, flagBits, ArrayUtils.EMPTY_STRING_ARRAY, flagVal);
    }

    @Override
    public void updateMessageFlags(String folder, String[] mailIDs, int flagBits, String[] userFlags, boolean flagVal) throws OXException {
        FullnameArgument argument = prepareMailFolderParam(folder);
        int folderAccountId = argument.getAccountId();
        initConnection(folderAccountId);
        String fullName = argument.getFullname();
        IMailMessageStorage messageStorage = mailAccess.getMessageStorage();
        String[] ids;
        if (null == mailIDs) {
            IMailMessageStorageBatch messageStorageBatch = messageStorage.supports(IMailMessageStorageBatch.class);
            if (null != messageStorageBatch) {
                ids = null;
                if (ArrayUtils.isEmpty(userFlags)) {
                    messageStorageBatch.updateMessageFlags(fullName, flagBits, flagVal);
                } else {
                    messageStorageBatch.updateMessageFlags(fullName, flagBits, userFlags, flagVal);
                }
            } else {
                ids = getAllMessageIDs(argument);
                if (ArrayUtils.isEmpty(userFlags)) {
                    messageStorage.updateMessageFlags(fullName, ids, flagBits, flagVal);
                } else {
                    messageStorage.updateMessageFlags(fullName, ids, flagBits, userFlags, flagVal);
                }

            }
        } else {
            ids = mailIDs;
            if (ArrayUtils.isEmpty(userFlags)) {
                messageStorage.updateMessageFlags(fullName, ids, flagBits, flagVal);
            } else {
                messageStorage.updateMessageFlags(fullName, ids, flagBits, userFlags, flagVal);
            }
        }
        postEvent(PushEventConstants.TOPIC_ATTR, folderAccountId, fullName, true, true, false, MORE_PROPS_UPDATE_FLAGS);
        boolean spamAction = (usm.isSpamEnabled() && ((flagBits & MailMessage.FLAG_SPAM) > 0));
        if (spamAction) {
            String spamFullname = mailAccess.getFolderStorage().getSpamFolder();
            postEvent(PushEventConstants.TOPIC_ATTR, folderAccountId, spamFullname, true, true);
        }
        /*
         * Update caches
         */
        if (spamAction) {
            /*
             * Remove from caches
             */
            try {
                if (MailMessageCache.getInstance().containsFolderMessages(folderAccountId, fullName, session.getUserId(), contextId)) {
                    MailMessageCache.getInstance().removeMessages(ids, folderAccountId, fullName, session.getUserId(), contextId);
                }
            } catch (OXException e) {
                LOG.error("", e);
            }
        } else {
            try {
                if (MailMessageCache.getInstance().containsFolderMessages(folderAccountId, fullName, session.getUserId(), contextId)) {
                    /*
                     * Update cache entries
                     */
                    MailMessageCache.getInstance().updateCachedMessages(ids, folderAccountId, fullName, session.getUserId(), contextId, FIELDS_FLAGS, new Object[] { Integer.valueOf(flagVal ? flagBits : (flagBits * -1)) });
                }
            } catch (OXException e) {
                LOG.error("", e);
            }
        }
    }

    @Override
    public MailMessage[] getUpdatedMessages(String folder, int[] fields) throws OXException {
        Callable<MailMessage[]> getUpdatedMessagesCallable = new Callable<MailMessage[]>() {

            @Override
            public MailMessage[] call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                MailFields mailFields = new MailFields(MailField.getFields(fields));
                checkFieldsForColorCheck(mailFields);
                MailFetchArguments fetchArguments = MailFetchArguments.builder(argument, mailFields.toArray(), null).build();
                Map<String, Object> fetchListenerState = new HashMap<>(4);
                MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, getMailAccess(), fetchListenerState);
                boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);
                if (notEmptyChain) {
                    MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, getMailAccess(), fetchListenerState);
                    if (attributation.isApplicable()) {
                        mailFields = new MailFields(attributation.getFields());
                    }
                }
                MailMessage[] mails = getMailAccess().getMessageStorage().getNewAndModifiedMessages(fullName, mailFields.toArray());
                if (notEmptyChain) {
                    MailFetchListenerResult result = listenerChain.onAfterFetch(mails, false, getMailAccess(), fetchListenerState);
                    if (ListenerReply.DENY == result.getReply()) {
                        OXException e = result.getError();
                        if (null == e) {
                            // Should not occur
                            e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                        }
                        throw e;
                    }
                    mails = result.getMails();
                }
                checkMailsForColor(mails);
                return mails;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(getUpdatedMessagesCallable);
    }

    @Override
    public MailMessage[] getDeletedMessages(String folder, int[] fields) throws OXException {
        Callable<MailMessage[]> getDeletedMessagesCallable = new Callable<MailMessage[]>() {

            @Override
            public MailMessage[] call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                initConnection(argument.getAccountId());
                String fullName = argument.getFullname();
                MailFields mailFields = new MailFields(MailField.getFields(fields));
                checkFieldsForColorCheck(mailFields);
                MailFetchArguments fetchArguments = MailFetchArguments.builder(argument, mailFields.toArray(), null).build();
                Map<String, Object> fetchListenerState = new HashMap<>(4);
                MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, getMailAccess(), fetchListenerState);
                boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);
                if (notEmptyChain) {
                    MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, getMailAccess(), fetchListenerState);
                    if (attributation.isApplicable()) {
                        mailFields = new MailFields(attributation.getFields());
                    }
                }
                MailMessage[] mails = getMailAccess().getMessageStorage().getDeletedMessages(fullName, mailFields.toArray());
                if (notEmptyChain) {
                    MailFetchListenerResult result = listenerChain.onAfterFetch(mails, false, getMailAccess(), fetchListenerState);
                    if (ListenerReply.DENY == result.getReply()) {
                        OXException e = result.getError();
                        if (null == e) {
                            // Should not occur
                            e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                        }
                        throw e;
                    }
                    mails = result.getMails();
                }
                checkMailsForColor(mails);
                return mails;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(getDeletedMessagesCallable);
    }

    /*-
     * ################################################################################
     * #############################   HELPER CLASSES   ###############################
     * ################################################################################
     */

    private static final class MailFolderComparator implements Comparator<MailFolder> {

        private final Map<String, Integer> indexMap;

        private final Collator collator;

        private final Integer na;

        public MailFolderComparator(String[] names, Locale locale) {
            super();
            indexMap = new HashMap<>(names.length);
            for (int i = 0; i < names.length; i++) {
                indexMap.put(names[i], Integer.valueOf(i));
            }
            na = Integer.valueOf(names.length);
            collator = Collators.getSecondaryInstance(locale);
        }

        private Integer getNumberOf(String name) {
            Integer ret = indexMap.get(name);
            if (null == ret) {
                return na;
            }
            return ret;
        }

        @Override
        public int compare(MailFolder o1, MailFolder o2) {
            if (o1.isDefaultFolder()) {
                if (o2.isDefaultFolder()) {
                    return getNumberOf(o1.getFullname()).compareTo(getNumberOf(o2.getFullname()));
                }
                return -1;
            }
            if (o2.isDefaultFolder()) {
                return 1;
            }
            return collator.compare(o1.getName(), o2.getName());
        }
    }

    private static final class SimpleMailFolderComparator implements Comparator<MailFolder> {

        private final Collator collator;

        public SimpleMailFolderComparator(Locale locale) {
            super();
            collator = Collators.getSecondaryInstance(locale);
        }

        @Override
        public int compare(MailFolder o1, MailFolder o2) {
            return collator.compare(o1.getName(), o2.getName());
        }
    }

    static String[] messages2ids(MailMessage[] messages) {
        if (null == messages) {
            return null;
        }
        List<String> retval = new ArrayList<>(messages.length);
        for (int i = 0; i < messages.length; i++) {
            MailMessage mail = messages[i];
            if (null != mail) {
                retval.add(mail.getMailId());
            }
        }
        return retval.toArray(new String[retval.size()]);
    }

    void postEvent(int accountId, String fullName, boolean contentRelated) {
        postEvent(accountId, fullName, contentRelated, false);
    }

    void postEventRemote(int accountId, String fullName, boolean contentRelated) {
        postEventRemote(accountId, fullName, contentRelated, false);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------- //

    void postEvent(int accountId, String fullName, boolean contentRelated, boolean immediateDelivery) {
        if (MailAccount.DEFAULT_ID != accountId) {
            /*
             * TODO: No event for non-primary account?
             */
            return;
        }
        EventPool.getInstance().put(new PooledEvent(contextId, session.getUserId(), accountId, prepareFullname(accountId, fullName), contentRelated, immediateDelivery, false, session));
    }

    private void postEventRemote(int accountId, String fullName, boolean contentRelated, boolean immediateDelivery) {
        if (MailAccount.DEFAULT_ID != accountId) {
            /*
             * TODO: No event for non-primary account?
             */
            return;
        }
        EventPool.getInstance().put(new PooledEvent(contextId, session.getUserId(), accountId, prepareFullname(accountId, fullName), contentRelated, immediateDelivery, true, session));
    }

    // ---------------------------------------------------------------------------------------------------------------------------------- //

    void postEvent(int accountId, String fullName, boolean contentRelated, boolean immediateDelivery, boolean async) {
        if (MailAccount.DEFAULT_ID != accountId) {
            /*
             * TODO: No event for non-primary account?
             */
            return;
        }
        EventPool.getInstance().put(new PooledEvent(contextId, session.getUserId(), accountId, prepareFullname(accountId, fullName), contentRelated, immediateDelivery, false, session).setAsync(async));
    }

    // ---------------------------------------------------------------------------------------------------------------------------------- //

    void postEvent(String topic, int accountId, String fullName, boolean contentRelated, boolean immediateDelivery) {
        if (MailAccount.DEFAULT_ID != accountId) {
            /*
             * TODO: No event for non-primary account?
             */
            return;
        }
        EventPool.getInstance().put(new PooledEvent(topic, contextId, session.getUserId(), accountId, prepareFullname(accountId, fullName), contentRelated, immediateDelivery, false, session));
    }

    // ---------------------------------------------------------------------------------------------------------------------------------- //

    private void postEvent(String topic, int accountId, String fullName, boolean contentRelated, boolean immediateDelivery, boolean async, Map<String, Object> moreProperties) {
        if (MailAccount.DEFAULT_ID != accountId) {
            /*
             * TODO: No event for non-primary account?
             */
            return;
        }
        PooledEvent pooledEvent = new PooledEvent(topic, contextId, session.getUserId(), accountId, prepareFullname(accountId, fullName), contentRelated, immediateDelivery, false, session);
        if (null != moreProperties) {
            for (Entry<String, Object> entry : moreProperties.entrySet()) {
                pooledEvent.putProperty(entry.getKey(), entry.getValue());
            }
        }
        EventPool.getInstance().put(pooledEvent.setAsync(async));
    }

    @Override
    public MailImportResult[] getMailImportResults() {
        MailImportResult[] mars = new MailImportResult[mailImportResults.size()];
        for (int i = 0; i < mars.length; i++) {
            mars[i] = mailImportResults.get(i);
        }

        return mars;
    }

    private String[] getAllMessageIDs(FullnameArgument argument) throws OXException {
        initConnection(argument.getAccountId());
        String fullName = argument.getFullname();
        MailMessage[] mails = mailAccess.getMessageStorage().searchMessages(fullName, null, MailSortField.RECEIVED_DATE, OrderDirection.ASC, null, FIELDS_ID_INFO);
        if ((mails == null) || (mails.length == 0)) {
            return new String[0];
        }
        String[] ret = new String[mails.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = mails[i].getMailId();
        }
        return ret;
    }

    private static final int SUBFOLDERS_NOT_ALLOWED_ERROR_CODE = 2012;
    private static final String SUBFOLDERS_NOT_ALLOWED_PREFIX = "IMAP";

    @Override
    public void archiveMailFolder(int days, String folderID, ServerSession session, boolean useDefaultName, boolean createIfAbsent) throws OXException {
        Callable<Void> archiveMailFolderCallable = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                try {
                    FullnameArgument fa = MailFolderUtility.prepareMailFolderParam(folderID);
                    int folderAccountId = fa.getAccountId();
                    initConnection(folderAccountId);

                    // Check archive full name
                    int[] separatorRef = new int[1];
                    String archiveFullname = checkArchiveFullNameFor(session, separatorRef, useDefaultName, createIfAbsent);
                    char separator = (char) separatorRef[0];

                    // Check location
                    {
                        String fullName = fa.getFullname();
                        if (fullName.equals(archiveFullname) || fullName.startsWith(archiveFullname + separator)) {
                            return null;
                        }
                    }

                    // Move to archive folder
                    Calendar cal = Calendar.getInstance(TimeZoneUtils.getTimeZone("UTC"));
                    cal.set(Calendar.MILLISECOND, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.add(Calendar.DATE, days * -1);

                    ReceivedDateTerm term = new ReceivedDateTerm(ComparisonType.LESS_THAN, cal.getTime());
                    MailMessage[] msgs = getMailAccess().getMessageStorage().searchMessages(fa.getFullname(), null, MailSortField.RECEIVED_DATE, OrderDirection.DESC, term, new MailField[] { MailField.ID, MailField.RECEIVED_DATE });
                    if (null == msgs || msgs.length <= 0) {
                        return null;
                    }

                    Map<Integer, List<String>> map = new HashMap<>(4);
                    for (MailMessage mailMessage : msgs) {
                        Date receivedDate = mailMessage.getReceivedDate();
                        cal.setTime(receivedDate);
                        Integer year = Integer.valueOf(cal.get(Calendar.YEAR));
                        List<String> ids = map.get(year);
                        if (null == ids) {
                            ids = new LinkedList<>();
                            map.put(year, ids);
                        }
                        ids.add(mailMessage.getMailId());
                    }

                    for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
                        String sYear = entry.getKey().toString();
                        String fn = archiveFullname + separator + sYear;
                        if (!getMailAccess().getFolderStorage().exists(fn)) {
                            final MailFolderDescription toCreate = new MailFolderDescription();
                            toCreate.setAccountId(folderAccountId);
                            toCreate.setParentAccountId(folderAccountId);
                            toCreate.setParentFullname(archiveFullname);
                            toCreate.setExists(false);
                            toCreate.setFullname(fn);
                            toCreate.setName(sYear);
                            toCreate.setSeparator(separator);
                            {
                                final DefaultMailPermission mp = new DefaultMailPermission();
                                mp.setEntity(session.getUserId());
                                final int p = MailPermission.ADMIN_PERMISSION;
                                mp.setAllPermission(p, p, p, p);
                                mp.setFolderAdmin(true);
                                mp.setGroupPermission(false);
                                toCreate.addPermission(mp);
                            }
                            try {
                                getMailAccess().getFolderStorage().createFolder(toCreate);
                            } catch (OXException e) {
                                if (SUBFOLDERS_NOT_ALLOWED_PREFIX.equals(e.getPrefix()) && e.getCode() == SUBFOLDERS_NOT_ALLOWED_ERROR_CODE) {
                                    if (getMailAccess().getFolderStorage().exists(archiveFullname)) {
                                        fn = archiveFullname;
                                    } else {
                                        throw MailExceptionCode.ARCHIVE_SUBFOLDER_NOT_ALLOWED.create(e);
                                    }
                                } else {
                                    throw e;
                                }
                            }
                            CacheFolderStorage.getInstance().removeFromCache(MailFolderUtility.prepareFullname(folderAccountId, archiveFullname), "0", true, session);
                        }

                        List<String> ids = entry.getValue();
                        getMailAccess().getMessageStorage().moveMessages(fa.getFullname(), fn, ids.toArray(new String[ids.size()]), true);
                    }

                    return null;
                } catch (RuntimeException e) {
                    throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
                } catch (OXException e) {
                    if (SUBFOLDERS_NOT_ALLOWED_PREFIX.equals(e.getPrefix()) && e.getCode() == SUBFOLDERS_NOT_ALLOWED_ERROR_CODE) {
                        throw MailExceptionCode.ARCHIVE_SUBFOLDER_NOT_ALLOWED.create(e);
                    }
                    throw e;
                }
            }
        };

        executeWithRetryOnUnexpectedConnectionClosure(archiveMailFolderCallable);
    }

    @Override
    public List<ArchiveDataWrapper> archiveMail(String folderID, List<String> ids, ServerSession session, boolean useDefaultName, boolean createIfAbsent) throws OXException {
        Callable<List<ArchiveDataWrapper>> archiveMailCallable = new Callable<List<ArchiveDataWrapper>>() {

            @Override
            public List<ArchiveDataWrapper> call() throws Exception {
                final List<ArchiveDataWrapper> retval = new ArrayList<>();

                // Expect array of identifiers: ["1234","1235",...,"1299"]
                FullnameArgument fa = MailFolderUtility.prepareMailFolderParam(folderID);
                initConnection(fa.getAccountId());

                // Check archive full name
                int[] separatorRef = new int[1];
                String archiveFullname = checkArchiveFullNameFor(session, separatorRef, useDefaultName, createIfAbsent);
                char separator = (char) separatorRef[0];

                // Check location
                {
                    String fullName = fa.getFullname();
                    if (fullName.equals(archiveFullname) || fullName.startsWith(archiveFullname + separator)) {
                        return null;
                    }
                }

                String fullName = fa.getFullname();
                MailMessage[] msgs = getMailAccess().getMessageStorage().getMessages(fullName, ids.toArray(new String[ids.size()]), new MailField[] { MailField.ID, MailField.RECEIVED_DATE });
                if (null == msgs || msgs.length <= 0) {
                    return null;
                }
                try {
                    move2Archive(msgs, fullName, archiveFullname, separator, retval);
                } catch (OXException e) {
                    if (SUBFOLDERS_NOT_ALLOWED_PREFIX.equals(e.getPrefix()) && e.getCode() == SUBFOLDERS_NOT_ALLOWED_ERROR_CODE) {
                        throw MailExceptionCode.ARCHIVE_SUBFOLDER_NOT_ALLOWED.create(e);
                    }
                    throw e;
                }
                return retval;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(archiveMailCallable);
    }

    @Override
    public List<ArchiveDataWrapper> archiveMultipleMail(List<FolderAndId> entries, ServerSession session, boolean useDefaultName, boolean createIfAbsent) throws OXException {
        // Expect array of objects: [{"folder":"INBOX/foo", "id":"1234"},{"folder":"INBOX/foo", "id":"1235"},...,{"folder":"INBOX/bar", "id":"1299"}]
        TIntObjectMap<Map<String, List<String>>> m = new TIntObjectHashMap<>(2);
        for (FolderAndId obj : entries) {

            FullnameArgument fa = MailFolderUtility.prepareMailFolderParam(obj.getFolderId());
            int folderAccountId = fa.getAccountId();

            Map<String, List<String>> map = m.get(folderAccountId);
            if (null == map) {
                map = new HashMap<>();
                m.put(folderAccountId, map);
            }

            String fullName = fa.getFullname();
            List<String> list = map.get(fullName);
            if (null == list) {
                list = new LinkedList<>();
                map.put(fullName, list);
            }

            list.add(obj.getMailId());
        }

        // Iterate map
        final List<ArchiveDataWrapper> retval = new ArrayList<>();
        final Reference<OXException> exceptionRef = new Reference<>();
        final Calendar cal = Calendar.getInstance(TimeZoneUtils.getTimeZone("UTC"));
        boolean success = m.forEachEntry(new TIntObjectProcedure<Map<String, List<String>>>() {

            @Override
            public boolean execute(int accountId, Map<String, List<String>> mapping) {
                boolean proceed = false;
                try {
                    Callable<Void> archiveMailsForAccountCallable = new Callable<Void>() {

                        @Override
                        public Void call() throws Exception {
                            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> mailAccess = initConnection(accountId);

                            // Check archive full name
                            int[] separatorRef = new int[1];
                            String archiveFullname = checkArchiveFullNameFor(session, separatorRef, useDefaultName, createIfAbsent);
                            char separator = (char) separatorRef[0];

                            // Move to archive folder
                            for (Map.Entry<String, List<String>> mappingEntry : mapping.entrySet()) {
                                String fullName = mappingEntry.getKey();

                                // Check location
                                if (!fullName.equals(archiveFullname) && !fullName.startsWith(archiveFullname + separator)) {
                                    List<String> mailIds = mappingEntry.getValue();

                                    MailMessage[] msgs = mailAccess.getMessageStorage().getMessages(fullName, mailIds.toArray(new String[mailIds.size()]), new MailField[] { MailField.ID, MailField.RECEIVED_DATE });
                                    if (null != msgs && msgs.length > 0) {
                                        move2Archive(msgs, fullName, archiveFullname, separator, cal, retval);
                                    }
                                }
                            }

                            // Return from callable call
                            return null;
                        }
                    };

                    executeWithRetryOnUnexpectedConnectionClosure(archiveMailsForAccountCallable);

                    proceed = true;
                } catch (OXException e) {
                    if (SUBFOLDERS_NOT_ALLOWED_PREFIX.equals(e.getPrefix()) && e.getCode() == SUBFOLDERS_NOT_ALLOWED_ERROR_CODE) {
                        exceptionRef.setValue(MailExceptionCode.ARCHIVE_SUBFOLDER_NOT_ALLOWED.create(e));
                    } else {
                        exceptionRef.setValue(e);
                    }
                } catch (RuntimeException e) {
                    exceptionRef.setValue(new OXException(e));
                }
                return proceed;
            }
        });

        if (!success) {
            throw exceptionRef.getValue();
        }

        return retval;
    }

    void move2Archive(MailMessage[] msgs, String fullName, String archiveFullname, char separator, List<ArchiveDataWrapper> result) throws OXException {
        Calendar cal = Calendar.getInstance(TimeZoneUtils.getTimeZone("UTC"));
        move2Archive(msgs, fullName, archiveFullname, separator, cal, result);
    }

    void move2Archive(MailMessage[] msgs, String fullName, String archiveFullname, char separator, Calendar cal, List<ArchiveDataWrapper> result) throws OXException {
        Map<Integer, List<String>> map = new HashMap<>(4);
        for (MailMessage mailMessage : msgs) {
            if (mailMessage == null) {
                continue;
            }
            Date receivedDate = mailMessage.getReceivedDate();
            cal.setTime(receivedDate);
            Integer year = Integer.valueOf(cal.get(Calendar.YEAR));
            List<String> ids = map.get(year);
            if (null == ids) {
                ids = new LinkedList<>();
                map.put(year, ids);
            }
            ids.add(mailMessage.getMailId());
        }

        int currentAccountId = mailAccess.getAccountId();
        Session currentSession = mailAccess.getSession();
        for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
            String sYear = entry.getKey().toString();
            String fn = archiveFullname + separator + sYear;
            StringBuilder sb = new StringBuilder("default").append(mailAccess.getAccountId()).append(separator).append(fn);
            boolean exists = mailAccess.getFolderStorage().exists(fn);
            result.add(new ArchiveDataWrapper(sb.toString(), !exists));
            if (!exists) {
                final MailFolderDescription toCreate = new MailFolderDescription();
                toCreate.setAccountId(currentAccountId);
                toCreate.setParentAccountId(currentAccountId);
                toCreate.setParentFullname(archiveFullname);
                toCreate.setExists(false);
                toCreate.setFullname(fn);
                toCreate.setName(sYear);
                toCreate.setSeparator(separator);
                {
                    final DefaultMailPermission mp = new DefaultMailPermission();
                    mp.setEntity(currentSession.getUserId());
                    final int p = MailPermission.ADMIN_PERMISSION;
                    mp.setAllPermission(p, p, p, p);
                    mp.setFolderAdmin(true);
                    mp.setGroupPermission(false);
                    toCreate.addPermission(mp);
                }
                try {
                    mailAccess.getFolderStorage().createFolder(toCreate);
                } catch (OXException e) {
                    if (SUBFOLDERS_NOT_ALLOWED_PREFIX.equals(e.getPrefix()) && e.getCode() == SUBFOLDERS_NOT_ALLOWED_ERROR_CODE) {
                        //Using parent folder as fallback
                        if (mailAccess.getFolderStorage().exists(archiveFullname)) {
                            fn = archiveFullname;
                        } else {
                            throw e;
                        }
                    } else {
                        throw e;
                    }
                }
                CacheFolderStorage.getInstance().removeFromCache(MailFolderUtility.prepareFullname(currentAccountId, archiveFullname), "0", true, currentSession);
            }

            List<String> ids = entry.getValue();
            mailAccess.getMessageStorage().moveMessages(fullName, fn, ids.toArray(new String[ids.size()]), true);
        }
    }

    /**
     * Checks the archive full name for given arguments
     *
     * @param session
     * @param separatorRef
     * @param useDefaultName
     * @param createIfAbsent
     * @return The archive full name
     * @throws OXException If checking archive full name fails
     */
    String checkArchiveFullNameFor(ServerSession session, int[] separatorRef, boolean useDefaultName, boolean createIfAbsent) throws OXException {
        final int currentAccountId = mailAccess.getAccountId();

        MailAccountStorageService service = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class);
        if (null == service) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(MailAccountStorageService.class.getName());
        }
        MailAccount mailAccount = service.getMailAccount(currentAccountId, session.getUserId(), session.getContextId());

        // Check archive full name
        char separator;
        String archiveFullName = mailAccount.getArchiveFullname();
        final String parentFullName;
        String archiveName;
        if (Strings.isEmpty(archiveFullName)) {
            archiveName = mailAccount.getArchive();
            boolean updateAccount = false;
            if (Strings.isEmpty(archiveName)) {
                final User currentUser = session.getUser();
                if (!useDefaultName) {
                    final String i18nArchive = StringHelper.valueOf(currentUser.getLocale()).getString(MailStrings.ARCHIVE);
                    throw MailExceptionCode.MISSING_DEFAULT_FOLDER_NAME.create(Category.CATEGORY_USER_INPUT, i18nArchive);
                }
                // Select default name for archive folder
                archiveName = StringHelper.valueOf(currentUser.getLocale()).getString(MailStrings.DEFAULT_ARCHIVE);
                updateAccount = true;
            }
            final String prefix = mailAccess.getFolderStorage().getDefaultFolderPrefix();
            if (Strings.isEmpty(prefix)) {
                separator = mailAccess.getFolderStorage().getFolder(INBOX_ID).getSeparator();
                archiveFullName = archiveName;
                parentFullName = MailFolder.ROOT_FOLDER_ID;
            } else {
                separator = prefix.charAt(prefix.length() - 1);
                archiveFullName = new StringBuilder(prefix).append(archiveName).toString();
                parentFullName = prefix.substring(0, prefix.length() - 1);
            }
            // Update mail account
            if (updateAccount) {
                final MailAccountStorageService mass = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class);
                if (null != mass) {
                    final String af = archiveFullName;
                    ThreadPools.getThreadPool().submit(new AbstractTask<Void>() {

                        @Override
                        public Void call() throws Exception {
                            final MailAccountDescription mad = new MailAccountDescription();
                            mad.setId(currentAccountId);
                            mad.setArchiveFullname(af);
                            mass.updateMailAccount(mad, EnumSet.of(Attribute.ARCHIVE_FULLNAME_LITERAL), session.getUserId(), session.getContextId(), session);
                            return null;
                        }
                    });
                }
            }
        } else {
            separator = mailAccess.getFolderStorage().getFolder(INBOX_ID).getSeparator();
            final int pos = archiveFullName.lastIndexOf(separator);
            if (pos > 0) {
                parentFullName = archiveFullName.substring(0, pos);
                archiveName = archiveFullName.substring(pos + 1);
            } else {
                parentFullName = MailFolder.ROOT_FOLDER_ID;
                archiveName = archiveFullName;
            }
        }
        if (!mailAccess.getFolderStorage().exists(archiveFullName)) {
            if (!createIfAbsent) {
                throw MailExceptionCode.FOLDER_NOT_FOUND.create(archiveFullName);
            }
            final MailFolderDescription toCreate = new MailFolderDescription();
            toCreate.setAccountId(currentAccountId);
            toCreate.setParentAccountId(currentAccountId);
            toCreate.setParentFullname(parentFullName);
            toCreate.setExists(false);
            toCreate.setFullname(archiveFullName);
            toCreate.setName(archiveName);
            toCreate.setSeparator(separator);
            {
                final DefaultMailPermission mp = new DefaultMailPermission();
                mp.setEntity(session.getUserId());
                final int p = MailPermission.ADMIN_PERMISSION;
                mp.setAllPermission(p, p, p, p);
                mp.setFolderAdmin(true);
                mp.setGroupPermission(false);
                toCreate.addPermission(mp);
            }
            mailAccess.getFolderStorage().createFolder(toCreate);
            CacheFolderStorage.getInstance().removeFromCache(parentFullName, "0", true, session);
        }

        separatorRef[0] = separator;
        return archiveFullName;
    }

    /**
     * Checks if specified IP address is contained in given collection of IP address ranges
     *
     * @param actual The IP address to check
     * @param ranges The collection of IP address ranges
     * @return <code>true</code> if contained; otherwise <code>false</code>
     */
    private static boolean isWhitelistedFromRateLimit(String actual, HostList ranges) {
        if (Strings.isEmpty(actual)) {
            return false;
        }

        return ranges.contains(actual);
    }

    private boolean isTransportOnly(int accountId) throws OXException {
        MailAccountStorageService storageService = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class);
        return (null != storageService) && (false == storageService.existsMailAccount(accountId, session.getUserId(), session.getContextId()));
    }

    @Override
    public MailMessage[] searchMails(String folder, IndexRange indexRange, MailSortField sortField, OrderDirection order, SearchTerm<?> searchTerm, MailField[] fields, String[] headers) throws OXException {
        Callable<MailMessage[]> searchMailsCallable = new Callable<MailMessage[]>() {

            @Override
            public MailMessage[] call() throws Exception {
                FullnameArgument argument = prepareMailFolderParam(folder);
                String fullName = argument.getFullname();
                initConnection(argument.getAccountId());

                MailField[] mailFields = fields;
                String[] headerNames = headers;

                MailFetchArguments fetchArguments = MailFetchArguments.builder(argument, mailFields, headerNames).build();
                Map<String, Object> fetchListenerState = new HashMap<>(4);
                MailFetchListenerChain listenerChain = MailFetchListenerRegistry.determineFetchListenerChainFor(fetchArguments, getMailAccess(), fetchListenerState);
                boolean notEmptyChain = MailFetchListenerChain.isNotEmptyChain(listenerChain);

                if (notEmptyChain) {
                    MailAttributation attributation = listenerChain.onBeforeFetch(fetchArguments, getMailAccess(), fetchListenerState);
                    if (attributation.isApplicable()) {
                        mailFields = attributation.getFields();
                        headerNames = attributation.getHeaderNames();
                    }
                }

                IMailMessageStorage messageStorage = getMailAccess().getMessageStorage();
                MailMessage[] mails;
                if (null != headerNames && 0 < headerNames.length) {
                    IMailMessageStorageExt ext = messageStorage.supports(IMailMessageStorageExt.class);
                    if (null != ext) {
                        mails = ext.searchMessages(fullName, indexRange, sortField, order, searchTerm, mailFields, headerNames);
                    } else {
                        mails = messageStorage.searchMessages(fullName, indexRange, sortField, order, searchTerm, mailFields);
                        enrichWithHeaders(fullName, mails, headerNames, messageStorage);
                    }
                } else {
                    mails = messageStorage.searchMessages(fullName, indexRange, sortField, order, searchTerm, mailFields);
                }

                if (!getMailAccess().getWarnings().isEmpty()) {
                    warnings.addAll(getMailAccess().getWarnings());
                }

                if (notEmptyChain) {
                    MailFetchListenerResult result = listenerChain.onAfterFetch(mails, false, getMailAccess(), fetchListenerState);
                    if (ListenerReply.DENY == result.getReply()) {
                        OXException e = result.getError();
                        if (null == e) {
                            // Should not occur
                            e = MailExceptionCode.UNEXPECTED_ERROR.create("Fetch listener processing failed");
                        }
                        throw e;
                    }
                    mails = result.getMails();
                }

                checkMailsForColor(mails);

                return mails;
            }
        };

        return executeWithRetryOnUnexpectedConnectionClosure(searchMailsCallable);
    }

    void enrichWithHeaders(String fullName, MailMessage[] mails, String[] headerNames, IMailMessageStorage messageStorage) throws OXException {
        int length = mails.length;
        MailMessage[] headers;
        {
            String[] ids = new String[length];
            for (int i = ids.length; i-- > 0;) {
                MailMessage m = mails[i];
                ids[i] = null == m ? null : m.getMailId();
            }
            headers = messageStorage.getMessages(fullName, ids, MailFields.toArray(MailField.HEADERS));
        }

        for (int i = length; i-- > 0;) {
            MailMessage mailMessage = mails[i];
            if (null != mailMessage) {
                MailMessage header = headers[i];
                if (null != header) {
                    for (String headerName : headerNames) {
                        String[] values = header.getHeader(headerName);
                        if (null != values) {
                            for (String value : values) {
                                mailMessage.addHeader(headerName, value);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes given callable with one-time retry behavior on connect error.
     *
     * @param <V> The type of the return value
     * @param callable The callable to execute
     * @return The return value
     * @throws OXException If a non-retriable exception occurs or retry attempts are expired
     */
    static <V> V executeWithRetryOnUnexpectedConnectionClosure(Callable<V> callable) throws OXException {
        try {
            return Failsafe.with(RETRY_POLICY_CONNECTION_CLOSED).onRetry(ON_RETRY_LISTENER_CONNECTION_CLOSED).get(callable);
        } catch (FailsafeException e) {
            // Checked exception occurred
            Throwable cause = e.getCause();
            if (cause instanceof OXException) {
                throw (OXException) cause;
            }

            Throwable t = cause == null ? e : cause;
            throw MailExceptionCode.UNEXPECTED_ERROR.create(t, t.getMessage());
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("Invalid fully-qualifying mail folder identifier: ")) {
                // Apparently, passed folder identifier is invalid
                throw MailExceptionCode.INVALID_FOLDER_IDENTIFIER.create(e, message.substring(50));
            }
            // Unchecked exception occurred
            throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            // Unchecked exception occurred
            throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    private static final RetryPolicy RETRY_POLICY_CONNECTION_CLOSED = new RetryPolicy()
        .withMaxRetries(1).withBackoff(1, 10, TimeUnit.SECONDS).withJitter(0.25f)
        .retryOn(t -> OXException.class.isInstance(t) && MimeMailExceptionCode.CONNECTION_CLOSED.equals((OXException) t));

    private static final CheckedConsumer<Throwable> ON_RETRY_LISTENER_CONNECTION_CLOSED = new CheckedConsumer<Throwable>() {

        @Override
        public void accept(Throwable t) throws Exception {
            LOG.debug("Mail server closed connection unexpectedly", t);
        }
    };

}
