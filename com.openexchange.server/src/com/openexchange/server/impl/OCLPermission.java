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

package com.openexchange.server.impl;

import java.io.Serializable;
import java.security.acl.Permission;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.group.GroupStorage;
import com.openexchange.tools.OXCloneable;

/**
 * {@link OCLPermission}
 *
 * @author <a href="mailto:martin.kauss@open-xchange.org">Martin Kauss</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class OCLPermission implements Permission, Cloneable, Serializable, OXCloneable<OCLPermission> {

    /*
     * Permisson Matrix # 2 4 8 16 32 64 128 #WERT
     *
     * Folder z 0 Folder +r 2 2 Folder +co 2 4 6 Folder +csf 2 4 8 14 Folder
     * admina 128
     *
     * Object z 0 Object +ro 2 2 Object +ra 2 4 6 Object adminr 128
     *
     * Object z 0 Object +wo 2 2 Object +wa 2 4 6 Object adminw 128
     *
     * Object +do 2 2 Object +da 2 4 4 Object admind 128
     *
     * (a) to delete a folder the user needs permissons to delete every object
     * in the folder!
     *
     * We must be able to: - set the owner - set role (only if principal ==
     * owner or another admin can add a new entity) - protect existing
     * permissons
     *
     *
     * CREATE TABLE folder ( "fuid" integer, "parent" integer, "fname" text,
     * "module" text, "type" text, "owner" text, "creator" text, "pid" integer,
     * "creating_date" timestamp, "created_from" text, "changing_date"
     * timestamp, "changed_from" text );
     *
     * fuid = unique folder id parent = parent folder (fuid) fname = folder name
     * module = system, task, calendar, contact, unbound type = system, private,
     * public, share owner = uid creator = uid pid = pointer to permission
     *
     * CREATE TABLE permission ( "puid" integer, "pid" integer, "role" integer,
     * "entity" text, "sealed" integer, "fp" integer, "orp" integer, "owp"
     * integer, "odp" integer );
     *
     * puid = unique permission id pid = permission id (folder.pid) role = role
     * entity = entity (uid, group, ...) sealed = sealed (0 / n) fp = folder
     * permission orp = object read permission owp = object write permission odp
     * = object delete permission
     */

    private static final String STR_USER = "User";

    private static final String STR_GROUP = "Group";

    private static final String STR_EMPTY = "";

    private static final String STR_FOLDER_ADMIN = "_FolderAdmin";

    private static final String STR_SYSTEM = "system";

    private static final String STR_TYPE = "type";

    private static final long serialVersionUID = 3740098766897625419L;

    private static final transient org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OCLPermission.class);

    public static final int NO_PERMISSIONS = 0;

    public static final int ADMIN_PERMISSION = 128;

    public static final int READ_FOLDER = 2;

    public static final int CREATE_OBJECTS_IN_FOLDER = 4;

    public static final int CREATE_SUB_FOLDERS = 8;

    public static final int READ_OWN_OBJECTS = 2;

    public static final int READ_ALL_OBJECTS = 4;

    public static final int WRITE_OWN_OBJECTS = 2;

    public static final int WRITE_ALL_OBJECTS = 4;

    public static final int DELETE_OWN_OBJECTS = 2;

    public static final int DELETE_ALL_OBJECTS = 4;

    public static final int ALL_GROUPS_AND_USERS = GroupStorage.GROUP_ZERO_IDENTIFIER;

    public static final int ALL_GUESTS = GroupStorage.GUEST_GROUP_IDENTIFIER;

    /**
     * The bit for system flag
     */
    public static final int SYSTEM_SYSTEM = 1;

    private String name;
    private int fuid;
    private int entity = -1;
    private int fp;
    private int orp;
    private int owp;
    private int odp;
    private int system;
    private FolderPermissionType type;
    private String legator;

    /**
     * This property defines if this permission declares the owner to be the
     * folder administrator who possesses the rights to alter a folder's
     * properties or to rename a folder
     */
    private boolean folderAdmin;

    /**
     * This property defines if this permission is applied to a system group or
     * to a single user instead
     */
    private boolean groupPermission;

    /**
     * Initializes a new {@link OCLPermission}
     */
    public OCLPermission() {
        super();
        fp = NO_PERMISSIONS;
        orp = NO_PERMISSIONS;
        owp = NO_PERMISSIONS;
        odp = NO_PERMISSIONS;
        type = FolderPermissionType.NORMAL;
        legator = null;
    }

    /**
     * Initializes a new {@link OCLPermission}
     *
     * @param entity The entity ID
     * @param fuid The folder ID
     */
    public OCLPermission(final int entity, final int fuid) {
        super();
        fp = NO_PERMISSIONS;
        orp = NO_PERMISSIONS;
        owp = NO_PERMISSIONS;
        odp = NO_PERMISSIONS;
        type = FolderPermissionType.NORMAL;
        legator = null;
        this.entity = entity;
        this.fuid = fuid;
    }

    /**
     * Reset for re-usage
     */
    public void reset() {
        name = null;
        fuid = 0;
        entity = -1;
        fp = 0;
        orp = 0;
        owp = 0;
        odp = 0;
        folderAdmin = false;
        groupPermission = false;
        system = 0;
        type = FolderPermissionType.NORMAL;
        legator = null;
    }

    /**
     * Sets the system bit mask
     *
     * @param system The system bit mask
     */
    public void setSystem(final int system) {
        this.system = system;
    }

    /**
     * Sets the folder's type
     *
     * @param type The folder's type
     */
    public void setType(final FolderPermissionType type) {
        this.type = type;
    }
    
    /**
     * Retrieves the id of the permission legator
     */
    public String getPermissionLegator() {
        return legator;
    }

    /**
     * Sets the permission legator
     * 
     * @param legator The permission legator
     */
    public void setPermissionLegator(String legator) {
        this.legator = legator;
    }

    /**
     * Sets the name
     *
     * @param name The name
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Sets the entity ID
     *
     * @param entity The entity ID
     */
    public void setEntity(final int entity) {
        this.entity = entity;
        if (name == null) {
            name = entity + (folderAdmin ? STR_FOLDER_ADMIN : STR_EMPTY) + (groupPermission ? STR_GROUP : STR_USER);
        }
    }

    /**
     * Set folder admin
     *
     * @param folderAdmin <code>true</code> to allow folder admin; otherwise
     *            <code>false</code>
     */
    public void setFolderAdmin(final boolean folderAdmin) {
        this.folderAdmin = folderAdmin;
        if (name == null) {
            name = entity + (folderAdmin ? STR_FOLDER_ADMIN : STR_EMPTY) + (groupPermission ? STR_GROUP : STR_USER);
        }
    }

    /**
     * Set group permission
     *
     * @param folderAdmin <code>true</code> to mark as group; otherwise
     *            <code>false</code>
     */
    public void setGroupPermission(final boolean groupPermission) {
        this.groupPermission = groupPermission;
        if (name == null) {
            name = entity + (folderAdmin ? STR_FOLDER_ADMIN : STR_EMPTY) + (groupPermission ? STR_GROUP : STR_USER);
        }
    }

    /**
     * Sets the folder permission
     *
     * @param p The folder permission
     * @return <code>true</code> if given permission value could be successfully
     *         applied; otherwise <code>false</code>
     */
    public boolean setFolderPermission(final int p) {
        if (validatePermission(p)) {
            this.fp = p;
            return true;
        }
        return false;
    }

    /**
     * Sets the read permission
     *
     * @param p The read permission
     * @return <code>true</code> if given permission value could be successfully
     *         applied; otherwise <code>false</code>
     */
    public boolean setReadObjectPermission(final int p) {
        if (validatePermission(p)) {
            this.orp = p;
            return true;
        }
        return false;
    }

    /**
     * Sets the write permission
     *
     * @param p The write permission
     * @return <code>true</code> if given permission value could be successfully
     *         applied; otherwise <code>false</code>
     */
    public boolean setWriteObjectPermission(final int p) {
        if (validatePermission(p)) {
            this.owp = p;
            return true;
        }
        return false;
    }

    /**
     * Sets the delete permission
     *
     * @param p The delete permission
     * @return <code>true</code> if given permission value could be successfully
     *         applied; otherwise <code>false</code>
     */
    public boolean setDeleteObjectPermission(final int p) {
        if (validatePermission(p)) {
            this.odp = p;
            return true;
        }
        return false;
    }

    /**
     * Sets all object-related permission
     *
     * @param pr The read permission
     * @param pw The write permission
     * @param pd The delete permission
     * @return <code>true</code> if given permission values could be
     *         successfully applied; otherwise <code>false</code>
     */
    public boolean setAllObjectPermission(final int pr, final int pw, final int pd) {
        if (validatePermission(pr) && validatePermission(pw) && validatePermission(pd)) {
            this.orp = pr;
            this.owp = pw;
            this.odp = pd;
            return true;
        }
        return false;
    }

    /**
     * Sets all permission
     *
     * @param fp The folder permission
     * @param opr The read permission
     * @param opw The write permission
     * @param opd The delete permission
     * @return <code>true</code> if given permission values could be
     *         successfully applied; otherwise <code>false</code>
     */
    public boolean setAllPermission(final int fp, final int opr, final int opw, final int opd) {
        if (validatePermission(fp) && validatePermission(opr) && validatePermission(opw) && validatePermission(opd)) {
            this.fp = fp;
            this.orp = opr;
            this.owp = opw;
            this.odp = opd;
            return true;
        }
        return false;
    }

    /**
     * Validates given permission value
     *
     * @param p The permission value to validate
     * @return <code>true</code> if value is valid; otherwise <code>false</code>
     */
    private final boolean validatePermission(final int p) {
        return ((p % 2 == 0 && (p <= 128 && p >= 0)));
    }

    /**
     * Sets the folder ID
     *
     * @param fuid The folder ID
     */
    public void setFuid(final int fuid) {
        this.fuid = fuid;
    }

    /**
     * Checks if this permission grants folder admin
     *
     * @return <code>true</code> if this permission grants folder admin;
     *         otherwise <code>false</code>
     */
    public boolean isFolderAdmin() {
        return folderAdmin;
    }

    /**
     * Checks if this permission is marked as group permission
     *
     * @return <code>true</code> if this permission is marked as group
     *         permission; otherwise <code>false</code>
     */
    public boolean isGroupPermission() {
        return groupPermission;
    }

    /**
     * Gets the folder permission
     *
     * @return The folder permission
     */
    public int getFolderPermission() {
        return fp;
    }

    /**
     * Gets the read permission
     *
     * @return The read permission
     */
    public int getReadPermission() {
        return orp;
    }

    /**
     * Gets the write permission
     *
     * @return The write permission
     */
    public int getWritePermission() {
        return owp;
    }

    /**
     * Gets the delete permission
     *
     * @return The delete permission
     */
    public int getDeletePermission() {
        return odp;
    }

    /**
     * Checks if this permission grants at least folder's visibility
     *
     * @return <code>true</code> if folder's visibility is granted; otherwise
     *         <code>false</code>
     */
    public boolean isFolderVisible() {
        if (isFolderAdmin()) {
            return true;
        }
        return (getFolderPermission() >= READ_FOLDER);
    }

    /**
     * Checks if this permission grants at least object creation
     *
     * @return <code>true</code> if object creation is granted; otherwise
     *         <code>false</code>
     */
    public boolean canCreateObjects() {
        return (getFolderPermission() >= CREATE_OBJECTS_IN_FOLDER);
    }

    /**
     * Checks if this permission grants at least subfolder creation
     *
     * @return <code>true</code> if subfolder creation is granted; otherwise
     *         <code>false</code>
     */
    public boolean canCreateSubfolders() {
        return (getFolderPermission() >= CREATE_SUB_FOLDERS);
    }

    /**
     * Checks if this permission grants at least own object read access
     *
     * @return <code>true</code> if own object read access is granted; otherwise
     *         <code>false</code>
     */
    public boolean canReadOwnObjects() {
        return (getReadPermission() >= READ_OWN_OBJECTS);
    }

    /**
     * Checks if this permission grants at least all object read access
     *
     * @return <code>true</code> if all object read access is granted; otherwise
     *         <code>false</code>
     */
    public boolean canReadAllObjects() {
        return (getReadPermission() >= READ_ALL_OBJECTS);
    }

    /**
     * Checks if this permission grants at least own object write access
     *
     * @return <code>true</code> if own object write access is granted;
     *         otherwise <code>false</code>
     */
    public boolean canWriteOwnObjects() {
        return (getWritePermission() >= WRITE_OWN_OBJECTS);
    }

    /**
     * Checks if this permission grants at least all object write access
     *
     * @return <code>true</code> if all object write access is granted;
     *         otherwise <code>false</code>
     */
    public boolean canWriteAllObjects() {
        return (getWritePermission() >= WRITE_ALL_OBJECTS);
    }

    /**
     * Checks if this permission grants at least own object deletion
     *
     * @return <code>true</code> if own object deletion is granted; otherwise
     *         <code>false</code>
     */
    public boolean canDeleteOwnObjects() {
        return (getDeletePermission() >= DELETE_OWN_OBJECTS);
    }

    /**
     * Checks if this permission grants at least all object deletion
     *
     * @return <code>true</code> if all object deletion is granted; otherwise
     *         <code>false</code>
     */
    public boolean canDeleteAllObjects() {
        return (getDeletePermission() >= DELETE_ALL_OBJECTS);
    }

    /**
     * Gets the name
     *
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the entity ID
     *
     * @return The entity ID
     */
    public int getEntity() {
        return entity;
    }

    /**
     * Gets the folder ID
     *
     * @return The folder ID
     */
    public int getFuid() {
        return fuid;
    }

    /**
     * Gets the system bit mask
     *
     * @return The system bit mask
     */
    public int getSystem() {
        return system;
    }

    /**
     * Gets the folder's type.
     *
     * @return The folder's type
     */
    public FolderPermissionType getType() {
        return type;
    }

    /**
     * Checks if this permission's system bit mask indicates the system flag
     *
     * @return <code>true</code> if system flag is set; otherwise
     *         <code>false</code>
     */
    public boolean isSystem() {
        return system == SYSTEM_SYSTEM;
    }

    /**
     * Compares this permission's sole permission values to the ones in
     * <code>op</code>.
     *
     * @param op The other permission
     * @return <code>true</code> if sole permission settings are equal;
     *         otherwise <code>false</code>.
     */
    public boolean equalsPermission(final OCLPermission op) {
        if (this == op) {
            return true;
        } else if (op == null) {
            return false;
        }
        return (fp == op.fp) && (orp == op.orp) && (owp == op.owp) && (odp == op.odp)
                && (folderAdmin == op.folderAdmin) && (groupPermission == op.groupPermission);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        } else if (other == null || !(other instanceof OCLPermission)) {
            return false;
        }
        final OCLPermission op = (OCLPermission) other;
        return (entity == op.entity) && (fuid == op.fuid) && (fp == op.fp) && (orp == op.orp) && (owp == op.owp)
                && (odp == op.odp) && (folderAdmin == op.folderAdmin) && (groupPermission == op.groupPermission)
                && (system == op.system) && (type == op.type)
                && ((legator != null && legator.equals(op.legator)) || (legator == null && op.legator == null));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + entity;
        hash = 31 * hash + fuid;
        hash = 31 * hash + fp;
        hash = 31 * hash + orp;
        hash = 31 * hash + owp;
        hash = 31 * hash + odp;
        hash = 31 * hash + (folderAdmin ? 1 : 0);
        hash = 31 * hash + (groupPermission ? 1 : 0);
        hash = 31 * hash + system;
        hash = 31 * hash + type.getTypeNumber();
        hash = 31 * hash + (legator == null ? 0 : legator.hashCode());
        return hash;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer(50);
        sb.append((folderAdmin ? STR_FOLDER_ADMIN : STR_EMPTY)).append((groupPermission ? STR_GROUP : STR_USER))
                .append(entity).append('@').append(fp).append('.').append(orp).append('.').append(owp).append('.')
                .append(odp).append(' ').append(STR_SYSTEM).append('=').append(system)
                .append(' ').append(STR_TYPE).append('=').append(type);
        return sb.toString();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        final OCLPermission clone = (OCLPermission) super.clone();
        return clone;
    }

    @Override
    public OCLPermission deepClone() {
        try {
            return ((OCLPermission) super.clone());
        } catch (CloneNotSupportedException e) {
            LOG.error("", e);
            return null;
        }
    }

}
