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

package com.openexchange.contact.storage.rdb.internal;

import static com.openexchange.java.Autoboxing.I;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DataTruncation;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.openexchange.contact.AutocompleteParameters;
import com.openexchange.contact.SortOptions;
import com.openexchange.contact.storage.ContactTombstoneStorage;
import com.openexchange.contact.storage.ContactUserStorage;
import com.openexchange.contact.storage.DefaultContactStorage;
import com.openexchange.contact.storage.rdb.fields.DistListMemberField;
import com.openexchange.contact.storage.rdb.fields.Fields;
import com.openexchange.contact.storage.rdb.fields.QueryFields;
import com.openexchange.contact.storage.rdb.mapping.Mappers;
import com.openexchange.contact.storage.rdb.sql.Executor;
import com.openexchange.contact.storage.rdb.sql.Table;
import com.openexchange.database.DatabaseExceptionCodes;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.database.IncorrectStringSQLException;
import com.openexchange.database.StringLiteralSQLException;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.ContactConfig;
import com.openexchange.groupware.contact.ContactExceptionCodes;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.impl.IDGenerator;
import com.openexchange.groupware.search.ContactSearchObject;
import com.openexchange.groupware.tools.mappings.Mapping;
import com.openexchange.groupware.tools.mappings.common.AbstractCollectionUpdate;
import com.openexchange.imagetransformation.ImageMetadataService;
import com.openexchange.imagetransformation.ImageTransformationService;
import com.openexchange.imagetransformation.ScaleType;
import com.openexchange.java.Lists;
import com.openexchange.java.Streams;
import com.openexchange.quota.Quota;
import com.openexchange.quota.QuotaExceptionCodes;
import com.openexchange.search.SearchTerm;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.session.Session;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.oxfolder.OXFolderProperties;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link RdbContactStorage} - Database storage for contacts.
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class RdbContactStorage extends DefaultContactStorage implements ContactUserStorage, ContactTombstoneStorage {

    private static boolean PREFETCH_ATTACHMENT_INFO = true;

    private static int DELETE_CHUNK_SIZE = 50;

    private final Executor executor;

    /**
     * Initializes a new {@link RdbContactStorage}.
     */
    public RdbContactStorage() {
        super();
        executor = new Executor();
        LOG.debug("RdbContactStorage initialized.");
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean supports(final Session session, final String folderId) {
        return true;
    }

    @Override
    public Contact get(final Session session, final String folderId, final String id, final ContactField[] fields) throws OXException {
        final int objectID = parse(id);
        final int contextID = session.getContextId();
        final int folderID = parse(folderId);
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getReadOnly();
        try {
            /*
             * check fields
             */
            final QueryFields queryFields = FolderObject.SYSTEM_LDAP_FOLDER_ID == folderID ? new QueryFields(fields, ContactField.INTERNAL_USERID) : new QueryFields(fields);
            if (false == queryFields.hasContactData()) {
                return null;// nothing to do
            }
            /*
             * get contact data
             */
            final Contact contact = executor.selectSingle(connection, Table.CONTACTS, contextID, objectID, queryFields.getContactDataFields());
            if (null == contact) {
                throw ContactExceptionCodes.CONTACT_NOT_FOUND.create(Integer.valueOf(objectID), Integer.valueOf(contextID));
            }
            contact.setObjectID(objectID);
            contact.setContextId(contextID);
            /*
             * merge image data if needed
             */
            if (queryFields.hasImageData() && 0 < contact.getNumberOfImages()) {
                final Contact imageData = executor.selectSingle(connection, Table.IMAGES, contextID, objectID, queryFields.getImageDataFields());
                if (null != imageData) {
                    Mappers.CONTACT.mergeDifferences(contact, imageData);
                }
            }
            /*
             * merge distribution list data if needed
             */
            if (queryFields.hasDistListData() && 0 < contact.getNumberOfDistributionLists()) {
                contact.setDistributionList(executor.select(connection, Table.DISTLIST, contextID, objectID, Fields.DISTLIST_DATABASE_ARRAY));
            }
            /*
             * add attachment information in advance if needed
             */
            // TODO: at this stage, we break the storage separation, since we
            // assume that attachments are stored in the same database
            if (PREFETCH_ATTACHMENT_INFO && queryFields.hasAttachmentData() && 0 < contact.getNumberOfAttachments()) {
                contact.setLastModifiedOfNewestAttachment(executor.selectNewestAttachmentDate(connection, contextID, objectID));
            }
            return contact;
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            connectionHelper.back();
        }
    }

    @Override
    public void create(final Session session, final String folderId, final Contact contact) throws OXException {
        final int contextID = session.getContextId();
        final ServerSession serverSession = ServerSessionAdapter.valueOf(session);
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getWritable();
        boolean rollback = true;
        try {
            /*
             * (re-)check folder/permissions with this connection
             */
            final FolderObject folder = new OXFolderAccess(connection, serverSession.getContext()).getFolderObject(parse(folderId), false);
            final EffectivePermission permission = folder.getEffectiveUserPermission(serverSession.getUserId(), serverSession.getUserPermissionBits(), connection);
            if (false == permission.canCreateObjects()) {
                throw ContactExceptionCodes.NO_CREATE_PERMISSION.create(Integer.valueOf(parse(folderId)), Integer.valueOf(contextID), Integer.valueOf(serverSession.getUserId()));
            }
            /*
             * check quota restrictions
             */
            final Quota quota = RdbContactQuotaProvider.getAmountQuota(serverSession, executor, connection);
            if (null != quota) {
                long limit = quota.getLimit();
                if (limit == 0 || (0 < limit && 1 + quota.getUsage() > limit)) {
                    throw QuotaExceptionCodes.QUOTA_EXCEEDED_CONTACTS.create(Long.valueOf(quota.getUsage()), Long.valueOf(limit));
                }
            }
            /*
             * prepare insert
             */
            contact.setObjectID(IDGenerator.getId(contextID, com.openexchange.groupware.Types.CONTACT, connection));
            final Date now = new Date();
            contact.setLastModified(now);
            contact.setCreationDate(now);
            contact.setParentFolderID(parse(folderId));
            contact.setContextId(contextID);
            /*
             * insert image data if needed
             */
            if (contact.containsImage1() && null != contact.getImage1()) {
                checkImageSize(contact);
                contact.setImageLastModified(now);
                executor.insert(connection, Table.IMAGES, contact, Fields.IMAGE_DATABASE_ARRAY);
            }
            /*
             * insert contact
             */
            executor.insert(connection, Table.CONTACTS, contact, Fields.CONTACT_DATABASE_ARRAY);
            /*
             * insert distribution list data if needed
             */
            if (contact.containsDistributionLists()) {
                final DistListMember[] members = DistListMember.create(contact.getDistributionList(), contextID, contact.getObjectID());
                executor.insert(connection, Table.DISTLIST, members, Fields.DISTLIST_DATABASE_ARRAY);
            }
            /*
             * commit
             */
            connectionHelper.commit();
            rollback = false;
        } catch (IncorrectStringSQLException e) {
            throw Tools.getIncorrectStringException(serverSession, e, contact, Table.CONTACTS);
        } catch (DataTruncation e) {
            throw Tools.getTruncationException(session, connection, e, contact, Table.CONTACTS);
        } catch (StringLiteralSQLException e) {
            throw DatabaseExceptionCodes.STRING_LITERAL_ERROR.create(e, e.getMessage());
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (OXException e) {
            throw e;
        } finally {
            if (rollback) {
                Databases.rollback(connection);
            }
            connectionHelper.backWritable();
        }
    }

    @Override
    public void delete(final Session session, final String folderId, final String id, final Date lastRead) throws OXException {
        final int folderID = parse(folderId);
        final int objectID = parse(id);
        final ServerSession serverSession = ServerSessionAdapter.valueOf(session);
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getWritable();
        boolean rollback = true;
        try {
            /*
             * (re-)check folder/permissions with this connection
             */
            final FolderObject folder = new OXFolderAccess(connection, serverSession.getContext()).getFolderObject(folderID, false);
            final EffectivePermission permission = folder.getEffectiveUserPermission(serverSession.getUserId(), serverSession.getUserPermissionBits(), connection);
            if (false == permission.canDeleteOwnObjects()) {
                throw ContactExceptionCodes.NO_DELETE_PERMISSION.create(I(parse(folderId)), I(session.getContextId()), I(serverSession.getUserId()));
            }
            /*
             * delete contacts
             */
            if (0 == deleteContacts(serverSession, connection, folderID, new int[] { objectID }, lastRead.getTime())) {
                throw ContactExceptionCodes.CONTACT_NOT_FOUND.create(I(objectID), I(session.getContextId()));
            }
            /*
             * commit
             */
            connectionHelper.commit();
            rollback = false;
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (OXException e) {
            throw e;
        } finally {
            if (rollback) {
                Databases.rollback(connection);
            }
            connectionHelper.backWritable();
        }
    }

    @Override
    public void delete(final Session session, final String folderId) throws OXException {
        final int contextID = session.getContextId();
        final int folderID = parse(folderId);
        final ServerSession serverSession = ServerSessionAdapter.valueOf(session);
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getWritable();
        int deletedContacts = 0;
        boolean rollback = true;
        try {
            /*
             * get a list of object IDs to delete
             */
            final List<Contact> contacts = executor.select(connection, Table.CONTACTS, contextID, folderID, null, Integer.MIN_VALUE, new ContactField[] { ContactField.OBJECT_ID }, null, null, session.getUserId());
            if (null == contacts || 0 == contacts.size()) {
                return;// nothing to do
            }
            /*
             * (re-)check folder/permissions with this connection
             */
            final FolderObject folder = new OXFolderAccess(connection, serverSession.getContext()).getFolderObject(folderID, false);
            final EffectivePermission permission = folder.getEffectiveUserPermission(serverSession.getUserId(), serverSession.getUserPermissionBits(), connection);
            if (false == permission.canDeleteOwnObjects()) {
                throw ContactExceptionCodes.NO_DELETE_PERMISSION.create(I(folderID), I(contextID), I(serverSession.getUserId()));
            }
            final int[] objectIDs = getObjectIDs(contacts);
            /*
             * delete contacts - per convention, don't check last modification
             * time when clearing a folder
             */
            deletedContacts = deleteContacts(serverSession, connection, folderID, objectIDs, Long.MIN_VALUE);
            /*
             * commit
             */
            connectionHelper.commit();
            rollback = false;
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (OXException e) {
            throw e;
        } finally {
            if (rollback) {
                Databases.rollback(connection);
            }
            if (deletedContacts <= 0) {
                connectionHelper.backWritableAfterReading();
            } else {
                connectionHelper.backWritable();
            }
        }
    }

    @Override
    public void delete(final Session session, final String folderId, final String[] ids, final Date lastRead) throws OXException {
        final int folderID = parse(folderId);
        final ServerSession serverSession = ServerSessionAdapter.valueOf(session);
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getWritable();
        boolean rollback = true;
        try {
            /*
             * (re-)check folder/permissions with this connection
             */
            final FolderObject folder = new OXFolderAccess(connection, serverSession.getContext()).getFolderObject(folderID, false);
            final EffectivePermission permission = folder.getEffectiveUserPermission(serverSession.getUserId(), serverSession.getUserPermissionBits(), connection);
            if (false == permission.canDeleteOwnObjects()) {
                throw ContactExceptionCodes.NO_DELETE_PERMISSION.create(I(folderID), I(session.getContextId()), I(serverSession.getUserId()));
            }
            /*
             * delete contacts
             */
            deleteContacts(serverSession, connection, folderID, parse(ids), lastRead.getTime());
            /*
             * commit
             */
            connectionHelper.commit();
            rollback = false;
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (OXException e) {
            throw e;
        } finally {
            if (rollback) {
                Databases.rollback(connection);
            }
            connectionHelper.backWritable();
        }
    }

    @Override
    public void insertTombstone(Session session, String folderId, Contact contact) throws OXException {
        ConnectionHelper connectionHelper = new ConnectionHelper(session);
        Connection connection = connectionHelper.getWritable();
        boolean rollback = true;
        try {
            /*
             * insert tombstone contact
             */
            if (0 >= contact.getObjectID()) {
                contact.setObjectID(IDGenerator.getId(session.getContextId(), com.openexchange.groupware.Types.CONTACT, connection));
            }
            insertDeletedContact(session, connection, parse(folderId), contact);
            /*
             * commit
             */
            connectionHelper.commit();
            rollback = false;
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            if (rollback) {
                Databases.rollback(connection);
            }
            connectionHelper.backWritable();
        }
    }

    @Override
    public void update(final Session session, final String folderId, final String id, final Contact contact, final Date lastRead) throws OXException {
        final int contextID = session.getContextId();
        final int userID = session.getUserId();
        final int objectID = parse(id);
        final long maxLastModified = lastRead.getTime();
        final ServerSession serverSession = ServerSessionAdapter.valueOf(session);
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getWritable();
        boolean rollback = true;
        try {
            /*
             * (re-)check folder/permissions with this connection
             */
            if (contact.containsParentFolderID() && contact.getParentFolderID() != parse(folderId)) {
                // move
                final FolderObject sourceFolder = new OXFolderAccess(connection, serverSession.getContext()).getFolderObject(parse(folderId), false);
                final EffectivePermission sourcePermission = sourceFolder.getEffectiveUserPermission(serverSession.getUserId(), serverSession.getUserConfiguration(), connection);
                if (false == sourcePermission.canReadOwnObjects()) {
                    throw ContactExceptionCodes.NO_ACCESS_PERMISSION.create(I(parse(folderId)), I(contextID), I(session.getUserId()));
                }
                final FolderObject targetFolder = new OXFolderAccess(connection, serverSession.getContext()).getFolderObject(contact.getParentFolderID(), false);
                final EffectivePermission targetPermission = targetFolder.getEffectiveUserPermission(serverSession.getUserId(), serverSession.getUserConfiguration(), connection);
                if (false == targetPermission.canWriteOwnObjects()) {
                    throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(contact.getObjectID()), I(contextID));
                }
            } else if (FolderObject.SYSTEM_LDAP_FOLDER_ID == parse(folderId)) {
                if (false == OXFolderProperties.isEnableInternalUsersEdit() && session.getUserId() != serverSession.getContext().getMailadmin()) {
                    throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(parse(id)), I(contextID));
                }
            } else {
                final FolderObject folder = new OXFolderAccess(connection, serverSession.getContext()).getFolderObject(parse(folderId), false);
                final EffectivePermission permission = folder.getEffectiveUserPermission(serverSession.getUserId(), serverSession.getUserConfiguration(), connection);
                if (false == permission.canWriteOwnObjects()) {
                    throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(parse(id)), I(contextID));
                }
            }
            /*
             * prepare insert
             */
            final Date now = new Date();
            contact.setLastModified(now);
            final QueryFields queryFields = new QueryFields(Mappers.CONTACT.getAssignedFields(contact));
            /*
             * insert copied records to 'deleted' tables with updated metadata
             * when parent folder changes
             */
            if (contact.containsParentFolderID() && false == Integer.toString(contact.getParentFolderID()).equals(folderId)) {
                final Contact update = new Contact();
                update.setLastModified(new Date());
                update.setModifiedBy(userID);
                if (0 == executor.replaceToDeletedContactsAndUpdate(connection, contextID, Integer.MIN_VALUE, new int[] { objectID }, maxLastModified, update, new ContactField[] { ContactField.MODIFIED_BY, ContactField.LAST_MODIFIED })) {
                    throw ContactExceptionCodes.CONTACT_NOT_FOUND.create(I(objectID), I(contextID));
                }
            }
            /*
             * update image data if needed
             */
            updateImageIfNeeded(contextID, objectID, contact, now, maxLastModified, queryFields, connection);
            /*
             * update contact data
             */
            if (0 == executor.update(connection, Table.CONTACTS, contextID, objectID, maxLastModified, contact, Fields.sort(queryFields.getContactDataFields()))) {
                // TODO: check imagelastmodified also?
                throw ContactExceptionCodes.OBJECT_HAS_CHANGED.create(I(contextID), I(objectID));
            }
            /*
             * update distlist data if needed
             */
            if (queryFields.hasDistListData()) {
                List<DistListMember> storedMembers = Lists.toList(executor.select(connection, Table.DISTLIST, contextID, objectID, Fields.DISTLIST_DATABASE_ARRAY));
                List<DistListMember> members = Lists.toList(DistListMember.create(contact.getDistributionList(), contextID, objectID));
                AbstractCollectionUpdate<DistListMember, DistListMemberField> distListUpdate = Mappers.getDistListUpdate(storedMembers, members);
                executor.deleteByUuid(connection, contextID, distListUpdate.getRemovedItems());
                executor.updateMembers(connection, contextID, distListUpdate.getUpdatedItems());
                List<DistListMember> addedItems = distListUpdate.getAddedItems();
                if (null != addedItems && false == addedItems.isEmpty()) {
                    executor.insert(connection, Table.DISTLIST, addedItems.toArray(new DistListMember[addedItems.size()]), Fields.DISTLIST_DATABASE_ARRAY);
                }
            }
            /*
             * commit
             */
            connectionHelper.commit();
            rollback = false;
        } catch (IncorrectStringSQLException e) {
            throw Tools.getIncorrectStringException(serverSession, e, contact, Table.CONTACTS);
        } catch (DataTruncation e) {
            throw Tools.getTruncationException(session, connection, e, contact, Table.CONTACTS);
        } catch (StringLiteralSQLException e) {
            throw DatabaseExceptionCodes.STRING_LITERAL_ERROR.create(e, e.getMessage());
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            if (rollback) {
                Databases.rollback(connection);
            }
            connectionHelper.backWritable();
        }
    }

    @Override
    public void updateReferences(final Session session, final Contact originalContact, final Contact updatedContact) throws OXException {
        /*
         * Check if there are relevant changes
         */
        if (originalContact.getMarkAsDistribtuionlist()) {
            return;// nothing to do in case of updated distribution lists
        }
        final Contact differences = Mappers.CONTACT.getDifferences(originalContact, updatedContact);
        final ContactField[] assignedFields = Mappers.CONTACT.getAssignedFields(differences);
        boolean relevantFieldChanged = false;
        if (null != assignedFields && 0 < assignedFields.length) {
            for (final ContactField assignedField : assignedFields) {
                if (Fields.DISTLIST_DATABASE_RELEVANT.contains(assignedField)) {
                    relevantFieldChanged = true;
                    break;
                }
            }
        }
        if (false == relevantFieldChanged) {
            return;// no fields relevant for the distlist table changed
        }
        final int contextID = session.getContextId();
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        boolean rollback = true;
        try {
            /*
             * Check which existing member references are affected
             */
            final List<Integer> affectedDistributionLists = new ArrayList<Integer>();
            final List<DistListMember> referencedMembers = executor.select(connectionHelper.getReadOnly(), Table.DISTLIST, contextID, originalContact.getObjectID(), originalContact.getParentFolderID(), DistListMemberField.values());
            connectionHelper.backReadOnly();
            if (null != referencedMembers && 0 < referencedMembers.size()) {
                for (final DistListMember member : referencedMembers) {
                    final DistListMemberField[] updatedFields = Tools.updateMember(member, updatedContact);
                    if (null != updatedFields && 0 < updatedFields.length) {
                        /*
                         * Update member, remember affected parent contact id of
                         * the list
                         */
                        if (0 < executor.updateMember(connectionHelper.getWritable(), Table.DISTLIST, contextID, member, updatedFields)) {
                            affectedDistributionLists.add(Integer.valueOf(member.getParentContactID()));
                        }
                    }
                }
            }
            /*
             * Update affected parent distribution lists' timestamps, too
             */
            if (0 < affectedDistributionLists.size()) {
                for (final Integer distListID : affectedDistributionLists) {
                    executor.update(connectionHelper.getWritable(), Table.CONTACTS, contextID, distListID.intValue(), Long.MIN_VALUE, updatedContact, new ContactField[] { ContactField.LAST_MODIFIED, ContactField.MODIFIED_BY });
                }
            }
            /*
             * commit
             */
            connectionHelper.commit();
            rollback = false;
        } catch (IncorrectStringSQLException e) {
            throw Tools.getIncorrectStringException(session, e, updatedContact, Table.CONTACTS);
        } catch (DataTruncation e) {
            throw Tools.getTruncationException(session, connectionHelper.getReadOnly(), e, updatedContact, Table.CONTACTS);
        } catch (StringLiteralSQLException e) {
            throw DatabaseExceptionCodes.STRING_LITERAL_ERROR.create(e, e.getMessage());
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            if (rollback) {
                Databases.rollback(connectionHelper.getWritable());
            }
            connectionHelper.back();
        }
    }

    @Override
    public SearchIterator<Contact> deleted(final Session session, final String folderId, final Date since, final ContactField[] fields) throws OXException {
        return this.getContacts(true, session, folderId, null, since, fields, null, null);
    }

    @Override
    public SearchIterator<Contact> deleted(final Session session, final String folderId, final Date since, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return this.getContacts(true, session, folderId, null, since, fields, null, sortOptions);
    }

    @Override
    public SearchIterator<Contact> modified(final Session session, final String folderId, final Date since, final ContactField[] fields) throws OXException {
        return this.getContacts(false, session, folderId, null, since, fields, null, null);
    }

    @Override
    public SearchIterator<Contact> modified(final Session session, final String folderId, final Date since, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return this.getContacts(false, session, folderId, null, since, fields, null, sortOptions);
    }

    @Override
    public <O> SearchIterator<Contact> search(final Session session, final SearchTerm<O> term, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return this.getContacts(false, session, null, null, null, fields, term, sortOptions);
    }

    @Override
    public SearchIterator<Contact> search(final Session session, final ContactSearchObject contactSearch, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return this.getContacts(session, contactSearch, fields, sortOptions);
    }

    @Override
    public SearchIterator<Contact> all(final Session session, final String folderId, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return this.getContacts(false, session, folderId, null, null, fields, null, sortOptions);
    }

    @Override
    public SearchIterator<Contact> list(final Session session, final String folderId, final String[] ids, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return this.getContacts(false, session, folderId, ids, null, fields, null, sortOptions);
    }

    @Override
    public int count(final Session session, final String folderId, final boolean canReadAll) throws OXException {
        final int contextID = session.getContextId();
        final int userID = session.getUserId();
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getReadOnly();
        try {
            return executor.count(connection, Table.CONTACTS, contextID, userID, parse(folderId), canReadAll);
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            connectionHelper.backReadOnly();
        }
    }

    @Override
    public SearchIterator<Contact> searchByBirthday(final Session session, final List<String> folderIDs, final Date from, final Date until, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return searchByAnnualDate(session, folderIDs, from, until, fields, sortOptions, ContactField.BIRTHDAY);
    }

    @Override
    public SearchIterator<Contact> searchByAnniversary(final Session session, final List<String> folderIDs, final Date from, final Date until, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        return searchByAnnualDate(session, folderIDs, from, until, fields, sortOptions, ContactField.ANNIVERSARY);
    }

    @Override
    public SearchIterator<Contact> autoComplete(final Session session, final List<String> folderIDs, final String query, final AutocompleteParameters parameters, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        /*
         * prepare select
         */
        final int contextID = session.getContextId();
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getReadOnly();
        final int[] parentFolderIDs = null != folderIDs ? parse(folderIDs.toArray(new String[folderIDs.size()])) : null;
        try {
            /*
             * check fields
             */
            final ContactField[] mandatoryFields = com.openexchange.tools.arrays.Arrays.add(Tools.getRequiredFields(sortOptions), ContactField.OBJECT_ID, ContactField.INTERNAL_USERID);
            final QueryFields queryFields = new QueryFields(fields, mandatoryFields);
            if (false == queryFields.hasContactData()) {
                return null;// nothing to do
            }
            /*
             * get contact data
             */
            List<Contact> contacts = executor.selectByAutoComplete(connection, contextID, parentFolderIDs, query, parameters, queryFields.getContactDataFields(), sortOptions);
            if (null != contacts && 0 < contacts.size()) {
                /*
                 * merge image data if needed
                 */
                if (queryFields.hasImageData()) {
                    contacts = mergeImageData(connection, Table.IMAGES, contextID, contacts, queryFields.getImageDataFields());
                }
                /*
                 * merge distribution list data if needed
                 */
                if (queryFields.hasDistListData()) {
                    contacts = mergeDistListData(connection, Table.DISTLIST, contextID, contacts);
                }
                /*
                 * merge attachment information in advance if needed
                 */
                // TODO: at this stage, we break the storage separation, since
                // we assume that attachments are stored in the same database
                if (PREFETCH_ATTACHMENT_INFO && queryFields.hasAttachmentData()) {
                    contacts = mergeAttachmentData(connection, contextID, contacts);
                }
            }
            return getSearchIterator(contacts);
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            connectionHelper.backReadOnly();
        }
    }

    private SearchIterator<Contact> searchByAnnualDate(final Session session, final List<String> folderIDs, final Date from, final Date until, final ContactField[] fields, final SortOptions sortOptions, final ContactField dateField) throws OXException {
        /*
         * prepare select
         */
        final int contextID = session.getContextId();
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getReadOnly();
        final int[] parentFolderIDs = null != folderIDs ? parse(folderIDs.toArray(new String[folderIDs.size()])) : null;
        try {
            /*
             * check fields
             */
            final QueryFields queryFields = new QueryFields(fields, ContactField.OBJECT_ID, ContactField.INTERNAL_USERID);
            if (false == queryFields.hasContactData()) {
                return null;// nothing to do
            }
            /*
             * get contact data
             */
            List<Contact> contacts = executor.selectByAnnualDate(connection, contextID, parentFolderIDs, from, until, queryFields.getContactDataFields(), sortOptions, dateField);
            if (null != contacts && 0 < contacts.size()) {
                /*
                 * merge image data if needed
                 */
                if (queryFields.hasImageData()) {
                    contacts = mergeImageData(connection, Table.IMAGES, contextID, contacts, queryFields.getImageDataFields());
                }
                /*
                 * merge distribution list data if needed
                 */
                if (queryFields.hasDistListData()) {
                    contacts = mergeDistListData(connection, Table.DISTLIST, contextID, contacts);
                }
                /*
                 * merge attachment information in advance if needed
                 */
                // TODO: at this stage, we break the storage separation, since
                // we assume that attachments are stored in the same database
                if (PREFETCH_ATTACHMENT_INFO && queryFields.hasAttachmentData()) {
                    contacts = mergeAttachmentData(connection, contextID, contacts);
                }
            }
            return getSearchIterator(contacts);
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            connectionHelper.backReadOnly();
        }
    }

    /**
     * Gets contacts from the database.
     *
     * @param deleted
     *            whether to query the tables for deleted objects or not
     * @param contextID
     *            the context ID
     * @param folderID
     *            the folder ID, or <code>null</code> if not used
     * @param ids
     *            the object IDs, or <code>null</code> if not used
     * @param since
     *            the exclusive minimum modification time to consider, or
     *            <code>null</code> if not used
     * @param fields
     *            the contact fields that should be retrieved
     * @param term
     *            a search term to apply, or <code>null</code> if not used
     * @param sortOptions
     *            the sort options to use, or <code>null</code> if not used
     * @return the contacts
     * @throws OXException
     */
    private <O> SearchIterator<Contact> getContacts(final boolean deleted, final Session session, final String folderID, final String[] ids, final Date since, final ContactField[] fields, final SearchTerm<O> term, final SortOptions sortOptions) throws OXException {
        /*
         * prepare select
         */
        final int contextID = session.getContextId();
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getReadOnly();
        final long minLastModified = null != since ? since.getTime() : Long.MIN_VALUE;
        final int parentFolderID = null != folderID ? parse(folderID) : Integer.MIN_VALUE;
        final int[] objectIDs = null != ids ? parse(ids) : null;
        try {
            /*
             * check fields
             */
            final QueryFields queryFields = FolderObject.SYSTEM_LDAP_FOLDER_ID == parentFolderID ? new QueryFields(fields, ContactField.OBJECT_ID, ContactField.INTERNAL_USERID) : new QueryFields(fields, ContactField.OBJECT_ID);
            if (false == queryFields.hasContactData()) {
                return null;// nothing to do
            }
            /*
             * get contact data
             */
            List<Contact> contacts;
            if (deleted) {
                /*
                 * pay attention to limited field availability when querying
                 * deleted contacts
                 */
                final ContactField[] requestedFields = queryFields.getContactDataFields();
                final List<ContactField> availableFields = new ArrayList<ContactField>();
                for (final ContactField requestedField : requestedFields) {
                    if (Fields.DEL_CONTACT_DATABASE.contains(requestedField)) {
                        availableFields.add(requestedField);
                    }
                }
                contacts = executor.select(connection, Table.DELETED_CONTACTS, contextID, parentFolderID, objectIDs, minLastModified, availableFields.toArray(new ContactField[availableFields.size()]), term, sortOptions, session.getUserId());
            } else {
                contacts = executor.select(connection, Table.CONTACTS, contextID, parentFolderID, objectIDs, minLastModified, queryFields.getContactDataFields(), term, sortOptions, session.getUserId());
                if (null != contacts && 0 < contacts.size()) {
                    /*
                     * merge image data if needed
                     */
                    if (queryFields.hasImageData()) {
                        contacts = mergeImageData(connection, Table.IMAGES, contextID, contacts, queryFields.getImageDataFields());
                    }
                    /*
                     * merge distribution list data if needed
                     */
                    if (queryFields.hasDistListData()) {
                        contacts = mergeDistListData(connection, Table.DISTLIST, contextID, contacts);
                    }
                    /*
                     * merge attachment information in advance if needed
                     */
                    // TODO: at this stage, we break the storage separation,
                    // since we assume that attachments are stored in the same
                    // database
                    if (PREFETCH_ATTACHMENT_INFO && queryFields.hasAttachmentData()) {
                        contacts = mergeAttachmentData(connection, contextID, contacts);
                    }
                }
            }
            /*
             * wrap into search iterator and return result
             */
            return getSearchIterator(contacts);
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            connectionHelper.backReadOnly();
        }
    }

    private SearchIterator<Contact> getContacts(final Session session, final ContactSearchObject contactSearch, final ContactField[] fields, final SortOptions sortOptions) throws OXException {
        /*
         * prepare select
         */
        final int contextID = session.getContextId();
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getReadOnly();
        try {
            /*
             * check fields
             */
            final ContactField[] mandatoryFields = com.openexchange.tools.arrays.Arrays.add(Tools.getRequiredFields(sortOptions), ContactField.OBJECT_ID, ContactField.INTERNAL_USERID);
            final QueryFields queryFields = new QueryFields(fields, mandatoryFields);
            if (false == queryFields.hasContactData()) {
                return null;// nothing to do
            }
            /*
             * get contact data
             */
            List<Contact> contacts = executor.select(connection, Table.CONTACTS, contextID, contactSearch, queryFields.getContactDataFields(), sortOptions, session.getUserId());
            if (null != contacts && 0 < contacts.size()) {
                /*
                 * merge image data if needed
                 */
                if (queryFields.hasImageData()) {
                    contacts = mergeImageData(connection, Table.IMAGES, contextID, contacts, queryFields.getImageDataFields());
                }
                /*
                 * merge distribution list data if needed
                 */
                if (queryFields.hasDistListData()) {
                    contacts = mergeDistListData(connection, Table.DISTLIST, contextID, contacts);
                }
                /*
                 * merge attachment information in advance if needed
                 */
                // TODO: at this stage, we break the storage separation, since
                // we assume that attachments are stored in the same database
                if (PREFETCH_ATTACHMENT_INFO && queryFields.hasAttachmentData()) {
                    contacts = mergeAttachmentData(connection, contextID, contacts);
                }
            }
            return getSearchIterator(contacts);
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            connectionHelper.backReadOnly();
        }
    }

    private int deleteContacts(final Session session, final Connection connection, final int folderID, final int[] objectIDs, final long maxLastModified) throws OXException, SQLException {
        int deletedContacts = 0;
        final int contextID = session.getContextId();
        final int userID = session.getUserId();
        /*
         * prepare contact to represent updated metadata
         */
        final ContactField[] updatedFields = new ContactField[] { ContactField.MODIFIED_BY, ContactField.LAST_MODIFIED, ContactField.NUMBER_OF_IMAGES };
        final Contact updatedMetadata = new Contact();
        updatedMetadata.setLastModified(new Date());
        updatedMetadata.setModifiedBy(userID);
        updatedMetadata.setNumberOfImages(0);
        for (int i = 0; i < objectIDs.length; i += DELETE_CHUNK_SIZE) {
            /*
             * prepare chunk
             */
            final int length = Math.min(objectIDs.length, i + DELETE_CHUNK_SIZE) - i;
            final int[] currentObjectIDs = new int[length];
            System.arraycopy(objectIDs, i, currentObjectIDs, 0, length);
            /*
             * insert copied records to 'deleted' contact-table with updated
             * metadata
             */
            executor.replaceToDeletedContactsAndUpdate(connection, contextID, folderID, currentObjectIDs, maxLastModified, updatedMetadata, updatedFields);
            /*
             * delete records in original tables
             */
            deletedContacts += executor.delete(connection, Table.CONTACTS, contextID, folderID, currentObjectIDs, maxLastModified);
            executor.delete(connection, Table.IMAGES, contextID, Integer.MIN_VALUE, currentObjectIDs, maxLastModified);
            executor.delete(connection, Table.DISTLIST, contextID, Integer.MIN_VALUE, currentObjectIDs);
        }
        return deletedContacts;
    }

    private int insertDeletedContact(Session session, Connection connection, int folderID, Contact contact) throws SQLException, OXException {
        /*
         * prepare contact to represent updated metadata
         */
        Contact insertedContact = contact.clone();
        insertedContact.setCreationDate(new Date());
        insertedContact.setLastModified(new Date());
        insertedContact.setParentFolderID(folderID);
        insertedContact.setCreatedBy(session.getUserId());
        insertedContact.setModifiedBy(session.getUserId());
        insertedContact.setContextId(session.getContextId());
        /*
         * insert record to 'deleted' contact-table with updated metadata
         */
        ContactField[] fields = Fields.DEL_CONTACT_DATABASE.toArray(new ContactField[Fields.DEL_CONTACT_DATABASE.size()]);
        return executor.replace(connection, Table.DELETED_CONTACTS, insertedContact, fields);
    }

    private List<Contact> mergeDistListData(final Connection connection, final Table table, final int contextID, final List<Contact> contacts) throws SQLException, OXException {
        final int[] objectIDs = getObjectIDsWithDistLists(contacts);
        if (null != objectIDs && 0 < objectIDs.length) {
            final Map<Integer, List<DistListMember>> distListData = executor.select(connection, table, contextID, objectIDs, Fields.DISTLIST_DATABASE_ARRAY);
            for (final Contact contact : contacts) {
                final List<DistListMember> distList = distListData.get(Integer.valueOf(contact.getObjectID()));
                if (null != distList) {
                    contact.setDistributionList(distList.toArray(new DistListMember[distList.size()]));
                }
            }
        }
        return contacts;
    }

    private List<Contact> mergeAttachmentData(final Connection connection, final int contextID, final List<Contact> contacts) throws SQLException {
        final int[] objectIDs = getObjectIDsWithAttachments(contacts);
        if (null != objectIDs && 0 < objectIDs.length) {
            final Map<Integer, Date> attachmentData = executor.selectNewestAttachmentDates(connection, contextID, objectIDs);
            for (final Contact contact : contacts) {
                final Date attachmentLastModified = attachmentData.get(Integer.valueOf(contact.getObjectID()));
                if (null != attachmentLastModified) {
                    contact.setLastModifiedOfNewestAttachment(attachmentLastModified);
                }
            }
        }
        return contacts;
    }

    private List<Contact> mergeImageData(final Connection connection, final Table table, final int contextID, final List<Contact> contacts, final ContactField[] fields) throws SQLException, OXException {
        final int[] objectIDs = getObjectIDsWithImages(contacts);
        if (null != objectIDs && 0 < objectIDs.length) {
            final List<Contact> imagaDataList = executor.select(connection, table, contextID, Integer.MIN_VALUE, objectIDs, Long.MIN_VALUE, fields, null, null);
            if (null != imagaDataList && 0 < imagaDataList.size()) {
                return mergeByID(contacts, imagaDataList);
            }
        }
        return contacts;
    }

    private static List<Contact> mergeByID(final List<Contact> into, final List<Contact> from) throws OXException {
        if (null == into) {
            throw new IllegalArgumentException("into");
        } else if (null == from) {
            throw new IllegalArgumentException("from");
        }
        for (final Contact fromData : from) {
            final int objectID = fromData.getObjectID();
            for (int i = 0; i < into.size(); i++) {
                final Contact intoData = into.get(i);
                if (objectID == intoData.getObjectID()) {
                    Mappers.CONTACT.mergeDifferences(intoData, fromData);
                    break;
                }
            }
        }
        return into;
    }

    private int[] getObjectIDsWithImages(final List<Contact> contacts) {
        int i = 0;
        final int[] objectIDs = new int[contacts.size()];
        for (final Contact contact : contacts) {
            if (0 < contact.getNumberOfImages()) {
                objectIDs[i++] = contact.getObjectID();
            }
        }
        return Arrays.copyOf(objectIDs, i);
    }

    private int[] getObjectIDsWithDistLists(final List<Contact> contacts) {
        int i = 0;
        final int[] objectIDs = new int[contacts.size()];
        for (final Contact contact : contacts) {
            if (0 < contact.getNumberOfDistributionLists()) {
                objectIDs[i++] = contact.getObjectID();
            }
        }
        return Arrays.copyOf(objectIDs, i);
    }

    private int[] getObjectIDsWithAttachments(final List<Contact> contacts) {
        int i = 0;
        final int[] objectIDs = new int[contacts.size()];
        for (final Contact contact : contacts) {
            if (0 < contact.getNumberOfAttachments()) {
                objectIDs[i++] = contact.getObjectID();
            }
        }
        return Arrays.copyOf(objectIDs, i);
    }

    private int[] getObjectIDs(final List<Contact> contacts) {
        int i = 0;
        final int[] objectIDs = new int[contacts.size()];
        for (final Contact contact : contacts) {
            objectIDs[i++] = contact.getObjectID();
        }
        return Arrays.copyOf(objectIDs, i);
    }

    @Override
    public int createGuestContact(final int contextId, final Contact contact, Connection connection) throws OXException {
        Connection con = connection;
        boolean newCon = false;
        DatabaseService dbService = null;
        if (null == con) {
            newCon = true;
            dbService = RdbServiceLookup.getService(DatabaseService.class);
            if (null == dbService) {
                throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
            }
            con = dbService.getWritable(contextId);
        }
        try {
            return create(contextId, contact, con);
        } catch (OXException e) {
            if (newCon) {
                Databases.rollback(con);
            }
            throw e;
        } finally {
            if (newCon && null != dbService) {
                dbService.backWritable(contextId, con);
            }
        }
    }

    @Override
    public void deleteGuestContact(final int contextId, final int userId, final Date lastRead, Connection connection) throws OXException {
        Connection con = connection;
        boolean newCon = false;
        DatabaseService dbService = null;
        if (null == con) {
            newCon = true;
            dbService = RdbServiceLookup.getService(DatabaseService.class);
            if (null == dbService) {
                throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
            }
            con = dbService.getWritable(contextId);
        }
        try {
            final Contact toDelete = executor.selectSingleGuestContact(con, Table.CONTACTS, contextId, userId, new ContactField[] { ContactField.OBJECT_ID });
            if (null != toDelete) {
                executor.deleteSingle(con, Table.CONTACTS, contextId, toDelete.getObjectID(), lastRead.getTime());
            }
        } catch (SQLException e) {
            if (newCon) {
                Databases.rollback(con);
            }
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (OXException e) {
            if (newCon) {
                Databases.rollback(con);
            }
            throw e;
        } finally {
            if (newCon && null != dbService) {
                dbService.backWritable(contextId, con);
            }
        }
    }

    private void checkImageSize(final Contact contact) {
        try {
            if (!contact.containsImage1()) {
                return;
            }

            final ContactConfig conf = ContactConfig.getInstance();
            final boolean scale_images = conf.getBoolean(ContactConfig.Property.SCALE_IMAGES).booleanValue();
            if (!scale_images) {
                return;
            }
            final int image_width = Integer.parseInt(conf.getString(ContactConfig.Property.SCALED_IMAGE_WIDTH));
            final int image_height = Integer.parseInt(conf.getString(ContactConfig.Property.SCALED_IMAGE_HEIGHT));
            int typeNumber;
            typeNumber = Integer.parseInt(conf.getString(ContactConfig.Property.SCALE_TYPE));
            ScaleType type = ScaleType.CONTAIN;
            switch (typeNumber) {
                case 1:
                    type = ScaleType.CONTAIN;
                    break;
                case 2:
                    type = ScaleType.COVER;
                    break;
                case 3:
                    type = ScaleType.AUTO;
                    break;
            }

            byte[] imageBytes = contact.getImage1();
            if (imageBytes == null || imageBytes.length == 0) {
                return;
            }

            byte[] transformedImage;
            {
                ImageTransformationService transformationService = RdbServiceLookup.getService(ImageTransformationService.class, true);
                String formatName = null != contact.getImageContentType() ? contact.getImageContentType() : "image/jpeg";

                ImageMetadataService imageMetadataService = RdbServiceLookup.optService(ImageMetadataService.class);
                if (null == imageMetadataService) {
                    BufferedImage originalImage = transformationService.transfom(imageBytes).getImage();
                    if (null == originalImage || originalImage.getWidth() <= image_width && originalImage.getHeight() <= image_height) {
                        return;
                    }
                } else {
                    Dimension dimension = imageMetadataService.getDimensionFor(Streams.newByteArrayInputStream(imageBytes), formatName, null);
                    if (null != dimension && dimension.getWidth() <= image_width && dimension.getHeight() <= image_height) {
                        return;
                    }
                }

                transformedImage = transformationService.transfom(imageBytes)
                    .rotate()
                    .scale(image_width, image_height, type, true)
                    .getBytes(formatName)
                ;
            }
            if (null != transformedImage && 0 < transformedImage.length) {
                contact.setImage1(transformedImage);
            }
        } catch (OXException | IOException | NumberFormatException ex) {
            LOG.error("Unable to resize contact image due to {}", ex.getMessage(), ex);
        }
    }

    private int create(final int contextId, final Contact contact, final Connection con) throws OXException {
        int objectId = -1;
        try {
            objectId = IDGenerator.getId(contextId, com.openexchange.groupware.Types.CONTACT, con);
            contact.setObjectID(objectId);
            final Date now = new Date();
            contact.setLastModified(now);
            contact.setCreationDate(now);
            contact.setContextId(contextId);
            /*
             * insert image data if needed
             */
            if (contact.containsImage1() && null != contact.getImage1()) {
                checkImageSize(contact);
                contact.setImageLastModified(now);
                executor.insert(con, Table.IMAGES, contact, Fields.IMAGE_DATABASE_ARRAY);
            }
            /*
             * insert contact
             */
            executor.insert(con, Table.CONTACTS, contact, Fields.CONTACT_DATABASE_ARRAY);
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        }
        return objectId;
    }

    @Override
    public Contact getGuestContact(final int contextId, final int guestId, final ContactField[] contactFields) throws OXException {
        final DatabaseService dbService = RdbServiceLookup.getService(DatabaseService.class);
        if (null == dbService) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
        }
        final Connection con = dbService.getReadOnly(contextId);
        Contact contact = null;
        try {
            final QueryFields queryFields = new QueryFields(contactFields, ContactField.CONTEXTID, ContactField.OBJECT_ID);
            contact = executor.selectSingleGuestContact(con, Table.CONTACTS, contextId, guestId, queryFields.getContactDataFields());
            if (null == contact) {
                throw ContactExceptionCodes.CONTACT_NOT_FOUND.create(I(guestId), I(contextId));
            }
            if (queryFields.hasImageData() && 0 < contact.getNumberOfImages()) {
                final Contact imageData = executor.selectSingle(con, Table.IMAGES, contextId, contact.getObjectID(), queryFields.getImageDataFields());
                if (null != imageData) {
                    Mappers.CONTACT.mergeDifferences(contact, imageData);
                }
            }
            /*
             * merge distribution list data if needed
             */
            if (queryFields.hasDistListData() && 0 < contact.getNumberOfDistributionLists()) {
                contact.setDistributionList(executor.select(con, Table.DISTLIST, contextId, contact.getObjectID(), Fields.DISTLIST_DATABASE_ARRAY));
            }
            /*
             * add attachment information in advance if needed
             */
            // TODO: at this stage, we break the storage separation, since we
            // assume that attachments are stored in the same database
            if (PREFETCH_ATTACHMENT_INFO && queryFields.hasAttachmentData() && 0 < contact.getNumberOfAttachments()) {
                contact.setLastModifiedOfNewestAttachment(executor.selectNewestAttachmentDate(con, contextId, contact.getObjectID()));
            }
            return contact;
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            dbService.backReadOnly(contextId, con);
        }
    }

    @Override
    public void updateGuestContact(final Session session, final int contactId, final Contact contact, final Date lastRead) throws OXException {
        final int contextId = session.getContextId();
        final int userId = session.getUserId();
        final ConnectionHelper connectionHelper = new ConnectionHelper(session);
        final Connection connection = connectionHelper.getWritable();
        try {
            if (contact.containsParentFolderID() && FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID != contact.getParentFolderID()) {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(contactId), I(contextId));
            }
            final Contact c = executor.selectSingle(connection, Table.CONTACTS, contextId, contactId, new ContactField[] { ContactField.INTERNAL_USERID, ContactField.FOLDER_ID, ContactField.LAST_MODIFIED });
            if (c == null) {
                throw ContactExceptionCodes.CONTACT_NOT_FOUND.create(I(contactId), I(contextId));
            }
            if (!c.containsInternalUserId() || userId != c.getInternalUserId()) {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(contactId), I(contextId));
            }
            if (!c.containsParentFolderID() || FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID != c.getParentFolderID()) {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(contactId), I(contextId));
            }
            if (!c.containsLastModified() || (null != lastRead && lastRead.before(c.getLastModified()))) {
                throw ContactExceptionCodes.OBJECT_HAS_CHANGED.create();
            }
            updateGuestContact(contextId, userId, contactId, contact, connection);
            connectionHelper.commit();
        } catch (DataTruncation e) {
            throw Tools.getTruncationException(session, connection, e, contact, Table.CONTACTS);
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            connectionHelper.backWritable();
        }
    }

    private void updateGuestContact(final int contextId, final int userId, final int contactId, final Contact contact, Connection connection) throws OXException {
        Connection con = connection;
        boolean newCon = false;
        DatabaseService dbService = null;
        if (null == con) {
            newCon = true;
            dbService = RdbServiceLookup.getService(DatabaseService.class);
            if (null == dbService) {
                throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(DatabaseService.class);
            }
            con = dbService.getWritable(contextId);
        }

        Date now = new Date();
        contact.setLastModified(now);

        QueryFields queryFields = new QueryFields(Mappers.CONTACT.getAssignedFields(contact));
        try {
            Contact toUpdate = executor.selectSingle(con, Table.CONTACTS, contextId, contactId, new ContactField[] { ContactField.CREATED_BY, ContactField.IMAGE_LAST_MODIFIED });
            if (null == toUpdate) {
                throw ContactExceptionCodes.CONTACT_NOT_FOUND.create(I(contactId), I(contextId));
            }
            if (toUpdate.getCreatedBy() != userId) {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(contactId), I(contextId));
            }
            long imageLastModified = Long.MIN_VALUE;
            if (toUpdate.getImageLastModified() != null) {
                imageLastModified = toUpdate.getImageLastModified().getTime();
            }
            updateImageIfNeeded(contextId, contactId, contact, now, imageLastModified, queryFields, con);
            executor.update(con, Table.CONTACTS, contextId, contactId, now.getTime(), contact, Fields.sort(queryFields.getContactDataFields()));
        } catch (SQLException e) {
            if (newCon) {
                Databases.rollback(con);
            }
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            if (newCon && null != dbService) {
                dbService.backWritable(contextId, con);
            }
        }
    }

    @Override
    public void updateGuestContact(final int contextId, final int contactId, final Contact contact, final Connection con) throws OXException {
        Date now = new Date();
        contact.setLastModified(now);

        QueryFields queryFields = new QueryFields(Mappers.CONTACT.getAssignedFields(contact));
        try {
            updateImageIfNeeded(contextId, contactId, contact, now, Long.MIN_VALUE, queryFields, con);
            executor.update(con, Table.CONTACTS, contextId, contactId, now.getTime(), contact, Fields.sort(queryFields.getContactDataFields()));
        } catch (SQLException e) {
            throw ContactExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        }
    }

    private void updateImageIfNeeded(int contextID, int objectID, Contact contact, Date now, long maxLastModified, QueryFields queryFields, Connection connection) throws SQLException, OXException {
        if (queryFields.hasImageData()) {
            contact.setImageLastModified(now);
            queryFields.update(Mappers.CONTACT.getAssignedFields(contact));
            if (null == contact.getImage1()) {
                // delete previous image if exists
                executor.deleteSingle(connection, Table.IMAGES, contextID, objectID, maxLastModified);
                contact.setNumberOfImages(0);
            } else {
                checkImageSize(contact);
                if (null != executor.selectSingle(connection, Table.IMAGES, contextID, objectID, new ContactField[] { ContactField.OBJECT_ID })) {
                    // update previous image
                    if (0 == executor.update(connection, Table.IMAGES, contextID, objectID, maxLastModified, contact, queryFields.getImageDataFields(true))) {
                        throw ContactExceptionCodes.OBJECT_HAS_CHANGED.create(I(contextID), Integer.valueOf(objectID));
                    }
                } else {
                    // create new image
                    final Contact imageData = new Contact();
                    imageData.setObjectID(objectID);
                    imageData.setContextId(contextID);
                    imageData.setImage1(contact.getImage1());
                    imageData.setImageContentType(contact.getImageContentType());
                    imageData.setImageLastModified(contact.getImageLastModified());
                    imageData.setNumberOfImages(1);
                    executor.insert(connection, Table.IMAGES, imageData, Fields.IMAGE_DATABASE_ARRAY);
                }
            }
        }
    }

    @Override
    public boolean supports(final ContactField... fields) {
        boolean supports = true;
        for (final ContactField contactField : fields) {
            final Mapping<? extends Object, Contact> opt = Mappers.CONTACT.opt(contactField);
            if (opt == null) {
                LOG.debug("Storage is unable to support provided fields.");
                supports = false;
                break;
            }
        }
        return supports;
    }
}
