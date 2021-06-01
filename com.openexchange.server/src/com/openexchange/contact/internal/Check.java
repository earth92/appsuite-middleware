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

package com.openexchange.contact.internal;

import static com.openexchange.contact.internal.Tools.parse;
import static com.openexchange.java.Autoboxing.I;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.contact.SortOptions;
import com.openexchange.contact.internal.mapping.ContactMapper;
import com.openexchange.contact.storage.ContactStorage;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.ContactExceptionCodes;
import com.openexchange.groupware.contact.Search;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.search.ContactSearchObject;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.search.CompositeSearchTerm;
import com.openexchange.search.CompositeSearchTerm.CompositeOperation;
import com.openexchange.search.SingleSearchTerm.SingleOperation;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.session.Session;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.oxfolder.OXFolderProperties;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link Check} - Static utility functions for the contact service.
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public final class Check {

    public static void argNotNull(final Object object, final String argumentName) {
		if (null == object) {
			throw new IllegalArgumentException("the passed argument '" + argumentName + "' may not be null");
		}
	}

	public static void hasStorages(final Map<ContactStorage, List<String>> storages) throws OXException {
	    if (null == storages || 0 == storages.size()) {
	        throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(ContactStorage.class.getName());
        }
	}

	public static void validateProperties(final Contact contact) throws OXException {
		ContactMapper.getInstance().validateAll(contact);
	}

	public static void isNotPrivate(final Contact contact, final Session session, final String folderID) throws OXException {
		if (contact.containsPrivateFlag()) {
			throw ContactExceptionCodes.NO_ACCESS_PERMISSION.create(I(parse(folderID)), I(session.getContextId()), I(session.getUserId()));
		}
	}

	public static void canReadOwn(final EffectivePermission permission, final Session session, final String folderID) throws OXException {
		if (false == permission.canReadOwnObjects()) {
			throw ContactExceptionCodes.NO_ACCESS_PERMISSION.create(I(parse(folderID)), I(session.getContextId()), I(session.getUserId()));
		}
	}

    public static void canWriteOwn(final EffectivePermission permission, final Session session) throws OXException {
        if (false == permission.canWriteOwnObjects()) {
            throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(session.getUserId()), I(session.getContextId()));
        }
        checkForSubscription(session, String.valueOf(permission.getFuid()), session.getContextId());
    }

    public static void canWriteAll(final EffectivePermission permission, final Session session) throws OXException {
        if (false == permission.canWriteAllObjects()) {
            throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(session.getUserId()), I(session.getContextId()));
        }
        checkForSubscription(session, String.valueOf(permission.getFuid()), session.getContextId());
    }

	public static void canReadAll(final EffectivePermission permission, final Session session, final String folderID) throws OXException {
		if (false == permission.canReadAllObjects()) {
			throw ContactExceptionCodes.NO_ACCESS_PERMISSION.create(I(parse(folderID)), I(session.getContextId()), I(session.getUserId()));
		}
	}

    public static void canCreateObjects(final EffectivePermission permission, final Session session, final String folderID) throws OXException {
        if (false == permission.canCreateObjects()) {
            throw ContactExceptionCodes.NO_CREATE_PERMISSION.create(I(parse(folderID)), I(session.getContextId()), I(session.getUserId()));
        }
        checkForSubscription(session, folderID, session.getContextId());
    }

    public static void canDeleteOwn(final EffectivePermission permission, final Session session, final String folderID) throws OXException {
        if (false == permission.canDeleteOwnObjects()) {
            throw ContactExceptionCodes.NO_DELETE_PERMISSION.create(I(parse(folderID)), I(session.getContextId()), I(session.getUserId()));
        }
        checkForSubscription(session, folderID, session.getContextId());
    }

    public static void canDeleteAll(final EffectivePermission permission, final Session session, final String folderID) throws OXException {
        if (false == permission.canDeleteAllObjects()) {
            throw ContactExceptionCodes.NO_DELETE_PERMISSION.create(I(parse(folderID)), I(session.getContextId()), I(session.getUserId()));
        }
        checkForSubscription(session, folderID, session.getContextId());
    }

    public static void isContactFolder(final FolderObject folder, final Session session) throws OXException {
		if (FolderObject.CONTACT != folder.getModule()) {
			throw ContactExceptionCodes.NON_CONTACT_FOLDER.create(I(folder.getObjectID()), I(session.getContextId()), I(session.getUserId()));
		}
    }

    public static void contactNotNull(final Contact contact, final int contextID, final int id) throws OXException {
        if (null == contact) {
            throw ContactExceptionCodes.CONTACT_NOT_FOUND.create(I(id), I(contextID));
        }
    }

    public static void lastModifiedBefore(final Contact contact, final Date lastRead) throws OXException {
    	if (lastRead.before(contact.getLastModified())) {
			throw ContactExceptionCodes.OBJECT_HAS_CHANGED.create();
		}
    }

    public static void folderEquals(final Contact contact, final String folderID, final int contextID) throws OXException {
    	if (contact.getParentFolderID() != parse(folderID)) {
			throw ContactExceptionCodes.NOT_IN_FOLDER.create(I(contact.getObjectID()), I(parse(folderID)), I(contextID));
		}
    }

    public static void noPrivateInPublic(final FolderObject folder, final Contact contact, final Session session) throws OXException {
    	if (FolderObject.PUBLIC == folder.getType() && contact.getPrivateFlag()) {
            throw ContactExceptionCodes.PFLAG_IN_PUBLIC_FOLDER.create(I(folder.getObjectID()), I(session.getContextId()), I(session.getUserId()));
        }
    }

    @SuppressWarnings("deprecation")
    public static void validateSearch(final ContactSearchObject contactSearch) throws OXException {
		Search.checkPatternLength(contactSearch);
		if (0 != contactSearch.getIgnoreOwn() || null != contactSearch.getAnniversaryRange() ||
				null != contactSearch.getBirthdayRange() || null != contactSearch.getBusinessPostalCodeRange() ||
				null != contactSearch.getCreationDateRange() || null != contactSearch.getDynamicSearchField() ||
				null != contactSearch.getDynamicSearchFieldValue() || null != contactSearch.getFrom() ||
				null != contactSearch.getLastModifiedRange() || null != contactSearch.getNumberOfEmployeesRange() ||
				null != contactSearch.getSalesVolumeRange() ||
				null != contactSearch.getOtherPostalCodeRange() || null != contactSearch.getPrivatePostalCodeRange()) {
			throw new UnsupportedOperationException("not implemented");
		}
	}

	/**
	 * Performs validation checks prior performing write operations on the global address book, throwing appropriate exceptions if
	 * checks fail.
	 *
	 * @param storage The queried storage
	 * @param session The session
	 * @param folderID The folder ID
	 * @param update The contact to be written
	 * @param original The original contact being updated, or <code>null</code> during contact creation
	 * @throws OXException
	 */
	public static void canWriteInGAB(ContactStorage storage, Session session, String folderID, Contact update, Contact original) throws OXException {
		if (FolderObject.SYSTEM_LDAP_FOLDER_ID == parse(folderID)) {
		    /*
		     * check legacy edit flag
		     */
	        if (false == OXFolderProperties.isEnableInternalUsersEdit()) {
	            throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(I(update.getObjectID()), I(session.getContextId()));
	        }
	        /*
             * further checks for mandatory properties
             */
            if (update.containsSurName() && Tools.isEmpty(update.getSurName())) {
                throw ContactExceptionCodes.LAST_NAME_MANDATORY.create();
            } else if (update.containsGivenName() && Tools.isEmpty(update.getGivenName())) {
                throw ContactExceptionCodes.FIRST_NAME_MANDATORY.create();
            }
			/*
			 * check display name
			 */
			checkDisplayNameUniqueness(storage, session, folderID, update, original);
	        /*
	         * check primary mail address
	         */
	        if (update.containsEmail1() && (null == original || null == original.getEmail1() || false == original.getEmail1().equals(update.getEmail1()))) {
	        	if (Tools.getContext(session).getMailadmin() != session.getUserId()) {
	        		throw ContactExceptionCodes.NO_PRIMARY_EMAIL_EDIT.create(
	        		    I(session.getContextId()), I(update.getObjectID()), I(session.getUserId()));
	        	}
	        }
		}
	}

    /**
     * Ensures that the display name is unique. Skip the check if configuration allows same display names.
     *
     * @param storage The {@link ContactStorage} to search for the display name in
     * @param session The current {@link Session}
     * @param folderID The folder identifier to search contacts in
     * @param update The updated {@link Contact}
     * @param original The original {@link Contact}. Can be <code>null</code>
     * @throws OXException
     *             <li> {@link ContactExceptionCodes#DISPLAY_NAME_MANDATORY} if the new display name is empty as per {@link Strings#isEmpty(String)} </li>
     *             <li>{@link ContactExceptionCodes#DISPLAY_NAME_IN_USE} if display names must be unique and is already in use</li>
     *             <li> {@link com.openexchange.server.ServiceExceptionCode#SERVICE_UNAVAILABLE} if {@link ConfigViewFactory} is not available </li>
     */
    private static void checkDisplayNameUniqueness(ContactStorage storage, Session session, String folderID, Contact update, Contact original) throws OXException {
        if (false == update.containsDisplayName()) {
            return;
        }
        if (Tools.isEmpty(update.getDisplayName())) {
            throw ContactExceptionCodes.DISPLAY_NAME_MANDATORY.create();
        }

        ConfigViewFactory configViewFactory = ContactServiceLookup.getService(ConfigViewFactory.class, true);
        ConfigView view = configViewFactory.getView(-1, session.getContextId());
        if (null != view && false == view.opt("com.openexchange.user.enforceUniqueDisplayName", Boolean.class, Boolean.TRUE).booleanValue()) {
            // Do not enforce unique display names
            return;
        }

        if (null != original && update.getDisplayName().equals(original.getDisplayName())) {
            // Was set before and is unchanged
            return;
        }

        /*
         * check if display name is already in use
         */
        CompositeSearchTerm searchTerm = new CompositeSearchTerm(CompositeOperation.AND);
        searchTerm.addSearchTerm(Tools.createContactFieldTerm(ContactField.FOLDER_ID, SingleOperation.EQUALS, folderID));
        searchTerm.addSearchTerm(Tools.createContactFieldTerm(ContactField.DISPLAY_NAME, SingleOperation.EQUALS, sanitizeDisplayName(update.getDisplayName())));
        searchTerm.addSearchTerm(Tools.createContactFieldTerm(ContactField.OBJECT_ID, SingleOperation.NOT_EQUALS, Integer.valueOf(update.getObjectID())));
        SearchIterator<Contact> searchIterator = null;
        try {
            searchIterator = storage.search(session, searchTerm, new ContactField[] { ContactField.OBJECT_ID }, new SortOptions(0, 1));
            if (searchIterator.hasNext()) {
                throw ContactExceptionCodes.DISPLAY_NAME_IN_USE.create(Integer.valueOf(session.getContextId()), Integer.valueOf(update.getObjectID()));
            }
        } finally {
            Tools.close(searchIterator);
        }
    }

    private static final Pattern MULTIPLE_WILDCARD_PATTERN = Pattern.compile("\\*");

    private static final Pattern SINGLE_WILDCARD_PATTERN = Pattern.compile("\\?");

    /**
     * Sanitize special characters to avoid generic searches
     *
     * @param update The updated display name to search for
     * @return A sanitized version of the display name
     */
    private static String sanitizeDisplayName(String displayName) {
        // Sanitize '*'
        Matcher matcher = MULTIPLE_WILDCARD_PATTERN.matcher(displayName);
        StringBuffer sb = new StringBuffer(displayName.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb, "\\\\*");
        }
        matcher.appendTail(sb);

        // Sanitize '?'
        matcher = SINGLE_WILDCARD_PATTERN.matcher(sb.toString());
        sb.setLength(0);
        while (matcher.find()) {
            matcher.appendReplacement(sb, "\\\\?");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
	 * Checks the supplied delta contact for possible changes to read-only fields. If read-only are about to be modified to a value
	 * different from the currently stored value, an appropriate exception is thrown. If they're going to be set to the property's
	 * default value, the properties are removed from the delta reference.
	 *
	 * @param userID The ID of the user performing the update
	 * @param storedContact The stored contact
	 * @param delta The delta holding the changes to be applied
	 * @throws OXException
	 */
	public static void readOnlyFields(int userID, Contact storedContact, Contact delta) throws OXException {
        if (delta.containsContextId()) {
            if (0 == delta.getContextId() || delta.getContextId() == storedContact.getContextId()) {
                delta.removeContextID();
            } else {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(
                    Integer.valueOf(storedContact.getObjectID()), Integer.valueOf(storedContact.getContextId()));
            }
        }
        if (delta.containsObjectID()) {
            if (0 == delta.getObjectID() || delta.getObjectID() == storedContact.getObjectID()) {
                delta.removeObjectID();
            } else {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(
                    Integer.valueOf(storedContact.getObjectID()), Integer.valueOf(storedContact.getContextId()));
            }
        }
        if (delta.containsInternalUserId()) {
            if (0 == delta.getInternalUserId() || delta.getInternalUserId() == storedContact.getInternalUserId()) {
                delta.removeInternalUserId();
            } else {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(
                    Integer.valueOf(storedContact.getObjectID()), Integer.valueOf(storedContact.getContextId()));
            }
        }
        if (delta.containsUid() && Strings.isNotEmpty(storedContact.getUid())) {
            if (Strings.isEmpty(delta.getUid()) || delta.getUid().equals(storedContact.getUid())) {
                delta.removeUid();
            } else {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(
                    Integer.valueOf(storedContact.getObjectID()), Integer.valueOf(storedContact.getContextId()));
            }
        }
        if (delta.containsCreatedBy()) {
            if (0 == delta.getCreatedBy() || delta.getCreatedBy() == storedContact.getCreatedBy()) {
                delta.removeCreatedBy();
            } else {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(
                    Integer.valueOf(storedContact.getObjectID()), Integer.valueOf(storedContact.getContextId()));
            }
        }
        if (delta.containsCreationDate()) {
            if (null == delta.getCreationDate() || delta.getCreationDate().equals(storedContact.getCreationDate())
                || 0 == delta.getCreationDate().getTime()) {
                delta.removeCreationDate();
            } else {
                throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(
                    Integer.valueOf(storedContact.getObjectID()), Integer.valueOf(storedContact.getContextId()));
            }
        }
        if (delta.containsPrivateFlag() && delta.getPrivateFlag() && storedContact.getCreatedBy() != userID) {
            throw ContactExceptionCodes.NO_CHANGE_PERMISSION.create(
                Integer.valueOf(storedContact.getObjectID()), Integer.valueOf(storedContact.getContextId()));
        }
	}

	private Check() {
		// prevent instantiation
	}

    private static void checkForSubscription(Session session, String fid, int cid) throws OXException {
        if ("true".equals(LogProperties.get(LogProperties.Name.SUBSCRIPTION_ADMIN))) {
            // Caller is allowed to delete/update/create objects in subscribed folders
            return;
        }
        if (new OXFolderAccess(ServerSessionAdapter.valueOf(session).getContext()).isSubscriptionFolder(fid, cid)) {
            throw ContactExceptionCodes.WRITE_IN_SUBSCRIPTION_NOT_ALLOWED.create(fid);
        }
    }

}
