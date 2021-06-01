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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.activation.DataHandler;
import javax.mail.internet.InternetAddress;
import com.openexchange.exception.OXException;
import com.openexchange.mail.FullnameArgument;
import com.openexchange.mail.MailPath;
import com.openexchange.mail.mime.ContentDisposition;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.HeaderCollection;

/**
 * {@link DraftMailMessage} - The Draft mail message with special handling for {@link #getReceivedDate()} and {@link #getSentDate()}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class DraftMailMessage extends MailMessage {

    private static final long serialVersionUID = -6341400961869347788L;

    private final MailMessage message;

    @Override
    public void addReplyTo(InternetAddress addr) {
        message.addReplyTo(addr);
    }

    @Override
    public void addReplyTo(InternetAddress[] addrs) {
        message.addReplyTo(addrs);
    }

    @Override
    public boolean containsReplyTo() {
        return message.containsReplyTo();
    }

    @Override
    public void removeReplyTo() {
        message.removeReplyTo();
    }

    @Override
    public InternetAddress[] getReplyTo() {
        return message.getReplyTo();
    }

    @Override
    public boolean isUnseen() {
        return message.isUnseen();
    }

    @Override
    public boolean containsMessageId() {
        return message.containsMessageId();
    }

    @Override
    public void removeMessageId() {
        message.removeMessageId();
    }

    @Override
    public void setMessageId(String messageId) {
        message.setMessageId(messageId);
    }

    @Override
    public String getInReplyTo() {
        return message.getInReplyTo();
    }

    @Override
    public boolean containsReferences() {
        return message.containsReferences();
    }

    @Override
    public void removeReferences() {
        message.removeReferences();
    }

    @Override
    public void setReferences(String sReferences) {
        message.setReferences(sReferences);
    }

    @Override
    public void setReferences(String[] references) {
        message.setReferences(references);
    }

    @Override
    public ContentType getContentType() {
        return message.getContentType();
    }

    @Override
    public boolean containsContentType() {
        return message.containsContentType();
    }

    @Override
    public void removeContentType() {
        message.removeContentType();
    }

    @Override
    public void setContentType(ContentType contentType) {
        message.setContentType(contentType);
    }

    @Override
    public void setContentType(String contentType) throws OXException {
        message.setContentType(contentType);
    }

    @Override
    public ContentDisposition getContentDisposition() {
        return message.getContentDisposition();
    }

    @Override
    public boolean containsContentDisposition() {
        return message.containsContentDisposition();
    }

    @Override
    public void removeContentDisposition() {
        message.removeContentDisposition();
    }

    @Override
    public void setContentDisposition(String disposition) throws OXException {
        message.setContentDisposition(disposition);
    }

    @Override
    public void setContentDisposition(ContentDisposition disposition) {
        message.setContentDisposition(disposition);
    }

    @Override
    public String toString() {
        return message.toString();
    }

    @Override
    public String getFileName() {
        return message.getFileName();
    }

    @Override
    public boolean containsFileName() {
        return message.containsFileName();
    }

    @Override
    public void removeFileName() {
        message.removeFileName();
    }

    @Override
    public void setFileName(String fileName) {
        message.setFileName(fileName);
    }

    @Override
    public void addHeader(String name, String value) {
        message.addHeader(name, value);
    }

    @Override
    public void setHeader(String name, String value) {
        message.setHeader(name, value);
    }

    @Override
    public void addHeaders(HeaderCollection headers) {
        message.addHeaders(headers);
    }

    @Override
    public boolean containsHeaders() {
        return message.containsHeaders();
    }

    @Override
    public void removeHeaders() {
        message.removeHeaders();
    }

    @Override
    public int getHeadersSize() {
        return message.getHeadersSize();
    }

    @Override
    public Iterator<Entry<String, String>> getHeadersIterator() {
        return message.getHeadersIterator();
    }

    @Override
    public boolean containsHeader(String name) {
        return message.containsHeader(name);
    }

    @Override
    public String[] getHeader(String name) {
        return message.getHeader(name);
    }

    @Override
    public void addFrom(InternetAddress addr) {
        message.addFrom(addr);
    }

    @Override
    public String getFirstHeader(String name) {
        return message.getFirstHeader(name);
    }

    @Override
    public void addFrom(InternetAddress[] addrs) {
        message.addFrom(addrs);
    }

    @Override
    public String getHeader(String name, String delimiter) {
        return message.getHeader(name, delimiter);
    }

    @Override
    public boolean containsFrom() {
        return message.containsFrom();
    }

    @Override
    public void removeFrom() {
        message.removeFrom();
    }

    @Override
    public InternetAddress[] getFrom() {
        return message.getFrom();
    }

    @Override
    public String getHeader(String name, char delimiter) {
        return message.getHeader(name, delimiter);
    }

    @Override
    public void addTo(InternetAddress addr) {
        message.addTo(addr);
    }

    @Override
    public HeaderCollection getHeaders() {
        return message.getHeaders();
    }

    @Override
    public void addTo(InternetAddress[] addrs) {
        message.addTo(addrs);
    }

    @Override
    public Iterator<Entry<String, String>> getNonMatchingHeaders(String[] nonMatchingHeaders) {
        return message.getNonMatchingHeaders(nonMatchingHeaders);
    }

    @Override
    public boolean containsTo() {
        return message.containsTo();
    }

    @Override
    public void removeTo() {
        message.removeTo();
    }

    @Override
    public Iterator<Entry<String, String>> getMatchingHeaders(String[] matchingHeaders) {
        return message.getMatchingHeaders(matchingHeaders);
    }

    @Override
    public InternetAddress[] getTo() {
        return message.getTo();
    }

    @Override
    public void removeHeader(String name) {
        message.removeHeader(name);
    }

    @Override
    public boolean hasHeaders(String... names) {
        return message.hasHeaders(names);
    }

    @Override
    public void addCc(InternetAddress addr) {
        message.addCc(addr);
    }

    @Override
    public void addCc(InternetAddress[] addrs) {
        message.addCc(addrs);
    }

    @Override
    public long getSize() {
        return message.getSize();
    }

    @Override
    public boolean containsSize() {
        return message.containsSize();
    }

    @Override
    public void removeSize() {
        message.removeSize();
    }

    @Override
    public boolean containsCc() {
        return message.containsCc();
    }

    @Override
    public void setSize(long size) {
        message.setSize(size);
    }

    @Override
    public void removeCc() {
        message.removeCc();
    }

    @Override
    public String getContentId() {
        return message.getContentId();
    }

    @Override
    public InternetAddress[] getCc() {
        return message.getCc();
    }

    @Override
    public boolean containsContentId() {
        return message.containsContentId();
    }

    @Override
    public void removeContentId() {
        message.removeContentId();
    }

    @Override
    public void setContentId(String contentId) {
        message.setContentId(contentId);
    }

    @Override
    public void addBcc(InternetAddress addr) {
        message.addBcc(addr);
    }

    @Override
    public String getSequenceId() {
        return message.getSequenceId();
    }

    @Override
    public boolean containsSequenceId() {
        return message.containsSequenceId();
    }

    @Override
    public void addBcc(InternetAddress[] addrs) {
        message.addBcc(addrs);
    }

    @Override
    public void removeSequenceId() {
        message.removeSequenceId();
    }

    @Override
    public void setSequenceId(String sequenceId) {
        message.setSequenceId(sequenceId);
    }

    @Override
    public boolean containsBcc() {
        return message.containsBcc();
    }

    @Override
    public MailPath getMsgref() {
        return message.getMsgref();
    }

    @Override
    public void removeBcc() {
        message.removeBcc();
    }

    @Override
    public InternetAddress[] getBcc() {
        return message.getBcc();
    }

    @Override
    public boolean containsMsgref() {
        return message.containsMsgref();
    }

    @Override
    public int getFlags() {
        return message.getFlags();
    }

    @Override
    public void removeMsgref() {
        message.removeMsgref();
    }

    @Override
    public boolean isAnswered() {
        return message.isAnswered();
    }

    @Override
    public void setMsgref(MailPath msgref) {
        message.setMsgref(msgref);
    }

    @Override
    public boolean isDeleted() {
        return message.isDeleted();
    }

    @Override
    public boolean isDraft() {
        return message.isDraft();
    }

    @Override
    public boolean isFlagged() {
        return message.isFlagged();
    }

    @Override
    public boolean isRecent() {
        return message.isRecent();
    }

    @Override
    public boolean hasEnclosedParts() throws OXException {
        return message.hasEnclosedParts();
    }

    @Override
    public boolean isSeen() {
        return message.isSeen();
    }

    @Override
    public boolean isSpam() {
        return message.isSpam();
    }

    @Override
    public Object getContent() throws OXException {
        return message.getContent();
    }

    @Override
    public boolean isForwarded() {
        return message.isForwarded();
    }

    @Override
    public boolean isReadAcknowledgment() {
        return message.isReadAcknowledgment();
    }

    @Override
    public DataHandler getDataHandler() throws OXException {
        return message.getDataHandler();
    }

    @Override
    public boolean isUser() {
        return message.isUser();
    }

    @Override
    public boolean containsFlags() {
        return message.containsFlags();
    }

    @Override
    public void removeFlags() {
        message.removeFlags();
    }

    @Override
    public InputStream getInputStream() throws OXException {
        return message.getInputStream();
    }

    @Override
    public void setFlags(int flags) {
        message.setFlags(flags);
    }

    @Override
    public void setFlag(int flag, boolean enable) throws OXException {
        message.setFlag(flag, enable);
    }

    @Override
    public int getEnclosedCount() throws OXException {
        return message.getEnclosedCount();
    }

    @Override
    public MailPart getEnclosedMailPart(int index) throws OXException {
        return message.getEnclosedMailPart(index);
    }

    @Override
    public boolean isPrevSeen() {
        return message.isPrevSeen();
    }

    @Override
    public void loadContent() throws OXException {
        message.loadContent();
    }

    @Override
    public boolean containsPrevSeen() {
        return message.containsPrevSeen();
    }

    @Override
    public void removePrevSeen() {
        message.removePrevSeen();
    }

    @Override
    public void setPrevSeen(boolean prevSeen) {
        message.setPrevSeen(prevSeen);
    }

    @Override
    public void writeTo(OutputStream out) throws OXException {
        message.writeTo(out);
    }

    @Override
    public int getThreadLevel() {
        return message.getThreadLevel();
    }

    @Override
    public boolean containsThreadLevel() {
        return message.containsThreadLevel();
    }

    @Override
    public void removeThreadLevel() {
        message.removeThreadLevel();
    }

    @Override
    public void setThreadLevel(int threadLevel) {
        message.setThreadLevel(threadLevel);
    }

    @Override
    public String getSource() throws OXException {
        return message.getSource();
    }

    @Override
    public String getSubject() {
        return message.getSubject();
    }

    @Override
    public byte[] getSourceBytes() throws OXException {
        return message.getSourceBytes();
    }

    @Override
    public boolean containsSubject() {
        return message.containsSubject();
    }

    @Override
    public void removeSubject() {
        message.removeSubject();
    }

    @Override
    public void prepareForCaching() {
        message.prepareForCaching();
    }

    @Override
    public void setSubject(String subject) {
        message.setSubject(subject);
    }

    @Override
    public void setSubject(String subject, boolean decoded) {
        message.setSubject(subject, decoded);
    }

    @Override
    public Date getSentDate() {
        final Date sentDate = message.getSentDate();
        if (null == sentDate) {
            final Date receivedDate = message.getReceivedDate();
            return null == receivedDate ? sentDate : receivedDate;
        }
        return sentDate;
    }

    @Override
    public boolean containsSentDate() {
        return message.containsSentDate();
    }

    @Override
    public void removeSentDate() {
        message.removeSentDate();
    }

    @Override
    public void setSentDate(Date sentDate) {
        message.setSentDate(sentDate);
    }

    @Override
    public Date getReceivedDate() {
        final Date receivedDate = message.getReceivedDate();
        if (null == receivedDate) {
            final Date sentDate = message.getSentDate();
            return null == sentDate ? receivedDate : sentDate;
        }
        return receivedDate;
    }

    @Override
    public Date getReceivedDateDirect() {
        return message.getReceivedDateDirect();
    }

    @Override
    public boolean containsReceivedDate() {
        return message.containsReceivedDate();
    }

    @Override
    public void removeReceivedDate() {
        message.removeReceivedDate();
    }

    @Override
    public void setReceivedDate(Date receivedDate) {
        message.setReceivedDate(receivedDate);
    }

    @Override
    public void addUserFlag(String userFlag) {
        message.addUserFlag(userFlag);
    }

    @Override
    public void addUserFlags(String[] userFlags) {
        message.addUserFlags(userFlags);
    }

    @Override
    public boolean containsUserFlags() {
        return message.containsUserFlags();
    }

    @Override
    public void removeUserFlags() {
        message.removeUserFlags();
    }

    @Override
    public String[] getUserFlags() {
        return message.getUserFlags();
    }

    @Override
    public int getColorLabel() {
        return message.getColorLabel();
    }

    @Override
    public boolean containsColorLabel() {
        return message.containsColorLabel();
    }

    @Override
    public void removeColorLabel() {
        message.removeColorLabel();
    }

    @Override
    public void setColorLabel(int colorLabel) {
        message.setColorLabel(colorLabel);
    }

    @Override
    public int getPriority() {
        return message.getPriority();
    }

    @Override
    public boolean containsPriority() {
        return message.containsPriority();
    }

    @Override
    public void removePriority() {
        message.removePriority();
    }

    @Override
    public void setPriority(int priority) {
        message.setPriority(priority);
    }

    @Override
    public InternetAddress getDispositionNotification() {
        return message.getDispositionNotification();
    }

    @Override
    public boolean containsDispositionNotification() {
        return message.containsDispositionNotification();
    }

    @Override
    public void removeDispositionNotification() {
        message.removeDispositionNotification();
    }

    @Override
    public void setDispositionNotification(InternetAddress dispositionNotification) {
        message.setDispositionNotification(dispositionNotification);
    }

    @Override
    public String getFolder() {
        return message.getFolder();
    }

    @Override
    public boolean containsFolder() {
        return message.containsFolder();
    }

    @Override
    public void removeFolder() {
        message.removeFolder();
    }

    @Override
    public void setFolder(String folder) {
        message.setFolder(folder);
    }

    @Override
    public int getAccountId() {
        return message.getAccountId();
    }

    @Override
    public boolean containsAccountId() {
        return message.containsAccountId();
    }

    @Override
    public void removeAccountId() {
        message.removeAccountId();
    }

    @Override
    public void setAccountId(int accountId) {
        message.setAccountId(accountId);
    }

    @Override
    public String getAccountName() {
        return message.getAccountName();
    }

    @Override
    public boolean containsAccountName() {
        return message.containsAccountName();
    }

    @Override
    public void removeAccountName() {
        message.removeAccountName();
    }

    @Override
    public void setAccountName(String accountName) {
        message.setAccountName(accountName);
    }

    @Override
    public boolean hasAttachment() {
        return message.hasAttachment();
    }

    @Override
    public boolean isHasAttachment() {
        return message.isHasAttachment();
    }

    @Override
    public boolean containsHasAttachment() {
        return message.containsHasAttachment();
    }

    @Override
    public void removeHasAttachment() {
        message.removeHasAttachment();
    }

    @Override
    public void setHasAttachment(boolean hasAttachment) {
        message.setHasAttachment(hasAttachment);
    }

    @Override
    public boolean isAlternativeHasAttachment() {
        return message.isAlternativeHasAttachment();
    }

    @Override
    public boolean containsAlternativeHasAttachment() {
        return message.containsAlternativeHasAttachment();
    }

    @Override
    public void removeAlternativeHasAttachment() {
        message.removeAlternativeHasAttachment();
    }

    @Override
    public void setAlternativeHasAttachment(boolean hasAttachment) {
        message.setAlternativeHasAttachment(hasAttachment);
    }

    @Override
    public Object clone() {
        return message.clone();
    }

    @Override
    public boolean isAppendVCard() {
        return message.isAppendVCard();
    }

    @Override
    public boolean containsAppendVCard() {
        return message.containsAppendVCard();
    }

    @Override
    public void removeAppendVCard() {
        message.removeAppendVCard();
    }

    @Override
    public void setAppendVCard(boolean appendVCard) {
        message.setAppendVCard(appendVCard);
    }

    @Override
    public int getRecentCount() {
        return message.getRecentCount();
    }

    @Override
    public boolean containsRecentCount() {
        return message.containsRecentCount();
    }

    @Override
    public void removeRecentCount() {
        message.removeRecentCount();
    }

    @Override
    public void setRecentCount(int recentCount) {
        message.setRecentCount(recentCount);
    }

    @Override
    public MailPath getMailPath() {
        return message.getMailPath();
    }

    @Override
    public String getMessageId() {
        return message.getMessageId();
    }

    @Override
    public String[] getReferences() {
        return message.getReferences();
    }

    @Override
    public String getMailId() {
        return message.getMailId();
    }

    @Override
    public void setMailId(String id) {
        message.setMailId(id);
    }

    @Override
    public int getUnreadMessages() {
        return message.getUnreadMessages();
    }

    @Override
    public void setUnreadMessages(int unreadMessages) {
        message.setUnreadMessages(unreadMessages);
    }

    @Override
    public void addFrom(Collection<InternetAddress> addrs) {
        message.addFrom(addrs);
    }

    @Override
    public void removeFromPersonals() {
        message.removeFromPersonals();
    }

    @Override
    public void addTo(Collection<InternetAddress> addrs) {
        message.addTo(addrs);
    }

    @Override
    public void removeToPersonals() {
        message.removeToPersonals();
    }

    @Override
    public void addCc(Collection<InternetAddress> addrs) {
        message.addCc(addrs);
    }

    @Override
    public void removeCcPersonals() {
        message.removeCcPersonals();
    }

    @Override
    public void addBcc(Collection<InternetAddress> addrs) {
        message.addBcc(addrs);
    }

    @Override
    public void removeBccPersonals() {
        message.removeBccPersonals();
    }

    @Override
    public InternetAddress[] getAllRecipients() {
        return message.getAllRecipients();
    }

    @Override
    public void addReplyTo(Collection<InternetAddress> addrs) {
        message.addReplyTo(addrs);
    }

    @Override
    public boolean isSubjectDecoded() {
        return message.isSubjectDecoded();
    }

    @Override
    public void addUserFlags(Collection<String> userFlags) {
        message.addUserFlags(userFlags);
    }

    @Override
    public FullnameArgument getOriginalFolder() {
        return message.getOriginalFolder();
    }

    @Override
    public boolean containsOriginalFolder() {
        return message.containsOriginalFolder();
    }

    @Override
    public void removeOriginalFolder() {
        message.removeOriginalFolder();
    }

    @Override
    public void setOriginalFolder(FullnameArgument originalFolder) {
        message.setOriginalFolder(originalFolder);
    }

    @Override
    public String getTextPreview() {
        return message.getTextPreview();
    }

    @Override
    public boolean containsTextPreview() {
        return message.containsTextPreview();
    }

    @Override
    public void removeTextPreview() {
        message.removeTextPreview();
    }

    @Override
    public void setTextPreview(String textPreview) {
        message.setTextPreview(textPreview);
    }

    @Override
    public String getOriginalId() {
        return message.getOriginalId();
    }

    @Override
    public boolean containsOriginalId() {
        return message.containsOriginalId();
    }

    @Override
    public void removeOriginalId() {
        message.removeOriginalId();
    }

    @Override
    public void setOriginalId(String originalId) {
        message.setOriginalId(originalId);
    }

    @Override
    public String[] getReferencesOrInReplyTo() {
        return message.getReferencesOrInReplyTo();
    }

    @Override
    public void setSecurityInfo(SecurityInfo securityInfo) {
        message.setSecurityInfo(securityInfo);
    }

    @Override
    public SecurityInfo getSecurityInfo() {
        return message.getSecurityInfo();
    }

    @Override
    public boolean containsSecurityInfo() {
        return message.containsSecurityInfo();
    }

    @Override
    public void removeSecurityInfo() {
        message.removeSecurityInfo();
    }

    @Override
    public void setSecurityResult(SecurityResult result) {
        message.setSecurityResult(result);
    }

    @Override
    public SecurityResult getSecurityResult() {
        return message.getSecurityResult();
    }

    @Override
    public boolean hasSecurityResult() {
        return message.hasSecurityResult();
    }

    @Override
    public boolean containsSecurityResult() {
        return message.containsSecurityResult();
    }

    @Override
    public void removeSecurityResult() {
        message.removeSecurityResult();
    }

    @Override
    public void setAuthenticityResult(MailAuthenticityResult authenticationResult) {
        message.setAuthenticityResult(authenticationResult);
    }

    @Override
    public MailAuthenticityResult getAuthenticityResult() {
        return message.getAuthenticityResult();
    }

    @Override
    public boolean hasAuthenticityResult() {
        return message.hasAuthenticityResult();
    }

    @Override
    public boolean containsAuthenticityResult() {
        return message.containsAuthenticityResult();
    }

    @Override
    public void removeAuthenticityResult() {
        message.removeAuthenticityResult();
    }

    /**
     * Initializes a new {@link DraftMailMessage}.
     */
    public DraftMailMessage(MailMessage message) {
        super();
        this.message = message;
    }

}
