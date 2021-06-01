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

package com.openexchange.user.copy.internal.contact;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import static com.openexchange.user.copy.internal.CopyTools.getIntOrNegative;
import static com.openexchange.user.copy.internal.CopyTools.setBinaryOrNull;
import static com.openexchange.user.copy.internal.CopyTools.setIntOrNull;
import static com.openexchange.user.copy.internal.CopyTools.setStringOrNull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.helpers.ContactDatabaseGetter;
import com.openexchange.groupware.contact.helpers.ContactDatabaseSetter;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.contact.helpers.ContactSwitcher;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.DistributionListEntryObject;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.impl.IDGenerator;
import com.openexchange.java.Strings;
import com.openexchange.java.util.UUIDs;
import com.openexchange.user.copy.CopyUserTaskService;
import com.openexchange.user.copy.ObjectMapping;
import com.openexchange.user.copy.UserCopyExceptionCodes;
import com.openexchange.user.copy.internal.CopyTools;
import com.openexchange.user.copy.internal.IntegerMapping;
import com.openexchange.user.copy.internal.connection.ConnectionFetcherTask;
import com.openexchange.user.copy.internal.context.ContextLoadTask;
import com.openexchange.user.copy.internal.folder.FolderCopyTask;
import com.openexchange.user.copy.internal.user.UserCopyTask;

/**
 * {@link ContactCopyTask}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 */
public class ContactCopyTask implements CopyUserTaskService {

    private static final String SELECT_DLIST =
        "SELECT " +
            "intfield01, intfield02, intfield03, intfield04, field01, " +
            "field02, field03, field04 " +
            "FROM " +
            "prg_dlist " +
            "WHERE " +
            "cid = ? " +
            "AND " +
            "intfield01 IN (#IDS#)";

    private static final String SELECT_IMAGE =
        "SELECT " +
            "image1, changing_date, mime_type " +
            "FROM " +
            "prg_contacts_image " +
            "WHERE " +
            "cid = ? AND intfield01 = ?";

    private static final String INSERT_IMAGE =
        "INSERT INTO " +
            "prg_contacts_image " +
            "(intfield01, image1, changing_date, mime_type, cid) " +
            "VALUES " +
            "(?, ?, ?, ?, ?)";

    private static final String INSERT_DLIST =
        "INSERT INTO " +
            "prg_dlist " +
            "(intfield01, intfield02, intfield03, intfield04, field01, field02, field03, field04, cid, uuid) " +
            "VALUES " +
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public ContactCopyTask() {
        super();
    }

    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#getAlreadyCopied()
     */
    @Override
    public String[] getAlreadyCopied() {
        return new String[] {
            UserCopyTask.class.getName(),
            ContextLoadTask.class.getName(),
            ConnectionFetcherTask.class.getName(),
            FolderCopyTask.class.getName()
        };
    }

    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#getObjectName()
     */
    @Override
    public String getObjectName() {
        return Contact.class.getName();
    }

    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#copyUser(java.util.Map)
     */
    @Override
    public IntegerMapping copyUser(final Map<String, ObjectMapping<?>> copied) throws OXException {
        final CopyTools copyTools = new CopyTools(copied);
        final Integer srcCtxId = copyTools.getSourceContextId();
        final Integer dstCtxId = copyTools.getDestinationContextId();
        final Integer srcUsrId = copyTools.getSourceUserId();
        final Integer dstUsrId = copyTools.getDestinationUserId();
        final Connection srcCon = copyTools.getSourceConnection();
        final Connection dstCon = copyTools.getDestinationConnection();
        final ObjectMapping<FolderObject> folderMapping = copyTools.getFolderMapping();
        final List<Integer> folderIds = new ArrayList<Integer>(folderMapping.getSourceKeys());
        final List<ContactField> contactFields = getCleanedContactFields();
        final Map<Integer, Contact> contacts = loadContactsFromDB(contactFields, folderIds, srcCon, i(srcCtxId), i(srcUsrId));
        final IntegerMapping mapping = new IntegerMapping();

        if (!contacts.isEmpty()) {
            loadAdditionalContentsFromDB(contacts, srcCon, srcCtxId.intValue());
            exchangeIds(contacts, folderMapping, dstCon, i(dstCtxId), i(srcUsrId), i(dstUsrId));
            writeContactsToDB(contacts, contactFields, dstCon);
            writeAdditionalContentsToDB(contacts, dstCon, i(dstCtxId));

            for (Map.Entry<Integer, Contact> entry : contacts.entrySet()) {
                Integer contactId = entry.getKey();
                Contact contact = entry.getValue();
                mapping.addMapping(contactId, I(contact.getObjectID()));
            }
        }

        return mapping;
    }

    private void writeAdditionalContentsToDB(final Map<Integer, Contact> contacts, final Connection con, final int cid) throws OXException {
        writeImagesToDB(contacts, con, cid);
        writeDistributionListsToDB(contacts, con, cid);
    }

    private void writeDistributionListsToDB(final Map<Integer, Contact> contacts, final Connection con, final int cid) throws OXException {
        /*
         * intfield01 = contactId
         * intfield02 = entryId
         * intfield03 = mailField
         * intfield04 = folderId
         * field01 = displayName
         * field02 = lastName
         * field03 = firstName
         * field04 = mailAddress
         */
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(INSERT_DLIST);
            for (final Contact contact : contacts.values()) {
                final DistributionListEntryObject[] distributionList = contact.getDistributionList();
                if (distributionList != null && distributionList.length > 0) {
                    for (final DistributionListEntryObject entry : distributionList) {
                        int i = 1;
                        stmt.setInt(i++, contact.getObjectID());
                        setIntOrNull(i++, stmt, entry.getEntryID());
                        setIntOrNull(i++, stmt, entry.getEmailfield());
                        setIntOrNull(i++, stmt, entry.getFolderID());
                        setStringOrNull(i++, stmt, entry.getDisplayname());
                        setStringOrNull(i++, stmt, entry.getLastname());
                        setStringOrNull(i++, stmt, entry.getFirstname());
                        setStringOrNull(i++, stmt, entry.getEmailaddress());
                        stmt.setInt(i++, cid);
                        setBinaryOrNull(i++, stmt, UUIDs.toByteArray(UUID.randomUUID()));

                        stmt.addBatch();
                    }
                }
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private void writeImagesToDB(final Map<Integer, Contact> contacts, final Connection con, final int cid) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(INSERT_IMAGE);
            for (final Contact contact : contacts.values()) {
                if (contact.containsImage1()) {
                    stmt.setInt(1, contact.getObjectID());
                    stmt.setBytes(2, contact.getImage1());
                    stmt.setLong(3, contact.getImageLastModified().getTime());
                    stmt.setString(4, contact.getImageContentType());
                    stmt.setInt(5, cid);

                    stmt.addBatch();
                }
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    @SuppressWarnings("deprecation")
    private void writeContactsToDB(final Map<Integer, Contact> contacts, final List<ContactField> contactFields, final Connection con) throws OXException {
        final String insertSql = buildInsertContactsSql(contactFields);
        PreparedStatement stmt = null;
        try {
            final ContactSwitcher getter = new ContactDatabaseGetter();
            stmt = con.prepareStatement(insertSql);
            for (Contact contact : contacts.values()) {
                int i = 1;
                for (final ContactField field : contactFields) {
                    if (field.isDBField() && !field.getDbName().equals("value")) {
                        if (contact.contains(field.getNumber())) {
                            final Object value = field.doSwitch(getter, contact);
                            stmt.setObject(i++, value, field.getSQLType());
                        } else {
                            stmt.setNull(i++, field.getSQLType());
                        }
                    }
                }
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private void exchangeIds(final Map<Integer, Contact> contacts, final ObjectMapping<FolderObject> folderMapping, final Connection con, final int cid, final int oldUid, final int newUid) throws OXException {
        for (Contact contact : contacts.values()) {
            try {
                final int newContactId = IDGenerator.getId(cid, com.openexchange.groupware.Types.CONTACT, con);
                contact.setObjectID(newContactId);

                final int oldParentFolder = contact.getParentFolderID();
                if (oldParentFolder == 6) {
                    /*
                     * Global address book - this is the users contact.
                     */
                } else {
                    final FolderObject oldFolder = folderMapping.getSource(oldParentFolder);
                    final FolderObject newFolder = folderMapping.getDestination(oldFolder);
                    contact.setParentFolderID(newFolder.getObjectID());
                }

                final int contactUid = contact.getInternalUserId();
                if (contactUid > 0) {
                    /*
                     * If this is the old users contact, we set the userId to the users new one.
                     * If it contains any other userId we set it to zero.
                     */
                    if (contactUid == oldUid) {
                        contact.setInternalUserId(newUid);
                    } else {
                        contact.setInternalUserId(0);
                    }
                }

                contact.setCreatedBy(newUid);
                contact.setModifiedBy(newUid);
                contact.setContextId(cid);
            } catch (SQLException e) {
                throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
            }
        }

        for (Contact contact : contacts.values()) {
            if (contact.containsDistributionLists()) {
                final DistributionListEntryObject[] distributionList = contact.getDistributionList();
                for (final DistributionListEntryObject entry : distributionList) {
                    final int entryId = entry.getEntryID();
                    if (entryId > 0) {
                        /*
                         * This list entry refers to an existing contact.
                         * If this contact isn't visible to the moved user, the entry becomes an external member.
                         */
                        final Contact dlistContact = contacts.get(I(entryId));
                        if (dlistContact == null) {
                            entry.setEntryID(-1);
                            entry.setEmailfield(-1);
                            entry.setFolderID(-1);
                        } else {
                            entry.setEntryID(dlistContact.getObjectID());
                        }
                    }
                }
            }
        }
    }

    void loadAdditionalContentsFromDB(final Map<Integer, Contact> contacts, final Connection con, final int cid) throws OXException {
        loadDistributionListsFromDB(contacts, con, cid);
        loadImagesFromDB(contacts, con, cid);
    }

    void loadImagesFromDB(final Map<Integer, Contact> contacts, final Connection con, final int cid) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            for (Entry<Integer, Contact> entry : contacts.entrySet()) {
                Integer contactId = entry.getKey();
                Contact contact = entry.getValue();
                stmt = con.prepareStatement(SELECT_IMAGE);
                stmt.setInt(1, cid);
                stmt.setInt(2, i(contactId));

                rs = stmt.executeQuery();
                if (rs.next()) {
                    final int numberOfImages = contact.getNumberOfImages();
                    contact.setImage1(rs.getBytes(1));
                    contact.setImageLastModified(new Date(rs.getLong(2)));
                    contact.setImageContentType(rs.getString(3));
                    /*
                     * contact.setImage1() modifies numberOfImages, so we restore the original value.
                     */
                    contact.setNumberOfImages(numberOfImages);
                }
                Databases.closeSQLStuff(rs, stmt);
            }
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    void loadDistributionListsFromDB(final Map<Integer, Contact> contacts, final Connection con, final int cid) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final String query = CopyTools.replaceIdsInQuery("#IDS#", SELECT_DLIST, contacts.keySet());
        final Map<Integer, List<DistributionListEntryObject>> dlistMap = new HashMap<Integer, List<DistributionListEntryObject>>();
        try {
            stmt = con.prepareStatement(query);
            stmt.setInt(1, cid);

            rs = stmt.executeQuery();
            while (rs.next()) {
                int i = 1;
                final DistributionListEntryObject entry = new DistributionListEntryObject();
                final int contactId = rs.getInt(i++);
                entry.setEntryID(getIntOrNegative(i++, rs));
                entry.setEmailfield(getIntOrNegative(i++, rs));
                entry.setFolderID(getIntOrNegative(i++, rs));
                entry.setDisplayname(rs.getString(i++));
                entry.setLastname(rs.getString(i++));
                entry.setFirstname(rs.getString(i++));
                entry.setEmailaddress(rs.getString(i++));

                List<DistributionListEntryObject> list = dlistMap.get(I(contactId));
                if (list == null) {
                    list = new ArrayList<DistributionListEntryObject>();
                    dlistMap.put(I(contactId), list);
                }

                list.add(entry);
            }
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }

        for (Map.Entry<Integer, List<DistributionListEntryObject>> entry : dlistMap.entrySet()) {
            Integer contactId = entry.getKey();
            List<DistributionListEntryObject> list = entry.getValue();
            DistributionListEntryObject[] entryArray = list.toArray(new DistributionListEntryObject[list.size()]);

            Contact contact = contacts.get(contactId);
            contact.setDistributionList(entryArray);
        }
    }

    @SuppressWarnings("deprecation")
    Map<Integer, Contact> loadContactsFromDB(final List<ContactField> contactFields, final List<Integer> folderIds, final Connection con, final int cid, final int uid) throws OXException {
        final Map<Integer, Contact> contacts = new HashMap<Integer, Contact>();
        final String selectSql = buildSelectContactsSql(contactFields, folderIds);
        final ContactSwitcher setter = new ContactDatabaseSetter();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(selectSql);
            stmt.setInt(1, cid);
            stmt.setInt(2, uid);

            rs = stmt.executeQuery();
            while (rs.next()) {
                final Contact contact = new Contact();
                for (final ContactField field : contactFields) {
                    if (field.isDBField() && !field.getDbName().equals("value")) {
                        final Object value = rs.getObject(field.getDbName());
                        if (!rs.wasNull()) {
                            field.doSwitch(setter, contact, value);
                        }
                    }
                }

                contacts.put(I(contact.getObjectID()), contact);
            }
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }

        return contacts;
    }

    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#done(java.util.Map, boolean)
     */
    @Override
    public void done(final Map<String, ObjectMapping<?>> copied, final boolean failed) {}

    @SuppressWarnings("deprecation")
    private String buildSelectContactsSql(final List<ContactField> contactFields, final List<Integer> folderIds) {
        final StringBuilder sb = new StringBuilder("SELECT ");
        boolean first = true;
        for (final ContactField field : contactFields) {
            if (field.isDBField() && !field.getDbName().equals("value")) {
                final String dbName = field.getDbName();
                if (first) {
                    sb.append(dbName);
                    first = false;
                } else {
                    sb.append(", ");
                    sb.append(dbName);
                }
            }
        }
        sb.append(" FROM prg_contacts WHERE cid = ? AND ((fid = 6 AND userid = ?) OR fid IN (#IDS#))");
        final String query = CopyTools.replaceIdsInQuery("#IDS#", sb.toString(), folderIds);

        return query;
    }

    @SuppressWarnings("deprecation")
    private String buildInsertContactsSql(final List<ContactField> contactFields) {
        final StringBuilder sb = new StringBuilder("INSERT INTO prg_contacts (");
        boolean first = true;
        int count = 0;
        for (final ContactField field : contactFields) {
            if (field.isDBField() && !field.getDbName().equals("value")) {
                count++;
                final String dbName = field.getDbName();
                if (first) {
                    sb.append(dbName);
                    first = false;
                } else {
                    sb.append(", ");
                    sb.append(dbName);
                }
            }
        }
        sb.append(") VALUES (");
        for (int i = 0; i < count; i++) {
            if (i == 0) {
                sb.append('?');
            } else {
                sb.append(", ?");
            }
        }
        sb.append(')');

        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    List<ContactField> getCleanedContactFields() {
        final List<ContactField> fields = new ArrayList<ContactField>();
        final List<String> dbFields = new ArrayList<String>();
        for (final ContactField field : ContactField.values()) {
            if (field.isDBField()) {
                final String fieldName = field.getDbName();
                if (Strings.isNotEmpty(fieldName) && !dbFields.contains(fieldName)) {
                    fields.add(field);
                    dbFields.add(field.getDbName());
                }
            }
        }

        return fields;
    }

}
