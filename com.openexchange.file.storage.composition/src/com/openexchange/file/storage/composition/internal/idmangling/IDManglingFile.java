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

package com.openexchange.file.storage.composition.internal.idmangling;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.file.storage.DelegatingFile;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.FileStorageObjectPermission;
import com.openexchange.file.storage.FolderPath;
import com.openexchange.file.storage.MediaStatus;
import com.openexchange.file.storage.composition.FileID;
import com.openexchange.file.storage.composition.FolderID;
import com.openexchange.file.storage.composition.UniqueFileID;
import com.openexchange.groupware.EntityInfo;
import com.openexchange.java.GeoLocation;

/**
 * {@link IDManglingFile}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class IDManglingFile implements DelegatingFile {

    private final File file;
    private final String id;
    private final String folder;
    private final String uniqueFileId;

    /**
     * Initializes a new {@link IDManglingFile} instance delegating all regular calls to the supplied file, but returning the unique ID
     * representations of the file's own object-, unique- and the parent folder ID properties based on the underlying service- and account
     * IDs.
     *
     * @param file The file delegate
     * @param service The service identifier
     * @param account The account identifier
     */
    IDManglingFile(final File file, final String service, final String account) {
        id = new FileID(service, account, file.getFolderId(), file.getId()).toUniqueID();
        folder = new FolderID(service, account, file.getFolderId()).toUniqueID();
        uniqueFileId = null != file.getUniqueId() ? new UniqueFileID(service, account, file.getUniqueId()).toUniqueID() : null;
        this.file = file;
    }

    @Override
    public File getDelegate() {
        return file;
    }

    @Override
    public boolean isAccurateSize() {
        return file.isAccurateSize();
    }

    @Override
    public void setAccurateSize(boolean accurateSize) {
        file.setAccurateSize(accurateSize);
    }

    @Override
    public boolean matches(final String pattern, final Field... fields) {
        return file.matches(pattern, fields);
    }

    @Override
    public void copyFrom(final File other) {
        file.copyFrom(other);
    }

    @Override
    public void copyInto(final File other) {
        file.copyInto(other);
    }

    @Override
    public void copyFrom(final File other, final Field... fields) {
        file.copyFrom(other, fields);
    }

    @Override
    public void copyInto(final File other, final Field... fields) {
        file.copyInto(other, fields);
    }

    @Override
    public Set<Field> differences(final File other) {
        return file.differences(other);
    }

    @Override
    public File dup() {
        return file.dup();
    }

    @Override
    public boolean equals(final File other, final Field criterium, final Field... criteria) {
        return file.equals(other, criterium, criteria);
    }

    @Override
    public String getCategories() {
        return file.getCategories();
    }

    @Override
    public int getColorLabel() {
        return file.getColorLabel();
    }

    @Override
    public String getContent() {
        return file.getContent();
    }

    @Override
    public Date getCreated() {
        return file.getCreated();
    }

    @Override
    public int getCreatedBy() {
        return file.getCreatedBy();
    }

    @Override
    public String getDescription() {
        return file.getDescription();
    }

    @Override
    public String getFileMD5Sum() {
        return file.getFileMD5Sum();
    }

    @Override
    public String getFileMIMEType() {
        return file.getFileMIMEType();
    }

    @Override
    public String getFileName() {
        return file.getFileName();
    }

    @Override
    public long getFileSize() {
        return file.getFileSize();
    }

    @Override
    public String getFolderId() {
        return folder;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Date getLastModified() {
        return file.getLastModified();
    }

    @Override
    public Date getLockedUntil() {
        return file.getLockedUntil();
    }

    @Override
    public int getModifiedBy() {
        return file.getModifiedBy();
    }

    @Override
    public int getNumberOfVersions() {
        return file.getNumberOfVersions();
    }

    @Override
    public String getProperty(final String key) {
        return file.getProperty(key);
    }

    @Override
    public Set<String> getPropertyNames() {
        return file.getPropertyNames();
    }

    @Override
    public long getSequenceNumber() {
        return file.getSequenceNumber();
    }

    @Override
    public String getTitle() {
        return file.getTitle();
    }

    @Override
    public String getURL() {
        return file.getURL();
    }

    @Override
    public String getVersion() {
        return file.getVersion();
    }

    @Override
    public String getVersionComment() {
        return file.getVersionComment();
    }

    @Override
    public boolean isCurrentVersion() {
        return file.isCurrentVersion();
    }

    @Override
    public Map<String, Object> getMeta() {
        return file.getMeta();
    }

    @Override
    public List<FileStorageObjectPermission> getObjectPermissions() {
        return file.getObjectPermissions();
    }

    @Override
    public void setCategories(final String categories) {
        file.setCategories(categories);
    }

    @Override
    public void setColorLabel(final int color) {
        file.setColorLabel(color);
    }

    @Override
    public void setCreated(final Date creationDate) {
        file.setCreated(creationDate);
    }

    @Override
    public void setCreatedBy(final int cretor) {
        file.setCreatedBy(cretor);
    }

    @Override
    public void setDescription(final String description) {
        file.setDescription(description);
    }

    @Override
    public void setFileMD5Sum(final String sum) {
        file.setFileMD5Sum(sum);
    }

    @Override
    public void setFileMIMEType(final String type) {
        file.setFileMIMEType(type);
    }

    @Override
    public void setFileName(final String fileName) {
        file.setFileName(fileName);
    }

    @Override
    public void setFileSize(final long length) {
        file.setFileSize(length);
    }

    @Override
    public void setFolderId(final String folderId) {
        throw new IllegalStateException("IDs are only read only with this class");
    }

    @Override
    public void setId(final String id) {
        throw new IllegalStateException("IDs are only read only with this class");
    }

    @Override
    public void setIsCurrentVersion(final boolean bool) {
        file.setIsCurrentVersion(bool);
    }

    @Override
    public void setLastModified(final Date now) {
        file.setLastModified(now);
    }

    @Override
    public void setLockedUntil(final Date lockedUntil) {
        file.setLockedUntil(lockedUntil);
    }

    @Override
    public void setModifiedBy(final int lastEditor) {
        file.setModifiedBy(lastEditor);
    }

    @Override
    public void setNumberOfVersions(final int numberOfVersions) {
        file.setNumberOfVersions(numberOfVersions);
    }

    @Override
    public void setTitle(final String title) {
        file.setTitle(title);
    }

    @Override
    public void setURL(final String url) {
        file.setURL(url);
    }

    @Override
    public void setVersion(final String version) {
        file.setVersion(version);
    }

    @Override
    public void setVersionComment(final String string) {
        file.setVersionComment(string);
    }

    @Override
    public void setMeta(Map<String, Object> properties) {
        file.setMeta(properties);
    }

    @Override
    public void setObjectPermissions(List<FileStorageObjectPermission> objectPermissions) {
        file.setObjectPermissions(objectPermissions);
    }

    @Override
    public boolean isShareable() {
        return file.isShareable();
    }

    @Override
    public void setShareable(boolean shareable) {
        file.setShareable(shareable);
    }

    @Override
    public FolderPath getOrigin() {
        return file.getOrigin();
    }

    @Override
    public void setOrigin(FolderPath origin) {
        file.setOrigin(origin);
    }

    @Override
    public Date getCaptureDate() {
        return file.getCaptureDate();
    }

    @Override
    public void setCaptureDate(Date captureDate) {
        file.setCaptureDate(captureDate);
    }

    @Override
    public Long getCameraIsoSpeed() {
        return file.getCameraIsoSpeed();
    }

    @Override
    public void setCameraIsoSpeed(long isoSpeed) {
        file.setCameraIsoSpeed(isoSpeed);
    }

    @Override
    public Double getCameraAperture() {
        return file.getCameraAperture();
    }

    @Override
    public void setCameraAperture(double aperture) {
        file.setCameraAperture(aperture);
    }

    @Override
    public Double getCameraExposureTime() {
        return file.getCameraExposureTime();
    }

    @Override
    public void setCameraExposureTime(double exposureTime) {
        file.setCameraExposureTime(exposureTime);
    }

    @Override
    public Double getCameraFocalLength() {
        return file.getCameraFocalLength();
    }

    @Override
    public void setCameraFocalLength(double focalLength) {
        file.setCameraFocalLength(focalLength);
    }

    @Override
    public String getCameraMake() {
        return file.getCameraMake();
    }

    @Override
    public void setCameraMake(String cameraMake) {
        file.setCameraMake(cameraMake);
    }

    @Override
    public String getCameraModel() {
        return file.getCameraModel();
    }

    @Override
    public void setCameraModel(String cameraModel) {
        file.setCameraModel(cameraModel);
    }

    @Override
    public GeoLocation getGeoLocation() {
        return file.getGeoLocation();
    }

    @Override
    public void setGeoLocation(GeoLocation geoLocation) {
        file.setGeoLocation(geoLocation);
    }

    @Override
    public Long getHeight() {
        return file.getHeight();
    }

    @Override
    public void setHeight(long height) {
        file.setHeight(height);
    }

    @Override
    public Long getWidth() {
        return file.getWidth();
    }

    @Override
    public void setWidth(long width) {
        file.setWidth(width);
    }

    @Override
    public Map<String, Object> getMediaMeta() {
        return file.getMediaMeta();
    }

    @Override
    public void setMediaMeta(Map<String, Object> mediaMeta) {
        file.setMediaMeta(mediaMeta);
    }

    @Override
    public MediaStatus getMediaStatus() {
        return file.getMediaStatus();
    }

    @Override
    public void setMediaStatus(MediaStatus mediaStatus) {
        file.setMediaStatus(mediaStatus);
    }

    @Override
    public String toString() {
        return "IDManglingFile [id=" + id + ", delegateId=" + file.getId() + ", name=" + file.getFileName() + "]";
    }

    @Override
    public void setSequenceNumber(long sequenceNumber) {
        file.setSequenceNumber(sequenceNumber);
    }

    @Override
    public EntityInfo getCreatedFrom() {
        return file.getCreatedFrom();
    }

    @Override
    public void setCreatedFrom(EntityInfo createdFrom) {
        file.setCreatedFrom(createdFrom);
    }

    @Override
    public EntityInfo getModifiedFrom() {
        return file.getModifiedFrom();
    }

    @Override
    public void setModifiedFrom(EntityInfo modifiedFrom) {
        file.setModifiedFrom(modifiedFrom);
    }

    @Override
    public String getUniqueId() {
        return uniqueFileId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        throw new IllegalStateException("IDs are only read only with this class");
    }
}
