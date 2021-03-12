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

package com.openexchange.ajax.find.calendar;

import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import com.openexchange.ajax.find.actions.AutocompleteRequest;
import com.openexchange.ajax.find.actions.AutocompleteResponse;
import com.openexchange.ajax.user.actions.GetRequest;
import com.openexchange.ajax.user.actions.GetResponse;
import com.openexchange.find.Module;
import com.openexchange.find.facet.FacetValue;
import com.openexchange.find.util.DisplayItems;
import com.openexchange.groupware.container.Contact;
import com.openexchange.test.ContactTestManager;

/**
 * {@link AutocompleteTest}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class AutocompleteTest extends CalendarFindTest {

    private ContactTestManager contactManager;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        contactManager = new ContactTestManager(getClient());
    }

    /**
     * Initializes a new {@link AutocompleteTest}.
     */
    public AutocompleteTest() {
        super();
    }

    @Test
    public void testAutocompleteCurrentUser() throws Exception {
        String defaultAddress = getClient().getValues().getDefaultAddress();
        GetRequest getRequest = new GetRequest(getClient().getValues().getUserId(), getClient().getValues().getTimeZone());
        GetResponse getResponse = getClient().execute(getRequest);
        Contact contact = getResponse.getContact();
        String displayName = DisplayItems.convert(contact, getClient().getValues().getLocale(), i18nServiceRegistry).getDisplayName();
        assertFoundFacetInAutocomplete(defaultAddress.substring(0, 3), displayName);
    }

    @Test
    public void testAutocompleteOtherContact() throws Exception {
        Contact contact = new Contact();
        contact.setParentFolderID(getClient().getValues().getPrivateContactFolder());
        contact.setSurName(randomUID());
        contact.setGivenName(randomUID());
        contact.setDisplayName(contact.getGivenName() + " " + contact.getSurName());
        contact.setEmail1(randomUID() + "@example.com");
        contact.setEmail2(randomUID() + "@example.com");
        contact.setEmail3(randomUID() + "@example.com");
        contact.setUid(randomUID());
        contact = contactManager.newAction(contact);
        String displayName = DisplayItems.convert(contact, getClient().getValues().getLocale(), i18nServiceRegistry).getDisplayName();
        assertFoundFacetInAutocomplete(contact.getDisplayName().substring(0, 3), displayName);
        assertFoundFacetInAutocomplete(contact.getSurName().substring(0, 4), displayName);
        assertFoundFacetInAutocomplete(contact.getGivenName().substring(0, 5), displayName);
        assertFoundFacetInAutocomplete(contact.getEmail1().substring(0, 3), displayName);
        assertFoundFacetInAutocomplete(contact.getEmail2().substring(0, 6), displayName);
        assertFoundFacetInAutocomplete(contact.getEmail3().substring(0, 5), displayName);
    }

    private FacetValue assertFoundFacetInAutocomplete(String prefix, String displayName) throws Exception {
        AutocompleteRequest autocompleteRequest = new AutocompleteRequest(prefix, Module.CALENDAR.getIdentifier());
        AutocompleteResponse autocompleteResponse = getClient().execute(autocompleteRequest);
        FacetValue foundFacetValue = findByDisplayName(autocompleteResponse.getFacets(), displayName);
        assertNotNull("no facet value found for: " + displayName, foundFacetValue);
        return foundFacetValue;
    }

}
