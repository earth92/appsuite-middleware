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

package com.openexchange.groupware.upload.impl;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.openexchange.groupware.upload.Upload;
import com.openexchange.groupware.upload.UploadFile;

/**
 * Just a plain class that wraps information about an upload e.g. files, form fields, content type, size, etc.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class UploadEvent implements Upload {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(UploadEvent.class);

    /*-
     * ------------ Members ------------
     */

    private final Map<String, List<UploadFile>> uploadFilesByFieldName;

    private final Map<String, String> formFields;

    private String action;

    private final Map<String, Object> parameters;

    /**
     * Initializes a new {@link UploadEvent}.
     */
    public UploadEvent() {
        super();
        uploadFilesByFieldName = new LinkedHashMap<String, List<UploadFile>>();
        formFields = new HashMap<String, String>();
        parameters = new HashMap<String, Object>();
    }

    /**
     * Adds given upload file.
     *
     * @param uploadFile The upload file to add.
     */
    public final void addUploadFile(UploadFile uploadFile) {
        if (null != uploadFile) {
            String fieldName = uploadFile.getFieldName();
            List<UploadFile> list = uploadFilesByFieldName.get(fieldName);
            if (null == list) {
                list = new LinkedList<UploadFile>();
                uploadFilesByFieldName.put(fieldName, list);
            }
            list.add(uploadFile);
        }
    }

    /**
     * Gets the (first) upload file associated with specified field name.
     *
     * @param fieldName The field name.
     * @return The upload file associated with specified field name or <code>null</code>
     */
    public final UploadFile getUploadFileByFieldName(String fieldName) {
        List<UploadFile> list = uploadFilesByFieldName.get(fieldName);
        return null == list || list.isEmpty() ? null : list.get(0);
    }

    /**
     * Gets the upload files associated with specified field name.
     *
     * @param fieldName The field name.
     * @return The upload files associated with specified field name or <code>null</code>
     */
    public final List<UploadFile> getUploadFilesByFieldName(String fieldName) {
        List<UploadFile> list = uploadFilesByFieldName.get(fieldName);
        return null == list ? null : Collections.unmodifiableList(list);
    }

    /**
     * Gets the upload files associated with specified file name.
     *
     * @param fileName The file name.
     * @return The upload files associated with specified file name.
     */
    public final List<UploadFile> getUploadFileByFileName(String fileName) {
        if (null == fileName) {
            return Collections.emptyList();
        }
        List<UploadFile> ret = new LinkedList<UploadFile>();
        for (List<UploadFile> ufs : uploadFilesByFieldName.values()) {
            for (UploadFile uf : ufs) {
                if (fileName.equals(uf.getFileName())) {
                    ret.add(uf);
                }
            }
        }
        return ret;
    }

    /**
     * Clears all upload files.
     */
    public final void clearUploadFiles() {
        cleanUp();
    }

    @Override
    public final int getNumberOfUploadFiles() {
        return createList().size();
    }

    @Override
    public final Iterator<UploadFile> getUploadFilesIterator() {
        return createList().iterator();
    }

    /**
     * Gets a list containing the upload files.
     *
     * @return A list containing the upload files.
     */
    public final List<UploadFile> getUploadFiles() {
        return createList();
    }

    private final List<UploadFile> createList() {
        if (uploadFilesByFieldName.isEmpty()) {
            return Collections.emptyList();
        }
        List<UploadFile> ret = new LinkedList<UploadFile>();
        for (List<UploadFile> ufs : uploadFilesByFieldName.values()) {
            ret.addAll(ufs);
        }
        return ret;
    }

    /**
     * Adds a name-value-pair of a form field.
     *
     * @param fieldName The field's name.
     * @param fieldValue The field's value.
     */
    public final void addFormField(String fieldName, String fieldValue) {
        formFields.put(fieldName, fieldValue);
    }

    /**
     * Gets the number of form fields.
     *
     * @return The number of form fields
     */
    public int getNumberOfFormFields() {
        return formFields.size();
    }

    /**
     * Removes the form field whose name equals specified field name.
     *
     * @param fieldName The field name.
     * @return The removed form field's value or <code>null</code>.
     */
    public final String removeFormField(String fieldName) {
        return formFields.remove(fieldName);
    }

    /**
     * Gets the form field whose name equals specified field name.
     *
     * @param fieldName The field name.
     * @return The value of associated form field or <code>null</code>.
     */
    public final String getFormField(String fieldName) {
        return formFields.get(fieldName);
    }

    /**
     * Clears all form fields.
     */
    public final void clearFormFields() {
        formFields.clear();
    }

    /**
     * Gets an iterator for form fields.
     *
     * @return An iterator for form fields.
     */
    public final Iterator<String> getFormFieldNames() {
        return formFields.keySet().iterator();
    }

    /**
     * Gets this upload event's action string.
     *
     * @return The action string.
     */
    public final String getAction() {
        return action;
    }

    /**
     * Sets this upload event's action string.
     *
     * @param action The action string.
     */
    public final void setAction(String action) {
        this.action = action;
    }

    /**
     * Gets the parameter associated with specified name.
     *
     * @param name The parameter's name.
     * @return The parameter associated with specified name or <code>null</code> .
     */
    public final Object getParameter(String name) {
        return name == null ? null : parameters.get(name);
    }

    /**
     * Associates specified parameter name with given parameter value.
     *
     * @param name The parameter name.
     * @param value The parameter value.
     */
    public final void setParameter(String name, Object value) {
        if (name != null && value != null) {
            parameters.put(name, value);
        }
    }

    /**
     * Removes the parameter associated with specified name.
     *
     * @param name The parameter's name.
     */
    public final void removeParameter(String name) {
        if (name != null) {
            parameters.remove(name);
        }
    }

    /**
     * Deletes all created temporary files created through this <code>UploadEvent</code> instance and clears upload files.
     */
    public final void cleanUp() {
        for (List<UploadFile> uploadFiles : uploadFilesByFieldName.values()) {
            for (UploadFile uploadFile : uploadFiles) {
                File tmpFile = uploadFile.getTmpFile();
                if (null != tmpFile && tmpFile.exists()) {
                    try {
                        if (!tmpFile.delete()) {
                            LOG.error("Temporary upload file could not be deleted: {}", tmpFile.getName());
                        }
                    } catch (Exception e) {
                        LOG.error("Temporary upload file could not be deleted: {}", tmpFile.getName(), e);
                    }
                }
            }
        }
        uploadFilesByFieldName.clear();
        LOG.debug("Upload event cleaned-up. All temporary stored files deleted.");
    }

    /**
     * Strips off heading path information from specified file path by looking for last occurrence of a common file separator character like
     * <code>'/'</code> or <code>'\'</code> to only return sole file name.
     *
     * @param filePath The file path
     * @return The sole file name.
     */
    public static final String getFileName(String filePath) {
        String retval = filePath;
        int pos;
        if ((pos = retval.lastIndexOf('\\')) > -1) {
            retval = retval.substring(pos + 1);
        } else if ((pos = retval.lastIndexOf('/')) > -1) {
            retval = retval.substring(pos + 1);
        }
        return retval;
    }

}
