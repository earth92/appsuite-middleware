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

package com.openexchange.groupware.container;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import com.google.common.collect.ImmutableList;
import com.openexchange.java.Strings;

/**
 * {@link FolderPathObject} - A path for a folder consisting of the individual names.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.0
 */
public class FolderPathObject implements Iterable<String>, Serializable {

    private static final long serialVersionUID = 3412678911624214154L;

    /** The folder path type determines to what starting folder a path belongs */
    public static enum FolderPathType {
        /** The folder path starts from user's default file storage folder */
        PRIVATE,
        /** The folder path starts from public file storage folder */
        PUBLIC,
        /** The folder path starts from shared file storage folder */
        SHARED,
        /** The folder path starts from undefined location, in which case it falls-back to user's default file storage folder */
        UNDEFINED;
    }

    /** The empty folder path */
    public static final FolderPathObject EMPTY_PATH = new FolderPathObject(ImmutableList.of()) {

        private static final long serialVersionUID = -3516637436349673116L;

        @Override
        public String toString() {
            return "";
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int size() {
            return 0;
        }
    };

    /**
     * Parses the folder path from specified path string
     *
     * @param folderPath The path string
     * @return The parsed instance
     */
    public static FolderPathObject parseFrom(String folderPath) {
        if (Strings.isEmpty(folderPath)) {
            return EMPTY_PATH;
        }

        return new FolderPathObject(Strings.splitBy(folderPath, '/', true));
    }

    /**
     * Creates a folder path consisting of the names from specified <code>Iterable</code> instance
     *
     * @param names The names to add
     * @return The folder path
     */
    public static FolderPathObject copyOf(Iterable<String> names) {
        if (null == names) {
            return null;
        }

        return new FolderPathObject(names);
    }

    /**
     * Creates a new builder instance.
     *
     * @return The new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** A builder for an instance of <code>FolderPath</code> */
    public static class Builder {

        private final List<String> names;

        Builder() {
            super();
            names = new ArrayList<>(8);
        }

        /**
         * Adds specified (untranslated) folder name to this builder.
         *
         * @param name The name to add
         * @return This builder
         */
        public Builder addNameToPath(String name) {
            if (null == name) {
                throw new IllegalArgumentException("Name must not be null");
            }
            names.add(name);
            return this;
        }

        /**
         * Builds the instance of <code>FolderPath</code> from this builder's arguments
         *
         * @return The instance of <code>FolderPath</code>
         */
        public FolderPathObject build() {
            return names.isEmpty() ? EMPTY_PATH : new FolderPathObject(names);
        }
    }

    // -------------------------------------------------------------------------------------------------------------------

    private final static String PUBLIC_INFOSTORE = Integer.toString(FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID);
    private final static String SHARED_INFOSTORE = Integer.toString(FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID);

    private static FolderPathType determineType(List<String> names) {
        FolderPathType type = FolderPathType.UNDEFINED;
        if (false == names.isEmpty()) {
            String firstName = names.get(0);
            if (Strings.isNotEmpty(firstName)) {
                if (PUBLIC_INFOSTORE.equals(firstName)) {
                    type = FolderPathType.PUBLIC;
                } else if (SHARED_INFOSTORE.equals(firstName)) {
                    type = FolderPathType.SHARED;
                } else {
                    type = FolderPathType.PRIVATE;
                }
            }
        }
        return type;
    }

    private final ImmutableList<String> names;
    private final int size;
    private final FolderPathType type;

    /**
     * Initializes a new {@link FolderPathObject}.
     */
    FolderPathObject(List<String> names) {
        super();
        this.names = ImmutableList.copyOf(names);
        size = names.size();
        this.type = determineType(this.names);
    }

    /**
     * Initializes a new {@link FolderPathObject}.
     */
    FolderPathObject(Iterable<String> names) {
        super();
        ImmutableList.Builder<String> b = ImmutableList.builder();
        for (String name : names) {
            b.add(name);
        }
        this.names = b.build();
        size = this.names.size();
        this.type = determineType(this.names);
    }

    /**
     * Initializes a new {@link FolderPathObject}.
     */
    FolderPathObject(String[] names) {
        super();
        this.names = ImmutableList.copyOf(names);
        size = names.length;
        this.type = determineType(this.names);
    }

    @Override
    public Iterator<String> iterator() {
        return names.iterator();
    }

    @Override
    public String toString() {
        return Strings.join(names, "/");
    }

    /**
     * Checks if this folder path is empty
     *
     * @return <code>true</code> if empty; otherwise <code>false</code>
     */
    public boolean isEmpty() {
        return names.isEmpty();
    }

    /**
     * Gets the number of name elements in this folder path.
     *
     * @return The number of name elements
     */
    public int size() {
        return size;
    }

    /**
     * Gets the type of this folder path.
     *
     * @return The type
     */
    public FolderPathType getType() {
        return type;
    }

    /**
     * Gets the path to use for restoring.
     *
     * @return The path for restoring
     */
    public Iterable<String> getPathForRestore() {
        if (size <= 1) {
            return Collections.emptyList();
        }

        return names.subList(1, size);
    }

}
