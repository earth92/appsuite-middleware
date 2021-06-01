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

package com.openexchange.file.storage.googledrive;

import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.ROOT_ID;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.File.ImageMediaMetadata;
import com.google.api.services.drive.model.File.ImageMediaMetadata.Location;
import com.google.api.services.drive.model.Revision;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.MediaStatus;
import com.openexchange.file.storage.googledrive.osgi.Services;
import com.openexchange.java.GeoLocation;
import com.openexchange.mime.MimeTypeMap;

/**
 * {@link GoogleDriveFile}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class GoogleDriveFile extends DefaultFile {

    private final static Logger LOGGER = LoggerFactory.getLogger(GoogleDriveFile.class);

    private final static SimpleDateFormat EXIF_FORMAT;

    static {
        EXIF_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        EXIF_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Initializes a new {@link GoogleDriveFile}.
     *
     * @param folderId The folder identifier
     * @param id The file identifier
     * @param userId The user identifier
     * @param rootFolderId The identifier of the root folder in the account
     */
    public GoogleDriveFile(String folderId, String id, int userId, String rootFolderId) {
        super();
        setFolderId(isRootFolder(folderId, rootFolderId) ? FileStorageFolder.ROOT_FULLNAME : folderId);
        setCreatedBy(userId);
        setModifiedBy(userId);
        setId(id);
        setFileName(id);
        setVersion(FileStorageFileAccess.CURRENT_VERSION);
        setIsCurrentVersion(true);
        setUniqueId(id);
    }

    private static boolean isRootFolder(String id, String rootFolderId) {
        return ROOT_ID.equals(id) || rootFolderId.equals(id);
    }

    @Override
    public String toString() {
        final String url = getURL();
        return url == null ? super.toString() : url;
    }

    /**
     * Parses specified Google Drive file.
     *
     * @param file The Google Drive file
     * @throws OXException If parsing Google Drive file fails
     * @return This Google Drive file
     */
    public GoogleDriveFile parseGoogleDriveFile(File file) throws OXException {
        return parseGoogleDriveFile(file, null);
    }

    /**
     * Parses specified Google Drive file.
     *
     * @param file The Google Drive file
     * @param fields The fields to consider
     * @throws OXException If parsing Google Drive file fails
     * @return This Google Drive file with property set applied
     */
    public GoogleDriveFile parseGoogleDriveFile(File file, List<Field> fields) throws OXException {
        if (null != file) {
            try {
                final String name = file.getName();
                setTitle(name);
                setFileName(name);

                final Set<Field> set = null == fields || fields.isEmpty() ? EnumSet.allOf(Field.class) : EnumSet.copyOf(fields);
                if (set.contains(Field.CREATED)) {
                    DateTime createdDate = file.getCreatedTime();
                    if (null != createdDate) {
                        setCreated(new Date(createdDate.getValue()));
                    }
                }
                if (set.contains(Field.LAST_MODIFIED) || set.contains(Field.LAST_MODIFIED_UTC)) {
                    DateTime modifiedDate = file.getModifiedTime();
                    if (null != modifiedDate) {
                        setLastModified(new Date(modifiedDate.getValue()));
                    }
                }
                if (set.contains(Field.FILE_MIMETYPE)) {
                    String contentType = file.getMimeType();
                    if (com.openexchange.java.Strings.isEmpty(contentType)) {
                        final MimeTypeMap map = Services.getService(MimeTypeMap.class);
                        contentType = map.getContentType(name);
                    }
                    setFileMIMEType(contentType);
                }
                if (set.contains(Field.FILE_SIZE)) {
                    Long fileSize = file.getSize();
                    if (null != fileSize) {
                        setFileSize(fileSize.longValue());
                    }
                }
                if (set.contains(Field.URL)) {
                    setURL(file.getWebContentLink());
                }
                if (set.contains(Field.COLOR_LABEL)) {
                    setColorLabel(0);
                }
                if (set.contains(Field.CATEGORIES)) {
                    setCategories(null);
                }
                if (set.contains(Field.DESCRIPTION)) {
                    setDescription(file.getDescription());
                }
                if (set.contains(Field.VERSION_COMMENT)) {
                    setVersionComment(null);
                }
                if (set.contains(Field.FILE_MD5SUM)) {
                    setFileMD5Sum(file.getMd5Checksum());
                }
                // ---------- MEDIA STUFF ----------
                ImageMediaMetadata imageMediaMetadata = file.getImageMediaMetadata();
                if (null != imageMediaMetadata) {
                    setMediaStatus(MediaStatus.valueFor(MediaStatus.Status.DONE_SUCCESS));
                    if (set.contains(Field.CAPTURE_DATE)) {
                        if (null != imageMediaMetadata.getTime()) {
                            try {
                                Date date;
                                synchronized (EXIF_FORMAT) {
                                    /*
                                     * EXIF doesn't standardize time zones, therefore the actual time is best guess. Using UTC for the moment
                                     */
                                    date = EXIF_FORMAT.parse(imageMediaMetadata.getTime());
                                }
                                setCaptureDate(date);
                            } catch (ParseException e) {
                                LOGGER.debug("Unable to set capture date.", e);
                            }
                        }
                    }
                    if (set.contains(Field.GEOLOCATION)) {
                        if (null != imageMediaMetadata.getLocation()) {
                            Location location = imageMediaMetadata.getLocation();
                            if (null != location.getLatitude() && null != location.getLongitude()) {
                                setGeoLocation(new GeoLocation(location.getLatitude().doubleValue(), location.getLongitude().doubleValue()));
                            }
                        }
                    }
                    if (set.contains(Field.WIDTH)) {
                        if (null != imageMediaMetadata.getWidth()) {
                            setWidth(imageMediaMetadata.getWidth().longValue());
                        }
                    }
                    if (set.contains(Field.HEIGHT)) {
                        if (null != imageMediaMetadata.getHeight()) {
                            setHeight(imageMediaMetadata.getHeight().longValue());
                        }
                    }
                    if (set.contains(Field.CAMERA_MAKE)) {
                        if (null != imageMediaMetadata.getCameraMake()) {
                            setCameraMake(imageMediaMetadata.getCameraMake());
                        }
                    }
                    if (set.contains(Field.CAMERA_MODEL)) {
                        if (null != imageMediaMetadata.getCameraModel()) {
                            setCameraModel(imageMediaMetadata.getCameraModel());
                        }
                    }
                    if (set.contains(Field.CAMERA_ISO_SPEED)) {
                        if (null != imageMediaMetadata.getIsoSpeed()) {
                            setCameraIsoSpeed(imageMediaMetadata.getIsoSpeed().longValue());
                        }
                    }
                    if (set.contains(Field.CAMERA_APERTURE)) {
                        if (null != imageMediaMetadata.getAperture()) {
                            setCameraAperture(imageMediaMetadata.getAperture().doubleValue());
                        }
                    }
                    if (set.contains(Field.CAMERA_EXPOSURE_TIME)) {
                        if (null != imageMediaMetadata.getExposureTime()) {
                            setCameraExposureTime(imageMediaMetadata.getExposureTime().doubleValue());
                        }
                    }
                    if (set.contains(Field.CAMERA_FOCAL_LENGTH)) {
                        if (null != imageMediaMetadata.getFocalLength()) {
                            setCameraFocalLength(imageMediaMetadata.getFocalLength().doubleValue());
                        }
                    }
                } else {
                    setMediaStatus(MediaStatus.valueFor(MediaStatus.Status.NONE));
                }

            } catch (RuntimeException e) {
                throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
            }
        }
        return this;
    }

    /**
     * Parses specified Google Drive revision.
     *
     * @param revision The Google Drive revision
     * @param name The file name/title to set
     * @param fields The fields to consider
     * @throws OXException If parsing Google Drive file fails
     * @return This Google Drive file with property set applied
     */
    public GoogleDriveFile parseRevision(Revision revision, String name, List<Field> fields) throws OXException {
        if (null != revision) {
            try {
                setVersion(revision.getId());
                String n = null == name ? revision.getOriginalFilename() : name;
                setTitle(n);
                setFileName(n);

                Set<Field> set = null == fields || fields.isEmpty() ? EnumSet.allOf(Field.class) : EnumSet.copyOf(fields);
                if (set.contains(Field.LAST_MODIFIED) || set.contains(Field.LAST_MODIFIED_UTC)) {
                    DateTime modifiedDate = revision.getModifiedTime();
                    if (null != modifiedDate) {
                        setLastModified(new Date(modifiedDate.getValue()));
                    }
                }
                if (set.contains(Field.FILE_MIMETYPE)) {
                    String contentType = revision.getMimeType();
                    if (com.openexchange.java.Strings.isEmpty(contentType)) {
                        final MimeTypeMap map = Services.getService(MimeTypeMap.class);
                        contentType = map.getContentType(n);
                    }
                    setFileMIMEType(contentType);
                }
                if (set.contains(Field.FILE_SIZE)) {
                    Long fileSize = revision.getSize();
                    if (null != fileSize) {
                        setFileSize(fileSize.longValue());
                    }
                }
                if (set.contains(Field.URL)) {
                    Object webContentLink = revision.get("webContentLink");
                    if (null != webContentLink && String.class.isAssignableFrom(webContentLink.getClass())) {
                        setURL((String) webContentLink);
                    }
                }
                if (set.contains(Field.FILE_MD5SUM)) {
                    setFileMD5Sum(revision.getMd5Checksum());
                }
            } catch (RuntimeException e) {
                throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
            }
        }
        return this;
    }
}
