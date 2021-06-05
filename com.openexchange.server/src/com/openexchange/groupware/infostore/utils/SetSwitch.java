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

/**
 *
 */

package com.openexchange.groupware.infostore.utils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.openexchange.file.storage.MediaStatus;
import com.openexchange.groupware.container.ObjectPermission;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreFolderPath;
import com.openexchange.java.GeoLocation;

public class SetSwitch implements MetadataSwitcher {

    public static void copy(final DocumentMetadata source, final DocumentMetadata dest) {
        final SetSwitch sw = new SetSwitch(dest);
        final GetSwitch gw = new GetSwitch(source);
        for (final Metadata metadata : Metadata.VALUES) {
            sw.setValue(metadata.doSwitch(gw));
            metadata.doSwitch(sw);
        }
    }

    private Object                 value;
    private final DocumentMetadata impl;

    public SetSwitch(final DocumentMetadata impl) {
        this.impl = impl;
    }

    public void setValue(final Object value) {
        this.value = value;
    }

    @Override
    public Object meta() {
        if (null != value) {
            impl.setMeta((Map<String, Object>) value);
        }
        return null;
    }

    @Override
    public Object lastModified() {
        if (null != value) {
            impl.setLastModified((Date) value);
        }
        return null;
    }

    @Override
    public Object creationDate() {
        if (null != value) {
            impl.setCreationDate((Date) value);
        }
        return null;
    }

    @Override
    public Object modifiedBy() {
        nullNumber();
        impl.setModifiedBy(((Integer) value).intValue());
        return null;
    }

    @Override
    public Object folderId() {
        nullNumberAsLong();
        impl.setFolderId(((Long) value).longValue());
        return null;
    }

    @Override
    public Object title() {
        if (null != value) {
            impl.setTitle((String) value);
        }
        return null;
    }

    @Override
    public Object version() {
        nullNumber();
        impl.setVersion(((Integer) value).intValue());
        return null;
    }

    @Override
    public Object content() {
        //impl.setContent((String)value);
        return null;
    }

    @Override
    public Object id() {
        nullNumber();
        impl.setId(((Integer) value).intValue());
        return null;
    }

    @Override
    public Object fileSize() {
        nullNumberAsLong();
        impl.setFileSize(((Long) value).longValue());
        return null;
    }

    @Override
    public Object description() {
        if (null != value) {
            impl.setDescription((String) value);
        }
        return null;
    }

    @Override
    public Object url() {
        if (null != value) {
            impl.setURL((String) value);
        }
        return null;
    }

    @Override
    public Object createdBy() {
        nullNumber();
        impl.setCreatedBy(((Integer) value).intValue());
        return null;
    }

    @Override
    public Object fileName() {
        if (null != value) {
            impl.setFileName((String) value);
        }
        return null;
    }

    @Override
    public Object fileMIMEType() {
        if (null != value) {
            impl.setFileMIMEType((String) value);
        }
        return null;
    }

    @Override
    public Object sequenceNumber() {
        if (null != value) {
            impl.setSequenceNumber(((Long) value).longValue());
        }
        return null;
    }

    @Override
    public Object categories() {
        if (null != value) {
            impl.setCategories((String) value);
        }
        return null;
    }

    @Override
    public Object lockedUntil() {
        if (null != value) {
            impl.setLockedUntil((Date) value);
        }
        return null;
    }

    @Override
    public Object fileMD5Sum() {
        if (null != value) {
            impl.setFileMD5Sum((String) value);
        }
        return null;
    }

    @Override
    public Object versionComment() {
        if (null != value) {
            impl.setVersionComment((String) value);
        }
        return null;
    }

    @Override
    public Object currentVersion() {
        if (null != value) {
            impl.setIsCurrentVersion(((Boolean) value).booleanValue());
        }
        return null;
    }

    @Override
    public Object colorLabel() {
        nullNumber();
        impl.setColorLabel(((Integer) value).intValue());
        return null;
    }

    private void nullNumber() {
        if (value == null) {
            value = Integer.valueOf(0);
        }
    }

    private void nullNumberAsLong() {
        if (value == null) {
            value = Long.valueOf(0);
        }
    }

    @Override
    public Object filestoreLocation() {
        if (null != value) {
            impl.setFilestoreLocation((String) value);
        }
        return null;
    }

    @Override
    public Object lastModifiedUTC() {
        return lastModified();
    }

    @Override
    public Object numberOfVersions() {
        impl.setNumberOfVersions(((Integer) value).intValue());
        return null;
    }

    @Override
    public Object objectPermissions() {
        impl.setObjectPermissions((List<ObjectPermission>) value);
        return null;
    }

    @Override
    public Object shareable() {
        if (null != value) {
            impl.setShareable(((Boolean) value).booleanValue());
        }
        return null;
    }

    @Override
    public Object origin() {
        if (null == value) {
            return null;
        }
        impl.setOriginFolderPath((InfostoreFolderPath) value);
        return null;
    }

    @Override
    public Object captureDate() {
        if (null != value) {
            impl.setCaptureDate((Date) value);
        }
        return null;
    }

    @Override
    public Object geolocation() {
        if (null != value) {
            impl.setGeoLocation((GeoLocation) value);
        }
        return null;
    }

    @Override
    public Object width() {
        if (null != value) {
            impl.setWidth(((Number) value).longValue());
        }
        return null;
    }

    @Override
    public Object height() {
        if (null != value) {
            impl.setHeight(((Number) value).longValue());
        }
        return null;
    }

    @Override
    public Object cameraMake() {
        if (null != value) {
            impl.setCameraMake((String) value);
        }
        return null;
    }

    @Override
    public Object cameraModel() {
        if (null != value) {
            impl.setCameraModel((String) value);
        }
        return null;
    }

    @Override
    public Object cameraIsoSpeed() {
        if (null != value) {
            impl.setCameraIsoSpeed(((Number) value).longValue());
        }
        return null;
    }

    @Override
    public Object cameraAperture() {
        if (null != value) {
            impl.setCameraAperture(((Number) value).doubleValue());
        }
        return null;
    }

    @Override
    public Object cameraExposureTime() {
        if (null != value) {
            impl.setCameraExposureTime(((Number) value).doubleValue());
        }
        return null;
    }

    @Override
    public Object cameraFocalLength() {
        if (null != value) {
            impl.setCameraFocalLength(((Number) value).doubleValue());
        }
        return null;
    }

    @Override
    public Object mediaMeta() {
        if (null != value) {
            impl.setMediaMeta((Map<String, Object>) value);
        }
        return null;
    }

    @Override
    public Object mediaStatus() {
        if (null != value) {
            impl.setMediaStatus((MediaStatus) value);
        }
        return null;
    }

    @Override
    public Object mediaDate() {
        return null;
    }

}
