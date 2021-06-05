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

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.groupware.EntityInfo;
import com.openexchange.java.GeoLocation;

/**
 * {@link DefaultFile}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class DefaultFile extends AbstractFile {

    private static final String DEFAULT_TYPE = "application/octet-stream";

    private String categories;
    private int colorLabel;
    private String content;
    private Date created;
    private int createdBy;
    private String description;
    private String fileMD5Sum;
    private String fileMIMEType;
    private String fileName;
    private long fileSize;
    private String folderId;
    private String id;
    private Long sequenceNumber;
    private Date lastModified;
    private Date lockedUntil;
    private int modifiedBy;
    private int numberOfVersions;
    private final Map<String, String> properties;
    private String title;
    private String url;
    private String version;
    private String versionComment;
    private boolean isCurrentVersion;
    private Map<String, Object> dynamicProperties;
    private List<FileStorageObjectPermission> objectPermissions;
    private boolean shareable;
    private FolderPath origin;
    private Date captureDate;
    private GeoLocation geoLocation;
    private Long width = null;
    private Long height = null;
    private String cameraMake = null;
    private String cameraModel = null;
    private Long cameraIsoSpeed = null;
    private Double cameraAperture = null;
    private Double cameraExposureTime = null;
    private Double cameraFocalLength = null;
    private Map<String, Object> mediaMeta = null;
    private MediaStatus mediaStatus = null;
    private EntityInfo createdFrom;
    private EntityInfo modifiedFrom;
    private String uniqueId;

    /**
     * Initializes a new {@link DefaultFile}.
     */
    public DefaultFile() {
        super();
        fileMIMEType = DEFAULT_TYPE;
        properties = new HashMap<String, String>();
        dynamicProperties = new LinkedHashMap<String, Object>();
    }

    /**
     * Initializes a new {@link DefaultFile} from given file.
     */
    public DefaultFile(final File file) {
        super();
        dynamicProperties = new LinkedHashMap<String, Object>();
        final Set<String> propertyNames = file.getPropertyNames();
        final Map<String, String> properties = new HashMap<String, String>(propertyNames.size());
        for (final String propertyName : propertyNames) {
            properties.put(propertyName, file.getProperty(propertyName));
        }
        this.properties = properties;
        copyFrom(file);
    }

    @Override
    public String getCategories() {
        return categories;
    }

    @Override
    public int getColorLabel() {
        return colorLabel;
    }

    @Override
    public String getContent() {
        return content;
    }

    /**
     * Sets the content of this file.
     *
     * @param content The content
     */
    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public int getCreatedBy() {
        return createdBy;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getFileMD5Sum() {
        return fileMD5Sum;
    }

    @Override
    public String getFileMIMEType() {
        return fileMIMEType;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public long getFileSize() {
        return fileSize;
    }

    @Override
    public String getFolderId() {
        return folderId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Date getLastModified() {
        return lastModified;
    }

    @Override
    public Date getLockedUntil() {
        return lockedUntil;
    }

    @Override
    public int getModifiedBy() {
        return modifiedBy;
    }

    @Override
    public int getNumberOfVersions() {
        return numberOfVersions;
    }

    @Override
    public String getProperty(final String key) {
        return properties.get(key);
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    /**
     * Sets specified property. A <code>null</code> value removes the property.
     *
     * @param name The name
     * @param value The value or <code>null</code> for removal
     */
    public void setProperty(final String name, final String value) {
        if (null == value) {
            properties.remove(name);
        } else {
            properties.put(name, value);
        }
    }

    @Override
    public long getSequenceNumber() {
        Long sequenceNumber = this.sequenceNumber;
        if (null != sequenceNumber) {
            return sequenceNumber.longValue();
        }

        Date lastModified = this.lastModified;
        return lastModified == null ? 0 : lastModified.getTime();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getURL() {
        return url;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getVersionComment() {
        return versionComment;
    }

    @Override
    public boolean isCurrentVersion() {
        return isCurrentVersion;
    }

    @Override
    public boolean isShareable() {
        return shareable;
    }

    @Override
    public FolderPath getOrigin() {
        return origin;
    }

    @Override
    public void setOrigin(FolderPath origin) {
        this.origin = origin;
    }

    @Override
    public void setCategories(final String categories) {
        this.categories = categories;
    }

    @Override
    public void setColorLabel(final int color) {
        colorLabel = color;
    }

    @Override
    public void setCreated(final Date creationDate) {
        created = creationDate;
    }

    @Override
    public void setCreatedBy(final int creator) {
        createdBy = creator;
    }

    @Override
    public void setDescription(final String description) {
        this.description = description;
    }

    @Override
    public void setFileMD5Sum(final String sum) {
        fileMD5Sum = sum;
    }

    @Override
    public void setFileMIMEType(final String type) {
        fileMIMEType = type;
    }

    @Override
    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void setFileSize(final long length) {
        fileSize = length;
    }

    @Override
    public void setFolderId(final String folderId) {
        this.folderId = folderId;
    }

    @Override
    public void setId(final String id) {
        this.id = id;
    }

    @Override
    public void setIsCurrentVersion(final boolean bool) {
        isCurrentVersion = bool;
    }

    @Override
    public void setLastModified(final Date now) {
        lastModified = now;
    }

    @Override
    public void setLockedUntil(final Date lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    @Override
    public void setModifiedBy(final int lastEditor) {
        modifiedBy = lastEditor;
    }

    @Override
    public void setNumberOfVersions(final int numberOfVersions) {
        this.numberOfVersions = numberOfVersions;
    }

    @Override
    public void setTitle(final String title) {
        this.title = title;
    }

    @Override
    public void setURL(final String url) {
        this.url = url;
    }

    @Override
    public void setVersion(final String version) {
        this.version = version;
    }

    @Override
    public void setVersionComment(final String string) {
        versionComment = string;
    }

    @Override
    public void setMeta(Map<String, Object> properties) {
        this.dynamicProperties = properties;
    }

    @Override
    public Map<String, Object> getMeta() {
        return dynamicProperties;
    }

    @Override
    public void setObjectPermissions(List<FileStorageObjectPermission> objectPermissions) {
        this.objectPermissions = objectPermissions;
    }

    @Override
    public List<FileStorageObjectPermission> getObjectPermissions() {
        return objectPermissions;
    }

    @Override
    public void setShareable(boolean shareable) {
        this.shareable = shareable;
    }

    @Override
    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = Long.valueOf(sequenceNumber);
    }

    @Override
    public Date getCaptureDate() {
        return captureDate;
    }

    @Override
    public void setCaptureDate(Date captureDate) {
        this.captureDate = captureDate;
    }

    @Override
    public GeoLocation getGeoLocation() {
        return geoLocation;
    }

    @Override
    public void setGeoLocation(GeoLocation geoLocation) {
        this.geoLocation = geoLocation;
    }

    @Override
    public Long getWidth() {
        return width;
    }

    @Override
    public void setWidth(long width) {
        if (width < 0) {
            this.width = null;
        } else {
            this.width = Long.valueOf(width);
        }
    }

    @Override
    public Long getHeight() {
        return height;
    }

    @Override
    public void setHeight(long height) {
        if (height < 0) {
            this.height = null;
        } else {
            this.height = Long.valueOf(height);
        }
    }

    @Override
    public Long getCameraIsoSpeed() {
        return cameraIsoSpeed;
    }

    @Override
    public void setCameraIsoSpeed(long isoSpeed) {
        if (isoSpeed < 0) {
            this.cameraIsoSpeed = null;
        } else {
            this.cameraIsoSpeed = Long.valueOf(isoSpeed);
        }
    }

    @Override
    public Double getCameraAperture() {
        return cameraAperture;
    }

    @Override
    public void setCameraAperture(double aperture) {
        if (aperture < 0) {
            this.cameraAperture = null;
        } else {
            this.cameraAperture = Double.valueOf(aperture);
        }
    }

    @Override
    public Double getCameraExposureTime() {
        return cameraExposureTime;
    }

    @Override
    public void setCameraExposureTime(double exposureTime) {
        if (exposureTime < 0) {
            this.cameraExposureTime = null;
        } else {
            this.cameraExposureTime = Double.valueOf(exposureTime);
        }
    }

    @Override
    public Double getCameraFocalLength() {
        return cameraFocalLength;
    }

    @Override
    public void setCameraFocalLength(double focalLength) {
        if (focalLength < 0) {
            this.cameraFocalLength = null;
        } else {
            this.cameraFocalLength = Double.valueOf(focalLength);
        }
    }

    @Override
    public String getCameraMake() {
        return cameraMake;
    }

    @Override
    public void setCameraMake(String cameraMake) {
        this.cameraMake = cameraMake;
    }

    @Override
    public String getCameraModel() {
        return cameraModel;
    }

    @Override
    public void setCameraModel(String cameraModel) {
        this.cameraModel = cameraModel;
    }

    @Override
    public Map<String, Object> getMediaMeta() {
        return mediaMeta;
    }

    @Override
    public void setMediaMeta(Map<String, Object> mediaMeta) {
        this.mediaMeta = mediaMeta;
    }

    @Override
    public MediaStatus getMediaStatus() {
        return mediaStatus;
    }

    @Override
    public void setMediaStatus(MediaStatus mediaStatus) {
        this.mediaStatus = mediaStatus;
    }

    @Override
    public EntityInfo getCreatedFrom() {
        return createdFrom;
    }

    @Override
    public void setCreatedFrom(EntityInfo createdFrom) {
        this.createdFrom = createdFrom;
    }

    @Override
    public EntityInfo getModifiedFrom() {
        return modifiedFrom;
    }

    @Override
    public void setModifiedFrom(EntityInfo modifiedFrom) {
        this.modifiedFrom = modifiedFrom;
    }

    @Override
    public String getUniqueId() {
        return this.uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

}
