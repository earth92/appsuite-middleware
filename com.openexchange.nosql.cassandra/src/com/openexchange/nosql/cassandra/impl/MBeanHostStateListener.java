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

import java.util.HashSet;
import java.util.Set;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Host.StateListener;
import com.openexchange.exception.OXException;
import com.openexchange.management.ManagementService;
import com.openexchange.nosql.cassandra.mbean.CassandraNodeMBean;
import com.openexchange.nosql.cassandra.mbean.impl.CassandraNodeMBeanImpl;
import com.openexchange.server.ServiceLookup;

/**
 * {@link MBeanHostStateListener}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class MBeanHostStateListener implements StateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MBeanHostStateListener.class);

    private ServiceLookup services;
    private Set<Host> hosts;

    /**
     * Initialises a new {@link MBeanHostStateListener}.
     */
    public MBeanHostStateListener(ServiceLookup services) {
        super();
        this.services = services;
        hosts = new HashSet<>();
    }

    @Override
    public void onAdd(Host host) {
        String hostAddress = host.getAddress().getHostAddress();
        if (hosts.contains(host)) {
            LOGGER.debug("The Cassandra node '{}' is already registered with this OX node", hostAddress);
            return;
        }
        try {
            registerMBean(host);
            hosts.add(host);
            LOGGER.info("Registered MBean for Cassandra node '{}", hostAddress);
        } catch (MalformedObjectNameException | NotCompliantMBeanException | OXException e) {
            LOGGER.error("Error registering MBean for Cassandra node '{}'", hostAddress, e);
        }
    }

    @Override
    public void onUp(Host host) {
        //nothing yet
    }

    @Override
    public void onDown(Host host) {
        //nothing yet
    }

    @Override
    public void onRemove(Host host) {
        String hostAddress = host.getAddress().getHostAddress();
        if (!hosts.contains(host)) {
            LOGGER.debug("The Cassandra node '{}' was already unregistered from this OX node", hostAddress);
            return;
        }
        try {
            unregisterMBean(host);
            hosts.remove(host);
            LOGGER.info("Unregistered MBean for Cassandra node '{}", hostAddress);
        } catch (MalformedObjectNameException | OXException e) {
            LOGGER.error("Error unregistering MBean for Cassandra node '{}'", hostAddress, e);
        }
    }

    @Override
    public void onRegister(Cluster cluster) {
        //nothing yet
    }

    @Override
    public void onUnregister(Cluster cluster) {
        for (Host host : hosts) {
            String hostAddress = host.getAddress().getHostAddress();
            try {
                unregisterMBean(host);
                LOGGER.info("Unregistered MBean for Cassandra node '{}", hostAddress);
            } catch (MalformedObjectNameException | OXException e) {
                LOGGER.error("Error unregistering MBean for Cassandra node '{}'", hostAddress, e);
            }
        }
        hosts.clear();
    }

    /////////////////////////////////////////// HELPERS //////////////////////////////////////////

    /**
     * Registers a new {@link CassandraNodeMBean} for the specified Cassandra {@link Host}
     * 
     * @param host The Cassandra {@link Host}
     * @throws MalformedObjectNameException if the {@link ObjectName} does not have the right format.
     * @throws NotCompliantMBeanException If the <code>mbeanInterface</code> does not follow JMX design patterns for Management Interfaces, or if <code>this</code> does not implement the specified interface
     * @throws OXException if the {@link ManagementService} is absent
     */
    private void registerMBean(Host host) throws MalformedObjectNameException, NotCompliantMBeanException, OXException {
        ObjectName objectName = createObjectName(host);
        CassandraNodeMBean mbean = new CassandraNodeMBeanImpl(services, host);

        ManagementService managementService = services.getService(ManagementService.class);
        managementService.registerMBean(objectName, mbean);
    }

    /**
     * Unregisters the {@link CassandraNodeMBean} for the specified Cassandra {@link Host}
     * 
     * @param host The Cassandra {@link Host}
     * @throws MalformedObjectNameException if the {@link ObjectName} does not have the right format.
     * @throws OXException if the {@link ManagementService} is absent
     */
    private void unregisterMBean(Host host) throws MalformedObjectNameException, OXException {
        ManagementService managementService = services.getService(ManagementService.class);
        ObjectName objectName = createObjectName(host);
        managementService.unregisterMBean(objectName);
    }

    /**
     * Creates a new {@link ObjectName} for the specified Cassandra {@link Host}. The created
     * {@link ObjectName} has the format:
     * 
     * <code>com.openexchange.nosql.cassandra:00=Cassandra Node Monitoring Tool,01=DATACENTER,02=RACK,name=HOSTNAME</code>
     * 
     * @param host The {@link Host}
     * @return The new {@link ObjectName}
     * @throws MalformedObjectNameException if the {@link ObjectName} does not have the right format.
     */
    private ObjectName createObjectName(Host host) throws MalformedObjectNameException {
        StringBuilder sb = new StringBuilder(CassandraNodeMBean.DOMAIN);
        sb.append(":00=").append(CassandraNodeMBean.NAME);
        // Append datacenter
        sb.append(",01=").append(host.getDatacenter());
        // Append rack
        sb.append(",02=").append(host.getRack());
        // Append hostname
        sb.append(",name=").append(host.getAddress().getHostAddress());
        return new ObjectName(sb.toString());
    }
}
