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

package com.openexchange.mail.mime.processing;

import static com.openexchange.java.Strings.toLowerCase;
import static com.openexchange.java.Strings.toUpperCase;
import static com.openexchange.mail.mime.utils.MimeMessageUtility.parseAddressList;
import static com.openexchange.mail.mime.utils.MimeMessageUtility.unfold;
import static com.openexchange.mail.text.HtmlProcessing.htmlFormat;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.idn.IDNA;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.i18n.MailStrings;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.html.HtmlService;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.CharsetDetector;
import com.openexchange.java.Strings;
import com.openexchange.mail.FullnameArgument;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.api.MailAccess;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.dataobjects.MailFolder;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.ContentDisposition;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.MimeTypes;
import com.openexchange.mail.mime.QuotedInternetAddress;
import com.openexchange.mail.mime.utils.MimeMessageUtility;
import com.openexchange.mail.usersetting.UserSettingMail;
import com.openexchange.mail.usersetting.UserSettingMailStorage;
import com.openexchange.mail.utils.MessageUtility;
import com.openexchange.mail.utils.MsisdnUtility;
import com.openexchange.mail.uuencode.UUEncodedMultiPart;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.UnifiedInboxManagement;
import com.openexchange.mailaccount.UnifiedInboxUID;
import com.openexchange.regional.RegionalSettingsService;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;

/**
 * {@link MimeProcessingUtility} - Provides some utility methods for {@link MimeForward} and {@link MimeReply}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class MimeProcessingUtility {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MimeProcessingUtility.class);

    /**
     * No instantiation
     */
    private MimeProcessingUtility() {
        super();
    }

    private static User getUser(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getUser();
        }
        return UserStorage.getInstance().getUser(session.getUserId(), session.getContextId());
    }

    private static UserSettingMail getUserSettingMail(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getUserSettingMail();
        }
        return UserSettingMailStorage.getInstance().getUserSettingMail(session);
    }

    /**
     * Checks if given address is known
     *
     * @param session The session
     * @param address The address
     * @return The mail account identifier or <code>-1</code> if unknown
     * @throws OXException If check fails
     */
    static int isKnownAddress(Session session, InternetAddress address) throws OXException {
        InternetAddress addr = new QuotedInternetAddress();
        addr.setAddress(address.getAddress());

        Set<InternetAddress> validAddrs = new HashSet<InternetAddress>(4);
        User user = getUser(session);
        for (String alias : user.getAliases()) {
            InternetAddress a = new QuotedInternetAddress();
            a.setAddress(alias);
            validAddrs.add(a);
        }
        if (MailProperties.getInstance().isSupportMsisdnAddresses()) {
            MsisdnUtility.addMsisdnAddress(validAddrs, session);
            String sAddress = addr.getAddress();
            int pos = sAddress.indexOf('/');
            if (pos > 0) {
                addr.setAddress(sAddress.substring(0, pos));
            }
        }
        if (validAddrs.contains(addr)) {
            return MailAccount.DEFAULT_ID;
        }

        MailAccountStorageService storageService = ServerServiceRegistry.getInstance().getService( MailAccountStorageService.class);
        int userId = session.getUserId();
        int contextId = session.getContextId();
        int accountId = storageService.getByPrimaryAddress(addr.getAddress(), userId, contextId);
        if (accountId != -1) {
            // Retry with IDN representation
            accountId = storageService.getByPrimaryAddress(IDNA.toIDN(addr.getAddress()), userId, contextId);
        }
        return accountId;
    }

    /**
     * Resolves specified "from" address to associated account identifier
     *
     * @param session The session
     * @param from The from addresses
     * @return The account identifier
     * @throws OXException If address cannot be resolved
     */
    static int resolveFrom2Account(Session session, InternetAddress[] from) throws OXException {
        if (null == from || from.length == 0) {
            return MailAccount.DEFAULT_ID;
        }
        return resolveFrom2Account(session, from[0]);
    }

    /**
     * Resolves specified "from" address to associated account identifier
     *
     * @param session The session
     * @param from The from address
     * @return The account identifier
     * @throws OXException If address cannot be resolved
     */
    static int resolveFrom2Account(Session session, InternetAddress from) throws OXException {
        /*
         * Resolve "From" to proper mail account to select right transport server
         */
        int accountId;
        if (null == from) {
            accountId = MailAccount.DEFAULT_ID;
        } else {
            MailAccountStorageService storageService = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class);
            int user = session.getUserId();
            int cid = session.getContextId();
            accountId = storageService.getByPrimaryAddress(from.getAddress(), user, cid);
            if (accountId != -1) {
                // Retry with IDN representation
                accountId = storageService.getByPrimaryAddress(IDNA.toIDN(from.getAddress()), user, cid);
            }
        }
        if (accountId == -1) {
            accountId = MailAccount.DEFAULT_ID;
        }
        return accountId;
    }

    /**
     * Checks if the "From" address from given original message in specified account is a valid address for given user.
     *
     * @param origMsg The original message possibly providing "From" address
     * @param accountId The account identifier
     * @param session The session providing user data
     * @param ctx The context in which the user resides
     * @return The validated "From" address or empty
     * @throws OXException If "From" address cannot be validated
     */
    public static Optional<InternetAddress> validateFrom(MailMessage origMsg, int accountId, Session session, Context ctx) throws OXException {
        if (origMsg == null) {
            return Optional.empty();
        }

        // Determine "From" address
        InternetAddress from;
        {
            InternetAddress[] originalFrom = origMsg.getFrom();
            if (originalFrom == null || originalFrom.length <= 0 || originalFrom[0] == null) {
                return Optional.empty();
            }
            from = originalFrom[0];
        }

        int accountIdToCheck = accountId;
        if (accountIdToCheck != MailAccount.DEFAULT_ID) {
            // Check for Unified Mail account
            UnifiedInboxManagement management = ServerServiceRegistry.getInstance().getService(UnifiedInboxManagement.class);
            if ((null != management) && (accountId == management.getUnifiedINBOXAccountID(session))) {
                int realAccountId;
                try {
                    UnifiedInboxUID uid = new UnifiedInboxUID(origMsg.getMailId());
                    realAccountId = uid.getAccountId();
                } catch (OXException e) {
                    // No Unified Mail identifier
                    FullnameArgument fa = UnifiedInboxUID.parsePossibleNestedFullName(origMsg.getFolder());
                    realAccountId = null == fa ? MailAccount.DEFAULT_ID : fa.getAccountId();
                }
                accountIdToCheck = realAccountId;
            }
        }

        return validateFrom0(from, accountIdToCheck, session, ctx);
    }

    private static Optional<InternetAddress> validateFrom0(InternetAddress from, int accountId, Session session, Context ctx) throws OXException {
        Set<InternetAddress> fromCandidates;
        if (accountId == MailAccount.DEFAULT_ID) {
            fromCandidates = new HashSet<InternetAddress>(8);
            addUserAliases(fromCandidates, session, ctx);
        } else {
            MailAccountStorageService mass = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class);
            if (null == mass) {
                fromCandidates = new HashSet<InternetAddress>(8);
                addUserAliases(fromCandidates, session, ctx);
            } else {
                QuotedInternetAddress a = new QuotedInternetAddress();
                a.setAddress(mass.getMailAccount(accountId, session.getUserId(), session.getContextId()).getPrimaryAddress());
                fromCandidates = new HashSet<InternetAddress>(2);
                fromCandidates.add(a);
            }
        }

        return fromCandidates.contains(from) ? Optional.of(from) : Optional.empty();
    }

    /**
     * Determines a possible <code>From</code> address
     *
     * @param origMsg The referenced message
     * @param accountId The associated account identifier
     * @param session The session
     * @param ctx The context
     * @return The possible <code>From</code> address or <code>null</code>
     * @throws OXException If an Open-Xchange error occurs
     */
    public static InternetAddress determinePossibleFrom(boolean isForward, MailMessage origMsg, int accountId, Session session, Context ctx) throws OXException {
        Set<InternetAddress> fromCandidates;
        InternetAddress likely = null;
        if (accountId == MailAccount.DEFAULT_ID) {
            if (isForward) {
                // Fall-back to primary address
                return null;
            }
            fromCandidates = new HashSet<InternetAddress>(8);
            likely = new QuotedInternetAddress();
            likely.setAddress(getUserSettingMail(session).getSendAddr());
            addUserAliases(fromCandidates, session, ctx);
        } else {
            // Check for Unified Mail account
            ServerServiceRegistry registry = ServerServiceRegistry.getInstance();
            UnifiedInboxManagement management = registry.getService(UnifiedInboxManagement.class);
            if ((null != management) && (accountId == management.getUnifiedINBOXAccountID(session))) {
                int realAccountId;
                try {
                    UnifiedInboxUID uid = new UnifiedInboxUID(origMsg.getMailId());
                    realAccountId = uid.getAccountId();
                } catch (OXException e) {
                    // No Unified Mail identifier
                    FullnameArgument fa = UnifiedInboxUID.parsePossibleNestedFullName(origMsg.getFolder());
                    realAccountId = null == fa ? MailAccount.DEFAULT_ID : fa.getAccountId();
                }

                if (realAccountId == MailAccount.DEFAULT_ID) {
                    if (isForward) {
                        // Fall-back to primary address
                        return null;
                    }
                    fromCandidates = new HashSet<InternetAddress>(8);
                    likely = new QuotedInternetAddress();
                    likely.setAddress(getUserSettingMail(session).getSendAddr());
                    addUserAliases(fromCandidates, session, ctx);
                } else {
                    MailAccountStorageService mass = registry.getService(MailAccountStorageService.class);
                    if (null == mass) {
                        if (isForward) {
                            // Fall-back to primary address
                            return null;
                        }
                        fromCandidates = new HashSet<InternetAddress>(8);
                        likely = new QuotedInternetAddress();
                        likely.setAddress(getUserSettingMail(session).getSendAddr());
                        addUserAliases(fromCandidates, session, ctx);
                    } else {
                        QuotedInternetAddress a = new QuotedInternetAddress();
                        a.setAddress(mass.getMailAccount(realAccountId, session.getUserId(), session.getContextId()).getPrimaryAddress());
                        if (isForward) {
                            return a;
                        }
                        fromCandidates = new HashSet<InternetAddress>(2);
                        likely = a;
                        fromCandidates.add(a);
                    }
                }
            } else {
                MailAccountStorageService mass = registry.getService(MailAccountStorageService.class);
                if (null == mass) {
                    if (isForward) {
                        // Fall-back to primary address
                        return null;
                    }
                    fromCandidates = new HashSet<InternetAddress>(8);
                    likely = new QuotedInternetAddress();
                    likely.setAddress(getUserSettingMail(session).getSendAddr());
                    addUserAliases(fromCandidates, session, ctx);
                } else {
                    QuotedInternetAddress a = new QuotedInternetAddress();
                    a.setAddress(mass.getMailAccount(accountId, session.getUserId(), session.getContextId()).getPrimaryAddress());
                    if (isForward) {
                        return a;
                    }
                    fromCandidates = new HashSet<InternetAddress>(2);
                    likely = a;
                    fromCandidates.add(a);
                }
            }
        }
        /*
         * Check if present anywhere
         */
        InternetAddress from = null;
        {
            String hdrVal = origMsg.getHeader(MessageHeaders.HDR_TO, MessageHeaders.HDR_ADDR_DELIM);
            InternetAddress[] toAddrs = null;
            if (hdrVal != null) {
                toAddrs = parseAddressList(hdrVal, true);
                for (InternetAddress addr : toAddrs) {
                    if (fromCandidates.contains(addr)) {
                        from = addr;
                        break;
                    }
                }
            }
            if (null == from) {
                hdrVal = origMsg.getHeader(MessageHeaders.HDR_CC, MessageHeaders.HDR_ADDR_DELIM);
                if (hdrVal != null) {
                    toAddrs = parseAddressList(unfold(hdrVal), true);
                    for (InternetAddress addr : toAddrs) {
                        if (fromCandidates.contains(addr)) {
                            from = addr;
                            break;
                        }
                    }
                }
            }
            if (null == from) {
                hdrVal = origMsg.getHeader(MessageHeaders.HDR_BCC, MessageHeaders.HDR_ADDR_DELIM);
                if (hdrVal != null) {
                    toAddrs = parseAddressList(unfold(hdrVal), true);
                    for (InternetAddress addr : toAddrs) {
                        if (fromCandidates.contains(addr)) {
                            from = addr;
                            break;
                        }
                    }
                }
            }
        }
        return null == from ? likely : from;
    }

    /**
     * Adds the session's user aliases to given set.
     *
     * @param set The set to add to
     * @param session The session providing user information
     * @param ctx The associated context
     * @throws OXException If operation fails
     */
    public static void addUserAliases(Set<InternetAddress> set, Session session, Context ctx) throws OXException {
        /*
         * Add user's aliases to set
         */
        String[] userAddrs = UserStorage.getInstance().getUser(session.getUserId(), ctx).getAliases();
        if (userAddrs != null && userAddrs.length > 0) {
            StringBuilder addrBuilder = new StringBuilder();
            addrBuilder.append(userAddrs[0]);
            for (int i = 1; i < userAddrs.length; i++) {
                addrBuilder.append(',').append(userAddrs[i]);
            }
            set.addAll(Arrays.asList(parseAddressList(addrBuilder.toString(), false)));
        }
    }

    /**
     * Gets denoted folder's owner if it is shared.
     *
     * @param fullName The full name
     * @param accountId The account identifier
     * @param session The session
     * @return The owner or <code>null</code>
     */
    public static final String getFolderOwnerIfShared(String fullName, int accountId, Session session) {
        if (null == fullName) {
            return null;
        }
        MailAccess<?, ?> access = null;
        try {
            access = MailAccess.getInstance(session, accountId);
            access.connect(false);
            final MailFolder folder = access.getFolderStorage().getFolder(fullName);
            return folder.isShared() ? folder.getOwner() : null;
        } catch (Exception e) {
            LOG.warn("Couldn't resolve owner for {}", fullName, e);
            return null;
        } finally {
            if (null != access) {
                access.close(true);
            }
        }
    }

    /**
     * Formats specified date in given style with given locale and time zone (optional for specific user).
     *
     * @param date The date to format
     * @param style The style to use
     * @param locale The locale
     * @param timeZone The time zone
     * @param session The session identifying the user, may be <code>null</code>
     * @return The formatted date
     */
    public static final String getFormattedDate(Date date, int style, Locale locale, TimeZone timeZone, Session session) {
        DateFormat dateFormat;
        if (null != session) {
            RegionalSettingsService service = ServerServiceRegistry.getInstance().getService(RegionalSettingsService.class);
            if (null != service) {
                dateFormat = service.getDateFormat(session.getContextId(), session.getUserId(), locale, style);
            } else {
                LOG.info(RegionalSettingsService.class.getSimpleName() + " is not available, using default date format.");
                dateFormat = DateFormat.getDateInstance(style, locale);
            }
        } else {
            dateFormat = DateFormat.getDateInstance(style, locale);
        }
        dateFormat.setTimeZone(timeZone);
        return dateFormat.format(date);
    }

    /**
     * Formats specified time in given style with given locale and time zone (optional for specific user).
     *
     * @param date The time to format
     * @param style The style to use
     * @param locale The locale
     * @param timeZone The time zone
     * @param session The session identifying the user, may be <code>null</code>
     * @return The formatted time
     */
    public static final String getFormattedTime(Date date, int style, Locale locale, TimeZone timeZone, Session session) {
        DateFormat dateFormat;
        if (null != session) {
            RegionalSettingsService service = ServerServiceRegistry.getInstance().getService(RegionalSettingsService.class);
            if (null != service) {
                dateFormat = service.getTimeFormat(session.getContextId(), session.getUserId(), locale, style);
            } else {
                LOG.info(RegionalSettingsService.class.getSimpleName() + " is not available, using default time format.");
                dateFormat = DateFormat.getTimeInstance(style, locale);
            }
        } else {
            dateFormat = DateFormat.getTimeInstance(style, locale);
        }
        dateFormat.setTimeZone(timeZone);
        return dateFormat.format(date);
    }

    /**
     * Checks if given part's disposition is inline; meaning more likely a regular message body than an attachment.
     *
     * @param part The message's part
     * @param contentType The part's Content-Type header
     * @return <code>true</code> if given part is considered to be an inline part; otherwise <code>false</code>
     * @throws OXException If part's headers cannot be accessed or parsed
     */
    static boolean isInline(MailPart part, ContentType contentType) throws OXException {
        final ContentDisposition cd;
        final boolean hasDisposition;
        {
            final String[] hdr = part.getHeader(MessageHeaders.HDR_CONTENT_DISPOSITION);
            if (null == hdr) {
                cd = new ContentDisposition();
                hasDisposition = false;
            } else {
                cd = new ContentDisposition(hdr[0]);
                hasDisposition = true;
            }
        }
        return (hasDisposition && Part.INLINE.equalsIgnoreCase(cd.getDisposition())) || (!hasDisposition && !cd.containsFilenameParameter() && !contentType.containsParameter("name"));
    }

    /**
     * Checks if specified part's filename ends with given suffix.
     *
     * @param suffix The suffix to check against
     * @param part The part whose filename shall be checked
     * @param contentType The part's Content-Type header
     * @return <code>true</code> if part's filename is not absent and ends with given suffix; otherwise <code>false</code>
     * @throws OXException If part's filename cannot be determined
     */
    static boolean fileNameEndsWith(String suffix, MailPart part, ContentType contentType) throws OXException {
        final String filename = getFileName(part, contentType);
        return null == filename ? false : filename.toLowerCase(Locale.ENGLISH).endsWith(suffix);
    }

    /**
     * Gets specified part's filename.
     *
     * @param part The part whose filename shall be returned
     * @param contentType The part's Content-Type header
     * @return The filename or <code>null</code>
     * @throws OXException If part's filename cannot be returned
     */
    private static String getFileName(MailPart part, ContentType contentType) throws OXException {
        final ContentDisposition cd;
        {
            final String[] hdr = part.getHeader(MessageHeaders.HDR_CONTENT_DISPOSITION);
            if (null == hdr) {
                cd = new ContentDisposition();
            } else {
                cd = new ContentDisposition(hdr[0]);
            }
        }
        String filename = cd.getFilenameParameter();
        if (null == filename) {
            filename = contentType.getParameter("name");
        }
        return MimeMessageUtility.decodeMultiEncodedHeader(filename);
    }

    /**
     * Determines the proper text version according to user's mail settings. Given content type is altered accordingly
     *
     * @param textPart The text part
     * @param contentType The text part's content type
     * @return The proper text version
     * @throws OXException If a mail error occurs
     * @throws IOException If an I/O error occurs
     */
    static String handleInlineTextPart(MailPart textPart, ContentType contentType, boolean allowHTML) throws IOException, OXException {
        final String charset = getCharset(textPart, contentType);
        if (contentType.startsWith("text/") && Strings.startsWithAny(toLowerCase(contentType.getSubType()), "htm", "xhtm")) {
            if (allowHTML) {
                return readContent(textPart, charset);
            }
            contentType.setBaseType("text/plain");
            final HtmlService htmlService = ServerServiceRegistry.getInstance().getService(HtmlService.class);
            return htmlService.html2text(readContent(textPart, charset), false);
            // return new Html2TextConverter().convertWithQuotes(MessageUtility.readMimePart(textPart, contentType));
        } else if (contentType.startsWith(MimeTypes.MIME_TEXT_PLAIN)) {
            final String content = readContent(textPart, charset);
            UUEncodedMultiPart uuencodedMP = UUEncodedMultiPart.valueFor(content);
            if (null != uuencodedMP && uuencodedMP.isUUEncoded()) {
                /*
                 * UUEncoded content detected. Extract normal text.
                 */
                return uuencodedMP.getCleanText();
            }
            return content;
        }
        return readContent(textPart, charset);
    }

    private static final String PRIMARY_TEXT= "text/";

    private static final String[] SUB_SPECIAL2 = { "rfc822-headers", "vcard", "x-vcard", "calendar", "x-vcalendar" };

    /**
     * Checks if content type matches one of special content types:
     * <ul>
     * <li><code>text/rfc822-headers</code></li>
     * <li><code>text/vcard</code></li>
     * <li><code>text/x-vcard</code></li>
     * <li><code>text/calendar</code></li>
     * <li><code>text/x-vcalendar</code></li>
     * </ul>
     *
     * @param contentType The content type
     * @return <code>true</code> if content type matches special; otherwise <code>false</code>
     */
    public static boolean isSpecial(String contentType) {
        if (null == contentType) {
            return false;
        }
        final String ct = contentType.toLowerCase(Locale.US);
        if (ct.startsWith(PRIMARY_TEXT, 0)) {
            final int off = PRIMARY_TEXT.length();
            for (String subtype : SUB_SPECIAL2) {
                if (ct.startsWith(subtype, off)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reads specified mail part's content catching possible <code>java.io.CharConversionException</code>.
     *
     * @param mailPart The mail part
     * @param charset The charset to use
     * @return The mail part's content as a string
     * @throws OXException If a mail error occurs
     * @throws IOException If an I/O error occurs
     */
    static String readContent(MailPart mailPart, String charset) throws OXException, IOException {
        try {
            return MessageUtility.readMailPart(mailPart, charset);
        } catch (java.io.CharConversionException e) {
            // Obviously charset was wrong or bogus implementation of character conversion
            final String fallback = "US-ASCII";
            LOG.warn("Character conversion exception while reading content with charset \"{}\". Using fallback charset \"{}\" instead.", charset, fallback, e);
            return MessageUtility.readMailPart(mailPart, fallback);
        }
    }

    private static final String TEXT = "text/";

    private static String getCharset(MailPart mailPart, ContentType contentType) throws OXException {
        final String charset;
        if (mailPart.containsHeader(MessageHeaders.HDR_CONTENT_TYPE)) {
            String cs = contentType.getCharsetParameter();
            if (!CharsetDetector.isValid(cs)) {
                if (null != cs) {
                    LOG.warn("Illegal or unsupported encoding in a message detected: \"{}\"", cs, new UnsupportedEncodingException(cs));
                }
                if (contentType.startsWith(TEXT)) {
                    cs = CharsetDetector.detectCharset(mailPart.getInputStream());
                } else {
                    cs = MailProperties.getInstance().getDefaultMimeCharset();
                }
            }
            charset = cs;
        } else {
            if (contentType.startsWith(TEXT)) {
                charset = CharsetDetector.detectCharset(mailPart.getInputStream());
            } else {
                charset = MailProperties.getInstance().getDefaultMimeCharset();
            }
        }
        return charset;
    }

    /**
     * Creates a {@link String} from given array of {@link InternetAddress} instances through invoking
     * {@link InternetAddress#toUnicodeString()}
     *
     * @param addrs The array of {@link InternetAddress} instances
     * @return A comma-separated list of addresses as a {@link String}
     */
    public static String addrs2String(InternetAddress[] addrs) {
        final StringBuilder tmp = new StringBuilder(addrs.length << 4);
        boolean first = true;
        for (InternetAddress addr : addrs) {
            final String string = addr2String(addr);
            if (!com.openexchange.java.Strings.isEmpty(string)) {
                if (first) {
                    first = false;
                } else {
                    tmp.append(", ");
                }
                tmp.append(string);
            }
        }
        return first ? "" : tmp.toString();
    }

    /**
     * Creates a {@link String} from given {@link InternetAddress} instance.
     *
     * @param addr The {@link InternetAddress} instance
     * @return The address string
     */
    static String addr2String(InternetAddress addr) {
        if (null == addr) {
            return "";
        }

        String sAddress = addr.getAddress();
        int pos = null == sAddress ? 0 : sAddress.indexOf('/');
        if (pos <= 0 || false == toUpperCase(sAddress).endsWith("/TYPE=PLMN")) {
            // No slash character present
            return addr.toUnicodeString();
        }

        // Assume something like "+491234567890/TYPE=PLMN"
        StringBuilder sb = new StringBuilder(32);
        String personal = addr.getPersonal();
        if (null == personal) {
            sb.append(MimeMessageUtility.prepareAddress(sAddress.substring(0, pos)));
        } else {
            sb.append(MimeProcessingUtility.preparePersonal(personal));
            sb.append(" <").append(MimeMessageUtility.prepareAddress(sAddress.substring(0, pos))).append('>');
        }
        return sb.toString();
    }

    private static final String CT_TEXT_HTM = "text/htm";

    /**
     * Appends the appropriate text version dependent on root's content type and current text's content type
     *
     * @param rootType The root's content type
     * @param contentType Current text's content type
     * @param text The text content
     * @param textBuilder The text builder to append to
     */
    static void appendRightVersion(ContentType rootType, ContentType contentType, String text, StringBuilder textBuilder) {
        if (rootType.getBaseType().equalsIgnoreCase(contentType.getBaseType())) {
            textBuilder.append(text);
        } else if (rootType.startsWith(CT_TEXT_HTM)) {
            textBuilder.append(htmlFormat(text));
        } else {
            textBuilder.append(ServerServiceRegistry.getInstance().getService(HtmlService.class).html2text(text, false));
            // textBuilder.append(new Html2TextConverter().convertWithQuotes(text));
        }
    }

    /**
     * Prepares specified personal string by surrounding it with quotes if needed.
     *
     * @param personal The personal
     * @return The prepared personal
     */
    static String preparePersonal(String personal) {
        return MimeMessageUtility.quotePhrase(personal, false);
    }

    /**
     * Gets the context associated with specified session
     *
     * @param session The session
     * @return The context
     * @throws OXException If context cannot be returned
     */
    static Context getContextFrom(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getContext();
        }

        return ContextStorage.getInstance().getContext(session.getContextId());
    }

    /**
     * Gets the user associated with specified session
     *
     * @param session The session
     * @return The user
     * @throws OXException If user cannot be returned
     */
    static User getUserFrom(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getUser();
        }

        return UserStorage.getInstance().getUser(session.getUserId(), session.getContextId());
    }

    /**
     * Gets the user mail setting associated with specified session
     *
     * @param session The session
     * @return The user mail setting
     * @throws OXException If user mail setting cannot be returned
     */
    static UserSettingMail getUserSettingMailFrom(Session session) throws OXException {
        if (session instanceof ServerSession) {
            return ((ServerSession) session).getUserSettingMail();
        }

        return UserSettingMailStorage.getInstance().getUserSettingMail(session);
    }

    /**
     * Gets the text from specified original mail for composing a reply.
     *
     * @param originalMail The original mail being replied to
     * @param allowHtmlContent Whether HTMl content is allowed
     * @param generateReplyPrefix Whether the {@link MailStrings#REPLY_PREFIX reply prefix} is supposed to be prepended
     * @param session The session providing user information
     * @return The text and its associated content-type
     * @throws OXException OIf reply text cannot be generated
     */
    public static TextAndContentType getTextForReply(MailMessage originalMail, boolean allowHtmlContent, boolean generateReplyPrefix, Session session) throws OXException {
        try {
            List<String> list = new LinkedList<String>();
            User user = getUserFrom(session);
            Locale locale = user.getLocale();
            LocaleAndTimeZone ltz = new LocaleAndTimeZone(locale, user.getTimeZone());

            UserSettingMail usm = getUserSettingMailFrom(session).clone();
            usm.setDisplayHtmlInlineContent(allowHtmlContent);
            usm.setDropReplyForwardPrefix(false == generateReplyPrefix);

            ContentType contentType = new ContentType();
            MimeReply.generateReplyText(originalMail, contentType, StringHelper.valueOf(locale), ltz, usm, session, originalMail.getAccountId(), list);

            StringBuilder replyTextBuilder = new StringBuilder(8192 << 1);
            int size = list.size();
            if (size > 0) {
                for (int i = size; i-- > 0;) {
                    replyTextBuilder.append(list.get(i));
                }
            }
            if (replyTextBuilder.length() <= 0) {
                /*
                 * No reply text found at all
                 */
                return null;
            }

            if (contentType.getPrimaryType() == null) {
                contentType.setContentType(MimeTypes.MIME_TEXT_PLAIN);
            }

            String replyText = replyTextBuilder.toString();
            replyTextBuilder = null;
            boolean isHtml = contentType.startsWithAny(CT_TEXT_HTM, "text/xhtm");
            if (isHtml) {
                contentType.setCharsetParameter("UTF-8");
                replyText = MimeForward.replaceMetaEquiv(replyText, contentType);
                HtmlService htmlService = ServerServiceRegistry.getInstance().getService(HtmlService.class);
                if (htmlService != null) {
                    replyText = htmlService.checkBaseTag(replyText, true);
                }
            } else {
                String cs = contentType.getCharsetParameter();
                if (cs == null || "US-ASCII".equalsIgnoreCase(cs) || !CharsetDetector.isValid(cs) || MessageUtility.isSpecialCharset(cs)) {
                    // Select default charset
                    contentType.setCharsetParameter(MailProperties.getInstance().getDefaultMimeCharset());
                }
            }
            return new TextAndContentType(replyText, contentType, isHtml);
        } catch (MessagingException e) {
            throw MimeMailException.handleMessagingException(e);
        } catch (IOException e) {
            if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
            }
            throw MailExceptionCode.IO_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Gets the text from specified original mail for composing a forward.
     *
     * @param originalMail The original mail being forwarded
     * @param allowHtmlContent Whether HTMl content is allowed
     * @param generateForwardPrefix Whether the {@link MailStrings#FORWARD_PREFIX forward prefix} is supposed to be prepended
     * @param contentIds An optional list for returning detected identifiers of inline images
     * @param session The session providing user information
     * @return The text and its associated content-type
     * @throws OXException If reply text cannot be generated
     */
    public static TextAndContentType getTextForForward(MailMessage originalMail, boolean allowHtmlContent, boolean generateForwardPrefix, List<String> contentIds, Session session) throws OXException {
        try {
            UserSettingMail usm = getUserSettingMailFrom(session).clone();
            usm.setDisplayHtmlInlineContent(allowHtmlContent);
            usm.setDropReplyForwardPrefix(false == generateForwardPrefix);

            ContentType originalContentType = originalMail.getContentType();

            ContentType contentType;
            String firstSeenText;
            if (originalContentType.startsWith("multipart/")) {
                contentType = new ContentType();
                firstSeenText = MimeForward.getFirstSeenText(originalMail, contentType, usm, originalMail, session, false);
            } else if (originalContentType.startsWith(TEXT) && !MimeProcessingUtility.isSpecial(originalContentType.getBaseType())) {
                // Original mail is a simple text mail: Add message body prefixed with forward text
                {
                    String cs = originalContentType.getCharsetParameter();
                    if (null == cs) {
                        originalContentType.setCharsetParameter(MessageUtility.checkCharset(originalMail, originalContentType));
                    }
                }
                contentType = originalContentType;
                firstSeenText = MimeProcessingUtility.readContent(originalMail, originalContentType.getCharsetParameter());
            } else {
                // Mail only consists of one non-textual part
                contentType = new ContentType().setPrimaryType("text").setSubType("plain").setCharsetParameter(MailProperties.getInstance().getDefaultMimeCharset());
                firstSeenText = "";
            }

            {
                String cs = contentType.getCharsetParameter();
                if (cs == null || "US-ASCII".equalsIgnoreCase(cs)) {
                    contentType.setCharsetParameter(MailProperties.getInstance().getDefaultMimeCharset());
                }
            }
            /*
             * Prepare the text to insert
             */
            boolean isHtml = contentType.startsWithAny(CT_TEXT_HTM, "text/xhtm");
            if (null == firstSeenText) {
                /*
                 * No reply text found at all
                 */
                return null;
            } else if (isHtml) {
                if (null != contentIds) {
                    contentIds.addAll(MimeMessageUtility.getContentIDs(firstSeenText));
                }
                contentType.setCharsetParameter("UTF-8");
                firstSeenText = MimeForward.replaceMetaEquiv(firstSeenText, contentType);
                HtmlService htmlService = ServerServiceRegistry.getInstance().getService(HtmlService.class);
                if (htmlService != null) {
                    firstSeenText = htmlService.checkBaseTag(firstSeenText, true);
                }
            }
            /*
             * Add appropriate text part prefixed with forward text
             */
            String txt = usm.isDropReplyForwardPrefix() ? firstSeenText : MimeForward.generateForwardText(firstSeenText, new LocaleAndTimeZone(getUserFrom(session)), originalMail, isHtml, session);
            {
                String cs = contentType.getCharsetParameter();
                if (cs == null || "US-ASCII".equalsIgnoreCase(cs) || !CharsetDetector.isValid(cs) || MessageUtility.isSpecialCharset(cs)) {
                    // Select default charset
                    contentType.setCharsetParameter(MailProperties.getInstance().getDefaultMimeCharset());
                }
            }
            return new TextAndContentType(txt, contentType, isHtml);
        } catch (MessagingException e) {
            throw MimeMailException.handleMessagingException(e);
        } catch (IOException e) {
            if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
            }
            throw MailExceptionCode.IO_ERROR.create(e, e.getMessage());
        }
    }

}
