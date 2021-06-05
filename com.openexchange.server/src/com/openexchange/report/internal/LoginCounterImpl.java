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

package com.openexchange.report.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.management.MBeanException;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.report.LoginCounterService;
import com.openexchange.server.services.ServerServiceRegistry;

/**
 * {@link LoginCounterImpl}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:vitali.sjablow@open-xchange.com">Vitali Sjablow</a>
 */
public class LoginCounterImpl implements LoginCounterService {

    private final org.slf4j.Logger logger;

    /**
     * Initializes a new {@link LoginCounterImpl}.
     */
    public LoginCounterImpl() {
        super();
        logger = org.slf4j.LoggerFactory.getLogger(ReportingMBean.class);
    }

    @Override
    public List<Object[]> getLastLoginTimeStamp(final int userId, final int contextId, final String client) throws OXException {
        final DatabaseService service = ServerServiceRegistry.getInstance().getService(DatabaseService.class);
        if (null == service) {
            throw new OXException(-1, "DatabaseService not available at the moment. Try again later.");
        }
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            con = service.getReadOnly(contextId);
            if ("*".equals(client.trim())) {
                stmt = con.prepareStatement("SELECT value, name FROM user_attribute WHERE cid=? AND id=? AND name LIKE 'client:%'");
                stmt.setInt(1, contextId);
                stmt.setInt(2, userId);
                rs = stmt.executeQuery();
                final List<Object[]> ret = new LinkedList<>();
                while (rs.next()) {
                    final String name = rs.getString(2);
                    ret.add(new Object[] { new Date(Long.parseLong(rs.getString(1))), name.substring(7) });
                }
                return ret;
            }
            // Query for single client identifier
            stmt = con.prepareStatement("SELECT value FROM user_attribute WHERE cid=? AND id=? AND name=?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            stmt.setString(3, "client:" + client);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new MBeanException(null, "No such entry found (user="+userId+", context="+contextId+", client=\""+client+"\").");
            }
            return Collections.singletonList(new Object[] { new Date(Long.parseLong(rs.getString(1))), client });
        } catch (Exception e) {
            logger.error("", e);
            throw new OXException(-1, e.getMessage(), e);
        } finally {
            closeSQLStuff(rs, stmt);
            if (null != con) {
                service.backReadOnly(contextId, con);
            }
        }
    }

    @Override
    public Map<String, Integer> getNumberOfLogins(final Date startDate, final Date endDate, boolean aggregate, String regex) throws OXException {
        if (startDate == null) {
            throw new OXException(new IllegalArgumentException("Parameter 'startDate' must not be null!"));
        }

        if (endDate == null) {
            throw new OXException(new IllegalArgumentException("Parameter 'endDate' must not be null!"));
        }

        final DatabaseService dbService = ServerServiceRegistry.getInstance().getService(DatabaseService.class);
        Map<String, Integer> schemaMap = null;
        try {
            schemaMap = Tools.getAllSchemata(logger);
        } catch (SQLException e) {
            logger.error("", e);
            final Exception wrapMe = new Exception(e.getMessage());
            throw new OXException(wrapMe);
        }

        /*
         * Get all logins of every schema
         */
        int sum = 0;
        final Map<String, Integer> results = new HashMap<>();
        for (final Entry<String, Integer> schemaEntry : schemaMap.entrySet()) {
            final Set<UserContextId> countedUsers = new HashSet<>();
            final int readPool = schemaEntry.getValue().intValue();
            final Connection connection = dbService.get(readPool, schemaEntry.getKey());
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                StringBuilder sb = new StringBuilder("SELECT ua.cid, ua.id, ua.name, ua.value FROM user_attribute ua JOIN user u on ua.id = u.id AND ua.cid = u.cid WHERE ua.name REGEXP ? AND u.guestCreatedBy = 0");
                if (regex == null) {
                    regex = ".*";
                }
                stmt = connection.prepareStatement(sb.toString());
                stmt.setString(1, "client:(" + regex + ")");
                rs = stmt.executeQuery();
                while (rs.next()) {
                    final int contextId = rs.getInt(1);
                    final int userId = rs.getInt(2);
                    final String client = rs.getString(3);
                    try {
                        Date lastLogin = new Date(Long.parseLong(rs.getString(4)));
                        if (lastLogin.after(startDate) && lastLogin.before(endDate)) {
                            if (aggregate) {
                                UserContextId userContextId = new UserContextId(contextId, userId);
                                if (!countedUsers.contains(userContextId)) {
                                    countedUsers.add(userContextId);
                                    ++sum;
                                }
                            } else {
                                ++sum;
                            }

                            Integer value = results.get(client);
                            if (value == null) {
                                results.put(client, 1);
                            } else {
                                results.put(client, value.intValue() + 1);
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Client value is not a number.", e);
                    }
                }
            } catch (SQLException e) {
                logger.error("", e);
                throw new OXException(e);
            } finally {
                Databases.closeSQLStuff(rs, stmt);
                dbService.back(readPool, connection);
            }
        }

        results.put(SUM, sum);
        return results;
    }

    /**
     * Closes the ResultSet.
     *
     * @param result <code>null</code> or a ResultSet to close.
     */
    private static void closeSQLStuff(final ResultSet result) {
        if (result != null) {
            try {
                result.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Closes the {@link Statement}.
     *
     * @param stmt <code>null</code> or a {@link Statement} to close.
     */
    private static void closeSQLStuff(final Statement stmt) {
        if (null != stmt) {
            try {
                stmt.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Closes the ResultSet and the Statement.
     *
     * @param result <code>null</code> or a ResultSet to close.
     * @param stmt <code>null</code> or a Statement to close.
     */
    private static void closeSQLStuff(final ResultSet result, final Statement stmt) {
        closeSQLStuff(result);
        closeSQLStuff(stmt);
    }

    private static final class UserContextId {

        private final int contextId;
        private final int userId;
        private final int hash;

        /**
         * Initializes a new {@link UserContextId}.
         * @param contextId
         * @param userId
         */
        public UserContextId(int contextId, int userId) {
            super();
            this.contextId = contextId;
            this.userId = userId;
            final int prime = 31;
            int result = 1;
            result = prime * result + contextId;
            result = prime * result + userId;
            hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UserContextId other = (UserContextId) obj;
            if (contextId != other.contextId) {
                return false;
            }
            if (userId != other.userId) {
                return false;
            }
            return true;
        }
    }

    @Override
    public Map<String, Long> getLastClientLogIns(int userId, int contextId, Date startDate, Date endDate) throws OXException {
        final DatabaseService service = ServerServiceRegistry.getInstance().getService(DatabaseService.class);
        
        if (null == service) {
            throw new OXException(-1, "DatabaseService not available at the moment. Try again later.");
        }
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            con = service.getReadOnly(contextId);
            stmt = con.prepareStatement("SELECT value, name FROM user_attribute WHERE cid=? AND id=? AND name LIKE 'client:%'");
            stmt.setInt(1, contextId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            return parseResult(startDate, endDate, rs);
        } catch (Exception e) {
            logger.error("", e);
            throw new OXException(-1, e.getMessage(), e);
        } finally {
            closeSQLStuff(rs, stmt);
            if (null != con) {
                service.backReadOnly(contextId, con);
            }
        }
    }

    private Map<String, Long> parseResult(Date startDate, Date endDate, ResultSet rs) throws SQLException {
        final Map<String, Long> returnMap = new HashMap<>();
        while (rs.next()) {
            try {
                final String name = rs.getString(2);
                final Long date = rs.getLong(1);
                Date lastLogin = new Date(Long.parseLong(rs.getString(1)));
                if (lastLogin.after(startDate) && lastLogin.before(endDate)) {
                    returnMap.put(name, date);
                }
            } catch (NumberFormatException e) {
                logger.warn("Client value is not a number.", e);
            }
        }
        return returnMap;
    }

}
