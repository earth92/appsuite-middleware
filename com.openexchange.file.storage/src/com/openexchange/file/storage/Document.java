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

package com.openexchange.file.storage;

import java.io.InputStream;
import com.openexchange.exception.OXException;

/**
 * {@link Document} - An efficient document view on a file.
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class Document {

    private String name, mimeType, etag;
    private long size;
    private long lastModified;
    private File file;

    /**
     * Initializes a new {@link Document}.
     */
    protected Document() {
        super();
        size = -1L;
        lastModified = -1L;
    }

    /**
     * Initializes a new {@link Document} from given instance.
     *
     * @param other The instance to copy from
     */
    protected Document(Document other) {
        this();
        this.name = other.getName();
        this.mimeType = other.getMimeType();
        this.etag = other.getEtag();
        this.size = other.getSize();
        this.lastModified = other.getLastModified();
        this.file = other.getFile();
    }

    /**
     * Gets the file metadata.
     *
     * @return The metadata, or <code>null</code> if not set.
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the file metadata.
     *
     * @param file The metadata to set
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Gets the name
     *
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name
     *
     * @param name The name
     * @return This reference
     */
    public Document setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Gets the MIME type
     *
     * @return The MIME type
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets the MIME type
     *
     * @param mimeType The MIME type
     * @return This reference
     */
    public Document setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    /**
     * Gets the eTag
     *
     * @return The eTag
     */
    public String getEtag() {
        return etag;
    }

    /**
     * Sets the eTag
     *
     * @param etag The eTag
     * @return This reference
     */
    public Document setEtag(String etag) {
        this.etag = etag;
        return this;
    }

    /**
     * Gets the size in bytes
     *
     * @return The size in bytes or <code>-1</code> if unavailable
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the size in bytes
     *
     * @param size The size in bytes
     * @return This reference
     */
    public Document setSize(long size) {
        this.size = size;
        return this;
    }

    /**
     * Gets the last-modified time stamp
     *
     * @return The last-modified time stamp or <code>-1</code> if unavailable
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Sets the last-modified time stamp
     *
     * @param lastModified The last-modified time stamp to set
     * @return This reference
     */
    public Document setLastModified(long lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    /**
     * Gets whether the InputStream returned by {@link getData()} is repetitive or not.
     *
     * @return Whether {@link getData()} returns a repetitive InputStream or not
     */
    public boolean isRepetitive() {
        return true;
    }

    /**
     * Gets the data input stream
     *
     * @return The input stream
     * @throws OXException If input stream cannot be provided
     */
    public abstract InputStream getData() throws OXException;

}
