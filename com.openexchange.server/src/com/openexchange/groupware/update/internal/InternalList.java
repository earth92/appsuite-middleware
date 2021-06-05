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

package com.openexchange.groupware.update.internal;

import java.util.ArrayList;
import java.util.List;
import com.openexchange.groupware.update.UpdateTaskAdapter;
import com.openexchange.groupware.update.UpdateTaskV2;
import com.openexchange.groupware.update.tasks.AddOAuthColumnToMailAccountTableTask;
import com.openexchange.groupware.update.tasks.AddSharedParentFolderToFolderPermissionTableUpdateTask;
import com.openexchange.groupware.update.tasks.AddSnippetAttachmentPrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.AddStartTLSColumnForMailAccountTablesTask;
import com.openexchange.groupware.update.tasks.AddTypeToFolderPermissionTableUpdateTask;
import com.openexchange.groupware.update.tasks.AddUUIDForDListTables;
import com.openexchange.groupware.update.tasks.AddUUIDForInfostoreReservedPaths;
import com.openexchange.groupware.update.tasks.AddUUIDForUpdateTaskTable;
import com.openexchange.groupware.update.tasks.AddUUIDForUserAttributeTable;
import com.openexchange.groupware.update.tasks.AllowNullValuesForStandardFolderNamesUpdateTask;
import com.openexchange.groupware.update.tasks.AllowTextInValuesOfDynamicContextAttributesTask;
import com.openexchange.groupware.update.tasks.AllowTextInValuesOfDynamicUserAttributesTask;
import com.openexchange.groupware.update.tasks.CorrectAttachmentCountInAppointments;
import com.openexchange.groupware.update.tasks.CorrectFileAsInContacts;
import com.openexchange.groupware.update.tasks.CorrectOrganizerInAppointments;
import com.openexchange.groupware.update.tasks.CreateIndexOnContextAttributesTask;
import com.openexchange.groupware.update.tasks.CreateIndexOnUserAttributesForAliasLookupTask;
import com.openexchange.groupware.update.tasks.DateExternalCreateForeignKeyUpdateTask;
import com.openexchange.groupware.update.tasks.DateExternalDropForeignKeyUpdateTask;
import com.openexchange.groupware.update.tasks.DelDateExternalCreateForeignKeyUpdateTask;
import com.openexchange.groupware.update.tasks.DelDateExternalDropForeignKeyUpdateTask;
import com.openexchange.groupware.update.tasks.DelDatesMembersPrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.DelDatesPrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.DelInfostorePrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.DropDuplicateEntryFromUpdateTaskTable;
import com.openexchange.groupware.update.tasks.DropVersionTableTask;
import com.openexchange.groupware.update.tasks.GenconfAttributesBoolsAddPrimaryKey;
import com.openexchange.groupware.update.tasks.GenconfAttributesBoolsAddUuidUpdateTask;
import com.openexchange.groupware.update.tasks.GenconfAttributesStringsAddPrimaryKey;
import com.openexchange.groupware.update.tasks.GenconfAttributesStringsAddUuidUpdateTask;
import com.openexchange.groupware.update.tasks.InfostoreClearDelTablesTask;
import com.openexchange.groupware.update.tasks.InfostoreDocumentCreateForeignKeyUpdateTask;
import com.openexchange.groupware.update.tasks.InfostoreDocumentDropForeignKeyUpdateTask;
import com.openexchange.groupware.update.tasks.InfostorePrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.MailAccountAddReplyToTask;
import com.openexchange.groupware.update.tasks.MakeFolderIdPrimaryForDelContactsTable;
import com.openexchange.groupware.update.tasks.MakeUUIDPrimaryForDListTables;
import com.openexchange.groupware.update.tasks.MakeUUIDPrimaryForDListTablesV2;
import com.openexchange.groupware.update.tasks.MakeUUIDPrimaryForInfostoreReservedPaths;
import com.openexchange.groupware.update.tasks.MakeUUIDPrimaryForUpdateTaskTable;
import com.openexchange.groupware.update.tasks.MakeUUIDPrimaryForUserAttributeTable;
import com.openexchange.groupware.update.tasks.MigrateUUIDsForUserAliasTable;
import com.openexchange.groupware.update.tasks.PrgDatesMembersPrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.PrgDatesPrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.PrgLinksAddPrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.PrgLinksAddUuidUpdateTask;
import com.openexchange.groupware.update.tasks.Release781UpdateTask;
import com.openexchange.groupware.update.tasks.RemoveAliasInUserAttributesTable;
import com.openexchange.groupware.update.tasks.RemoveRedundantKeysForBug26913UpdateTask;
import com.openexchange.groupware.update.tasks.ResourceClearDelTablesTask;
import com.openexchange.groupware.update.tasks.UnsupportedSubscriptionsRemoverTask;
import com.openexchange.groupware.update.tasks.UserClearDelTablesTask;
import com.openexchange.groupware.update.tasks.UserSettingServerAddPrimaryKeyUpdateTask;
import com.openexchange.groupware.update.tasks.UserSettingServerAddUuidUpdateTask;
import com.openexchange.groupware.update.tasks.VirtualFolderAddSortNumTask;
import com.openexchange.groupware.update.tasks.objectusagecount.CreateObjectUseCountTableTask;
import com.openexchange.groupware.update.tasks.objectusagecount.CreatePrincipalUseCountTableTask;
import com.openexchange.tools.oxfolder.RemoveInconsistentLocksUpdateTasks;

/**
 * Lists all update tasks of the com.openexchange.server bundle.
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public final class InternalList {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(InternalList.class);

    private static final InternalList SINGLETON = new InternalList();

    /**
     * Gets the {@link InternalList} instance
     *
     * @return The instance
     */
    public static final InternalList getInstance() {
        return SINGLETON;
    }

    private InternalList() {
        super();
    }

    /**
     * Starts the internal list.
     */
    public void start() {
        final DynamicSet registry = DynamicSet.getInstance();
        TASKS = genTaskList();
        for (final UpdateTaskV2 task : TASKS) {
            if (!registry.addUpdateTask(task)) {
                LOG.error("Internal update task \"{}\" could not be registered.", task.getClass().getName(), new Exception());
            }
        }
    }

    /**
     * Stops the internal list.
     */
    public void stop() {
        final DynamicSet registry = DynamicSet.getInstance();
        for (final UpdateTaskV2 task : TASKS) {
            registry.removeUpdateTask(task);
        }
    }

    /**
     * All this tasks should extend {@link UpdateTaskAdapter} to fulfill the prerequisites to be sorted among their dependencies.
     */
    private static UpdateTaskV2[] TASKS = null;

    private static UpdateTaskV2[] genTaskList() {
        List<UpdateTaskV2> list = new ArrayList<>(256);

        // Renames "Unified INBOX" to "Unified Mail"
        list.add(new com.openexchange.groupware.update.tasks.UnifiedINBOXRenamerTask());

        // Creates necessary tables for mail header cache
        list.add(new com.openexchange.groupware.update.tasks.HeaderCacheCreateTableTask());

        // Extends the calendar tables and creates table to store the confirmation data for external participants.
        list.add(new com.openexchange.groupware.update.tasks.ExtendCalendarForIMIPHandlingTask());

        // Enables the bit for the contact collector feature for every user because that bit was not checked before 6.16.
        list.add(new com.openexchange.groupware.update.tasks.ContactCollectorReEnabler());

        // +++++++++++++++++++++++++++++++++ Version 6.18 starts here. +++++++++++++++++++++++++++++++++

        // Adds a column to the table user_setting_server named folderTree to store the selected folder tree.
        list.add(new com.openexchange.groupware.update.tasks.FolderTreeSelectionTask());

        // Repairs appointments where the number of attachments does not match the real amount of attachments.
        list.add(new com.openexchange.groupware.update.tasks.AttachmentCountUpdateTask());

        // Creates an initial empty filestore usage entry for every context that currently did not uploaded anything.
        list.add(new com.openexchange.groupware.update.tasks.AddInitialFilestoreUsage());

        // Currently users contacts are created with the display name attribute filed. Outlook primarily uses the fileAs attribute. This
        // task copies the display name to fileAs if that is empty.
        list.add(new com.openexchange.groupware.update.tasks.AddFileAsForUserContacts());

        // Extend field "reason" for participants.
        list.add(new com.openexchange.groupware.update.tasks.ParticipantCommentFieldLength());

        // New table for linking several appointments (from different sources) together that represent the same person.
        list.add(new com.openexchange.groupware.update.tasks.AggregatingContactTableService());

        // Creates new table for multi-purpose ID generation
        list.add(new com.openexchange.groupware.update.tasks.IDCreateTableTask());

        // TODO: Enable virtual folder tree update task when needed
        // Migrates existing folder data to new outlook-like folder tree structure
        // new com.openexchange.folderstorage.virtual.VirtualTreeMigrationTask()

        // +++++++++++++++++++++++++++++++++ Version 6.20 starts here. +++++++++++++++++++++++++++++++++

        // Transforms the "info" field to a TEXT field. This fields seems not to be used anywhere.
        list.add(new com.openexchange.groupware.update.tasks.ContactInfoField2Text());

        // Creates new Contact fields (First Name); Last Name); Company) for Kana based search in japanese environments.
        list.add(new com.openexchange.groupware.update.tasks.ContactFieldsForJapaneseKanaSearch());

        // Remove linkedin subscriptions to force use of new oauth
        list.add(new com.openexchange.groupware.update.tasks.LinkedInCrawlerSubscriptionsRemoverTask());

        // Remove yahoo subscriptions to force use of new oauth
        list.add(new com.openexchange.groupware.update.tasks.DeleteOldYahooSubscriptions());

        // Switch the column type of 'value' in contextAttribute to TEXT
        list.add(new AllowTextInValuesOfDynamicContextAttributesTask());

        // Switch the column type of 'value' in user_attribute to TEXT
        list.add(new AllowTextInValuesOfDynamicUserAttributesTask());

        // Recreate the index on the context attributes table
        list.add(new CreateIndexOnContextAttributesTask());

        // Recreate the index on the user attributes table for alias lookup
        list.add(new CreateIndexOnUserAttributesForAliasLookupTask());

        // Correct the attachment count in the dates table
        list.add(new CorrectAttachmentCountInAppointments());

        // Corrects the organizer in appointments. When exporting iCal and importing it again the organizer gets value 'null' instead of SQL
        // NULL. This task corrects this.
        list.add(new CorrectOrganizerInAppointments());

        // Corrects field90 aka fileAs in contacts to have proper contact names in card view of Outlook OXtender 2.
        list.add(new CorrectFileAsInContacts());

        // Add "sortNum" column to virtual folder table.
        list.add(new VirtualFolderAddSortNumTask());

        // +++++++++++++++++++++++++++++++++ Version 6.20.1 starts here. +++++++++++++++++++++++++++++++++

        // Restores the initial permissions on the public root folder.
        list.add(new com.openexchange.groupware.update.tasks.DropIndividualUserPermissionsOnPublicFolderTask());

        // Adds Outlook address fields to contact tables
        list.add(new com.openexchange.groupware.update.tasks.ContactAddOutlookAddressFieldsTask());

        // Add UID field to contact tables.
        list.add(new com.openexchange.groupware.update.tasks.ContactAddUIDFieldTask());

        // Add UIDs to contacts if missing
        list.add(new com.openexchange.groupware.update.tasks.ContactAddUIDValueTask());

        // Adds UIDs to tasks.
        list.add(new com.openexchange.groupware.update.tasks.TasksAddUidColumnTask());

        // Adds UID indexes.
        list.add(new com.openexchange.groupware.update.tasks.CalendarAddUIDIndexTask());

        // Drops rather needless foreign keys
        list.add(new com.openexchange.groupware.update.tasks.DropFKTask());

        // Adds 'organizerId'); 'principal' and 'principalId' to prg_dates and del_dates
        list.add(new com.openexchange.groupware.update.tasks.AppointmentAddOrganizerIdPrincipalPrincipalIdColumnsTask());

        // Adds index to prg_dates_members and del_dates_members
        list.add(new com.openexchange.groupware.update.tasks.CalendarAddIndex2DatesMembers());

        // Adds index to oxfolder_tree and del_oxfolder_tree
        list.add(new com.openexchange.groupware.update.tasks.FolderAddIndex2LastModified());

        // Checks for missing folder 'public_infostore' (15) in any available context
        list.add(new com.openexchange.groupware.update.tasks.CheckForPublicInfostoreFolderTask());

        // Drops useless foreign keys from 'malPollHash' table
        list.add(new com.openexchange.groupware.update.tasks.MALPollDropConstraintsTask());

        // +++++++++++++++++++++++++++++++++ Version 6.20.3 starts here. +++++++++++++++++++++++++++++++++

        // Extends dn fields in calendar tables to 320 chars.
        list.add(new com.openexchange.groupware.update.tasks.CalendarExtendDNColumnTaskV2());

        // +++++++++++++++++++++++++++++++++ Version 6.20.5 starts here. +++++++++++++++++++++++++++++++++

        // Creates indexes on tables "prg_dlist" and "del_dlist" to improve look-up.
        list.add(new com.openexchange.groupware.update.tasks.DListAddIndexForLookup());

        // +++++++++++++++++++++++++++++++++ Version 6.20.7 starts here. +++++++++++++++++++++++++++++++++

        // Another attempt: Adds 'organizerId'); 'principal' and 'principalId' to prg_dates and del_dates
        list.add(new com.openexchange.groupware.update.tasks.AppointmentAddOrganizerIdPrincipalPrincipalIdColumnsTask2());

        // Add UIDs to appointments if missing.
        list.add(new com.openexchange.groupware.update.tasks.CalendarAddUIDValueTask());

        // +++++++++++++++++++++++++++++++++ Version 6.22.0 starts here. +++++++++++++++++++++++++++++++++

        // Add "replyTo" column to mail/transport account table
        list.add(new MailAccountAddReplyToTask());

        // Migrate "replyTo" information from properties table to account tables
        list.add(new com.openexchange.groupware.update.tasks.MailAccountMigrateReplyToTask());

        // Add 'filename' column to appointment tables.
        list.add(new com.openexchange.groupware.update.tasks.AppointmentAddFilenameColumnTask());

        // Add 'filename' column to task tables.
        list.add(new com.openexchange.groupware.update.tasks.TasksAddFilenameColumnTask());

        // Removes unnecessary indexes from certain tables (see Bug #21882)
        list.add(new com.openexchange.groupware.update.tasks.RemoveUnnecessaryIndexes());
        list.add(new com.openexchange.groupware.update.tasks.RemoveUnnecessaryIndexes2());

        // +++++++++++++++++++++++++++++++++ Version 6.22.1 starts here. +++++++++++++++++++++++++++++++++
        list.add(new com.openexchange.groupware.update.tasks.ContactFixUserDistListReferencesTask());

        // +++++++++++++++++++++++++++++++++ Version 7.0.0 starts here. +++++++++++++++++++++++++++++++++

        // Extends the resources' description field
        list.add(new com.openexchange.groupware.update.tasks.EnlargeResourceDescription());

        // Extends the UID field
        list.add(new com.openexchange.groupware.update.tasks.EnlargeCalendarUid());

        // Sets the changing date once for users with a different defaultSendAddress
        list.add(new com.openexchange.groupware.update.tasks.ContactAdjustLastModifiedForChangedSenderAddress());

        // Drop foreign key constraints from obsolete tables
        list.add(new com.openexchange.groupware.update.tasks.HeaderCacheDropFKTask());

        // +++++++++++++++++++++++++++++++++ Version 7.4.0 starts here. +++++++++++++++++++++++++++++++++

        // Add UUID column to genconf_attributes_strings table
        list.add(new GenconfAttributesStringsAddUuidUpdateTask());

        // Add UUID column to genconf_attributes_bools table
        list.add(new GenconfAttributesBoolsAddUuidUpdateTask());

        // Add UUID column to updatTask table
        list.add(new AddUUIDForUpdateTaskTable());

        //Add Uuid column to prg_links table
        list.add(new PrgLinksAddUuidUpdateTask());

        //Add Uuid column to dlist tables
        list.add(new AddUUIDForDListTables());

        // Add UUID column to user_attribute table
        list.add(new AddUUIDForUserAttributeTable());

        //Add UUID column to infostoreReservedPaths table
        list.add(new AddUUIDForInfostoreReservedPaths());

        //Add UUID column to user_setting_server table
        list.add(new UserSettingServerAddUuidUpdateTask());

        //Drop foreign key from infostore_document table
        list.add(new InfostoreDocumentDropForeignKeyUpdateTask());

        //Add folder_id to primary key in infostore table
        list.add(new InfostorePrimaryKeyUpdateTask());

        //Add foreign key to infostore_document_table
        list.add(new InfostoreDocumentCreateForeignKeyUpdateTask());

        //Add folder_id to primary key in del_infostore table
        list.add(new DelInfostorePrimaryKeyUpdateTask());

        //Drop foreign key from dateExternal table
        list.add(new DateExternalDropForeignKeyUpdateTask());

        //Add folder_id to primary key in prg_dates
        list.add(new PrgDatesPrimaryKeyUpdateTask());

        //Create foreign key in dateExternal table
        list.add(new DateExternalCreateForeignKeyUpdateTask());

        //Drop foreign key from delDateExternal table
        list.add(new DelDateExternalDropForeignKeyUpdateTask());

        //Add folder_id to primary key in del_dates
        list.add(new DelDatesPrimaryKeyUpdateTask());

        //Create foreign key in delDateExternal table
        list.add(new DelDateExternalCreateForeignKeyUpdateTask());

        // Add folder_id to primary key in del_contacts
        list.add(new MakeFolderIdPrimaryForDelContactsTable());

        // Remove redundant keys (see bug 26913)
        list.add(new RemoveRedundantKeysForBug26913UpdateTask());

        // Add synthetic primary keys to tables without natural key if full primary key support is enabled
        {

            // Add primary key to genconf_attributes_strings table
            list.add(new GenconfAttributesStringsAddPrimaryKey());

            // Add primary key to genconf_attributes_bools table
            list.add(new GenconfAttributesBoolsAddPrimaryKey());

            // Add primary key to updateTask table
            list.add(new DropDuplicateEntryFromUpdateTaskTable());
            list.add(new MakeUUIDPrimaryForUpdateTaskTable());

            // Add primary key to user_attribute table
            list.add(new MakeUUIDPrimaryForUserAttributeTable());

            //Add primary key to prg_links table
            list.add(new PrgLinksAddPrimaryKeyUpdateTask());

            //Add primary key to dlist tables
            list.add(new MakeUUIDPrimaryForDListTables());

            //Add primary key to infostoreReservedPaths table;
            list.add(new MakeUUIDPrimaryForInfostoreReservedPaths());

            //Add primary key to user_setting_server table
            list.add(new UserSettingServerAddPrimaryKeyUpdateTask());

            //Add folder_id to primary key in prg_dates_members
            list.add(new PrgDatesMembersPrimaryKeyUpdateTask());

            //Add folder_id to primary key in del_dates_members
            list.add(new DelDatesMembersPrimaryKeyUpdateTask());

        }

        // Adds "archive" and "archive_fullname" columns to mail/transport account table
        list.add(new com.openexchange.groupware.update.tasks.MailAccountAddArchiveTask());

        // +++++++++++++++++++++++++++++++++ Version 7.4.1 starts here. +++++++++++++++++++++++++++++++++

        // Removes obsolete data from the 'del_contacts', 'del_dlist' and 'del_contacts_image' tables
        list.add(new com.openexchange.groupware.update.tasks.ContactClearDelTablesTasks());

        // Removes obsolete data from the 'del_task' table
        list.add(new com.openexchange.groupware.update.tasks.TaskClearDelTablesTasks());

        // Removes obsolete data from the 'del_dates' table
        list.add(new com.openexchange.groupware.update.tasks.AppointmentClearDelTablesTasks());

        // Removes obsolete data from the 'del_user' table
        list.add(new UserClearDelTablesTask());

        // Removes obsolete data from the 'del_resource' table
        list.add(new ResourceClearDelTablesTask());

        // Removes obsolete data from the 'del_infostore_document' table
        list.add(new InfostoreClearDelTablesTask());

        // Removes obsolete data from the 'del_oxfolder_tree', and 'virtualBackupTree' tables
        list.add(new com.openexchange.groupware.update.tasks.FolderClearDelTablesTasks());

        // Adds default values to the 'del_oxfolder_tree', and 'virtualBackupTree' tables.
        list.add(new com.openexchange.groupware.update.tasks.FolderDefaultValuesForDelTablesTasks());

        // Extends the sizes of the 'filename', 'title' and 'file_size' columns in the 'infostore_document' table
        list.add(new com.openexchange.groupware.update.tasks.InfostoreExtendFilenameTitleAndFilesizeTask());

        // Extends the size of the 'name' column in the 'infostoreReservedPaths' table.
        list.add(new com.openexchange.groupware.update.tasks.InfostoreExtendReservedPathsNameTask());

        // +++++++++++++++++++++++++++++++++ Version 7.4.2 starts here. +++++++++++++++++++++++++++++++++

        // Extends the size of the 'fname' column in the 'oxfolder_tree' table, as well as the 'name' column in the 'virtualTree' table.
        list.add(new com.openexchange.groupware.update.tasks.FolderExtendNameTask());

        // Extende folder tables by "meta" JSON BLOB
        list.add(new com.openexchange.groupware.update.tasks.AddMetaForOXFolderTable());

        // Add primary key to snippetAttachment table, fix for bug 30293
        list.add(new AddSnippetAttachmentPrimaryKeyUpdateTask());

        // Performs several adjustments to DB schema to get aligned to clean v7.4.1 installation
        list.add(new com.openexchange.groupware.update.tasks.DropFKTaskv2());

        // Adds/corrects user mail index: INDEX (mail) -> INDEX (cid, mail(255))
        list.add(new com.openexchange.groupware.update.tasks.UserAddMailIndexTask());

        // +++++++++++++++++++++++++++++++++ Version 7.6.0 starts here. +++++++++++++++++++++++++++++++++

        // Extends infostore document tables by "meta" JSON BLOB.
        list.add(new com.openexchange.groupware.update.tasks.AddMetaForInfostoreDocumentTable());

        // Extends infostore document tables by the (`cid`, `file_md5sum`) index.
        list.add(new com.openexchange.groupware.update.tasks.AddMD5SumIndexForInfostoreDocumentTable());

        // Adds (cid,changing_date) index to calendar tables if missing
        list.add(new com.openexchange.groupware.update.tasks.CalendarAddChangingDateIndexTask());

        // Checks and drops obsolete tables possibly created for managing POP3 accounts
        list.add(new com.openexchange.groupware.update.tasks.POP3CheckAndDropObsoleteTablesTask());

        // Ensures that each folder located below a user's default infostore trash folder is of type 16
        list.add(new com.openexchange.groupware.update.tasks.FolderInheritTrashFolderTypeTask());

        // +++++++++++++++++++++++++++++++++ Version 7.6.1 starts here. +++++++++++++++++++++++++++++++++

        // Removes invalid priority values from tasks
        list.add(new com.openexchange.groupware.update.tasks.TasksDeleteInvalidPriorityTask());

        // Corrects values in the 'changing_date' column that are set to {@link Long#MAX_VALUE}.
        list.add(new com.openexchange.groupware.update.tasks.FolderCorrectChangingDateTask());

        // (Re-)adds indexes in prg_contacts for "auto-complete" queries
        list.add(new com.openexchange.groupware.update.tasks.ContactsAddIndex4AutoCompleteSearchV2());

        // Check if foreign keys in date tables are dropped and drop them if necessary
        list.add(new com.openexchange.groupware.update.tasks.CheckAndDropDateExternalForeignKeysUpdateTask());

        // Adds the 'full_time' column to the tasks tables
        list.add(new com.openexchange.groupware.update.tasks.TasksAddFulltimeColumnTask());

        // Check for possibly preset message format preference in JSLob and aligns the DB value accordingly
        list.add(new com.openexchange.groupware.update.tasks.CheckForPresetMessageFormatInJSLob());

        // +++++++++++++++++++++++++++++++++ Version 7.8.0 starts here. +++++++++++++++++++++++++++++++++

        // Adds permissions to system- and root-folders for the virtual guest group.
        list.add(new com.openexchange.groupware.update.tasks.FolderPermissionAddGuestGroup());

        // Adds the column 'guestCreatedBy' to the tables 'user' and 'del_user'
        list.add(new com.openexchange.groupware.update.tasks.UserAddGuestCreatedByTask());

        // Create table for object permissions
        list.add(new com.openexchange.groupware.update.tasks.objectpermission.ObjectPermissionCreateTableTask());

        // Extends "user" table by the (`cid`, `guestCreatedBy`) index
        list.add(new com.openexchange.groupware.update.tasks.AddGuestCreatedByIndexForUserTable());

        // Drop redundant indices
        list.add(new com.openexchange.groupware.update.tasks.DropRendundantIndicesUpdateTask());

        // Migrates the user aliases from the user_attribute table to the user_alias table; but does not delete the entries in the user_attribute table.
        list.add(new com.openexchange.groupware.update.tasks.MigrateAliasUpdateTask());

        // Grants "read all" permissions for the user infostore folder
        list.add(new com.openexchange.groupware.update.tasks.FolderPermissionReadAllForUserInfostore());

        // Add vCardId column if missing
        list.add(new com.openexchange.groupware.update.tasks.ContactAddVCardIdTask());

        // Add vCardId column for del table if missing
        list.add(new com.openexchange.groupware.update.tasks.ContactAddVCardIdToDelTask());

        // Remove accounts related to facebook
        list.add(new com.openexchange.groupware.update.tasks.RemoveFacebookAccountsTask());

        // Add primary key to dlist tables
        list.add(new MakeUUIDPrimaryForDListTablesV2());

        // Creates indexes on tables "prg_contacts" and "del_contacts" to improve auto-complete
        list.add(new com.openexchange.groupware.update.tasks.CalendarAddIndex2DatesMembersV2());

        // +++++++++++++++++++++++++++++++++ Version 7.8.1 starts here. +++++++++++++++++++++++++++++++++

        list.add(new DropVersionTableTask());

        // Checks if the 'uuid' column exists in the 'user_alias' table. If absent, adds the column and migrates all UUIDs for each alias entry
        list.add(new MigrateUUIDsForUserAliasTable());

        // Removes the aliases from the user attributes table. They are stored in the table `user_alias` with version 7.8.0
        list.add(new RemoveAliasInUserAttributesTable());

        // Create object_use_count table
        list.add(new CreateObjectUseCountTableTask());

        // Re-executes PrgLinksAddPrimaryKeyUpdateTask --> Adds primary key to `prg_links` table
        list.add(new com.openexchange.groupware.update.tasks.PrgLinksAddPrimaryKeyUpdateTaskV2());

        // Corrects values in the 'created_from' column for folders nested below/underneath personal 'Trash' folder
        list.add(new com.openexchange.groupware.update.tasks.FolderCorrectOwnerTask());

        // Checks and drops obsolete tables possibly created for managing POP3 accounts
        list.add(new com.openexchange.groupware.update.tasks.POP3CheckAndDropObsoleteTablesTaskV2());

        // (Re-)adds department index in prg_contacts for "auto-complete" queries
        list.add(new com.openexchange.groupware.update.tasks.ContactsAddDepartmentIndex4AutoCompleteSearch());

        // +++++++++++++++++++++++++++++++++ Version 7.8.2 starts here. +++++++++++++++++++++++++++++++++

        list.add(new Release781UpdateTask());

        // Adds "starttls" column to "user_mail_account" and "user_transport_account" tables and attempts to set a reasonable default value for that column dependent on mail account data
        list.add(new AddStartTLSColumnForMailAccountTablesTask());

        // Applies MEDIUM TEXT to "user_setting" table.
        list.add(new com.openexchange.groupware.update.tasks.UserSettingMediumTextTask());

        // +++++++++++++++++++++++++++++++++ Version 7.8.3 starts here. +++++++++++++++++++++++++++++++++

        list.add(new AllowNullValuesForStandardFolderNamesUpdateTask());

        //Adds "oauth" column to account tables
        list.add(new AddOAuthColumnToMailAccountTableTask());

        // Removes inconsistent locks (See Bug #47929)
        list.add(new RemoveInconsistentLocksUpdateTasks());

        // Extends the "password" column for "user_mail_account" and "user_transport_account" tables
        list.add(new com.openexchange.groupware.update.tasks.MailAccountExtendPasswordTask());

        // +++++++++++++++++++++++++++++++++ Version 7.8.4 starts here. +++++++++++++++++++++++++++++++++

        list.add(new com.openexchange.groupware.update.tasks.ChangePrimaryKeyForUserAttribute());

        // Drops rather needless foreign keys from tables.
        list.add(new com.openexchange.groupware.update.tasks.DropFKTaskv3());

        // Adds several columns to "user_mail_account" and "user_transport_account" tables for tracking/managing failed authentication attempts.
        list.add(new com.openexchange.groupware.update.tasks.AddFailedAuthColumnsToMailAccountTablesTask());

        // +++++++++++++++++++++++++++++++++ Version 7.10.0 starts here. +++++++++++++++++++++++++++++++++

        // Extends uidl column of the pop3_storage_ids and pop3_storage_deleted tables
        list.add(new com.openexchange.groupware.update.tasks.POP3ExtendUidlTask());

        // Adds the column "type" to the oxfolder_permissions table
        list.add(new AddTypeToFolderPermissionTableUpdateTask());

        // Adds the column "sharedParentFolder" to the oxfolder_permissions table
        list.add(new AddSharedParentFolderToFolderPermissionTableUpdateTask());

        // Drops rather needless foreign key from "object_use_count" table
        list.add(new com.openexchange.groupware.update.tasks.DropForeignKeyFromObjectUseCountTable());

        // Add origin column to "del_infostore" table to be able to restore deleted files to origin
        list.add(new com.openexchange.groupware.update.tasks.AddOriginColumnToInfostoreDocumentTables());

        // Drops aliases from non-existent users from database
        list.add(new com.openexchange.groupware.update.tasks.DropAliasesFromNonExistentUsers());

        // Removes any duplicate entries from the updateTask table
        list.add(new com.openexchange.groupware.update.tasks.RemoveDuplicatesFromUpdateTaskTable());

        // Converts mail account tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.MailAccountConvertUtf8ToUtf8mb4Task());

        // Converts object_use_count and object_permission to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.ObjectUseCountPermissionTableUtf8Mb4UpdateTask());

        // Converts task tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.TaskConvertUtf8ToUtf8mb4Task());

        // Converts attachment tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.AttachmentConvertUtf8ToUtf8mb4Task());

        // Converts the reminder table to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.ReminderTableUtf8Mb4UpdateTask());

        // Converts folder tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.FolderConvertUtf8ToUtf8mb4Task());

        // Converts login2user, updateTask, replicationMonitor, quota_context tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.AdminTablesUtf8Mb4UpdateTask());

        // Converts sequence tables to uf8mb4
        list.add(new com.openexchange.groupware.update.tasks.SequenceTablesUtf8Mb4UpdateTask());

        // Converts LDAP tables to uf8mb4
        list.add(new com.openexchange.groupware.update.tasks.LdapConvertUtf8ToUtf8mb4Task());

        // Converts settings tables to uf8mb4
        list.add(new com.openexchange.groupware.update.tasks.SettingsConvertUtf8ToUtf8mb4Task());

        // Converts misc. tables (prg_links, filestore_usage, etc.) to utf8mb4.
        list.add(new com.openexchange.groupware.update.tasks.MiscConvertUtf8ToUtf8mb4Task());

        // Converts "contextAttribute" to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.ContextAttributeConvertUtf8ToUtf8mb4Task());

        // Converts prg_dlist, del_dlist, prg_contacts_image, del_contacts_image, del_contacts, prg_contacts tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.ContactTablesUtf8Mb4UpdateTask());

        // Converts infostore tables to utf8mb4.
        list.add(new com.openexchange.groupware.update.tasks.InfostoreConvertUtf8ToUtf8mb4Task());

        // Converts aggregated contacts
        list.add(new com.openexchange.groupware.update.tasks.AggregatingContactsConvertUtf8ToUtf8mb4Task());

        // Convert ID sequence table to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.IDConvertToUtf8mb4Task());

        // Convert the resource and del_resource to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.ResourceTablesUtf8Mb4UpdateTask());

        // Converts OAuth accessor tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.OAuthAccessorConvertToUtf8mb4());
        list.add(new com.openexchange.groupware.update.tasks.IndexedFoldersConvertToUtf8mb4());

        // Convert legacy calendar tables to utf8mb4
        list.add(new com.openexchange.groupware.update.tasks.LegacyCalendarTablesUtf8Mb4UpdateTask());

        // Drops the unused "Shared address book" from database
        list.add(new com.openexchange.groupware.update.tasks.DropUnusedSharedAddressBookFolder());

        // +++++++++++++++++++++++++++++++++ Version 7.10.1 starts here. +++++++++++++++++++++++++++++++++

        // Adds the 'salt' column to 'user' and 'del_user' table in preparation for usage in the following release
        list.add(new com.openexchange.groupware.update.tasks.AddUserSaltColumnTask());

        // +++++++++++++++++++++++++++++++++ Version 7.10.2 starts here. +++++++++++++++++++++++++++++++++

        // Drop unused tables, see MW-1092
        list.add(new com.openexchange.groupware.update.tasks.DropICalIDsTableTask());
        list.add(new com.openexchange.groupware.update.tasks.DropICalPrincipalTableTask());
        list.add(new com.openexchange.groupware.update.tasks.DropVCardIDsTableTask());
        list.add(new com.openexchange.groupware.update.tasks.DropVCardPrincipalTableTask());
        list.add(new com.openexchange.groupware.update.tasks.DropPrgContactsLinkageTableTask());

        // Extends infostore document tables media-related fields
        list.add(new com.openexchange.groupware.update.tasks.AddMediaFieldsForInfostoreDocumentTable());
        list.add(new com.openexchange.groupware.update.tasks.AddMediaFieldsForInfostoreDocumentTableV2());
        list.add(new com.openexchange.groupware.update.tasks.AddMediaFieldsForInfostoreDocumentTableV3());

        // Add principal table update task
        list.add(new CreatePrincipalUseCountTableTask());

        list.add(new UnsupportedSubscriptionsRemoverTask());

        // +++++++++++++++++++++++++++++++++ Version 7.10.3 starts here. +++++++++++++++++++++++++++++++++
        // Drop publication related tables
        list.add(new com.openexchange.groupware.update.tasks.DropPublicationTablesTask());
        list.add(new com.openexchange.groupware.update.tasks.DeleteOXMFSubscriptionTask());

        // Add 'checksum' column to attachment tablesm see MW-1235
        list.add(new com.openexchange.groupware.update.tasks.AddChecksumColumnToAttachmentsTablesUpdateTask());

        // +++++++++++++++++++++++++++++++++ Version 7.10.4 starts here. +++++++++++++++++++++++++++++++++
        // Remove readable DAV user agent names from table user_attribute see MWB-58
        list.add(new com.openexchange.groupware.update.tasks.RemoveDAVUserAgentNamesForMWB58());

        // Reassign guests whose user who created them has been deleted see MWB-257
        list.add(new com.openexchange.groupware.update.tasks.ReassignGuestsWithDeletedUserToAdminUpdateTask());

        // +++++++++++++++++++++++++++++++++ Version 7.10.5 starts here. +++++++++++++++++++++++++++++++++
        // Re-add accidentally removed com.openexchange.groupware.update.tasks.CreateSubscribeTableTask, see MWB-489
        list.add(new com.openexchange.groupware.update.tasks.CreateSubscribeTableTask());

        list.add(new com.openexchange.groupware.update.tasks.DropUWAWidgetsTask());
        list.add(new com.openexchange.groupware.update.tasks.DropSwiftFilestoreTask());

        // +++++++++++++++++++++++++++++++++ Version 8.0.0 starts here. +++++++++++++++++++++++++++++++++

        // Re-assign guests that have guestCreatedBy set to 0 to context admin
        list.add(new com.openexchange.groupware.update.tasks.ReassignGuestsWithUserZeroToAdminUpdateTask());

        // Correct invalid timezone identifiers in table user
        list.add(new com.openexchange.groupware.update.tasks.CorrectInvalidTimezoneIdsTask());

        return list.toArray(new UpdateTaskV2[list.size()]);
    }

}
