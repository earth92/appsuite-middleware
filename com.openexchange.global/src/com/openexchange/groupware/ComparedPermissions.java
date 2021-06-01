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

package com.openexchange.groupware;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.exception.OXException;

/**
 * {@link ComparedPermissions}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.8.0
 */
public abstract class ComparedPermissions<P, GP extends P> {

    private Collection<P> newPermissions;
    private Collection<P> originalPermissions;
    private List<GP> newGuests;
    private Map<Integer, P> addedGuests;
    private Map<Integer, P> addedUsers;
    private List<P> addedGroups;
    private List<P> removedGuests;
    private List<P> removedUsers;
    private List<P> modifiedUsers;
    private Map<Integer, P> modifiedGuests;
    private boolean hasChanges;

    /**
     * Initializes a new {@link ComparedPermissions}. You must call {@link #calc()} to initialize the instance completely!
     *
     * @param newPermissions The new permissions or <code>null</code>
     * @param originalPermissions The original permissions or <code>null</code>
     */
    protected ComparedPermissions(P[] newPermissions, P[] originalPermissions) {
        this(newPermissions == null ? null : Arrays.asList(newPermissions), originalPermissions == null ? null : Arrays.asList(originalPermissions));
    }

    /**
     * Initializes a new {@link ComparedPermissions}. You must call {@link #calc()} to initialize the instance completely!
     *
     * @param newPermissions The new permissions or <code>null</code>
     * @param originalPermissions The original permissions or <code>null</code>
     */
    protected ComparedPermissions(Collection<P> newPermissions, Collection<P> originalPermissions) {
        super();
        this.newPermissions = newPermissions;
        this.originalPermissions = originalPermissions;
    }

    protected abstract boolean isSystemPermission(P p);

    protected abstract boolean isUnresolvedGuestPermission(P p);

    protected abstract boolean isGuestUser(int userId) throws OXException;

    protected abstract boolean isGroupPermission(P p);

    protected abstract int getEntityId(P p);

    protected abstract boolean areEqual(P p1, P p2);

    @SuppressWarnings("unchecked")
    protected void calc() throws OXException {
        if (newPermissions == null) {
            newGuests = Collections.emptyList();
            addedGuests = Collections.emptyMap();
            addedUsers = Collections.emptyMap();
            addedGroups = Collections.emptyList();
            removedGuests = Collections.emptyList();
            removedUsers = Collections.emptyList();
            modifiedGuests = Collections.emptyMap();
            modifiedUsers = Collections.emptyList();
            hasChanges = false;
            return;
        }

        newGuests = new LinkedList<>();
        addedGuests = new LinkedHashMap<>();
        addedUsers = new LinkedHashMap<>();
        addedGroups = new LinkedList<>();
        removedGuests = new LinkedList<>();
        removedUsers = new LinkedList<>();
        modifiedUsers = new LinkedList<>();
        modifiedGuests = new LinkedHashMap<>();

        /*
         * Calculate added permissions
         */
        final Map<Integer, P> newUsers = new HashMap<>(newPermissions.size());
        final Map<Integer, P> newGroups = new HashMap<>();
        for (P permission : newPermissions) {
            if (isSystemPermission(permission)) {
                continue;
            }

            if (isUnresolvedGuestPermission(permission)) {
                // Check for guests among the new permissions
                newGuests.add((GP) permission);
            } else {
                if (isGroupPermission(permission)) {
                    newGroups.put(Integer.valueOf(getEntityId(permission)), permission);
                } else {
                    newUsers.put(Integer.valueOf(getEntityId(permission)), permission);
                }
            }
        }

        /*
         * Calculate removed permissions
         */
        final Map<Integer, P> oldUsers;
        final Map<Integer, P> oldGroups;
        if (null == originalPermissions) {
            oldUsers = Collections.emptyMap();
            oldGroups = Collections.emptyMap();
        } else {
            oldUsers = new HashMap<>(originalPermissions.size());
            oldGroups = new HashMap<>();
            for (P permission : originalPermissions) {
                if (isSystemPermission(permission)) {
                    continue;
                }

                if (isGroupPermission(permission)) {
                    oldGroups.put(Integer.valueOf(getEntityId(permission)), permission);
                } else {
                    oldUsers.put(Integer.valueOf(getEntityId(permission)), permission);
                }
            }
        }

        boolean permissionsChanged = newGuests.size() > 0;
        Set<Integer> addedUserIds = new HashSet<>(newUsers.keySet());
        addedUserIds.removeAll(oldUsers.keySet());
        permissionsChanged |= addedUserIds.size() > 0;

        Set<Integer> addedGroupIds = new HashSet<>(newGroups.keySet());
        addedGroupIds.removeAll(oldGroups.keySet());
        permissionsChanged |= addedGroupIds.size() > 0;

        Set<Integer> removedUserIds = new HashSet<>(oldUsers.keySet());
        removedUserIds.removeAll(newUsers.keySet());
        permissionsChanged |= removedUserIds.size() > 0;

        Set<Integer> removedGroupIds = new HashSet<>(oldGroups.keySet());
        removedGroupIds.removeAll(newGroups.keySet());
        permissionsChanged |= removedGroupIds.size() > 0;

        /*
         * Calculate new user and modified guest permissions
         */
        for (P newPermission : newUsers.values()) {
            int entityId = getEntityId(newPermission);
            P oldPermission = oldUsers.get(Integer.valueOf(entityId));
            if (!areEqual(newPermission, oldPermission)) {
                permissionsChanged = true;
                boolean isGuest = isGuestUser(entityId);
                if (oldPermission == null) {
                    if (isGuest) {
                        addedGuests.put(Integer.valueOf(entityId), newPermission);
                    } else {
                        addedUsers.put(Integer.valueOf(entityId), newPermission);
                    }
                } else if (isGuest) {
                    modifiedGuests.put(Integer.valueOf(entityId), newPermission);
                }
            }
        }

        for (P newPermission : newGroups.values()) {
            P oldPermission = oldGroups.get(Integer.valueOf(getEntityId(newPermission)));
            if (!areEqual(newPermission, oldPermission)) {
                permissionsChanged = true;
                addedGroups.add(newPermission);
            }
        }

        /*
         * Calculate removed guest permissions
         */
        for (Integer removed : removedUserIds) {
            P removedUser = oldUsers.get(removed);
            if (isGuestUser(removed.intValue())) {
                removedGuests.add(removedUser);
            }
            removedUsers.add(removedUser);
        }

        for (Map.Entry<Integer, P> entry : newUsers.entrySet()) {
            if (oldUsers.containsKey(entry.getKey())) {
                modifiedUsers.add(entry.getValue());
            }
        }

        hasChanges = permissionsChanged;
    }

    /**
     * @return Whether the permissions of both folder objects differ from each other
     */
    public boolean hasChanges() {
        return hasChanges;
    }

    /**
     * @return <code>true</code> if new permissions of type {@link GuestPermission} have been added
     */
    public boolean hasNewGuests() {
        return !newGuests.isEmpty();
    }

    /**
     * @return <code>true</code> if permissions for already existing guest users have been added
     */
    public boolean hasAddedGuests() {
        return !addedGuests.isEmpty();
    }

    /**
     * @return <code>true</code> if permissions for non-guest users have been added
     */
    public boolean hasAddedUsers() {
        return !addedUsers.isEmpty();
    }

    /**
     * @return <code>true</code> if permissions for groups have been added
     */
    public boolean hasAddedGroups() {
        return !addedGroups.isEmpty();
    }

    /**
     * @return <code>true</code> if guest permissions have been removed
     */
    public boolean hasRemovedGuests() {
        return !removedGuests.isEmpty();
    }

    /**
     * @return <code>true</code> if user permissions have been removed
     */
    public boolean hasRemovedUsers() {
        return !removedUsers.isEmpty();
    }

    /**
     * @return <code>true</code> if user permissions have been modified
     */
    public boolean hasModifiedUsers() {
        return !modifiedUsers.isEmpty();
    }

    /**
     * @return <code>true</code> if guest permissions have been modified
     */
    public boolean hasModifiedGuests() {
        return !modifiedGuests.isEmpty();
    }

    /**
     * @return A list of new {@link GuestPermission}s; never <code>null</code>
     */
    public List<GP> getNewGuestPermissions() {
        return newGuests;
    }

    /**
     * @return A list of added guest permissions for already existing guest users; never <code>null</code>
     */
    public List<P> getAddedGuestPermissions() {
        return new ArrayList<>(addedGuests.values());
    }

    /**
     * @return A list of added permissions for non-guest guest users; never <code>null</code>
     */
    public List<P> getAddedUserPermissions() {
        return new ArrayList<>(addedUsers.values());
    }

    /**
     * @return A list of added permissions for groups; never <code>null</code>
     */
    public List<P> getAddedGroupPermissions() {
        return addedGroups;
    }

    /**
     * @return A list of added permissions for all entities except new guests, i.e. no {@link GuestPermission}s
     */
    public List<P> getAddedPermissions() {
        List<P> permissions = new ArrayList<>(addedGuests.size() + addedUsers.size() + addedGroups.size());
        permissions.addAll(addedGuests.values());
        permissions.addAll(addedUsers.values());
        permissions.addAll(addedGroups);
        return permissions;
    }

    /**
     * @return A list of removed guest permissions; never <code>null</code>
     */
    public List<P> getRemovedGuestPermissions() {
        return removedGuests;
    }

    /**
     * @return A list of removed user permissions; never <code>null</code>
     */
    public List<P> getRemovedUserPermissions() {
        return removedUsers;
    }

    /**
     * @return A list of modified user permissions; never <code>null</code>
     */
    public List<P> getModifiedUserPermissions() {
        return modifiedUsers;
    }

    /**
     * @return A list of modified guest permissions; never <code>null</code>
     */
    public List<P> getModifiedGuestPermissions() {
        return new ArrayList<>(modifiedGuests.values());
    }

    /**
     * @return A list of already existing guest user IDs for whom permissions have been added
     */
    public List<Integer> getAddedGuests() {
        return new ArrayList<>(addedGuests.keySet());
    }

    /**
     * @return A list of non-guest user IDs for whom permissions have been added
     */
    public List<Integer> getAddedUsers() {
        return new ArrayList<>(addedUsers.keySet());
    }

    /**
     * @return A list of already existing guest user IDs for whom permissions have been modified
     */
    public List<Integer> getModifiedGuests() {
        return new ArrayList<>(modifiedGuests.keySet());
    }

    /**
     * @param guest The added guests ID
     * @return Gets the permission of the according added and already existing guest user
     */
    public P getAddedGuestPermission(Integer guestId) {
        return addedGuests.get(guestId);
    }

    /**
     * @param guest The modified guests ID
     * @return Gets the permission of the according modified guest user
     */
    public P getModifiedGuestPermission(Integer guestId) {
        return modifiedGuests.get(guestId);
    }

    /**
     * Gets the collection of original (storage) permissions that were used to calculate the changes.
     *
     * @return The original permissions
     */
    public Collection<P> getOriginalPermissions() {
        return originalPermissions;
    }

    /**
     * Gets the collection of the new (updated) permissions that were used to calculate the changes.
     *
     * @return The new permissions
     */
    public Collection<P> getNewPermissions() {
        return newPermissions;
    }

}
