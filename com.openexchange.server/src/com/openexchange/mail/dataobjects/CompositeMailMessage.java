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

package com.openexchange.mail.dataobjects;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import com.openexchange.exception.OXException;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.mime.ContentDisposition;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.MimeDefaultSession;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.datasource.StreamDataSource;
import com.openexchange.mail.mime.utils.MimeMessageUtility;
import com.openexchange.mail.utils.MessageUtility;

/**
 * {@link CompositeMailMessage} - Extends the common {@link MailMessage} class by the possibility to add extra parts to an existing
 * {@link MailMessage} instance whose MIME type is <code>multipart/*</code>
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class CompositeMailMessage extends MailMessage {

    private static final long serialVersionUID = -3153633514125635904L;

    private final MailMessage delegate;

    private final transient List<MailPart> additionalParts;

    private final int delegateEnclosedCount;

    private static final String MULTIPART = "multipart/";

    /**
     * Constructor
     *
     * @param delegate The delegate mail
     * @throws OXException If invocation of {@link MailMessage#getEnclosedCount()} fails
     */
    public CompositeMailMessage(MailMessage delegate) throws OXException {
        super();
        if (!delegate.getContentType().startsWith(MULTIPART)) {
            throw new IllegalArgumentException("Specified delegate mail must be of MIME type multipart/*");
        }
        this.delegate = delegate;
        if (delegate.containsAppendVCard()) {
            setAppendVCard(delegate.isAppendVCard());
        }
        if (delegate.containsBcc()) {
            addBcc(delegate.getBcc());
        }
        if (delegate.containsReplyTo()) {
            addReplyTo(delegate.getReplyTo());
        }
        if (delegate.containsCc()) {
            addCc(delegate.getCc());
        }
        if (delegate.containsColorLabel()) {
            setColorLabel(delegate.getColorLabel());
        }
        if (delegate.containsContentId()) {
            setContentId(delegate.getContentId());
        }
        if (delegate.containsContentType()) {
            setContentType(delegate.getContentType());
        }
        if (delegate.containsContentDisposition()) {
            setContentDisposition(delegate.getContentDisposition());
        }
        if (delegate.containsDispositionNotification()) {
            setDispositionNotification(delegate.getDispositionNotification());
        }
        if (delegate.containsFileName()) {
            setFileName(delegate.getFileName());
        }
        if (delegate.containsFlags()) {
            setFlags(delegate.getFlags());
        }
        if (delegate.containsFolder()) {
            setFolder(delegate.getFolder());
        }
        if (delegate.containsFrom()) {
            addFrom(delegate.getFrom());
        }
        if (delegate.containsHasAttachment()) {
            setHasAttachment(delegate.isHasAttachment());
        }
        if (delegate.containsAlternativeHasAttachment()) {
            setAlternativeHasAttachment(delegate.isAlternativeHasAttachment());
        }
        if (delegate.containsHeaders()) {
            final int len = delegate.getHeadersSize();
            final Iterator<Map.Entry<String, String>> iter = delegate.getHeadersIterator();
            for (int i = 0; i < len; i++) {
                final Map.Entry<String, String> e = iter.next();
                addHeader(e.getKey(), e.getValue());
            }
        }
        if (delegate.containsMsgref()) {
            setMsgref(delegate.getMsgref());
        }
        if (delegate.containsPriority()) {
            setPriority(delegate.getPriority());
        }
        if (delegate.containsReceivedDate()) {
            setReceivedDate(delegate.getReceivedDate());
        }
        if (delegate.containsSentDate()) {
            setSentDate(delegate.getSentDate());
        }
        if (delegate.containsSequenceId()) {
            setSequenceId(delegate.getSequenceId());
        }
        if (delegate.containsSize()) {
            setSize(delegate.getSize());
        }
        if (delegate.containsSubject()) {
            setSubject(delegate.getSubject(), delegate.isSubjectDecoded());
        }
        if (delegate.containsThreadLevel()) {
            setThreadLevel(delegate.getThreadLevel());
        }
        if (delegate.containsTo()) {
            addTo(delegate.getTo());
        }
        setMailId(delegate.getMailId());
        if (delegate.containsUserFlags()) {
            addUserFlags(delegate.getUserFlags());
        }
        additionalParts = new ArrayList<MailPart>();
        delegateEnclosedCount = delegate.getEnclosedCount();
    }

    /**
     * Appends the specified mail part
     *
     * @param mailPart The mail part to add
     */
    public void addAdditionalParts(MailPart mailPart) {
        additionalParts.add(mailPart);
    }

    /**
     * Removes the mail part at the specified position. Shifts any subsequent mail parts to the left (subtracts one from their indices).
     * Returns the mail part that was removed.
     *
     * @param index The index
     * @return The mail part that was removed.
     */
    public MailPart removeAdditionalParts(int index) {
        return additionalParts.remove(index);
    }

    @Override
    public Object getContent() throws OXException {
        return delegate.getContent();
    }

    @Override
    public DataHandler getDataHandler() throws OXException {
        return delegate.getDataHandler();
    }

    @Override
    public int getEnclosedCount() throws OXException {
        return delegateEnclosedCount + additionalParts.size();
    }

    @Override
    public MailPart getEnclosedMailPart(int index) throws OXException {
        if (delegateEnclosedCount > 0) {
            /*
             * Delegate mail holds enclosed parts
             */
            if (index >= delegateEnclosedCount) {
                try {
                    return additionalParts.get(index - delegateEnclosedCount);
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }
            }
            return delegate.getEnclosedMailPart(index);
        }
        try {
            return additionalParts.get(index);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexOutOfBoundsException("No mail part at index " + index);
        }
    }

    @Override
    public InputStream getInputStream() throws OXException {
        return delegate.getInputStream();
    }

    @Override
    public void loadContent() throws OXException {
        delegate.loadContent();
    }

    @Override
    public void prepareForCaching() {
        delegate.prepareForCaching();
    }

    @Override
    public String getMailId() {
        return delegate.getMailId();
    }

    @Override
    public void setMailId(String id) {
        delegate.setMailId(id);
    }

    @Override
    public int getUnreadMessages() {
        return delegate.getUnreadMessages();
    }

    @Override
    public void setUnreadMessages(int unreadMessages) {
        delegate.setUnreadMessages(unreadMessages);
    }

    @Override
    public boolean hasHeaders(String... names) {
        return delegate.hasHeaders(names);
    }

    @Override
    public Date getReceivedDateDirect() {
        return delegate.getReceivedDateDirect();
    }

    @Override
    public int getRecentCount() {
        return delegate.getRecentCount();
    }

    @Override
    public boolean containsRecentCount() {
        return delegate.containsRecentCount();
    }

    @Override
    public void removeRecentCount() {
        delegate.removeRecentCount();
    }

    @Override
    public void setRecentCount(int recentCount) {
        delegate.setRecentCount(recentCount);
    }

    @Override
    public void writeTo(OutputStream out) throws OXException {
        if (additionalParts.isEmpty()) {
            delegate.writeTo(out);
            return;
        }
        try {
            final MimeMessage mimeMessage = new MimeMessage(MimeDefaultSession.getDefaultSession());
            /*
             * Copy headers
             */
            final int size = getHeadersSize();
            final Iterator<Map.Entry<String, String>> iter = getHeadersIterator();
            for (int i = 0; i < size; i++) {
                final Map.Entry<String, String> entry = iter.next();
                mimeMessage.addHeader(entry.getKey(), entry.getValue());
            }
            final MimeMultipart mimeMultipart = new MimeMultipart("mixed");
            /*
             * Add parts from delegate
             */
            for (int i = 0; i < delegateEnclosedCount; i++) {
                final MailPart mp = delegate.getEnclosedMailPart(i);
                final MimeBodyPart bodyPart = new MimeBodyPart();
                {
                    final StreamDataSource.InputStreamProvider isp = new StreamDataSource.InputStreamProvider() {

                        @Override
                        public InputStream getInputStream() throws IOException {
                            try {
                                return mp.getInputStream();
                            } catch (OXException e) {
                                final IOException io = new IOException(e.getMessage());
                                io.initCause(e);
                                throw io;
                            }
                        }

                        @Override
                        public String getName() {
                            return null;
                        }
                    };
                    bodyPart.setDataHandler(new DataHandler(new StreamDataSource(isp, mp.getContentType().toString())));
                }
                final String fileName = mp.getFileName();
                if (fileName != null && !mp.getContentType().containsNameParameter()) {
                    mp.getContentType().setNameParameter(fileName);
                }
                bodyPart.setHeader(MessageHeaders.HDR_CONTENT_TYPE, MimeMessageUtility.foldContentType(mp.getContentType().toString()));
                final String disposition = bodyPart.getHeader(MessageHeaders.HDR_CONTENT_DISPOSITION, null);
                final ContentDisposition contentDisposition;
                if (disposition == null) {
                    contentDisposition = mp.getContentDisposition();
                } else {
                    contentDisposition = new ContentDisposition(disposition);
                    contentDisposition.setDisposition(mp.getContentDisposition().getDisposition());
                }
                if (fileName != null && !contentDisposition.containsFilenameParameter()) {
                    contentDisposition.setFilenameParameter(fileName);
                }
                bodyPart.setHeader(MessageHeaders.HDR_CONTENT_DISPOSITION, MimeMessageUtility.foldContentDisposition(contentDisposition.toString()));
                mimeMultipart.addBodyPart(bodyPart);
            }
            /*
             * Add additional parts
             */
            for (MailPart mp : additionalParts) {
                final MimeBodyPart bodyPart = new MimeBodyPart();
                {
                    final StreamDataSource.InputStreamProvider isp = new StreamDataSource.InputStreamProvider() {

                        @Override
                        public InputStream getInputStream() throws IOException {
                            try {
                                return mp.getInputStream();
                            } catch (OXException e) {
                                final IOException io = new IOException(e.getMessage());
                                io.initCause(e);
                                throw io;
                            }
                        }

                        @Override
                        public String getName() {
                            return null;
                        }
                    };
                    bodyPart.setDataHandler(new DataHandler(new StreamDataSource(isp, mp.getContentType().toString())));
                }
                final String fileName = mp.getFileName();
                if (fileName != null && !mp.getContentType().containsNameParameter()) {
                    mp.getContentType().setNameParameter(fileName);
                }
                bodyPart.setHeader(MessageHeaders.HDR_CONTENT_TYPE, MimeMessageUtility.foldContentType(mp.getContentType().toString()));
                final String disposition = bodyPart.getHeader(MessageHeaders.HDR_CONTENT_DISPOSITION, null);
                final ContentDisposition contentDisposition;
                if (disposition == null) {
                    contentDisposition = mp.getContentDisposition();
                } else {
                    contentDisposition = new ContentDisposition(disposition);
                    contentDisposition.setDisposition(mp.getContentDisposition().getDisposition());
                }
                if (fileName != null && !contentDisposition.containsFilenameParameter()) {
                    contentDisposition.setFilenameParameter(fileName);
                }
                bodyPart.setHeader(MessageHeaders.HDR_CONTENT_DISPOSITION, MimeMessageUtility.foldContentDisposition(contentDisposition.toString()));
                mimeMultipart.addBodyPart(bodyPart);
            }
            MessageUtility.setContent(mimeMultipart, mimeMessage);
            // mimeMessage.setContent(mimeMultipart);
            mimeMessage.writeTo(out);
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
