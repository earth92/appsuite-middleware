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

package com.openexchange.pns.subscription.storage.rdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import com.openexchange.context.ContextService;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.java.util.UUIDs;
import com.openexchange.pns.DefaultPushSubscription;
import com.openexchange.pns.DefaultPushSubscription.Builder;
import com.openexchange.pns.KnownTopic;
import com.openexchange.pns.PushExceptionCodes;
import com.openexchange.pns.PushMatch;
import com.openexchange.pns.PushNotifications;
import com.openexchange.pns.PushSubscription;
import com.openexchange.pns.PushSubscriptionRegistry;
import com.openexchange.pns.PushSubscriptionResult;
import com.openexchange.pns.subscription.storage.ClientAndTransport;
import com.openexchange.pns.subscription.storage.MapBackedHits;
import com.openexchange.pns.subscription.storage.rdb.cache.RdbPushSubscriptionRegistryCache;

/**
 * {@link RdbPushSubscriptionRegistry}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.3
 */
public class RdbPushSubscriptionRegistry implements PushSubscriptionRegistry {

    /** Controls whether to use cache instance instead of querying database */
    private static final boolean USE_CACHE = false;

    private final DatabaseService databaseService;
    private final ContextService contextService;
    private volatile RdbPushSubscriptionRegistryCache cache;

    /**
     * Initializes a new {@link RdbPushSubscriptionRegistry}.
     *
     * @param databaseService The database service to use
     * @param contextService The context service
     */
    public RdbPushSubscriptionRegistry(DatabaseService databaseService, ContextService contextService) {
        super();
        this.databaseService = databaseService;
        this.contextService = contextService;
    }

    /**
     * Sets the cache instance (if enabled)
     *
     * @param cache The cache
     */
    public void setCache(RdbPushSubscriptionRegistryCache cache) {
        if (USE_CACHE) {
            this.cache = cache;
        }
    }

    /**
     * Loads the subscriptions belonging to given user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The user-associated subscriptions
     * @throws OXException If subscriptions cannot be loaded
     */
    public List<PushSubscription> loadSubscriptionsFor(int userId, int contextId) throws OXException {
        Connection con = databaseService.getReadOnly(contextId);
        try {
            return loadSubscriptionsFor(userId, contextId, con);
        } finally {
            databaseService.backReadOnly(contextId, con);
        }
    }

    /**
     * Loads the subscriptions belonging to given user.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param con The connection to use
     * @return The user-associated subscriptions
     * @throws OXException If subscriptions cannot be loaded
     */
    public List<PushSubscription> loadSubscriptionsFor(int userId, int contextId, Connection con) throws OXException {
        if (null == con) {
            return loadSubscriptionsFor(userId, contextId);
        }

        Map<UUID, BuilderAndTopics> builders;
        {
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = con.prepareStatement(
                    "SELECT p.id, p.token, p.client, p.transport, p.all_flag, p.expires, w.topic AS prefix, e.topic FROM pns_subscription p"
                        + " LEFT JOIN pns_subscription_topic_wildcard w ON p.id=w.id"
                        + " LEFT JOIN pns_subscription_topic_exact e ON p.id=e.id"
                        + " WHERE p.cid=? AND p.user=?");
                stmt.setInt(1, contextId);
                stmt.setInt(2, userId);
                rs = stmt.executeQuery();
                if (false == rs.next()) {
                    return Collections.emptyList();
                }

                builders = new LinkedHashMap<>();
                do {
                    UUID uuid = UUIDs.toUUID(rs.getBytes(1));
                    List<String> topics;
                    {
                        BuilderAndTopics builderAndTopics = builders.get(uuid);
                        if (null == builderAndTopics) {
                            // Create new BuilderAndTopics instance and start with a new topics list
                            topics = new LinkedList<>();
                            DefaultPushSubscription.Builder builder = DefaultPushSubscription.builder()
                                .contextId(contextId)
                                .userId(userId)
                                .token(rs.getString(2))
                                .client(rs.getString(3))
                                .transportId(rs.getString(4));
                            if (rs.getInt(5) > 0) {
                                topics.add(KnownTopic.ALL.getName());
                            }
                            long expires = rs.getLong(6);
                            if (false == rs.wasNull()) {
                                builder.expires(new Date(expires));
                            }
                            builderAndTopics = new BuilderAndTopics(builder, topics);
                            builders.put(uuid, builderAndTopics);
                        } else {
                            // Grab topics list
                            topics = builderAndTopics.topics;
                        }
                    }
                    String prefix = rs.getString(7);
                    if (null != prefix) {
                        // E.g. "ox:mail:*"
                        topics.add(prefix + (prefix.endsWith(":") ? "*" : ":*"));
                    }
                    String topic = rs.getString(8);
                    if (null != topic) {
                        // E.g. "ox:mail:new"
                        topics.add(topic);
                    }
                } while (rs.next());

            } catch (SQLException e) {
                throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
            } finally {
                Databases.closeSQLStuff(rs, stmt);
            }
        }

        List<PushSubscription> subscriptions = new ArrayList<>(builders.size());
        for (BuilderAndTopics builderAndTopics : builders.values()) {
            DefaultPushSubscription.Builder builder = builderAndTopics.builder;
            builder.topics(builderAndTopics.topics);
            subscriptions.add(builder.build());
        }
        return filterExpired(contextId, subscriptions);
    }

    @Override
    public boolean hasInterestedSubscriptions(int userId, int contextId, String topic) throws OXException {
        return hasInterestedSubscriptions(null, userId, contextId, topic);
    }

    @Override
    public boolean hasInterestedSubscriptions(String client, int userId, int contextId, String topic) throws OXException {
        // Check cache
        RdbPushSubscriptionRegistryCache cache = this.cache;
        if (null != cache) {
            return cache.getCollectionFor(userId, contextId).hasInterestedSubscriptions(client, topic);
        }

        Connection con = databaseService.getReadOnly(contextId);
        try {
            return hasSubscriptions(client, userId, contextId, topic, con);
        } finally {
            databaseService.backReadOnly(contextId, con);
        }
    }

    /**
     * Gets all subscriptions interested in specified topic belonging to given user.
     *
     * @param optClient The optional client to filter by
     * @param userId The user identifier
     * @param contextId The context identifier
     * @param topic The topic
     * @param con The connection to use
     * @return All subscriptions for specified affiliation
     * @throws OXException If subscriptions cannot be returned
     */
    public boolean hasSubscriptions(String optClient, int userId, int contextId, String topic, Connection con) throws OXException {
        if (null == con) {
            return hasInterestedSubscriptions(optClient, userId, contextId, topic);
        }

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(
                "SELECT 1 FROM pns_subscription s" +
                " LEFT JOIN pns_subscription_topic_wildcard twc ON s.id=twc.id" +
                " LEFT JOIN pns_subscription_topic_exact te ON s.id=te.id" +
                " WHERE s.cid=? AND s.user=?" + (null == optClient ? "" : " AND s.client=?") + " AND ((s.all_flag=1) OR (te.topic=?) OR (? LIKE CONCAT(twc.topic, '%')));");
            int pos = 1;
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            if (null != optClient) {
                stmt.setString(pos++, optClient);
            }
            stmt.setString(pos++, topic);
            stmt.setString(pos, topic);
            rs = stmt.executeQuery();

            return rs.next();
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    @Override
    public MapBackedHits getInterestedSubscriptions(int[] userIds, int contextId, String topic) throws OXException {
        return getInterestedSubscriptions(null, userIds, contextId, topic);
    }

    @Override
    public MapBackedHits getInterestedSubscriptions(String client, int[] userIds, int contextId, String topic) throws OXException {
        // Check cache
        RdbPushSubscriptionRegistryCache cache = this.cache;
        if (null != cache) {
            HashMap<ClientAndTransport, List<PushMatch>> map = new HashMap<>();
            for (int userId : userIds) {
                MapBackedHits hits = cache.getCollectionFor(userId, contextId).getInterestedSubscriptions(client, topic);
                if (null != hits && false == hits.isEmpty()) {
                    map.putAll(hits.getMap());
                }
            }
            return new MapBackedHits(map);
        }

        Map<ClientAndTransport, List<PushMatch>> subscriptions;
        Connection con = databaseService.getReadOnly(contextId);
        try {
            subscriptions = getSubscriptions(client, userIds, contextId, topic, con);
        } finally {
            databaseService.backReadOnly(contextId, con);
        }
        subscriptions = filterExpired(contextId, subscriptions);
        return null == subscriptions || subscriptions.isEmpty() ? MapBackedHits.EMPTY : new MapBackedHits(subscriptions);
    }

    private List<PushSubscription> filterExpired(int contextId, List<PushSubscription> subscriptions) {
        if (null != subscriptions && 0 < subscriptions.size()) {
            Date now = new Date();
            List<PushSubscription> expiredSubscriptions = new ArrayList<PushSubscription>();
            for (Iterator<PushSubscription> iterator = subscriptions.iterator(); iterator.hasNext();) {
                PushSubscription subscription = iterator.next();
                Date expires = subscription.getExpires();
                if (null != expires && expires.before(now)) {
                    expiredSubscriptions.add(subscription);
                    iterator.remove();
                }
            }
            try {
                unregisterSubscriptions(contextId, expiredSubscriptions);
            } catch (OXException e) {
                org.slf4j.LoggerFactory.getLogger(RdbPushSubscriptionRegistry.class).error("Error unregistering expired subscriptions", e);
            }
        }
        return subscriptions;
    }

    private Map<ClientAndTransport, List<PushMatch>> filterExpired(int contextId, Map<ClientAndTransport, List<PushMatch>> subscriptions) {
        if (null != subscriptions && 0 < subscriptions.size()) {
            Date now = new Date();
            List<PushSubscription> expiredSubscriptions = new ArrayList<PushSubscription>();
            for (Iterator<Entry<ClientAndTransport, List<PushMatch>>> entryIterator = subscriptions.entrySet().iterator(); entryIterator.hasNext();) {
                Entry<ClientAndTransport, List<PushMatch>> entry = entryIterator.next();
                for (Iterator<PushMatch> iterator = entry.getValue().iterator(); iterator.hasNext();) {
                    PushMatch match = iterator.next();
                    if (RdbPushMatch.class.isInstance(match)) {
                        Date expires = ((RdbPushMatch) match).getExpires();
                        if (null != expires && expires.before(now)) {
                            expiredSubscriptions.add(DefaultPushSubscription.instanceFor(match));
                            iterator.remove();
                        }
                    }
                }
                if (entry.getValue().isEmpty()) {
                    entryIterator.remove();
                }
            }
            try {
                unregisterSubscriptions(contextId, expiredSubscriptions);
            } catch (OXException e) {
                org.slf4j.LoggerFactory.getLogger(RdbPushSubscriptionRegistry.class).error("Error unregistering expired subscriptions", e);
            }
        }
        return subscriptions;
    }

    private void unregisterSubscriptions(int contextId, List<PushSubscription> subscriptions) throws OXException {
        if (null == subscriptions || 0 == subscriptions.size()) {
            return;
        }

        Connection con = databaseService.getWritable(contextId);
        int rollback = 0;
        boolean modified = false;
        try {
            Databases.startTransaction(con);
            rollback = 1;

            for (PushSubscription subscription : subscriptions) {
                modified |= (null != removeSubscription(subscription, con));
            }

            con.commit();
            rollback = 2;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
            if (modified) {
                databaseService.backWritable(contextId, con);
            } else {
                databaseService.backWritableAfterReading(contextId, con);
            }
        }
    }

    /**
     * Gets all subscriptions interested in specified topic belonging to certain users.
     *
     * @param optClient The optional client to filter by
     * @param userIds The user identifiers
     * @param contextId The context identifier
     * @param topic The topic
     * @param con The connection to use
     * @return All subscriptions for specified affiliation
     * @throws OXException If subscriptions cannot be returned
     */
    private Map<ClientAndTransport, List<PushMatch>> getSubscriptions(String optClient, int[] userIds, int contextId, String topic, Connection con) throws OXException {
        if (null == con) {
            return getInterestedSubscriptions(optClient, userIds, contextId, topic).getMap();
        }
        if (null == userIds || 0 == userIds.length) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder()
            .append("SELECT s.id, s.user, s.token, s.client, s.transport, s.last_modified, s.expires, s.all_flag, twc.topic wildcard, te.topic FROM pns_subscription s")
            .append(" LEFT JOIN pns_subscription_topic_wildcard twc ON s.id=twc.id")
            .append(" LEFT JOIN pns_subscription_topic_exact te ON s.id=te.id")
            .append(" WHERE s.cid=?")
        ;
        if (1 == userIds.length) {
            stringBuilder.append(" AND s.user=?");
        } else {
            stringBuilder.append(" AND s.user IN (?");
            for (int i = 1; i < userIds.length; i++) {
                stringBuilder.append(",?");
            }
            stringBuilder.append(')');
        }
        if (null != optClient) {
            stringBuilder.append(" AND s.client=?");
        }
        stringBuilder.append(" AND ((s.all_flag=1) OR (te.topic=?) OR (? LIKE CONCAT(twc.topic, '%')));");

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(stringBuilder.toString());
            int pos = 1;
            stmt.setInt(pos++, contextId);
            for (int userId : userIds) {
                stmt.setInt(pos++, userId);
            }
            if (null != optClient) {
                stmt.setString(pos++, optClient);
            }
            stmt.setString(pos++, topic);
            stmt.setString(pos, topic);
            rs = stmt.executeQuery();

            if (false == rs.next()) {
                return null;
            }

            Map<ClientAndTransport, List<PushMatch>> map = new LinkedHashMap<>(6);
            Set<UUID> processed = new HashSet<>();
            do {
                UUID id = UUIDs.toUUID(rs.getBytes(1));

                // Check if not yet processed
                if (processed.add(id)) {
                    int userId = rs.getInt(2);
                    String token = rs.getString(3);
                    String client = rs.getString(4);
                    String transportId = rs.getString(5);
                    Date lastModified = new Date(rs.getLong(6));
                    long expiresValue = rs.getLong(7);
                    Date expires = rs.wasNull() ? null : new Date(expiresValue);

                    // Determine matching topic
                    String matchingTopic;
                    {
                        boolean all = rs.getInt(8) > 0;
                        if (all) {
                            matchingTopic = KnownTopic.ALL.getName();
                        } else {
                            matchingTopic = rs.getString(9);
                            if (rs.wasNull()) {
                                matchingTopic = null;
                            } else {
                                // E.g. "ox:mail:*"
                                if (topic.startsWith(matchingTopic)) {
                                    matchingTopic = new StringBuilder(matchingTopic).append('*').toString();
                                } else {
                                    // Unsatisfiable match
                                    matchingTopic = null;
                                }
                            }

                            if (null == matchingTopic) { // Last reason why that row is present in result set
                                // E.g. "ox:mail:new"
                                matchingTopic = rs.getString(10);
                            }
                        }
                    }

                    // Add to appropriate list
                    RdbPushMatch pushMatch = new RdbPushMatch(userId, contextId, client, transportId, token, matchingTopic, lastModified, expires);
                    ClientAndTransport cat = new ClientAndTransport(client, transportId);
                    com.openexchange.tools.arrays.Collections.put(map, cat, pushMatch);
                }
            } while (rs.next());

            return map;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    /*-
     * UUIDs.toByteArray(UUID.randomUUID())
     *
     * "CREATE TABLE pns_subscription (" +
        "id BINARY(16) NOT NULL" +
        "cid INT4 UNSIGNED NOT NULL," +
        "user INT4 UNSIGNED NOT NULL," +
        "client VARCHAR(64) CHARACTER SET latin1 NOT NULL," +
        "transport VARCHAR(32) CHARACTER SET latin1 NOT NULL," +
        "last_modified BIGINT(64) NOT NULL," +
        "PRIMARY KEY (cid, user, token)," +
        "UNIQUE KEY `subscription_id` (`id`)," +
        "INDEX `affiliationIndex` (cid, user, affiliation)" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
     */

    private byte[] getSubscriptionId(int userId, int contextId, String token, String client, Connection con) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT id FROM pns_subscription WHERE cid=? AND user=? AND token=? AND client=?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setString(3, token);
            stmt.setString(4, client);
            rs = stmt.executeQuery();
            return false == rs.next() ? null : rs.getBytes(1);
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    @Override
    public PushSubscriptionResult registerSubscription(PushSubscription subscription) throws OXException {
        if (null == subscription) {
            return PushSubscriptionResult.builder().withError(OXException.general("subscription must not be null")).build();
        }

        int contextId = subscription.getContextId();
        Connection con = databaseService.getWritable(contextId);
        int rollback = 0;
        boolean modified = false;
        try {
            Databases.startTransaction(con);
            rollback = 1;

            PushSubscriptionResult result = registerSubscription(subscription, con);
            if (PushSubscriptionResult.Status.OK == result.getStatus()) {
                modified = true;
            }

            con.commit();
            rollback = 2;
            return result;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
            if (modified) {
                databaseService.backWritable(contextId, con);
            } else {
                databaseService.backWritableAfterReading(contextId, con);
            }
        }
    }

    /**
     * Registers or updates specified subscription.
     *
     * @param subscription The subscription to register
     * @param con The connection to use
     * @throws OXException If registration fails
     */
    public PushSubscriptionResult registerSubscription(PushSubscription subscription, Connection con) throws OXException {
        if (null == con) {
            return registerSubscription(subscription);
        }

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT cid, user FROM pns_subscription WHERE token=? AND transport=? AND client=?");
            stmt.setString(1, subscription.getToken());
            stmt.setString(2, subscription.getTransportId());
            stmt.setString(3, subscription.getClient());
            rs = stmt.executeQuery();
            if (rs.next()) {
                // Such a token has already been registered for given transport and client. Check to what user that token belongs.
                int tokenUsingContextId = rs.getInt(1);
                int tokenUsingUserId = rs.getInt(2);
                if (tokenUsingContextId == subscription.getContextId() && tokenUsingUserId == subscription.getUserId()) {
                    // Actually an update, but may force to invalidate some resources as new registration might ship with other topics. Therefore:
                    return PushSubscriptionResult.builder().withConflictingUserId(tokenUsingUserId, tokenUsingContextId).build();
                }
                return PushSubscriptionResult.builder().withConflictingUserId(tokenUsingUserId, tokenUsingContextId).build();
            }
            Databases.closeSQLStuff(rs, stmt);
            stmt = null;
            rs = null;

            List<String> prefixes = null;
            List<String> topics = null;
            boolean isAll = false;
            for (Iterator<String> iter = subscription.getTopics().iterator(); !isAll && iter.hasNext();) {
                String topic = iter.next();
                if (KnownTopic.ALL.getName().equals(topic)) {
                    isAll = true;
                } else {
                    try {
                        PushNotifications.validateTopicName(topic);
                    } catch (IllegalArgumentException e) {
                        throw PushExceptionCodes.INVALID_TOPIC.create(e, topic);
                    }
                    if (topic.endsWith(":*")) {
                        // Wild-card topic: we remove the *
                        if (null == prefixes) {
                            prefixes = new LinkedList<>();
                        }
                        prefixes.add(topic.substring(0, topic.length() - 1));
                    } else {
                        // Exact match
                        if (null == topics) {
                            topics = new LinkedList<>();
                        }
                        topics.add(topic);
                    }
                }
            }

            // Insert new or update existing subscription
            long lastModified = System.currentTimeMillis();
            byte[] id = UUIDs.toByteArray(UUID.randomUUID());
            stmt = con.prepareStatement(
                "INSERT INTO pns_subscription (id,cid,user,token,client,transport,all_flag,last_modified,expires) " +
                "VALUES (?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE transport=?, all_flag=?, last_modified=?, expires=?;"
            );
            stmt.setBytes(1, id);
            stmt.setInt(2, subscription.getContextId());
            stmt.setInt(3, subscription.getUserId());
            stmt.setString(4, subscription.getToken());
            stmt.setString(5, subscription.getClient());
            stmt.setString(6, subscription.getTransportId());
            stmt.setInt(7, isAll ? 1 : 0);
            stmt.setLong(8, lastModified);
            if (null == subscription.getExpires()) {
                stmt.setNull(9, Types.BIGINT);
                stmt.setNull(13, Types.BIGINT);
            } else {
                long expiration = subscription.getExpires().getTime();
                stmt.setLong(9, expiration);
                stmt.setLong(13, expiration);
            }
            stmt.setString(10, subscription.getTransportId());
            stmt.setInt(11, isAll ? 1 : 0);
            stmt.setLong(12, lastModified);
            int rowCount = stmt.executeUpdate();
            Databases.closeSQLStuff(stmt);
            stmt = null;

            boolean alreadyExists = (1 != rowCount);
            if (alreadyExists) {
                // Already exists, so take over existing subscription identifier
                id = getSubscriptionId(subscription.getUserId(), subscription.getContextId(), subscription.getToken(), subscription.getClient(), con);
                if (null == id) {
                    return PushSubscriptionResult.builder().withError(OXException.general("Token registration failed")).build();
                }
            }

            // Drop possibly existing entries
            if (alreadyExists) {
                stmt = con.prepareStatement("DELETE FROM pns_subscription_topic_exact WHERE id=?");
                stmt.setBytes(1, id);
                stmt.executeUpdate();
                Databases.closeSQLStuff(stmt);
                stmt = null;

                stmt = con.prepareStatement("DELETE FROM pns_subscription_topic_wildcard WHERE id=?");
                stmt.setBytes(1, id);
                stmt.executeUpdate();
                Databases.closeSQLStuff(stmt);
                stmt = null;
            }

            // Insert individual topics / topic wild-cards (if subscription is not interested in all)
            if (false == isAll) {
                if (null != prefixes) {
                    stmt = con.prepareStatement("INSERT IGNORE INTO pns_subscription_topic_wildcard (id, cid, topic) VALUES (?, ?, ?)");
                    stmt.setBytes(1, id);
                    stmt.setInt(2, subscription.getContextId());
                    for (String prefix : prefixes) {
                        stmt.setString(3, prefix);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    Databases.closeSQLStuff(stmt);
                    stmt = null;
                }

                if (null != topics) {
                    stmt = con.prepareStatement("INSERT IGNORE INTO pns_subscription_topic_exact (id, cid, topic) VALUES (?, ?, ?)");
                    stmt.setBytes(1, id);
                    stmt.setInt(2, subscription.getContextId());
                    for (String topic : topics) {
                        stmt.setString(3, topic);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    Databases.closeSQLStuff(stmt);
                    stmt = null;
                }
            }

            // Insert into in-memory collection as well
            RdbPushSubscriptionRegistryCache cache = this.cache;
            if (null != cache) {
                cache.addAndInvalidateIfPresent(subscription);
            }

            return PushSubscriptionResult.OK_RESULT;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    @Override
    public boolean unregisterSubscription(PushSubscription subscription) throws OXException {
        if (null == subscription) {
            return false;
        }

        return null != removeSubscription(subscription);
    }

    /**
     * Unregisters specified subscription.
     *
     * @param subscription The subscription to unregister
     * @throws OXException If unregistration fails
     */
    public PushSubscription removeSubscription(PushSubscription subscription) throws OXException {
        if (null == subscription) {
            return null;
        }

        int contextId = subscription.getContextId();
        Connection con = databaseService.getWritable(contextId);
        int rollback = 0;
        PushSubscription deleted = null;
        try {
            Databases.startTransaction(con);
            rollback = 1;

            deleted = removeSubscription(subscription, con);

            con.commit();
            rollback = 2;

            return deleted;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
            if (null != deleted) {
                databaseService.backWritable(contextId, con);
            } else {
                databaseService.backWritableAfterReading(contextId, con);
            }
        }
    }

    /**
     * Unregisters specified subscription.
     *
     * @param subscription The subscription to unregister
     * @param con The connection to use
     * @throws OXException If unregistration fails
     */
    public PushSubscription removeSubscription(PushSubscription subscription, Connection con) throws OXException {
        if (null == con) {
            return removeSubscription(subscription);
        }

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String client = subscription.getClient();
            if (null == client) {
                stmt = con.prepareStatement("SELECT id FROM pns_subscription WHERE cid=? AND user=? AND token=?");
                stmt.setInt(1, subscription.getContextId());
                stmt.setInt(2, subscription.getUserId());
                stmt.setString(3, subscription.getToken());
            } else {
                stmt = con.prepareStatement("SELECT id FROM pns_subscription WHERE cid=? AND user=? AND token=? AND client=?");
                stmt.setInt(1, subscription.getContextId());
                stmt.setInt(2, subscription.getUserId());
                stmt.setString(3, subscription.getToken());
                stmt.setString(4, client);
            }
            rs = stmt.executeQuery();
            if (false == rs.next()) {
                return null;
            }

            byte[] id = rs.getBytes(1);
            Databases.closeSQLStuff(rs, stmt);
            rs = null;
            stmt = null;

            stmt = con.prepareStatement("SELECT s.cid, s.user, s.token, s.client, s.transport, s.last_modified, s.expires, s.all_flag, twc.topic wildcard, te.topic FROM pns_subscription s LEFT JOIN pns_subscription_topic_wildcard twc ON s.id=twc.id LEFT JOIN pns_subscription_topic_exact te ON s.id=te.id WHERE s.id=?");
            stmt.setBytes(1, id);
            rs = stmt.executeQuery();

            // Select first row and build subscription instance
            rs.next();
            DefaultPushSubscription.Builder removedSubscription = DefaultPushSubscription.builder().contextId(rs.getInt(1)).userId(rs.getInt(2)).token(rs.getString(3)).client(rs.getString(4)).transportId(rs.getString(5));
            long expires = rs.getLong(7); // expires
            if (false == rs.wasNull()) {
                removedSubscription.expires(new Date(expires));
            }

            // Collect topics
            {
                List<String> topics = new LinkedList<>();
                if (rs.getInt(8) > 0) {
                    topics.add(KnownTopic.ALL.getName());
                }

                Set<String> setTopics = new LinkedHashSet<>();
                Set<String> setWildcards = new LinkedHashSet<>();
                do {
                    String wildcard = rs.getString(9); // wildcard
                    if (false == rs.wasNull()) {
                        setWildcards.add(wildcard);
                    }

                    String topic = rs.getString(10); // topic
                    if (false == rs.wasNull()) {
                        setTopics.add(topic);
                    }
                } while (rs.next());
                for (String wildcard : setWildcards) {
                    topics.add(wildcard + "*");
                }
                for (String topic : setTopics) {
                    topics.add(topic);
                }
                removedSubscription.topics(topics);
            }
            Databases.closeSQLStuff(rs, stmt);
            rs = null;
            stmt = null;

            boolean deleted = deleteById(id, null, con);
            if (deleted) {
                // Remove from in-memory collection as well
                RdbPushSubscriptionRegistryCache cache = this.cache;
                if (null != cache) {
                    cache.removeAndInvalidateIfPresent(subscription);
                }
            }

            return removedSubscription.build();
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    /**
     * Deletes the subscription identified by given ID.
     *
     * @param id The ID
     * @param cleanUpTask The optional clean-up task
     * @param con The connection to use
     * @return <code>true</code> if such a subscription was deleted; otherwise <code>false</code>
     * @throws OXException If delete attempt fails
     */
    public boolean deleteById(byte[] id, Runnable cleanUpTask, Connection con) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("DELETE FROM pns_subscription_topic_exact WHERE id=?");
            stmt.setBytes(1, id);
            stmt.executeUpdate();
            Databases.closeSQLStuff(stmt);

            stmt = con.prepareStatement("DELETE FROM pns_subscription_topic_wildcard WHERE id=?");
            stmt.setBytes(1, id);
            stmt.executeUpdate();
            Databases.closeSQLStuff(stmt);

            stmt = con.prepareStatement("DELETE FROM pns_subscription WHERE id=?");
            stmt.setBytes(1, id);
            int rows = stmt.executeUpdate();
            Databases.closeSQLStuff(stmt);
            stmt = null;

            if (null != cleanUpTask) {
                cleanUpTask.run();
            }

            return rows > 0;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw PushExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    @Override
    public int unregisterSubscription(String token, String transportId) throws OXException {
        if (null == token || null == transportId) {
            return 0;
        }

        int removed = 0;
        for (Integer iContextId : contextService.getDistinctContextsPerSchema()) {
            // Delete for whole schema using connection for representative context
            int contextId = iContextId.intValue();
            Connection con = databaseService.getWritable(contextId);
            int rollback = 0;
            boolean modified = false;
            try {
                List<byte[]> ids = getSubscriptionIds(contextId, token, transportId, con);

                if (false == ids.isEmpty()) {
                    Databases.startTransaction(con);
                    rollback = 1;

                    int numDeleted = deleteSubscription(ids, con);
                    modified = numDeleted > 0;
                    removed += numDeleted;

                    con.commit();
                    rollback = 2;
                }
            } catch (SQLException e) {
                throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
            } finally {
                if (rollback > 0) {
                    if (rollback == 1) {
                        Databases.rollback(con);
                    }
                    Databases.autocommit(con);
                }
                if (modified) {
                    databaseService.backWritable(contextId, con);
                } else {
                    databaseService.backWritableAfterReading(contextId, con);
                }
            }
        }
        return removed;
    }

    private List<byte[]> getSubscriptionIds(int contextId, String token, String transportId, Connection con) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT id FROM pns_subscription WHERE cid=? AND transport=? AND token=?");
            stmt.setInt(1, contextId);
            stmt.setString(2, transportId);
            stmt.setString(3, token);
            rs = stmt.executeQuery();
            if (false == rs.next()) {
                return Collections.emptyList();
            }

            List<byte[]> ids = new LinkedList<>();
            do {
                ids.add(rs.getBytes(1));
            } while (rs.next());
            return ids;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

    private int deleteSubscription(List<byte[]> ids, Connection con) throws OXException {
        int deleted = 0;
        for (byte[] id : ids) {
            if (deleteById(id, null, con)) {
                deleted++;
            }
        }

        // Clear cache
        RdbPushSubscriptionRegistryCache cache = this.cache;
        if (null != cache) {
            cache.clear(true);
        }

        return deleted;
    }

    @Override
    public boolean updateToken(PushSubscription subscription, String newToken) throws OXException {
        if (null == subscription || null == newToken) {
            return false;
        }

        int contextId = subscription.getContextId();
        Connection con = databaseService.getWritable(contextId);
        try {
            return updateToken(subscription, newToken, con);
        } finally {
            databaseService.backWritable(contextId, con);
        }
    }

    /**
     * Updates specified subscription's token (and last-modified time stamp).
     *
     * @param subscription The subscription to update
     * @param newToken The new token to set
     * @param con The connection to use
     * @return <code>true</code> if such a subscription has been updated; otherwise <code>false</code> if no such subscription existed
     * @throws OXException If update fails
     */
    public boolean updateToken(PushSubscription subscription, String newToken, Connection con) throws OXException {
        if (null == con) {
            return updateToken(subscription, newToken);
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("UPDATE pns_subscription SET token=?, last_modified=? WHERE cid=? AND user=? AND token=? AND client=?");
            stmt.setString(1, newToken);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setInt(3, subscription.getContextId());
            stmt.setInt(4, subscription.getUserId());
            stmt.setString(5, subscription.getToken());
            stmt.setString(6, subscription.getClient());
            int rows = stmt.executeUpdate();
            boolean updated = rows > 0;

            if (updated) {
                RdbPushSubscriptionRegistryCache cache = this.cache;
                if (null != cache) {
                    cache.dropFor(subscription.getUserId(), subscription.getContextId());
                }
            }

            return updated;
        } catch (SQLException e) {
            throw PushExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    // -----------------------------------------------------------------------------------------------------

    private static final class BuilderAndTopics {

        final DefaultPushSubscription.Builder builder;
        final List<String> topics;

        BuilderAndTopics(Builder builder, List<String> topics) {
            super();
            this.builder = builder;
            this.topics = topics;
        }
    }

}
