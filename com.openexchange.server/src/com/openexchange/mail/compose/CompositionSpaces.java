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

package com.openexchange.mail.compose;

import static com.openexchange.java.util.UUIDs.getUnformattedString;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import org.slf4j.Logger;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.AJAXUtility;
import com.openexchange.contact.ContactService;
import com.openexchange.contact.vcard.VCardExport;
import com.openexchange.contact.vcard.VCardUtil;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.i18n.Translator;
import com.openexchange.i18n.TranslatorFactory;
import com.openexchange.image.ImageActionFactory;
import com.openexchange.image.ImageDataSource;
import com.openexchange.image.ImageLocation;
import com.openexchange.java.ISO8601Utils;
import com.openexchange.java.InterruptibleCharSequence;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.java.util.UUIDs;
import com.openexchange.logging.ConsoleTable;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.utils.MimeMessageUtility;
import com.openexchange.mail.text.HtmlProcessing;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.threadpool.AbstractTrackableTask;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;

/**
 * {@link CompositionSpaces} - Utility class for composition space.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.2
 */
public class CompositionSpaces {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CompositionSpaces.class);
    }

    /**
     * Initializes a new {@link CompositionSpaces}.
     */
    private CompositionSpaces() {
        super();
    }

    /**
     * Gets the vCard for the user associated with specified session.
     *
     * @param session The session providing user information
     * @return The vCard as byte array
     * @throws OXException
     */
    public static byte[] getUserVCardBytes(Session session) throws OXException {
        ContactService contactService = ServerServiceRegistry.getInstance().getService(ContactService.class);
        Contact contact = contactService.getUser(session, session.getUserId());
        VCardExport vCardExport = null;
        try {
            vCardExport = VCardUtil.exportContact(contact, session);
            return vCardExport.toByteArray();
        } finally {
            Streams.close(vCardExport);
        }
    }

    /**
     * Gets the file name of the vCard file for the user associated with specified session.
     *
     * @param session The session providing user information
     * @return The vCard file name
     * @throws OXException
     */
    public static String getUserVCardFileName(Session session) throws OXException {
        String displayName;
        if (session instanceof ServerSession) {
            displayName = ((ServerSession) session).getUser().getDisplayName();
        } else {
            displayName = UserStorage.getInstance().getUser(session.getUserId(), session.getContextId()).getDisplayName();
        }
        String saneDisplayName = Strings.replaceWhitespacesWith(displayName, "");
        return saneDisplayName + ".vcf";
    }

    /**
     * Gets the vCard information for the user associated with specified session.
     *
     * @param session The session providing user information
     * @return The vCard information
     * @throws OXException
     */
    public static VCardAndFileName getUserVCard(Session session) throws OXException {
        return new VCardAndFileName(getUserVCardBytes(session), getUserVCardFileName(session));
    }

    /*
     * The display of a users given and sur name name in e.g. notification mails (Hello John Doe, ...).
     * The placeholders mean $givenname $surname.
     */
    private static final String USER_NAME = "%1$s %2$s";

    /**
     * Gets the vCard for the given contact.
     *
     * @param contactId The identifier of the contact
     * @param folderId The identifier of the folder in which the contact resides
     * @param session The session providing user information
     * @return The vCard as byte array
     * @throws OXException If contact's vCard cannot be returned
     */
    public static VCardAndFileName getContactVCard(String contactId, String folderId, Session session) throws OXException {
        ContactService contactService = ServerServiceRegistry.getInstance().getService(ContactService.class);
        if (null == contactService) {
            throw ServiceExceptionCode.absentService(ContactService.class);
        }
        Contact contact = contactService.getContact(session, folderId, contactId);

        byte[] vcard;
        {
            VCardExport vCardExport = null;
            try {
                vCardExport = VCardUtil.exportContact(contact, session);
                vcard = vCardExport.toByteArray();
            } finally {
                Streams.close(vCardExport);
            }
        }

        String displayName = contact.getDisplayName();
        if (Strings.isEmpty(displayName)) {
            String givenName = contact.getGivenName();
            String surname = contact.getSurName();

            TranslatorFactory translatorFactory = ServerServiceRegistry.getInstance().getService(TranslatorFactory.class);
            if (null != translatorFactory) {
                // Determine user's locale
                User user;
                if (session instanceof ServerSession) {
                    user = ((ServerSession) session).getUser();
                } else {
                    user = UserStorage.getInstance().getUser(session.getUserId(), session.getContextId());
                }
                Translator translator = translatorFactory.translatorFor(user.getLocale());
                displayName = String.format(translator.translate(USER_NAME), givenName, surname);
            } else {
                displayName = new StringBuilder(givenName).append(' ').append(surname).toString();
            }
        }
        return new VCardAndFileName(vcard, Strings.replaceWhitespacesWith(displayName, "") + ".vcf");
    }

    /**
     * Parses a composition space's UUID from specified unformatted string.
     *
     * @param id The composition space identifier as an unformatted string; e.g. <code>067e61623b6f4ae2a1712470b63dff00</code>
     * @return The UUID
     * @throws OXException If passed string in invalid
     */
    public static UUID parseCompositionSpaceId(String id) throws OXException {
        try {
            return UUIDs.fromUnformattedString(id);
        } catch (IllegalArgumentException e) {
            throw CompositionSpaceErrorCode.NO_SUCH_COMPOSITION_SPACE.create(e, id);
        }
    }

    /**
     * Parses a composition space's UUID from specified unformatted string.
     *
     * @param id The composition space identifier as an unformatted string; e.g. <code>067e61623b6f4ae2a1712470b63dff00</code>
     * @return The UUID or <code>null</code> if passed string in invalid
     */
    public static UUID parseCompositionSpaceIdIfValid(String id) {
        try {
            return UUIDs.fromUnformattedString(id);
        } catch (@SuppressWarnings("unused") IllegalArgumentException x) {
            return null;
        }
    }

    /**
     * Parses an attachment's UUID from specified unformatted string.
     *
     * @param id The attachment identifier as an unformatted string; e.g. <code>067e61623b6f4ae2a1712470b63dff00</code>
     * @return The UUID
     * @throws OXException If passed string in invalid
     */
    public static UUID parseAttachmentId(String id) throws OXException {
        try {
            return UUIDs.fromUnformattedString(id);
        } catch (IllegalArgumentException e) {
            throw CompositionSpaceErrorCode.NO_SUCH_ATTACHMENT_RESOURCE.create(e, id);
        }
    }

    /**
     * Parses an attachment's UUID from specified unformatted string.
     *
     * @param id The attachment identifier as an unformatted string; e.g. <code>067e61623b6f4ae2a1712470b63dff00</code>
     * @return The UUID or <code>null</code> if passed string in invalid
     */
    public static UUID parseAttachmentIdIfValid(String id) {
        try {
            return UUIDs.fromUnformattedString(id);
        } catch (@SuppressWarnings("unused") IllegalArgumentException x) {
            return null;
        }
    }

    /**
     * Checks if specified mail part has the vCard marker.
     *
     * @param mailPart The mail part to check
     * @param session The session
     * @return <code>true</code> if vCard marker is present; otherwise <code>false</code>
     */
    public static boolean hasVCardMarker(MailPart mailPart, Session session) {
        String header = mailPart.getFirstHeader(MessageHeaders.HDR_X_OX_VCARD);
        if (Strings.isEmpty(header)) {
            return false;
        }
        String userId = new StringBuilder(16).append(session.getUserId()).append('@').append(session.getContextId()).toString();
        return userId.equals(header);
    }

    /**
     * Checks if specified mail part has the vCard marker.
     *
     * @param part The part to check
     * @param session The session
     * @return <code>true</code> if vCard marker is present; otherwise <code>false</code>
     * @throws OXException If check fails
     */
    public static boolean hasVCardMarker(MimeBodyPart part, Session session) throws OXException {
        try {
            String header = part.getHeader(MessageHeaders.HDR_X_OX_VCARD, null);
            if (Strings.isEmpty(header)) {
                return false;
            }
            String userId = new StringBuilder(16).append(session.getUserId()).append('@').append(session.getContextId()).toString();
            return userId.equals(header);
        } catch (MessagingException e) {
            throw MimeMailException.handleMessagingException(e);
        }
    }

    private static final Pattern PATTERN_SRC = MimeMessageUtility.PATTERN_SRC;

    /**
     * Replaces <code>&lt;img&gt;</code> tags providing an inline image through exchanging <code>"src"</code> value appropriately.
     * <p>
     * <code>&lt;img src="cid:123456"&gt;</code> is converted to<br>
     * <code>&lt;img src="/ajax/image/mail/compose/image?uid=71ff23e06f424cc5bcb08a92e006838a"&gt;</code>
     *
     * @param htmlContent The HTML content to replace in
     * @param contentId2InlineAttachments The detected inline images
     * @param imageDataSource The image data source to use
     * @param session The session providing user information
     * @return The (possibly) processed HTML content
     * @throws OXException If replacing <code>&lt;img&gt;</code> tags fails
     * @see #replaceCidInlineImages(String, Optional, Map, ImageDataSource, Session)
     */
    public static String replaceCidInlineImages(String htmlContent, Map<ContentId, Attachment> contentId2InlineAttachments, AbstractCompositionSpaceImageDataSource imageDataSource, Session session) throws OXException {
        return replaceCidInlineImages(htmlContent, Optional.empty(), contentId2InlineAttachments, imageDataSource, session);
    }

    /**
     * Replaces <code>&lt;img&gt;</code> tags providing an inline image through exchanging <code>"src"</code> value appropriately.
     * <p>
     * <code>&lt;img src="cid:123456"&gt;</code> is converted to<br>
     * <code>&lt;img src="/ajax/image/mail/compose/image?uid=71ff23e06f424cc5bcb08a92e006838a"&gt;</code> or<br>
     * <code>&lt;img src="/ajax/image/mail/compose/image?uid=71ff23e06f424cc5bcb08a92e006838a&id=26aa23e06f424cc5bcb08a92e006838a"&gt;</code>
     *
     * @param htmlContent The HTML content to replace in
     * @param optionalCompositionSpaceId The optional identifier for associated composition space; if present the identifier is considered when building image location for given <code>ImageDataSource</code> instance
     * @param contentId2InlineAttachments The detected inline images
     * @param imageDataSource The image data source to use
     * @param session The session providing user information
     * @return The (possibly) processed HTML content
     * @throws OXException If replacing <code>&lt;img&gt;</code> tags fails
     */
    public static String replaceCidInlineImages(String htmlContent, Optional<UUID> optionalCompositionSpaceId, Map<ContentId, Attachment> contentId2InlineAttachments, AbstractCompositionSpaceImageDataSource imageDataSource, Session session) throws OXException {
        // Fast check
        if (htmlContent.indexOf("<img") < 0) {
            return htmlContent;
        }

        // Check with matcher
        Matcher matcher = PATTERN_SRC.matcher(htmlContent);
        if (!matcher.find()) {
            return htmlContent;
        }

        StringBuffer sb = new StringBuffer(htmlContent.length());
        do {
            String imageTag = matcher.group();
            String srcValue = matcher.group(1);
            if (srcValue.startsWith("cid:")) {
                ContentId contentId = ContentId.valueOf(srcValue.substring(4));
                Attachment attachment = contentId2InlineAttachments.get(contentId);
                if (null != attachment) {
                    ImageLocation imageLocation = new ImageLocation.Builder(getUnformattedString(attachment.getId())).id(optionalCompositionSpaceId.isPresent() ? getUnformattedString(optionalCompositionSpaceId.get()) : null).optImageHost(HtmlProcessing.imageHost()).build();
                    String imageUrl = imageDataSource.generateUrl(imageLocation, session);
                    int st = matcher.start(1) - matcher.start();
                    int end = matcher.end(1) - matcher.start();
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(imageTag.substring(0, st) + imageUrl + imageTag.substring(end)));
                }
            }
        } while (matcher.find());
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static final Pattern PATTERN_IMAGE_ID = Pattern.compile("[?&]" + AJAXServlet.PARAMETER_UID + "=([^&]+)");

    private static final String UTF_8 = "UTF-8";

    private static final ConcurrentMap<AbstractCompositionSpaceImageDataSource, Pattern> CACHED_IMAGE_SRC_PATTERNS = new ConcurrentHashMap<>(4, 0.9F, 1);

    /*
     * Something like "/ajax/image/mail/compose/image..."
     */
    private static Pattern getImageSrcPattern(AbstractCompositionSpaceImageDataSource imageDataSource) {
        Pattern pattern = CACHED_IMAGE_SRC_PATTERNS.get(imageDataSource);
        if (pattern == null) {
            Pattern newPattern = Pattern.compile("[a-zA-Z_0-9&-.]+/(?:[a-zA-Z_0-9&-.]+/)*" + ImageActionFactory.ALIAS_APPENDIX + imageDataSource.getAlias(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            pattern = CACHED_IMAGE_SRC_PATTERNS.putIfAbsent(imageDataSource, newPattern);
            if (pattern == null) {
                pattern = newPattern;
            }
        }
        return pattern;
    }

    /*
     * Something like "/appsuite/api/mail/compose/5e9f9b6d15a94a31a8ba175489e5363a/attachments/8f119070e6af4143bd2f3c74bd8973a9..."
     */
    private static final Pattern PATTERN_IMAGE_SRC_START_BY_URL = Pattern.compile("[a-zA-Z_0-9&-.]+/(?:[a-zA-Z_0-9&-.]+/)*" + "mail/compose/" + "([.-_a-zA-Z0-9]+)" + "/attachments/" + "([.-_a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Gets all attachment UUIDs that are referenced in image URLs as part of <code>&lt;img&gt;</code> tags.
     *
     * @param htmlContent The content to parse
     * @param imageDataSource The image data source to use for determining image URLs
     * @return A list of found identifiers
     */
    public static List<UUID> getReferencedImageAttachmentIds(String htmlContent, AbstractCompositionSpaceImageDataSource imageDataSource) {
        // Fast check
        if (htmlContent.indexOf("<img") < 0) {
            return Collections.emptyList();
        }

        // Check with matcher
        Matcher matcher = PATTERN_SRC.matcher(htmlContent);
        if (!matcher.find()) {
            return Collections.emptyList();
        }

        List<UUID> attachmentIds = new ArrayList<>(4);
        Optional<Matcher> mailComposeUrlMatcher;
        do {
            UUID attachmentId = null;
            String srcValue = matcher.group(1);
            if (srcValue.indexOf(imageDataSource.getAlias()) > 0 && returnMatcherOnFind(getImageSrcPattern(imageDataSource), srcValue).isPresent()) {
                Matcher attachmentIdMatcher = PATTERN_IMAGE_ID.matcher(Strings.replaceSequenceWith(srcValue, "&amp;", '&'));
                if (attachmentIdMatcher.find()) {
                    attachmentId = parseAttachmentIdIfValid(AJAXUtility.decodeUrl(attachmentIdMatcher.group(1), UTF_8));
                }
            } else if (srcValue.indexOf("/mail/compose/") > 0 &&  (mailComposeUrlMatcher = returnMatcherOnFind(PATTERN_IMAGE_SRC_START_BY_URL, srcValue)).isPresent()) {
                attachmentId = parseAttachmentIdIfValid(AJAXUtility.decodeUrl(mailComposeUrlMatcher.get().group(2), UTF_8));
            }
            if (attachmentId != null) {
                attachmentIds.add(attachmentId);
            }
        } while (matcher.find());

        return attachmentIds;
    }

    private static Optional<Matcher> returnMatcherOnFind(Pattern pattern, CharSequence input) {
        Matcher matcher = pattern.matcher(InterruptibleCharSequence.valueOf(input));

        Future<Boolean> future = ThreadPools.getThreadPool().submit(new MatcherFindTask(matcher));

        try {
            return future.get(60, TimeUnit.SECONDS).booleanValue() ? Optional.of(matcher) : Optional.empty();
        } catch (InterruptedException e) {
            // Keep interrupted status
            LoggerHolder.LOG.warn("Interrupted while trying to parse: {}", input, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            LoggerHolder.LOG.warn("Failed to parse: {}", input, cause == null ? e : cause);
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw (cause instanceof RuntimeException) ? ((RuntimeException) cause) : new RuntimeException(e);
        } catch (TimeoutException e) {
            LoggerHolder.LOG.warn("Timed out while trying to parse: {}", input);
            future.cancel(true);
            return Optional.empty();
        }
    }

    /**
     * Replaces <code>&lt;img&gt;</code> tags providing an inline image through exchanging <code>"src"</code> value appropriately.
     * <p>
     * <code>&lt;img src="/ajax/image/mail/compose/image?uid=71ff23e06f424cc5bcb08a92e006838a"&gt;</code> is converted to<br>
     * <code>&lt;img src="cid:123456"&gt;</code>
     *
     * @param htmlContent The HTML content to replace in
     * @param attachmentId2inlineAttachments The detected inline images
     * @param contentId2InlineAttachment The map to fill with actually used inline attachments
     * @param fileAttachments The complete attachment mapping from which to remove actually used inline attachments
     * @param imageDataSource The image data source to use for determining image URLs
     * @return The (possibly) processed HTML content
     */
    public static String replaceLinkedInlineImages(String htmlContent, Map<String, Attachment> attachmentId2inlineAttachments, Map<ContentId, Attachment> contentId2InlineAttachment, Map<UUID, Attachment> fileAttachments, AbstractCompositionSpaceImageDataSource imageDataSource) {
        // Fast check
        if (htmlContent.indexOf("<img") < 0) {
            return htmlContent;
        }

        // Check with matcher
        Matcher matcher = PATTERN_SRC.matcher(htmlContent);
        if (!matcher.find()) {
            return htmlContent;
        }

        StringBuffer sb = new StringBuffer(htmlContent.length());
        Optional<Matcher> mailComposeUrlMatcher;
        do {
            String imageTag = matcher.group();
            String srcValue = matcher.group(1);
            if (srcValue.indexOf(imageDataSource.getAlias()) > 0 && returnMatcherOnFind(getImageSrcPattern(imageDataSource), srcValue).isPresent()) {
                Matcher attachmentIdMatcher = PATTERN_IMAGE_ID.matcher(Strings.replaceSequenceWith(srcValue, "&amp;", '&'));
                if (attachmentIdMatcher.find()) {
                    String attachmentId = AJAXUtility.decodeUrl(attachmentIdMatcher.group(1), UTF_8);
                    replaceLinkedInlineImage(attachmentId, imageTag, sb, matcher, attachmentId2inlineAttachments, contentId2InlineAttachment, fileAttachments);
                }
            } else if (srcValue.indexOf("/mail/compose/") > 0 &&  (mailComposeUrlMatcher = returnMatcherOnFind(PATTERN_IMAGE_SRC_START_BY_URL, srcValue)).isPresent()) {
                String attachmentId = AJAXUtility.decodeUrl(mailComposeUrlMatcher.get().group(2), UTF_8);
                replaceLinkedInlineImage(attachmentId, imageTag, sb, matcher, attachmentId2inlineAttachments, contentId2InlineAttachment, fileAttachments);
            }
        } while (matcher.find());
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static void replaceLinkedInlineImage(String attachmentId, String imageTag, StringBuffer sb, Matcher matcher, Map<String, Attachment> attachmentId2inlineAttachments, Map<ContentId, Attachment> contentId2InlineAttachment, Map<UUID, Attachment> fileAttachments) {
        Attachment attachment = attachmentId2inlineAttachments.get(attachmentId);
        if (null == attachment) {
            // No such inline image... Yield a blank "src" attribute for current <img> tag
            LoggerHolder.LOG.warn("No such inline image found for attachment identifier {}", attachmentId);
            matcher.appendReplacement(sb, "");
        } else {
            ContentId contentId = attachment.getContentIdAsObject();

            String imageUrl = "cid:" + contentId.getContentId();
            int st = matcher.start(1) - matcher.start();
            int end = matcher.end(1) - matcher.start();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(imageTag.substring(0, st) + imageUrl + imageTag.substring(end)));

            contentId2InlineAttachment.put(contentId, attachment);
            fileAttachments.remove(attachment.getId());
        }
    }

    /**
     * Replaces <code>&lt;img&gt;</code> tags providing an inline image through exchanging <code>"src"</code> value appropriately.
     * <p>
     * <code>&lt;img src="/ajax/image/mail/compose/image?id=5e9f9b6d15a94a31a8ba175489e5363a&uid=71ff23e06f424cc5bcb08a92e006838a"&gt;</code> is converted to<br>
     * <code>&lt;img src="cid:123456"&gt;</code>
     *
     * @param htmlContent The HTML content to replace in
     * @param contentIdsByAttachmentIds Mapping from known content IDs to attachment IDs
     * @param imageDataSource The image data source to use for determining image URLs
     * @return The (possibly) processed HTML content
     */
    public static String replaceLinkedInlineImages(String htmlContent, Map<UUID, ContentId> contentIdsByAttachmentIds, AbstractCompositionSpaceImageDataSource imageDataSource) {
        // Fast check
        if (htmlContent.indexOf("<img") < 0) {
            return htmlContent;
        }

        // Check with matcher
        Matcher matcher = PATTERN_SRC.matcher(htmlContent);
        if (!matcher.find()) {
            return htmlContent;
        }

        StringBuffer sb = new StringBuffer(htmlContent.length());
        Optional<Matcher> mailComposeUrlMatcher;
        do {
            String imageTag = matcher.group();
            String srcValue = matcher.group(1);
            if (srcValue.indexOf(imageDataSource.getAlias()) > 0 && returnMatcherOnFind(getImageSrcPattern(imageDataSource), srcValue).isPresent()) {
                Matcher attachmentIdMatcher = PATTERN_IMAGE_ID.matcher(Strings.replaceSequenceWith(srcValue, "&amp;", '&'));
                if (attachmentIdMatcher.find()) {
                    String attachmentId = AJAXUtility.decodeUrl(attachmentIdMatcher.group(1), UTF_8);
                    replaceLinkedInlineImage(attachmentId, imageTag, sb, matcher, contentIdsByAttachmentIds);
                }
            } else if (srcValue.indexOf("/mail/compose/") > 0 &&  (mailComposeUrlMatcher = returnMatcherOnFind(PATTERN_IMAGE_SRC_START_BY_URL, srcValue)).isPresent()) {
                String attachmentId = AJAXUtility.decodeUrl(mailComposeUrlMatcher.get().group(2), UTF_8);
                replaceLinkedInlineImage(attachmentId, imageTag, sb, matcher, contentIdsByAttachmentIds);
            }
        } while (matcher.find());
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static void replaceLinkedInlineImage(String attachmentIdStr, String imageTag, StringBuffer sb, Matcher matcher, Map<UUID, ContentId> contentIdsByAttachmentIds) {
        ContentId contentId = null;
        UUID attachmentId = parseAttachmentIdIfValid(attachmentIdStr);
        if (attachmentId != null) {
            contentId = contentIdsByAttachmentIds.get(attachmentId);
        }

        if (contentId == null) {
            // No such inline image... Yield a blank "src" attribute for current <img> tag
            LoggerHolder.LOG.warn("No such inline image found for attachment identifier {}", attachmentIdStr);
            matcher.appendReplacement(sb, "");
        } else {
            String imageUrl = "cid:" + contentId.getContentId();
            int st = matcher.start(1) - matcher.start();
            int end = matcher.end(1) - matcher.start();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(imageTag.substring(0, st) + imageUrl + imageTag.substring(end)));
        }
    }

    /**
     * Converts a UUID into a log-friendly object. If used as a positional log argument,
     * the UUID will appear as unformatted string in log messages.
     *
     * @param uuid The uuid
     * @return The log argument
     */
    public static Object getUUIDForLogging(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return new Object() {
            @Override
            public String toString() {
                return UUIDs.getUnformattedString(uuid);
            }
        };
    }

    /**
     * Builds a table layout for given composition space for logging purposes.
     *
     * @param compositionSpace The composition space
     * @param optionalUserAgent The optional User-Agent identifier
     * @return The console table
     */
    public static String buildConsoleTableFor(CompositionSpace compositionSpace, Optional<String> optionalUserAgent) {
        if (compositionSpace == null) {
            return "null";
        }

        Message message = compositionSpace.getMessage();
        if (message == null) {
            if (optionalUserAgent.isPresent()) {
                ConsoleTable.Builder table = ConsoleTable.builder(1, "ID", "Last-Modified", "User-Agent");
                table.addRow(compositionSpace.getId(), ISO8601Utils.format(new Date(compositionSpace.getLastModified()), false), optionalUserAgent.get());
                return table.build().buildTable();
            }

            ConsoleTable.Builder table = ConsoleTable.builder(1, "ID", "Last-Modified");
            table.addRow(compositionSpace.getId(), ISO8601Utils.format(new Date(compositionSpace.getLastModified()), false));
            return table.build().buildTable();
        }

        if (optionalUserAgent.isPresent()) {
            String contentHash = CompositionSpaces.getMD5HashForLogging(message.getContent());
            ConsoleTable.Builder table = ConsoleTable.builder(1, "ID", "Last-Modified", "Meta", "Subject", "To", "Content-Hash", "User-Agent");
            table.addRow(compositionSpace.getId(), ISO8601Utils.format(new Date(compositionSpace.getLastModified()), false), getValueForLogging(message.getMeta()), getValueForLogging(message.getSubject()), getValueForLogging(message.getTo()), contentHash, optionalUserAgent.get());
            return table.build().buildTable();
        }

        String contentHash = CompositionSpaces.getMD5HashForLogging(message.getContent());
        ConsoleTable.Builder table = ConsoleTable.builder(1, "ID", "Last-Modified", "Meta", "Subject", "To", "Content-Hash");
        table.addRow(compositionSpace.getId(), ISO8601Utils.format(new Date(compositionSpace.getLastModified()), false), getValueForLogging(message.getMeta()), getValueForLogging(message.getSubject()), getValueForLogging(message.getTo()), contentHash);
        return table.build().buildTable();
    }

    /**
     * Builds a table layout for given composition spaces for logging purposes.
     *
     * @param compositionSpaces The composition spaces
     * @param optionalUserAgent The optional User-Agent identifier
     * @return The console table
     */
    public static String buildConsoleTableFor(List<CompositionSpace> compositionSpaces, Optional<String> optionalUserAgent) {
        if (compositionSpaces == null) {
            return "null";
        }

        int size = compositionSpaces.size();
        if (size <= 0) {
            return "<empty>";
        }

        if (optionalUserAgent.isPresent()) {
            String userAgent = optionalUserAgent.get();
            ConsoleTable.Builder table = ConsoleTable.builder(size, "ID", "Last-Modified", "Meta", "Subject", "To", "Content-Hash", "User-Agent");
            for (CompositionSpace compositionSpace : compositionSpaces) {
                Message message = compositionSpace.getMessage();
                String contentHash = CompositionSpaces.getMD5HashForLogging(message.getContent());
                table.addRow(compositionSpace.getId(), ISO8601Utils.format(new Date(compositionSpace.getLastModified()), false), getValueForLogging(message.getMeta()), getValueForLogging(message.getSubject()), getValueForLogging(message.getTo()), contentHash, userAgent);
            }
            return table.build().buildTable();
        }

        ConsoleTable.Builder table = ConsoleTable.builder(size, "ID", "Last-Modified", "Meta", "Subject", "To", "Content-Hash");
        for (CompositionSpace compositionSpace : compositionSpaces) {
            Message message = compositionSpace.getMessage();
            String contentHash = CompositionSpaces.getMD5HashForLogging(message.getContent());
            table.addRow(compositionSpace.getId(), ISO8601Utils.format(new Date(compositionSpace.getLastModified()), false), getValueForLogging(message.getMeta()), getValueForLogging(message.getSubject()), getValueForLogging(message.getTo()), contentHash);
        }
        return table.build().buildTable();
    }

    private static String getValueForLogging(Object value) {
        if (value == null) {
            return "null";
        }

        String sValue = value.toString();
        return Strings.isEmpty(sValue) ? "<empty>" : sValue;
    }

    /**
     * Calculates the MD5 hash for given value for logging purposes.
     *
     * @param value The value
     * @return The hash or <code>"null"</code> if given value is <code>null</code>, <code>"&lt;empty&gt;"</code> if given value is empty or <code>"&lt;failure&gt;"</code> if hash calculation failed
     */
    public static String getMD5HashForLogging(String value) {
        if (value == null) {
            return "null";
        }
        if (Strings.isEmpty(value)) {
            return "<empty>";
        }
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            md5.update(value.getBytes(StandardCharsets.UTF_8));
            return Strings.asHex(md5.digest());
        } catch (Exception e) {
            return "<failure>";
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private static final class MatcherFindTask extends AbstractTrackableTask<Boolean> {

        private final Matcher matcher;

        MatcherFindTask(Matcher matcher) {
            super();
            this.matcher = matcher;
        }

        @Override
        public Boolean call() throws Exception {
            return Boolean.valueOf(matcher.find());
        }
    }

}
