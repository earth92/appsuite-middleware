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

import static com.openexchange.mail.MailExceptionCode.getSize;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.upload.StreamedUploadFile;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.java.util.UUIDs;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.compose.Attachment.ContentDisposition;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.mail.mime.MimeTypes;
import com.openexchange.mail.parser.MailMessageParser;
import com.openexchange.session.Session;

/**
 * {@link AttachmentStorages} - A utility class for attachment storage.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.2
 */
public class AttachmentStorages {

    /**
     * Initializes a new {@link AttachmentStorages}.
     */
    private AttachmentStorages() {
        super();
    }

    /**
     * Generates an appropriate Content-ID for given attachment.
     *
     * @param attachmentId The attachment identifier
     * @return The Content-ID
     */
    public static ContentId generateContentIdForAttachmentId(UUID attachmentId) {
        return attachmentId == null ? null : ContentId.valueOf(new StringBuilder(64).append(UUIDs.getUnformattedString(attachmentId)).append("@open-xchange.com").toString());
    }

    private static final ContentDisposition ATTACHMENT = ContentDisposition.ATTACHMENT;
    private static final ContentDisposition INLINE = ContentDisposition.INLINE;

    /**
     * Creates an attachment description for given non-inline mail part.
     *
     * @param mailPart The mail part
     * @param partNumber The part's (sequence) number
     * @param size The size of the mail part or <code>-1</code> if unknown
     * @param compositionSpaceId The identifier of the composition space
     * @param session The session
     * @return The newly created attachment description
     */
    public static AttachmentDescription createAttachmentDescriptionFor(MailPart mailPart, int partNumber, long size, UUID compositionSpaceId, Session session) {
        AttachmentDescription attachment = new AttachmentDescription();
        String partId = mailPart.getFirstHeader(MessageHeaders.HDR_X_PART_ID);
        if (Strings.isNotEmpty(partId)) {
            UUID attachmentId = CompositionSpaces.parseAttachmentIdIfValid(partId);
            if (attachmentId != null) {
                attachment.setId(attachmentId);
            }
        }
        attachment.setCompositionSpaceId(compositionSpaceId);
        attachment.setContentDisposition(ATTACHMENT);
        attachment.setMimeType(mailPart.getContentType().getBaseType());
        String fileName = mailPart.getFileName();
        attachment.setName(Strings.isEmpty(fileName) ? MailMessageParser.generateFilename(Integer.toString(partNumber), mailPart.getContentType().getBaseType()) : fileName);
        attachment.setSize(size < 0 ? -1L : size);
        attachment.setOrigin(CompositionSpaces.hasVCardMarker(mailPart, session) ? AttachmentOrigin.VCARD : AttachmentOrigin.MAIL);
        return attachment;
    }

    /**
     * Creates an attachment description for given non-inline mail message.
     *
     * @param mailMessage The mail message
     * @param partNumber The (sequence) number
     * @param size The size of the mail message or <code>-1</code> if unknown
     * @param compositionSpaceId The identifier of the composition space
     * @return The newly created attachment description
     */
    public static AttachmentDescription createAttachmentDescriptionFor(MailMessage mailMessage, int partNumber, long size, UUID compositionSpaceId) {
        AttachmentDescription attachment = new AttachmentDescription();
        String partId = mailMessage.getFirstHeader(MessageHeaders.HDR_X_PART_ID);
        if (Strings.isNotEmpty(partId)) {
            UUID attachmentId = CompositionSpaces.parseAttachmentIdIfValid(partId);
            if (attachmentId != null) {
                attachment.setId(attachmentId);
            }
        }
        attachment.setCompositionSpaceId(compositionSpaceId);
        attachment.setContentDisposition(ATTACHMENT);
        attachment.setMimeType(MimeTypes.MIME_MESSAGE_RFC822);
        String subject = mailMessage.getSubject();
        attachment.setName((Strings.isEmpty(subject) ? "mail" + (partNumber > 0 ? Integer.toString(partNumber) : "") : subject.replaceAll("\\p{Blank}+", "_")) + ".eml");
        attachment.setSize(size < 0 ? -1L : size);
        attachment.setOrigin(AttachmentOrigin.MAIL);
        return attachment;
    }

    /**
     * Creates an attachment description for given inline mail part.
     *
     * @param mailPart The mail part
     * @param contentId The value for the Content-Id header
     * @param partNumber The part's (sequence) number
     * @param compositionSpaceId The identifier of the composition space
     * @return The newly created attachment description
     */
    public static AttachmentDescription createInlineAttachmentDescriptionFor(MailPart mailPart, ContentId contentId, int partNumber, UUID compositionSpaceId) {
        AttachmentDescription attachment = new AttachmentDescription();
        String partId = mailPart.getFirstHeader(MessageHeaders.HDR_X_PART_ID);
        if (Strings.isNotEmpty(partId)) {
            UUID attachmentId = CompositionSpaces.parseAttachmentIdIfValid(partId);
            if (attachmentId != null) {
                attachment.setId(attachmentId);
            }
        }
        attachment.setCompositionSpaceId(compositionSpaceId);
        attachment.setContentDisposition(INLINE);
        attachment.setContentId(contentId);
        attachment.setMimeType(mailPart.getContentType().getBaseType());
        String fileName = mailPart.getFileName();
        attachment.setName(Strings.isEmpty(fileName) ? MailMessageParser.generateFilename(Integer.toString(partNumber), mailPart.getContentType().getBaseType()) : fileName);
        attachment.setOrigin(AttachmentOrigin.MAIL);
        return attachment;
    }

    /**
     * Creates an attachment description for given user vCard.
     *
     * @param userVCard The user vCard
     * @param compositionSpaceId The identifier of the composition space
     * @return The newly created attachment description
     */
    public static AttachmentDescription createVCardAttachmentDescriptionFor(VCardAndFileName userVCard, UUID compositionSpaceId, boolean isSessionUserVCard) {
        // Compile attachment
        AttachmentDescription attachment = new AttachmentDescription();
        attachment.setCompositionSpaceId(compositionSpaceId);
        attachment.setContentDisposition(ContentDisposition.ATTACHMENT);
        attachment.setMimeType(MimeTypes.MIME_TEXT_VCARD + "; charset=\"UTF-8\"");
        attachment.setName(userVCard.getFileName());
        attachment.setSize(userVCard.getVcard().length);
        attachment.setOrigin(isSessionUserVCard ? AttachmentOrigin.VCARD : AttachmentOrigin.CONTACT);
        return attachment;
    }

    /**
     * Creates an attachment description for given upload file.
     *
     * @param uploadFile The upload file
     * @param disposition The disposition to set
     * @param compositionSpaceId The The identifier of the composition space
     * @return The newly created attachment description
     * @throws OXException If attachment description cannot be created
     */
    public static AttachmentDescription createUploadFileAttachmentDescriptionFor(StreamedUploadFile uploadFile, String disposition, UUID compositionSpaceId) throws OXException {
        ContentDisposition contentDisposition = ContentDisposition.dispositionFor(disposition);
        return createUploadFileAttachmentDescriptionFor(uploadFile, contentDisposition, compositionSpaceId);
    }

    /**
     * Creates an attachment description for given upload file.
     *
     * @param uploadFile The upload file
     * @param contentDisposition The disposition to set
     * @param compositionSpaceId The The identifier of the composition space
     * @return The newly created attachment description
     * @throws OXException If attachment description cannot be created
     */
    public static AttachmentDescription createUploadFileAttachmentDescriptionFor(StreamedUploadFile uploadFile, ContentDisposition contentDisposition, UUID compositionSpaceId) throws OXException {
        AttachmentDescription attachment = new AttachmentDescription();
        attachment.setCompositionSpaceId(compositionSpaceId);
        attachment.setContentDisposition(null == contentDisposition ? ATTACHMENT : contentDisposition);
        ContentType contentType = new ContentType(uploadFile.getContentType());
        attachment.setMimeType(contentType.getBaseType());
        {
            String fileName = uploadFile.getPreparedFileName();
            if (fileName.indexOf('.') < 0) {
                // Ensure file extension is present in file name
                String fileExtension = MimeType2ExtMap.getFileExtension(contentType.getBaseType(), null);
                if (fileExtension != null) {
                    fileName = new StringBuilder(fileName).append('.').append(fileExtension).toString();
                }
            }
            attachment.setName(fileName);
        }
        attachment.setOrigin(AttachmentOrigin.UPLOAD);
        return attachment;
    }

    /**
     * Saves the specified attachment binary data and meta data using given storage instance.
     *
     * @param input The input stream providing binary data
     * @param attachment The attachment providing meta data
     * @param session The session providing user information
     * @param attachmentStorage The storage instance to use
     * @return The resulting attachment
     * @throws OXException If saving attachment fails
     * @see #saveAttachment(InputStream, AttachmentDescription, Optional, Session, AttachmentStorage)
     */
    public static Attachment saveAttachment(InputStream input, AttachmentDescription attachmentDesc, Session session, AttachmentStorage attachmentStorage) throws OXException {
        return saveAttachment(input, attachmentDesc, Optional.empty(), session, attachmentStorage);
    }

    /**
     * Saves the specified attachment binary data and meta data using given storage instance.
     *
     * @param input The input stream providing binary data
     * @param attachment The attachment providing meta data
     * @param optionalEncrypt The optional encryption flag on initial opening of a composition space. If present and <code>true</code> the
     *                        attachment to save is supposed to be encrypted according to caller. If present and <code>false</code>  the
     *                        attachment to save is <b>not</b> supposed to be encrypted according to caller. If absent, encryption is
     *                        automatically determined.<br>
     *                        <b>Note</b>: The flag MUST be aligned to associated composition space
     * @param session The session providing user information
     * @param attachmentStorage The storage instance to use
     * @return The resulting attachment
     * @throws OXException If saving attachment fails
     */
    public static Attachment saveAttachment(InputStream input, AttachmentDescription attachmentDesc, Optional<Boolean> optionalEncrypt, Session session, AttachmentStorage attachmentStorage) throws OXException {
        Attachment savedAttachment = null;
        InputStream in = input;
        try {
            // Optimistic save
            savedAttachment = attachmentStorage.saveAttachment(in, attachmentDesc, null, optionalEncrypt, session);
            Streams.close(in);
            in = null;

            // Check if max. mail size might be exceeded
            long maxMailSize = MailProperties.getInstance().getMaxMailSize(session.getUserId(), session.getContextId());
            if (maxMailSize > 0) {
                SizeReturner sizeReturner = attachmentStorage.getSizeOfAttachmentsByCompositionSpace(savedAttachment.getCompositionSpaceId(), session);
                if (sizeReturner.getTotalSize() > maxMailSize) {
                    throw MailExceptionCode.MAX_MESSAGE_SIZE_EXCEEDED.create(getSize(maxMailSize, 0, false, true));
                }
            }

            // All fine. Return newly saved attachment
            Attachment retval = savedAttachment;
            savedAttachment = null; // Avoid premature deletion
            return retval;
        } finally {
            Streams.close(in);
            if (null != savedAttachment) {
                attachmentStorage.deleteAttachment(savedAttachment.getId(), session);
            }
        }
    }

}
