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

package com.openexchange.ajax.user;

import static org.junit.Assert.assertTrue;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AbstractAJAXSession;
import com.openexchange.ajax.framework.MultipleRequest;
import com.openexchange.ajax.user.actions.GetRequest;
import com.openexchange.ajax.user.actions.GetResponse;
import com.openexchange.ajax.user.actions.SearchRequest;
import com.openexchange.ajax.user.actions.SearchResponse;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.search.ContactSearchObject;

/**
 * {@link Bug13911Test}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public class Bug13911Test extends AbstractAJAXSession {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Bug13911Test.class);

    private static final int[] COLUMNS = new int[] { Contact.OBJECT_ID, Contact.FOLDER_ID, Contact.DISPLAY_NAME, Contact.EMAIL1, Contact.MARK_AS_DISTRIBUTIONLIST, Contact.INTERNAL_USERID, Contact.EMAIL2, Contact.EMAIL3, Contact.SUR_NAME, Contact.GIVEN_NAME };

    private AJAXClient client;

    private Contact contact;

    public Bug13911Test() {
        super();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        client = getClient();
        final GetResponse response = client.execute(new GetRequest(client.getValues().getUserId(), client.getValues().getTimeZone()));
        contact = response.getContact();
    }

    /**
     * The following search request does not find users in global addressbook:
     * {"module":"contacts","action":"search","columns":"1,20,500,555,602,524,556,557"
     * ,"sort":"500","order":"asc","data":{"display_name":"e",
     * "email1":"e","email2":"e","email3":"e","last_name":"e","first_name":"e","orSearch":true}}
     *
     * @throws Throwable
     */
    @Test
    public void testPatternSearchMultiple() throws Throwable {
        for (final String value : new String[] { contact.getDisplayName(), contact.getSurName(), contact.getGivenName(), contact.getEmail1() }) {
            final String pattern = surroundWithWildcards(getPart(value));
            LOG.info("Pattern: " + pattern);
            final ContactSearchObject cso = new ContactSearchObject();
            cso.setDisplayName(pattern);
            cso.setEmail1(pattern);
            cso.setEmail2(pattern);
            cso.setEmail3(pattern);
            cso.setSurname(pattern);
            cso.setGivenName(pattern);
            cso.setOrSearch(true);
            final SearchRequest[] searches = new SearchRequest[] { new SearchRequest(cso, COLUMNS, true) };
            final SearchResponse response = client.execute(new MultipleRequest<SearchResponse>(searches)).getResponse(0);
            boolean found = false;
            for (final Object[] test : response) {
                final int id = ((Integer) test[response.getColumnPos(Contact.INTERNAL_USERID)]).intValue();
                if (id == contact.getInternalUserId()) {
                    found = true;
                    break;
                }
            }
            assertTrue("Searched user contact not found.", found);
        }
    }

    @Test
    public void testPatternSearch() throws Throwable {
        for (final String value : new String[] { contact.getDisplayName(), contact.getSurName(), contact.getGivenName(), contact.getEmail1() }) {
            final String pattern = surroundWithWildcards(getPart(value));
            LOG.info("Pattern: " + pattern);
            final ContactSearchObject cso = new ContactSearchObject();
            cso.setDisplayName(pattern);
            cso.setEmail1(pattern);
            cso.setEmail2(pattern);
            cso.setEmail3(pattern);
            cso.setSurname(pattern);
            cso.setGivenName(pattern);
            cso.setOrSearch(true);
            final SearchRequest request = new SearchRequest(cso, COLUMNS, true);
            final SearchResponse response = client.execute(request);
            boolean found = false;
            for (final Object[] test : response) {
                final int id = ((Integer) test[response.getColumnPos(Contact.INTERNAL_USERID)]).intValue();
                if (id == contact.getInternalUserId()) {
                    found = true;
                    break;
                }
            }
            assertTrue("Searched user contact not found using pattern: " + pattern, found);
        }
    }

    private String getPart(final String value) {
        final Random rand = new Random(System.currentTimeMillis());
        final int start = rand.nextInt(value.length());
        final int length = rand.nextInt(value.length() - start);
        return value.substring(start, start + length);
    }

    private static String surroundWithWildcards(final String value) {
        String ret = value;
        if ("".equals(ret) || ret.charAt(0) != '*') {
            // Prepend '*' character
            ret = new StringBuilder(ret.length() + 1).append('*').append(ret).toString();
        }
        if (ret.charAt(ret.length() - 1) != '*' || (ret.length() > 1 && ret.charAt(ret.length() - 2) == '\\')) {
            // Append '*' character
            ret = new StringBuilder(ret.length() + 1).append(ret).append('*').toString();
        }
        return ret;
    }

}
