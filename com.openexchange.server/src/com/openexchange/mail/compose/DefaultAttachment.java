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

import java.io.Closeable;
import java.io.InputStream;
import java.util.UUID;
import com.openexchange.exception.OXException;
import com.openexchange.mail.MailExceptionCode;

/**
 * {@link DefaultAttachment}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.2
 */
public class DefaultAttachment implements RandomAccessAttachment {

    /**
     * Creates a new instance of <code>DefaultAttachment</code> carrying given attachment and composition space identifier.
     *
     * @param attachmentId The attachment identifier
     * @param compositionSpaceId The composition space identifier
     * @return The <code>DefaultAttachment</code> instance
     */
    public static DefaultAttachment createWithId(UUID attachmentId, UUID compositionSpaceId) {
        return new DefaultAttachment(null, attachmentId, compositionSpaceId, null, null, -1, null, null, null, null);
    }

    /**
     * Creates a new builder for an instance of <code>DefaultAttachment</code>
     *
     * @param id The attachment identifier
     * @return The new builder
     */
    public static Builder builder(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
        return new Builder(id);
    }

    /**
     * Creates a new builder for an instance of <code>DefaultAttachment</code> taking over attributes from given
     * <code>AttachmentDescription</code> instance.
     * <p>
     * Returned builder has no value set for {@link Builder#withDataProvider(DataProvider) data provider} and
     * {@link Builder#withStorageReference(AttachmentStorageReference) storage reference}.
     *
     * @param attachmentDesc The attachment description to taker over from
     * @return The new builder
     */
    public static Builder builder(AttachmentDescription attachmentDesc) {
        if (attachmentDesc == null) {
            throw new IllegalArgumentException("Attachment description must not be null");
        }
        return new Builder(attachmentDesc.getId()).applyFromAttachmentDescription(attachmentDesc);
    }

    /** The builder for an instance of <code>DefaultAttachment</code> */
    public static class Builder {

        private DataProvider dataProvider;
        private UUID id;
        private UUID compositionSpaceId;
        private AttachmentStorageReference storageReference;
        private String name;
        private long size;
        private String mimeType;
        private ContentId contentId;
        private ContentDisposition disposition;
        private AttachmentOrigin origin;

        /**
         * Initializes a new {@link DefaultAttachment.Builder}.
         */
        Builder(UUID id) {
            super();
            this.id = id;
            size = -1;
        }

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withDataProvider(DataProvider dataProvider) {
            this.dataProvider = dataProvider;
            return this;
        }

        public Builder withCompositionSpaceId(UUID compositionSpaceId) {
            this.compositionSpaceId = compositionSpaceId;
            return this;
        }

        public Builder withStorageReference(AttachmentStorageReference storageReference) {
            this.storageReference = storageReference;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withSize(long size) {
            this.size = size;
            return this;
        }

        public Builder withMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder withContentId(String contentId) {
            this.contentId = ContentId.valueOf(contentId);
            return this;
        }

        public Builder withContentId(ContentId contentId) {
            this.contentId = contentId;
            return this;
        }

        public Builder withDisposition(ContentDisposition disposition) {
            this.disposition = disposition;
            return this;
        }

        public Builder withContentDisposition(ContentDisposition disposition) {
            this.disposition = disposition;
            return this;
        }

        public Builder withOrigin(AttachmentOrigin origin) {
            this.origin = origin;
            return this;
        }

        Builder applyFromAttachmentDescription(AttachmentDescription attachmentDesc) {
            withCompositionSpaceId(attachmentDesc.getCompositionSpaceId());
            withContentDisposition(attachmentDesc.getContentDisposition());
            withContentId(attachmentDesc.getContentId());
            withMimeType(attachmentDesc.getMimeType());
            withName(attachmentDesc.getName());
            withOrigin(attachmentDesc.getOrigin());
            withSize(attachmentDesc.getSize());
            return this;
        }

        public DefaultAttachment build() {
            return new DefaultAttachment(dataProvider, id, compositionSpaceId, storageReference, name, size, mimeType, contentId, disposition, origin);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private final DataProvider dataProvider;
    private final UUID id;
    private final UUID compositionSpaceId;
    private final AttachmentStorageReference storageReference;
    private final String name;
    private final long size;
    private final String mimeType;
    private final ContentId contentId;
    private final ContentDisposition disposition;
    private final AttachmentOrigin origin;

    DefaultAttachment(DataProvider dataProvider, UUID id, UUID compositionSpaceId, AttachmentStorageReference storageReference, String name, long size, String mimeType, ContentId contentId, ContentDisposition disposition, AttachmentOrigin origin) {
        super();
        this.dataProvider = dataProvider;
        this.id = id;
        this.compositionSpaceId = compositionSpaceId;
        this.storageReference = storageReference;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType;
        this.contentId = contentId;
        this.disposition = disposition;
        this.origin = origin;
    }

    @Override
    public void close() {
        if (dataProvider instanceof Closeable) {
            try {
                ((Closeable) dataProvider).close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getCompositionSpaceId() {
        return compositionSpaceId;
    }

    @Override
    public AttachmentStorageReference getStorageReference() {
        return storageReference;
    }

    @Override
    public boolean supportsRandomAccess() {
        return dataProvider instanceof SeekingDataProvider;
    }

    @Override
    public InputStream getData() throws OXException {
        return dataProvider.getData();
    }

    @Override
    public InputStream getData(long offset, long length) throws OXException {
        try {
            SeekingDataProvider seekingDataProvider = (SeekingDataProvider) dataProvider;
            return seekingDataProvider.getData(offset, length);
        } catch (ClassCastException e) {
            throw MailExceptionCode.UNSUPPORTED_OPERATION.create(e, new Object[0]);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public String getContentId() {
        return contentId == null ? null : contentId.getContentId();
    }

    @Override
    public ContentId getContentIdAsObject() {
        return contentId;
    }

    @Override
    public ContentDisposition getContentDisposition() {
        return disposition;
    }

    @Override
    public AttachmentOrigin getOrigin() {
        return origin;
    }

    @Override
    public String toString() {
        StringBuilder builder2 = new StringBuilder();
        builder2.append("[");
        if (dataProvider != null) {
            builder2.append("dataProvider=").append(dataProvider).append(", ");
        }
        if (id != null) {
            builder2.append("id=").append(id).append(", ");
        }
        if (compositionSpaceId != null) {
            builder2.append("compositionSpaceId=").append(compositionSpaceId).append(", ");
        }
        if (storageReference != null) {
            builder2.append("storageReference=").append(storageReference).append(", ");
        }
        if (name != null) {
            builder2.append("name=").append(name).append(", ");
        }
        builder2.append("size=").append(size).append(", ");
        if (mimeType != null) {
            builder2.append("mimeType=").append(mimeType).append(", ");
        }
        if (contentId != null) {
            builder2.append("contentId=").append(contentId).append(", ");
        }
        if (disposition != null) {
            builder2.append("disposition=").append(disposition).append(", ");
        }
        if (origin != null) {
            builder2.append("origin=").append(origin);
        }
        builder2.append("]");
        return builder2.toString();
    }

}
