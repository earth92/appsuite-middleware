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

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.common.collect.ImmutableSet;
import com.openexchange.groupware.EntityInfo;
import com.openexchange.java.GeoLocation;

/**
 * A {@link File} represents the meta data known about a file
 */
public interface File {

    /**
     * The default search fields: {@link Field#TITLE}, {@link Field#FILENAME}, {@link Field#DESCRIPTION}, {@link Field#URL}, {@link Field#CATEGORIES}, {@link Field#VERSION_COMMENT}
     */
    public static final Set<Field> DEFAULT_SEARCH_FIELDS = ImmutableSet.of(Field.TITLE, Field.FILENAME, Field.DESCRIPTION, Field.URL, Field.CATEGORIES, Field.VERSION_COMMENT);

    String getProperty(String key);

    Set<String> getPropertyNames();

    Date getLastModified();

    void setLastModified(Date now);

    Date getCreated();

    void setCreated(Date creationDate);

    int getModifiedBy();

    void setModifiedBy(int lastEditor);

    String getFolderId();

    void setFolderId(String folderId);

    String getTitle();

    void setTitle(String title);

    String getVersion();

    void setVersion(String version);

    String getContent();

    long getFileSize();

    void setFileSize(long length);

    String getFileMIMEType();

    void setFileMIMEType(String type);

    String getFileName();

    void setFileName(String fileName);

    String getId();

    void setId(String id);

    int getCreatedBy();

    void setCreatedBy(int cretor);

    String getDescription();

    void setDescription(String description);

    String getURL();

    void setURL(String url);

    /**
     * Gets the sequence number associated with this file
     *
     * @return The sequence number as a UTC long (the number of milliseconds since January 1, 1970, 00:00:00 GMT)
     */
    long getSequenceNumber();

    String getCategories();

    void setCategories(String categories);

    Date getLockedUntil();

    void setLockedUntil(Date lockedUntil);

    String getFileMD5Sum();

    void setFileMD5Sum(String sum);

    int getColorLabel();

    void setColorLabel(int color);

    boolean isCurrentVersion();

    void setIsCurrentVersion(boolean bool);

    String getVersionComment();

    void setVersionComment(String string);

    void setNumberOfVersions(int numberOfVersions);

    int getNumberOfVersions();

    Map<String, Object> getMeta();

    void setMeta(Map<String, Object> properties);

    EntityInfo getCreatedFrom();

    void setCreatedFrom(EntityInfo createdFrom);

    EntityInfo getModifiedFrom();

    void setModifiedFrom(EntityInfo modifiedFrom);

    /**
     * 
     * Gets the lifetime unique identifier of the file that does not change e.g. after move or rename operations.
     *
     * @return The lifetime unique identifier.
     */
    String getUniqueId();

    /**
     * 
     * Sets the lifetime unique identifier of the file that does not change e.g. after move or rename operations.
     *
     * @param uniqueId The lifetime unique identifier.
     */
    void setUniqueId(String uniqueId);

    /**
     * Checks whether {@link #getFileSize()} returns the exact size w/o any encodings (e.g. base64) applied.
     *
     * @return <code>true</code> for exact size; otherwise <code>false</code>
     */
    boolean isAccurateSize();

    /**
     * Sets whether the {@link #getFileSize()} returns the exact size w/o any encodings (e.g. base64) applied
     *
     * @param accurateSize <code>true</code> for exact size; otherwise <code>false</code>
     */
    void setAccurateSize(boolean accurateSize);

    /**
     * Gets the object permissions in case they are defined.
     *
     * @return A list holding additional object permissions, or <code>null</code> if not defined or not supported by the storage
     */
    List<FileStorageObjectPermission> getObjectPermissions();

    /**
     * Sets the object permissions.
     *
     * @param objectPermissions The object permissions to set, or <code>null</code> to remove previously set permissions
     */
    void setObjectPermissions(List<FileStorageObjectPermission> objectPermissions);

    /**
     * Gets a value indicating whether the item can be shared to others based on underlying storage's capabilities and the permissions of
     * the requesting user.
     *
     * @return <code>true</code> if the file is shareable, <code>false</code>, otherwise
     */
    boolean isShareable();

    /**
     * Sets the flag indicating that the item can be shared to others based on underlying storage's capabilities and the permissions of
     * the requesting user.
     *
     * @param shareable <code>true</code> if the file is shareable, <code>false</code>, otherwise
     */
    void setShareable(boolean shareable);

    /**
     * Sets the sequence number of the file.
     * <p>
     * This method needs to be called, if the internal time stamp changes while the file is processed. If no sequenceNumber is set, the last modified time stamp can be used as well.
     *
     * @param sequenceNumber The sequence number as a UTC long (the number of milliseconds since January 1, 1970, 00:00:00 GMT)
     */
    void setSequenceNumber(long sequenceNumber);

    /**
     * Gets the origin folder path.
     *
     * @return The origin folder path or <code>null</code>
     */
    FolderPath getOrigin();

    /**
     * Sets the origin folder path.
     *
     * @param origin The origin folder path to set
     */
    void setOrigin(FolderPath origin);

    // ------------------------------------------------------------ MEDIA STUFF ------------------------------------------------------------

    /**
     * Gets the capture date of the image associated with this file
     *
     * @return The capture date
     */
    Date getCaptureDate();

    /**
     * Sets the capture date of the image associated with this file
     *
     * @param captureDate The capture date
     */
    void setCaptureDate(Date captureDate);

    /**
     * Gets the geo location of the media resource associated with this file
     *
     * @return The geo location
     */
    GeoLocation getGeoLocation();

    /**
     * Sets the geo location of the media resource associated with this file
     *
     * @param geoLocation The geo location
     */
    void setGeoLocation(GeoLocation geoLocation);

    /**
     * Gets the width of the media resource associated with this file
     *
     * @return The width or <code>null</code> if unknown/not set
     */
    Long getWidth();

    /**
     * Sets the width of the media resource associated with this file
     *
     * @param width The width
     */
    void setWidth(long width);

    /**
     * Gets the height of the media resource associated with this file
     *
     * @return The height or <code>null</code> if unknown/not set
     */
    Long getHeight();

    /**
     * Sets the height of the media resource associated with this file
     *
     * @param heigth The height
     */
    void setHeight(long height);

    /**
     * Gets the name for the manufacturer of the recording equipment used to create the photo.
     *
     * @return The camera make or <code>null</code> if unknown/not set
     */
    String getCameraMake();

    /**
     * Sets the name for the manufacturer of the recording equipment used to create the photo.
     *
     * @param cameraMake The model make
     */
    void setCameraMake(String cameraMake);

    /**
     * Gets the name of the camera model associated with this file
     *
     * @return The camera model or <code>null</code> if unknown/not set
     */
    String getCameraModel();

    /**
     * Sets the name of the camera model associated with this file
     *
     * @param cameraModel The duration
     */
    void setCameraModel(String cameraModel);

    /**
     * Gets ISO speed value of a camera or input device associated with this file
     *
     * @return The ISO speed value or <code>null</code> if unknown/not set
     */
    Long getCameraIsoSpeed();

    /**
     * Sets ISO speed value of a camera or input device associated with this file
     *
     * @param isoSpeed The ISO speed value
     */
    void setCameraIsoSpeed(long isoSpeed);

    /**
     * Gets the aperture used to create the photo (f-number).
     *
     * @return The value or <code>null</code> for none
     */
    java.lang.Double getCameraAperture();

    /**
     * Set the aperture used to create the photo (f-number).
     *
     * @param aperture The aperture
     */
    void setCameraAperture(double aperture);

    /**
     * Gets the focal length used to create the photo, in millimeters.
     *
     * @return The value or <code>null</code> for none
     */
    java.lang.Double getCameraFocalLength();

    /**
     * Sets the focal length used to create the photo, in millimeters.
     *
     * @param focalLength The focal length
     */
    void setCameraFocalLength(double focalLength);

    /**
     * Gets the length of the exposure, in seconds.
     *
     * @return The value or <code>null</code> for none
     */
    java.lang.Double getCameraExposureTime();

    /**
     * Sets the length of the exposure, in seconds.
     *
     * @param exposureTime The exposure time
     */
    void setCameraExposureTime(double exposureTime);

    /**
     * Gets the meta information for the media resource associated with this file
     *
     * @return The meta information
     */
    Map<String, Object> getMediaMeta();

    /**
     * Sets the meta information for the media resource associated with this file
     *
     * @param mediaMeta The meta information
     */
    void setMediaMeta(Map<String, Object> mediaMeta);

    /**
     * Gets the status of parsing/analyzing media meta-data from the media resource associated with this file
     *
     * @return The media status
     */
    MediaStatus getMediaStatus();

    /**
     * Sets the status of parsing/analyzing media meta-data from the media resource associated with this file
     *
     * @param mediaStatus The media status
     */
    void setMediaStatus(MediaStatus mediaStatus);

    // --------------------------------------------------------- END OF MEDIA STUFF --------------------------------------------------------

    File dup();

    void copyInto(File other);

    void copyFrom(File other);

    void copyInto(File other, Field...fields);

    void copyFrom(File other, Field...fields);

    Set<File.Field> differences(File other);

    boolean equals(File other, Field criterium, Field...criteria);

    /**
     * Indicates whether this file matches given pattern.
     *
     * @param pattern The pattern possibly containing wild-card characters
     * @param fields The fields to consider; if <code>null</code> {@link #DEFAULT_SEARCH_FIELDS} is used
     * @return <code>true</code> if this file matches; otherwise <code>false</code>
     */
    boolean matches(String pattern, Field... fields);

    /**
     * An enumeration of file fields.
     */
    public static enum Field {

        LAST_MODIFIED("last_modified", 5),
        CREATED("creation_date", 4),
        MODIFIED_BY("modified_by", 3),
        FOLDER_ID("folder_id", 20),
        TITLE("title", 700),
        VERSION("version", 705),
        CONTENT("content", 750),
        ID("id", 1),
        FILE_SIZE("file_size", 704),
        DESCRIPTION("description", 706),
        URL("url", 701),
        CREATED_BY("created_by", 2),
        FILENAME("filename", 702),
        FILE_MIMETYPE("file_mimetype", 703),
        SEQUENCE_NUMBER("sequence_number", 751),
        CATEGORIES("categories", 100),
        LOCKED_UNTIL("locked_until", 707),
        FILE_MD5SUM("file_md5sum", 708),
        VERSION_COMMENT("version_comment", 709),
        CURRENT_VERSION("current_version", 710),
        COLOR_LABEL("color_label", 102),
        LAST_MODIFIED_UTC("last_modified_utc", 6),
        NUMBER_OF_VERSIONS("number_of_versions", 711),
        META("meta", 23),
        OBJECT_PERMISSIONS("object_permissions", 108),
        SHAREABLE("shareable", 109),
        ORIGIN("origin", 712),
        CAPTURE_DATE("capture_date", 713),
        GEOLOCATION("geolocation", 714),
        WIDTH("width", 715),
        HEIGHT("height", 716),
        CAMERA_MAKE("camera_make", 717),
        CAMERA_MODEL("camera_model", 718),
        CAMERA_ISO_SPEED("camera_iso_speed", 719),
        CAMERA_APERTURE("camera_aperture", 720),
        CAMERA_EXPOSURE_TIME("camera_exposure_time", 721),
        CAMERA_FOCAL_LENGTH("camera_focal_length", 722),
        MEDIA_META("media_meta", 723),
        MEDIA_STATUS("media_status", 724),
        MEDIA_DATE("media_date", 725),
        CREATED_FROM("created_from", 51),
        MODIFIED_FROM("modified_from", 52),
        UNIQUE_ID("unique_id", 726)
        ;

        /** The set containing all media-associated fields */
        public static final Set<Field> MEDIA_FIELDS = EnumSet.of(CAPTURE_DATE, GEOLOCATION, WIDTH, HEIGHT, CAMERA_MAKE, CAMERA_MODEL, CAMERA_APERTURE, CAMERA_EXPOSURE_TIME, CAMERA_FOCAL_LENGTH, CAMERA_ISO_SPEED, MEDIA_META, MEDIA_STATUS, MEDIA_DATE);

        private final int number;
        private final String name;

        private Field(final String name, final int number) {
            this.number = number;
            this.name = name;
        }

        /**
         * Gets the field's name (e.g. when outputting JSON content).
         *
         * @return The name
         */
        public String getName() {
            return name;
        }

        /**
         * Gets the field's number.
         *
         * @return The number
         */
        public int getNumber() {
            return number;
        }

        /**
         * Applies specified switcher to this field by invoking appropriate method from given switcher instance.
         *
         * @param switcher The switcher to invoke
         * @param args The optional arguments to pass to the switcher invocation
         * @return The resulting object
         */
        public Object doSwitch(final FileFieldSwitcher switcher, final Object... args) {
            switch (this) {
            case LAST_MODIFIED:
                return switcher.lastModified(args);
            case CREATED:
                return switcher.created(args);
            case MODIFIED_BY:
                return switcher.modifiedBy(args);
            case FOLDER_ID:
                return switcher.folderId(args);
            case TITLE:
                return switcher.title(args);
            case VERSION:
                return switcher.version(args);
            case CONTENT:
                return switcher.content(args);
            case ID:
                return switcher.id(args);
            case FILE_SIZE:
                return switcher.fileSize(args);
            case DESCRIPTION:
                return switcher.description(args);
            case URL:
                return switcher.url(args);
            case CREATED_BY:
                return switcher.createdBy(args);
            case FILENAME:
                return switcher.filename(args);
            case FILE_MIMETYPE:
                return switcher.fileMimetype(args);
            case SEQUENCE_NUMBER:
                return switcher.sequenceNumber(args);
            case CATEGORIES:
                return switcher.categories(args);
            case LOCKED_UNTIL:
                return switcher.lockedUntil(args);
            case FILE_MD5SUM:
                return switcher.fileMd5sum(args);
            case VERSION_COMMENT:
                return switcher.versionComment(args);
            case CURRENT_VERSION:
                return switcher.currentVersion(args);
            case COLOR_LABEL:
                return switcher.colorLabel(args);
            case LAST_MODIFIED_UTC:
                return switcher.lastModifiedUtc(args);
            case NUMBER_OF_VERSIONS:
                return switcher.numberOfVersions(args);
            case META:
                return switcher.meta(args);
            case OBJECT_PERMISSIONS:
                return switcher.objectPermissions(args);
            case SHAREABLE:
                return switcher.shareable(args);
            case ORIGIN:
                return switcher.origin(args);
            case CAPTURE_DATE:
                return switcher.captureDate(args);
            case GEOLOCATION:
                return switcher.geolocation(args);
            case WIDTH:
                return switcher.width(args);
            case HEIGHT:
                return switcher.height(args);
            case CAMERA_MAKE:
                return switcher.cameraMake(args);
            case CAMERA_MODEL:
                return switcher.cameraModel(args);
            case CAMERA_ISO_SPEED:
                return switcher.cameraIsoSpeed(args);
            case CAMERA_APERTURE:
                return switcher.cameraAperture(args);
            case CAMERA_EXPOSURE_TIME:
                return switcher.cameraExposureTime(args);
            case CAMERA_FOCAL_LENGTH:
                return switcher.cameraFocalLength(args);
            case MEDIA_META:
                return switcher.mediaMeta(args);
            case MEDIA_STATUS:
                return switcher.mediaStatus(args);
            case MEDIA_DATE:
                return switcher.mediaDate(args);
            case CREATED_FROM:
                return switcher.created_from(args);
            case MODIFIED_FROM:
                return switcher.modified_from(args);
            case UNIQUE_ID:
                return switcher.unique_id(args);
            default:
                throw new IllegalArgumentException("Don't know field: " + getName());
            }
        }

        /**
         * Applies specified handler to this field.
         *
         * @param handler The handler to invoke
         * @param args The optional arguments to pass to handler invocation
         * @return The resulting object
         */
        public Object handle(final FileFieldHandler handler, final Object... args) {
            return handler.handle(this, args);
        }

        public static List<Object> forAllFields(final FileFieldSwitcher switcher, Object... args) {
            Field[] allFields = values();
            List<Object> retval = new ArrayList<Object>(allFields.length);
            for (Field field : allFields) {
                retval.add(field.doSwitch(switcher, args));
            }
            return retval;
        }

        public static <T> T inject(FileFieldSwitcher switcher, T arg, Object... args) {
            final Object[] newArgs = new Object[args.length + 1];
            newArgs[0] = arg;
            System.arraycopy(args, 0, newArgs, 1, args.length);
            for (Field field : values()) {
                arg = (T) field.doSwitch(switcher, args);
            }
            return arg;
        }

        public static List<Object> forAllFields(FileFieldHandler handler, Object... args) {
            Field[] allFields = values();
            List<Object> retval = new ArrayList<Object>(allFields.length);
            for (Field field : allFields) {
                retval.add(field.handle(handler, args));
            }
            return retval;
        }

        public static <T> T inject(FileFieldHandler handler, T arg, Object... args) {
            Object[] newArgs;
            if (args == null || args.length == 0) {
                newArgs = new Object[] { arg };
            } else {
                newArgs = new Object[args.length + 1];
                newArgs[0] = arg;
                System.arraycopy(args, 0, newArgs, 1, args.length);
            }

            for (Field field : values()) {
                arg = (T) field.handle(handler, newArgs);
            }
            return arg;
        }

        /**
         * Adds needed date fields (last-modified and capture date) if media sort date is requested or is the sort criteria.
         *
         * @param fields The fields to enhance
         * @param optSortField The sort field to consider or <code>null</code>
         * @return The possibly enhanced fields
         */
        public static List<Field> addDateFieldsIfNeeded(List<Field> fields, Field optSortField) {
            if (Field.MEDIA_DATE != optSortField && !contains(fields, Field.MEDIA_DATE)) {
                return fields;
            }

            // Add last-modified and capture date (if absent)
            return enhanceBy(fields, Field.LAST_MODIFIED, Field.CAPTURE_DATE);
        }

        public static List<Field> enhanceBy(List<Field> fields, Field... toAdd) {
            if (null == toAdd || toAdd.length <= 0 || null == fields || fields.isEmpty()) {
                return fields;
            }

            List<Field> tmp = null;
            for (Field fieldToAdd : toAdd) {
                if (null == tmp) {
                    if (false == fields.contains(fieldToAdd)) {
                        tmp = new ArrayList<>(fields);
                        tmp.add(fieldToAdd);
                    }
                } else {
                    if (false == tmp.contains(fieldToAdd)) {
                        tmp.add(fieldToAdd);
                    }
                }
            }

            return null == tmp ? fields : tmp;
        }

        public static List<Field> reduceBy(List<Field> fields, Field... toDrop) {
            if (null == toDrop || toDrop.length <= 0 || null == fields || fields.isEmpty()) {
                return fields;
            }

            List<Field> tmp = null;
            for (Field fieldToDrop : toDrop) {
                if (null == tmp) {
                    if (false == fields.contains(fieldToDrop)) {
                        tmp = new ArrayList<>(fields);
                        tmp.remove(fieldToDrop);
                    }
                } else {
                    if (tmp.contains(fieldToDrop)) {
                        tmp.remove(fieldToDrop);
                    }
                }
            }

            return null == tmp ? fields : tmp;
        }

        /**
         * Checks if specified fields contains given field.
         *
         * @param metadata The fields
         * @param f The field
         * @return <code>true</code> if contained; otherwise <code>false</code>
         */
        public static boolean contains(List<Field> fields, Field f) {
            if (null == fields || fields.isEmpty() || null == f) {
                return false;
            }

            for (Field fld : fields) {
                if (f == fld) {
                    return true;
                }
            }
            return false;
        }

        private static final Map<String, Field> byName = new HashMap<String, Field>();
        static {
            for (final Field field : values()) {
                byName.put(field.getName(), field);
            }
        }

        private static final Map<Integer, Field> byNumber = new HashMap<Integer, Field>();

        static {
            for (final Field field : values()) {
                byNumber.put(Integer.valueOf(field.getNumber()), field);
            }
        }

        public static Field get(final String key) {
            if (key == null) {
                return null;
            }
            final Field field = byName.get(key);
            if (field != null) {
                return field;
            }
            try {
                final int number = Integer.parseInt(key);
                return byNumber.get(Integer.valueOf(number));
            } catch (@SuppressWarnings("unused") final NumberFormatException x) {
                return null;
            }
        }

        public static List<Field> get(final Collection<String> keys) {
            if (keys == null) {
                return Collections.emptyList();
            }
            final List<Field> retval = new ArrayList<Field>(keys.size());
            for (final String key : keys) {
                retval.add(get(key));
            }
            return retval;
        }

        public static Field get(final int number) {
            return byNumber.get(I(number));
        }

        public static List<Field> get(final int[] numbers) {
            final List<Field> fields = new ArrayList<Field>(numbers.length);
            for (int number : numbers) {
                Field field = byNumber.get(I(number));
                if (field != null) {
                    fields.add(field);
                }
            }
            return fields;
        }

    }
}
