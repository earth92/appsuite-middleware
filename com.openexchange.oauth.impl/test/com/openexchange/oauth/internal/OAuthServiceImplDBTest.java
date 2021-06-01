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

package com.openexchange.oauth.internal;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.junit.Assert.assertEqualAttributes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import com.openexchange.exception.OXException;
import com.openexchange.oauth.API;
import com.openexchange.oauth.DefaultOAuthAccount;
import com.openexchange.oauth.KnownApi;
import com.openexchange.oauth.OAuthAccount;
import com.openexchange.oauth.OAuthAccountStorage;
import com.openexchange.oauth.OAuthConstants;
import com.openexchange.oauth.OAuthInteractionType;
import com.openexchange.oauth.OAuthToken;
import com.openexchange.oauth.SimOAuthServiceMetaDataRegistry;
import com.openexchange.oauth.impl.AbstractOAuthServiceMetaData;
import com.openexchange.oauth.impl.internal.OAuthServiceImpl;
import com.openexchange.oauth.scope.OAuthScope;
import com.openexchange.session.Session;
import com.openexchange.tools.sql.SQLTestCase;

/**
 * The {@link OAuthServiceImplDBTest} tests the DB interaction of the OAuthServiceImpl class, with the OAuth interactions
 * taken out through subclassing. The OAuth interactions are tested elsewhere.
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
//TODO: Fix tests
@SuppressWarnings("unused")
public class OAuthServiceImplDBTest extends SQLTestCase {

    private OAuthServiceImpl oauth;
    private SimOAuthServiceMetaDataRegistry registry;
    private OAuthAccountStorage oauthAccountStorage;

    @Mock
    private Session session;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        session = Mockito.mock(Session.class);
        Mockito.when(I(session.getUserId())).thenReturn(I(23));
        Mockito.when(I(session.getContextId())).thenReturn(I(1));

        registry = new SimOAuthServiceMetaDataRegistry();
        registry.addService(new AbstractOAuthServiceMetaData() {

            @Override
            public String getDisplayName() {
                return "The cool oauthService";
            }

            @Override
            public String getId() {
                return "com.openexchange.test";
            }

            @Override
            public boolean needsRequestToken() {
                return true;
            }

            @Override
            public String processAuthorizationURL(final String authUrl, Session session) {
                return authUrl;
            }

            @Override
            public API getAPI() {
                return KnownApi.OTHER;
            }

            @Override
            public Set<OAuthScope> getAvailableScopes(int userId, int ctxId) {
                return Collections.emptySet();
            }

            @Override
            public String getUserIdentity(Session session, int accountId, String accessToken, String accessSecret) throws OXException {
                return "someIdentity";
            }
        });
        oauthAccountStorage = new OAuthAccountStorage() {

            @Override
            public void updateAccount(Session session, int accountId, Map<String, Object> arguments) throws OXException {
                // TODO Auto-generated method stub

            }

            @Override
            public void updateAccount(Session session, OAuthAccount account) throws OXException {
                // TODO Auto-generated method stub

            }

            @Override
            public int storeAccount(Session session, OAuthAccount account) throws OXException {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public List<OAuthAccount> getAccounts(Session session, String serviceMetaData) throws OXException {
                // TODO Auto-generated method stub
                return Collections.emptyList();
            }

            @Override
            public List<OAuthAccount> getAccounts(Session session) throws OXException {
                // TODO Auto-generated method stub
                return Collections.emptyList();
            }

            @Override
            public OAuthAccount getAccount(Session session, int accountId, boolean loadSecrets) throws OXException {
                // TODO Auto-generated method stub
                return new DefaultOAuthAccount();
            }

            @Override
            public OAuthAccount getAccount(int contextId, int userId, int accountId) throws OXException {
                // TODO Auto-generated method stub
                return new DefaultOAuthAccount();
            }

            @Override
            public OAuthAccount findByUserIdentity(Session session, String userIdentity, String serviceId, boolean loadSecrets) throws OXException {
                // TODO Auto-generated method stub
                return new DefaultOAuthAccount();
            }

            @Override
            public boolean hasUserIdentity(Session session, int accountId, String serviceId) throws OXException {
                // TODO Auto-generated method stub
                return true;
            }

            @Override
            public boolean deleteAccount(Session session, int accountId) throws OXException {
                // TODO Auto-generated method stub
                return true;

            }
        };
        oauth = new OAuthServiceImpl(registry, oauthAccountStorage, null) {

            @Override
            protected void obtainToken(final OAuthInteractionType type, final Map<String, Object> arguments, final DefaultOAuthAccount account, Set<OAuthScope> scopes) {
                account.setToken("myAccessToken");
                account.setSecret("myAccessSecret");
                account.setEnabledScopes(scopes);
            }
        };

        exec("DELETE FROM oauthAccounts");
    }

    // Success Cases

    @Test
    public void testCreateAccount() throws OXException, SQLException {
        final OAuthAccount authAccount = createTestAccount();

        assertNotNull(authAccount);
        assertEquals("Test OAuthAccount", authAccount.getDisplayName());
        assertTrue(authAccount.getId() != 0);
        assertNotNull(authAccount.getMetaData());
        assertEquals("com.openexchange.test", authAccount.getMetaData().getId());
        assertEquals("myAccessToken", authAccount.getToken());
        assertEquals("myAccessSecret", authAccount.getSecret());

        assertResult("SELECT 1 FROM oauthAccounts WHERE id = " + authAccount.getId() + " AND displayName = 'Test OAuthAccount' AND serviceId = 'com.openexchange.test' AND accessToken = 'myAccessToken' AND accessSecret = 'myAccessSecret' AND cid = 1 AND user = 23");

    }

    private OAuthAccount createTestAccount() throws OXException {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put(OAuthConstants.ARGUMENT_DISPLAY_NAME, "Test OAuthAccount");
        arguments.put(OAuthConstants.ARGUMENT_PIN, "pin");
        arguments.put(OAuthConstants.ARGUMENT_SESSION, null);
        arguments.put(OAuthConstants.ARGUMENT_REQUEST_TOKEN, new OAuthToken() {

            @Override
            public String getSecret() {
                return "requestSecret";
            }

            @Override
            public String getToken() {
                return "requestToken";
            }
            
            @Override
            public long getExpiration() {
                return Long.MAX_VALUE;
            }

        });

        Set<OAuthScope> scopes = new HashSet<>();
        scopes.add(TestOAuthScope.calendar);
        scopes.add(TestOAuthScope.drive);

        final OAuthAccount authAccount = oauth.createAccount(session, "com.openexchange.test", scopes, OAuthInteractionType.OUT_OF_BAND, arguments);
        return authAccount;
    }

    @Test
    public void testDefaultDisplayName() throws OXException {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put(OAuthConstants.ARGUMENT_PIN, "pin");
        arguments.put(OAuthConstants.ARGUMENT_SESSION, null);
        arguments.put(OAuthConstants.ARGUMENT_REQUEST_TOKEN, new OAuthToken() {

            @Override
            public String getSecret() {
                return "requestSecret";
            }

            @Override
            public String getToken() {
                return "requestToken";
            }
            
            @Override
            public long getExpiration() {
                return Long.MAX_VALUE;
            }
        });

        Set<OAuthScope> scopes = new HashSet<>();
        scopes.add(TestOAuthScope.calendar);
        scopes.add(TestOAuthScope.drive);

        final OAuthAccount authAccount = oauth.createAccount(session, "com.openexchange.test", scopes, OAuthInteractionType.OUT_OF_BAND, arguments);

        assertNotNull(authAccount);
        assertEquals("The cool oauthService", authAccount.getDisplayName());
    }

    @Test
    public void testGetAccount() throws Exception {
        final OAuthAccount authAccount = createTestAccount();

        final OAuthAccount account = oauth.getAccount(null, authAccount.getId());

        assertNotNull(account);
        assertEqualAttributes(authAccount, account);
    }

    @Test
    public void testGetAccountsForUser() throws Exception {
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,23,1,'account1user1', '1234', '4321', 'com.openexchange.test');");
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,23,2,'account2user1', '1234', '4321', 'com.openexchange.test');");
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,42,3,'account1user2', '1234', '4321', 'com.openexchange.test');");

        final List<OAuthAccount> accounts = oauth.getAccounts(session);

        assertEquals(2, accounts.size());

        final DefaultOAuthAccount expected = new DefaultOAuthAccount();
        expected.setToken("1234");
        expected.setSecret("4321");
        expected.setMetaData(registry.getService("com.openexchange.test", -1, -1));

        for (final OAuthAccount account : accounts) {
            expected.setDisplayName("account" + account.getId() + "user1");
            assertTrue("Unexpected id: " + account.getId(), account.getId() == 1 || account.getId() == 2);
            assertEqualAttributes(expected, account, "displayName", "token", "secret", "metaData");
        }

    }

    @Test
    public void testGetAccountsForUserAndService() throws Exception {
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,23,1,'account1user1', '1234', '4321', 'com.openexchange.test');");
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,23,2,'account2user1', '1234', '4321', 'com.openexchange.test');");
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,23,3,'account3user1', '1234', '4321', 'com.openexchange.notTest');");
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,42,4,'account1user2', '1234', '4321', 'com.openexchange.test');");

        final List<OAuthAccount> accounts = oauth.getAccounts(session, "com.openexchange.test");

        assertEquals(2, accounts.size());

        final DefaultOAuthAccount expected = new DefaultOAuthAccount();
        expected.setToken("1234");
        expected.setSecret("4321");
        expected.setMetaData(registry.getService("com.openexchange.test", -1, -1));

        for (final OAuthAccount account : accounts) {
            expected.setDisplayName("account" + account.getId() + "user1");
            assertTrue("Unexpected id: " + account.getId(), account.getId() == 1 || account.getId() == 2);
            assertEqualAttributes(expected, account, "displayName", "token", "secret", "metaData");
        }
    }

    @Test
    public void testUpdateAccount() throws Exception {
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,23,1,'account1', '1234', '4321', 'com.openexchange.test');");

        Set<OAuthScope> scopes = new HashSet<>();
        scopes.add(TestOAuthScope.calendar);
        scopes.add(TestOAuthScope.drive);

        final Map<String, Object> update = new HashMap<>();
        update.put(OAuthConstants.ARGUMENT_DISPLAY_NAME, "updatedDisplayName");
        update.put(OAuthConstants.ARGUMENT_SCOPES, scopes);
        update.put(OAuthConstants.ARGUMENT_SESSION, null);
        oauth.updateAccount(session, 1, update);

        assertResult("SELECT 1 FROM oauthAccounts WHERE cid = 1 AND user = 23 AND displayName = 'updatedDisplayName' AND id = 1");
    }

    @Test
    public void testDeleteAccount() throws Exception {
        exec("INSERT INTO oauthAccounts (cid, user, id, displayName, accessToken, accessSecret, serviceId) VALUES (1,23,1,'account1', '1234', '4321', 'com.openexchange.test');");

        oauth.deleteAccount(session, 1);

        assertNoResult("SELECT 1 FROM oauthAccounts WHERE cid = 1 AND user = 23 AND id = 1");
    }

    // Error Cases

    @Test
    public void testUnknownAccountMetadataOnCreate() {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put(OAuthConstants.ARGUMENT_DISPLAY_NAME, "Test OAuthAccount");
        arguments.put(OAuthConstants.ARGUMENT_PIN, "pin");
        arguments.put(OAuthConstants.ARGUMENT_SESSION, null);
        arguments.put(OAuthConstants.ARGUMENT_REQUEST_TOKEN, new OAuthToken() {

            @Override
            public String getSecret() {
                return "requestSecret";
            }

            @Override
            public String getToken() {
                return "requestToken";
            }
            
            @Override
            public long getExpiration() {
                return Long.MAX_VALUE;
            }
        });

        Set<OAuthScope> scopes = new HashSet<>();
        scopes.add(TestOAuthScope.calendar);
        scopes.add(TestOAuthScope.drive);

        try {
            oauth.createAccount(session, "com.openexchange.fantasy", scopes, OAuthInteractionType.OUT_OF_BAND, arguments);
            fail("Should have died");
        } catch (OXException e) {
            // Hooray;
        }
    }

    @Test
    public void testUnknownIdOnGet() {
        try {
            oauth.getAccount(null, 12);
            fail("Should have died");
        } catch (OXException x) {
            // Hooray!
        }
    }

    @Test
    public void testUnknownIdOnUpdate() {
        try {
            Set<OAuthScope> scopes = new HashSet<>();
            scopes.add(TestOAuthScope.calendar);
            scopes.add(TestOAuthScope.drive);

            final Map<String, Object> update = new HashMap<>();
            update.put(OAuthConstants.ARGUMENT_DISPLAY_NAME, "updatedDisplayName");
            update.put(OAuthConstants.ARGUMENT_SCOPES, scopes);
            update.put(OAuthConstants.ARGUMENT_SESSION, null);
            oauth.updateAccount(session, 12, update);
            fail("Should have died");
        } catch (OXException x) {
            // Hooray!
        }
    }

    @Test
    public void testUnknownIdOnDelete() {
        try {
            oauth.deleteAccount(session, 12);
            // Don't die here, just gracefully do nothing
        } catch (OXException x) {
            fail(x.getMessage());
        }
    }
}
