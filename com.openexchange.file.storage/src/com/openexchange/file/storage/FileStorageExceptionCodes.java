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

import com.openexchange.exception.Category;
import com.openexchange.exception.DisplayableOXExceptionCode;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionFactory;
import com.openexchange.exception.OXExceptionStrings;

/**
 * {@link FileStorageExceptionCodes} - Enumeration of all {@link OXException}s.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since Open-Xchange v6.18.2
 */
public enum FileStorageExceptionCodes implements DisplayableOXExceptionCode {

    /**
     * An error occurred: %1$s
     */
    UNEXPECTED_ERROR("An error occurred: %1$s", Category.CATEGORY_ERROR, 1),
    /**
     * A SQL error occurred: %1$s
     */
    SQL_ERROR("A SQL error occurred: %1$s", Category.CATEGORY_ERROR, 2, OXExceptionStrings.SQL_ERROR_MSG),
    /**
     * An I/O error occurred: %1$s
     */
    IO_ERROR("An I/O error occurred: %1$s", Category.CATEGORY_ERROR, 3),
    /**
     * %1$s protocol error occurred: %2$s
     */
    PROTOCOL_ERROR("%1$s protocol error occurred: %2$s", Category.CATEGORY_ERROR, 5),
    /**
     * A JSON occurred: %1$s
     */
    JSON_ERROR("A JSON error occurred: %1$s", Category.CATEGORY_ERROR, 14),
    /**
     * File storage account %1$s of service "%2$s" could not be found for user %3$s in context %4$s.
     */
    ACCOUNT_NOT_FOUND("File storage account %1$s of service \"%2$s\" could not be found for user %3$s in context %4$s.", Category.CATEGORY_USER_INPUT, 4, FileStorageExceptionMessages.ACCOUNT_NOT_FOUND_MSG),
    /**
     * The operation is not supported by service %1$s.
     */
    OPERATION_NOT_SUPPORTED("The operation is not supported by service %1$s.", Category.CATEGORY_ERROR, 6),
    /**
     * The folder "%1$s" cannot be found in account %2$s of service "%3$s" of user %4$s in context %5$s.
     */
    FOLDER_NOT_FOUND("The folder \"%1$s\" cannot be found in account %2$s of service \"%3$s\" of user %4$s in context %5$s.", Category.CATEGORY_ERROR, 7, FileStorageExceptionMessages.FOLDER_NOT_EXISTS_MSG),
    /**
     * Invalid file identifier: %1$s
     */
    INVALID_FILE_IDENTIFIER("Invalid file identifier: %1$s", Category.CATEGORY_ERROR, 8, FileStorageExceptionMessages.FILE_NOT_EXISTS_MSG),
    /**
     * Invalid header "%1$s": %2$s
     */
    INVALID_HEADER("Invalid header \"%1$s\": %2$s", Category.CATEGORY_ERROR, 9),
    /**
     * Unknown action to perform: %1$s.
     */
    UNKNOWN_ACTION("Unknown action to perform: %1$s.", Category.CATEGORY_ERROR, 10),
    /**
     * A file error occurred: %1$s
     */
    FILE_ERROR("A file error occurred: %1$s", Category.CATEGORY_ERROR, 11),
    /**
     * Wrongly formatted address: %1$s.
     */
    ADDRESS_ERROR("Wrongly formatted address: %1$s.", Category.CATEGORY_ERROR, 12),
    /**
     * Unknown file content: %1$s.
     */
    UNKNOWN_FILE_CONTENT("Unknown file content: %1$s.", Category.CATEGORY_ERROR, 15),
    /**
     * Unknown file storage service: %1$s.
     */
    UNKNOWN_FILE_STORAGE_SERVICE("Unknown file storage service: %1$s.", Category.CATEGORY_SERVICE_DOWN, 16),
    /**
     * Missing parameter: %1$s.
     */
    MISSING_PARAMETER("Missing parameter: %1$s.", Category.CATEGORY_USER_INPUT, 17),
    /**
     * Invalid parameter: %1$s with type '%2$s'. Expected '%3$s'.
     */
    INVALID_PARAMETER("Invalid parameter: %1$s with type '%2$s'. Expected '%3$s'.", Category.CATEGORY_USER_INPUT, 18),
    /**
     * File part is read-only: %1$s
     */
    READ_ONLY("File part is read-only: %1$s", Category.CATEGORY_USER_INPUT, 19),
    /**
     * Unknown color label index: %1$s
     */
    UNKNOWN_COLOR_LABEL("Unknown color label index: %1$s", Category.CATEGORY_USER_INPUT, 20),
    /**
     * A duplicate folder named "%1$s" already exists below parent folder "%2$s".
     */
    DUPLICATE_FOLDER("A duplicate folder named \"%1$s\" already exists below parent folder \"%2$s\".", Category.CATEGORY_ERROR, 21, FileStorageExceptionMessages.DUPLICATE_FOLDER_MSG),
    /**
     * No create access on folder %1$s.
     */
    NO_CREATE_ACCESS("No create access on folder %1$s.", Category.CATEGORY_PERMISSION_DENIED, 22, FileStorageExceptionMessages.NO_CREATE_ACCESS_MSG),
    /**
     * Not connected
     */
    NOT_CONNECTED("Not connected", Category.CATEGORY_PERMISSION_DENIED, 23),
    /**
     * Invalid sorting column. Cannot sort by %1$s.
     */
    INVALID_SORTING_COLUMN("Invalid sorting column. Cannot sort by %1$s.", Category.CATEGORY_USER_INPUT, 24),
    /**
     * No attachment found with section identifier %1$s in file %2$s in folder %3$s.
     */
    ATTACHMENT_NOT_FOUND("No attachment found with section identifier %1$s in file %2$s in folder %3$s.", Category.CATEGORY_USER_INPUT, 25, FileStorageExceptionMessages.FILE_NOT_EXISTS_MSG),
    /**
     * File %1$s not found in folder %2$s.
     */
    FILE_NOT_FOUND("File %1$s not found in folder %2$s.", Category.CATEGORY_USER_INPUT, 26, FileStorageExceptionMessages.FILE_NOT_EXISTS_MSG),
    /**
     * No account manager could be found for service: %1$s.
     */
    NO_ACCOUNT_MANAGER_FOR_SERVICE("No account manager could be found for service: %1$s.", Category.CATEGORY_ERROR, 27),
    /**
     * Invalid URL "%1$s". Error: %2$s.
     */
    INVALID_URL("Invalid URL \"%1$s\". Error: %2$s.", Category.CATEGORY_USER_INPUT, 28, FileStorageExceptionMessages.INVALID_URL_MSG),
    /**
     * No such folder.
     */
    NO_SUCH_FOLDER("No such folder.", Category.CATEGORY_USER_INPUT, 29, FileStorageExceptionMessages.FOLDER_NOT_EXISTS_MSG),
    /**
     * Authentication failed for login %1$s to server %2$s (service: %3$s).
     */
    LOGIN_FAILED("Authentication failed for login %1$s to server %2$s (service: %3$s).", Category.CATEGORY_CONFIGURATION, 30),
    /**
     * In order to accomplish the search, %1$d or more characters are required.
     */
    PATTERN_NEEDS_MORE_CHARACTERS(FileStorageExceptionMessages.PATTERN_NEEDS_MORE_CHARACTERS_MSG, CATEGORY_USER_INPUT, 602, FileStorageExceptionMessages.PATTERN_NEEDS_MORE_CHARACTERS_MSG),
    /**
     * Invalid folder identifier: %1$s
     */
    INVALID_FOLDER_IDENTIFIER("Invalid folder identifier: %1$s", Category.CATEGORY_ERROR, 31, FileStorageExceptionMessages.FOLDER_NOT_EXISTS_MSG),
    /**
     * The allowed quota is reached. Please delete some items in order to store new ones.
     */
    QUOTA_REACHED("The allowed Quota is reached", Category.CATEGORY_CAPACITY, 32, FileStorageExceptionMessages.QUOTA_REACHED_MSG),
    /**
     * Search term no supported: %1$s.
     */
    SEARCH_TERM_NOT_SUPPORTED("Search term no supported: %1$s.", Category.CATEGORY_ERROR, 33),
    /**
     * ZIP archive exceeds max. allowed size of %1$s
     */
    ARCHIVE_MAX_SIZE_EXCEEDED(" ZIP archive exceeds max. allowed size of %1$s", Category.CATEGORY_PERMISSION_DENIED, 34, FileStorageExceptionMessages.ARCHIVE_MAX_SIZE_EXCEEDED_MSG),
    /**
     * File storage account %1$s of service "%2$s" cannot be accessed for user %3$s in context %4$s.
     */
    ACCOUNT_NOT_ACCESSIBLE("File storage account %1$s of service \"%2$s\" cannot be accessed for user %3$s in context %4$s.", Category.CATEGORY_ERROR, 35, FileStorageExceptionMessages.ACCOUNT_NOT_ACCESSIBLE_MSG),
    /**
     * The folder \"%1$s\" cannot be moved to \"%2$s\".
     */
    FOLDER_MOVE_NOT_SUPPORTED("The folder \"%1$s\" cannot be moved to \"%2$s\".", Category.CATEGORY_USER_INPUT, 36, FileStorageExceptionMessages.FOLDER_MOVE_NOT_SUPPORTED_MSG),
    /**
     * The file \"%1$s\" cannot be moved to \"%2$s\".
     */
    FILE_MOVE_NOT_SUPPORTED("The file \"%1$s\" cannot be moved to \"%2$s\".", Category.CATEGORY_USER_INPUT, 37, FileStorageExceptionMessages.FILE_MOVE_NOT_SUPPORTED_MSG),
    /**
     * No administrative file access exists for service "%1$s"
     */
    ADMIN_FILE_ACCESS_NOT_AVAILABLE("No administrative file access exists for service \"%1$s\"", Category.CATEGORY_ERROR, 38),
    /**
     * File version %1$s of file %2$s not found in folder %3$s.
     */
    FILE_VERSION_NOT_FOUND("File version %1$s of file %2$s not found in folder %3$s.", Category.CATEGORY_USER_INPUT, 39, FileStorageExceptionMessages.FILE_VERSION_NOT_EXISTS_MSG),
    /**
     * The notes of the file \"%1$s\" in folder \"%2$s\" are lost when moving it into the account \"%3$s\".
     */
    LOSS_OF_NOTES("Metadata of file \"%4$s\" not supported in target folder \"%5$s\"", CATEGORY_WARNING, 40, FileStorageExceptionMessages.LOSS_OF_NOTES_MSG),
    /**
     * All previous versions of the file \"%1$s\" in folder \"%2$s\" are lost when moving it into the account \"%3$s\".
     */
    LOSS_OF_VERSIONS("Metadata of file \"%4$s\" not supported in target folder \"%5$s\"", CATEGORY_WARNING, 41, FileStorageExceptionMessages.LOSS_OF_VERSIONS_MSG),
    /**
     * The assigned categories of the file \"%1$s\" in folder \"%2$s\" are lost when moving it into the account \"%3$s\".
     */
    LOSS_OF_CATEGORIES("Metadata of file \"%4$s\" not supported in target folder \"%5$s\"", CATEGORY_WARNING, 42, FileStorageExceptionMessages.LOSS_OF_CATEGORIES_MSG),
    /**
     * The shared file \"%1$s\" in folder \"%2$s\" is no longer accessible by other users when moving it into the account \"%3$s\".
     */
    LOSS_OF_FILE_SHARES("Metadata of file \"%4$s\" not supported in target folder \"%5$s\"", CATEGORY_WARNING, 43, FileStorageExceptionMessages.LOSS_OF_FILE_SHARES_MSG),
    /**
     * The shared folder \"%1$s\" is no longer accessible by other users when moving it into the account \"%2$s\".
     */
    LOSS_OF_FOLDER_SHARES("Metadata of folder \"%3$s\" not supported in target folder \"%4$s\"", CATEGORY_WARNING, 44, FileStorageExceptionMessages.LOSS_OF_FOLDER_SHARES_MSG),
    /**
     * The file \"%1$s\" was not updated due to possible data loss. Please review the warnings for details. | File update aborted: %2$s
     */
    FILE_UPDATE_ABORTED("File update aborted: %2$s", Category.CATEGORY_CONFLICT, 45, FileStorageExceptionMessages.FILE_UPDATE_ABORTED_MSG),
    /**
     * Versioning not supported by '%1$s' file storage.
     */
    VERSIONING_NOT_SUPPORTED("Versioning not supported by '%1$s' file storage.", Category.CATEGORY_ERROR, 46, null),
    /**
     * The %1$s URL does not denote a file: %2$s
     */
    NOT_A_FILE("The %1$s URL does not denote a file: %2$s", CATEGORY_USER_INPUT, 47, FileStorageExceptionMessages.NOT_A_FILE_MSG),
    /**
     * The %1$s URL does not denote a directory: %2$s
     */
    NOT_A_FOLDER("The %1$s URL does not denote a directory: %2$s", CATEGORY_USER_INPUT, 48, FileStorageExceptionMessages.NOT_A_FOLDER_MSG),
    /**
     * Missing file name.
     */
    MISSING_FILE_NAME("Missing file name.", CATEGORY_USER_INPUT, 49, FileStorageExceptionMessages.MISSING_FILE_NAME_MSG),
    /**
     * Update denied for %1$s resource: %2$s
     */
    UPDATE_DENIED("Update denied for \"%1$s\" resource: %2$s", CATEGORY_USER_INPUT, 50, FileStorageExceptionMessages.UPDATE_DENIED_MSG),
    /**
     * Delete denied for \"%1$s\" resource: %2$s
     */
    DELETE_DENIED("Delete denied for \"%1$s\" resource: %2$s", CATEGORY_PERMISSION_DENIED, 51, FileStorageExceptionMessages.DELETE_DENIED_MSG),
    /**
     * Invalid property "%1$s". Should be "%2$s".
     */
    INVALID_PROPERTY("Invalid property \"%1$s\". Should be \"%2$s\".", CATEGORY_ERROR, 52, null),
    /**
     * Invalid \"%1$s\" property \"%2$s\".
     */
    INVALID_TYPE_PROPERTY("Invalid \"%1$s\" property \"%2$s\".", CATEGORY_ERROR, 53, null),
    /**
     * Missing configuration for %1$s account "%2$s".
     */
    MISSING_CONFIG("Missing configuration for %1$s account \"%2$s\".", Category.CATEGORY_CONFIGURATION, 54, FileStorageExceptionMessages.MISSING_CONFIG_MSG),
    /**
     * "The %1$s resource does not exist: %2$s"
     */
    NOT_FOUND("The %1$s resource does not exist: %2$s", Category.CATEGORY_ERROR, 55, FileStorageExceptionMessages.NOT_FOUND_MSG),
    /**
     * Authentication failed for the file storage account with identifier %1$s (service: %2$s): %3$s
     * Authentication failed. Please check the provided credentials and try again.
     */
    AUTHENTICATION_FAILED("Authentication failed for the file storage account with identifier %1$s (service: %2$s): %3$s", Category.CATEGORY_CONFIGURATION, 56, FileStorageExceptionMessages.AUTHENTICATION_FAILED_MSG),
    /**
     * The file %1$s doesn't have any content.
     */
    NO_CONTENT("The file %1$s doesn't have any content.", Category.CATEGORY_ERROR, 57, FileStorageExceptionMessages.NO_CONTENT_MSG),
    /**
     * Individual permissions are not supported in account \"%1$s\". | No permission support in account \"%1$s\" (folder \"%2$s\", context %3$s.)
     */
    NO_PERMISSION_SUPPORT("No permission support in account \"%1$s\" (folder \"%2$s\", context %3$s.)", Category.CATEGORY_ERROR, 58, FileStorageExceptionMessages.NO_PERMISSION_SUPPORT_MSG),
    /**
     * Saving notes for file \"%1$s\" is not supported in account \"%2$s\". | Metadata of file \"%3$s\" not supported in target folder \"%4$s\"
     */
    NO_NOTES_SUPPORT("Metadata of file \"%3$s\" not supported in target folder \"%4$s\"", CATEGORY_WARNING, 59, FileStorageExceptionMessages.NO_NOTES_SUPPORT_MSG),
    /**
     * Assigning categories to file \"%1$s\" is not supported in account \"%2$s\". | Metadata of file \"%3$s\" not supported in target folder \"%4$s\"
     */
    NO_CATEGORIES_SUPPORT("Metadata of file \"%3$s\" not supported in target folder \"%4$s\"", CATEGORY_WARNING, 60, FileStorageExceptionMessages.NO_CATEGORIES_SUPPORT_MSG),
    /**
     * The file \"%1$s\" was not updated due to possible data loss. Please review the warnings for details. | File save aborted: %2$s
     */
    FILE_SAVE_ABORTED("File save aborted: %2$s", Category.CATEGORY_CONFLICT, 61, FileStorageExceptionMessages.FILE_SAVE_ABORTED_MSG),
    /**
     * Invalid permissions (%1$d) for entity \"%2$d\" on object \"%3$s\".
     */
    INVALID_OBJECT_PERMISSIONS("Invalid permissions (%1$d) for entity (%2$d) on object %3$s.", Category.CATEGORY_PERMISSION_DENIED, 62),
    /**
     * Invalid permissions (%1$d) for entity \"%2$d\" on object \"%3$s\".
     */
    INVALID_OBJECT_PERMISSIONS_SIMPLE("The user does not have sufficient permissions for the entity specified in the query", Category.CATEGORY_PERMISSION_DENIED, 62, FileStorageExceptionMessages.INVALID_OBJECT_PERMISSIONS_SIMPLE_MSG),
    /**
     * The storage backend denied this request because of a exceeded rate limit.
     */
    STORAGE_RATE_LIMIT("The storage backend denied this request because of a exceeded rate limit.", Category.CATEGORY_CAPACITY, 63, FileStorageExceptionMessages.STORAGE_RATE_LIMIT_MSG),
    /**
     * Bad or expired access token. Need to re-authenticate user.
     */
    UNLINKED_ERROR("Bad or expired access token. Need to re-authenticate user.", Category.CATEGORY_USER_INPUT, 64, FileStorageExceptionMessages.UNLINKED_ERROR_MSG),

    /**
     * File name contains illegal characters: \"%1$s\"
     */
    ILLEGAL_CHARACTERS("File name contains illegal characters: \"%1$s\"", Category.CATEGORY_USER_INPUT, 65, FileStorageExceptionMessages.ILLEGAL_CHARACTERS_MSG),

    /**
     * File name is a reserved name: \"%1$s\"
     */
    RESERVED_NAME("File name is a reserved name: \"%1$s\"", Category.CATEGORY_USER_INPUT, 66, FileStorageExceptionMessages.RESERVED_NAME_MSG),

    /**
     * File name must not be <code>"."</code> or <code>".."</code>.
     */
    ONLY_DOTS_NAME("File name must not be \".\" or \"..\".", Category.CATEGORY_USER_INPUT, 67, FileStorageExceptionMessages.ONLY_DOTS_MSG),

    /**
     * File name must not end with a dot or whitespace.
     */
    WHITESPACE_END("File name must not end with a dot or whitespace.", Category.CATEGORY_USER_INPUT, 68, FileStorageExceptionMessages.WHITESPACE_END_MSG),

    /**
     * A file with that name already exists.
     */
    FILE_ALREADY_EXISTS("A file with that name already exists.", Category.CATEGORY_USER_INPUT, 69, FileStorageExceptionMessages.FILE_ALREADY_EXISTS),

    /**
     * The file(s) \"%1$s\" were not moved due to possible data loss. Please review the warnings for details.
     */
    FILE_MOVE_ABORTED("The file(s) \"%1$s\" were not moved due to possible data loss. Please review the warnings for details.", Category.CATEGORY_CONFLICT, 70, FileStorageExceptionMessages.FILE_MOVE_ABORTED_MSG),

    /**
     * The specified MIME type is not allowed being set
     */
    DENIED_MIME_TYPE("The specified MIME type is not allowed being set", Category.CATEGORY_USER_INPUT, 71, OXExceptionStrings.MESSAGE_DENIED),

    /**
     * Restore from trash is not supported
     */
    NO_RESTORE_SUPPORT("Restore from trash is not supported", Category.CATEGORY_ERROR, 72, FileStorageExceptionMessages.NO_RESTORE_SUPPORT_MSG),

    /**
     * Invalid account name
     */
    ACCOUNT_NAME_TOO_LONG("Invalid account name.", Category.CATEGORY_ERROR, 73, FileStorageExceptionMessages.ACCOUNT_NAME_TO_LONG_MSG),

    /**
     * With moving the file \"%1$s\" out of shared folder \"%2$s\" to shared folder \"%3$s\" people will lose access.
     */
    MOVE_TO_NOT_SHARED_WARNING("With moving the file \"%1$s\" out of shared folder \"%2$s\" to shared folder \"%3$s\" people will lose access.", CATEGORY_WARNING, 74, FileStorageExceptionMessages.MOVE_TO_NOT_SHARED_WARNING),

    /**
     * The file \"%1$s\" will be shared with everyone who has access to \"%3$s\". Everyone who can see \"%2$s\" will lose access.
     */
    MOVE_TO_ANOTHER_SHARED_WARNING("The file \"%1$s\" will be shared with everyone who has access to \"%3$s\". Everyone who can see \"%2$s\" will lose access.", CATEGORY_WARNING, 75, FileStorageExceptionMessages.MOVE_TO_ANOTHER_SHARED_WARNING),

    /**
     * The file \"%1$s\" will be shared with everyone who has access to \"%3$s\".
     */
    MOVE_TO_SHARED_WARNING("The file \"%1$s\" will be shared with everyone who has access to \"%3$s\".", CATEGORY_WARNING, 76, FileStorageExceptionMessages.MOVE_TO_SHARED_WARNING),

    /**
     * With moving file \"%1$s\" its permissions will get lost.
     */
    MOVE_SHARED_FILE_WARNING("With moving file \"%1$s\" its permissions will get lost.", CATEGORY_WARNING, 77, FileStorageExceptionMessages.MOVE_SHARED_FILE),

    /**
     * The account could not be updated: 1$s
     */
    UNALLOWED_ACCOUNT_UPDATE("The account could not be updated: 1$s", Category.CATEGORY_ERROR, 78, FileStorageExceptionMessages.UNALLOWED_ACCOUNT_UPDATE_MSG),

    /**
     * Searching folder by folder name is not supported
     */
    NO_SEARCH_SUPPORT("Searching folder by folder name is not supported", Category.CATEGORY_ERROR, 79, FileStorageExceptionMessages.NO_SEARCH_SUPPORT_MSG),

    /**
     * Maximum number of %1$d accounts for provider %2$s is exceeded.
     */
    MAX_ACCOUNTS_EXCEEDED("Maximum number of %1$d accounts for provider %2$s is exceeded.", Category.CATEGORY_ERROR, 80, FileStorageExceptionMessages.MAX_ACCOUNTS_EXCEEDED_MSG),

    ;

    /**
     * The prefix constant.
     */
    public static final String PREFIX = "FILE_STORAGE";

    private final Category category;

    private final int detailNumber;

    private final String message;

    private final String displayMessage;

    private FileStorageExceptionCodes(final String message, final Category category, final int detailNumber) {
        this(message, category, detailNumber, null);
    }

    private FileStorageExceptionCodes(final String message, final Category category, final int detailNumber, final String displayMessage) {
        this.message = message;
        this.detailNumber = detailNumber;
        this.category = category;
        this.displayMessage = displayMessage != null ? displayMessage : OXExceptionStrings.MESSAGE;
    }

    @Override
    public Category getCategory() {
        return category;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getNumber() {
        return detailNumber;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public String getDisplayMessage() {
        return displayMessage;
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
        return OXExceptionFactory.getInstance().create(this, new Object[0]);
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(final Object... args) {
        return OXExceptionFactory.getInstance().create(this, (Throwable) null, args);
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param cause The optional initial cause
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(final Throwable cause, final Object... args) {
        return OXExceptionFactory.getInstance().create(this, cause, args);
    }
}
