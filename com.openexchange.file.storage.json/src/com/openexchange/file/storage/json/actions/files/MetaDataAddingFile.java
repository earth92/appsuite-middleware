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

package com.openexchange.file.storage.json.actions.files;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.file.storage.DelegatingFile;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.FileStorageObjectPermission;
import com.openexchange.file.storage.FolderPath;
import com.openexchange.file.storage.MediaStatus;
import com.openexchange.groupware.EntityInfo;
import com.openexchange.java.GeoLocation;


/**
 * {@link MetaDataAddingFile} - Possibly adds meta-data to an existing file.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class MetaDataAddingFile implements DelegatingFile {

    private final File file;
    private Map<String, Object> metaData;

    /**
     * Initializes a new {@link MetaDataAddingFile}.
     */
    public MetaDataAddingFile(File file) {
        super();
        this.file = file;
    }

    @Override
    public File getDelegate() {
        return file;
    }

    @Override
    public String getProperty(String key) {
        return file.getProperty(key);
    }

    @Override
    public Set<String> getPropertyNames() {
        return file.getPropertyNames();
    }

    @Override
    public Date getLastModified() {
        return file.getLastModified();
    }

    @Override
    public void setLastModified(Date now) {
        file.setLastModified(now);
    }

    @Override
    public Date getCreated() {
        return file.getCreated();
    }

    @Override
    public void setCreated(Date creationDate) {
        file.setCreated(creationDate);
    }

    @Override
    public int getModifiedBy() {
        return file.getModifiedBy();
    }

    @Override
    public void setModifiedBy(int lastEditor) {
        file.setModifiedBy(lastEditor);
    }

    @Override
    public String getFolderId() {
        return file.getFolderId();
    }

    @Override
    public void setFolderId(String folderId) {
        file.setFolderId(folderId);
    }

    @Override
    public String getTitle() {
        return file.getTitle();
    }

    @Override
    public void setTitle(String title) {
        file.setTitle(title);
    }

    @Override
    public String getVersion() {
        return file.getVersion();
    }

    @Override
    public void setVersion(String version) {
        file.setVersion(version);
    }

    @Override
    public String getContent() {
        return file.getContent();
    }

    @Override
    public long getFileSize() {
        return file.getFileSize();
    }

    @Override
    public void setFileSize(long length) {
        file.setFileSize(length);
    }

    @Override
    public String getFileMIMEType() {
        return file.getFileMIMEType();
    }

    @Override
    public void setFileMIMEType(String type) {
        file.setFileMIMEType(type);
    }

    @Override
    public String getFileName() {
        return file.getFileName();
    }

    @Override
    public void setFileName(String fileName) {
        file.setFileName(fileName);
    }

    @Override
    public String getId() {
        return file.getId();
    }

    @Override
    public void setId(String id) {
        file.setId(id);
    }

    @Override
    public int getCreatedBy() {
        return file.getCreatedBy();
    }

    @Override
    public void setCreatedBy(int cretor) {
        file.setCreatedBy(cretor);
    }

    @Override
    public String getDescription() {
        return file.getDescription();
    }

    @Override
    public void setDescription(String description) {
        file.setDescription(description);
    }

    @Override
    public String getURL() {
        return file.getURL();
    }

    @Override
    public void setURL(String url) {
        file.setURL(url);
    }

    @Override
    public long getSequenceNumber() {
        return file.getSequenceNumber();
    }

    @Override
    public String getCategories() {
        return file.getCategories();
    }

    @Override
    public void setCategories(String categories) {
        file.setCategories(categories);
    }

    @Override
    public Date getLockedUntil() {
        return file.getLockedUntil();
    }

    @Override
    public void setLockedUntil(Date lockedUntil) {
        file.setLockedUntil(lockedUntil);
    }

    @Override
    public String getFileMD5Sum() {
        return file.getFileMD5Sum();
    }

    @Override
    public void setFileMD5Sum(String sum) {
        file.setFileMD5Sum(sum);
    }

    @Override
    public int getColorLabel() {
        return file.getColorLabel();
    }

    @Override
    public void setColorLabel(int color) {
        file.setColorLabel(color);
    }

    @Override
    public boolean isCurrentVersion() {
        return file.isCurrentVersion();
    }

    @Override
    public void setIsCurrentVersion(boolean bool) {
        file.setIsCurrentVersion(bool);
    }

    @Override
    public String getVersionComment() {
        return file.getVersionComment();
    }

    @Override
    public void setVersionComment(String string) {
        file.setVersionComment(string);
    }

    @Override
    public void setNumberOfVersions(int numberOfVersions) {
        file.setNumberOfVersions(numberOfVersions);
    }

    @Override
    public int getNumberOfVersions() {
        return file.getNumberOfVersions();
    }

    @Override
    public Map<String, Object> getMeta() {
        return null == metaData ? file.getMeta() : metaData;
    }

    @Override
    public void setMeta(Map<String, Object> properties) {
        this.metaData = properties;
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
    public List<FileStorageObjectPermission> getObjectPermissions() {
        return file.getObjectPermissions();
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
    public File dup() {
        return file.dup();
    }

    @Override
    public void copyInto(File other) {
        file.copyInto(other);
    }

    @Override
    public void copyFrom(File other) {
        file.copyFrom(other);
    }

    @Override
    public void copyInto(File other, Field... fields) {
        file.copyInto(other, fields);
    }

    @Override
    public void copyFrom(File other, Field... fields) {
        file.copyFrom(other, fields);
    }

    @Override
    public Set<Field> differences(File other) {
        return file.differences(other);
    }

    @Override
    public boolean equals(File other, Field criterium, Field... criteria) {
        return file.equals(other, criterium, criteria);
    }

    @Override
    public boolean matches(String pattern, Field... fields) {
        return file.matches(pattern, fields);
    }

    @Override
    public void setSequenceNumber(long sequenceNumber) {
        file.setSequenceNumber(sequenceNumber);
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
    public GeoLocation getGeoLocation() {
        return file.getGeoLocation();
    }

    @Override
    public void setGeoLocation(GeoLocation geoLocation) {
        file.setGeoLocation(geoLocation);
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
    public Long getHeight() {
        return file.getHeight();
    }

    @Override
    public void setHeight(long height) {
        file.setHeight(height);
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
        return file.getUniqueId();
    }

    @Override
    public void setUniqueId(String uniqueId) {
        file.setUniqueId(uniqueId);
    }

}
