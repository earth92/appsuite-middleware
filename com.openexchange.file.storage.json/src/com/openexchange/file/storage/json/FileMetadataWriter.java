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

package com.openexchange.file.storage.json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.customizer.file.AdditionalFileField;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.AbstractFileFieldHandler;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.FileFieldHandler;
import com.openexchange.file.storage.json.actions.files.AJAXInfostoreRequest;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIterators;

/**
 * {@link FileMetadataWriter}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class FileMetadataWriter {

    /**
     * The logger constant.
     */
    protected static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FileMetadataWriter.class);

    private final FileFieldCollector fieldCollector;

    /**
     * Initializes a new {@link FileMetadataWriter}.
     *
     * @param fieldCollector The collector for additional file fields, or <code>null</code> if not available
     */
    public FileMetadataWriter(FileFieldCollector fieldCollector) {
        super();
        this.fieldCollector = null == fieldCollector ? FileFieldCollector.EMPTY : fieldCollector;
    }

    /**
     * Serializes a single file with all metadata to JSON.
     *
     * @param request The underlying infostore request
     * @param file The file to write
     * @return A JSON object holding the serialized file
     */
    public JSONObject write(final AJAXInfostoreRequest request, final File file) {
        /*
         * serialize regular fields
         */
        JSONObject jsonObject = new JSONObject(32);
        jsonObject = File.Field.inject(getJsonHandler(file, new JsonFieldHandler(request, jsonObject)), jsonObject);
        /*
         * render additional fields if available
         */
        List<AdditionalFileField> additionalFields = fieldCollector.getFields();
        for (AdditionalFileField additionalField : additionalFields) {
            try {
                Object value = additionalField.getValue(file, request.getSession());
                jsonObject.put(additionalField.getColumnName(), additionalField.renderJSON(request.getRequestData(), value));
            } catch (JSONException e) {
                LOG.error("Error writing field: {}", additionalField.getColumnName(), e);
            }
        }
        return jsonObject;
    }

    /**
     * Serializes a single file with a specific field set.
     *
     * @param request The underlying infostore request
     * @param file The file to write
     * @param fields The basic file fields to write
     * @param additionalFields The column IDs of additional file fields to write; may be <code>null</code>
     * @return A JSON object holding the serialized file
     */
    public JSONObject writeSpecific(final AJAXInfostoreRequest request, final File file, final Field[] fields, final int[] additionalColumns) {
        /*
         * serialize regular fields
         */
        JSONObject jsonObject = new JSONObject(32);
        FileFieldHandler jsonHandler = getJsonHandler(file, new JsonFieldHandler(request, jsonObject));
        for (Field field : fields) {
            field.handle(jsonHandler, jsonObject);
        }

        /*
         * render additional fields if available
         */
        if (additionalColumns != null && additionalColumns.length > 0) {
            List<AdditionalFileField> additionalFields = fieldCollector.getFields(additionalColumns);
            for (AdditionalFileField additionalField : additionalFields) {
                try {
                    Object value = additionalField.getValue(file, request.getSession());
                    jsonObject.put(additionalField.getColumnName(), additionalField.renderJSON(request.getRequestData(), value));
                } catch (JSONException e) {
                    LOG.error("Error writing field: {}", additionalField.getColumnName(), e);
                }
            }
        }
        return jsonObject;
    }

    /**
     * Serializes all files from a search iterator to JSON.
     *
     * @param request The underlying infostore request
     * @param searchIterator A search iterator for the files to write
     * @return A JSON array holding all serialized files based on the requested columns
     */
    public JSONArray write(AJAXInfostoreRequest request, SearchIterator<File> searchIterator) throws OXException {
        int[] columns = request.getRequestedColumns();
        List<Field> fields = Field.get(columns);
        try {
            if (columns.length != fields.size()) {
                /*
                 * convert pre-loaded files to allow batch retrieval for additional fields
                 */
                return write(request, SearchIterators.asList(searchIterator));
            }
            /*
             * prefer to write iteratively if only regular file fields requested
             */
            JsonFieldHandler handler = new JsonFieldHandler(request);
            JSONArray filesArray = new JSONArray(32);
            while (searchIterator.hasNext()) {
                filesArray.put(writeArray(handler, searchIterator.next(), fields));
            }
            return filesArray;
        } finally {
            SearchIterators.close(searchIterator);
        }
    }

    /**
     * Serializes a list of files to JSON.
     *
     * @param request The underlying infostore request
     * @param files The files to write
     * @return A JSON array holding all serialized files based on the requested columns
     */
    public JSONArray write(AJAXInfostoreRequest request, List<File> files) throws OXException {
        /*
         * pre-load additional field values
         */
        int[] columns = request.getRequestedColumns();
        Map<Integer, List<Object>> additionalFieldValues = null;
        {
            List<AdditionalFileField> additionalFields = fieldCollector.getFields(columns);
            int size = additionalFields.size();
            if (0 < size) {
                additionalFieldValues = new HashMap<>(size);
                for (AdditionalFileField additionalField : additionalFields) {
                    List<Object> values = additionalField.getValues(files, request.getSession());
                    additionalFieldValues.put(Integer.valueOf(additionalField.getColumnID()), values);
                }
            }
        }
        /*
         * serialize each file to json
         */
        JsonFieldHandler handler = new JsonFieldHandler(request);
        JSONArray filesArray = new JSONArray(files.size());
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            JSONArray fileArray = new JSONArray(columns.length);
            for (int column : columns) {
                Field field = Field.get(column);
                if (null != field) {
                    fileArray.put(field.handle(handler, file));
                } else {
                    List<Object> fieldValues = null != additionalFieldValues ? additionalFieldValues.get(Integer.valueOf(column)) : null;
                    if (null != fieldValues) {
                        AdditionalFileField additionalFileField = fieldCollector.getField(column);
                        if (null != additionalFileField) {
                            fileArray.put(additionalFileField.renderJSON(request.getRequestData(), fieldValues.get(i)));
                        } else {
                            fileArray.put(JSONObject.NULL);
                        }
                    } else {
                        fileArray.put(JSONObject.NULL);
                    }
                }
            }
            filesArray.put(fileArray);
        }
        return filesArray;
    }

    JSONArray writeArray(JsonFieldHandler handler, File f, List<File.Field> columns) {
        JSONArray array = new JSONArray(columns.size());
        for (Field field : columns) {
            array.put(field.handle(handler, f));
        }
        return array;
    }

    private static FileFieldHandler getJsonHandler(final File file, final JsonFieldHandler fieldHandler) {
        return new AbstractFileFieldHandler() {
            @Override
            public Object handle(Field field, Object... args) {
                JSONObject jsonObject = get(0, JSONObject.class, args);
                try {
                    Object result = fieldHandler.handle(field, file);
                    if (result instanceof JsonFieldHandler.NamedValue) {
                        JsonFieldHandler.NamedValue<?> namedValue = JsonFieldHandler.NamedValue.class.cast(result);
                        jsonObject.putIfAbsent(namedValue.getName(), namedValue.getValue());
                    } else {
                        jsonObject.put(field.getName(), result);
                    }
                } catch (JSONException e) {
                    LOG.error("Error writing field: {}", field.getName(), e);
                }
                return jsonObject;
            }
        };
    }

}
