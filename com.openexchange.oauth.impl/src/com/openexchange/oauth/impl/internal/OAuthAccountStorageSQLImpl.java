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

package com.openexchange.oauth.impl.internal;

import static com.openexchange.database.Databases.autocommit;
import static com.openexchange.database.Databases.closeSQLStuff;
import static com.openexchange.database.Databases.rollback;
import static com.openexchange.database.Databases.startTransaction;
import static com.openexchange.java.Autoboxing.I;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.context.ContextService;
import com.openexchange.crypto.CryptoErrorMessage;
import com.openexchange.crypto.CryptoService;
import com.openexchange.database.Databases;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.id.IDGeneratorService;
import com.openexchange.java.Strings;
import com.openexchange.oauth.DefaultOAuthAccount;
import com.openexchange.oauth.DefaultOAuthToken;
import com.openexchange.oauth.OAuthAccount;
import com.openexchange.oauth.OAuthAccountStorage;
import com.openexchange.oauth.OAuthConstants;
import com.openexchange.oauth.OAuthExceptionCodes;
import com.openexchange.oauth.OAuthServiceMetaDataRegistry;
import com.openexchange.oauth.OAuthToken;
import com.openexchange.oauth.access.OAuthAccess;
import com.openexchange.oauth.access.OAuthAccessRegistry;
import com.openexchange.oauth.access.OAuthAccessRegistryService;
import com.openexchange.oauth.impl.services.Services;
import com.openexchange.oauth.scope.OAuthScope;
import com.openexchange.oauth.scope.OAuthScopeRegistry;
import com.openexchange.oauth.scope.OXScope;
import com.openexchange.secret.SecretEncryptionFactoryService;
import com.openexchange.secret.SecretEncryptionService;
import com.openexchange.secret.SecretEncryptionStrategy;
import com.openexchange.secret.recovery.EncryptedItemCleanUpService;
import com.openexchange.secret.recovery.EncryptedItemDetectorService;
import com.openexchange.secret.recovery.SecretMigrator;
import com.openexchange.session.Session;
import com.openexchange.sql.builder.StatementBuilder;
import com.openexchange.sql.grammar.Command;
import com.openexchange.sql.grammar.INSERT;
import com.openexchange.sql.grammar.UPDATE;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link OAuthAccountStorageSQLImpl}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class OAuthAccountStorageSQLImpl implements OAuthAccountStorage, SecretEncryptionStrategy<PWUpdate>, EncryptedItemDetectorService, SecretMigrator, EncryptedItemCleanUpService {

    /** The logger constant */
    static final Logger LOG = LoggerFactory.getLogger(OAuthAccountStorageSQLImpl.class);

    private final DBProvider provider;
    private final IDGeneratorService idGenerator;
    private final OAuthServiceMetaDataRegistry registry;
    private final ContextService contextService;

    /**
     * Initialises a new {@link OAuthAccountStorageSQLImpl}.
     */
    public OAuthAccountStorageSQLImpl(DBProvider provider, IDGeneratorService idGenerator, OAuthServiceMetaDataRegistry registry, ContextService contextService) {
        super();
        this.provider = provider;
        this.idGenerator = idGenerator;
        this.registry = registry;
        this.contextService = contextService;
    }

    @Override
    public int storeAccount(Session session, OAuthAccount account) throws OXException {
        int contextId = session.getContextId();
        int user = session.getUserId();

        // Crypt tokens
        DefaultOAuthToken.class.cast(account).setToken(encrypt(account.getToken(), session));
        DefaultOAuthToken.class.cast(account).setSecret(encrypt(account.getSecret(), session));
        DefaultOAuthAccount.class.cast(account).setId(idGenerator.getId(OAuthConstants.TYPE_ACCOUNT, contextId));

        // Create INSERT command
        ArrayList<Object> values = new ArrayList<>(SQLStructure.OAUTH_COLUMN.values().length);
        INSERT insert = SQLStructure.insertAccount(account, contextId, user, values);
        // Execute INSERT command
        executeUpdate(contextId, insert, values);
        LOG.info("Created new {} account with ID {} for user {} in context {}", account.getMetaData().getDisplayName(), I(account.getId()), I(user), I(contextId));
        return account.getId();
    }

    @Override
    public OAuthAccount getAccount(Session session, int accountId, boolean loadSecrets) throws OXException {
        Connection connection = Connection.class.cast(session.getParameter("__connection"));
        try {
            if (connection != null && Databases.isInTransaction(connection)) {
                // Given connection is already in transaction. Invoke & return immediately.
                return getAccount(session, accountId, loadSecrets, connection);
            }
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        }

        final Context context = getContext(session.getContextId());
        connection = getConnection(true, context);
        try {
            return getAccount(session, accountId, loadSecrets, connection);
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            provider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public OAuthAccount getAccount(int contextId, int userId, int accountId) throws OXException {
        Context context = getContext(contextId);
        Connection connection = getConnection(true, context);
        try {
            return getAccount(contextId, userId, accountId, connection);
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            provider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public boolean deleteAccount(Session session, int accountId) throws OXException {
        int userId = session.getUserId();
        int contextId = session.getContextId();

        Connection connection = Connection.class.cast(session.getParameter("__connection"));
        if (connection != null) {
            try {
                if (Databases.isInTransaction(connection)) {
                    // Given connection is already in transaction. Invoke & return immediately.
                    return deleteAccount(session, accountId, connection);
                }
            } catch (SQLException e) {
                throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
            }
        }

        Context context = getContext(contextId);
        connection = getConnection(false, context);
        int rollback = 0;
        try {
            startTransaction(connection);
            rollback = 1;

            final DeleteListenerRegistry deleteListenerRegistry = DeleteListenerRegistry.getInstance();
            final Map<String, Object> properties = new HashMap<>(2);
            // Hint to not update the scopes since it's an OAuth account deletion
            // This hint has to be passed via the delete listener
            properties.put(OAuthConstants.SESSION_PARAM_UPDATE_SCOPES, Boolean.FALSE);

            deleteListenerRegistry.triggerOnBeforeDeletion(accountId, properties, userId, contextId, connection);
            boolean deleted = deleteAccount(session, accountId, connection);
            deleteListenerRegistry.triggerOnAfterDeletion(accountId, properties, userId, contextId, connection);

            connection.commit(); // COMMIT
            rollback = 2;
            LOG.info("Deleted OAuth account with id '{}' for user '{}' in context '{}'", I(accountId), I(userId), I(contextId));
            return deleted;
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } catch (OXException e) {
            throw e;
        } catch (RuntimeException e) {
            throw OAuthExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(connection);
                }
                Databases.autocommit(connection);
            }
            provider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public void updateAccount(Session session, OAuthAccount account) throws OXException {
        // Crypt tokens
        DefaultOAuthToken.class.cast(account).setToken(encrypt(account.getToken(), session));
        DefaultOAuthToken.class.cast(account).setSecret(encrypt(account.getSecret(), session));
        /*
         * Get connection
         */
        int contextId = session.getContextId();
        int userId = session.getUserId();
        Context ctx = getContext(contextId);
        int rollback = 0;
        Connection writeCon = getConnection(false, ctx);
        try {
            /*
             * Create UPDATE command
             */
            final ArrayList<Object> values = new ArrayList<>(SQLStructure.OAUTH_COLUMN.values().length);
            final UPDATE update = SQLStructure.updateAccount(account, contextId, userId, values);
            Databases.startTransaction(writeCon);
            rollback = 1;
            String identity = getUserIdentity(session, account.getMetaData().getId(), account.getId(), writeCon);
            if (Strings.isNotEmpty(identity) && Strings.isNotEmpty(account.getUserIdentity()) && !account.getUserIdentity().equals(identity)) {
                // The user selected the wrong account
                throw OAuthExceptionCodes.WRONG_OAUTH_ACCOUNT.create(account.getDisplayName());
            }
            /*
             * Execute UPDATE command
             */
            executeUpdate(update, values, writeCon);
            /*
             * Signal re-authorized event
             */
            Map<String, Object> properties = Collections.<String, Object> emptyMap();
            ReauthorizeListenerRegistry.getInstance().onAfterOAuthAccountReauthorized(account.getId(), properties, userId, contextId, writeCon);
            /*
             * Commit
             */
            writeCon.commit();
            /*
             * Re-authorise
             */
            OAuthAccessRegistryService registryService = Services.getService(OAuthAccessRegistryService.class);
            OAuthAccessRegistry oAuthAccessRegistry = registryService.get(account.getMetaData().getId());
            // No need to re-authorise if access not present
            OAuthAccess access = oAuthAccessRegistry.get(contextId, userId, account.getId());
            if (access != null) {
                // Initialise the access with the new access token
                access.initialize();
            }
            rollback = 2;
        } catch (SQLException e) {
            LOG.error(e.toString());
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(writeCon);
                }
                Databases.autocommit(writeCon);
            }
            if (writeCon != null) {
                provider.releaseWriteConnection(ctx, writeCon);
            }
        }
    }

    @Override
    public void updateAccount(Session session, int accountId, Map<String, Object> arguments) throws OXException {
        final List<Setter> list = setterFrom(arguments, accountId);
        if (list.isEmpty()) {
            return;
        }
        Connection connection = Connection.class.cast(session.getParameter("__connection"));
        try {
            if (connection != null && Databases.isInTransaction(connection)) {
                // Given connection is already in transaction. Invoke & return immediately.
                updateAccount(session, accountId, list, connection);
                return;
            }
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        }
        final Context context = getContext(session.getContextId());
        connection = getConnection(false, context);
        try {
            updateAccount(session, accountId, list, connection);
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            provider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public OAuthAccount findByUserIdentity(Session session, String userIdentity, String serviceId, boolean loadSecrets) throws OXException {
        final SecretEncryptionService<PWUpdate> encryptionService = Services.getService(SecretEncryptionFactoryService.class).createService(this);
        final OAuthScopeRegistry scopeRegistry = Services.getService(OAuthScopeRegistry.class);
        int contextId = session.getContextId();
        int userId = session.getUserId();
        final Context context = getContext(contextId);

        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection connection = getConnection(true, context);
        try {
            stmt = connection.prepareStatement("SELECT id, displayName, accessToken, accessSecret, serviceId, scope, identity, expiryDate FROM oauthAccounts WHERE cid = ? AND user = ? AND serviceId = ? AND identity = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setString(3, serviceId);
            stmt.setString(4, userIdentity);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            int accountId = rs.getInt(1);
            DefaultOAuthAccount account = new DefaultOAuthAccount();
            account.setId(accountId);
            String displayName = rs.getString(2);
            account.setDisplayName(displayName);
            if (loadSecrets) {
                try {
                    account.setToken(decryptToken(rs.getString(3), accountId, session, encryptionService));
                    account.setSecret(decryptSecret(rs.getString(4), accountId, session, encryptionService));
                } catch (OXException e) {
                    if (CryptoErrorMessage.BadPassword.equals(e)) {
                        throw e;
                    }

                    throw OAuthExceptionCodes.INVALID_ACCOUNT_EXTENDED.create(e.getCause(), displayName, I(accountId));
                }
            }

            account.setMetaData(registry.getService(rs.getString(5), userId, contextId));
            String scopes = rs.getString(6);
            Set<OAuthScope> enabledScopes = scopeRegistry.getAvailableScopes(account.getMetaData().getAPI(), OXScope.valuesOf(scopes));
            account.setEnabledScopes(enabledScopes);
            account.setUserIdentity(rs.getString(7));
            account.setExpiration(rs.getLong(8));
            return account;
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(rs, stmt);
            provider.releaseReadConnection(context, connection);
        }
    }

    @Override
    public boolean hasUserIdentity(Session session, int accountId, String serviceId) throws OXException {
        return Strings.isNotEmpty(getUserIdentity(session, serviceId, accountId, null));
    }

    @Override
    public List<OAuthAccount> getAccounts(Session session) throws OXException {
        final SecretEncryptionService<PWUpdate> encryptionService = Services.getService(SecretEncryptionFactoryService.class).createService(this);
        final OAuthScopeRegistry scopeRegistry = Services.getService(OAuthScopeRegistry.class);
        int userId = session.getUserId();
        int contextId = session.getContextId();
        final Context context = getContext(contextId);

        PreparedStatement stmt = null;
        ResultSet rs = null;
        final Connection con = getConnection(true, context);
        try {
            stmt = con.prepareStatement("SELECT id, displayName, accessToken, accessSecret, serviceId, scope, identity, expiryDate FROM oauthAccounts WHERE cid = ? AND user = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return Collections.emptyList();
            }
            final List<OAuthAccount> accounts = new ArrayList<>(8);
            do {
                try {
                    final DefaultOAuthAccount account = new DefaultOAuthAccount();
                    account.setMetaData(registry.getService(rs.getString(5), userId, contextId));
                    account.setId(rs.getInt(1));
                    account.setDisplayName(rs.getString(2));
                    try {
                        account.setToken(decryptToken(rs.getString(3), account.getId(), session, encryptionService));
                        account.setSecret(decryptSecret(rs.getString(4), account.getId(), session, encryptionService));
                    } catch (OXException e) {
                        // Log for debug purposes and ignore...
                        LOG.debug("", e);
                    }
                    String scopes = rs.getString(6);
                    if (Strings.isNotEmpty(scopes)) {
                        Set<OAuthScope> enabledScopes = scopeRegistry.getAvailableScopes(account.getMetaData().getAPI(), OXScope.valuesOf(scopes));
                        account.setEnabledScopes(enabledScopes);
                    }
                    account.setUserIdentity(rs.getString(7));
                    account.setExpiration(rs.getLong(8));
                    accounts.add(account);
                } catch (OXException e) {
                    if (!OAuthExceptionCodes.UNKNOWN_OAUTH_SERVICE_META_DATA.equals(e)) {
                        throw e;
                    }
                    // Obviously associated service is not available. Log for debug purposes and ignore...
                    LOG.debug("{}", e.getMessage(), e);
                }
            } while (rs.next());
            return accounts;
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
            provider.releaseReadConnection(context, con);
        }
    }

    @Override
    public List<OAuthAccount> getAccounts(Session session, String serviceMetaData) throws OXException {
        final SecretEncryptionService<PWUpdate> encryptionService = Services.getService(SecretEncryptionFactoryService.class).createService(this);
        final OAuthScopeRegistry scopeRegistry = Services.getService(OAuthScopeRegistry.class);
        int userId = session.getUserId();
        int contextId = session.getContextId();
        final Context context = getContext(contextId);

        PreparedStatement stmt = null;
        ResultSet rs = null;
        final Connection con = getConnection(true, context);
        try {
            stmt = con.prepareStatement("SELECT id, displayName, accessToken, accessSecret, scope, identity, expiryDate FROM oauthAccounts WHERE cid = ? AND user = ? AND serviceId = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setString(3, serviceMetaData);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return Collections.emptyList();
            }
            final List<OAuthAccount> accounts = new ArrayList<>(8);
            do {

                try {
                    final DefaultOAuthAccount account = new DefaultOAuthAccount();
                    account.setId(rs.getInt(1));
                    account.setDisplayName(rs.getString(2));
                    try {
                        account.setToken(decryptToken(rs.getString(3), account.getId(), session, encryptionService));
                        account.setSecret(decryptSecret(rs.getString(4), account.getId(), session, encryptionService));
                    } catch (OXException x) {
                        // Log for debug purposes and ignore...
                        LOG.debug("{}", x.getMessage(), x);
                    }
                    account.setMetaData(registry.getService(serviceMetaData, userId, contextId));
                    String scopes = rs.getString(5);
                    Set<OAuthScope> enabledScopes = scopeRegistry.getAvailableScopes(account.getMetaData().getAPI(), OXScope.valuesOf(scopes));
                    account.setEnabledScopes(enabledScopes);
                    account.setUserIdentity(rs.getString(6));
                    account.setExpiration(rs.getLong(7));
                    accounts.add(account);
                } catch (OXException e) {
                    if (!OAuthExceptionCodes.UNKNOWN_OAUTH_SERVICE_META_DATA.equals(e)) {
                        throw e;
                    }
                    // Obviously associated service is not available. Log for debug purposes and ignore...
                    LOG.debug("{}", e.getMessage(), e);
                }
            } while (rs.next());
            return accounts;
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(rs, stmt);
            provider.releaseReadConnection(context, con);
        }
    }

    @Override
    public void update(String recrypted, PWUpdate customizationNote) throws OXException {
        final StringBuilder b = new StringBuilder();
        b.append("UPDATE oauthAccounts SET ").append(customizationNote.field).append("= ? WHERE cid = ? AND id = ?");

        final Context context = getContext(customizationNote.cid);
        PreparedStatement stmt = null;
        Connection con = null;
        try {
            con = getConnection(false, context);
            stmt = con.prepareStatement(b.toString());
            stmt.setString(1, recrypted);
            stmt.setInt(2, customizationNote.cid);
            stmt.setInt(3, customizationNote.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
            provider.releaseWriteConnection(context, con);
        }
    }

    @Override
    public boolean hasEncryptedItems(ServerSession session) throws OXException {
        final int contextId = session.getContextId();
        final Context context = getContext(contextId);
        PreparedStatement stmt = null;
        final Connection con = getConnection(true, context);
        try {
            stmt = con.prepareStatement("SELECT 1 FROM oauthAccounts WHERE cid = ? AND user = ? LIMIT 1");
            stmt.setInt(1, contextId);
            stmt.setInt(2, session.getUserId());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(stmt);
            provider.releaseReadConnection(context, con);
        }
    }

    @Override
    public void migrate(String oldSecret, String newSecret, ServerSession session) throws OXException {
        final CryptoService cryptoService = Services.getService(CryptoService.class);
        final int contextId = session.getContextId();
        final Context context = getContext(contextId);
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final Connection con = getConnection(false, context);
        try {
            stmt = con.prepareStatement("SELECT id, accessToken, accessSecret FROM oauthAccounts WHERE cid = ? AND user = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, session.getUserId());
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return;
            }
            final List<OAuthAccount> accounts = new ArrayList<>(8);
            do {
                try {
                    // Try using the new secret. Maybe this account doesn't need the migration
                    final String accessToken = rs.getString(2);
                    if (Strings.isNotEmpty(accessToken)) {
                        cryptoService.decrypt(accessToken, newSecret);
                    }
                    final String accessSecret = rs.getString(3);
                    if (Strings.isNotEmpty(accessSecret)) {
                        cryptoService.decrypt(accessSecret, newSecret);
                    }
                } catch (OXException e) {
                    int accountId = rs.getInt(1);
                    LOG.debug("Cannot decrypt access token and/or secret tokens with old password. The account with id {} of user {} in context {} needs migration.", I(accountId), I(session.getUserId()), I(contextId), e);
                    // Needs migration
                    final DefaultOAuthAccount account = new DefaultOAuthAccount();
                    account.setId(accountId);
                    account.setToken(cryptoService.decrypt(rs.getString(2), oldSecret));
                    account.setSecret(cryptoService.decrypt(rs.getString(3), oldSecret));
                    accounts.add(account);
                }
            } while (rs.next());
            if (accounts.isEmpty()) {
                return;
            }
            /*
             * Update
             */
            closeSQLStuff(rs, stmt);
            rs = null; stmt = null;
            stmt = con.prepareStatement("UPDATE oauthAccounts SET accessToken = ?, accessSecret = ? WHERE cid = ? AND user = ? AND id = ?");
            stmt.setInt(3, contextId);
            stmt.setInt(4, session.getUserId());
            for (final OAuthAccount oAuthAccount : accounts) {
                stmt.setString(1, cryptoService.encrypt(oAuthAccount.getToken(), newSecret));
                stmt.setString(2, cryptoService.encrypt(oAuthAccount.getSecret(), newSecret));
                stmt.setInt(5, oAuthAccount.getId());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(rs, stmt);
            provider.releaseWriteConnection(context, con);
        }
    }

    @Override
    public void cleanUpEncryptedItems(String secret, ServerSession session) throws OXException {
        final CryptoService cryptoService = Services.getService(CryptoService.class);
        final int contextId = session.getContextId();
        final Context context = getContext(contextId);
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Boolean committed = null;
        final Connection con = getConnection(false, context);
        try {
            stmt = con.prepareStatement("SELECT id, accessToken, accessSecret FROM oauthAccounts WHERE cid = ? AND user = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, session.getUserId());
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return;
            }
            final List<Integer> accounts = new ArrayList<>(8);
            do {
                try {
                    // Try using the secret.
                    final String accessToken = rs.getString(2);
                    if (Strings.isNotEmpty(accessToken)) {
                        cryptoService.decrypt(accessToken, secret);
                    }
                    final String accessSecret = rs.getString(3);
                    if (Strings.isNotEmpty(accessSecret)) {
                        cryptoService.decrypt(accessSecret, secret);
                    }
                } catch (OXException e) {
                    // Clean-up
                    int accountId = rs.getInt(1);
                    LOG.debug("Cannot encrypt access and/or secret tokens of account {} for user {} in context {}. Performing clean-up", I(accountId), I(session.getUserId()), I(contextId), e);
                    accounts.add(I(accountId));
                }
            } while (rs.next());
            closeSQLStuff(rs, stmt);
            rs = null;
            stmt = null;
            if (accounts.isEmpty()) {
                return;
            }
            /*
             * Delete them
             */
            committed = Boolean.FALSE;
            startTransaction(con);
            // Statement
            stmt = con.prepareStatement("UPDATE oauthAccounts SET accessToken = ?, accessSecret = ? WHERE cid = ? AND user = ? AND id = ?");
            stmt.setString(1, "");
            stmt.setString(2, "");
            stmt.setInt(3, contextId);
            stmt.setInt(4, session.getUserId());
            for (final Integer accountId : accounts) {
                stmt.setInt(5, accountId.intValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
            con.commit();
            committed = Boolean.TRUE;
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(rs, stmt);
            if (null != committed) {
                if (!committed.booleanValue()) {
                    rollback(con);
                }
                autocommit(con);
            }
            provider.releaseWriteConnection(context, con);
        }
    }

    @Override
    public void removeUnrecoverableItems(String secret, ServerSession session) throws OXException {
        final CryptoService cryptoService = Services.getService(CryptoService.class);
        final int contextId = session.getContextId();
        final Context context = getContext(contextId);
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Boolean committed = null;
        final Connection con = getConnection(false, context);
        try {
            stmt = con.prepareStatement("SELECT id, accessToken, accessSecret FROM oauthAccounts WHERE cid = ? AND user = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, session.getUserId());
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return;
            }
            final List<Integer> accounts = new ArrayList<>(8);
            do {
                try {
                    // Try using the secret.
                    final String accessToken = rs.getString(2);
                    if (Strings.isNotEmpty(accessToken)) {
                        cryptoService.decrypt(accessToken, secret);
                    }
                    final String accessSecret = rs.getString(3);
                    if (Strings.isNotEmpty(accessSecret)) {
                        cryptoService.decrypt(accessSecret, secret);
                    }
                } catch (OXException e) {
                    // Clean-up
                    int accountId = rs.getInt(1);
                    LOG.debug("Cannot decrypt access and/or secret tokens of account {} for user {} in context {}. Removing unrecoverable item.", I(accountId), I(session.getUserId()), I(contextId), e);
                    accounts.add(Integer.valueOf(rs.getInt(1)));
                }
            } while (rs.next());
            closeSQLStuff(rs, stmt);
            rs = null;
            stmt = null;
            if (accounts.isEmpty()) {
                return;
            }
            /*
             * Delete them
             */
            committed = Boolean.FALSE;
            startTransaction(con);
            // Statement
            stmt = con.prepareStatement("DELETE FROM oauthAccounts  WHERE cid = ? AND user = ? AND id = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, session.getUserId());
            for (final Integer accountId : accounts) {
                stmt.setInt(3, accountId.intValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
            con.commit();
            committed = Boolean.TRUE;
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(rs, stmt);
            if (null != committed) {
                if (!committed.booleanValue()) {
                    rollback(con);
                }
                autocommit(con);
            }
            provider.releaseWriteConnection(context, con);
        }
    }

    ////////////////////////////////////// HELPERS //////////////////////////////////////////

    protected boolean deleteAccount(Session session, int accountId, Connection connection) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement("DELETE FROM oauthAccounts WHERE cid = ? AND user = ? and id = ?");
            stmt.setInt(1, session.getContextId());
            stmt.setInt(2, session.getUserId());
            stmt.setInt(3, accountId);
            return stmt.executeUpdate() > 0;
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     *
     * @param session
     * @param accountId
     * @param list
     * @param connection
     * @throws SQLException
     * @throws OXException
     */
    private void updateAccount(Session session, int accountId, List<Setter> list, Connection connection) throws SQLException, OXException {
        // Compile SQL UPDATE statement
        StringBuilder stmtBuilder = new StringBuilder(128);
        {
            stmtBuilder.append("UPDATE oauthAccounts SET ");
            int size = list.size();
            list.get(0).appendTo(stmtBuilder);
            for (int i = 1; i < size; i++) {
                stmtBuilder.append(", ");
                list.get(i).appendTo(stmtBuilder);
            }
            stmtBuilder.append(" WHERE cid = ? AND user = ? and id = ?");
        }

        PreparedStatement stmt = null;
        try {
            boolean signalReauthorization = false;
            {
                String sql = stmtBuilder.toString();
                stmtBuilder = null;
                stmt = connection.prepareStatement(sql);
                signalReauthorization = sql.indexOf("accessToken") >= 0 || sql.indexOf("accessSecret") >= 0;
            }

            int pos = 1;
            for (final Setter setter : list) {
                pos = setter.set(pos, stmt);
            }
            int contextId = session.getContextId();
            int userId = session.getUserId();
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos, accountId);
            final int rows = stmt.executeUpdate();
            if (rows <= 0) {
                throw OAuthExceptionCodes.ACCOUNT_NOT_FOUND.create(I(accountId), I(userId), I(contextId));
            }
            /*
             * Signal re-authorized event
             */
            if (signalReauthorization) {
                Map<String, Object> properties = Collections.<String, Object> emptyMap();
                ReauthorizeListenerRegistry.getInstance().onAfterOAuthAccountReauthorized(accountId, properties, userId, contextId, connection);
            }
        } finally {
            closeSQLStuff(stmt);
        }
    }

    /**
     *
     * @param session
     * @param accountId
     * @param connection
     * @return
     * @throws SQLException
     * @throws OXException
     */
    private OAuthAccount getAccount(Session session, int accountId, boolean loadSecrets, Connection connection) throws SQLException, OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.prepareStatement("SELECT displayName, accessToken, accessSecret, serviceId, scope, identity, expiryDate FROM oauthAccounts WHERE cid = ? AND user = ? and id = ?");
            int contextId = session.getContextId();
            int userId = session.getUserId();
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setInt(3, accountId);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                throw OAuthExceptionCodes.ACCOUNT_NOT_FOUND.create(Integer.valueOf(accountId), Integer.valueOf(userId), Integer.valueOf(contextId));
            }

            DefaultOAuthAccount account = new DefaultOAuthAccount();
            account.setId(accountId);
            String displayName = rs.getString(1);
            account.setDisplayName(displayName);
            if (loadSecrets) {
                try {
                    SecretEncryptionService<PWUpdate> encryptionService = Services.getService(SecretEncryptionFactoryService.class).createService(this);
                    account.setToken(decryptToken(rs.getString(2), accountId, session, encryptionService));
                    account.setSecret(decryptSecret(rs.getString(3), accountId, session, encryptionService));
                } catch (OXException e) {
                    if (CryptoErrorMessage.BadPassword.equals(e)) {
                        throw e;
                    }

                    throw OAuthExceptionCodes.INVALID_ACCOUNT_EXTENDED.create(e.getCause(), displayName, I(accountId));
                }
            }

            if (Strings.isEmpty(account.getSecret())) {
                LOG.debug("The account {} of user {} in context {} has an empty secret", I(accountId), I(session.getUserId()), I(session.getContextId()));
            }

            account.setMetaData(registry.getService(rs.getString(4), userId, contextId));
            String scopes = rs.getString(5);
            OAuthScopeRegistry scopeRegistry = Services.getService(OAuthScopeRegistry.class);
            Set<OAuthScope> enabledScopes = scopeRegistry.getAvailableScopes(account.getMetaData().getAPI(), OXScope.valuesOf(scopes));
            account.setEnabledScopes(enabledScopes);
            account.setUserIdentity(rs.getString(6));
            account.setExpiration(rs.getLong(7));
            return account;
        } finally {
            closeSQLStuff(rs, stmt);
        }
    }

    private OAuthAccount getAccount(int contextId, int userId, int accountId, Connection connection) throws SQLException, OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.prepareStatement("SELECT displayName, serviceId, scope FROM oauthAccounts WHERE cid = ? AND user = ? and id = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setInt(3, accountId);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                throw OAuthExceptionCodes.ACCOUNT_NOT_FOUND.create(Integer.valueOf(accountId), Integer.valueOf(userId), Integer.valueOf(contextId));
            }

            DefaultOAuthAccount account = new DefaultOAuthAccount();
            account.setId(accountId);
            String displayName = rs.getString(1);
            account.setDisplayName(displayName);

            account.setMetaData(registry.getService(rs.getString(2), userId, contextId));
            String scopes = rs.getString(3);
            OAuthScopeRegistry scopeRegistry = Services.getService(OAuthScopeRegistry.class);
            Set<OAuthScope> enabledScopes = scopeRegistry.getAvailableScopes(account.getMetaData().getAPI(), OXScope.valuesOf(scopes));
            account.setEnabledScopes(enabledScopes);
            return account;
        } finally {
            closeSQLStuff(rs, stmt);
        }
    }

    /**
     *
     * @param session
     * @param serviceId
     * @param accountId
     * @param connection
     * @return
     * @throws OXException
     */
    private String getUserIdentity(Session session, String serviceId, int accountId, Connection connection) throws OXException {
        if (connection != null) {
            // Use given connection
            return doGetUserIdentity(session, serviceId, accountId, connection);
        }

        // Acquire connection...
        final Context context = getContext(session.getContextId());
        Connection con = getConnection(true, context);
        try {
            return doGetUserIdentity(session, serviceId, accountId, con);
        } finally {
            provider.releaseReadConnection(context, con);
        }
    }

    private String doGetUserIdentity(Session session, String serviceId, int accountId, Connection connection) throws OXException {
        int contextId = session.getContextId();
        int userId = session.getUserId();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.prepareStatement("SELECT identity FROM oauthAccounts WHERE cid = ? AND user = ? AND serviceId = ? AND id = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setString(3, serviceId);
            stmt.setInt(4, accountId);
            rs = stmt.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(rs, stmt);
        }
    }

    /**
     * Retrieves the {@link Context} with the specified identifier from the storage
     *
     * @param contextId The {@link Context} identifier
     * @return The {@link Context}
     * @throws OXException if the {@link Context} cannot be retrieved
     */
    Context getContext(final int contextId) throws OXException {
        try {
            return contextService.getContext(contextId);
        } catch (OXException e) {
            throw e;
        }
    }

    /**
     * Releases specified read-only connection.
     *
     * @param ctx The associated context
     * @param con The connection to release
     */
    void releaseReadConnection(Context context, Connection connection) {
        provider.releaseReadConnection(context, connection);
    }

    /**
     * Releases specified read-write connection.
     *
     * @param ctx The associated context
     * @param con The connection to release
     */
    void releaseWriteConnection(Context context, Connection connection) {
        provider.releaseWriteConnection(context, connection);
    }

    /**
     * Retrieves a {@link ConnectException} from the {@link DBProvider}
     *
     * @param readOnly <code>true</code> to retrieve a read-only {@link Connection}
     * @param context The {@link Context} for which to retrieve the {@link Connection}
     * @return The {@link Connection}
     * @throws OXException if the {@link Connection} cannot be retrieved
     */
    Connection getConnection(final boolean readOnly, final Context context) throws OXException {
        return readOnly ? provider.getReadConnection(context) : provider.getWriteConnection(context);
    }

    /**
     *
     * @param contextId
     * @param command
     * @param values
     * @throws OXException
     */
    private void executeUpdate(int contextId, Command command, List<Object> values) throws OXException {
        Context ctx = getContext(contextId);
        Connection writeCon = getConnection(false, ctx);
        try {
            executeUpdate(command, values, writeCon);
        } finally {
            if (writeCon != null) {
                provider.releaseWriteConnection(ctx, writeCon);
            }
        }
    }

    /**
     *
     * @param command
     * @param values
     * @param writeCon
     * @throws OXException
     */
    private void executeUpdate(Command command, List<Object> values, Connection writeCon) throws OXException {
        try {
            new StatementBuilder().executeStatement(writeCon, command, values);
        } catch (SQLException e) {
            LOG.error(e.toString());
            throw OAuthExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        }
    }

    /**
     *
     * @param toEncrypt
     * @param session
     * @return
     * @throws OXException
     */
    private String encrypt(final String toEncrypt, final Session session) throws OXException {
        if (Strings.isEmpty(toEncrypt)) {
            return toEncrypt;
        }
        final SecretEncryptionService<PWUpdate> service = Services.getService(SecretEncryptionFactoryService.class).createService(this);
        return service.encrypt(session, toEncrypt);
    }

    private static String decryptToken(String encrypted, int accountId, Session session, SecretEncryptionService<PWUpdate> encryptionService) throws OXException {
        return decrypt(true, encrypted, accountId, session, encryptionService);
    }

    private static String decryptSecret(String encrypted, int accountId, Session session, SecretEncryptionService<PWUpdate> encryptionService) throws OXException {
        return decrypt(false, encrypted, accountId, session, encryptionService);
    }

    private static String decrypt(boolean token, String encrypted, int accountId, Session session, SecretEncryptionService<PWUpdate> encryptionService) throws OXException {
        return encryptionService.decrypt(session, encrypted, new PWUpdate(token ? "accessToken" : "accessSecret", session.getContextId(), accountId));
    }

    /**
     * {@link Setter}
     */
    private static interface Setter {

        void appendTo(StringBuilder stmtBuilder);

        int set(int pos, PreparedStatement stmt) throws SQLException;
    }

    /**
     *
     * @param arguments
     * @param accountId // FIXME: remove when TEMP is removed
     * @return
     * @throws OXException
     */
    @SuppressWarnings("unchecked")
    private List<Setter> setterFrom(final Map<String, Object> arguments, int accountId) throws OXException {
        final List<Setter> ret = new ArrayList<>(4);
        /*
         * Check for display name
         */
        final String displayName = (String) arguments.get(OAuthConstants.ARGUMENT_DISPLAY_NAME);
        if (null != displayName) {
            ret.add(new Setter() {

                @Override
                public int set(final int pos, final PreparedStatement stmt) throws SQLException {
                    stmt.setString(pos, displayName);
                    return pos + 1;
                }

                @Override
                public void appendTo(final StringBuilder stmtBuilder) {
                    stmtBuilder.append("displayName = ?");
                }
            });
        }
        /*
         * Check for request token
         */
        final OAuthToken token = (OAuthToken) arguments.get(OAuthConstants.ARGUMENT_REQUEST_TOKEN);
        if (null != token) {
            /*
             * Crypt tokens
             */
            final Session session = (Session) arguments.get(OAuthConstants.ARGUMENT_SESSION);
            if (null == session) {
                throw OAuthExceptionCodes.MISSING_ARGUMENT.create(OAuthConstants.ARGUMENT_SESSION);
            }
            final String sToken = encrypt(token.getToken(), session);
            final String secret = encrypt(token.getSecret(), session);
            final long expiry = token.getExpiration();
            ret.add(new Setter() {

                @Override
                public int set(final int pos, final PreparedStatement stmt) throws SQLException {
                    stmt.setString(pos, sToken);
                    stmt.setString(pos + 1, secret);
                    stmt.setLong(pos + 2, expiry);
                    if (Strings.isEmpty(secret)) {
                        LOG.debug("Setting empty OAuth secret for account '{}' of user '{}' in context '{}'", I(accountId), I(session.getUserId()), I(session.getContextId()));
                    }
                    return pos + 3;
                }

                @Override
                public void appendTo(final StringBuilder stmtBuilder) {
                    stmtBuilder.append("accessToken = ?, accessSecret = ?, expiryDate = ?");
                }
            });
        }

        /*
         * Scopes
         */
        final Set<OAuthScope> scopes = (Set<OAuthScope>) arguments.get(OAuthConstants.ARGUMENT_SCOPES);
        if (null != scopes) {
            ret.add(new Setter() {

                @Override
                public void appendTo(StringBuilder stmtBuilder) {
                    stmtBuilder.append("scope = ?");
                }

                @Override
                public int set(int pos, PreparedStatement stmt) throws SQLException {
                    String scope = Strings.concat(" ", scopes.toArray());
                    stmt.setString(pos, scope);
                    return pos + 1;
                }
            });
        }
        /*
         * Check for display name
         */
        final String identity = (String) arguments.get(OAuthConstants.ARGUMENT_IDENTITY);
        if (null != identity) {
            ret.add(new Setter() {

                @Override
                public int set(final int pos, final PreparedStatement stmt) throws SQLException {
                    stmt.setString(pos, identity);
                    return pos + 1;
                }

                @Override
                public void appendTo(final StringBuilder stmtBuilder) {
                    stmtBuilder.append("identity = ?");
                }
            });
        }
        /*
         * Other arguments?
         */
        return ret;
    }
}
