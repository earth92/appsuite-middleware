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

import static com.openexchange.java.Autoboxing.I;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.openexchange.config.lean.Property;
import com.openexchange.nosql.cassandra.CassandraService;

/**
 * {@link CassandraProperty}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public enum CassandraProperty implements Property {
    /**
     * Defines the name of the Cassandra cluster. Technically this name does not correlate
     * with the name configured in the real Cassandra cluster, but it's rather used to distinguish
     * exposed JMX metrics when multiple Cluster instances live in the same JVM
     */
    clusterName("ox"),
    /**
     * Defines the Cassandra seed node(s) as a comma separated list
     */
    clusterContactPoints("127.0.0.1"),
    /**
     * Defines the port on which the Cassandra server is running.
     * <p/>
     * Defaults to <code>9042</code>
     */
    port(I(9042)),
    /**
     * Defines load balancing policy to use for the cluster. There are three
     * load balancing policies to choose from:
     * <ul>
     * <li>{@link CassandraLoadBalancingPolicy#RoundRobin}</li>
     * <li>{@link CassandraLoadBalancingPolicy#DCAwareRoundRobin}</li>
     * <li>{@link CassandraLoadBalancingPolicy#DCTokenAwareRoundRobin}</li>
     * </ul>
     * <p/>
     * Defaults to {@link CassandraLoadBalancingPolicy#RoundRobin}
     */
    loadBalancingPolicy(CassandraLoadBalancingPolicy.RoundRobin.name()),
    /**
     * A policy that defines a default behaviour to adopt when a request fails. There are three
     * retry policies to choose from:
     * <ul>
     * <li>{@link CassandraRetryPolicy#defaultRetryPolicy}</li>
     * <li>{@link CassandraRetryPolicy#downgradingConsistencyRetryPolicy}</li>
     * <li>{@link CassandraRetryPolicy#fallthroughRetryPolicy}</li>
     * </ul>
     * <p/>
     *
     * Defaults to {@link CassandraRetryPolicy#defaultRetryPolicy}
     */
    retryPolicy(CassandraRetryPolicy.defaultRetryPolicy.name()),
    /**
     * Logs the retry decision of the policy.
     * <p/>
     * Defaults to <code>false</code>
     */
    logRetryPolicy(Boolean.FALSE),
    /**
     * Enables the query logger which logs all executed statements
     * <p/>
     * Defatuls to <code>false</code>
     */
    enableQueryLogger(Boolean.FALSE),
    /**
     * Defines the latency threshold in milliseconds beyond which queries are considered 'slow'
     * and logged as such by the Cassandra service. Used in conjunction with the 'enableQueryLogger'
     * property.
     * <p/>
     * Defaults to <code>5000</code> msec.
     */
    queryLatencyThreshold(I(5000)),
    /**
     * Defines the amount of time (in seconds) for connection keepalive in the form of a heartbeat.
     * When a connection has been idle for the given amount of time, the Cassandra service will
     * simulate activity by writing a dummy request to it (by sending an <code>OPTIONS</code> message).
     * <p/>
     * To disable heartbeat, set the interval to 0.
     * <p/>
     * Defaults to 30 seconds
     */
    poolingHeartbeat(I(30)),
    /**
     * The Cassandra service's connection pools have a variable size, which gets adjusted automatically
     * depending on the current load. There will always be at least a minimum number of connections, and
     * at most a maximum number. These values can be configured independently by host distance (the distance
     * is determined by your LoadBalancingPolicy, and will generally indicate whether a host is in the same
     * datacenter or not).
     * <p/>
     * Defaults to minimum 4 and maximum 10 for local nodes (i.e. in the same datacenter) and minimum 2 and
     * maximum 4 for remote nodes
     */
    minimumLocalConnectionsPerNode(I(4)),
    maximumLocalConnectionsPerNode(I(10)),
    minimumRemoteConnectionsPerNode(I(2)),
    maximumRemoteConnectionsPerNode(I(4)),
    /**
     * When activity goes down, the driver will "trash" connections if the maximum number of requests
     * in a 10 second time period can be satisfied by less than the number of connections opened. Trashed
     * connections are kept open but do not accept new requests. After the given timeout, trashed connections
     * are closed and removed. If during that idle period activity increases again, those connections will be
     * resurrected back into the active pool and reused.
     * <p/>
     * Defaults to 120 seconds
     */
    idleConnectionTrashTimeout(I(120)),
    /**
     * Defines the throttling of concurrent requests per connection on local (on the same datacenter)
     * and remote nodes (on a different datacenter).
     * <p/>
     * For Cassandra clusters that use a protocol v2 and below, there is no reason to throttle.
     * It should be set to 128 (the max)
     * <p/>
     * For Cassandra clusters that use a protocol v3 and up, it is set by default to 1024 for LOCAL hosts,
     * and to 256 for REMOTE hosts. These low defaults were chosen so that the default configuration
     * for protocol v2 and v3 allow the same total number of simultaneous requests (to avoid bad surprises
     * when clients migrate from v2 to v3). This threshold can be raised, or even set it to the max which is
     * 32768 for LOCAL nodes and 2000 for REMOTE nodes
     * <p/>
     * Note that that high values will give clients more bandwidth and therefore put more pressure on
     * the cluster. This might require some tuning, especially with many clients.
     */
    maximumRequestsPerLocalConnection(I(1024)),
    maximumRequestsPerRemoteConnection(I(256)),
    /**
     * When the {@link CassandraService} tries to send a request to a host, it will first try to acquire
     * a connection from this host's pool. If the pool is busy (i.e. all connections are already handling
     * their maximum number of in flight requests), the acquisition attempt gets enqueued until a
     * connection becomes available again.
     * <p/>
     * The size of that queue is controlled by {@link PoolingOptions#setMaxQueueSize}. If the queue has
     * already reached its limit, further attempts to acquire a connection will be rejected immediately:
     * the {@link CassandraService} will move on and try to acquire a connection from the next host's
     * pool. The limit can be set to 0 to disable queueing entirely.
     * <p/>
     * If all hosts are busy with a full queue, the request will fail with a {@link NoHostAvailableException}.
     */
    acquisitionQueueMaxSize(I(256)),
    /**
     * The connection timeout in milliseconds.
     */
    connectTimeout(I(SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS)),
    /**
     * The read timeout in milliseconds.
     */
    readTimeout(I(SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS)),
    ;

    private final Object defaultValue;

    private static final String PREFIX = "com.openexchange.nosql.cassandra.";

    /**
     * Initializes a new {@link CassandraProperty}.
     */
    private CassandraProperty(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Returns the fully qualified name for the property
     *
     * @return the fully qualified name for the property
     */
    @Override
    public String getFQPropertyName() {
        return PREFIX + name();
    }

    /**
     * Returns the default value of this property
     *
     * @return the default value of this property
     */
    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }
}
