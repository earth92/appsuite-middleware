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

package com.openexchange.folderstorage;

import com.openexchange.exception.Category;
import com.openexchange.exception.DisplayableOXExceptionCode;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXException.Generic;
import com.openexchange.exception.OXExceptionFactory;
import com.openexchange.exception.OXExceptionStrings;

/**
 * {@link FolderExceptionErrorMessage} - Error messages for folder exceptions.
 * <p>
 * Subclasses are supposed to start at <code>1000</code>.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public enum FolderExceptionErrorMessage implements DisplayableOXExceptionCode {

    /**
     * Unexpected error: %1$s
     */
    UNEXPECTED_ERROR("Unexpected error: %1$s", Category.CATEGORY_ERROR, 1001),
    /**
     * I/O error: %1$s
     */
    IO_ERROR("I/O error: %1$s", Category.CATEGORY_ERROR, 1002),
    /**
     * Folder "%1$s" is not visible to user "%2$s" in context "%3$s"
     * <p>
     * Folder identifier should be passed as first argument to not injure privacy through publishing folder name.
     */
    FOLDER_NOT_VISIBLE("Folder \"%1$s\" is not visible to user \"%2$s\" in context \"%3$s\"", Category.CATEGORY_PERMISSION_DENIED, 3, FolderExceptionMessages.FOLDER_NOT_VISIBLE_MSG_DISPLAY),
    /**
     * No appropriate folder storage for tree identifier "%1$s" and folder identifier "%2$s".
     */
    NO_STORAGE_FOR_ID("No appropriate folder storage for tree identifier \"%1$s\" and folder identifier \"%2$s\".", Category.CATEGORY_ERROR, 1004),
    /**
     * No appropriate folder storage for tree identifier "%1$s" and content type "%2$s".
     */
    NO_STORAGE_FOR_CT("No appropriate folder storage for tree identifier \"%1$s\" and content type \"%2$s\".", Category.CATEGORY_ERROR, 1005),
    /**
     * Missing session.
     */
    MISSING_SESSION("Missing session.", Category.CATEGORY_ERROR, 1006, FolderExceptionMessages.MISSING_SESSION_MSG_DISPLAY),
    /**
     * SQL error: %1$s
     */
    SQL_ERROR("SQL error: %1$s", Category.CATEGORY_ERROR, 1007, OXExceptionStrings.SQL_ERROR_MSG),
    /**
     * Folder "%1$s" could not be found in tree "%2$s".
     */
    NOT_FOUND("Folder \"%1$s\" could not be found in tree \"%2$s\".", Category.CATEGORY_ERROR, 8, FolderExceptionMessages.NOT_FOUND_MSG_DISPLAY),
    /**
     * Missing tree identifier.
     */
    MISSING_TREE_ID("Missing tree identifier.", Category.CATEGORY_ERROR, 1009),
    /**
     * Missing parent folder identifier.
     */
    MISSING_PARENT_ID("Missing parent folder identifier.", Category.CATEGORY_ERROR, 1010),
    /**
     * Missing folder identifier.
     */
    MISSING_FOLDER_ID("Missing folder identifier.", Category.CATEGORY_ERROR, 1011),
    /**
     * Parent folder "%1$s" does not allow folder content type "%2$s" in tree "%3$s" for user %4$s in context %5$s.
     */
    INVALID_CONTENT_TYPE("Parent folder \"%1$s\" does not allow folder content type \"%2$s\" in tree \"%3$s\" for user %4$s in context %5$s.", Category.CATEGORY_ERROR, 1012, FolderExceptionMessages.INVALID_CONTENT_TYPE_MSG_DISPLAY),
    /**
     * Move operation not permitted for folder "%1$s" for user %2$s in context %3$s
     */
    MOVE_NOT_PERMITTED("Move operation not permitted for folder \"%1$s\" for user %2$s in context %3$s.", Category.CATEGORY_ERROR, 1013, FolderExceptionMessages.MOVE_NOT_PERMITTED_MSG_DISPLAY),
    /**
     * A folder named "%1$s" already exists below parent folder "%2$s" in tree "%3$s".
     */
    EQUAL_NAME("A folder named \"%1$s\" already exists below parent folder \"%2$s\" in tree \"%3$s\".", Category.CATEGORY_PERMISSION_DENIED, 1014, FolderExceptionMessages.EQUAL_NAME_MSG_DISPLAY),
    /**
     * Subscribe operation not permitted on tree "%1$s".
     */
    NO_REAL_SUBSCRIBE("Subscribe operation not permitted on tree \"%1$s\".", Category.CATEGORY_PERMISSION_DENIED, 1015),
    /**
     * Unsubscribe operation not permitted on tree "%1$s".
     */
    NO_REAL_UNSUBSCRIBE("Unsubscribe operation not permitted on tree \"%1$s\".", Category.CATEGORY_PERMISSION_DENIED, 1016),
    /**
     * Unsubscribe operation not permitted on folder "%1$s" in tree "%2$s". Unsubscribe subfolders first.
     */
    NO_UNSUBSCRIBE("Unsubscribe operation not permitted on folder \"%1$s\" in tree \"%2$s\". Unsubscribe subfolders first.", Category.CATEGORY_PERMISSION_DENIED, 1017),
    /**
     * Unknown content type: %1$s.
     */
    UNKNOWN_CONTENT_TYPE("Unknown content type: %1$s.", Category.CATEGORY_ERROR, 1018),
    /**
     * Missing parameter: %1$s.
     */
    MISSING_PARAMETER("Missing parameter: %1$s.", Category.CATEGORY_ERROR, 1019),
    /**
     * Unsupported storage type: %1$s.
     */
    UNSUPPORTED_STORAGE_TYPE("Unsupported storage type: %1$s.", Category.CATEGORY_ERROR, 1020),
    /**
     * Missing property: %1$s.
     */
    MISSING_PROPERTY("Missing property: %1$s.", Category.CATEGORY_ERROR, 1021),
    /**
     * The object has been changed in the meantime.
     */
    CONCURRENT_MODIFICATION("The object has been changed in the meantime.", Category.CATEGORY_CONFLICT, 1022),
    /**
     * JSON error: %1$s
     */
    JSON_ERROR("JSON error: %1$s", Category.CATEGORY_ERROR, 1023),
    /**
     * No default folder available for content type "%1$s" in tree "%2$s".
     */
    NO_DEFAULT_FOLDER("No default folder available for content type \"%1$s\" in tree \"%2$s\".", Category.CATEGORY_ERROR, 1024),
    /**
     * Invalid folder identifier: %1$s.
     */
    INVALID_FOLDER_ID("Invalid folder identifier: %1$s.", Category.CATEGORY_ERROR, 1025),
    /**
     * Folder "%1$s" must not be deleted by user "%2$s" in context "%3$s".
     * <p>
     * Folder identifier should be passed as first argument to not injure privacy through publishing folder name.
     */
    FOLDER_NOT_DELETEABLE("Folder \"%1$s\" must not be deleted by user \"%2$s\" in context \"%3$s\".", Category.CATEGORY_PERMISSION_DENIED, 1026, FolderExceptionMessages.FOLDER_NOT_DELETEABLE_MSG_DISPLAY),
    /**
     * Folder "%1$s" must not be moved by user "%2$s" in context "%3$s".
     * <p>
     * Folder identifier should be passed as first argument to not injure privacy through publishing folder name.
     */
    FOLDER_NOT_MOVEABLE("Folder \"%1$s\" must not be moved by user \"%2$s\" in context \"%3$s\".", Category.CATEGORY_PERMISSION_DENIED, 1027, FolderExceptionMessages.FOLDER_NOT_MOVEABLE_MSG_DISPLAY),
    /**
     * A temporary error occurred. Please retry.
     */
    TEMPORARY_ERROR("A temporary error occurred. Please retry.", Category.CATEGORY_ERROR, 1028),
    /**
     * User "%2$s" must not create subfolders below folder "%2$s" in context "%3$s".
     */
    NO_CREATE_SUBFOLDERS("User \"%2$s\" must not create subfolders below folder \"%2$s\" in context \"%3$s\".", Category.CATEGORY_PERMISSION_DENIED, 1029, FolderExceptionMessages.NO_CREATE_SUBFOLDERS_MSG_DISPLAY),
    /**
     * No mail folder allowed below a public folder.
     */
    NO_PUBLIC_MAIL_FOLDER("No mail folder allowed below a public folder.", Category.CATEGORY_PERMISSION_DENIED, 1030, FolderExceptionMessages.NO_PUBLIC_MAIL_FOLDER_MSG_DISPLAY),
    /**
     * No such tree with identifier "%1$s".
     */
    TREE_NOT_FOUND("No such tree with identifier \"%1$s\".", Category.CATEGORY_PERMISSION_DENIED, 1031),
    /**
     * A tree with identifier "%1$s" already exists.
     */
    DUPLICATE_TREE("A tree with identifier \"%1$s\" already exists.", Category.CATEGORY_ERROR, 1032),
    /**
     * The folder name "%1$s" is reserved. Please choose another name.
     */
    RESERVED_NAME("The folder name \"%1$s\" is reserved. Please choose another name.", Category.CATEGORY_PERMISSION_DENIED, 1033, FolderExceptionMessages.RESERVED_NAME_MSG_DISPLAY),
    /**
     * Found two folders named "%1$s" located below the parent folder "%2$s". Please rename one of the folders. There should be no two folders with the same name.
     */
    DUPLICATE_NAME("Found two folders named \"%1$s\" located below the parent folder \"%2$s\". Please rename one of the folders. There should be no two folders with the same name.", Category.CATEGORY_PERMISSION_DENIED, 1034, FolderExceptionMessages.DUPLICATE_NAME_MSG_DISPLAY),
    /**
     * An unexpected error occurred: %1$s. Please try again.
     */
    TRY_AGAIN("An unexpected error occurred: %1$s. Please try again.", Category.CATEGORY_TRY_AGAIN, 1035),
    /**
     * Specified session is invalid: %1$s
     */
    INVALID_SESSION("Specified session is invalid: %1$s", Category.CATEGORY_ERROR, 1036),
    /**
     * Failed to delete following folder/s: %1$s
     */
    FOLDER_DELETION_FAILED("Failed to delete following folder/s: %1$s", Category.CATEGORY_ERROR, 1037, FolderExceptionMessages.FOLDER_DELETION_FAILED_MSG_DISPLAY),
    /**
     * Folder updated aborted: %2$s
     */
    FOLDER_UPDATE_ABORTED("Folder update aborted: %2$s", Category.CATEGORY_CONFLICT, 1038, FolderExceptionMessages.FOLDER_UPDATE_ABORTED_MSG_DISPLAY),
    /**
     * The set permissions (%1$d) are invalid for entity (%2$d) on object %3$s.
     */
    INVALID_PERMISSIONS("The set permissions (%1$d) are invalid for entity (%2$d) on object %3$s.", Category.CATEGORY_PERMISSION_DENIED, 1039, FolderExceptionMessages.INVALID_PERMISSIONS_MSG),
    /**
     * Folder name contains illegal characters: \"%1$s\"
     */
    ILLEGAL_CHARACTERS("Folder name contains illegal characters: \"%1$s\"", Category.CATEGORY_USER_INPUT, 1040, FolderExceptionMessages.ILLEGAL_CHARACTERS_MSG),
    /**
     * Invoked method is not supported.
     */
    UNSUPPORTED_OPERATION("Invoked method is not supported.", CATEGORY_ERROR, 1041),
    /**
     * Restore from trash is not supported.
     */
    NO_RESTORE_SUPPORT("Restore from trash is not supported.", Category.CATEGORY_ERROR, 1042, FolderExceptionMessages.NO_RESTORE_SUPPORT_MSG),
    /**
     * Process was interrupted. Please try again.
     */
    INTERRUPT_ERROR("Process was interrupted. Please try again.", CATEGORY_TRY_AGAIN, 1043, OXExceptionStrings.MESSAGE_RETRY),
    /**
     * Changing the subscription or the used_for_sync state is not allowed for default folders.
     */
    SUBSCRIBE_NOT_ALLOWED("Changing the subscription or the used_for_sync state is not allowed for default folders.", CATEGORY_USER_INPUT, 1044, OXExceptionStrings.MESSAGE_DENIED),
    /**
     * With moving the shared folder \"%1$s\" people will lose access.
     */
    MOVE_TO_NOT_SHARED_WARNING("With moving the shared folder \"%1$s\" people will lose access.", CATEGORY_WARNING, 1045, FolderExceptionMessages.MOVE_TO_NOT_SHARED_WARNING),
    /**
     * This folder will be shared with everyone who has access to \"%3$s\". Everyone who can see \"%1$s\" will lose access.
     */
    MOVE_TO_ANOTHER_SHARED_WARNING("This folder will be shared with everyone who has access to \"%3$s\". Everyone who can see \"%1$s\" will lose access.", CATEGORY_WARNING, 1046, FolderExceptionMessages.MOVE_TO_ANOTHER_SHARED_WARNING),
    /**
     * This folder will be shared with everyone who has access to \"%3$s\".
     */
    MOVE_TO_SHARED_WARNING("This folder will be shared with everyone who has access to \"%3$s\".", CATEGORY_WARNING, 1047, FolderExceptionMessages.MOVE_TO_SHARED_WARNING),
    /**
     * You are moving a folder that contains shares. People will lose access.
     */
    MOVE_SHARED_SUBFOLDERS_TO_NOT_SHARED_WARNING("You are moving a folder that contains shares. People will lose access.", CATEGORY_WARNING, 1048, FolderExceptionMessages.MOVE_SHARED_SUBFOLDERS_TO_NOT_SHARED_WARNING),
    /**
     * This folder will be shared with everyone who has access to \"%3$s\". Everyone who can see the subfolders of \"%1$s\" will lose access.
     */
    MOVE_SHARED_SUBFOLDERS_TO_SHARED_WARNING("This folder will be shared with everyone who has access to \"%3$s\". Everyone who can see the subfolders of \"%1$s\" will lose access.", CATEGORY_WARNING, 1049, FolderExceptionMessages.MOVE_SHARED_SUBFOLDERS_TO_SHARED_WARNING),
    /**
     * Searching folder by folder name is not supported
     */
    NO_SEARCH_SUPPORT("Searching folder by folder name is not supported", CATEGORY_ERROR, 1050, FolderExceptionMessages.NO_SEARCH_SUPPORT_MSG),

    ;

    private static final String PREFIX = "FLD";

    /**
     * The prefix for this error codes.
     */
    public static String prefix() {
        return PREFIX;
    }

    private final Category category;
    private final int detailNumber;
    private final String message;
    private final String displayMessage;

    /**
     * Initializes a new {@link FolderExceptionErrorMessage}.
     */
    private FolderExceptionErrorMessage(final String message, final Category category, final int detailNumber) {
        this(message, category, detailNumber, null);
    }

    /**
     * Initializes a new {@link FolderExceptionErrorMessage}.
     */
    private FolderExceptionErrorMessage(final String message, final Category category, final int detailNumber, final String displayMessage) {
        this.message = message;
        this.detailNumber = detailNumber;
        this.category = category;
        this.displayMessage = displayMessage != null ? displayMessage : OXExceptionStrings.MESSAGE;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public Category getCategory() {
        return category;
    }

    @Override
    public int getNumber() {
        return detailNumber;
    }

    @Override
    public String getMessage() {
        return message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayMessage() {
        return this.displayMessage;
    }

    @Override
    public boolean equals(final OXException e) {
        return OXExceptionFactory.getInstance().equals(this, e);
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @return The newly created {@link OXException} instance
     */
    public OXException create() {
        return specials(OXExceptionFactory.getInstance().create(this, new Object[0]));
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(final Object... args) {
        return specials(OXExceptionFactory.getInstance().create(this, (Throwable) null, args));
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param cause The optional initial cause
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(final Throwable cause, final Object... args) {
        return specials(OXExceptionFactory.getInstance().create(this, cause, args));
    }

    private OXException specials(final OXException exc) {
        switch (this) {
            case NOT_FOUND:
                exc.setGeneric(Generic.NOT_FOUND);
                break;
            default:
                break;
        }

        if (exc.getCategories().contains(Category.CATEGORY_CONFLICT)) {
            exc.setGeneric(Generic.CONFLICT);
        }

        if (exc.getCategories().contains(Category.CATEGORY_PERMISSION_DENIED)) {
            exc.setGeneric(Generic.NO_PERMISSION);
        }
        return exc;
    }

}
