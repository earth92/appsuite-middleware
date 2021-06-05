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

package com.openexchange.mail.parser.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import com.openexchange.exception.OXException;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MimeDefaultSession;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.converters.MimeMessageConverter;
import com.openexchange.mail.parser.ContentProvider;
import com.openexchange.mail.parser.MailMessageHandler;
import com.openexchange.mail.parser.MailMessageParser;
import com.openexchange.mail.utils.MessageUtility;
import com.openexchange.mail.uuencode.UUEncodedPart;

/**
 * {@link DumperMessageHandler} - For testing purposes
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class DumperMessageHandler implements MailMessageHandler {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DumperMessageHandler.class);

    private final boolean bodyOnly;

    private final StringBuilder strBuilder;

    /**
	 *
	 */
    public DumperMessageHandler(boolean bodyOnly) {
        super();
        strBuilder = new StringBuilder(8192 << 2);
        this.bodyOnly = bodyOnly;
    }

    public String getString() {
        return strBuilder.toString();
    }

    @Override
    public boolean handleMultipartEnd(MailPart mp, String id) throws OXException {
        return true;
    }

    @Override
    public boolean handleAttachment(MailPart part, boolean isInline, String baseContentType, String fileName, String id) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleAttachment:\n");
        strBuilder.append("isInline=").append(isInline).append('\n');
        strBuilder.append("ContentType=").append(baseContentType).append('\n');
        strBuilder.append("fileName=").append(fileName).append('\n');
        strBuilder.append("sequenceId=").append(id).append('\n');
        try {
            strBuilder.append("Content:\n").append(MessageUtility.readMailPart(part, "US-ASCII"));
        } catch (IOException e) {
            LOG.error("", e);
        }
        return true;
    }

    @Override
    public boolean handleBccRecipient(InternetAddress[] recipientAddrs) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleBccRecipient:\n");
        strBuilder.append("Bcc=").append(Arrays.toString(recipientAddrs)).append('\n');
        return true;
    }

    @Override
    public boolean handleCcRecipient(InternetAddress[] recipientAddrs) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleCcRecipient:\n");
        strBuilder.append("Cc=").append(Arrays.toString(recipientAddrs)).append('\n');
        return true;
    }

    @Override
    public boolean handleColorLabel(int colorLabel) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleColorLabel:\n");
        strBuilder.append("ColorLabel=").append(colorLabel).append('\n');
        return true;
    }

    @Override
    public boolean handleContentId(String contentId) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleContentId:\n");
        strBuilder.append("Content-ID=").append(contentId).append('\n');
        return true;
    }

    @Override
    public boolean handleFrom(InternetAddress[] fromAddrs) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleFrom:\n");
        strBuilder.append("From=").append(Arrays.toString(fromAddrs)).append('\n');
        return true;
    }

    @Override
    public boolean handleHeaders(int size, Iterator<Entry<String, String>> iter) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleHeaders:\n");
        for (int i = 0; i < size; i++) {
            final Map.Entry<String, String> e = iter.next();
            strBuilder.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return true;
    }

    @Override
    public boolean handleImagePart(MailPart part, String imageCID, String baseContentType, boolean isInline, String fileName, String id) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleImagePart:\n");
        strBuilder.append("ContentType=").append(baseContentType).append('\n');
        strBuilder.append("Content-ID=").append(imageCID).append('\n');
        strBuilder.append("isInline=").append(isInline).append('\n');
        strBuilder.append("fileName=").append(fileName).append('\n');
        strBuilder.append("sequenceId=").append(id).append('\n');
        try {
            strBuilder.append("Content:\n").append(MessageUtility.readStream(part.getInputStream(), "US-ASCII"));
        } catch (IOException e) {
            LOG.error("", e);
        }
        return true;
    }

    @Override
    public boolean handleInlineHtml(ContentProvider htmlContent, ContentType contentType, long size, String fileName, String id) throws OXException {
        strBuilder.append('\n').append("handleInlineHtml:\n");
        strBuilder.append("ContentType=").append(contentType).append('\n');
        strBuilder.append("Size=").append(size).append('\n');
        strBuilder.append("Filename=").append(fileName).append('\n');
        strBuilder.append("sequenceId=").append(id).append('\n');

        strBuilder.append("Content:\n").append(htmlContent.getContent());

        return true;
    }

    @Override
    public boolean handleInlinePlainText(String plainTextContent, ContentType contentType, long size, String fileName, String id) throws OXException {
        strBuilder.append('\n').append("handleInlinePlainText:\n");
        strBuilder.append("ContentType=").append(contentType).append('\n');
        strBuilder.append("Size=").append(size).append('\n');
        strBuilder.append("Filename=").append(fileName).append('\n');
        strBuilder.append("sequenceId=").append(id).append('\n');

        strBuilder.append("Content:\n").append(plainTextContent);
        return true;
    }

    @Override
    public boolean handleInlineUUEncodedAttachment(UUEncodedPart part, String id) throws OXException {
        return true;
    }

    @Override
    public boolean handleInlineUUEncodedPlainText(String decodedTextContent, ContentType contentType, int size, String fileName, String id) throws OXException {
        return true;
    }

    @Override
    public void handleMessageEnd(MailMessage msg) throws OXException {
    }

    @Override
    public boolean handleMultipart(MailPart mp, int bodyPartCount, String id) throws OXException {
        return true;
    }

    @Override
    public boolean handleNestedMessage(MailPart mailPart, String id) throws OXException {
        final Object content = mailPart.getContent();
        final MailMessage nestedMail;
        if (content instanceof MailMessage) {
            nestedMail = (MailMessage) content;
        } else if (content instanceof InputStream) {
            try {
                nestedMail = MimeMessageConverter.convertMessage(new MimeMessage(
                    MimeDefaultSession.getDefaultSession(),
                    (InputStream) content));
            } catch (MessagingException e) {
                throw MimeMailException.handleMessagingException(e);
            }
        } else {
            LOG.error("Ignoring nested message. Cannot handle part's content which should be a RFC822 message according to its content type: {}", (null == content ? "null" : content.getClass().getSimpleName()));
            return true;
        }
        final DumperMessageHandler handler = new DumperMessageHandler(bodyOnly);
        new MailMessageParser().parseMailMessage(nestedMail, handler, id);
        strBuilder.append(handler.getString());
        return true;
    }

    @Override
    public boolean handlePriority(int priority) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handlePriority:\n");
        strBuilder.append("Priority=").append(priority).append('\n');
        return true;
    }

    @Override
    public boolean handleMsgRef(String msgRef) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleMsgRef:\n");
        strBuilder.append("MsgRef=").append(msgRef).append('\n');
        return true;
    }

    @Override
    public boolean handleDispositionNotification(InternetAddress dispositionNotificationTo, boolean acknowledged) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleDispositionNotification:\n");
        strBuilder.append("DispositionNotificationTo=").append(dispositionNotificationTo.toUnicodeString()).append('\n');
        return true;
    }

    @Override
    public boolean handleReceivedDate(Date receivedDate) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleReceivedDate:\n");
        strBuilder.append("ReceivedDate=").append(receivedDate).append('\n');
        return true;
    }

    @Override
    public boolean handleSentDate(Date sentDate) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleSentDate:\n");
        strBuilder.append("SentDate=").append(sentDate).append('\n');
        return true;
    }

    @Override
    public boolean handleSpecialPart(MailPart part, String baseContentType, String fileName, String id) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleSpecialPart:\n");
        strBuilder.append("ContentType=").append(baseContentType).append('\n');
        strBuilder.append("filename=").append(fileName).append('\n');
        strBuilder.append("sequenceId=").append(id).append('\n');
        try {
            strBuilder.append("Content:\n").append(MessageUtility.readStream(part.getInputStream(), "US-ASCII"));
        } catch (IOException e) {
            LOG.error("", e);
        }
        return true;
    }

    @Override
    public boolean handleSubject(String subject) throws OXException {
        strBuilder.append('\n').append("handleSubject:\n");
        strBuilder.append("Subject=").append(subject).append('\n');
        return true;
    }

    @Override
    public boolean handleSystemFlags(int flags) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleSystemFlags:\n");
        strBuilder.append("Flags=").append(flags).append('\n');
        return true;
    }

    @Override
    public boolean handleToRecipient(InternetAddress[] recipientAddrs) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleToRecipient:\n");
        strBuilder.append("To=").append(Arrays.toString(recipientAddrs)).append('\n');
        return true;
    }

    @Override
    public boolean handleUserFlags(String[] userFlags) throws OXException {
        if (bodyOnly) {
            return true;
        }
        strBuilder.append('\n').append("handleUserFlags:\n");
        strBuilder.append("UserFlags=").append(Arrays.toString(userFlags)).append('\n');
        return true;
    }

}
