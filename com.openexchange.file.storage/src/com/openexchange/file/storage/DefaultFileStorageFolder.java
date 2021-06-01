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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.openexchange.groupware.EntityInfo;

/**
 * {@link DefaultFileStorageFolder} - The default file storage folder providing setter methods.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class DefaultFileStorageFolder implements FileStorageFolder, SetterAwareFileStorageFolder {

    protected Set<String> capabilities;

    protected String id;

    protected int fileCount;

    protected String name;

    protected FileStoragePermission ownPermission;

    protected String parentId;

    protected List<FileStoragePermission> permissions;

    protected boolean subfolders;

    protected boolean subscribedSubfolders;

    protected boolean defaultFolder;

    protected boolean holdsFolders;

    protected boolean holdsFiles;

    protected boolean rootFolder;

    protected boolean b_rootFolder;

    protected boolean subscribed;

    protected boolean b_subscribed;

    protected boolean b_holdsFolders;

    protected boolean b_holdsFiles;

    protected boolean b_defaultFolder;

    protected boolean b_subscribedSubfolders;

    protected boolean b_subfolders;

    protected boolean b_createdFrom;

    protected boolean b_modifiedFrom;

    protected boolean exists;

    protected Date creationDate;

    protected Date lastModifiedDate;

    protected Map<String, Object> properties;

    protected int createdBy;

    protected int modifiedBy;

    protected EntityInfo createdFrom;

    protected EntityInfo modifiedFrom;

    protected Map<String, Object> meta;

    /**
     * Initializes a new {@link DefaultFileStorageFolder}.
     */
    public DefaultFileStorageFolder() {
        super();
        fileCount = -1;
        properties = Collections.emptyMap();
        createdBy = -1;
        modifiedBy = -1;
    }

    /**
     * Gets the capabilities of this folder; e.g <code>"QUOTA"</code>, <code>"PERMISSIONS"</code>, etc.
     *
     * @return The list of capabilities or <code>null</code> if not set
     */
    @Override
    public Set<String> getCapabilities() {
        if (null == capabilities) {
            return null;
        }
        return new HashSet<String>(capabilities);
    }

    /**
     * Sets the capabilities.
     *
     * @param capabilities The capabilities
     */
    public void setCapabilities(final Set<String> capabilities) {
        if (capabilities == null) {
            this.capabilities = null;
        } else {
            this.capabilities = new HashSet<String>(capabilities);
        }
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Sets the folder identifier.
     *
     * @param id The folder identifier
     */
    public void setId(final String id) {
        this.id = id;
    }

    @Override
    public int getFileCount() {
        return fileCount;
    }

    /**
     * Sets the file count.
     *
     * @param fileCount The file count
     */
    public void setFileCount(final int fileCount) {
        this.fileCount = fileCount;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name The name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getLocalizedName(Locale locale) {
        return name;
    }

    @Override
    public FileStoragePermission getOwnPermission() {
        return ownPermission;
    }

    /**
     * Sets the own permission.
     *
     * @param ownPermission The own permission
     */
    public void setOwnPermission(final FileStoragePermission ownPermission) {
        this.ownPermission = ownPermission;
    }

    @Override
    public String getParentId() {
        return parentId;
    }

    /**
     * Sets the parent identifier.
     *
     * @param parentId The parent identifier
     */
    public void setParentId(final String parentId) {
        this.parentId = parentId;
    }

    @Override
    public List<FileStoragePermission> getPermissions() {
        if (null == permissions) {
            return null;
        }
        return new ArrayList<FileStoragePermission>(permissions);
    }

    /**
     * Sets the permissions
     *
     * @param permissions The permissions
     */
    public void setPermissions(final List<FileStoragePermission> permissions) {
        if (permissions == null) {
            this.permissions = null;
        } else {
            this.permissions = new ArrayList<FileStoragePermission>(permissions);
        }
    }

    /**
     * Adds given permission.
     *
     * @param permission The permission
     */
    public void addPermission(final FileStoragePermission permission) {
        if (null == permissions) {
            permissions = new ArrayList<FileStoragePermission>(4);
        }
        permissions.add(permission);
    }

    @Override
    public boolean hasSubfolders() {
        return subfolders;
    }

    /**
     * Sets whether this folder has subfolders.
     *
     * @param subfolders <code>true</code> if this folder has subfolders; otherwise <code>false</code>
     */
    public void setSubfolders(final boolean subfolders) {
        this.subfolders = subfolders;
        b_subfolders = true;
    }

    /**
     * Indicates whether this folder has the has-subfolders flag set
     *
     * @return <code>true</code> if this folder has the has-subfolders flag set; otherwise <code>false</code>
     */
    public boolean containsSubfolders() {
        return b_subfolders;
    }

    /**
     * Removes whether this folder has subfolders.
     */
    public void removeSubfolders() {
        subfolders = false;
        b_subfolders = false;
    }

    @Override
    public boolean hasSubscribedSubfolders() {
        return subscribedSubfolders;
    }

    /**
     * Sets whether this folder has subscribed subfolders.
     *
     * @param subscribedSubfolders <code>true</code> if this folder has subscribed subfolders; otherwise <code>false</code>
     */
    public void setSubscribedSubfolders(final boolean subscribedSubfolders) {
        this.subscribedSubfolders = subscribedSubfolders;
        b_subscribedSubfolders = true;
    }

    /**
     * Indicates whether this folder has the has-subscribed-subfolders flag set
     *
     * @return <code>true</code> if this folder has the has-subscribed-subfolders flag set; otherwise <code>false</code>
     */
    public boolean containsSubscribedSubfolders() {
        return b_subscribedSubfolders;
    }

    /**
     * Removes whether this folder has subscribed subfolders.
     */
    public void removeSubscribedSubfolders() {
        subscribedSubfolders = false;
        b_subscribedSubfolders = false;
    }

    @Override
    public boolean isDefaultFolder() {
        return defaultFolder;
    }

    /**
     * Sets whether this folder is a default folder.
     *
     * @param defaultFolder <code>true</code> if this folder is a default folder; otherwise <code>false</code>
     */
    public void setDefaultFolder(final boolean defaultFolder) {
        this.defaultFolder = defaultFolder;
        b_defaultFolder = true;
    }

    /**
     * Indicates whether this folder has the default-folder flag set
     *
     * @return <code>true</code> if this folder has the default-folder flag set; otherwise <code>false</code>
     */
    public boolean containsDefaultFolder() {
        return b_defaultFolder;
    }

    /**
     * Removes whether this folder is a default folder.
     */
    public void removeDefaultFolder() {
        defaultFolder = false;
        b_defaultFolder = false;
    }

    @Override
    public boolean isHoldsFolders() {
        return holdsFolders;
    }

    /**
     * Sets whether this folder has the capability to hold subfolders.
     *
     * @param holdsFolders <code>true</code> if this folder has the capability to hold subfolders; otherwise <code>false</code>
     */
    public void setHoldsFolders(final boolean holdsFolders) {
        this.holdsFolders = holdsFolders;
        b_holdsFolders = true;
    }

    /**
     * Indicates whether this folder has the holds-folders flag set
     *
     * @return <code>true</code> if this folder has the holds-folders flag set; otherwise <code>false</code>
     */
    public boolean containsHoldsFolders() {
        return b_holdsFolders;
    }

    /**
     * Removes whether this folder holds folders.
     */
    public void removeHoldsFolders() {
        holdsFolders = false;
        b_holdsFolders = false;
    }

    @Override
    public boolean isHoldsFiles() {
        return holdsFiles;
    }

    /**
     * Indicates whether this folder has the holds-files flag set
     *
     * @return <code>true</code> if this folder has the holds-files flag set; otherwise <code>false</code>
     */
    public boolean containsHoldsFiles() {
        return b_holdsFiles;
    }

    /**
     * Removes whether this folder holds files.
     */
    public void removeHoldsFiles() {
        holdsFiles = false;
        b_holdsFiles = false;
    }

    /**
     * Sets whether this folder has the capability to hold files.
     *
     * @param holdsFiles <code>true</code> if this folder has the capability to hold files; otherwise <code>false</code>
     */
    public void setHoldsFiles(final boolean holdsFiles) {
        this.holdsFiles = holdsFiles;
        b_holdsFiles = true;
    }

    @Override
    public boolean isRootFolder() {
        return rootFolder;
    }

    /**
     * Sets if this folder is the root folder.
     *
     * @param rootFolder <code>true</code> if this folder is the root folder; otherwise <code>false</code>
     */
    public void setRootFolder(final boolean rootFolder) {
        this.rootFolder = rootFolder;
        b_rootFolder = true;
    }

    /**
     * Indicates whether this folder has the root-folder flag set
     *
     * @return <code>true</code> if this folder has the root-folder flag set; otherwise <code>false</code>
     */
    public boolean containsRootFolder() {
        return b_rootFolder;
    }

    /**
     * Removes whether this folder is the root folder.
     */
    public void removeRootFolder() {
        rootFolder = false;
        b_rootFolder = false;
    }

    @Override
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * Sets if this folder is subscribed.
     *
     * @param subscribed <code>true</code> if this folder is subscribed; otherwise <code>false</code>
     */
    public void setSubscribed(final boolean subscribed) {
        this.subscribed = subscribed;
        b_subscribed = true;
    }

    /**
     * Indicates whether this folder has the subscribed flag set
     *
     * @return <code>true</code> if this folder has the subscribed flag set; otherwise <code>false</code>
     */
    @Override
    public boolean containsSubscribed() {
        return b_subscribed;
    }

    /**
     * Removes whether this folder is subscribed.
     */
    public void removeSubscribed() {
        subscribed = false;
        b_subscribed = false;
    }

    /**
     * Indicates whether this folder exists in folder storage.
     *
     * @return <code>true</code> if this folder exists in folder storage; otherwise <code>false</code>
     */
    public boolean exists() {
        return exists;
    }

    /**
     * Sets whether this folder exists in folder storage.
     *
     * @param exists <code>true</code> if this folder exists in folder storage; otherwise <code>false</code>
     */
    public void setExists(final boolean exists) {
        this.exists = exists;
    }

    @Override
    public Date getCreationDate() {
        return creationDate;
    }

    @Override
    public Date getLastModifiedDate() {
        return lastModifiedDate;
    }

    /**
     * Sets the creation date
     *
     * @param creationDate The creation date to set
     */
    public void setCreationDate(final Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Sets the last modified date.
     *
     * @param lastModifiedDate The last modified date to set
     */
    public void setLastModifiedDate(final Date lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the properties
     *
     * @param properties The properties to set
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, Object> getMeta() {
        return this.meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    @Override
    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public int getModifiedBy() {
        return this.modifiedBy;
    }

    public void setModifiedBy(int modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    @Override
    public EntityInfo getCreatedFrom() {
        return this.createdFrom;
    }

    public void setCreatedFrom(EntityInfo createdFrom) {
        b_createdFrom = null != createdFrom;
        this.createdFrom = createdFrom;
    }

    public boolean containsCreatedFrom() {
        return b_createdFrom;
    }

    @Override
    public EntityInfo getModifiedFrom() {
        return this.modifiedFrom;
    }

    public void setModifiedFrom(EntityInfo modifiedFrom) {
        b_modifiedFrom = null != modifiedFrom;
        this.modifiedFrom = modifiedFrom;
    }

    public boolean containsModifiedFrom() {
        return b_modifiedFrom;
    }

}
