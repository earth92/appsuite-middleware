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

package com.openexchange.folderstorage.internal;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.google.common.collect.ImmutableSet;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.Type;
import com.openexchange.folderstorage.database.contentType.InfostoreContentType;
import com.openexchange.groupware.EntityInfo;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.infostore.InfostoreFacades;
import com.openexchange.groupware.userconfiguration.AllowAllUserPermissionBits;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.groupware.userconfiguration.UserPermissionBitsStorage;
import com.openexchange.tools.oxfolder.OXFolderProperties;
import gnu.trove.EmptyTIntSet;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link EffectivePermission} - A read-only permission considering user access restrictions and folder boundaries.
 * <p>
 * Access to any of the setXXX() method will throw an {@link UnsupportedOperationException}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class EffectivePermission implements Permission {

    private static final long serialVersionUID = -6459987256871091818L;

    private static final int MODULE_INFOSTORE = InfostoreContentType.getInstance().getModule();

    /**
     * <code>"6"</code>
     */
    private static final String GAB = Integer.toString(FolderObject.SYSTEM_LDAP_FOLDER_ID);

    /**
     * <code>"9"</code>
     */
    private static final String INFOSTORE = Integer.toString(FolderObject.SYSTEM_INFOSTORE_FOLDER_ID);

    /**
     * <code>"10"</code>
     */
    private static final String INFOSTORE_USER = Integer.toString(FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID);

    /**
     * <code>"15"</code>
     */
    private static final String INFOSTORE_PUBLIC = Integer.toString(FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID);

    /**
     * <code>"9"</code>, <code>"10"</code>, and <code>"15"</code>
     */
    private static final Set<String> SYSTEM_INFOSTORES = ImmutableSet.of(INFOSTORE, INFOSTORE_PUBLIC, INFOSTORE_USER);

    /**
     * The configuration profile of the current logged in user.
     */
    private UserPermissionBits userPermissionBits;

    /**
     * The allowed content types.
     */
    private TIntSet allowedContentTypes;

    /**
     * The type of the referenced folder.
     */
    private final Type type;

    /**
     * The content type of the referenced folder.
     */
    private final ContentType contentType;

    /**
     * The referenced folder's identifier.
     */
    private final String folderId;

    /**
     * The underlying permission.
     */
    private Permission underlyingPerm;

    /**
     * The overridden entity identifier.
     */
    private int entityId;

    /**
     * The context.
     */
    private Context context;

    /**
     * Initializes a new {@link EffectivePermission}.
     *
     * @param underlyingPerm The underlying permission
     * @param folderId The referenced folder's identifier
     * @param type The type of the referenced folder
     * @param contentType The type of the referenced folder
     * @param userPermissionBits The configuration profile of the current logged in user
     */
    public EffectivePermission(Permission underlyingPerm, String folderId, Type type, ContentType contentType, UserPermissionBits userPermissionBits, List<ContentType> allowedContentTypes) {
        super();
        this.underlyingPerm = underlyingPerm;
        this.folderId = folderId;
        this.contentType = contentType;
        this.type = type;
        this.userPermissionBits = userPermissionBits;
        if (null == allowedContentTypes || allowedContentTypes.isEmpty()) {
            this.allowedContentTypes = EmptyTIntSet.getInstance();
        } else {
            TIntSet set = new TIntHashSet(allowedContentTypes.size() + 1);
            for (ContentType allowedContentType : allowedContentTypes) {
                set.add(allowedContentType.getModule());
            }
            // Module SYSTEM is allowed in any case
            set.add(FolderObject.SYSTEM_MODULE);
            set.add(FolderObject.UNBOUND);
            this.allowedContentTypes = set;
        }
        entityId = -1;
    }

    /**
     * Sets the entity information.
     *
     * @param entityId The entity identifier
     * @param context The context
     * @return This effective permission with information applied
     */
    public EffectivePermission setEntityInfo(final int entityId, final Context context) {
        this.entityId = entityId;
        this.context = context;
        return this;
    }

    @Override
    public boolean isVisible() {
        return isAdmin() || getFolderPermission() > NO_PERMISSIONS;
    }

    @Override
    public int hashCode() {
        return underlyingPerm.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Permission)) {
            return false;
        }
        final Permission other = (Permission) obj;
        if (isAdmin() != other.isAdmin()) {
            return false;
        }
        if (getDeletePermission() != other.getDeletePermission()) {
            return false;
        }
        if (false == Objects.equals(getIdentifier(), other.getIdentifier())) {
            return false;
        }
        if (getEntity() != other.getEntity()) {
            return false;
        }
        if (getFolderPermission() != other.getFolderPermission()) {
            return false;
        }
        if (isGroup() != other.isGroup()) {
            return false;
        }
        if (getReadPermission() != other.getReadPermission()) {
            return false;
        }
        if (getSystem() != other.getSystem()) {
            return false;
        }
        if (getWritePermission() != other.getWritePermission()) {
            return false;
        }
        return true;
    }

    private boolean hasModuleAccess() {
        final int module = getModule();
        if (!getUserPermissionBits().hasModuleAccess(module)) {
            return false;
        }
        if (MODULE_INFOSTORE == module && !InfostoreFacades.isInfoStoreAvailable() && !SYSTEM_INFOSTORES.contains(folderId) && isNumericId()) {
            return false;
        }
        if (!allowedContentTypes.isEmpty() && !allowedContentTypes.contains(module)) {
            return false;
        }
        return true;
    }

    private boolean isNumericId() {
        return com.openexchange.java.util.Tools.getUnsignedInteger(folderId) >= 0;
    }

    private UserPermissionBits getUserPermissionBits() {
        UserPermissionBits userPermissionBits = this.userPermissionBits;
        if (null == userPermissionBits) {
            try {
                userPermissionBits = this.userPermissionBits = UserPermissionBitsStorage.getInstance().getUserPermissionBits(getEntity(), context);
            } catch (OXException e) {
                userPermissionBits = this.userPermissionBits = new AllowAllUserPermissionBits(getEntity(), null, context);
            }
        }
        return userPermissionBits;
    }

    private int getFolderType() {
        return null != type ? type.getType() : -1;
    }

    private int getModule() {
        return contentType.getModule();
    }

    @Override
    public int getDeletePermission() {
        if (Integer.toString(FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID).equals(folderId)) {
            return underlyingPerm.getDeletePermission();
        }
        if (!hasModuleAccess()) {
            return NO_PERMISSIONS;
        } else if ((FolderObject.PUBLIC == getFolderType()) || String.valueOf(FolderObject.SYSTEM_PUBLIC_FOLDER_ID).equals(folderId)) {
            if ((getModule() != FolderObject.INFOSTORE) && !getUserPermissionBits().hasFullPublicFolderAccess()) {
                return NO_PERMISSIONS;
                /*
                 * return super.getDeletePermission() > DELETE_ALL_OBJECTS ? DELETE_ALL_OBJECTS : super .getDeletePermission();
                 */
            }
        } else if (!getUserPermissionBits().hasFullSharedFolderAccess() && (FolderObject.SHARED == getFolderType())) {
            return NO_PERMISSIONS;
        }
        return underlyingPerm.getDeletePermission();
    }

    @Override
    public String getIdentifier() {
        return entityId <= 0 ? underlyingPerm.getIdentifier() : String.valueOf(entityId);
    }

    @Override
    public int getEntity() {
        return entityId <= 0 ? underlyingPerm.getEntity() : entityId;
    }

    @Override
    public EntityInfo getEntityInfo() {
        return entityId <= 0 ? underlyingPerm.getEntityInfo() : null;
    }

    @Override
    public int getFolderPermission() {
        if (Integer.toString(FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID).equals(folderId)) {
            return underlyingPerm.getFolderPermission();
        }
        if (!hasModuleAccess()) {
            return NO_PERMISSIONS;
        } else if ((FolderObject.PUBLIC == getFolderType()) || String.valueOf(FolderObject.SYSTEM_PUBLIC_FOLDER_ID).equals(folderId)) {
            if ((getModule() != FolderObject.INFOSTORE) && !getUserPermissionBits().hasFullPublicFolderAccess()) {
                final int folderPermission = underlyingPerm.getFolderPermission();
                return folderPermission > READ_FOLDER ? READ_FOLDER : folderPermission;
            }
        } else if (!getUserPermissionBits().hasFullSharedFolderAccess() && (FolderObject.SHARED == getFolderType())) {
            return NO_PERMISSIONS;
        }
        return underlyingPerm.getFolderPermission();
    }

    @Override
    public int getReadPermission() {
        if (Integer.toString(FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID).equals(folderId)) {
            return underlyingPerm.getReadPermission();
        }
        if (!hasModuleAccess()) {
            return NO_PERMISSIONS;
        } else if ((FolderObject.PUBLIC == getFolderType()) || String.valueOf(FolderObject.SYSTEM_PUBLIC_FOLDER_ID).equals(folderId)) {
            if ((getModule() != FolderObject.INFOSTORE) && !getUserPermissionBits().hasFullPublicFolderAccess()) {
                final int readPermission = underlyingPerm.getReadPermission();
                return readPermission > READ_ALL_OBJECTS ? READ_ALL_OBJECTS : readPermission;
            }
        } else if (!getUserPermissionBits().hasFullSharedFolderAccess() && (FolderObject.SHARED == getFolderType())) {
            return NO_PERMISSIONS;
        }
        return underlyingPerm.getReadPermission();
    }

    @Override
    public int getSystem() {
        return underlyingPerm.getSystem();
    }

    @Override
    public int getWritePermission() {
        if (GAB.equals(folderId)) {
            int writePermission = underlyingPerm.getWritePermission();
            return OXFolderProperties.isEnableInternalUsersEdit() ? (writePermission <= NO_PERMISSIONS ? WRITE_OWN_OBJECTS : writePermission) : NO_PERMISSIONS;
        } else if (Integer.toString(FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID).equals(folderId)) {
            return underlyingPerm.getWritePermission();
        }

        if (!hasModuleAccess()) {
            return NO_PERMISSIONS;
        } else if ((FolderObject.PUBLIC == getFolderType()) || String.valueOf(FolderObject.SYSTEM_PUBLIC_FOLDER_ID).equals(folderId)) {
            if ((getModule() != FolderObject.INFOSTORE) && !getUserPermissionBits().hasFullPublicFolderAccess()) {
                return NO_PERMISSIONS;
            }
        } else if (!getUserPermissionBits().hasFullSharedFolderAccess() && (FolderObject.SHARED == getFolderType())) {
            return NO_PERMISSIONS;
        }
        return underlyingPerm.getWritePermission();
    }

    @Override
    public boolean isAdmin() {
        if (Integer.toString(FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID).equals(folderId)) {
            return underlyingPerm.isAdmin();
        }
        if (!hasModuleAccess()) {
            return false;
        } else if ((FolderObject.PUBLIC == getFolderType()) && (getModule() != FolderObject.INFOSTORE) && !getUserPermissionBits().hasFullPublicFolderAccess()) {
            return false;
        }
        return underlyingPerm.isAdmin();
    }

    @Override
    public boolean isGroup() {
        return underlyingPerm.isGroup();
    }

    @Override
    public void setAdmin(final boolean admin) {
        throw new UnsupportedOperationException("EffectivePermission.setAdmin()");
    }

    @Override
    public void setAllPermissions(final int folderPermission, final int readPermission, final int writePermission, final int deletePermission) {
        throw new UnsupportedOperationException("EffectivePermission.setAllPermissions()");
    }

    @Override
    public void setDeletePermission(final int permission) {
        throw new UnsupportedOperationException("EffectivePermission.setDeletePermission()");
    }

    @Override
    public void setIdentifier(String identifier) {
        throw new UnsupportedOperationException("EffectivePermission.setIdentifier()");
    }

    @Override
    public void setEntity(final int entity) {
        throw new UnsupportedOperationException("EffectivePermission.setEntity()");
    }

    @Override
    public void setEntityInfo(EntityInfo entityInfo) {
        throw new UnsupportedOperationException("EffectivePermission.setEntityInfo()");
    }

    @Override
    public void setFolderPermission(final int permission) {
        throw new UnsupportedOperationException("EffectivePermission.setFolderPermission()");
    }

    @Override
    public void setGroup(final boolean group) {
        throw new UnsupportedOperationException("EffectivePermission.setGroup()");
    }

    @Override
    public void setMaxPermissions() {
        throw new UnsupportedOperationException("EffectivePermission.setMaxPermissions()");
    }

    @Override
    public void setNoPermissions() {
        throw new UnsupportedOperationException("EffectivePermission.setNoPermissions()");
    }

    @Override
    public void setReadPermission(final int permission) {
        throw new UnsupportedOperationException("EffectivePermission.setReadPermission()");
    }

    @Override
    public void setSystem(final int system) {
        throw new UnsupportedOperationException("EffectivePermission.setSystem()");
    }

    @Override
    public void setWritePermission(final int permission) {
        throw new UnsupportedOperationException("EffectivePermission.setWritePermission()");
    }

    @Override
    public Object clone() {
        try {
            final EffectivePermission clone = (EffectivePermission) super.clone();
            clone.userPermissionBits = getUserPermissionBits().clone();
            clone.underlyingPerm = (Permission) underlyingPerm.clone();
            clone.allowedContentTypes =
                (null == allowedContentTypes || allowedContentTypes.isEmpty()) ? new TIntHashSet(1) : new TIntHashSet(allowedContentTypes.toArray());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e.getMessage());
        }
    }

    @Override
    public FolderPermissionType getType() {
        return underlyingPerm.getType();
    }

    @Override
    public void setType(FolderPermissionType type) {
        throw new UnsupportedOperationException("EffectivePermission.setType()");
    }

    @Override
    public String getPermissionLegator() {
        return underlyingPerm.getPermissionLegator();
    }

    @Override
    public void setPermissionLegator(String legator) {
        throw new UnsupportedOperationException("EffectivePermission.setPermissionLegator()");
    }

}
