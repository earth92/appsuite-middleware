/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.contact.picture.impl.finder;

import java.util.LinkedHashSet;
import com.openexchange.contact.common.ContactsParameters;
import com.openexchange.contact.picture.PictureSearchData;
import com.openexchange.contact.provider.composition.IDBasedContactsAccess;
import com.openexchange.contact.provider.composition.IDBasedContactsAccessFactory;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.Contact;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;
import com.openexchange.tools.arrays.Arrays;

/**
 * {@link ContactIDFinder} - Finds picture based on contact identifier
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.1
 */
public class ContactIDFinder extends AbstractContactFinder {

    /**
     * Initializes a new {@link ContactUserFinder}.
     *
     * @param idBasedContactsAccessFactory The {@link IDBasedContactsAccessFactory}
     */
    public ContactIDFinder(IDBasedContactsAccessFactory idBasedContactsAccessFactory) {
        super(idBasedContactsAccessFactory);
    }

    private static final ContactField[] FIELDS = new ContactField[] { ContactField.EMAIL1, ContactField.EMAIL2, ContactField.EMAIL3 };

    @Override
    public Contact getContact(Session session, PictureSearchData data, ContactField... fields) throws OXException {
        if (data.hasContact() && data.hasFolder()) {
            IDBasedContactsAccess contactsAccess = idBasedContactsAccessFactory.createAccess(session);
            try {
                contactsAccess.set(ContactsParameters.PARAMETER_FIELDS, Arrays.add(fields, FIELDS));
                return contactsAccess.getContact(createContactID(data));
            } finally {
                contactsAccess.finish();
            }
        }
        return null;
    }

    @Override
    public PictureSearchData modfiyResult(Contact contact) {
        LinkedHashSet<String> set = new LinkedHashSet<>(4, .9f);
        if (contact.containsEmail1() && Strings.isNotEmpty(contact.getEmail1())) {
            set.add(contact.getEmail1());
        }
        if (contact.containsEmail2() && Strings.isNotEmpty(contact.getEmail2())) {
            set.add(contact.getEmail2());
        }
        if (contact.containsEmail3() && Strings.isNotEmpty(contact.getEmail3())) {
            set.add(contact.getEmail3());
        }
        return new PictureSearchData(null, null, null, null, set);
    }

    @Override
    public void handleException(PictureSearchData data, OXException e) {
        LOGGER.debug("Unable to get contact for ID {} in folder {},", data.getContactId(), data.getFolderId(), e);
    }

}
