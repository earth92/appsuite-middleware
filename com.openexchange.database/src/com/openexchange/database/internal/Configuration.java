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

package com.openexchange.database.internal;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import com.openexchange.config.ConfigurationService;
import com.openexchange.database.DBPoolingExceptionCodes;
import com.openexchange.exception.OXException;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.Strings;
import com.openexchange.pooling.ExhaustedActions;
import com.openexchange.pooling.PoolConfig;

/**
 * Contains the settings to connect to the configuration database as well as generic JDBC properties to use.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class Configuration {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Configuration.class);

    private static final String CONFIG_DB_FILENAME = "configdb.properties";

    private final AtomicReference<Properties> propsReference = new AtomicReference<Properties>();

    private static final String JDBC_CONFIG = "dbconnector.yaml";

    private final AtomicReference<Properties> jdbcPropsReference = new AtomicReference<Properties>();

    private final Properties configDbReadProps = new Properties();

    private final Properties configDbWriteProps = new Properties();

    private final AtomicReference<PoolConfig> poolConfigReference = new AtomicReference<PoolConfig>(PoolConfig.DEFAULT_CONFIG);

    public Configuration() {
        super();
    }

    String getReadUrl() {
        return getProperty(Property.READ_URL);
    }

    Properties getConfigDbReadProps() {
        return configDbReadProps;
    }

    String getWriteUrl() {
        return getProperty(Property.WRITE_URL);
    }

    Properties getConfigDbWriteProps() {
        return configDbWriteProps;
    }

    /**
     * Gets the JDBC properties this configuration is using
     *
     * @return The JDBC properties
     */
    public Properties getJdbcProps() {
        return jdbcPropsReference.get();
    }

    private String getProperty(final Property property) {
        return getProperty(property, null);
    }

    private interface Convert<T> {

        T convert(String toConvert);
    }

    private <T> T getUniversal(final Property property, final T def, final Convert<T> converter) {
        final T retval;
        Properties props = propsReference.get();
        if (props != null && props.containsKey(property.getPropertyName())) {
            retval = converter.convert(props.getProperty(property.getPropertyName()));
        } else {
            retval = def;
        }
        return retval;
    }

    String getProperty(final Property property, final String def) {
        return getUniversal(property, def, new Convert<String>() {

            @Override
            public String convert(final String toConvert) {
                return toConvert;
            }
        });
    }

    int getInt(final Property property, final int def) {
        return getUniversal(property, Integer.valueOf(def), new Convert<Integer>() {

            @Override
            public Integer convert(final String toConvert) {
                return Integer.valueOf(toConvert);
            }
        }).intValue();
    }

    long getLong(final Property property, final long def) {
        return getUniversal(property, Long.valueOf(def), new Convert<Long>() {

            @Override
            public Long convert(final String toConvert) {
                return Long.valueOf(toConvert);
            }
        }).longValue();
    }

    boolean getBoolean(final Property property, final boolean def) {
        return getUniversal(property, Boolean.valueOf(def), new Convert<Boolean>() {

            @Override
            public Boolean convert(final String toConvert) {
                return Boolean.valueOf(toConvert);
            }
        }).booleanValue();
    }

    public void readConfiguration(final ConfigurationService service) throws OXException {
        readConfiguration(service, true);
    }

    public void readConfiguration(final ConfigurationService service, final boolean logPoolConfig) throws OXException {
        Properties props = propsReference.get();
        if (null != props) {
            throw DBPoolingExceptionCodes.ALREADY_INITIALIZED.create(this.getClass().getName());
        }
        props = service.getFile(CONFIG_DB_FILENAME);
        if (props.isEmpty()) {
            throw DBPoolingExceptionCodes.MISSING_CONFIGURATION.create();
        }
        if (!props.containsKey(Property.REPLICATION_MONITOR.getPropertyName())) {
            boolean replicationMonitor = service.getBoolProperty(Property.REPLICATION_MONITOR.getPropertyName(), true);
            props.put(Property.REPLICATION_MONITOR.getPropertyName(), replicationMonitor ? "true" : "false");
        }
        if (!props.containsKey(Property.CHECK_WRITE_CONS.getPropertyName())) {
            boolean alwaysCheckOnActivate = service.getBoolProperty(Property.CHECK_WRITE_CONS.getPropertyName(), false);
            props.put(Property.CHECK_WRITE_CONS.getPropertyName(), alwaysCheckOnActivate ? "true" : "false");
        }
        if (!props.containsKey(Property.ALWAYS_CHECK_ON_ACTIVATE.getPropertyName())) {
            boolean alwaysCheckOnActivate = service.getBoolProperty(Property.ALWAYS_CHECK_ON_ACTIVATE.getPropertyName(), false);
            props.put(Property.ALWAYS_CHECK_ON_ACTIVATE.getPropertyName(), alwaysCheckOnActivate ? "true" : "false");
        }
        propsReference.set(props);
        readJdbcProps(service);
        separateReadWrite();
        loadDrivers();
        initPoolConfig(logPoolConfig);
    }

    private void readJdbcProps(ConfigurationService config) {
        Properties jdbcProps = new Properties();

        // Set defaults:
        jdbcProps.setProperty("useUnicode", "true");
        jdbcProps.setProperty("characterEncoding", "UTF-8");
        jdbcProps.setProperty("autoReconnect", "false");
        jdbcProps.setProperty("useServerPrepStmts", "false");
        jdbcProps.setProperty("useTimezone", "true");
        jdbcProps.setProperty("serverTimezone", "UTC");
        jdbcProps.setProperty("connectTimeout", "15000");
        jdbcProps.setProperty("socketTimeout", "15000");
        jdbcProps.setProperty("useSSL", "false");

        // Apply config
        jdbcProps.putAll(parseJdbcYaml(config));
        jdbcPropsReference.set(jdbcProps);
    }

    private Map<String, String> parseJdbcYaml(ConfigurationService config) {
        Object yaml = config.getYaml(JDBC_CONFIG);
        if (yaml == null) {
            return Collections.emptyMap();
        }

        if (!Map.class.isInstance(yaml)) {
            LOG.error("Can't parse connector configuration file: {}", JDBC_CONFIG);
            return Collections.emptyMap();
        }

        Map<String, Object> jdbcConfig = (Map<String, Object>) yaml;
        Object obj = jdbcConfig.get("com.mysql.jdbc");
        if (!Map.class.isInstance(obj)) {
            LOG.error("Can't parse connector configuration file: {}", JDBC_CONFIG);
            return Collections.emptyMap();
        }

        // Enforce Strings
        Map<String, Object> map = (Map<String, Object>) obj;
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }

    private void separateReadWrite() {
        Properties jdbcProps = jdbcPropsReference.get();
        configDbReadProps.putAll(jdbcProps);
        configDbWriteProps.putAll(jdbcProps);
        Properties props = propsReference.get();
        for (final Object tmp : props.keySet()) {
            final String key = (String) tmp;
            if (key.startsWith("readProperty.")) {
                final String value = props.getProperty(key);
                final int equalSignPos = value.indexOf('=');
                final String readKey = value.substring(0, equalSignPos);
                final String readValue = value.substring(equalSignPos + 1);
                configDbReadProps.put(readKey, readValue);
            } else if (key.startsWith("writeProperty.")) {
                final String value = props.getProperty(key);
                final int equalSignPos = value.indexOf('=');
                final String readKey = value.substring(0, equalSignPos);
                final String readValue = value.substring(equalSignPos + 1);
                configDbWriteProps.put(readKey, readValue);
            }
        }
    }

    private void loadDrivers() throws OXException {
        final String readDriverClass = getProperty(Property.READ_DRIVER_CLASS);
        if (null == readDriverClass) {
            throw DBPoolingExceptionCodes.PROPERTY_MISSING.create(Property.READ_DRIVER_CLASS.getPropertyName());
        }
        try {
            Class.forName(readDriverClass);
        } catch (ClassNotFoundException e) {
            throw DBPoolingExceptionCodes.NO_DRIVER.create(e, readDriverClass);
        }
        final String writeDriverClass = getProperty(Property.WRITE_DRIVER_CLASS);
        if (null == writeDriverClass) {
            throw DBPoolingExceptionCodes.PROPERTY_MISSING.create(Property.WRITE_DRIVER_CLASS.getPropertyName());
        }
        try {
            Class.forName(writeDriverClass);
        } catch (ClassNotFoundException e) {
            throw DBPoolingExceptionCodes.NO_DRIVER.create(e, writeDriverClass);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        propsReference.set(null);
        jdbcPropsReference.set(null);
        configDbReadProps.clear();
        configDbWriteProps.clear();
    }

    /**
     * Reads the pooling configuration from the configdb.properties file.
     */
    private void initPoolConfig(boolean logPoolConfig) {
        PoolConfig poolConfig = poolConfigReference.get();
        PoolConfig.Builder poolConfigBuilder = PoolConfig.builder();
        poolConfigBuilder.withMaxIdle(getInt(Property.MAX_IDLE, poolConfig.maxIdle));
        poolConfigBuilder.withMaxIdleTime(getLong(Property.MAX_IDLE_TIME, poolConfig.maxIdleTime));
        poolConfigBuilder.withMaxActive(getInt(Property.MAX_ACTIVE, poolConfig.maxActive));
        poolConfigBuilder.withMaxWait(getLong(Property.MAX_WAIT, poolConfig.maxWait));
        poolConfigBuilder.withMaxLifeTime(getLong(Property.MAX_LIFE_TIME, poolConfig.maxLifeTime));
        poolConfigBuilder.withExhaustedAction(ExhaustedActions.valueOf(getProperty(Property.EXHAUSTED_ACTION, poolConfig.exhaustedAction.name())));
        poolConfigBuilder.withTestOnActivate(getBoolean(Property.TEST_ON_ACTIVATE, poolConfig.testOnActivate));
        poolConfigBuilder.withAlwaysCheckOnActivate(getBoolean(Property.ALWAYS_CHECK_ON_ACTIVATE, poolConfig.alwaysCheckOnActivate));
        poolConfigBuilder.withTestOnDeactivate(getBoolean(Property.TEST_ON_DEACTIVATE, poolConfig.testOnDeactivate));
        poolConfigBuilder.withTestOnIdle(getBoolean(Property.TEST_ON_IDLE, poolConfig.testOnIdle));
        poolConfigBuilder.withTestThreads(getBoolean(Property.TEST_THREADS, poolConfig.testThreads));
        poolConfig = poolConfigBuilder.build();
        poolConfigReference.set(poolConfig);

        if (logPoolConfig) {
            doLogCurrentPoolConfig(poolConfig);
        }
    }

    /**
     * Logs the current pool configuration.
     */
    public void logCurrentPoolConfig() {
        doLogCurrentPoolConfig(poolConfigReference.get());
    }

    private void doLogCurrentPoolConfig(PoolConfig poolConfig) {
        if (poolConfig == null) {
            return;
        }

        List<Object> logArgs = new ArrayList<>(24);
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(I(poolConfig.maxIdle));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(L(poolConfig.maxIdleTime));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(I(poolConfig.maxActive));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(L(poolConfig.maxWait));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(L(poolConfig.maxLifeTime));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(poolConfig.exhaustedAction);
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(Autoboxing.valueOf(poolConfig.testOnActivate));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(Autoboxing.valueOf(poolConfig.testOnDeactivate));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(Autoboxing.valueOf(poolConfig.testOnIdle));
        logArgs.add(Strings.getLineSeparator());
        logArgs.add(Autoboxing.valueOf(poolConfig.testThreads));
        // @formatter:off
        LOG.info("Database pooling options:" +
            "{}    Maximum idle connections: {}" +
            "{}    Maximum idle time: {}ms" +
            "{}    Maximum active connections: {}" +
            "{}    Maximum wait time for a connection: {}ms" +
            "{}    Maximum life time of a connection: {}ms" +
            "{}    Action if connections exhausted: {}" +
            "{}    Test connections on activate: {}" +
            "{}    Test connections on deactivate: {}" +
            "{}    Test idle connections: {}" +
            "{}    Test threads for bad connection usage (SLOW): {}",
            logArgs.toArray(new Object[logArgs.size()])
         // @formatter:on
        );
    }


    PoolConfig getPoolConfig() {
        return poolConfigReference.get();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        Properties jdbcProps = jdbcPropsReference.get();
        result = prime * result + ((jdbcProps == null) ? 0 : jdbcProps.hashCode());
        PoolConfig poolConfig = poolConfigReference.get();
        result = prime * result + ((poolConfig == null) ? 0 : poolConfig.hashCode());
        Properties props = propsReference.get();
        result = prime * result + ((props == null) ? 0 : props.hashCode());
        result = prime * result + ((configDbReadProps == null) ? 0 : configDbReadProps.hashCode());
        result = prime * result + ((configDbWriteProps == null) ? 0 : configDbWriteProps.hashCode());
        return result;
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
        Configuration other = (Configuration) obj;
        Properties jdbcProps = jdbcPropsReference.get();
        Properties otherJdbcProps = other.jdbcPropsReference.get();
        if (jdbcProps == null) {
            if (otherJdbcProps != null) {
                return false;
            }
        } else if (!jdbcProps.equals(otherJdbcProps)) {
            return false;
        }
        PoolConfig poolConfig = poolConfigReference.get();
        if (poolConfig == null) {
            if (other.poolConfigReference.get() != null) {
                return false;
            }
        } else if (!poolConfig.equals(other.poolConfigReference.get())) {
            return false;
        }
        Properties props = propsReference.get();
        Properties otherProps = other.propsReference.get();
        if (props == null) {
            if (otherProps != null) {
                return false;
            }
        } else if (!matches(props, otherProps)) {
            return false;
        }
        if (configDbReadProps == null) {
            if (other.configDbReadProps != null) {
                return false;
            }
        } else if (!matches(configDbReadProps, other.configDbReadProps)) {
            return false;
        }
        if (configDbWriteProps == null) {
            if (other.configDbWriteProps != null) {
                return false;
            }
        } else if (!matches(configDbWriteProps, other.configDbWriteProps)) {
            return false;
        }
        return true;
    }

    /**
     * Checks differences between this configuration and given configuration
     *
     * @param other The other configuration to check against
     * @return The detecetd differences
     */
    ConfigurationDifference getDifferenceTo(Configuration other) {
        boolean bJdbcProps = false;
        boolean bPoolConfig = false;
        boolean bProps = false;
        boolean bConfigDbReadProps = false;
        boolean bConfigDbWriteProps = false;

        Properties jdbcProps = jdbcPropsReference.get();
        Properties otherJdbcProps = other.jdbcPropsReference.get();
        if (!matches(jdbcProps, otherJdbcProps)) {
            bJdbcProps = true;
        }

        PoolConfig poolConfig = poolConfigReference.get();
        if (!poolConfig.equals(other.poolConfigReference.get())) {
            bPoolConfig = true;
        }

        Properties props = propsReference.get();
        Properties otherProps = other.propsReference.get();
        if (!matches(props, otherProps)) {
            bProps = true;
        }

        if (!matches(configDbReadProps, other.configDbReadProps)) {
            bConfigDbReadProps = true;
        }

        if (!matches(configDbWriteProps, other.configDbWriteProps)) {
            bConfigDbWriteProps = true;
        }

        return new ConfigurationDifference(bJdbcProps, bPoolConfig, bProps, bConfigDbReadProps, bConfigDbWriteProps);
    }

    /**
     * Matches if two {@link Properties} can be considered equal
     *
     * @param p1 The first {@link Properties}
     * @param p2 The second {@link Properties}
     * @return <code>true</code> if both properties contain equal objects
     *         <code>false</code> otherwise
     */
    private static boolean matches(Properties p1, Properties p2) {
        if (p1.size() != p2.size()) {
            return false;
        }

        for (Map.Entry<Object, Object> f : p1.entrySet()) {
            Object p2Value = p2.get(f.getKey());
            if (null == p2Value) {
                if (null != f.getValue()) {
                    return false;
                }
            } else {
                if (false == p2Value.equals(f.getValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Enumeration of all properties in the configdb.properties file.
     */
    public static enum Property {
        /** URL for configdb read. */
        READ_URL("readUrl"),
        /** URL for configdb write. */
        WRITE_URL("writeUrl"),
        /** Class name of driver for configdb read. */
        READ_DRIVER_CLASS("readDriverClass"),
        /** Class name of driver for configdb write. */
        WRITE_DRIVER_CLASS("writeDriverClass"),
        /** Interval of the cleaner threads. */
        CLEANER_INTERVAL("cleanerInterval"),
        /** Maximum of idle connections. */
        MAX_IDLE("maxIdle"),
        /** Maximum idle time. */
        MAX_IDLE_TIME("maxIdleTime"),
        /** Maximum of active connections. */
        MAX_ACTIVE("maxActive"),
        /** Maximum time to wait for a connection. */
        MAX_WAIT("maxWait"),
        /** Maximum life time of a connection. */
        MAX_LIFE_TIME("maxLifeTime"),
        /** Action if the maximum is reached. */
        EXHAUSTED_ACTION("exhaustedAction"),
        /** Validate connections if they are activated. */
        TEST_ON_ACTIVATE("testOnActivate"),
        /** Validate connections if they are deactivated. */
        TEST_ON_DEACTIVATE("testOnDeactivate"),
        /** Validate connections on a pool clean run. */
        TEST_ON_IDLE("testOnIdle"),
        /** Test threads if they use connections correctly. */
        TEST_THREADS("testThreads"),
        /** Allows to disable the replication monitor. */
        REPLICATION_MONITOR("com.openexchange.database.replicationMonitor"),
        /** Allows to write a warning into the logs if a connection to the master is only used to read data. */
        CHECK_WRITE_CONS("com.openexchange.database.checkWriteCons"),
        /** Specifies the lock mechanism to use. */
        LOCK_MECH("com.openexchange.database.lockMech"),
        /** Whether connection's validity is always explicitly checked on activate */
        ALWAYS_CHECK_ON_ACTIVATE("com.openexchange.database.alwaysCheckOnActivate");

        private final String propertyName;

        private Property(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }

    /**
     * Represents the difference between two <code>Configuration</code> instances.
     */
    public static class ConfigurationDifference {

        private final boolean jdbcProps;
        private final boolean poolConfig;
        private final boolean props;
        private final boolean configDbReadProps;
        private final boolean configDbWriteProps;

        /**
         * Initializes a new {@link ConfigurationDifference}.
         */
        ConfigurationDifference(boolean jdbcProps, boolean poolConfig, boolean props, boolean configDbReadProps, boolean configDbWriteProps) {
            super();
            this.jdbcProps = jdbcProps;
            this.poolConfig = poolConfig;
            this.props = props;
            this.configDbReadProps = configDbReadProps;
            this.configDbWriteProps = configDbWriteProps;
        }

        /**
         * Checks if anything is different.
         *
         * @return <code>true</code> if anything is different; otherwise <code>false</code> if considered equal
         */
        public boolean anythingDifferent() {
            return jdbcProps || poolConfig || props || configDbReadProps || configDbWriteProps;
        }

        /**
         * Checks whether JDBC properties are different.
         *
         * @return <code>true</code> if JDBC properties were different; otherwise <code>false</code>
         */
        public boolean areJdbcPropsDifferent() {
            return jdbcProps;
        }


        /**
         * Checks whether pool config is different.
         *
         * @return <code>true</code> if pool config is different; otherwise <code>false</code>
         */
        public boolean isPoolConfigDifferent() {
            return poolConfig;
        }


        /**
         * Checks whether properties are different.
         *
         * @return <code>true</code> if properties were different; otherwise <code>false</code>
         */
        public boolean arePropsDifferent() {
            return props;
        }


        /**
         * Checks whether configDb-read properties are different.
         *
         * @return <code>true</code> if configDb-read properties were different; otherwise <code>false</code>
         */
        public boolean areConfigDbReadPropsDifferent() {
            return configDbReadProps;
        }


        /**
         * Checks whether configDb-write properties are different.
         *
         * @return <code>true</code> if configDb-write properties were different; otherwise <code>false</code>
         */
        public boolean areConfigDbWritePropsDifferent() {
            return configDbWriteProps;
        }
    }

}
