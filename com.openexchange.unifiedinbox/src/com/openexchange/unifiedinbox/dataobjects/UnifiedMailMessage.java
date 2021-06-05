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

package com.openexchange.unifiedinbox.dataobjects;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.activation.DataHandler;
import javax.mail.internet.InternetAddress;
import com.openexchange.exception.OXException;
import com.openexchange.mail.MailPath;
import com.openexchange.mail.dataobjects.Delegatized;
import com.openexchange.mail.dataobjects.MailAuthenticityResult;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.dataobjects.SecurityInfo;
import com.openexchange.mail.dataobjects.SecurityResult;
import com.openexchange.mail.mime.ContentDisposition;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.HeaderCollection;

/**
 * {@link UnifiedMailMessage}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class UnifiedMailMessage extends MailMessage implements Delegatized {

    private static final long serialVersionUID = 9180380482758580171L;

    private final MailMessage delegatee;
    private String mailId;
    private String folder;
    private Integer accountId;
    private int undelegatedAccountId;
    private Integer unreadCount;

    /**
     * Initializes a new {@link UnifiedMailMessage}.
     */
    public UnifiedMailMessage(MailMessage delegatee, int undelegatedAccountId) {
        super();
        this.undelegatedAccountId = undelegatedAccountId;
        this.delegatee = delegatee;
    }

    @Override
    public void setUndelegatedAccountId(final int undelegatedAccountId) {
        if (delegatee instanceof Delegatized) {
            ((Delegatized) delegatee).setUndelegatedAccountId(undelegatedAccountId);
        }
        this.undelegatedAccountId = undelegatedAccountId;
    }

    @Override
    public int getUndelegatedAccountId() {
        return undelegatedAccountId;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(32);
        builder.append("UnifiedMailMessage [");
        {
            final String id = getMailId();
            if (id != null) {
                builder.append("id=").append(id).append(", ");
            }
        }
        {
            final String folder = getFolder();
            if (folder != null) {
                builder.append("folder=").append(folder);
            }
        }
        builder.append(']');
        return builder.toString();
    }

    @Override
    public String getMailId() {
        return mailId == null ? delegatee.getMailId() : mailId;
    }

    @Override
    public void setHeader(final String name, final String value) {
        delegatee.setHeader(name, value);
    }

    @Override
    public MailPath getMailPath() {
        return delegatee.getMailPath();
    }

    @Override
    public void addReplyTo(final InternetAddress addr) {
        delegatee.addReplyTo(addr);
    }

    @Override
    public void addReplyTo(final InternetAddress[] addrs) {
        delegatee.addReplyTo(addrs);
    }

    @Override
    public boolean containsReplyTo() {
        return delegatee.containsReplyTo();
    }

    @Override
    public void removeReplyTo() {
        delegatee.removeReplyTo();
    }

    @Override
    public InternetAddress[] getReplyTo() {
        return delegatee.getReplyTo();
    }

    @Override
    public boolean isUnseen() {
        return delegatee.isUnseen();
    }

    @Override
    public String getMessageId() {
        return delegatee.getMessageId();
    }

    @Override
    public boolean containsMessageId() {
        return delegatee.containsMessageId();
    }

    @Override
    public void removeMessageId() {
        delegatee.removeMessageId();
    }

    @Override
    public void setMessageId(final String messageId) {
        delegatee.setMessageId(messageId);
    }

    @Override
    public String getInReplyTo() {
        return delegatee.getInReplyTo();
    }

    @Override
    public String[] getReferences() {
        return delegatee.getReferences();
    }

    @Override
    public boolean containsReferences() {
        return delegatee.containsReferences();
    }

    @Override
    public void removeReferences() {
        delegatee.removeReferences();
    }

    @Override
    public void setReferences(final String sReferences) {
        delegatee.setReferences(sReferences);
    }

    @Override
    public void setReferences(final String[] references) {
        delegatee.setReferences(references);
    }

    @Override
    public void setMailId(final String id) {
        this.mailId = id;
    }

    @Override
    public int hashCode() {
        return delegatee.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return delegatee.equals(obj);
    }

    @Override
    public ContentType getContentType() {
        return delegatee.getContentType();
    }

    @Override
    public boolean containsContentType() {
        return delegatee.containsContentType();
    }

    @Override
    public void removeContentType() {
        delegatee.removeContentType();
    }

    @Override
    public void setContentType(final ContentType contentType) {
        delegatee.setContentType(contentType);
    }

    @Override
    public void setContentType(final String contentType) throws OXException {
        delegatee.setContentType(contentType);
    }

    @Override
    public ContentDisposition getContentDisposition() {
        return delegatee.getContentDisposition();
    }

    @Override
    public boolean containsContentDisposition() {
        return delegatee.containsContentDisposition();
    }

    @Override
    public void removeContentDisposition() {
        delegatee.removeContentDisposition();
    }

    @Override
    public void setContentDisposition(final String disposition) throws OXException {
        delegatee.setContentDisposition(disposition);
    }

    @Override
    public void setContentDisposition(final ContentDisposition disposition) {
        delegatee.setContentDisposition(disposition);
    }

    @Override
    public String getFileName() {
        return delegatee.getFileName();
    }

    @Override
    public boolean containsFileName() {
        return delegatee.containsFileName();
    }

    @Override
    public void removeFileName() {
        delegatee.removeFileName();
    }

    @Override
    public void setFileName(final String fileName) {
        delegatee.setFileName(fileName);
    }

    @Override
    public void addHeader(final String name, final String value) {
        delegatee.addHeader(name, value);
    }

    @Override
    public void addHeaders(final HeaderCollection headers) {
        delegatee.addHeaders(headers);
    }

    @Override
    public boolean containsHeaders() {
        return delegatee.containsHeaders();
    }

    @Override
    public void removeHeaders() {
        delegatee.removeHeaders();
    }

    @Override
    public int getHeadersSize() {
        return delegatee.getHeadersSize();
    }

    @Override
    public Iterator<Entry<String, String>> getHeadersIterator() {
        return delegatee.getHeadersIterator();
    }

    @Override
    public boolean containsHeader(final String name) {
        return delegatee.containsHeader(name);
    }

    @Override
    public String[] getHeader(final String name) {
        return delegatee.getHeader(name);
    }

    @Override
    public void addFrom(final InternetAddress addr) {
        delegatee.addFrom(addr);
    }

    @Override
    public String getFirstHeader(final String name) {
        return delegatee.getFirstHeader(name);
    }

    @Override
    public void addFrom(final InternetAddress[] addrs) {
        delegatee.addFrom(addrs);
    }

    @Override
    public String getHeader(final String name, final String delimiter) {
        return delegatee.getHeader(name, delimiter);
    }

    @Override
    public boolean containsFrom() {
        return delegatee.containsFrom();
    }

    @Override
    public void removeFrom() {
        delegatee.removeFrom();
    }

    @Override
    public String getHeader(final String name, final char delimiter) {
        return delegatee.getHeader(name, delimiter);
    }

    @Override
    public InternetAddress[] getFrom() {
        return delegatee.getFrom();
    }

    @Override
    public HeaderCollection getHeaders() {
        return delegatee.getHeaders();
    }

    @Override
    public void addTo(final InternetAddress addr) {
        delegatee.addTo(addr);
    }

    @Override
    public Iterator<Entry<String, String>> getNonMatchingHeaders(final String[] nonMatchingHeaders) {
        return delegatee.getNonMatchingHeaders(nonMatchingHeaders);
    }

    @Override
    public void addTo(final InternetAddress[] addrs) {
        delegatee.addTo(addrs);
    }

    @Override
    public boolean containsTo() {
        return delegatee.containsTo();
    }

    @Override
    public Iterator<Entry<String, String>> getMatchingHeaders(final String[] matchingHeaders) {
        return delegatee.getMatchingHeaders(matchingHeaders);
    }

    @Override
    public void removeTo() {
        delegatee.removeTo();
    }

    @Override
    public InternetAddress[] getTo() {
        return delegatee.getTo();
    }

    @Override
    public void removeHeader(final String name) {
        delegatee.removeHeader(name);
    }

    @Override
    public boolean hasHeaders(final String... names) {
        return delegatee.hasHeaders(names);
    }

    @Override
    public void addCc(final InternetAddress addr) {
        delegatee.addCc(addr);
    }

    @Override
    public long getSize() {
        return delegatee.getSize();
    }

    @Override
    public boolean containsSize() {
        return delegatee.containsSize();
    }

    @Override
    public void addCc(final InternetAddress[] addrs) {
        delegatee.addCc(addrs);
    }

    @Override
    public void removeSize() {
        delegatee.removeSize();
    }

    @Override
    public void setSize(final long size) {
        delegatee.setSize(size);
    }

    @Override
    public boolean containsCc() {
        return delegatee.containsCc();
    }

    @Override
    public String getContentId() {
        return delegatee.getContentId();
    }

    @Override
    public void removeCc() {
        delegatee.removeCc();
    }

    @Override
    public InternetAddress[] getCc() {
        return delegatee.getCc();
    }

    @Override
    public boolean containsContentId() {
        return delegatee.containsContentId();
    }

    @Override
    public void removeContentId() {
        delegatee.removeContentId();
    }

    @Override
    public void setContentId(final String contentId) {
        delegatee.setContentId(contentId);
    }

    @Override
    public void addBcc(final InternetAddress addr) {
        delegatee.addBcc(addr);
    }

    @Override
    public String getSequenceId() {
        return delegatee.getSequenceId();
    }

    @Override
    public boolean containsSequenceId() {
        return delegatee.containsSequenceId();
    }

    @Override
    public void removeSequenceId() {
        delegatee.removeSequenceId();
    }

    @Override
    public void addBcc(final InternetAddress[] addrs) {
        delegatee.addBcc(addrs);
    }

    @Override
    public void setSequenceId(final String sequenceId) {
        delegatee.setSequenceId(sequenceId);
    }

    @Override
    public MailPath getMsgref() {
        return delegatee.getMsgref();
    }

    @Override
    public boolean containsBcc() {
        return delegatee.containsBcc();
    }

    @Override
    public void removeBcc() {
        delegatee.removeBcc();
    }

    @Override
    public InternetAddress[] getBcc() {
        return delegatee.getBcc();
    }

    @Override
    public boolean containsMsgref() {
        return delegatee.containsMsgref();
    }

    @Override
    public void removeMsgref() {
        delegatee.removeMsgref();
    }

    @Override
    public int getFlags() {
        return delegatee.getFlags();
    }

    @Override
    public void setMsgref(final MailPath msgref) {
        delegatee.setMsgref(msgref);
    }

    @Override
    public boolean isAnswered() {
        return delegatee.isAnswered();
    }

    @Override
    public boolean isDeleted() {
        return delegatee.isDeleted();
    }

    @Override
    public boolean isDraft() {
        return delegatee.isDraft();
    }

    @Override
    public boolean isFlagged() {
        return delegatee.isFlagged();
    }

    @Override
    public boolean isRecent() {
        return delegatee.isRecent();
    }

    @Override
    public boolean hasEnclosedParts() throws OXException {
        return delegatee.hasEnclosedParts();
    }

    @Override
    public boolean isSeen() {
        return delegatee.isSeen();
    }

    @Override
    public Object getContent() throws OXException {
        return delegatee.getContent();
    }

    @Override
    public boolean isSpam() {
        return delegatee.isSpam();
    }

    @Override
    public boolean isForwarded() {
        return delegatee.isForwarded();
    }

    @Override
    public boolean isReadAcknowledgment() {
        return delegatee.isReadAcknowledgment();
    }

    @Override
    public DataHandler getDataHandler() throws OXException {
        return delegatee.getDataHandler();
    }

    @Override
    public boolean isUser() {
        return delegatee.isUser();
    }

    @Override
    public boolean containsFlags() {
        return delegatee.containsFlags();
    }

    @Override
    public InputStream getInputStream() throws OXException {
        return delegatee.getInputStream();
    }

    @Override
    public void removeFlags() {
        delegatee.removeFlags();
    }

    @Override
    public void setFlags(final int flags) {
        delegatee.setFlags(flags);
    }

    @Override
    public int getEnclosedCount() throws OXException {
        return delegatee.getEnclosedCount();
    }

    @Override
    public void setFlag(final int flag, final boolean enable) throws OXException {
        delegatee.setFlag(flag, enable);
    }

    @Override
    public MailPart getEnclosedMailPart(final int index) throws OXException {
        return delegatee.getEnclosedMailPart(index);
    }

    @Override
    public boolean isPrevSeen() {
        return delegatee.isPrevSeen();
    }

    @Override
    public void loadContent() throws OXException {
        delegatee.loadContent();
    }

    @Override
    public boolean containsPrevSeen() {
        return delegatee.containsPrevSeen();
    }

    @Override
    public void removePrevSeen() {
        delegatee.removePrevSeen();
    }

    @Override
    public void setPrevSeen(final boolean prevSeen) {
        delegatee.setPrevSeen(prevSeen);
    }

    @Override
    public void writeTo(final OutputStream out) throws OXException {
        delegatee.writeTo(out);
    }

    @Override
    public int getThreadLevel() {
        return delegatee.getThreadLevel();
    }

    @Override
    public boolean containsThreadLevel() {
        return delegatee.containsThreadLevel();
    }

    @Override
    public void removeThreadLevel() {
        delegatee.removeThreadLevel();
    }

    @Override
    public void setThreadLevel(final int threadLevel) {
        delegatee.setThreadLevel(threadLevel);
    }

    @Override
    public String getSource() throws OXException {
        return delegatee.getSource();
    }

    @Override
    public String getSubject() {
        return delegatee.getSubject();
    }

    @Override
    public boolean containsSubject() {
        return delegatee.containsSubject();
    }

    @Override
    public byte[] getSourceBytes() throws OXException {
        return delegatee.getSourceBytes();
    }

    @Override
    public void removeSubject() {
        delegatee.removeSubject();
    }

    @Override
    public void setSubject(final String subject) {
        delegatee.setSubject(subject);
    }

    @Override
    public void setSubject(String subject, boolean decoded) {
        delegatee.setSubject(subject, decoded);
    }

    @Override
    public void prepareForCaching() {
        delegatee.prepareForCaching();
    }

    @Override
    public Date getSentDate() {
        return delegatee.getSentDate();
    }

    @Override
    public boolean containsSentDate() {
        return delegatee.containsSentDate();
    }

    @Override
    public void removeSentDate() {
        delegatee.removeSentDate();
    }

    @Override
    public void setSentDate(final Date sentDate) {
        delegatee.setSentDate(sentDate);
    }

    @Override
    public Date getReceivedDate() {
        return delegatee.getReceivedDate();
    }

    @Override
    public Date getReceivedDateDirect() {
        return delegatee.getReceivedDateDirect();
    }

    @Override
    public boolean containsReceivedDate() {
        return delegatee.containsReceivedDate();
    }

    @Override
    public void removeReceivedDate() {
        delegatee.removeReceivedDate();
    }

    @Override
    public void setReceivedDate(final Date receivedDate) {
        delegatee.setReceivedDate(receivedDate);
    }

    @Override
    public void addUserFlag(final String userFlag) {
        delegatee.addUserFlag(userFlag);
    }

    @Override
    public void addUserFlags(final String[] userFlags) {
        delegatee.addUserFlags(userFlags);
    }

    @Override
    public boolean containsUserFlags() {
        return delegatee.containsUserFlags();
    }

    @Override
    public void removeUserFlags() {
        delegatee.removeUserFlags();
    }

    @Override
    public String[] getUserFlags() {
        return delegatee.getUserFlags();
    }

    @Override
    public int getColorLabel() {
        return delegatee.getColorLabel();
    }

    @Override
    public boolean containsColorLabel() {
        return delegatee.containsColorLabel();
    }

    @Override
    public void removeColorLabel() {
        delegatee.removeColorLabel();
    }

    @Override
    public void setColorLabel(final int colorLabel) {
        delegatee.setColorLabel(colorLabel);
    }

    @Override
    public int getPriority() {
        return delegatee.getPriority();
    }

    @Override
    public boolean containsPriority() {
        return delegatee.containsPriority();
    }

    @Override
    public void removePriority() {
        delegatee.removePriority();
    }

    @Override
    public void setPriority(final int priority) {
        delegatee.setPriority(priority);
    }

    @Override
    public InternetAddress getDispositionNotification() {
        return delegatee.getDispositionNotification();
    }

    @Override
    public boolean containsDispositionNotification() {
        return delegatee.containsDispositionNotification();
    }

    @Override
    public void removeDispositionNotification() {
        delegatee.removeDispositionNotification();
    }

    @Override
    public void setDispositionNotification(final InternetAddress dispositionNotification) {
        delegatee.setDispositionNotification(dispositionNotification);
    }

    @Override
    public String getFolder() {
        return folder == null ? delegatee.getFolder() : folder;
    }

    @Override
    public boolean containsFolder() {
        return delegatee.containsFolder();
    }

    @Override
    public void removeFolder() {
        delegatee.removeFolder();
    }

    @Override
    public void setFolder(final String folder) {
        this.folder = folder;
    }

    @Override
    public int getAccountId() {
        return null == accountId ? delegatee.getAccountId() : accountId.intValue();
    }

    @Override
    public boolean containsAccountId() {
        return null != accountId || delegatee.containsAccountId();
    }

    @Override
    public void removeAccountId() {
        delegatee.removeAccountId();
    }

    @Override
    public void setAccountId(final int accountId) {
        this.accountId = Integer.valueOf(accountId);
    }

    @Override
    public String getAccountName() {
        return delegatee.getAccountName();
    }

    @Override
    public boolean containsAccountName() {
        return delegatee.containsAccountName();
    }

    @Override
    public void removeAccountName() {
        delegatee.removeAccountName();
    }

    @Override
    public void setAccountName(final String accountName) {
        delegatee.setAccountName(accountName);
    }

    @Override
    public boolean hasAttachment() {
        return delegatee.hasAttachment();
    }

    @Override
    public boolean isHasAttachment() {
        return delegatee.isHasAttachment();
    }

    @Override
    public boolean containsHasAttachment() {
        return delegatee.containsHasAttachment();
    }

    @Override
    public void removeHasAttachment() {
        delegatee.removeHasAttachment();
    }

    @Override
    public void setHasAttachment(final boolean hasAttachment) {
        delegatee.setHasAttachment(hasAttachment);
    }

    @Override
    public boolean isAlternativeHasAttachment() {
        return delegatee.isAlternativeHasAttachment();
    }

    @Override
    public boolean containsAlternativeHasAttachment() {
        return delegatee.containsAlternativeHasAttachment();
    }

    @Override
    public void removeAlternativeHasAttachment() {
        delegatee.removeAlternativeHasAttachment();
    }

    @Override
    public void setAlternativeHasAttachment(boolean hasAttachment) {
        delegatee.setAlternativeHasAttachment(hasAttachment);
    }

    @Override
    public Object clone() {
        return delegatee.clone();
    }

    @Override
    public boolean isAppendVCard() {
        return delegatee.isAppendVCard();
    }

    @Override
    public boolean containsAppendVCard() {
        return delegatee.containsAppendVCard();
    }

    @Override
    public void removeAppendVCard() {
        delegatee.removeAppendVCard();
    }

    @Override
    public void setAppendVCard(final boolean appendVCard) {
        delegatee.setAppendVCard(appendVCard);
    }

    @Override
    public int getRecentCount() {
        return delegatee.getRecentCount();
    }

    @Override
    public boolean containsRecentCount() {
        return delegatee.containsRecentCount();
    }

    @Override
    public void removeRecentCount() {
        delegatee.removeRecentCount();
    }

    @Override
    public void setRecentCount(final int recentCount) {
        delegatee.setRecentCount(recentCount);
    }

    @Override
    public int getUnreadMessages() {
        final Integer unreadCount = this.unreadCount;
        return null == unreadCount ? -1 : unreadCount.intValue(); // delegatee.getUnreadMessages();
    }

    @Override
    public void setUnreadMessages(final int unreadMessages) {
        this.unreadCount = Integer.valueOf(unreadMessages);
        // delegatee.setUnreadMessages(unreadMessages);
    }

    @Override
    public void setSecurityInfo(SecurityInfo securityInfo) {
        delegatee.setSecurityInfo(securityInfo);
    }

    @Override
    public SecurityInfo getSecurityInfo () {
        return delegatee.getSecurityInfo();
    }

    @Override
    public boolean containsSecurityInfo () {
        return delegatee.containsSecurityInfo();
    }

    @Override
    public void removeSecurityInfo () {
        delegatee.removeSecurityInfo();
    }

    @Override
    public void setSecurityResult(SecurityResult result) {
        delegatee.setSecurityResult(result);
    }

    @Override
    public SecurityResult getSecurityResult() {
        return delegatee.getSecurityResult();
    }

    @Override
    public boolean hasSecurityResult() {
        return delegatee.hasSecurityResult();
    }

    @Override
    public boolean containsSecurityResult() {
        return delegatee.containsSecurityResult();
    }

    @Override
    public void removeSecurityResult() {
        delegatee.removeSecurityResult();
    }

    @Override
    public void setAuthenticityResult(MailAuthenticityResult authenticationResult) {
        delegatee.setAuthenticityResult(authenticationResult);
    }

    @Override
    public MailAuthenticityResult getAuthenticityResult() {
        return delegatee.getAuthenticityResult();
    }

    @Override
    public boolean hasAuthenticityResult() {
        return delegatee.hasAuthenticityResult();
    }

    @Override
    public boolean containsAuthenticityResult() {
        return delegatee.containsAuthenticityResult();
    }

    @Override
    public void removeAuthenticityResult() {
        delegatee.removeAuthenticityResult();
    }

    @Override
    public void addFrom(Collection<InternetAddress> addrs) {
        delegatee.addFrom(addrs);
    }

    @Override
    public void removeFromPersonals() {
        delegatee.removeFromPersonals();
    }

    @Override
    public void addTo(Collection<InternetAddress> addrs) {
        delegatee.addTo(addrs);
    }

    @Override
    public void removeToPersonals() {
        delegatee.removeToPersonals();
    }

    @Override
    public void addCc(Collection<InternetAddress> addrs) {
        delegatee.addCc(addrs);
    }

    @Override
    public void removeCcPersonals() {
        delegatee.removeCcPersonals();
    }

    @Override
    public void addBcc(Collection<InternetAddress> addrs) {
        delegatee.addBcc(addrs);
    }

    @Override
    public void removeBccPersonals() {
        delegatee.removeBccPersonals();
    }

    @Override
    public InternetAddress[] getAllRecipients() {
        return delegatee.getAllRecipients();
    }

    @Override
    public void addReplyTo(Collection<InternetAddress> addrs) {
        delegatee.addReplyTo(addrs);
    }

    @Override
    public boolean isSubjectDecoded() {
        return delegatee.isSubjectDecoded();
    }

    @Override
    public void addUserFlags(Collection<String> userFlags) {
        delegatee.addUserFlags(userFlags);
    }

    @Override
    public String getTextPreview() {
        return delegatee.getTextPreview();
    }

    @Override
    public boolean containsTextPreview() {
        return delegatee.containsTextPreview();
    }

    @Override
    public void removeTextPreview() {
        delegatee.removeTextPreview();
    }

    @Override
    public void setTextPreview(String textPreview) {
        delegatee.setTextPreview(textPreview);
    }

    @Override
    public String[] getReferencesOrInReplyTo() {
        return delegatee.getReferencesOrInReplyTo();
    }

}
