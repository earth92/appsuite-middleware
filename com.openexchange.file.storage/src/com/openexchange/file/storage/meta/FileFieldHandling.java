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

package com.openexchange.file.storage.meta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.FileFieldHandler;
import com.openexchange.file.storage.FileFieldSwitcher;

/**
 * {@link FileFieldHandling}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class FileFieldHandling {

    public static FileFieldHandler toHandler(final FileFieldSwitcher switcher) {
        return new SwitcherHandler(switcher);
    }

    public static FileFieldSwitcher toSwitcher(final FileFieldHandler handler) {
        return new HandlerSwitcher(handler);
    }

    public static void copy(final File orig, final File dest, final File.Field... fields) {
        final FileFieldGet get = new FileFieldGet();
        final FileFieldSet set = new FileFieldSet();

        for (final Field field : fields) {
            field.doSwitch(set, dest, field.doSwitch(get, orig));
        }
    }

    public static void copy(final File orig, final File dest) {
        copy(orig, dest, Field.values());
    }

    public static File dup(final File orig) {
        final DefaultFile copy = new DefaultFile();
        copy(orig, copy);
        return copy;
    }

    public static Map<String, Object> toMap(final File file, final File.Field... fields) {
        final Map<String, Object> map = new HashMap<String, Object>();
        final FileFieldGet get = new FileFieldGet();

        for (final Field field : fields) {
            final Object value = field.doSwitch(get, file);
            map.put(field.getName(), value);
        }

        return map;
    }

    public static Map<String, Object> toMap(final File file) {
        return toMap(file, Field.values());
    }

    public static String toString(final File file) {
        return toMap(file).toString();
    }

    public static void fromMap(final Map<String, Object> map, final File file, final List<Field> foundFields) {
        final FileFieldSet set = new FileFieldSet();

        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            final Field field = Field.get(entry.getKey());
            if (null != field) {
                foundFields.add(field);
                field.doSwitch(set, file, entry.getValue());
            }
        }
    }

    public static void fromMap(final Map<String, Object> map, final File file) {
        fromMap(map, file, new ArrayList<Field>(map.size()));
    }

    public static List<Object> toList(final File file, final File.Field... fields) {
        final FileFieldGet get = new FileFieldGet();
        final List<Object> list = new ArrayList<Object>(fields.length);
        for (final Field field : fields) {
            list.add(field.doSwitch(get, file));
        }
        return list;
    }

    private static class SwitcherHandler implements FileFieldHandler {

        private final FileFieldSwitcher switcher;

        public SwitcherHandler(final FileFieldSwitcher switcher) {
            this.switcher = switcher;
        }

        @Override
        public Object handle(final Field field, final Object... args) {
            return field.doSwitch(switcher, args);
        }
    }

    private static class HandlerSwitcher implements FileFieldSwitcher {

        private final FileFieldHandler handler;

        public HandlerSwitcher(final FileFieldHandler handler) {
            this.handler = handler;
        }

        @Override
        public Object categories(final Object... args) {
            return handler.handle(Field.CATEGORIES, args);
        }

        @Override
        public Object colorLabel(final Object... args) {
            return handler.handle(Field.COLOR_LABEL, args);
        }

        @Override
        public Object content(final Object... args) {
            return handler.handle(Field.CONTENT, args);
        }

        @Override
        public Object created(final Object... args) {
            return handler.handle(Field.CREATED, args);
        }

        @Override
        public Object createdBy(final Object... args) {
            return handler.handle(Field.CREATED_BY, args);
        }

        @Override
        public Object currentVersion(final Object... args) {
            return handler.handle(Field.CURRENT_VERSION, args);
        }

        @Override
        public Object description(final Object... args) {
            return handler.handle(Field.DESCRIPTION, args);
        }

        @Override
        public Object fileMd5sum(final Object... args) {
            return handler.handle(Field.FILE_MD5SUM, args);
        }

        @Override
        public Object fileMimetype(final Object... args) {
            return handler.handle(Field.FILE_MIMETYPE, args);
        }

        @Override
        public Object fileSize(final Object... args) {
            return handler.handle(Field.FILE_SIZE, args);
        }

        @Override
        public Object filename(final Object... args) {
            return handler.handle(Field.FILENAME, args);
        }

        @Override
        public Object folderId(final Object... args) {
            return handler.handle(Field.FOLDER_ID, args);
        }

        @Override
        public Object id(final Object... args) {
            return handler.handle(Field.ID, args);
        }

        @Override
        public Object lastModified(final Object... args) {
            return handler.handle(Field.LAST_MODIFIED, args);
        }

        @Override
        public Object lastModifiedUtc(final Object... args) {
            return handler.handle(Field.LAST_MODIFIED_UTC, args);
        }

        @Override
        public Object lockedUntil(final Object... args) {
            return handler.handle(Field.LOCKED_UNTIL, args);
        }

        @Override
        public Object modifiedBy(final Object... args) {
            return handler.handle(Field.MODIFIED_BY, args);
        }

        @Override
        public Object numberOfVersions(final Object... args) {
            return handler.handle(Field.NUMBER_OF_VERSIONS, args);
        }

        @Override
        public Object sequenceNumber(final Object... args) {
            return handler.handle(Field.SEQUENCE_NUMBER, args);
        }

        @Override
        public Object title(final Object... args) {
            return handler.handle(Field.TITLE, args);
        }

        @Override
        public Object url(final Object... args) {
            return handler.handle(Field.URL, args);
        }

        @Override
        public Object version(final Object... args) {
            return handler.handle(Field.VERSION, args);
        }

        @Override
        public Object versionComment(final Object... args) {
            return handler.handle(Field.VERSION_COMMENT, args);
        }

        @Override
        public Object meta(Object... args) {
            return handler.handle(Field.META, args);
        }

        @Override
        public Object objectPermissions(Object... args) {
            return handler.handle(Field.OBJECT_PERMISSIONS, args);
        }

        @Override
        public Object shareable(Object... args) {
            return handler.handle(Field.SHAREABLE, args);
        }

        @Override
        public Object origin(Object... args) {
            return handler.handle(Field.ORIGIN, args);
        }

        @Override
        public Object captureDate(Object... args) {
            return handler.handle(Field.CAPTURE_DATE, args);
        }

        @Override
        public Object geolocation(Object... args) {
            return handler.handle(Field.GEOLOCATION, args);
        }

        @Override
        public Object width(Object... args) {
            return handler.handle(Field.WIDTH, args);
        }

        @Override
        public Object height(Object... args) {
            return handler.handle(Field.HEIGHT, args);
        }

        @Override
        public Object cameraMake(Object... args) {
            return handler.handle(Field.CAMERA_MAKE, args);
        }

        @Override
        public Object cameraModel(Object... args) {
            return handler.handle(Field.CAMERA_MODEL, args);
        }

        @Override
        public Object cameraIsoSpeed(Object... args) {
            return handler.handle(Field.CAMERA_ISO_SPEED, args);
        }

        @Override
        public Object cameraAperture(Object... args) {
            return handler.handle(Field.CAMERA_APERTURE, args);
        }

        @Override
        public Object cameraExposureTime(Object... args) {
            return handler.handle(Field.CAMERA_EXPOSURE_TIME, args);
        }

        @Override
        public Object cameraFocalLength(Object... args) {
            return handler.handle(Field.CAMERA_FOCAL_LENGTH, args);
        }

        @Override
        public Object mediaMeta(Object... args) {
            return handler.handle(Field.MEDIA_META, args);
        }

        @Override
        public Object mediaStatus(Object[] args) {
            return handler.handle(Field.MEDIA_STATUS, args);
        }

        @Override
        public Object mediaDate(Object[] args) {
            return handler.handle(Field.MEDIA_DATE, args);
        }

        @Override
        public Object created_from(Object...args) {
            return handler.handle(Field.CREATED_FROM, args);
        }

        @Override
        public Object modified_from(Object... args) {
            return handler.handle(Field.MODIFIED_FROM, args);
        }

        @Override
        public Object unique_id(Object[] args) {
            return handler.handle(Field.UNIQUE_ID, args);
        }

    }
}
