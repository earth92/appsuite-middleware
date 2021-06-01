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

package com.openexchange.mail.dataobjects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import com.openexchange.mail.api.MailFolderStorage;
import com.openexchange.mail.permission.MailPermission;
import com.openexchange.mailaccount.MailAccount;

/**
 * {@link MailFolderDescription} - A simple object for updating or creating a folder which holds user-modifiable folder attributes.
 * <p>
 * See the method descriptions of {@link #setExists(boolean)} and/or {@link #setFullname(String)} how to signal a create or an update
 * operation.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class MailFolderDescription {

    private static final MailPermission[] EMPTY_PERMS = new MailPermission[0];

    private boolean b_exists;

    private boolean b_fullname;

    private boolean b_name;

    private boolean b_parentFullname;

    private boolean b_permissions;

    private boolean b_separator;

    private boolean b_subscribed;

    private boolean exists;

    private String fullname;

    private int accountId;

    private String name;

    private String parentFullname;

    private int parentAccountId;

    private List<MailPermission> permissions;

    private char separator;

    private boolean subscribed;

    /**
     * Initializes a new {@link MailFolderDescription}
     */
    public MailFolderDescription() {
        super();
        parentAccountId = MailAccount.DEFAULT_ID;
        accountId = MailAccount.DEFAULT_ID;
    }

    /**
     * Adds a permission
     *
     * @param permission The permission to add
     */
    public void addPermission(MailPermission permission) {
        if (null == permission) {
            return;
        } else if (null == permissions) {
            permissions = new ArrayList<MailPermission>();
            b_permissions = true;
        }
        permissions.add(permission);
    }

    /**
     * Adds an array of permissions
     *
     * @param permissions The array of permissions to add
     */
    public void addPermissions(MailPermission[] permissions) {
        if ((null == permissions) || (permissions.length == 0)) {
            return;
        } else if (null == this.permissions) {
            this.permissions = new ArrayList<MailPermission>(permissions.length);
            b_permissions = true;
        }
        this.permissions.addAll(Arrays.asList(permissions));
    }

    /**
     * Adds a collection of permissions
     *
     * @param permissions The collection of permissions to add
     */
    public void addPermissions(Collection<? extends MailPermission> permissions) {
        if ((null == permissions) || (permissions.isEmpty())) {
            return;
        } else if (null == this.permissions) {
            this.permissions = new ArrayList<MailPermission>(permissions.size());
            b_permissions = true;
        }
        this.permissions.addAll(permissions);
    }

    /**
     * Checks if existence status has been set
     *
     * @return <code>true</code> if exists status is set; otherwise <code>false</code>
     */
    public boolean containsExists() {
        return b_exists;
    }

    /**
     * Checks if fullname has been set
     *
     * @return <code>true</code> if fullname is set; otherwise <code>false</code>
     */
    public boolean containsFullname() {
        return b_fullname;
    }

    /**
     * Checks if name has been set
     *
     * @return <code>true</code> if name is set; otherwise <code>false</code>
     */
    public boolean containsName() {
        return b_name;
    }

    /**
     * Checks if parent fullname has been set
     *
     * @return <code>true</code> if parentFullname is set; otherwise <code>false</code>
     */
    public boolean containsParentFullname() {
        return b_parentFullname;
    }

    /**
     * Checks if permissions have been set
     *
     * @return <code>true</code> if permissions are set; otherwise <code>false</code>
     */
    public boolean containsPermissions() {
        return b_permissions;
    }

    /**
     * Checks if separator has been set
     *
     * @return <code>true</code> if separator is set; otherwise <code>false</code>
     */
    public boolean containsSeparator() {
        return b_separator;
    }

    /**
     * Checks if subscription status has been set
     *
     * @return <code>true</code> if subscribed is set; otherwise <code>false</code>
     */
    public boolean containsSubscribed() {
        return b_subscribed;
    }

    /**
     * Checks if this folder exists
     *
     * @see #setExists(boolean)
     * @return <code>true</code> if folder exists in mailbox; otherwise <code>false</code>
     */
    public boolean exists() {
        return exists;
    }

    /**
     * Gets the fullname
     *
     * @see #setFullname(String)
     * @return the fullname
     */
    public String getFullname() {
        return fullname;
    }

    /**
     * Gets the account ID belonging to fullname.
     *
     * @return The account ID belonging to fullname.
     */
    public int getAccountId() {
        return accountId;
    }

    /**
     * Gets the name
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the parent fullname
     *
     * @return the parent fullname
     */
    public String getParentFullname() {
        return parentFullname;
    }

    /**
     * Gets the account ID belonging to parent.
     *
     * @return The account ID belonging to parent.
     */
    public int getParentAccountId() {
        return parentAccountId;
    }

    /**
     * Gets the permissions
     *
     * @return the permissions as array of {@link MailPermission}
     */
    public MailPermission[] getPermissions() {
        if (null == permissions) {
            return EMPTY_PERMS;
        }
        return permissions.toArray(new MailPermission[permissions.size()]);
    }

    /**
     * Gets the separator
     *
     * @return the separator
     */
    public char getSeparator() {
        return separator;
    }

    /**
     * Gets the subscription status
     *
     * @return the subscription status
     */
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * Removes exists status
     */
    public void removeExists() {
        exists = false;
        b_exists = false;
    }

    /**
     * Removes the fullname
     */
    public void removeFullname() {
        fullname = null;
        b_fullname = false;
    }

    /**
     * Removes the name
     */
    public void removeName() {
        name = null;
        b_name = false;
    }

    /**
     * Removes the parent fullname
     */
    public void removeParentFullname() {
        parentFullname = null;
        b_parentFullname = false;
    }

    /**
     * Removes the permissions
     */
    public void removePermissions() {
        permissions = null;
        b_permissions = false;
    }

    /**
     * Removes the separator
     */
    public void removeSeparator() {
        separator = '\0';
        b_separator = false;
    }

    /**
     * Removes the subscription status
     */
    public void removeSubscribed() {
        subscribed = false;
        b_subscribed = false;
    }

    /**
     * Sets the exists status
     * <p>
     * This flag is checked at first to detect if an update or create operation shall be performed. If not set - this is
     * {@link #containsExists()} returns <code>false</code> - the folder's fullname in conjunction with
     * {@link MailFolderStorage#exists(String)} is used to determine folder existence.
     *
     * @see #setFullname(String)
     * @param exists <code>true</code> if folder exists in mailbox; otherwise <code>false</code>
     */
    public void setExists(boolean exists) {
        this.exists = exists;
        b_exists = true;
    }

    /**
     * Sets the fullname
     * <p>
     * If {@link #containsExists()} returns <code>false</code> the {@link MailFolderStorage#exists(String)} method is used to determine
     * folder existence.
     *
     * @see #setExists(boolean)
     * @param fullname the fullname to set
     */
    public void setFullname(String fullname) {
        this.fullname = fullname;
        b_fullname = true;
    }

    /**
     * Sets the account ID belonging to fullname.
     *
     * @param accountId The account ID
     */
    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    /**
     * Sets the name
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
        b_name = true;
    }

    /**
     * Sets the parent fullname
     *
     * @param parentFullname the parent fullname to set
     */
    public void setParentFullname(String parentFullname) {
        this.parentFullname = parentFullname;
        b_parentFullname = true;
    }

    /**
     * Sets the account ID belonging to parent.
     *
     * @param parentAccountId The account ID belonging to parent.
     */
    public void setParentAccountId(int parentAccountId) {
        this.parentAccountId = parentAccountId;
    }

    /**
     * Sets the separator
     *
     * @param separator the separator to set
     */
    public void setSeparator(char separator) {
        this.separator = separator;
        b_separator = true;
    }

    /**
     * Sets the subscription status
     *
     * @param subscribed the subscription status to set
     */
    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
        b_subscribed = true;
    }

}
