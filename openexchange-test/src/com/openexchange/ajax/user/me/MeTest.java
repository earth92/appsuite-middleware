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
 *    trademarks of the OX Software GmbH. group of companies.
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

package com.openexchange.ajax.user.me;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import com.openexchange.ajax.framework.AbstractAPIClientSession;
import com.openexchange.testing.httpclient.models.CurrentUserData;
import com.openexchange.testing.httpclient.models.CurrentUserResponse;
import com.openexchange.testing.httpclient.modules.UserMeApi;


/**
 * {@link MeTest}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.10.4
 */
public class MeTest extends AbstractAPIClientSession {

    /**
     * Tests that the following response data is orderly returned:
     *
     * <pre>
     * {
     *   "data": {
     *     "context_id": 1,
     *     "user_id": 3,
     *     "context_admin": 2,
     *     "login_name": "peter",
     *     "display_name": "Peter",
     *     "mail_login": "peter@example.com"
     *     "email_address": "peter@example.com",
     *     "email_aliases": [
     *       "peter@example.com"
     *     ],
     *   },
     *   "timestamp": 1583243886037
     * }
     * </pre>
     */
    @Test
    public void testGet() throws Exception {
        UserMeApi api = new UserMeApi(getApiClient());
        CurrentUserResponse response = api.getCurrentUser();
        CurrentUserData me = response.getData();
        assertEquals("Missing or wrong user_id", testUser.getUserId(), me.getUserId().intValue());
        assertEquals("Missing or wrong context_id", testContext.getId(), me.getContextId().intValue());
        assertNotNull("Missing context_admin", me.getContextAdmin());
        assertNotNull("Missing login_name", me.getLoginName());
        assertNotNull("Missing display_name", me.getDisplayName());
        assertNotNull("Missing mail_login", me.getMailLogin());
        assertEquals("Missing or wrong email_address", testUser.getLogin(), me.getEmailAddress());
        assertNotNull("Missing email_aliases", me.getEmailAliases());
        assertTrue("Missing primary address in email_aliases", me.getEmailAliases().stream().anyMatch(a -> {
            try {
                return a.equals(testUser.getLogin());
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }));
    }

}
