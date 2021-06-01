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

package com.openexchange.mail.structure.parser;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.activation.DataHandler;
import javax.mail.internet.InternetAddress;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.mail.FullnameArgument;
import com.openexchange.mail.MailPath;
import com.openexchange.mail.dataobjects.MailAuthenticityResult;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.dataobjects.SecurityInfo;
import com.openexchange.mail.dataobjects.SecurityResult;
import com.openexchange.mail.dataobjects.compose.ComposedMailMessage;
import com.openexchange.mail.dataobjects.compose.TextBodyMailPart;
import com.openexchange.mail.mime.ContentDisposition;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.HeaderCollection;
import com.openexchange.session.Session;

/**
 * {@link ComposedMailWrapper}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
final class ComposedMailWrapper extends ComposedMailMessage {

    private static final long serialVersionUID = -3283856474686683383L;

    private final MailMessage mail;

    /**
     * Initializes a new {@link ComposedMailWrapper}.
     *
     * @param session The session
     * @param ctx The context
     */
    public ComposedMailWrapper(MailMessage mail, Session session, Context ctx) {
        super(session, ctx);
        this.mail = mail;
    }

    @Override
    public void setHeader(String name, String value) {
        mail.setHeader(name, value);
    }

    @Override
    public String getMessageId() {
        return mail.getMessageId();
    }

    @Override
    public boolean containsMessageId() {
        return mail.containsMessageId();
    }

    @Override
    public void removeMessageId() {
        mail.removeMessageId();
    }

    @Override
    public void setMessageId(String messageId) {
        mail.setMessageId(messageId);
    }

    @Override
    public String getInReplyTo() {
        return mail.getInReplyTo();
    }

    @Override
    public String[] getReferences() {
        return mail.getReferences();
    }

    @Override
    public boolean containsReferences() {
        return mail.containsReferences();
    }

    @Override
    public void removeReferences() {
        mail.removeReferences();
    }

    @Override
    public void setReferences(String sReferences) {
        mail.setReferences(sReferences);
    }

    @Override
    public void setReferences(String[] references) {
        mail.setReferences(references);
    }

    @Override
    public int hashCode() {
        return mail.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return mail.equals(obj);
    }

    @Override
    public ContentType getContentType() {
        return mail.getContentType();
    }

    @Override
    public boolean containsContentType() {
        return mail.containsContentType();
    }

    @Override
    public void removeContentType() {
        mail.removeContentType();
    }

    @Override
    public void setContentType(ContentType contentType) {
        mail.setContentType(contentType);
    }

    @Override
    public void setContentType(String contentType) throws OXException {
        mail.setContentType(contentType);
    }

    @Override
    public ContentDisposition getContentDisposition() {
        return mail.getContentDisposition();
    }

    @Override
    public boolean containsContentDisposition() {
        return mail.containsContentDisposition();
    }

    @Override
    public void removeContentDisposition() {
        mail.removeContentDisposition();
    }

    @Override
    public void setContentDisposition(String disposition) throws OXException {
        mail.setContentDisposition(disposition);
    }

    @Override
    public String toString() {
        return mail.toString();
    }

    @Override
    public void setContentDisposition(ContentDisposition disposition) {
        mail.setContentDisposition(disposition);
    }

    @Override
    public String getFileName() {
        return mail.getFileName();
    }

    @Override
    public boolean containsFileName() {
        return mail.containsFileName();
    }

    @Override
    public void removeFileName() {
        mail.removeFileName();
    }

    @Override
    public void setFileName(String fileName) {
        mail.setFileName(fileName);
    }

    @Override
    public void addHeader(String name, String value) {
        mail.addHeader(name, value);
    }

    @Override
    public void addHeaders(HeaderCollection headers) {
        mail.addHeaders(headers);
    }

    @Override
    public boolean containsHeaders() {
        return mail.containsHeaders();
    }

    @Override
    public void removeHeaders() {
        mail.removeHeaders();
    }

    @Override
    public int getHeadersSize() {
        return mail.getHeadersSize();
    }

    @Override
    public Iterator<Entry<String, String>> getHeadersIterator() {
        return mail.getHeadersIterator();
    }

    @Override
    public boolean containsHeader(String name) {
        return mail.containsHeader(name);
    }

    @Override
    public String[] getHeader(String name) {
        return mail.getHeader(name);
    }

    @Override
    public void addFrom(InternetAddress addr) {
        mail.addFrom(addr);
    }

    @Override
    public String getFirstHeader(String name) {
        return mail.getFirstHeader(name);
    }

    @Override
    public void addFrom(InternetAddress[] addrs) {
        mail.addFrom(addrs);
    }

    @Override
    public String getHeader(String name, String delimiter) {
        return mail.getHeader(name, delimiter);
    }

    @Override
    public boolean containsFrom() {
        return mail.containsFrom();
    }

    @Override
    public void removeFrom() {
        mail.removeFrom();
    }

    @Override
    public String getHeader(String name, char delimiter) {
        return mail.getHeader(name, delimiter);
    }

    @Override
    public InternetAddress[] getFrom() {
        return mail.getFrom();
    }

    @Override
    public HeaderCollection getHeaders() {
        return mail.getHeaders();
    }

    @Override
    public void addTo(InternetAddress addr) {
        mail.addTo(addr);
    }

    @Override
    public Iterator<Entry<String, String>> getNonMatchingHeaders(String[] nonMatchingHeaders) {
        return mail.getNonMatchingHeaders(nonMatchingHeaders);
    }

    @Override
    public void addTo(InternetAddress[] addrs) {
        mail.addTo(addrs);
    }

    @Override
    public boolean containsTo() {
        return mail.containsTo();
    }

    @Override
    public Iterator<Entry<String, String>> getMatchingHeaders(String[] matchingHeaders) {
        return mail.getMatchingHeaders(matchingHeaders);
    }

    @Override
    public void removeTo() {
        mail.removeTo();
    }

    @Override
    public InternetAddress[] getTo() {
        return mail.getTo();
    }

    @Override
    public void removeHeader(String name) {
        mail.removeHeader(name);
    }

    @Override
    public boolean hasHeaders(String... names) {
        return mail.hasHeaders(names);
    }

    @Override
    public void addCc(InternetAddress addr) {
        mail.addCc(addr);
    }

    @Override
    public long getSize() {
        return mail.getSize();
    }

    @Override
    public boolean containsSize() {
        return mail.containsSize();
    }

    @Override
    public void addCc(InternetAddress[] addrs) {
        mail.addCc(addrs);
    }

    @Override
    public void removeSize() {
        mail.removeSize();
    }

    @Override
    public void setSize(long size) {
        mail.setSize(size);
    }

    @Override
    public void addReplyTo(InternetAddress addr) {
        mail.addReplyTo(addr);
    }

    @Override
    public void addReplyTo(InternetAddress[] addrs) {
        mail.addReplyTo(addrs);
    }

    @Override
    public boolean containsReplyTo() {
        return mail.containsReplyTo();
    }

    @Override
    public void removeReplyTo() {
        mail.removeReplyTo();
    }

    @Override
    public InternetAddress[] getReplyTo() {
        return mail.getReplyTo();
    }

    @Override
    public boolean isUnseen() {
        return mail.isUnseen();
    }

    @Override
    public boolean containsCc() {
        return mail.containsCc();
    }

    @Override
    public String getContentId() {
        return mail.getContentId();
    }

    @Override
    public void removeCc() {
        mail.removeCc();
    }

    @Override
    public InternetAddress[] getCc() {
        return mail.getCc();
    }

    @Override
    public boolean containsContentId() {
        return mail.containsContentId();
    }

    @Override
    public void removeContentId() {
        mail.removeContentId();
    }

    @Override
    public void setContentId(String contentId) {
        mail.setContentId(contentId);
    }

    @Override
    public void addBcc(InternetAddress addr) {
        mail.addBcc(addr);
    }

    @Override
    public String getSequenceId() {
        return mail.getSequenceId();
    }

    @Override
    public boolean containsSequenceId() {
        return mail.containsSequenceId();
    }

    @Override
    public void removeSequenceId() {
        mail.removeSequenceId();
    }

    @Override
    public void addBcc(InternetAddress[] addrs) {
        mail.addBcc(addrs);
    }

    @Override
    public void setSequenceId(String sequenceId) {
        mail.setSequenceId(sequenceId);
    }

    @Override
    public MailPath getMsgref() {
        return mail.getMsgref();
    }

    @Override
    public boolean containsBcc() {
        return mail.containsBcc();
    }

    @Override
    public void removeBcc() {
        mail.removeBcc();
    }

    @Override
    public InternetAddress[] getBcc() {
        return mail.getBcc();
    }

    @Override
    public boolean containsMsgref() {
        return mail.containsMsgref();
    }

    @Override
    public void removeMsgref() {
        mail.removeMsgref();
    }

    @Override
    public int getFlags() {
        return mail.getFlags();
    }

    @Override
    public void setMsgref(MailPath msgref) {
        mail.setMsgref(msgref);
    }

    @Override
    public boolean isAnswered() {
        return mail.isAnswered();
    }

    @Override
    public boolean isDeleted() {
        return mail.isDeleted();
    }

    @Override
    public boolean isDraft() {
        return mail.isDraft();
    }

    @Override
    public boolean isFlagged() {
        return mail.isFlagged();
    }

    @Override
    public boolean isRecent() {
        return mail.isRecent();
    }

    @Override
    public boolean hasEnclosedParts() throws OXException {
        return mail.hasEnclosedParts();
    }

    @Override
    public boolean isSeen() {
        return mail.isSeen();
    }

    @Override
    public Object getContent() throws OXException {
        return mail.getContent();
    }

    @Override
    public boolean isSpam() {
        return mail.isSpam();
    }

    @Override
    public boolean isForwarded() {
        return mail.isForwarded();
    }

    @Override
    public boolean isReadAcknowledgment() {
        return mail.isReadAcknowledgment();
    }

    @Override
    public DataHandler getDataHandler() throws OXException {
        return mail.getDataHandler();
    }

    @Override
    public boolean isUser() {
        return mail.isUser();
    }

    @Override
    public boolean containsFlags() {
        return mail.containsFlags();
    }

    @Override
    public InputStream getInputStream() throws OXException {
        return mail.getInputStream();
    }

    @Override
    public void removeFlags() {
        mail.removeFlags();
    }

    @Override
    public void setFlags(int flags) {
        mail.setFlags(flags);
    }

    @Override
    public int getEnclosedCount() throws OXException {
        return mail.getEnclosedCount();
    }

    @Override
    public void setFlag(int flag, boolean enable) throws OXException {
        mail.setFlag(flag, enable);
    }

    @Override
    public MailPart getEnclosedMailPart(int index) throws OXException {
        return mail.getEnclosedMailPart(index);
    }

    @Override
    public boolean isPrevSeen() {
        return mail.isPrevSeen();
    }

    @Override
    public void loadContent() throws OXException {
        mail.loadContent();
    }

    @Override
    public boolean containsPrevSeen() {
        return mail.containsPrevSeen();
    }

    @Override
    public void removePrevSeen() {
        mail.removePrevSeen();
    }

    @Override
    public void setPrevSeen(boolean prevSeen) {
        mail.setPrevSeen(prevSeen);
    }

    @Override
    public void writeTo(OutputStream out) throws OXException {
        mail.writeTo(out);
    }

    @Override
    public int getThreadLevel() {
        return mail.getThreadLevel();
    }

    @Override
    public boolean containsThreadLevel() {
        return mail.containsThreadLevel();
    }

    @Override
    public void removeThreadLevel() {
        mail.removeThreadLevel();
    }

    @Override
    public void setThreadLevel(int threadLevel) {
        mail.setThreadLevel(threadLevel);
    }

    @Override
    public String getSource() throws OXException {
        return mail.getSource();
    }

    @Override
    public String getSubject() {
        return mail.getSubject();
    }

    @Override
    public boolean containsSubject() {
        return mail.containsSubject();
    }

    @Override
    public byte[] getSourceBytes() throws OXException {
        return mail.getSourceBytes();
    }

    @Override
    public void removeSubject() {
        mail.removeSubject();
    }

    @Override
    public void setSubject(String subject) {
        mail.setSubject(subject);
    }

    @Override
    public void setSubject(String subject, boolean decoded) {
        mail.setSubject(subject, decoded);
    }

    @Override
    public void prepareForCaching() {
        mail.prepareForCaching();
    }

    @Override
    public Date getSentDate() {
        return mail.getSentDate();
    }

    @Override
    public boolean containsSentDate() {
        return mail.containsSentDate();
    }

    @Override
    public void removeSentDate() {
        mail.removeSentDate();
    }

    @Override
    public void setSentDate(Date sentDate) {
        mail.setSentDate(sentDate);
    }

    @Override
    public Date getReceivedDate() {
        return mail.getReceivedDate();
    }

    @Override
    public Date getReceivedDateDirect() {
        return mail.getReceivedDateDirect();
    }

    @Override
    public boolean containsReceivedDate() {
        return mail.containsReceivedDate();
    }

    @Override
    public void removeReceivedDate() {
        mail.removeReceivedDate();
    }

    @Override
    public void setReceivedDate(Date receivedDate) {
        mail.setReceivedDate(receivedDate);
    }

    @Override
    public void addUserFlag(String userFlag) {
        mail.addUserFlag(userFlag);
    }

    @Override
    public void addUserFlags(String[] userFlags) {
        mail.addUserFlags(userFlags);
    }

    @Override
    public boolean containsUserFlags() {
        return mail.containsUserFlags();
    }

    @Override
    public void removeUserFlags() {
        mail.removeUserFlags();
    }

    @Override
    public String[] getUserFlags() {
        return mail.getUserFlags();
    }

    @Override
    public int getColorLabel() {
        return mail.getColorLabel();
    }

    @Override
    public boolean containsColorLabel() {
        return mail.containsColorLabel();
    }

    @Override
    public void removeColorLabel() {
        mail.removeColorLabel();
    }

    @Override
    public void setColorLabel(int colorLabel) {
        mail.setColorLabel(colorLabel);
    }

    @Override
    public int getPriority() {
        return mail.getPriority();
    }

    @Override
    public boolean containsPriority() {
        return mail.containsPriority();
    }

    @Override
    public void removePriority() {
        mail.removePriority();
    }

    @Override
    public void setPriority(int priority) {
        mail.setPriority(priority);
    }

    @Override
    public InternetAddress getDispositionNotification() {
        return mail.getDispositionNotification();
    }

    @Override
    public boolean containsDispositionNotification() {
        return mail.containsDispositionNotification();
    }

    @Override
    public void removeDispositionNotification() {
        mail.removeDispositionNotification();
    }

    @Override
    public void setDispositionNotification(InternetAddress dispositionNotification) {
        mail.setDispositionNotification(dispositionNotification);
    }

    @Override
    public String getFolder() {
        return mail.getFolder();
    }

    @Override
    public boolean containsFolder() {
        return mail.containsFolder();
    }

    @Override
    public void removeFolder() {
        mail.removeFolder();
    }

    @Override
    public void setFolder(String folder) {
        mail.setFolder(folder);
    }

    @Override
    public int getAccountId() {
        return mail.getAccountId();
    }

    @Override
    public boolean containsAccountId() {
        return mail.containsAccountId();
    }

    @Override
    public void removeAccountId() {
        mail.removeAccountId();
    }

    @Override
    public void setAccountId(int accountId) {
        mail.setAccountId(accountId);
    }

    @Override
    public String getAccountName() {
        return mail.getAccountName();
    }

    @Override
    public boolean containsAccountName() {
        return mail.containsAccountName();
    }

    @Override
    public void removeAccountName() {
        mail.removeAccountName();
    }

    @Override
    public void setAccountName(String accountName) {
        mail.setAccountName(accountName);
    }

    @Override
    public boolean hasAttachment() {
        return mail.hasAttachment();
    }

    @Override
    public boolean isHasAttachment() {
        return mail.isHasAttachment();
    }

    @Override
    public boolean containsHasAttachment() {
        return mail.containsHasAttachment();
    }

    @Override
    public void removeHasAttachment() {
        mail.removeHasAttachment();
    }

    @Override
    public void setHasAttachment(boolean hasAttachment) {
        mail.setHasAttachment(hasAttachment);
    }

    @Override
    public boolean isAlternativeHasAttachment() {
        return mail.isAlternativeHasAttachment();
    }

    @Override
    public boolean containsAlternativeHasAttachment() {
        return mail.containsAlternativeHasAttachment();
    }

    @Override
    public void removeAlternativeHasAttachment() {
        mail.removeAlternativeHasAttachment();
    }

    @Override
    public void setAlternativeHasAttachment(boolean hasAttachment) {
        mail.setAlternativeHasAttachment(hasAttachment);
    }

    @Override
    public Object clone() {
        return mail.clone();
    }

    @Override
    public boolean isAppendVCard() {
        return mail.isAppendVCard();
    }

    @Override
    public boolean containsAppendVCard() {
        return mail.containsAppendVCard();
    }

    @Override
    public void removeAppendVCard() {
        mail.removeAppendVCard();
    }

    @Override
    public void setAppendVCard(boolean appendVCard) {
        mail.setAppendVCard(appendVCard);
    }

    @Override
    public int getRecentCount() {
        return mail.getRecentCount();
    }

    @Override
    public boolean containsRecentCount() {
        return mail.containsRecentCount();
    }

    @Override
    public void removeRecentCount() {
        mail.removeRecentCount();
    }

    @Override
    public void setRecentCount(int recentCount) {
        mail.setRecentCount(recentCount);
    }

    @Override
    public MailPath getMailPath() {
        return mail.getMailPath();
    }

    @Override
    public String getMailId() {
        return mail.getMailId();
    }

    @Override
    public void setMailId(String id) {
        mail.setMailId(id);
    }

    @Override
    public int getUnreadMessages() {
        return mail.getUnreadMessages();
    }

    @Override
    public void setUnreadMessages(int unreadMessages) {
        mail.setUnreadMessages(unreadMessages);
    }

    @Override
    public void addFrom(Collection<InternetAddress> addrs) {
        mail.addFrom(addrs);
    }

    @Override
    public void removeFromPersonals() {
        mail.removeFromPersonals();
    }

    @Override
    public void addTo(Collection<InternetAddress> addrs) {
        mail.addTo(addrs);
    }

    @Override
    public void removeToPersonals() {
        mail.removeToPersonals();
    }

    @Override
    public void addCc(Collection<InternetAddress> addrs) {
        mail.addCc(addrs);
    }

    @Override
    public void removeCcPersonals() {
        mail.removeCcPersonals();
    }

    @Override
    public void addBcc(Collection<InternetAddress> addrs) {
        mail.addBcc(addrs);
    }

    @Override
    public void removeBccPersonals() {
        mail.removeBccPersonals();
    }

    @Override
    public InternetAddress[] getAllRecipients() {
        return mail.getAllRecipients();
    }

    @Override
    public void addReplyTo(Collection<InternetAddress> addrs) {
        mail.addReplyTo(addrs);
    }

    @Override
    public boolean isSubjectDecoded() {
        return mail.isSubjectDecoded();
    }

    @Override
    public void addUserFlags(Collection<String> userFlags) {
        mail.addUserFlags(userFlags);
    }

    @Override
    public FullnameArgument getOriginalFolder() {
        return mail.getOriginalFolder();
    }

    @Override
    public boolean containsOriginalFolder() {
        return mail.containsOriginalFolder();
    }

    @Override
    public void removeOriginalFolder() {
        mail.removeOriginalFolder();
    }

    @Override
    public void setOriginalFolder(FullnameArgument originalFolder) {
        mail.setOriginalFolder(originalFolder);
    }

    @Override
    public String getTextPreview() {
        return mail.getTextPreview();
    }

    @Override
    public boolean containsTextPreview() {
        return mail.containsTextPreview();
    }

    @Override
    public void removeTextPreview() {
        mail.removeTextPreview();
    }

    @Override
    public void setTextPreview(String textPreview) {
        mail.setTextPreview(textPreview);
    }

    @Override
    public String getOriginalId() {
        return mail.getOriginalId();
    }

    @Override
    public boolean containsOriginalId() {
        return mail.containsOriginalId();
    }

    @Override
    public void removeOriginalId() {
        mail.removeOriginalId();
    }

    @Override
    public void setOriginalId(String originalId) {
        mail.setOriginalId(originalId);
    }

    @Override
    public String[] getReferencesOrInReplyTo() {
        return mail.getReferencesOrInReplyTo();
    }

    @Override
    public void setSecurityInfo(SecurityInfo securityInfo) {
        mail.setSecurityInfo(securityInfo);
    }

    @Override
    public SecurityInfo getSecurityInfo() {
        return mail.getSecurityInfo();
    }

    @Override
    public boolean containsSecurityInfo() {
        return mail.containsSecurityInfo();
    }

    @Override
    public void removeSecurityInfo() {
        mail.removeSecurityInfo();
    }

    @Override
    public void setSecurityResult(SecurityResult result) {
        mail.setSecurityResult(result);
    }

    @Override
    public SecurityResult getSecurityResult() {
        return mail.getSecurityResult();
    }

    @Override
    public boolean hasSecurityResult() {
        return mail.hasSecurityResult();
    }

    @Override
    public boolean containsSecurityResult() {
        return mail.containsSecurityResult();
    }

    @Override
    public void removeSecurityResult() {
        mail.removeSecurityResult();
    }

    @Override
    public void setAuthenticityResult(MailAuthenticityResult authenticationResult) {
        mail.setAuthenticityResult(authenticationResult);
    }

    @Override
    public MailAuthenticityResult getAuthenticityResult() {
        return mail.getAuthenticityResult();
    }

    @Override
    public boolean hasAuthenticityResult() {
        return mail.hasAuthenticityResult();
    }

    @Override
    public boolean containsAuthenticityResult() {
        return mail.containsAuthenticityResult();
    }

    @Override
    public void removeAuthenticityResult() {
        mail.removeAuthenticityResult();
    }

    @Override
    public void setBodyPart(TextBodyMailPart mailPart) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TextBodyMailPart getBodyPart() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MailPart removeEnclosedPart(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEnclosedPart(MailPart part) {
        throw new UnsupportedOperationException();
    }

}
