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

package com.openexchange.nosql.cassandra.impl;

import java.util.List;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.SchemaChangeListenerBase;
import com.openexchange.exception.OXException;
import com.openexchange.management.ManagementService;
import com.openexchange.nosql.cassandra.mbean.CassandraKeyspaceMBean;
import com.openexchange.nosql.cassandra.mbean.CassandraNodeMBean;
import com.openexchange.nosql.cassandra.mbean.impl.CassandraKeyspaceMBeanImpl;
import com.openexchange.server.ServiceLookup;

/**
 * {@link MBeanSchemaChangeListener}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class MBeanSchemaChangeListener extends SchemaChangeListenerBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MBeanSchemaChangeListener.class);

    private ServiceLookup services;

    /**
     * Initialises a new {@link MBeanSchemaChangeListener}.
     */
    public MBeanSchemaChangeListener(ServiceLookup services) {
        super();
        this.services = services;
    }

    @Override
    public void onKeyspaceAdded(KeyspaceMetadata keyspace) {
        registerMBean(keyspace);
    }

    @Override
    public void onKeyspaceRemoved(KeyspaceMetadata keyspace) {
        unregisterMBean(keyspace);
    }

    @Override
    public void onRegister(Cluster cluster) {
        // Register the keyspaces mbeans
        List<KeyspaceMetadata> keyspaces = cluster.getMetadata().getKeyspaces();
        for (KeyspaceMetadata keyspaceMetadata : keyspaces) {
            registerMBean(keyspaceMetadata);
        }
    }

    @Override
    public void onUnregister(Cluster cluster) {
        List<KeyspaceMetadata> keyspaces = cluster.getMetadata().getKeyspaces();
        for (KeyspaceMetadata keyspaceMetadata : keyspaces) {
            unregisterMBean(keyspaceMetadata);
        }
    }

    /////////////////////////////////////////// HELEPRS //////////////////////////////////////////

    /**
     * Registers a new {@link CassandraKeyspaceMBean} for the specified Cassandra keyspace
     * 
     * @param keyspaceMetadata The {@link KeyspaceMetadata}
     * @throws MalformedObjectNameException if the {@link ObjectName} does not have the right format.
     * @throws NotCompliantMBeanException If the <code>mbeanInterface</code> does not follow JMX design
     *             patterns for Management Interfaces, or if <code>this</code> does not implement the
     *             specified interface
     * @throws OXException if the {@link ManagementService} is absent
     */
    private void registerMBean(KeyspaceMetadata keyspaceMetadata) {
        try {
            ObjectName objectName = createObjectName(keyspaceMetadata);
            CassandraKeyspaceMBean mbean = new CassandraKeyspaceMBeanImpl(services, keyspaceMetadata.getName());

            ManagementService managementService = services.getService(ManagementService.class);
            managementService.registerMBean(objectName, mbean);
            LOGGER.info("Registered MBean for keyspace '{}'", keyspaceMetadata.getName());
        } catch (NotCompliantMBeanException | MalformedObjectNameException | OXException e) {
            LOGGER.error("Error registering MBean for keyspace '{}'", keyspaceMetadata.getName(), e);
        }
    }

    /**
     * Unregisters the {@link CassandraNodeMBean} for the specified Cassandra {@link Host}
     * 
     * @param host The Cassandra {@link Host}
     * @throws MalformedObjectNameException if the {@link ObjectName} does not have the right format.
     * @throws OXException if the {@link ManagementService} is absent
     */
    private void unregisterMBean(KeyspaceMetadata keyspaceMetadata) {
        try {
            ManagementService managementService = services.getService(ManagementService.class);
            ObjectName objectName = createObjectName(keyspaceMetadata);
            managementService.unregisterMBean(objectName);
            LOGGER.info("Unregistered MBean for keyspace '{}'", keyspaceMetadata.getName());
        } catch (MalformedObjectNameException | OXException e) {
            LOGGER.error("Error unregistering MBean for keyspace '{}'", keyspaceMetadata.getName(), e);
        }
    }

    /**
     * Creates a new {@link ObjectName} for the specified Cassandra keyspace. The created
     * {@link ObjectName} has the format:
     * 
     * <code>com.openexchange.nosql.cassandra:00=Keyspace Monitoring Tool,name=KEYSPACE_NAME</code>
     * 
     * @param keyspaceMetadata The {@link KeyspaceMetadata}
     * @return The new {@link ObjectName}
     * @throws MalformedObjectNameException if the {@link ObjectName} does not have the right format.
     */
    private ObjectName createObjectName(KeyspaceMetadata keyspaceMetadata) throws MalformedObjectNameException {
        StringBuilder sb = new StringBuilder(CassandraKeyspaceMBean.DOMAIN);
        // Append the mbean name
        sb.append(":00=").append(CassandraKeyspaceMBean.NAME);
        // Append the keyspace name
        sb.append(",name=").append(keyspaceMetadata.getName());
        return new ObjectName(sb.toString());
    }
}
