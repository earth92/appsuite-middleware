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

package com.openexchange.share.notification;

import com.openexchange.i18n.LocalizableStrings;


/**
 * Translatable Strings to compose share notification mails.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.8.0
 */
public class NotificationStrings implements LocalizableStrings {

    // subject
    /** $username has shared file "$filename" with you. */
    public static final String SUBJECT_SHARED_FILE = "%1$s has shared the file \"%2$s\" with you.";

    /** $username has shared $number_of_files files with you. */
    public static final String SUBJECT_SHARED_FILES = "%1$s has shared %2$s files with you.";

    /** $username has shared image "$filename" with you. */
    public static final String SUBJECT_SHARED_IMAGE = "%1$s has shared the image \"%2$s\" with you.";

    /** $username has shared $number_of_images images with you. */
    public static final String SUBJECT_SHARED_IMAGES = "%1$s has shared %2$s images with you.";

    /** $username has shared item "$filename" with you. */
    public static final String SUBJECT_SHARED_ITEM = "%1$s has shared the item \"%2$s\" with you.";

    /** $username has shared $number items with you. */
    public static final String SUBJECT_SHARED_ITEMS = "%1$s has shared %2$s items with you.";

    /** $username has shared folder "$folder" with you. */
    public static final String SUBJECT_SHARED_FOLDER = "%1$s has shared the folder \"%2$s\" with you.";

    /** $username has shared $number folders with you. */
    public static final String SUBJECT_SHARED_FOLDERS = "%1$s has shared %2$d folders with you.";

    /** $username has shared the calendar "$folder" with you. */
    public static final String SUBJECT_SHARED_CALENDAR = "%1$s has shared the calendar \"%2$s\" with you.";

    /** $username has shared $number calendars with you. */
    public static final String SUBJECT_SHARED_CALENDARS = "%1$s has shared %2$d calendars with you.";

    /** $username has shared file "$filename" with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_FILE_GROUP = "%1$s has shared the file \"%2$s\" with the group \"%3$s\".";

    /** $username has shared $number_of_files files with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_FILES_GROUP = "%1$s has shared %2$s files with the group \"%3$s\".";

    /** $username has shared image "$filename" with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_IMAGE_GROUP = "%1$s has shared the image \"%2$s\" with the group \"%3$s\".";

    /** $username has shared $number_of_images images with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_IMAGES_GROUP = "%1$s has shared %2$s images with the group \"%3$s\".";

    /** $username has shared item "$filename" with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_ITEM_GROUP = "%1$s has shared the item \"%2$s\" with the group \"%3$s\".";

    /** $username has shared $number items with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_ITEMS_GROUP = "%1$s has shared %2$s items with the group \"%3$s\".";

    /** $username has shared folder "$folder" with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_FOLDER_GROUP = "%1$s has shared the folder \"%2$s\" with the group \"%3$s\".";

    /** $username has shared $number folders with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_FOLDERS_GROUP = "%1$s has shared %2$d folders with the group \"%3$s\".";

    /** $username has shared calendar "$folder" with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_CALENDAR_GROUP = "%1$s has shared the calendar \"%2$s\" with the group \"%3$s\".";

    /** $username has shared $number calendars with the group "Sales Dept.". */
    public static final String SUBJECT_SHARED_CALENDARS_GROUP = "%1$s has shared %2$d calendars with the group \"%3$s\".";

    // detailed body
    /** $username ($user_email) has shared file "$filename" with you. Please click the button below to view it. */
    public static final String HAS_SHARED_FILE_NO_MESSAGE = "%1$s (%2$s) has shared the file \"%3$s\" with you. Please click the button below to view it.";

    /** $username ($user_email) has shared $number_of_files files with you. Please click the button below to view them. */
    public static final String HAS_SHARED_FILES_NO_MESSAGE = "%1$s (%2$s) has shared %3$s files with you. Please click the button below to view them.";

    /** $username ($user_email) has shared image "$filename" with you. Please click the button below to view it. */
    public static final String HAS_SHARED_IMAGE_NO_MESSAGE = "%1$s (%2$s) has shared the image \"%3$s\" with you. Please click the button below to view it.";

    /** $username ($user_email) has shared $number_of_images images with you. Please click the button below to view them. */
    public static final String HAS_SHARED_IMAGES_NO_IMAGES = "%1$s (%2$s) has shared %3$s images with you. Please click the button below to view them.";

    /** $username ($user_email) has shared item "$filename" with you. Please click the button below to view it. */
    public static final String HAS_SHARED_ITEM_NO_MESSAGE = "%1$s (%2$s) has shared the item \"%3$s\" with you. Please click the button below to view it.";

    /** $username ($user_email) has shared $number items with you. Please click the button below to view them. */
    public static final String HAS_SHARED_ITEMS_NO_MESSAGE = "%1$s (%2$s) has shared %3$s items with you. Please click the button below to view them.";

    /** $username ($user_email) has shared folder $folder with you. Please click the button below to view it. */
    public static final String HAS_SHARED_FOLDER_NO_MESSAGE = "%1$s (%2$s) has shared the folder \"%3$s\" with you. Please click the button below to view it.";

    /** $username ($user_email) has shared folder $number folders with you. Please click the button below to view them. */
    public static final String HAS_SHARED_FOLDERS_NO_MESSAGE = "%1$s (%2$s) has shared %3$d folders with you. Please click the button below to view them.";

    /** $username ($user_email) has shared calendar $folder with you. Please click the button below to view it. */
    public static final String HAS_SHARED_CALENDAR_NO_MESSAGE = "%1$s (%2$s) has shared the calendar \"%3$s\" with you. Please click the button below to view it.";

    /** $username ($user_email) has shared $number calendars with you. Please click the button below to view them. */
    public static final String HAS_SHARED_CALENDARS_NO_MESSAGE = "%1$s (%2$s) has shared %3$d calendars with you. Please click the button below to view them.";

    /** $username ($user_email) has shared file "$filename" with the group "Sales Dept.". Please click the button below to view it. */
    public static final String HAS_SHARED_FILE_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared the file \"%3$s\" with the group \"%4$s\". Please click the button below to view it.";

    /** $username ($user_email) has shared $number_of_files files with the group "Sales Dept.". Please click the button below to view them. */
    public static final String HAS_SHARED_FILES_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$s files with the group \"%4$s\". Please click the button below to view them.";

    /** $username ($user_email) has shared image "$filename" with the group "Sales Dept.". Please click the button below to view it. */
    public static final String HAS_SHARED_IMAGE_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared the image \"%3$s\" with the group \"%4$s\". Please click the button below to view it.";

    /** $username ($user_email) has shared $number_of_images images with the group "Sales Dept.". Please click the button below to view them. */
    public static final String HAS_SHARED_IMAGES_NO_IMAGES_GROUP = "%1$s (%2$s) has shared %3$s images with the group \"%4$s\". Please click the button below to view them.";

    /** $username ($user_email) has shared item "$filename" with the group "Sales Dept.". Please click the button below to view it. */
    public static final String HAS_SHARED_ITEM_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared the item \"%3$s\" with the group \"%4$s\". Please click the button below to view it.";

    /** $username ($user_email) has shared $number items with the group "Sales Dept.". Please click the button below to view them. */
    public static final String HAS_SHARED_ITEMS_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$s items with the group \"%4$s\". Please click the button below to view them.";

    /** $username ($user_email) has shared folder $folder with the group "Sales Dept.". Please click the button below to view it. */
    public static final String HAS_SHARED_FOLDER_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared the folder \"%3$s\" with the group \"%4$s\". Please click the button below to view it.";

    /** $username ($user_email) has shared folder $number folders with the group "Sales Dept.". Please click the button below to view them. */
    public static final String HAS_SHARED_FOLDERS_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$d folders with the group \"%4$s\". Please click the button below to view them.";

    /** $username ($user_email) has shared the calendar $folder with the group "Sales Dept.". Please click the button below to view it. */
    public static final String HAS_SHARED_CALENDAR_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared the calendar \"%3$s\" with the group \"%4$s\". Please click the button below to view it.";

    /** $username ($user_email) has shared $number calendars with the group "Sales Dept.". Please click the button below to view them. */
    public static final String HAS_SHARED_CALENDARS_NO_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$d calendars with the group \"%4$s\". Please click the button below to view them.";

    /** $username ($user_email) has shared file "$filename" with you and left you a message: */
    public static final String HAS_SHARED_FILE_AND_MESSAGE = "%1$s (%2$s) has shared the file \"%3$s\" with you and left you a message:";

    /** $username ($user_email) has shared $number_of_files files with you and left you a message: */
    public static final String HAS_SHARED_FILES_AND_MESSAGE = "%1$s (%2$s) has shared %3$s files with you and left you a message:";

    /** $username ($user_email) has shared photo "$filename" with you and left you a message: */
    public static final String HAS_SHARED_PHOTO_AND_MESSAGE = "%1$s (%2$s) has shared the image \"%3$s\" with you and left you a message:";

    /** $username ($user_email) has shared $number_of_images images with you and left you a message: */
    public static final String HAS_SHARED_IMAGES_AND_MESSAGE = "%1$s (%2$s) has shared %3$s images with you and left you a message:";

    /** $username ($user_email) has shared a folder with you and left you a message: */
    public static final String HAS_SHARED_FOLDER_AND_MESSAGE = "%1$s (%2$s) has shared the folder \"%3$s\" with you and left you a message:";

    /** $username ($user_email) has shared a calendar with you and left you a message: */
    public static final String HAS_SHARED_CALENDAR_AND_MESSAGE = "%1$s (%2$s) has shared the calendar \"%3$s\" with you and left you a message:";

    /** $username ($user_email) has shared item "$filename" with you. */
    public static final String HAS_SHARED_ITEM_AND_MESSAGE = "%1$s (%2$s) has shared the item \"%3$s\" with you and left you a message:";

    /** $username ($user_email) has shared $number_of_items items with you. */
    public static final String HAS_SHARED_ITEMS_AND_MESSAGE = "%1$s (%2$s) has shared %3$d items with you and left you a message:";

    /** $username ($user_email) has shared $number_of_folder items with you. */
    public static final String HAS_SHARED_FOLDERS_AND_MESSAGE = "%1$s (%2$s) has shared %3$d folders with you and left you a message:";

    /** $username ($user_email) has shared $number_of_folder calendars with you. */
    public static final String HAS_SHARED_CALENDARS_AND_MESSAGE = "%1$s (%2$s) has shared %3$d calendars with you and left you a message:";

    /** $username ($user_email) has shared file "$filename" with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_FILE_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared the file \"%3$s\" with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared $number_of_files files with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_FILES_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$s files with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared photo "$filename" with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_PHOTO_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared the image \"%3$s\" with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared $number_of_images images with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_IMAGES_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$s images with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared a folder with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_FOLDER_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared the folder \"%3$s\" with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared a calendar with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_CALENDAR_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared the calendar \"%3$s\" with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared item "$filename" with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_ITEM_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared the item \"%3$s\" with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared $number_of_items items with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_ITEMS_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$d items with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared $number_of_folder items with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_FOLDERS_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$d folders with the group \"%4$s\" and left a message:";

    /** $username ($user_email) has shared $number_of_folder calendars with the group "Sales Dept." and left a message: */
    public static final String HAS_SHARED_CALENDARS_AND_MESSAGE_GROUP = "%1$s (%2$s) has shared %3$d calendars with the group \"%4$s\" and left a message:";

    // button with label
    /** View image */
    public static final String VIEW_IMAGE = "View image";

    /** View images */
    public static final String VIEW_IMAGES = "View images";

    /** View file */
    public static final String VIEW_FILE = "View file";

    /** View files */
    public static final String VIEW_FILES = "View files";

    /** View folder */
    public static final String VIEW_FOLDER = "View folder";

    /** View folders */
    public static final String VIEW_FOLDERS = "View folders";

    /** View calendar */
    public static final String VIEW_CALENDAR = "View calendar";

    /** View calendars */
    public static final String VIEW_CALENDARS = "View calendars";

    /** View item */
    public static final String VIEW_ITEM = "View item";

    /** View items */
    public static final String VIEW_ITEMS = "View items";

    /** The link will expire on 01/01/16 **/
    public static final String LINK_EXPIRE = "The link will expire on %1$s";

    /** The link is password protected. To see it you must use the following password: kac7Nede **/
    public static final String USE_PASSWORD = "This link is password protected. You need the following password to open it:";

    /*
     * Password reset confirm mails
     */

    /**
     * OX App Suite - Forgot your password?
     */
    public static final String PWRC_SUBJECT = "%1$s - Forgot your password?";

    public static final String PWRC_GREETING = "Hello,";

    /** we got informed that you forgot your password. You can set a new password here: */
    public static final String PWRC_REQUESTRECEIVED = "we got informed that you forgot your password. You can set a new password here:";

    /** Set password */
    public static final String PWRC_LINK_LABEL = "Set password";

    /** If you didn't request this, please ignore this email. Your password won't change until you access the button above and set a new one. */
    public static final String PWRC_IGNORE = "If you didn't request this, please ignore this email. Your password won't change until you access the button above and set a new one.";

    /** This is an automated message, please do not reply. */
    public static final String PWRC_AUTOMATED_MAIL = "This is an automated message, please do not reply.";

    // An error occurred for user 'unknown'
    public static final String UNKNOWN_USER_NAME = "unknown";

    /*
     * Drive Mail notifications
     */
    /** and %1$d more files. */
    public static final String DRIVE_MAIL_MORE_FILES = "and %1$d more files.";

    /** and 1 more file. */
    public static final String DRIVE_MAIL_ONE_MORE_FILE = "and 1 more file.";

}

