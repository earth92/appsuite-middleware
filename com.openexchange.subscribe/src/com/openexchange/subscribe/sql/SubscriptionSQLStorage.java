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

package com.openexchange.subscribe.sql;

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.sql.grammar.Constant.PLACEHOLDER;
import static com.openexchange.subscribe.SubscriptionErrorMessage.IDGiven;
import static com.openexchange.subscribe.SubscriptionErrorMessage.SQLException;
import static com.openexchange.subscribe.SubscriptionErrorMessage.SubscriptionNotFound;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.openexchange.database.Databases;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.datatypes.genericonf.storage.GenericConfigurationStorageService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.impl.IDGenerator;
import com.openexchange.sql.builder.StatementBuilder;
import com.openexchange.sql.grammar.DELETE;
import com.openexchange.sql.grammar.EQUALS;
import com.openexchange.sql.grammar.Expression;
import com.openexchange.sql.grammar.IN;
import com.openexchange.sql.grammar.INSERT;
import com.openexchange.sql.grammar.LIST;
import com.openexchange.sql.grammar.SELECT;
import com.openexchange.sql.grammar.Table;
import com.openexchange.sql.grammar.UPDATE;
import com.openexchange.subscribe.AdministrativeSubscriptionStorage;
import com.openexchange.subscribe.EncryptedField;
import com.openexchange.subscribe.Subscription;
import com.openexchange.subscribe.SubscriptionSourceDiscoveryService;
import com.openexchange.user.User;

/**
 * @author <a href="mailto:martin.herfurth@open-xchange.org">Martin Herfurth</a>
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias Prinz</a> - deleteAllSubscriptionsForUser
 */
public class SubscriptionSQLStorage implements AdministrativeSubscriptionStorage {

    static final Table subscriptions = new Table("subscriptions");

    private final DBProvider dbProvider;
    private final DBTransactionPolicy txPolicy;

    private final GenericConfigurationStorageService storageService;

    private final SubscriptionSourceDiscoveryService discoveryService;

    public SubscriptionSQLStorage(final DBProvider dbProvider, final GenericConfigurationStorageService storageService, final SubscriptionSourceDiscoveryService discoveryService) {
        this(dbProvider, DBTransactionPolicy.NORMAL_TRANSACTIONS, storageService, discoveryService);
    }

    public SubscriptionSQLStorage(final DBProvider dbProvider, final DBTransactionPolicy txPolicy, final GenericConfigurationStorageService storageService, final SubscriptionSourceDiscoveryService discoveryService) {
        this.dbProvider = dbProvider;
        this.txPolicy = txPolicy;
        this.storageService = storageService;
        this.discoveryService = discoveryService;
    }

    @Override
    public List<Subscription> getSubscriptionsForContext(Context ctx) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection con = dbProvider.getReadConnection(ctx);
        try {
            stmt = con.prepareStatement("SELECT id, folder_id, last_update, created, user_id, enabled, configuration_id, source_id FROM subscriptions WHERE cid=?");
            stmt.setInt(1, ctx.getContextId());
            rs = stmt.executeQuery();
            return parseResultSet(rs, ctx, con);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
            dbProvider.releaseReadConnection(ctx, con);
        }
    }

    @Override
    public List<Subscription> getSubscriptionsForContextAndProvider(Context ctx, String sourceId) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection con = dbProvider.getReadConnection(ctx);
        try {
            stmt = con.prepareStatement("SELECT id, folder_id, last_update, created, user_id, enabled, configuration_id, source_id FROM subscriptions WHERE cid=? AND source_id=?");
            stmt.setInt(1, ctx.getContextId());
            stmt.setString(2, sourceId);
            rs = stmt.executeQuery();
            return parseResultSet(rs, ctx, con);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
            dbProvider.releaseReadConnection(ctx, con);
        }
    }

    @Override
    public void forgetSubscription(final Subscription subscription) throws OXException {
        if (!exist(subscription.getId(), subscription.getContext())) {
            return;
        }

        Connection writeConnection = null;
        int rollback = 0;
        try {
            writeConnection = dbProvider.getWriteConnection(subscription.getContext());
            txPolicy.setAutoCommit(writeConnection, false);
            rollback = 1;

            delete(subscription, writeConnection);

            txPolicy.commit(writeConnection);
            rollback = 2;
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(writeConnection);
                }
                Databases.autocommit(writeConnection);
            }
            if (writeConnection != null) {
                dbProvider.releaseWriteConnection(subscription.getContext(), writeConnection);
            }
        }
    }

    @Override
    public Subscription getSubscription(final Context ctx, final int id) throws OXException {
        ResultSet resultSet = null;
        StatementBuilder builder = null;
        Connection readConnection = dbProvider.getReadConnection(ctx);
        try {
            final SELECT select = new SELECT("id", "user_id", "configuration_id", "source_id", "folder_id", "last_update", "created", "enabled").FROM(subscriptions).WHERE(new EQUALS("id", PLACEHOLDER).AND(new EQUALS("cid", PLACEHOLDER)));

            final List<Object> values = new ArrayList<Object>();
            values.add(I(id));
            values.add(I(ctx.getContextId()));

            builder = new StatementBuilder();
            resultSet = builder.executeQuery(readConnection, select, values);
            final List<Subscription> subscriptions = parseResultSet(resultSet, ctx, readConnection);

            Subscription retval = null;
            if (!subscriptions.isEmpty()) {
                retval = subscriptions.get(0);
            }
            return retval;
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (builder != null) {
                    builder.closePreparedStatement(null, resultSet);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            } finally {
                dbProvider.releaseReadConnection(ctx, readConnection);
            }
        }
    }

    @Override
    public List<Subscription> getSubscriptions(final Context ctx, final String folderId) throws OXException {
        List<Subscription> retval = null;

        ResultSet resultSet = null;
        StatementBuilder builder = null;
        Connection readConnection = dbProvider.getReadConnection(ctx);
        try {
            final SELECT select = new SELECT("id", "user_id", "configuration_id", "source_id", "folder_id", "last_update", "created", "enabled").FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("folder_id", PLACEHOLDER)));

            final List<Object> values = new ArrayList<Object>();
            values.add(I(ctx.getContextId()));
            values.add(folderId);

            builder = new StatementBuilder();
            resultSet = builder.executeQuery(readConnection, select, values);
            retval = parseResultSet(resultSet, ctx, readConnection);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (builder != null) {
                    builder.closePreparedStatement(null, resultSet);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            } finally {
                dbProvider.releaseReadConnection(ctx, readConnection);
            }
        }

        return retval;
    }

    @Override
    public List<Subscription> getSubscriptionsOfUser(Context ctx, int userId, String sourceId) throws OXException {
        Connection con = dbProvider.getReadConnection(ctx);
        try {
            return getSubscriptionsOfUser(ctx, userId, sourceId, con);
        } finally {
            dbProvider.releaseReadConnection(ctx, con);
        }
    }

    public List<Subscription> getSubscriptionsOfUser(Context ctx, int userId, String sourceId, Connection con) throws OXException {
        if (null == con) {
            return getSubscriptionsOfUser(ctx, userId, sourceId);
        }
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT id, folder_id, last_update, created, user_id, enabled, configuration_id, source_id FROM subscriptions WHERE cid=? AND user_id=? AND source_id = ?");
            stmt.setInt(1, ctx.getContextId());
            stmt.setInt(2, userId);
            stmt.setString(3, sourceId);
            rs = stmt.executeQuery();
            return parseResultSet(rs, ctx, con);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    @Override
    public List<Subscription> getSubscriptionsOfUser(Context ctx, int userId) throws OXException {
        ResultSet resultSet = null;
        StatementBuilder builder = null;
        Connection readConnection = dbProvider.getReadConnection(ctx);
        try {
            final SELECT select = new SELECT("*").FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("user_id", PLACEHOLDER)));

            final List<Object> values = new LinkedList<Object>();
            values.add(I(ctx.getContextId()));
            values.add(I(userId));

            builder = new StatementBuilder();
            resultSet = builder.executeQuery(readConnection, select, values);
            return parseResultSet(resultSet, ctx, readConnection);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (builder != null) {
                    builder.closePreparedStatement(null, resultSet);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            } finally {
                dbProvider.releaseReadConnection(ctx, readConnection);
            }
        }
    }

    @Override
    public void rememberSubscription(final Subscription subscription) throws OXException {
        if (subscription.getId() > 0) {
            throw IDGiven.create();
        }

        Connection writeConnection = null;
        int rollback = 0;
        try {
            writeConnection = dbProvider.getWriteConnection(subscription.getContext());
            txPolicy.setAutoCommit(writeConnection, false);
            rollback = 1;

            final int id = save(subscription, writeConnection);
            subscription.setId(id);

            txPolicy.commit(writeConnection);
            rollback = 2;
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(writeConnection);
                }
                Databases.autocommit(writeConnection);
            }
            if (writeConnection != null) {
                dbProvider.releaseWriteConnection(subscription.getContext(), writeConnection);
            }
        }
    }

    @Override
    public void updateSubscription(final Subscription subscription) throws OXException {
        if (!exist(subscription.getId(), subscription.getContext())) {
            throw SubscriptionNotFound.create();
        }

        Connection writeConnection = null;
        int rollback = 0;
        try {
            writeConnection = dbProvider.getWriteConnection(subscription.getContext());
            txPolicy.setAutoCommit(writeConnection, false);
            rollback = 1;

            update(subscription, writeConnection);

            txPolicy.commit(writeConnection);
            rollback = 2;
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(writeConnection);
                }
                Databases.autocommit(writeConnection);
            }
            if (writeConnection != null) {
                dbProvider.releaseWriteConnection(subscription.getContext(), writeConnection);
            }
        }
    }

    @Override
    public boolean deleteSubscription(Context ctx, int userId, int id) throws OXException {
        Connection writeConnection = dbProvider.getWriteConnection(ctx);
        try {
            return delete(getSubscription(ctx, id), writeConnection);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            dbProvider.releaseWriteConnection(ctx, writeConnection);
        }
    }

    @Override
    public boolean deleteSubscription(Context ctx, int userId, int id, Connection connection) throws OXException {
        try {
            return delete(getSubscription(ctx, id), connection);
        } catch (SQLException e) {
            throw SQLException.create(e);
        }
    }

    private boolean delete(final Subscription subscription, final Connection writeConnection) throws SQLException, OXException {
        storageService.delete(writeConnection, subscription.getContext(), getConfigurationId(subscription));

        final DELETE delete = new DELETE().FROM(subscriptions).WHERE(new EQUALS("id", PLACEHOLDER).AND(new EQUALS("cid", PLACEHOLDER)));

        final List<Object> values = new ArrayList<Object>();
        values.add(I(subscription.getId()));
        values.add(I(subscription.getContext().getContextId()));
        
        return new StatementBuilder().executeStatement(writeConnection, delete, values) > 0;
    }

    private int save(final Subscription subscription, final Connection writeConnection) throws OXException, SQLException {
        final int configId = storageService.save(writeConnection, subscription.getContext(), subscription.getConfiguration());

        final int id = IDGenerator.getId(subscription.getContext().getContextId(), Types.SUBSCRIPTION, writeConnection);

        final INSERT insert = new INSERT().INTO(subscriptions).SET("id", PLACEHOLDER).SET("cid", PLACEHOLDER).SET("user_id", PLACEHOLDER).SET("configuration_id", PLACEHOLDER).SET("source_id", PLACEHOLDER).SET("folder_id", PLACEHOLDER).SET("last_update", PLACEHOLDER).SET("created", PLACEHOLDER).SET("enabled", PLACEHOLDER);

        final List<Object> values = new ArrayList<Object>();
        values.add(I(id));
        values.add(I(subscription.getContext().getContextId()));
        values.add(I(subscription.getUserId()));
        values.add(I(configId));
        values.add(subscription.getSource().getId());
        values.add(subscription.getFolderId());
        values.add(L(subscription.getLastUpdate()));
        values.add(L(System.currentTimeMillis()));
        values.add(B(subscription.isEnabled()));

        new StatementBuilder().executeStatement(writeConnection, insert, values);
        return id;
    }

    private void update(final Subscription subscription, final Connection writeConnection) throws OXException, SQLException {
        if (subscription.getConfiguration() != null) {
            final int configId = getConfigurationId(subscription);
            storageService.update(writeConnection, subscription.getContext(), configId, subscription.getConfiguration());
        }

        final UPDATE update = new UPDATE(subscriptions);
        final List<Object> values = new ArrayList<Object>();

        if (subscription.containsUserId()) {
            update.SET("user_id", PLACEHOLDER);
            values.add(I(subscription.getUserId()));
        }
        if (subscription.containsSource()) {
            update.SET("source_id", PLACEHOLDER);
            values.add(subscription.getSource().getId());
        }
        if (subscription.containsFolderId()) {
            update.SET("folder_id", PLACEHOLDER);
            values.add(subscription.getFolderId());
        }
        if (subscription.containsLastUpdate()) {
            update.SET("last_update", PLACEHOLDER);
            values.add(L(subscription.getLastUpdate()));
        }
        if (subscription.containsEnabled()) {
            update.SET("enabled", PLACEHOLDER);
            values.add(B(subscription.isEnabled()));
        }

        update.WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("id", PLACEHOLDER)));
        values.add(I(subscription.getContext().getContextId()));
        values.add(I(subscription.getId()));

        if (values.size() > 2) {
            new StatementBuilder().executeStatement(writeConnection, update, values);
        }
    }

    @Override
    public void touch(Context ctx, int subscriptionId, long currentTimeMillis) throws OXException {
        Connection writeConnection = null;
        int rollback = 0;
        try {
            writeConnection = dbProvider.getWriteConnection(ctx);
            txPolicy.setAutoCommit(writeConnection, false);
            rollback = 1;

            new StatementBuilder().executeStatement(writeConnection, new UPDATE(subscriptions).SET("last_update", PLACEHOLDER).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("id", PLACEHOLDER))), Arrays.<Object> asList(L(currentTimeMillis), I(ctx.getContextId()), I(subscriptionId)));

            txPolicy.commit(writeConnection);
            rollback = 2;
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(writeConnection);
                }
                Databases.autocommit(writeConnection);
            }
            if (writeConnection != null) {
                dbProvider.releaseWriteConnection(ctx, writeConnection);
            }
        }
    }

    private int getConfigurationId(final Subscription subscription) throws OXException {
        int retval = 0;
        Connection readConection = null;
        ResultSet resultSet = null;
        StatementBuilder builder = null;
        try {
            readConection = dbProvider.getReadConnection(subscription.getContext());

            final SELECT select = new SELECT("configuration_id").FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("id", PLACEHOLDER)));

            final List<Object> values = new ArrayList<Object>();
            values.add(I(subscription.getContext().getContextId()));
            values.add(I(subscription.getId()));

            builder = new StatementBuilder();
            resultSet = builder.executeQuery(readConection, select, values);

            if (resultSet.next()) {
                retval = resultSet.getInt("configuration_id");
            }
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (builder != null) {
                    builder.closePreparedStatement(null, resultSet);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            } finally {
                dbProvider.releaseReadConnection(subscription.getContext(), readConection);
            }
        }
        return retval;
    }

    private List<Subscription> parseResultSet(final ResultSet resultSet, final Context ctx, final Connection readConnection) throws OXException, SQLException {
        if (!resultSet.next()) {
            return Collections.emptyList();
        }

        List<Subscription> retval = new LinkedList<Subscription>();
        do {
            final Subscription subscription = new Subscription();
            subscription.setContext(ctx);
            subscription.setFolderId(resultSet.getString("folder_id"));
            subscription.setId(resultSet.getInt("id"));
            subscription.setLastUpdate(resultSet.getLong("last_update"));
            subscription.setCreated(resultSet.getLong("created"));
            subscription.setUserId(resultSet.getInt("user_id"));
            subscription.setEnabled(resultSet.getBoolean("enabled"));

            final Map<String, Object> content = new HashMap<String, Object>();
            storageService.fill(readConnection, ctx, resultSet.getInt("configuration_id"), content);

            String sourceId = resultSet.getString("source_id");
            content.put("source_id", sourceId);

            subscription.setConfiguration(content);
            subscription.setSource(discoveryService.getSource(sourceId));

            retval.add(subscription);
        } while (resultSet.next());
        return retval;
    }

    private boolean exist(final int id, final Context ctx) throws OXException {
        boolean retval = false;

        Connection readConnection = null;
        ResultSet resultSet = null;
        StatementBuilder builder = null;
        try {
            readConnection = dbProvider.getReadConnection(ctx);
            final SELECT select = new SELECT("id").FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("id", PLACEHOLDER)));

            final List<Object> values = new ArrayList<Object>();
            values.add(I(ctx.getContextId()));
            values.add(I(id));

            builder = new StatementBuilder();
            resultSet = builder.executeQuery(readConnection, select, values);
            retval = resultSet.next();
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (builder != null) {
                    builder.closePreparedStatement(null, resultSet);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            } finally {
                dbProvider.releaseReadConnection(ctx, readConnection);
            }
        }

        return retval;
    }

    @Override
    public void deleteAllSubscriptionsForUser(final int userId, final Context ctx) throws OXException {
        Connection writeConnection = null;
        try {
            writeConnection = dbProvider.getWriteConnection(ctx);
            txPolicy.setAutoCommit(writeConnection, false);
            deleteAllSubscriptionsForUser(userId, ctx, writeConnection);
            txPolicy.commit(writeConnection);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (writeConnection != null) {
                    txPolicy.rollback(writeConnection);
                    txPolicy.setAutoCommit(writeConnection, true);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            }
            dbProvider.releaseWriteConnection(ctx, writeConnection);
        }
    }

    @Override
    public void deleteAllSubscriptionsForUser(int userId, Context ctx, Connection connection) throws OXException {
        try {
            final List<Subscription> subs = getSubscriptionsOfUser(ctx, userId);
            for (final Subscription sub : subs) {
                delete(sub, connection);
            }
        } catch (SQLException e) {
            throw SQLException.create(e);
        }
    }

    @Override
    public void deleteAllSubscriptionsInContext(final int contextId, final Context ctx) throws OXException {
        Connection writeConnection = null;
        try {
            writeConnection = dbProvider.getWriteConnection(ctx);
            txPolicy.setAutoCommit(writeConnection, false);
            final DELETE delete = new DELETE().FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER));

            final List<Object> values = new ArrayList<Object>();
            values.add(I(ctx.getContextId()));

            new StatementBuilder().executeStatement(writeConnection, delete, values);
            storageService.delete(writeConnection, ctx);
            txPolicy.commit(writeConnection);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (writeConnection != null) {
                    txPolicy.rollback(writeConnection);
                    txPolicy.setAutoCommit(writeConnection, true);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            }
            dbProvider.releaseWriteConnection(ctx, writeConnection);
        }
    }

    @Override
    public void deleteAllSubscriptionsWhereConfigMatches(final Map<String, Object> query, final String sourceId, final Context ctx) throws OXException {
        boolean modified = false;
        Connection writeConnection = null;
        try {
            writeConnection = dbProvider.getWriteConnection(ctx);
            txPolicy.setAutoCommit(writeConnection, false);
            final DELETE delete = new DELETE().FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("source_id", PLACEHOLDER)).AND(new EQUALS("configuration_id", PLACEHOLDER)));
            final List<Object> values = new ArrayList<Object>(Arrays.asList(null, null, null));
            values.set(0, I(ctx.getContextId()));
            values.set(1, sourceId);

            final List<Integer> configIds = storageService.search(ctx, query);
            for (final Integer configId : configIds) {
                values.set(2, configId);
                final int deleted = new StatementBuilder().executeStatement(writeConnection, delete, values);
                if (deleted == 1) {
                    // Delete the generic configuration only if the source_id matched
                    storageService.delete(writeConnection, ctx, configId.intValue());
                    modified = true;
                }
            }
            txPolicy.commit(writeConnection);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (writeConnection != null) {
                    txPolicy.rollback(writeConnection);
                    txPolicy.setAutoCommit(writeConnection, true);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            }
            if (modified) {
                dbProvider.releaseWriteConnection(ctx, writeConnection);
            } else {
                dbProvider.releaseWriteConnectionAfterReading(ctx, writeConnection);
            }
        }
    }

    @Override
    public Map<String, Boolean> hasSubscriptions(final Context ctx, final List<String> folderIds) throws OXException {

        final Map<String, Boolean> retval = new HashMap<String, Boolean>();
        for (final String folderId : folderIds) {
            retval.put(folderId, Boolean.FALSE);
        }

        Connection readConnection = null;
        ResultSet resultSet = null;
        StatementBuilder builder = null;
        try {
            readConnection = dbProvider.getReadConnection(ctx);
            final ArrayList<Expression> placeholders = new ArrayList<Expression>(folderIds.size());
            for (int i = 0; i < folderIds.size(); i++) {
                placeholders.add(PLACEHOLDER);
            }

            final SELECT select = new SELECT("folder_id").FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new IN("folder_id", new LIST(placeholders))));

            final List<Object> values = new ArrayList<Object>();
            values.add(I(ctx.getContextId()));
            values.addAll(folderIds);

            builder = new StatementBuilder();
            resultSet = builder.executeQuery(readConnection, select, values);
            while (resultSet.next()) {
                final String folderId = resultSet.getString(1);
                retval.put(folderId, Boolean.TRUE);
            }
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (builder != null) {
                    builder.closePreparedStatement(null, resultSet);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            } finally {
                dbProvider.releaseReadConnection(ctx, readConnection);
            }
        }

        return retval;

    }

    @Override
    public boolean hasSubscriptions(final Context ctx, final User user) throws OXException {

        Connection readConnection = null;
        ResultSet resultSet = null;
        StatementBuilder builder = null;
        try {
            readConnection = dbProvider.getReadConnection(ctx);
            final SELECT select = new SELECT("1").FROM(subscriptions).WHERE(new EQUALS("cid", PLACEHOLDER).AND(new EQUALS("user_id", PLACEHOLDER)));

            final List<Object> values = new ArrayList<Object>();
            values.add(I(ctx.getContextId()));
            values.add(I(user.getId()));

            builder = new StatementBuilder();
            resultSet = builder.executeQuery(readConnection, select, values);
            return resultSet.next();
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            try {
                if (builder != null) {
                    builder.closePreparedStatement(null, resultSet);
                }
            } catch (SQLException e) {
                throw SQLException.create(e);
            } finally {
                dbProvider.releaseReadConnection(ctx, readConnection);
            }
        }
    }

    @Override
    public void update(final String recrypted, final EncryptedField customizationNote) throws OXException {
        final int configId = getConfigurationId(customizationNote.subscription);
        final Map<String, Object> update = new HashMap<String, Object>();
        update.put(customizationNote.field, recrypted);

        storageService.update(customizationNote.subscription.getContext(), configId, update);

    }

    @Override
    public List<Subscription> getSubscriptionsForContext(Context ctx, String sourceId, Connection con) throws OXException {
        if (null == con) {
            return getSubscriptionsForContext(ctx, sourceId);
        }

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT id, folder_id, last_update, created, user_id, enabled, configuration_id, source_id FROM subscriptions WHERE cid=? AND source_id = ?");
            stmt.setInt(1, ctx.getContextId());
            stmt.setString(2, sourceId);
            rs = stmt.executeQuery();
            return parseResultSet(rs, ctx, con);
        } catch (SQLException e) {
            throw SQLException.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    private List<Subscription> getSubscriptionsForContext(Context ctx, String sourceId) throws OXException {
        Connection con = dbProvider.getReadConnection(ctx);
        try {
            return getSubscriptionsForContext(ctx, sourceId, con);
        } finally {
            dbProvider.releaseReadConnection(ctx, con);
        }
    }
}
