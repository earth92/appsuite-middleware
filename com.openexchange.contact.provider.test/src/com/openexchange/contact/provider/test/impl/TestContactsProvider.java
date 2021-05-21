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

package com.openexchange.contact.provider.test.impl;

import java.util.EnumSet;
import java.util.Locale;
import org.json.JSONObject;
import com.openexchange.contact.common.ContactsAccount;
import com.openexchange.contact.common.ContactsParameters;
import com.openexchange.contact.provider.AutoProvisioningContactsProvider;
import com.openexchange.contact.provider.ContactsAccessCapability;
import com.openexchange.contact.provider.ContactsProvider;
import com.openexchange.contact.provider.basic.BasicContactsAccess;
import com.openexchange.contact.provider.basic.BasicContactsProvider;
import com.openexchange.contact.provider.basic.ContactsSettings;
import com.openexchange.contact.provider.test.impl.utils.TestContacts;
import com.openexchange.contact.provider.test.storage.TestContactsStorage;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.session.Session;

/**
 * {@link TestContactsProvider} - An "in memory" implementation of {@link ContactsProvider} for testing purpose only
 *
 * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
 * @since v8.0.0
 */
public class TestContactsProvider implements AutoProvisioningContactsProvider, BasicContactsProvider {

    public static final String PROVIDER_ID = "com.openexchange.contact.provider.test";
    public static final String PROVIDER_DISPLAY_NAME = "c.o.contact.provider.test";

    private final TestContactsStorage storage;

    /**
     * Initializes a new {@link TestContactsProvider}.
     */
    public TestContactsProvider() {
        this.storage = new TestContactsStorage(TestContacts.TEST_DATA);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName(Locale locale) {
        return PROVIDER_DISPLAY_NAME;
    }

    @Override
    public EnumSet<ContactsAccessCapability> getCapabilities() {
        return ContactsAccessCapability.getCapabilities(TestContactsAccess.class);
    }

    @Override
    public void onAccountDeleted(Session session, ContactsAccount account, ContactsParameters parameters) throws OXException {
        // no-op
    }

    @Override
    public void onAccountDeleted(Context context, ContactsAccount account, ContactsParameters parameters) throws OXException {
        // no-op
    }

    @Override
    public JSONObject autoConfigureAccount(Session session, JSONObject userConfig, ContactsParameters parameters) throws OXException {
        return new JSONObject();
    }

    @Override
    public ContactsSettings probe(Session session, ContactsSettings settings, ContactsParameters parameters) throws OXException {
        return settings;
    }

    @Override
    public JSONObject configureAccount(Session session, ContactsSettings settings, ContactsParameters parameters) throws OXException {
        return new JSONObject();
    }

    @Override
    public JSONObject reconfigureAccount(Session session, ContactsAccount account, ContactsSettings settings, ContactsParameters parameters) throws OXException {
        return new JSONObject();
    }

    @Override
    public BasicContactsAccess connect(Session session, ContactsAccount account, ContactsParameters parameters) throws OXException {
        return new TestContactsAccess(storage, account);
    }
}
