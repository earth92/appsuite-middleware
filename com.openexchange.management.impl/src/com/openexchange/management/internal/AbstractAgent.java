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

package com.openexchange.management.internal;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.monitor.GaugeMonitor;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.management.ManagementExceptionCode;
import com.openexchange.management.services.ManagementServiceRegistry;
import com.openexchange.password.mechanism.PasswordMech;
import com.openexchange.password.mechanism.PasswordMechRegistry;

/**
 * {@link AbstractAgent} - An abstract JMX agent
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class AbstractAgent {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractAgent.class);

    private static final class AbstractAgentSocketFactory implements RMIServerSocketFactory, Serializable {

        /**
         * serialVersionUID
         */
        private static final long serialVersionUID = 8324426326551371658L;

        private final int backlog;

        private final InetAddress bindAddress;

        public AbstractAgentSocketFactory(final int backlog, final String bindAddr) throws UnknownHostException {
            this.backlog = backlog < 1 ? 0 : backlog;
            bindAddress = bindAddr.charAt(0) == '*' ? null : InetAddress.getByName(bindAddr);
        }

        @Override
        public ServerSocket createServerSocket(final int port) throws IOException {
            return new ServerSocket(port, backlog, bindAddress);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }

            return obj.getClass().equals(getClass());
        }

        @Override
        public int hashCode() {
            return AbstractAgentSocketFactory.class.hashCode();
        }

    }

    protected static final class AbstractAgentJMXAuthenticator implements JMXAuthenticator {

        private final String[] credentials;

        public AbstractAgentJMXAuthenticator(final String[] credentials) {
            super();
            this.credentials = new String[credentials.length];
            System.arraycopy(credentials, 0, this.credentials, 0, credentials.length);
        }

        @Override
        public Subject authenticate(final Object lCredentials) {
            if (!(lCredentials instanceof String[])) {
                if (lCredentials == null) {
                    throw new SecurityException("Credentials required");
                }
                throw new SecurityException("Credentials should be String[]");
            }
            final String[] creds = (String[]) lCredentials;
            if (creds.length < 2) {
                throw new SecurityException("Credentials should at least have 2 elements");
            }
            /*
             * Perform authentication
             */
            final String username = creds[0];
            final String password = creds[1];

            ConfigurationService configurationService = ManagementServiceRegistry.getServiceRegistry().getService(ConfigurationService.class);
            String hashAlgorithm = configurationService.getProperty("JMXPasswordHashAlgorithm", "SHA");

            PasswordMechRegistry passwordMechRegistry = ManagementServiceRegistry.getServiceRegistry().getService(PasswordMechRegistry.class);
            PasswordMech passwordMech = passwordMechRegistry.get(hashAlgorithm);
            if (passwordMech == null) {
                throw new IllegalArgumentException("The identifier '" + hashAlgorithm + "' for the password hash mechanism is unknown.");
            }
            try {
                if ((this.credentials[0].equals(username)) && passwordMech.check(password, this.credentials[1], null)) {
                    return new Subject(true, Collections.singleton(new JMXPrincipal(username)), Collections.EMPTY_SET, Collections.EMPTY_SET);
                }
            } catch (OXException e) {
                //Fall through and send SecurityException
            }
            throw new SecurityException("Invalid credentials");
        }
    }

    protected final AtomicBoolean initialized;

    protected final Map<Integer, Registry> registries;

    protected final Map<JMXServiceURL, JMXConnectorServer> connectors;

    protected final ThreadMXBean threadMXBean;

    protected final AtomicReference<GaugeMonitor> gaugeMonitorRef;

    protected RMIServerSocketFactory rmiSocketFactory;

    protected MBeanServer mbs;

    /**
     * Initializes a new {@link AbstractAgent}
     */
    protected AbstractAgent() {
        super();
        gaugeMonitorRef = new AtomicReference<GaugeMonitor>();
        initialized = new AtomicBoolean();
        registries = new HashMap<Integer, Registry>();
        connectors = new HashMap<JMXServiceURL, JMXConnectorServer>();
        threadMXBean = ManagementFactory.getThreadMXBean();
        mbs = ManagementFactory.getPlatformMBeanServer();
    }

    /**
     * Gets the {@link ThreadMXBean} instance.
     *
     * @return The {@link ThreadMXBean} instance
     */
    public ThreadMXBean getThreadMXBean() {
        return threadMXBean;
    }

    /**
     * Uniquely identifies the MBean and registers it to MBeanServer
     *
     * @param name The MBeans's object name as a string
     * @param mbean The MBean object
     * @throws OXException If registering the MBean to MBeanServer fails
     */
    public void registerMBean(final String name, final Object mbean) throws OXException {
        try {
            registerMBean(new ObjectName(name), mbean);
        } catch (MalformedObjectNameException e) {
            throw ManagementExceptionCode.MALFORMED_OBJECT_NAME.create(e, name);
        }
    }

    /**
     * Unregister the MBean identified through specified object name as a string.
     *
     * @param name The MBean's object name as a string
     * @throws OXException If unregistering the MBean fails
     */
    public void unregisterMBean(final String name) throws OXException {
        try {
            unregisterMBean(new ObjectName(name));
        } catch (MalformedObjectNameException e) {
            throw ManagementExceptionCode.MALFORMED_OBJECT_NAME.create(e, name);
        }
    }

    /**
     * Uniquely identifies the MBean and registers it to MBeanServer
     *
     * @param objectName The MBean's object name
     * @param mbean The MBean object
     * @throws OXException If registering the MBean to MBeanServer fails
     */
    public void registerMBean(final ObjectName objectName, final Object mbean) throws OXException {
        if (mbs.isRegistered(objectName)) {
            LOG.warn("{} already registered", objectName.getCanonicalName());
            return;
        }
        try {
            mbs.registerMBean(mbean, objectName);
        } catch (InstanceAlreadyExistsException e) {
            throw ManagementExceptionCode.ALREADY_EXISTS.create(e, mbean.getClass().getName());
        } catch (MBeanRegistrationException e) {
            throw ManagementExceptionCode.MBEAN_REGISTRATION.create(e, mbean.getClass().getName());
        } catch (NotCompliantMBeanException e) {
            throw ManagementExceptionCode.NOT_COMPLIANT_MBEAN.create(e, mbean.getClass().getName());
        }
        LOG.debug("{} registered", objectName.getCanonicalName());
    }

    /**
     * Unregister the MBean identified through specified object name.
     *
     * @param objectName The MBean's object name
     * @throws OXException If unregistering the MBean fails
     */
    public void unregisterMBean(final ObjectName objectName) throws OXException {
        if (mbs.isRegistered(objectName)) {
            try {
                mbs.unregisterMBean(objectName);
            } catch (InstanceNotFoundException e) {
                throw ManagementExceptionCode.NOT_FOUND.create(e, objectName);
            } catch (MBeanRegistrationException e) {
                throw ManagementExceptionCode.MBEAN_REGISTRATION.create(e, objectName);
            }
            LOG.debug("{} unregistered", objectName.getCanonicalName());
        }
    }

    /**
     * Adds the MBean identified by given object name to {@link GaugeMonitor} for being observed
     *
     * @param objectName The MBean's object name
     * @param attributeName The observed attribute
     * @throws OXException If adding the MBean to {@link GaugeMonitor} fails
     */
    public final void addObservedMBean(final ObjectName objectName, final String attributeName) throws OXException {
        GaugeMonitor gm = gaugeMonitorRef.get();
        while (gm == null) {
            final GaugeMonitor newgm = new GaugeMonitor();
            newgm.setGranularityPeriod(5000);
            newgm.setDifferenceMode(false);
            newgm.setNotifyHigh(false);
            newgm.setNotifyLow(true);
            newgm.setThresholds(Integer.valueOf(50), Integer.valueOf(5));
            if (gaugeMonitorRef.compareAndSet(gm, newgm)) {
                /*
                 * No other thread initialized gauge monitor in the meantime; thus register and start it
                 */
                gm = gaugeMonitorRef.get();
                try {
                    mbs.registerMBean(gm, new ObjectName("Services:type=GaugeMonitor"));
                } catch (InstanceAlreadyExistsException e) {
                    throw ManagementExceptionCode.ALREADY_EXISTS.create(e, gm.getClass().getName());
                } catch (MBeanRegistrationException e) {
                    throw ManagementExceptionCode.MBEAN_REGISTRATION.create(e, gm.getClass().getName());
                } catch (NotCompliantMBeanException e) {
                    throw ManagementExceptionCode.NOT_COMPLIANT_MBEAN.create(e, gm.getClass().getName());
                } catch (MalformedObjectNameException e) {
                    throw ManagementExceptionCode.MALFORMED_OBJECT_NAME.create(e, "Services:type=GaugeMonitor");
                }
                gm.start();
            } else {
                gm = gaugeMonitorRef.get();
            }
        }
        gm.addObservedObject(objectName);
        gm.setObservedAttribute(attributeName);
    }

    /**
     * Removes the observed MBean identified by specified object name from the {@link GaugeMonitor}
     *
     * @param objectName The MBean's object name
     * @throws OXException If removing the MBean from {@link GaugeMonitor} fails
     */
    public final void removeObservedMBean(final ObjectName objectName) {
        final GaugeMonitor gm = gaugeMonitorRef.get();
        if (gm != null) {
            gm.removeObservedObject(objectName);
        }
    }

    /**
     * Adds a RMI registry
     *
     * @param port The port on which the RMI registry should listen on
     * @param bindAddr The bind address for created sockets by RMI registry
     * @throws OXException If RMI registry cannot be created
     */
    protected final void addRMIRegistry(final int port, final String bindAddr) throws OXException {
        try {
            if (initialized.compareAndSet(false, true)) {
                rmiSocketFactory = new AbstractAgentSocketFactory(0, bindAddr);
            }
            Registry registry = LocateRegistry.createRegistry(port, null, rmiSocketFactory);
            registries.put(Integer.valueOf(port), registry);
            LOG.info("RMI registry created on port {} and bind address {}", I(port), bindAddr);
        } catch (UnknownHostException e) {
            throw ManagementExceptionCode.UNKNOWN_HOST_ERROR.create(e, e.getMessage());
        } catch (RemoteException e) {
            throw ManagementExceptionCode.REMOTE_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Lists the RMI registries ports
     *
     * @return An array of <code>int</code> representing RMI ports
     */
    public final int[] getRMIRegistryPorts() {
        final int size = registries.size();
        final Iterator<Integer> iter = registries.keySet().iterator();
        final int[] portnumbers = new int[size];
        for (int i = 0; i < size; i++) {
            portnumbers[i] = iter.next().intValue();
        }
        return portnumbers;
    }

    /**
     * Creates a JMX connector server bound to specified JMX URL.
     *
     * @param urlstr The JMX URL as a string
     * @param jmxLogin The JMX login or <code>null</code> to use no authentication for connecting to specified JMX URL
     * @param jmxPassword The JMX password (only needed if previous parameter is not <code>null</code>)
     * @return The {@link JMXServiceURL} to which the connector is bound
     * @throws OXException If connector cannot be added
     */
    protected final JMXServiceURL addConnectorServer(final String urlstr, final String jmxLogin, final String jmxPassword) throws OXException {
        try {
            return addConnectorServer(new JMXServiceURL(urlstr), jmxLogin, jmxPassword);
        } catch (MalformedURLException e) {
            throw ManagementExceptionCode.MALFORMED_URL.create(e, urlstr);
        }
    }

    /**
     * Creates a JMX connector server bound to specified JMX URL.
     *
     * @param url The JMX URL
     * @param jmxLogin The JMX login or <code>null</code> to use no authentication for connecting to specified JMX URL
     * @param jmxPassword The JMX password (only needed if previous parameter is not <code>null</code>)
     * @return The {@link JMXServiceURL} to which the connector is bound
     * @throws OXException If connector cannot be added
     */
    protected final JMXServiceURL addConnectorServer(final JMXServiceURL url, final String jmxLogin, final String jmxPassword) throws OXException {
        if (connectors.containsKey(url)) {
            throw ManagementExceptionCode.JMX_URL_ALREADY_BOUND.create(url);
        }
        try {
            final Map<String, Object> environment;
            // environment.put(RMIConnectorServer.
            // RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, rmiSocketFactory);
            // environment.put(RMIConnectorServer.
            // RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, rmiSocketFactory);
            if (jmxLogin == null || jmxPassword == null) {
                environment = null;
            } else {
                environment = new HashMap<String, Object>(1);
                environment.put(JMXConnectorServer.AUTHENTICATOR, new AbstractAgentJMXAuthenticator(new String[] { jmxLogin, jmxPassword }));
            }
            final JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, environment, mbs);
            cs.start();
            connectors.put(url, cs);
            LOG.info("JMX connector server on {} started", url);
            return url;
        } catch (IOException e) {
            throw ManagementExceptionCode.IO_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Removes the JMX connector server bound to specified JMX URL
     *
     * @param url The JMX URL
     */
    protected final void removeConnectorServer(final JMXServiceURL url) {
        final JMXConnectorServer connector = connectors.remove(url);
        if (connector == null) {
            return;
        }
        try {
            connector.stop();
            LOG.info("JMX connector server on {} stopped", url);
        } catch (IOException e) {
            LOG.error("JMX connector server on {} could not be stopped", url, e);
            return;
        }
    }

    /**
     * Starts the agent
     */
    public abstract void run();

    /**
     * Shuts the agent down
     */
    public abstract void stop();

    /**
     * Gets a free port at the time of call to this method. The logic leverages the built in java.net.ServerSocket implementation which
     * binds a server socket to a free port when instantiated with a port <code> 0 </code>.
     * <P>
     * Note that this method guarantees the availability of the port only at the time of call. The method does not bind to this port.
     * <p>
     * Checking for free port can fail for several reasons which may indicate potential problems with the system. This method acknowledges
     * the fact and following is the general contract:
     * <li>Best effort is made to find a port which can be bound to. All the exceptional conditions in the due course are considered SEVERE.
     * <li>If any exceptional condition is experienced, <code> 0 </code> is returned, indicating that the method failed for some reasons and
     * the callers should take the corrective action. (The method need not always throw an exception for this).
     * <li>Method is synchronized on this class.
     *
     * @return integer depicting the free port number available at this time 0 otherwise.
     */
    protected static final int getFreePort() {
        int freePort = 0;
        boolean portFound = false;
        ServerSocket serverSocket = null;
        synchronized (AbstractAgent.class) {
            try {
                /*
                 * following call normally returns the free port, to which the ServerSocket is bound.
                 */
                serverSocket = new ServerSocket(0);
                freePort = serverSocket.getLocalPort();
                portFound = true;
            } catch (Exception e) {
                /*
                 * Squelch the exception
                 */
                LOG.error("", e);
            } finally {
                if (!portFound) {
                    freePort = 0;
                }
                try {
                    if (serverSocket != null) {
                        serverSocket.close();
                        if (!serverSocket.isClosed()) {
                            throw new Exception("local exception ...");
                        }
                    }
                } catch (Exception e) {
                    /*
                     * Squelch the exception
                     */
                    freePort = 0;
                }
            }
            return freePort;
        }
    }

}
