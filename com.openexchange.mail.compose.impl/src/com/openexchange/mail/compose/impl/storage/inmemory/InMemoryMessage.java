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

package com.openexchange.mail.compose.impl.storage.inmemory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.google.common.collect.ImmutableList;
import com.openexchange.exception.OXException;
import com.openexchange.java.BufferingQueue;
import com.openexchange.mail.compose.Address;
import com.openexchange.mail.compose.Attachment;
import com.openexchange.mail.compose.CompositionSpaceDescription;
import com.openexchange.mail.compose.Message;
import com.openexchange.mail.compose.MessageDescription;
import com.openexchange.mail.compose.Meta;
import com.openexchange.mail.compose.Security;
import com.openexchange.mail.compose.SharedAttachmentsInfo;
import com.openexchange.mail.compose.impl.storage.db.CompositionSpaceContainer;
import com.openexchange.mail.compose.impl.storage.db.CompositionSpaceDbStorage;
import com.openexchange.mail.compose.impl.storage.db.RdbCompositionSpaceStorageService;


/**
 * {@link InMemoryMessage}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.1
 */
public class InMemoryMessage implements Message {

    private final Lock lock;
    private final UUID compositionSpaceId;
    private final int userId;
    private final int contextId;
    private final BufferingQueue<InMemoryMessage> bufferingQueue;
    private final int hash;
    private MessageDescription messageDescription; // Guarded by lock

    private final Meta meta;
    private volatile Address from;
    private volatile Address sender;
    private volatile Address replyTo;
    private volatile List<Address> to;
    private volatile List<Address> cc;
    private volatile List<Address> bcc;
    private volatile String subject;
    private volatile String content;
    private volatile ContentType contentType;
    private volatile boolean requestReadReceipt;
    private volatile SharedAttachmentsInfo sharedAttachmentsInfo;
    private volatile List<Attachment> attachments;
    private volatile Security security;
    private volatile Priority priority;
    private volatile boolean contentEncrypted;
    private volatile Map<String, String> customHeaders;

    /**
     * Initializes a new {@link InMemoryMessage}.
     */
    public InMemoryMessage(UUID compositionSpaceId, MessageDescription initialMessageDesc, BufferingQueue<InMemoryMessage> bufferingQueue, int userId, int contextId) {
        super();
        lock = new ReentrantLock();
        this.bufferingQueue = bufferingQueue;
        this.compositionSpaceId = compositionSpaceId;
        this.userId = userId;
        this.contextId = contextId;

        hash = 32 * 1 + compositionSpaceId.hashCode();

        if (null == initialMessageDesc) {
            meta = Meta.META_NEW;
            priority = Priority.NORMAL;
            sharedAttachmentsInfo = SharedAttachmentsInfo.DISABLED;
            security = Security.DISABLED;
            contentEncrypted = false;
        } else {
            from = initialMessageDesc.getFrom();
            sender = initialMessageDesc.getSender();
            replyTo = initialMessageDesc.getReplyTo();
            to = initialMessageDesc.getTo();
            cc = initialMessageDesc.getCc();
            bcc = initialMessageDesc.getBcc();
            subject = initialMessageDesc.getSubject();
            content = initialMessageDesc.getContent();
            contentType = initialMessageDesc.getContentType();
            requestReadReceipt = initialMessageDesc.isRequestReadReceipt();
            sharedAttachmentsInfo = initialMessageDesc.getSharedAttachmentsInfo();
            attachments = initialMessageDesc.getAttachments();
            meta = initialMessageDesc.getMeta();
            security = initialMessageDesc.getSecurity();
            priority = initialMessageDesc.getPriority();
            contentEncrypted = initialMessageDesc.isContentEncrypted();
        }
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        InMemoryMessage other = (InMemoryMessage) obj;
        if (compositionSpaceId == null) {
            if (other.compositionSpaceId != null) {
                return false;
            }
        } else if (!compositionSpaceId.equals(other.compositionSpaceId)) {
            return false;
        }
        return true;
    }

    @Override
    public Address getFrom() {
        return from;
    }

    @Override
    public Address getSender() {
        return sender;
    }

    @Override
    public Address getReplyTo() {
        return replyTo;
    }

    @Override
    public List<Address> getTo() {
        return to;
    }

    @Override
    public List<Address> getCc() {
        return cc;
    }

    @Override
    public List<Address> getBcc() {
        return bcc;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public ContentType getContentType() {
        return contentType;
    }

    @Override
    public boolean isRequestReadReceipt() {
        return requestReadReceipt;
    }

    @Override
    public SharedAttachmentsInfo getSharedAttachments() {
        return sharedAttachmentsInfo;
    }

    @Override
    public List<Attachment> getAttachments() {
        return attachments;
    }

    @Override
    public Meta getMeta() {
        return meta;
    }

    @Override
    public Security getSecurity() {
        return security;
    }

    @Override
    public Priority getPriority() {
        return priority;
    }

    @Override
    public boolean isContentEncrypted() {
        return contentEncrypted;
    }

    @Override
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    /**
     * Sets the custom headers
     *
     * @param customHeaders The custom headers
     */
    public void setCustomHeaders(Map<String, String> customHeaders) {
        lock.lock();
        try {
            this.customHeaders = customHeaders;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsentElseReset(this);
            }
            md.setCustomHeaders(customHeaders);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the content-encrypted flag
     *
     * @param contentEncrypted The flag to set
     */
    public void setContentEncrypted(boolean contentEncrypted) {
        lock.lock();
        try {
            this.contentEncrypted = contentEncrypted;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsentElseReset(this);
            }
            md.setContentEncrypted(contentEncrypted);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the from address
     *
     * @param from The from address to set
     */
    public void setFrom(Address from) {
        lock.lock();
        try {
            this.from = from;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsentElseReset(this);
            }
            md.setFrom(from);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the reply-to address
     *
     * @param replyTo The reply-to address to set
     */
    public void setReplyTo(Address replyTo) {
        lock.lock();
        try {
            this.replyTo = replyTo;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsentElseReset(this);
            }
            md.setReplyTo(replyTo);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the sender address
     *
     * @param sender The sender address to set
     */
    public void setSender(Address sender) {
        lock.lock();
        try {
            this.sender = sender;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setSender(sender);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the to recipient addresses
     *
     * @param to The to recipient addresses to set
     */
    public void setTo(List<Address> to) {
        lock.lock();
        try {
            List<Address> toSet = immutableListFor(to);
            this.to = toSet;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setTo(toSet);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the cc recipient addresses
     *
     * @param cc The cc recipient addresses to set
     */
    public void setCc(List<Address> cc) {
        lock.lock();
        try {
            List<Address> toSet = immutableListFor(cc);
            this.cc = toSet;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setCc(toSet);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the bcc recipient addresses
     *
     * @param bcc The bcc recipient addresses to set
     */
    public void setBcc(List<Address> bcc) {
        lock.lock();
        try {
            List<Address> toSet = immutableListFor(bcc);
            this.bcc = toSet;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setBcc(toSet);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the subject
     *
     * @param subject The subject to set
     */
    public void setSubject(String subject) {
        lock.lock();
        try {
            this.subject = subject;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setSubject(subject);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the content
     *
     * @param content The content to set
     */
    public void setContent(String content) {
        lock.lock();
        try {
            this.content = content;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setContent(content);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the content type
     *
     * @param contentType The content type to set
     */
    public void setContentType(ContentType contentType) {
        lock.lock();
        try {
            this.contentType = contentType;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setContentType(contentType);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the "request read receipt" flag
     *
     * @param requestReadReceipt The "request read receipt" flag to set
     */
    public void setRequestReadReceipt(boolean requestReadReceipt) {
        lock.lock();
        try {
            this.requestReadReceipt = requestReadReceipt;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setRequestReadReceipt(requestReadReceipt);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the attachments
     *
     * @param attachments The attachments to set
     */
    public void setAttachments(List<Attachment> attachments) {
        lock.lock();
        try {
            List<Attachment> toSet = immutableListFor(attachments);
            this.attachments = toSet;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setAttachments(toSet);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the priority
     *
     * @param priority The priority to set
     */
    public void setPriority(Priority priority) {
        lock.lock();
        try {
            this.priority = priority;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setPriority(priority);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the security settings
     *
     * @param security The security settings to set
     */
    public void setSecurity(Security security) {
        lock.lock();
        try {
            this.security = security;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setSecurity(security);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the shared attachments information
     *
     * @param sharedAttachmentsInfo The shared attachments information to set
     */
    public void setSharedAttachmentsInfo(SharedAttachmentsInfo sharedAttachmentsInfo) {
        lock.lock();
        try {
            this.sharedAttachmentsInfo = sharedAttachmentsInfo;
            MessageDescription md = messageDescription;
            if (null == md) {
                md = new MessageDescription();
                messageDescription = md;
                bufferingQueue.offerIfAbsent(this);
            }
            md.setsharedAttachmentsInfo(sharedAttachmentsInfo);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Applies the changes provided by given <code>MessageDescription</code> instance.
     *
     * @param md The message description providing the changes to apply
     */
    public void applyFromMessageDescription(MessageDescription md) {
        if (null != md) {
            lock.lock();
            try {
                if (md.containsAttachments()) {
                    setAttachments(md.getAttachments());
                }
                if (md.containsBcc()) {
                    setBcc(md.getBcc());
                }
                if (md.containsCc()) {
                    setBcc(md.getCc());
                }
                if (md.containsTo()) {
                    setBcc(md.getTo());
                }
                if (md.containsContent()) {
                    setContent(content);
                }
                if (md.containsContentType()) {
                    setContentType(md.getContentType());
                }
                if (md.containsFrom()) {
                    setFrom(md.getFrom());
                }
                if (md.containsReplyTo()) {
                    setReplyTo(md.getReplyTo());
                }
                if (md.containsPriority()) {
                    setPriority(md.getPriority());
                }
                if (md.containsRequestReadReceipt()) {
                    setRequestReadReceipt(md.isRequestReadReceipt());
                }
                if (md.containsSecurity()) {
                    setSecurity(md.getSecurity());
                }
                if (md.containsSender()) {
                    setSender(md.getSender());
                }
                if (md.containsSharedAttachmentsInfo()) {
                    setSharedAttachmentsInfo(md.getSharedAttachmentsInfo());
                }
                if (md.containsSubject()) {
                    setSubject(md.getSubject());
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Flushes this in-memory message to persistent storage
     *
     * @param persistentStorage The persistent storage
     * @throws OXException If flushing to storage fails
     */
    public void flushToStorage(RdbCompositionSpaceStorageService persistentStorage) throws OXException {
        lock.lock();
        try {
            MessageDescription md = messageDescription;
            if (null == md) {
                // Already flushed
                return;
            }

            messageDescription = null;
            CompositionSpaceDbStorage dbStorage = persistentStorage.newDbStorageFor(userId, contextId);

            CompositionSpaceDescription compositionSpaceDesc = new CompositionSpaceDescription().setUuid(compositionSpaceId).setMessage(md);
            dbStorage.updateCompositionSpace(CompositionSpaceContainer.fromCompositionSpaceDescription(compositionSpaceDesc), true);
        } finally {
            lock.unlock();
        }
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    private static <E> List<E> immutableListFor(List<E> list) {
        return null == list || list.isEmpty() ? Collections.emptyList() : ImmutableList.copyOf(list);
    }

}
