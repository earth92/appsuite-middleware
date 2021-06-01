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

package com.openexchange.microsoft.graph.onedrive.impl;

import static com.openexchange.java.Autoboxing.I;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.Quota;
import com.openexchange.file.storage.Quota.Type;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.microsoft.graph.api.MicrosoftGraphOneDriveAPI;
import com.openexchange.microsoft.graph.api.MicrosoftGraphQueryParameters;
import com.openexchange.microsoft.graph.api.MicrosoftGraphQueryParameters.Builder;
import com.openexchange.microsoft.graph.api.MicrosoftGraphQueryParameters.ParameterName;
import com.openexchange.microsoft.graph.api.exception.MicrosoftGraphAPIExceptionCodes;
import com.openexchange.microsoft.graph.onedrive.MicrosoftGraphDriveService;
import com.openexchange.microsoft.graph.onedrive.OneDriveFile;
import com.openexchange.microsoft.graph.onedrive.OneDriveFolder;
import com.openexchange.microsoft.graph.onedrive.exception.MicrosoftGraphDriveServiceExceptionCodes;
import com.openexchange.microsoft.graph.onedrive.parser.OneDriveFileParser;
import com.openexchange.microsoft.graph.onedrive.parser.OneDriveFolderParser;

/**
 * {@link MicrosoftGraphDriveServiceImpl}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.2
 */
public class MicrosoftGraphDriveServiceImpl implements MicrosoftGraphDriveService {

    private static final Logger LOG = LoggerFactory.getLogger(MicrosoftGraphDriveServiceImpl.class);

    /**
     * The file size limit for the one-shot upload operation
     */
    private static final int ONESHOT_LIMIT = 1024 * 1024 * 4;
    /**
     * Infostore's root folder id
     */
    private static final String ROOT_ID = "";

    private final MicrosoftGraphOneDriveAPI api;
    private final OneDriveFolderParser folderEntityParser;
    private final OneDriveFileParser fileEntityParser;

    /**
     * Initialises a new {@link MicrosoftGraphDriveServiceImpl}.
     *
     * @param api The {@link MicrosoftGraphOneDriveAPI}
     */
    public MicrosoftGraphDriveServiceImpl(MicrosoftGraphOneDriveAPI api) {
        super();
        this.api = api;
        this.folderEntityParser = new OneDriveFolderParser();
        this.fileEntityParser = new OneDriveFileParser();
    }

    @Override
    public String getRootFolderId(String accessToken) throws OXException {
        JSONObject root = api.getRoot(accessToken, new Builder().withParameter(ParameterName.SELECT, "id").build());
        return root.optString("id");
    }

    @Override
    public boolean existsFolder(String accessToken, String folderId) throws OXException {
        try {
            api.getFolder(accessToken, folderId, new Builder().withParameter(ParameterName.SELECT, "id").build());
            return true;
        } catch (OXException e) {
            if (MicrosoftGraphAPIExceptionCodes.ITEM_NOT_FOUND.equals(e)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public OneDriveFolder getRootFolder(int userId, String accessToken) throws OXException {
        return folderEntityParser.parseEntity(userId, hasSubFolders(accessToken, null), api.getRoot(accessToken));
    }

    @Override
    public OneDriveFolder getFolder(int userId, String accessToken, String folderId) throws OXException {
        return folderEntityParser.parseEntity(userId, hasSubFolders(accessToken, folderId), api.getFolder(accessToken, folderId));
    }

    @Override
    public List<OneDriveFolder> getSubFolders(int userId, String accessToken, String folderId) throws OXException {
        int top = 100;
        String skipToken = null;
        List<OneDriveFolder> list = new LinkedList<>();
        do {
            Builder paramBuilder = new Builder();
            paramBuilder.withParameter(ParameterName.TOP, Integer.toString(top)).withParameter(ParameterName.SKIPTOKEN, skipToken);

            JSONObject response = api.getChildren(accessToken, folderId, paramBuilder.build());
            skipToken = extractSkipToken(response);
            list.addAll(parseEntities(userId, accessToken, response.optJSONArray("value")));

        } while (Strings.isNotEmpty(skipToken));
        return list;
    }

    @Override
    public OneDriveFolder createFolder(int userId, String accessToken, String folderName, String parentId, boolean autorename) throws OXException {
        if (autorename) {
            return folderEntityParser.parseEntity(userId, false, api.createFolder(accessToken, folderName, parentId, autorename));
        }
        // The Microsoft Graph API does not return an error when the 'autorename' behaviour is not present and the folder exists;
        // hence we have to manually search for any folders with that name
        checkFolderExistence(accessToken, folderName, parentId);
        return folderEntityParser.parseEntity(userId, false, api.createFolder(accessToken, folderName, parentId, autorename));
    }

    @Override
    public void deleteFolder(String accessToken, String folderId) throws OXException {
        if (ROOT_ID.equals(folderId)) {
            throw MicrosoftGraphDriveServiceExceptionCodes.CANNOT_DELETE_ROOT_FOLDER.create();
        }
        api.deleteItem(accessToken, folderId);
    }

    @Override
    public void clearFolder(String accessToken, String folderId) throws OXException {
        JSONObject ids = api.getChildren(accessToken, folderId, new Builder().withParameter(ParameterName.SELECT, "id").build());
        JSONArray idsArray = ids.optJSONArray("value");
        if (idsArray == null || idsArray.isEmpty()) {
            return;
        }
        for (int index = 0; index < idsArray.length(); index++) {
            JSONObject item = idsArray.optJSONObject(index);
            if (item == null || item.isEmpty()) {
                continue;
            }
            String id = item.optString("id");
            if (Strings.isEmpty(id)) {
                continue;
            }
            api.deleteItem(accessToken, id);
        }
    }

    @Override
    public void renameFolder(String accessToken, String folderId, String newName) throws OXException {
        try {
            api.patchItem(accessToken, folderId, compileUpdateBody(null, newName));
        } catch (OXException e) {
            if (MicrosoftGraphAPIExceptionCodes.NAME_ALREADY_EXISTS.equals(e)) {
                throw MicrosoftGraphDriveServiceExceptionCodes.FOLDER_ALREADY_EXISTS.create(e, newName, folderId);
            }
            throw e;
        }
    }

    @Override
    public String moveFolder(String accessToken, String folderId, String parentId, String newName) throws OXException {
        try {
            JSONObject patchItem = api.patchItem(accessToken, folderId, compileUpdateBody(parentId, newName));
            return patchItem.optString("id");
        } catch (OXException e) {
            if (MicrosoftGraphAPIExceptionCodes.NAME_ALREADY_EXISTS.equals(e)) {
                throw MicrosoftGraphDriveServiceExceptionCodes.FOLDER_ALREADY_EXISTS.create(e, newName, parentId);
            }
            throw e;
        }
    }

    @Override
    public String moveFolder(String accessToken, String folderId, String parentId) throws OXException {
        try {
            return moveItem(accessToken, folderId, parentId);
        } catch (OXException e) {
            if (MicrosoftGraphAPIExceptionCodes.NAME_ALREADY_EXISTS.equals(e)) {
                throw MicrosoftGraphDriveServiceExceptionCodes.FOLDER_ALREADY_EXISTS.create(e, folderId, parentId);
            }
            throw e;
        }
    }

    /////////////////////////////////////// FILES //////////////////////////////////////

    @Override
    public List<OneDriveFile> getFiles(int userId, String accessToken, String folderId) throws OXException {
        int offset = 100;
        String skipToken = null;
        List<OneDriveFile> list = new LinkedList<>();
        do {
            MicrosoftGraphQueryParameters.Builder paramBuilder = new MicrosoftGraphQueryParameters.Builder();
            paramBuilder.withParameter(ParameterName.TOP, Integer.toString(offset)).withParameter(ParameterName.SKIPTOKEN, skipToken);

            JSONObject response = api.getChildren(accessToken, folderId, paramBuilder.build());
            skipToken = extractSkipToken(response);
            list.addAll(fileEntityParser.parseEntities(userId, response.optJSONArray("value")));
        } while (Strings.isNotEmpty(skipToken));
        return list;
    }

    @Override
    public OneDriveFile getFile(int userId, String accessToken, String itemId) throws OXException {
        return fileEntityParser.parseEntity(userId, api.getItem(accessToken, itemId));
    }

    @Override
    public List<OneDriveFile> getFiles(int userId, String accessToken, List<String> itemIds) throws OXException {
        List<OneDriveFile> files = new LinkedList<>();
        for (String itemId : itemIds) {
            try {
                files.add(fileEntityParser.parseEntity(userId, api.getItem(accessToken, itemId)));
            } catch (OXException e) {
                if (MicrosoftGraphAPIExceptionCodes.ITEM_NOT_FOUND.equals(e)) {
                    LOG.debug("Item with id '{}' for user with id '{}' was not found in OneDrive", itemId, I(userId));
                    continue;
                }
                throw e;
            }
        }
        return files;
    }

    @Override
    public void deleteFile(String accessToken, String fileId) throws OXException {
        api.deleteItem(accessToken, fileId);
    }

    @Override
    public String moveFile(String accessToken, String fileId, String parentId) throws OXException {
        return moveItem(accessToken, fileId, parentId);
    }

    @Override
    public String updateFile(String accessToken, File file, List<Field> modifiedFields, String parentId) throws OXException {
        JSONObject body = compileUpdateBody(file, modifiedFields, parentId);
        if (body.isEmpty()) {
            return file.getId();
        }
        JSONObject response = api.patchItem(accessToken, file.getId(), body);
        return response.optString("id");
    }

    @Override
    public String copyFile(String accessToken, String itemId, File file, List<Field> modifiedFields, String parentId) throws OXException {
        return api.copyItem(accessToken, itemId, compileUpdateBody(file, modifiedFields, parentId));
    }

    @Override
    public String copyFile(String accessToken, String itemId, String parentId) throws OXException {
        return api.copyItem(accessToken, itemId, compileUpdateBody(parentId));
    }

    @Override
    public InputStream getFile(String accessToken, String fileId) throws OXException {
        return api.getContent(accessToken, fileId);
    }

    @Override
    public InputStream getThumbnail(String accessToken, String itemId) throws OXException {
        return api.getThumbnailContent(accessToken, itemId);
    }

    @Override
    public Quota getQuota(String accessToken) throws OXException {
        JSONObject drive = api.getDrive(accessToken, new Builder().withParameter(ParameterName.SELECT, "quota").build());
        JSONObject quota = drive.optJSONObject("quota");
        if (quota == null || quota.isEmpty()) {
            // Should never happen, but just in case...
            return new Quota(-1, -1, Type.STORAGE);
        }
        return new Quota(quota.optInt("total"), quota.optInt("used"), Type.STORAGE);
    }

    @Override
    public List<OneDriveFile> searchFiles(int userId, String accessToken, String query, String folderId, boolean includeSubfolders) throws OXException {
        int top = 100;
        String skipToken = null;
        List<OneDriveFile> list = new LinkedList<>();
        do {
            Builder b = new MicrosoftGraphQueryParameters.Builder();
            b.withParameter(ParameterName.TOP, Integer.toString(top)).withParameter(ParameterName.SKIPTOKEN, skipToken);

            JSONObject result = api.searchItems(accessToken, query, b.build());
            skipToken = extractSkipToken(result);

            JSONArray array = result.optJSONArray("value");
            if (array == null || array.isEmpty()) {
                return list;
            }

            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null || item.isEmpty()) {
                    continue;
                }
                if (!item.hasAndNotNull("file")) {
                    continue;
                }

                JSONObject parentRef = item.optJSONObject("parentReference");
                if (parentRef == null || parentRef.isEmpty()) {
                    continue;
                }
                if (folderId == null || includeSubfolders || folderId.equals(parentRef.optString("id"))) {
                    list.add(fileEntityParser.parseEntity(userId, item));
                }
            }
        } while (Strings.isNotEmpty(skipToken));
        return list;
    }

    @SuppressWarnings("resource")
    @Override
    public String upload(String accessToken, File file, InputStream inputStream) throws OXException {
        JSONObject responseBody;
        long contentLength = file.getFileSize();
        InputStream is = inputStream;
        if (contentLength <= 0) {
            ThresholdFileHolder sink = null;
            try {
                sink = new ThresholdFileHolder();
                sink.write(inputStream);
                contentLength = sink.getLength();
                is = sink.getClosingStream();
                sink = null; // Intentional: Avoid premature closing
            } finally {
                Streams.close(sink);
            }
        }

        if (contentLength > ONESHOT_LIMIT) {
            responseBody = api.streamingUpload(accessToken, file.getFolderId(), file.getId(), file.getFileName(), contentLength, is);
        } else {
            responseBody = api.oneshotUpload(accessToken, file.getFolderId(), file.getId(), file.getFileName(), file.getFileMIMEType(), is);
        }
        return responseBody.optString("id");
    }

    //////////////////////////////////////// HELPERS /////////////////////////////////////

    /**
     * Moves the item with the specified identifier under the parent folder with the specified identifier
     *
     * @param accessToken The oauth access token
     * @param itemId The item's identifier
     * @param parentId The new parent's identifier
     * @return The new identifier of the item
     * @throws OXException if an error is occurred
     */
    private String moveItem(String accessToken, String itemId, String parentId) throws OXException {
        JSONObject patchItem = api.patchItem(accessToken, itemId, compileUpdateBody(parentId));
        return patchItem.optString("id");
    }

    /**
     * Checks whether a folder with the specified name already exists as a sub-folder of the folder with the specified identifier
     *
     * @param accessToken The oauth access token
     * @param folderName The folder name to check
     * @param parentId The parent folder's identifier
     * @throws OXException if an error is occurred
     */
    private void checkFolderExistence(String accessToken, String folderName, String parentId) throws OXException {
        Builder b = new Builder();
        b.withParameter(ParameterName.SELECT, "id,name,parentReference").withParameter(ParameterName.FILTER, "folder ne null");
        JSONObject children = api.searchItems(accessToken, folderName, b.build());
        JSONArray namesArray = children.optJSONArray("value");
        if (namesArray == null || namesArray.isEmpty()) {
            return;
        }
        for (int index = 0; index < namesArray.length(); index++) {
            JSONObject candidate = namesArray.optJSONObject(index);
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            JSONObject parentRef = candidate.optJSONObject("parentReference");
            if (parentRef == null || parentRef.isEmpty()) {
                continue;
            }
            if (parentId.equals(parentRef.optString("id"))) {
                throw MicrosoftGraphDriveServiceExceptionCodes.FOLDER_ALREADY_EXISTS.create(folderName, parentId);
            }
        }
    }

    /**
     * Checks whether the folder with the specified identifier has any sub-folders
     *
     * @param accessToken The oauth access token
     * @param folderId The folder identifier
     * @return <code>true</code> if the folder has at least one sub-folder;<code>false</code> otherwise
     * @throws OXException if an error is occurred
     */
    private boolean hasSubFolders(String accessToken, String folderId) throws OXException {
        MicrosoftGraphQueryParameters params = new Builder().withParameter(ParameterName.SELECT, "folder").build();
        JSONObject j = Strings.isEmpty(folderId) ? api.getRootChildren(accessToken, params) : api.getChildren(accessToken, folderId, params);
        JSONArray entities = j.optJSONArray("value");
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        for (int index = 0; index < entities.length(); index++) {
            JSONObject entity = entities.optJSONObject(index);
            if (entity == null || entity.isEmpty()) {
                continue;
            }
            if (entity.hasAndNotNull("folder")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the specified {@link JSONArray} of entities in to a {@link List}
     * of {@link OneDriveFolder}s
     *
     * @param userId the user identifier
     * @param entities The {@link JSONArray} with the entities
     * @return A {@link List} with the {@link OneDriveFolder}s
     */
    private List<OneDriveFolder> parseEntities(int userId, String accessToken, JSONArray entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<OneDriveFolder> folders = new LinkedList<>();
        for (int index = 0; index < entities.length(); index++) {
            JSONObject entity = entities.optJSONObject(index);
            String folderId = entity.optString("id");
            try {
                folders.add(folderEntityParser.parseEntity(userId, hasSubFolders(accessToken, folderId), entity));
            } catch (OXException e) {
                LOG.debug("{}", e);
            }
        }
        return folders;
    }

    /**
     * Checks and extracts a 'skipToken' if available.
     *
     * @param response The {@link JSONObject} response body
     * @return the skipToken or <code>null</code> if none available.
     */
    private String extractSkipToken(JSONObject response) {
        String nextLink = response.optString("@odata.nextLink");
        if (Strings.isEmpty(nextLink)) {
            return null;
        }
        int indexOfKey = nextLink.indexOf("skiptoken");
        if (indexOfKey < 0) {
            return null;
        }
        int indexOfValue = nextLink.indexOf('=', indexOfKey);
        if (indexOfValue < 0) {
            return null;
        }
        int indexOfLast = nextLink.indexOf('=', indexOfValue);
        return ((indexOfLast < 0)) ? nextLink.substring(indexOfValue) : nextLink.substring(indexOfValue, indexOfLast);
    }

    /**
     * Compiles an update body with the specified parent identifier and the specified modified fields.
     * The following fields are considered:
     * <ul>
     * <li>{@link Field#FILENAME}</li>
     * <li>{@link Field#DESCRIPTION}</li>
     * </ul>
     *
     * @param file The {@link File} containing the item's metadata
     * @param modifiedFields A {@link List} with the modified fields
     * @param description The description of the new item
     * @return The update body
     * @throws OXException if a JSON error is occurred
     * @see <a href="https://developer.microsoft.com/en-us/graph/docs/api-reference/v1.0/api/driveitem_update">Update Item</a>
     * @see <a href="https://developer.microsoft.com/en-us/graph/docs/api-reference/v1.0/api/driveitem_move">Move Item</a>
     */
    private JSONObject compileUpdateBody(File file, List<Field> modifiedFields, String parentId) throws OXException {
        try {
            JSONObject body = new JSONObject();
            String newName = null;
            if (modifiedFields == null || modifiedFields.contains(Field.FILENAME)) {
                newName = file.getFileName();
            }
            String description = null;
            if (modifiedFields == null || modifiedFields.contains(Field.DESCRIPTION)) {
                description = file.getDescription();
            }
            if (parentId != null) {
                JSONObject parentRef = new JSONObject();
                parentRef.put("id", parentId);
                body.put("parentReference", parentRef);
            }
            return compileUpdateBody(parentId, newName, description);
        } catch (JSONException e) {
            throw MicrosoftGraphDriveServiceExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Compiles an update body with the specified parent identifier. The parent identifier
     * designates the destination folder in a move operation.
     *
     * @param parentId The parent identifier
     * @return The update body
     * @throws OXException if a JSON error is occurred
     * @see <a href="https://developer.microsoft.com/en-us/graph/docs/api-reference/v1.0/api/driveitem_move">Move Item</a>
     */
    private JSONObject compileUpdateBody(String parentId) throws OXException {
        return compileUpdateBody(parentId, null, null);
    }

    /**
     * Compiles an update body with the specified parent identifier and the specified new name.
     * The parent identifier designates the destination folder in a move operation. The new name
     * designates the new name of the item (file or folder) in a rename and move operation.
     *
     * @param parentId The parent identifier
     * @param newName The new name of the folder
     * @return The update body
     * @throws OXException if a JSON error is occurred
     * @see <a href="https://developer.microsoft.com/en-us/graph/docs/api-reference/v1.0/api/driveitem_update">Update Item</a>
     * @see <a href="https://developer.microsoft.com/en-us/graph/docs/api-reference/v1.0/api/driveitem_move">Move Item</a>
     */
    private JSONObject compileUpdateBody(String parentId, String newName) throws OXException {
        return compileUpdateBody(parentId, newName, null);
    }

    /**
     * Compiles an update body with the specified parent identifier and the specified new name.
     * The parent identifier designates the destination folder in a move operation. The new name
     * designates the new name of the item (file or folder) in a rename and move operation.
     *
     * @param parentId The parent identifier
     * @param newName The new name of the item
     * @param description The description of the new item
     * @return The update body
     * @throws OXException if a JSON error is occurred
     * @see <a href="https://developer.microsoft.com/en-us/graph/docs/api-reference/v1.0/api/driveitem_update">Update Item</a>
     * @see <a href="https://developer.microsoft.com/en-us/graph/docs/api-reference/v1.0/api/driveitem_move">Move Item</a>
     */
    private JSONObject compileUpdateBody(String parentId, String newName, String description) throws OXException {
        try {
            JSONObject body = new JSONObject();
            if (parentId != null) {
                JSONObject parentRef = new JSONObject();
                parentRef.put("id", parentId);
                body.put("parentReference", parentRef);
            }
            if (Strings.isNotEmpty(newName)) {
                body.put("name", newName);
            }
            if (Strings.isNotEmpty(description)) {
                body.put("description", description);
            }
            return body;
        } catch (JSONException e) {
            throw MicrosoftGraphDriveServiceExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }
}
