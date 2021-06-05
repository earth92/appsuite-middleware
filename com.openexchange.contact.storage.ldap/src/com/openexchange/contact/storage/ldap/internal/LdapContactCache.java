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

package com.openexchange.contact.storage.ldap.internal;

import static com.openexchange.java.Autoboxing.I;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheService;
import com.openexchange.contact.SortOptions;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.Contact;
import com.openexchange.search.CompositeSearchTerm;
import com.openexchange.search.Operand;
import com.openexchange.search.SearchTerm;
import com.openexchange.search.SingleSearchTerm;
import com.openexchange.timer.TimerService;
import com.openexchange.tools.iterator.SearchIterator;

/**
 * {@link LdapContactCache}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class LdapContactCache {

    static final Logger LOG = org.slf4j.LoggerFactory.getLogger(LdapContactCache.class);

    private static final EnumSet<ContactField> CACHED_FIELDS = EnumSet.of(
        ContactField.CONTEXTID, ContactField.FOLDER_ID, ContactField.OBJECT_ID, ContactField.INTERNAL_USERID, ContactField.UID,
        ContactField.LAST_MODIFIED, ContactField.CREATION_DATE, ContactField.MODIFIED_BY, ContactField.CREATED_BY,
        ContactField.PRIVATE_FLAG, ContactField.MARK_AS_DISTRIBUTIONLIST, ContactField.DISTRIBUTIONLIST,
        ContactField.EMAIL1, ContactField.EMAIL2, ContactField.EMAIL3,
        ContactField.DISPLAY_NAME, ContactField.FILE_AS, ContactField.COMPANY, ContactField.YOMI_COMPANY,
        ContactField.SUR_NAME, ContactField.GIVEN_NAME, ContactField.YOMI_LAST_NAME, ContactField.YOMI_FIRST_NAME
    );
    static final ContactField[] CACHED_FIELDS_ARRAY = CACHED_FIELDS.toArray(new ContactField[CACHED_FIELDS.size()]);

    final LdapContactStorage storage;
    private final int refreshInterval;
    final boolean incrementalSync;
    private volatile ContactLoader loader;

    /**
     * Initializes a new {@link LdapContactCache}.
     *
     * @param storage the storage to cache the data for
     * @param refreshInterval the refresh interval in ms
     * @throws OXException
     */
    public LdapContactCache(LdapContactStorage storage, int refreshInterval, boolean incrementalSync, Properties properties) throws OXException {
        super();
        this.storage = storage;
        this.refreshInterval = refreshInterval;
        this.loader = null;
        this.incrementalSync = incrementalSync;
        initCache(getRegionName(), properties);
    }

    String getRegionName() throws OXException {
        return "CONTACT_LDAP_" + storage.getContextID() + "_" + storage.getFolderID();
    }

    /**
     * Gets the contact to which the specified object ID is mapped.
     *
     * @param objectID the contact's object ID
     * @return the contact, or <code>null</code> if not found.
     * @throws OXException
     */
    public Contact get(int objectID) throws OXException {
        return (Contact)getCache().get(Integer.valueOf(objectID));
    }

    /**
     * Gets a list of the contact with the specified object IDs.
     *
     * @param objectIDs
     * @return
     * @throws OXException
     */
    public List<Contact> list(int[] objectIDs) throws OXException {
        Cache cache = getCache();
        List<Contact> contacts = new ArrayList<Contact>(objectIDs.length);
        for (int objectID : objectIDs) {
            contacts.add((Contact)cache.get(Integer.valueOf(objectID)));
        }
        return contacts;
    }

    /**
     * Gets all contacts.
     *
     * @return the contacts
     * @throws OXException
     */
    public List<Contact> values() throws OXException {
        Collection<Serializable> values = getCache().values();
        if (null == values) {
            return Collections.emptyList();
        }
        List<Contact> contacts = new ArrayList<Contact>(values.size());
        for (Serializable value : values) {
            contacts.add((Contact)value);
        }
        return contacts;
    }

    /**
     * @return If the cache is already filled with objects.
     */
    public boolean isCacheReady() {
        return loader != null;
    }

    /**
     * Gets a value indicating whether all of the supplied fields are present
     * in the cache or not.
     *
     * @param requestedFields the contact fields
     * @return <code>true</code>, if the fields are cached, <code>false</code>,
     *         otherwise
     */
    public static boolean isCached(ContactField[] requestedFields) {
        return null != requestedFields && CACHED_FIELDS.containsAll(Arrays.asList(requestedFields));
    }

    /**
     * Creates an array of contact fields that are not covered by the cached
     * data.
     *
     * @param requestedFields the fields to check
     * @param mandatoryFields fields that always should be added to the result
     * @return the unknown fields
     */
    public static ContactField[] getUnknownFields(ContactField[] requestedFields, ContactField...mandatoryFields) {
        if (null == requestedFields || 0 >= requestedFields.length) {
            return requestedFields;
        }

        Set<ContactField> unknownFields = new HashSet<ContactField>();
        for (ContactField requestedField : requestedFields) {
            if (false == CACHED_FIELDS.contains(requestedField)) {
                unknownFields.add(requestedField);
            }
        }
        if (null != mandatoryFields && 0 < mandatoryFields.length) {
            for (ContactField mandatoryField : mandatoryFields) {
                unknownFields.add(mandatoryField);
            }
        }
        return unknownFields.toArray(new ContactField[unknownFields.size()]);
    }

    /**
     * Gets a value indicating whether all of the fields referred by the
     * supplied search term are present in the cache or not.
     *
     * @param term the term to check
     * @return <code>true</code>, if the fields are cached, <code>false</code>,
     *         otherwise
     * @throws OXException
     */
    public static boolean isCached(SearchTerm<?> term) throws OXException {
        if (null != term) {
            if (SingleSearchTerm.class.isInstance(term)) {
                return isCached((SingleSearchTerm)term);
            } else if (CompositeSearchTerm.class.isInstance(term)) {
                return isCached((CompositeSearchTerm)term);
            } else {
                throw new IllegalArgumentException("Need either an 'SingleSearchTerm' or 'CompositeSearchTerm'.");
            }
        }
        return false;
    }

    private static boolean isCached(SingleSearchTerm term) {
        if (null != term.getOperands()) {
            for (Operand<?> operand : term.getOperands()) {
                if (Operand.Type.COLUMN.equals(operand.getType())) {
                    if (ContactField.class.isInstance(operand.getValue())) {
                        if (false == CACHED_FIELDS.contains(operand.getValue())) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isCached(CompositeSearchTerm term) throws OXException {
        if (null != term.getOperands()) {
            for (SearchTerm<?> searchTerm : term.getOperands()) {
                if (false == isCached(searchTerm)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void initCache(String regionName, Properties properties) throws OXException {
        Properties customProperties = new Properties();
        for (Entry<Object, Object> entry : properties.entrySet()) {
            if (null != entry.getKey() && String.class.isInstance(entry.getKey())) {
                customProperties.put(((String)entry.getKey()).replace("[REGIONNAME]", regionName), entry.getValue());
            }
        }
        LdapServiceLookup.getService(CacheService.class).loadConfiguration(customProperties);
    }

    private Cache getCache() throws OXException {
        if (null == this.loader) {
            synchronized (this) {
                if (null == this.loader) {
                    /*
                     * blocking load on first access, scheduled refresh afterwards
                     */
                    loader = new ContactLoader();
                    loader.reloadContacts();
                    LdapServiceLookup.getService(TimerService.class).scheduleWithFixedDelay(
                        loader, refreshInterval, refreshInterval, TimeUnit.MILLISECONDS);
                }
            }
        }
        return LdapServiceLookup.getService(CacheService.class).getCache(getRegionName());
    }

    private final class ContactLoader implements Runnable {

        private Date lastModified;

        public ContactLoader() {
            super();
            this.lastModified = null;
        }

        @Override
        public void run() {
            try {
                if (false == incrementalSync || null == this.lastModified || 1000 >= lastModified.getTime()) {
                    reloadContacts();
                } else {
                    refreshContacts();
                }
            } catch (OXException e) {
                LOG.error("", e);
            }
        }

        private void refreshContacts() throws OXException {
            Cache cache = LdapServiceLookup.getService(CacheService.class).getCache(getRegionName());
            LOG.debug("Refreshing contacts.");
            long start = System.currentTimeMillis();
            int deleted = 0;
            int updated = 0;
            Date newLastModified = new Date(0);
            SearchIterator<Contact> modifiedContacts = null;
            SearchIterator<Contact> deletedContacts = null;
            try {
                modifiedContacts = storage.doModified(null, Integer.toString(storage.getFolderID()), lastModified,
                    CACHED_FIELDS_ARRAY, SortOptions.EMPTY);
                while (modifiedContacts.hasNext()) {
                    Contact contact = modifiedContacts.next();
                    newLastModified = getLatestModified(newLastModified, contact);
                    cache.put(Integer.valueOf(contact.getObjectID()), contact, false);
                    updated++;
                }
                deletedContacts = storage.doDeleted(null, Integer.toString(storage.getFolderID()), lastModified, new ContactField[] {
                    ContactField.OBJECT_ID, ContactField.LAST_MODIFIED }, SortOptions.EMPTY);
                while (deletedContacts.hasNext()) {
                    Contact contact = deletedContacts.next();
                    newLastModified = getLatestModified(newLastModified, contact);
                    cache.remove(Integer.valueOf(contact.getObjectID()));
                    deleted++;
                }
            } finally {
                Tools.close(modifiedContacts);
                Tools.close(deletedContacts);
            }
            if (0 < updated || 0 < deleted) {
                this.lastModified = newLastModified;
                LOG.debug("Contacts refreshed, got {} modified and {} deleted contacts in {}ms.", I(updated), I(deleted), Long.valueOf(System.currentTimeMillis() - start));
            } else {
                LOG.debug("No changes detected, check took {}ms.", Long.valueOf(System.currentTimeMillis() - start));
            }
        }

        void reloadContacts() throws OXException {
            Cache cache = LdapServiceLookup.getService(CacheService.class).getCache(getRegionName());
            LOG.debug("Reloading contacts.");
            Date start = new Date();
            int added = 0;
            Date newLastModified = new Date(0);
            SearchIterator<Contact> contacts = null;
            try {
                contacts = storage.doAll(null, Integer.toString(storage.getFolderID()), CACHED_FIELDS_ARRAY, SortOptions.EMPTY);
                while (contacts.hasNext()) {
                    Contact contact = contacts.next();
                    newLastModified = getLatestModified(newLastModified, contact);
                    cache.put(Integer.valueOf(contact.getObjectID()), contact, false);
                    added++;
                }
            } finally {
                Tools.close(contacts);
            }
            this.lastModified = newLastModified;
            LOG.debug("Contacts reloaded, got {} entries in {}ms.", I(added), Long.valueOf(System.currentTimeMillis() - start.getTime()));
        }

        private Date getLatestModified(Date lastModified, Contact contact) {
            return null != contact.getLastModified() && lastModified.after(contact.getLastModified()) ?
                lastModified : contact.getLastModified();
        }

    }

}
