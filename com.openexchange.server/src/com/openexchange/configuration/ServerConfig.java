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

package com.openexchange.configuration;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import com.openexchange.ajax.writer.LoginWriter;
import com.openexchange.config.ConfigTools;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.attach.AttachmentConfig;
import com.openexchange.groupware.infostore.InfostoreConfig;
import com.openexchange.groupware.notify.NotificationConfig;
import com.openexchange.java.Strings;
import com.openexchange.uploaddir.UploadDirService;

/**
 * This class handles the configuration parameters read from the configuration property file server.properties.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class ServerConfig implements Reloadable {

    /** The logger constant */
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ServerConfig.class);

    /**
     * Singleton object.
     */
    private static final ServerConfig SINGLETON = new ServerConfig();

    /**
     * Gets the instance.
     *
     * @return The instance
     */
    public static ServerConfig getInstance() {
        return SINGLETON;
    }

    // ------------------------------------------------------------------------------ //

    /**
     * Creates a new default configuration for "server.properties" file
     *
     * @return The newly created default configuration
     */
    private static Config newDefaultConfig() {
        return new ConfigBuilder(new Properties()).init().build();
    }

    /** Builds an instance of <code>Config</code> from a <code>Properties</code> instance (initialized from "server.properties" file) */
    private static class ConfigBuilder {

        private final Properties props;

        private String uploadDirectory = "/tmp/";
        private int maxFileUploadSize = 10000;
        private int maxUploadIdleTimeMillis = 300000;
        private boolean prefetchEnabled;
        private String defaultEncoding;
        private int jmxPort;
        private String jmxBindAddress;
        private Boolean checkIP;
        private String ipMaskV4;
        private String ipMaskV6;
        private final ClientWhitelist clientWhitelist;
        private String uiWebPath;
        private int cookieTTL;
        private boolean cookieHttpOnly;
        private int maxBodySize;
        private int defaultMaxConcurrentAJAXRequests;

        ConfigBuilder(Properties props) {
            super();
            this.props = props;
            clientWhitelist = new ClientWhitelist();
        }

        ConfigBuilder init() {
            // UPLOAD_DIRECTORY
            uploadDirectory = getPropertyInternal(Property.UploadDirectory);
            if (!uploadDirectory.endsWith("/")) {
                uploadDirectory += "/";
            }
            uploadDirectory += ".OX/";
            try {
                if (new File(uploadDirectory).mkdir()) {
                    Runtime.getRuntime().exec("chmod 700 " + uploadDirectory);
                    Runtime.getRuntime().exec("chown open-xchange:open-xchange " + uploadDirectory);
                    LOG.info("Temporary upload directory created");
                }
            } catch (Exception e) {
                LOG.error("Temporary upload directory could NOT be properly created", e);
            }
            // MAX_FILE_UPLOAD_SIZE
            try {
                maxFileUploadSize = Integer.parseInt(getPropertyInternal(Property.MaxFileUploadSize));
            } catch (NumberFormatException e) {
                maxFileUploadSize = 10000;
            }
            // MAX_UPLOAD_IDLE_TIME_MILLIS
            try {
                maxUploadIdleTimeMillis = Integer.parseInt(getPropertyInternal(Property.MaxUploadIdleTimeMillis));
            } catch (NumberFormatException e) {
                maxUploadIdleTimeMillis = 300000;
            }
            // PrefetchEnabled
            prefetchEnabled = Boolean.parseBoolean(getPropertyInternal(Property.PrefetchEnabled));
            // Default encoding
            defaultEncoding = getPropertyInternal(Property.DefaultEncoding);
            // JMX port
            jmxPort = Integer.parseInt(getPropertyInternal(Property.JMX_PORT));
            // JMX bind address
            jmxBindAddress = getPropertyInternal(Property.JMX_BIND_ADDRESS);
            // Check IP
            checkIP = Boolean.valueOf(getPropertyInternal(Property.IP_CHECK));
            ipMaskV4 = getPropertyInternal(Property.IP_MASK_V4);
            ipMaskV6 = getPropertyInternal(Property.IP_MASK_V6);
            // IP check whitelist
            clientWhitelist.clear();
            clientWhitelist.add(getPropertyInternal(Property.IP_CHECK_WHITELIST));
            // UI web path
            uiWebPath = getPropertyInternal(Property.UI_WEB_PATH);
            cookieTTL = (int) ConfigTools.parseTimespan(getPropertyInternal(Property.COOKIE_TTL));
            cookieHttpOnly = Boolean.parseBoolean(getPropertyInternal(Property.COOKIE_HTTP_ONLY));
            // The max. body size
            maxBodySize = Integer.parseInt(getPropertyInternal(Property.MAX_BODY_SIZE));
            // Default max. concurrent AJAX requests
            defaultMaxConcurrentAJAXRequests = Integer.parseInt(getPropertyInternal(Property.DEFAULT_MAX_CONCURRENT_AJAX_REQUESTS));
            return this;
        }

        private String getPropertyInternal(final Property property) {
            return props.getProperty(property.getPropertyName(), property.getDefaultValue());
        }

        Config build() {
            return new Config(props, uploadDirectory, maxFileUploadSize, maxUploadIdleTimeMillis, prefetchEnabled, defaultEncoding, jmxPort, jmxBindAddress, checkIP, ipMaskV4, ipMaskV6, clientWhitelist, uiWebPath, cookieTTL, cookieHttpOnly, maxBodySize, defaultMaxConcurrentAJAXRequests);
        }
    }

    /** Immutable configuration from "server.properties" file */
    private static class Config {

        final Properties props;
        final String uploadDirectory;
        final int maxFileUploadSize;
        final int maxUploadIdleTimeMillis;
        final boolean prefetchEnabled;
        final String defaultEncoding;
        final int jmxPort;
        final String jmxBindAddress;
        final Boolean checkIP;
        final String ipMaskV4;
        final String ipMaskV6;
        final ClientWhitelist clientWhitelist;
        final String uiWebPath;
        final int cookieTTL;
        final boolean cookieHttpOnly;
        final int maxBodySize;
        final int defaultMaxConcurrentAJAXRequests;

        Config(Properties props, String uploadDirectory, int maxFileUploadSize, int maxUploadIdleTimeMillis, boolean prefetchEnabled, String defaultEncoding, int jmxPort, String jmxBindAddress, Boolean checkIP, String ipMaskV4, String ipMaskV6, ClientWhitelist clientWhitelist, String uiWebPath, int cookieTTL, boolean cookieHttpOnly, int maxBodySize, int defaultMaxConcurrentAJAXRequests) {
            super();
            this.props = props;
            this.uploadDirectory = uploadDirectory;
            this.maxFileUploadSize = maxFileUploadSize;
            this.maxUploadIdleTimeMillis = maxUploadIdleTimeMillis;
            this.prefetchEnabled = prefetchEnabled;
            this.defaultEncoding = defaultEncoding;
            this.jmxPort = jmxPort;
            this.jmxBindAddress = jmxBindAddress;
            this.checkIP = checkIP;
            this.ipMaskV4 = ipMaskV4;
            this.ipMaskV6 = ipMaskV6;
            this.clientWhitelist = clientWhitelist;
            this.uiWebPath = uiWebPath;
            this.cookieTTL = cookieTTL;
            this.cookieHttpOnly = cookieHttpOnly;
            this.maxBodySize = maxBodySize;
            this.defaultMaxConcurrentAJAXRequests = defaultMaxConcurrentAJAXRequests;
        }
    }

    // ------------------------------------------------------------------------------ //

    private final AtomicReference<Config> configReference;

    private ServerConfig() {
        super();
        configReference = new AtomicReference<>(newDefaultConfig());
    }

    @Override
    public void reloadConfiguration(ConfigurationService configService) {
        initialize(configService);

        try {
            AttachmentConfig attachmentConfig = AttachmentConfig.getInstance();
            attachmentConfig.stop();
            attachmentConfig.start();
        } catch (Exception e) {
            LOG.warn("Could not reload attachment configuration.", e);
        }

        try {
            InfostoreConfig infostoreConfig = InfostoreConfig.getInstance();
            infostoreConfig.stop();
            infostoreConfig.start();
        } catch (Exception e) {
            LOG.warn("Could not reload infostore configuration.", e);
        }

        try {
            NotificationConfig notificationConfig = NotificationConfig.getInstance();
            notificationConfig.stop();
            notificationConfig.start();
        } catch (Exception e) {
            LOG.warn("Could not reload infostore configuration.", e);
        }

        LoginWriter.invalidateRandomTokenEnabled();
    }

    /** The name of the properties file. */
    private static final String FILENAME = "server.properties";

    /**
     * Initializes this server config instance.
     *
     * @param confService The configuration service to use
     */
    public void initialize(ConfigurationService confService) {
        Properties newProps = confService.getFile(FILENAME);
        if (null == newProps) {
            LOG.info("Configuration file {} is missing. Using defaults.", FILENAME);
            configReference.set(newDefaultConfig());
        } else {
            configReference.set(new ConfigBuilder(newProps).init().build());
            LOG.info("Successfully read configuration file {}.", FILENAME);
        }
        File tmpDir = getTmpDir();
        ImageIO.setCacheDirectory(tmpDir);
        System.setProperty("java.io.tmpdir", tmpDir.getPath());
        System.setProperty("jna.tmpdir", tmpDir.getPath());
    }

    /**
     * Shuts-down this server config instance.
     */
    public void shutdown() {
        configReference.set(newDefaultConfig());
    }

    // ------------------------------------------------------------------------------ //

    /**
     * Gets the currently active configuration.
     *
     * @return The currently active configuration
     */
    private static Config getCurrentConfig() {
        return SINGLETON.configReference.get();
    }

    /**
     * Returns the value of the property with the specified key. This method returns <code>null</code> if the property is not found.
     *
     * @param key the property key.
     * @return the value of the property or <code>null</code> if the property is not found.
     */
    private static String getProperty(final String key) {
        return getCurrentConfig().props.getProperty(key);
    }

    /**
     * Gets the upload directory to save to.
     * <p>
     * <b>Note</b>: If not within "com.openexchange.server" bundle, please prefer to use {@link UploadDirService} instead.
     *
     * @return The directory
     * @throws IllegalArgumentException If upload directory cannot be returned; e.g. property missing or no such directory exists
     */
    public static File getTmpDir() {
        String path = getProperty(Property.UploadDirectory);
        if (null == path) {
            throw new IllegalArgumentException("Path is null. Probably property \"UPLOAD_DIRECTORY\" is not set.");
        }
        final File tmpDir = new File(path);
        if (!tmpDir.exists()) {
            if (!tmpDir.mkdirs()) {
                throw new IllegalArgumentException("Directory " + path + " does not exist and cannot be created.");
            }
            LOG.info("Directory {} did not exist, but could be created.", path);
        }
        if (!tmpDir.isDirectory()) {
            throw new IllegalArgumentException(path + " is not a directory.");
        }
        return tmpDir;
    }

    /**
     * @param property
     *            wanted property.
     * @return the value of the property.
     */
    @SuppressWarnings("unchecked")
    public static <V> V getPropertyObject(final Property property) {
        try {
            final Object value;
            switch (property) {
            case UploadDirectory:
                value = getCurrentConfig().uploadDirectory;
                break;
            case MaxFileUploadSize:
                value = Integer.valueOf(getCurrentConfig().maxFileUploadSize);
                break;
            case MaxUploadIdleTimeMillis:
                value = Integer.valueOf(getCurrentConfig().maxUploadIdleTimeMillis);
                break;
            case PrefetchEnabled:
                value = Boolean.valueOf(getCurrentConfig().prefetchEnabled);
                break;
            case DefaultEncoding:
                value = getCurrentConfig().defaultEncoding;
                break;
            case JMX_PORT:
                value = Integer.valueOf(getCurrentConfig().jmxPort);
                break;
            case JMX_BIND_ADDRESS:
                value = getCurrentConfig().jmxBindAddress;
                break;
            case UI_WEB_PATH:
                value = getCurrentConfig().uiWebPath;
                break;
            case COOKIE_TTL:
                value = Integer.valueOf(getCurrentConfig().cookieTTL);
                break;
            case COOKIE_HTTP_ONLY:
                value = Boolean.valueOf(getCurrentConfig().cookieHttpOnly);
                break;
            case IP_CHECK:
                value = getCurrentConfig().checkIP;
                break;
            case IP_MASK_V4:
                value = getCurrentConfig().ipMaskV4;
                break;
            case IP_MASK_V6:
                value = getCurrentConfig().ipMaskV6;
                break;
            case IP_CHECK_WHITELIST:
                value = getCurrentConfig().clientWhitelist;
                break;
            case MAX_BODY_SIZE:
                value = Integer.valueOf(getCurrentConfig().maxBodySize);
                break;
            case DEFAULT_MAX_CONCURRENT_AJAX_REQUESTS:
                value = Integer.valueOf(getCurrentConfig().defaultMaxConcurrentAJAXRequests);
                break;
            default:
                value = getProperty(property.getPropertyName());
            }
            return (V) value;
        } catch (ClassCastException e) {
            LOG.debug("", e);
            return null;
        }
    }

    /**
     * @param property wanted property.
     * @return the value of the property.
     */
    public static String getProperty(final Property property) {
        final String value;
        switch (property) {
        case UploadDirectory:
            value = getCurrentConfig().uploadDirectory;
            break;
        case MaxFileUploadSize:
            value = Integer.toString(getCurrentConfig().maxFileUploadSize);
            break;
        case MaxUploadIdleTimeMillis:
            value = Integer.toString(getCurrentConfig().maxUploadIdleTimeMillis);
            break;
        case PrefetchEnabled:
            value = String.valueOf(getCurrentConfig().prefetchEnabled);
            break;
        case DefaultEncoding:
            value = getCurrentConfig().defaultEncoding;
            break;
        case JMX_PORT:
            value = Integer.toString(getCurrentConfig().jmxPort);
            break;
        case JMX_BIND_ADDRESS:
            value = getCurrentConfig().jmxBindAddress;
            break;
        case UI_WEB_PATH:
            value = getCurrentConfig().uiWebPath;
            break;
        case COOKIE_TTL:
            value = Integer.toString(getCurrentConfig().cookieTTL);
            break;
        case COOKIE_HTTP_ONLY:
            value = String.valueOf(getCurrentConfig().cookieHttpOnly);
            break;
        case IP_CHECK:
            value = getCurrentConfig().checkIP.toString();
            break;
        case IP_MASK_V4:
            value = getCurrentConfig().ipMaskV4.toString();
            break;
        case IP_MASK_V6:
            value = getCurrentConfig().ipMaskV6.toString();
            break;
        case MAX_BODY_SIZE:
            value = Integer.toString(getCurrentConfig().maxBodySize);
            break;
        case DEFAULT_MAX_CONCURRENT_AJAX_REQUESTS:
            value = Integer.toString(getCurrentConfig().defaultMaxConcurrentAJAXRequests);
            break;
        default:
            value = getProperty(property.getPropertyName());
        }
        return value;
    }

    /**
     * Returns <code>true</code> if and only if the property named by the argument exists and is equal to the string <code>"true"</code>.
     * The test of this string is case insensitive.
     * <p>
     * If there is no property with the specified name, or if the specified name is empty or null, then <code>false</code> is returned.
     *
     * @param property the property.
     * @return the <code>boolean</code> value of the property.
     */
    public static boolean getBoolean(final Property property) {
        final boolean value;
        if (Property.PrefetchEnabled == property) {
            value = getCurrentConfig().prefetchEnabled;
        } else if (Property.COOKIE_HTTP_ONLY == property) {
            value = getCurrentConfig().cookieHttpOnly;
        } else {
            value = Boolean.parseBoolean(getCurrentConfig().props.getProperty(property.getPropertyName()));
        }
        return value;
    }

    public static Integer getInteger(final Property property) throws OXException {
        final Integer value;
        switch (property) {
        case MaxFileUploadSize:
            value = I(getCurrentConfig().maxFileUploadSize);
            break;
        case MaxUploadIdleTimeMillis:
            value = I(getCurrentConfig().maxUploadIdleTimeMillis);
            break;
        case JMX_PORT:
            value = I(getCurrentConfig().jmxPort);
            break;
        case COOKIE_TTL:
            value = I(getCurrentConfig().cookieTTL);
            break;
        case MAX_BODY_SIZE:
            value = I(getCurrentConfig().maxBodySize);
            break;
        case DEFAULT_MAX_CONCURRENT_AJAX_REQUESTS:
            value = I(getCurrentConfig().defaultMaxConcurrentAJAXRequests);
            break;
        default:
            try {
                final String prop = getProperty(property.getPropertyName());
                if (prop == null) {
                    throw ConfigurationExceptionCodes.PROPERTY_MISSING.create(property.getPropertyName());
                }
                value = Integer.valueOf(getProperty(property.getPropertyName()));
            } catch (NumberFormatException e) {
                throw ConfigurationExceptionCodes.PROPERTY_NOT_AN_INTEGER.create(property.getPropertyName());
            }
        }
        return value;
    }

    /**
     * @param property wanted property.
     * @return the value of the property.
     * @throws OXException If property is missing or its type is not an integer
     */
    public static int getInt(final Property property) throws OXException {
        final int value;
        switch (property) {
        case MaxFileUploadSize:
            value = getCurrentConfig().maxFileUploadSize;
            break;
        case MaxUploadIdleTimeMillis:
            value = getCurrentConfig().maxUploadIdleTimeMillis;
            break;
        case JMX_PORT:
            value = getCurrentConfig().jmxPort;
            break;
        case COOKIE_TTL:
            value = getCurrentConfig().cookieTTL;
            break;
        case MAX_BODY_SIZE:
            value = getCurrentConfig().maxBodySize;
            break;
        case DEFAULT_MAX_CONCURRENT_AJAX_REQUESTS:
            value = getCurrentConfig().defaultMaxConcurrentAJAXRequests;
            break;
        default:
            try {
                final String prop = getProperty(property.getPropertyName());
                if (Strings.isEmpty(prop)) {
                    throw ConfigurationExceptionCodes.PROPERTY_MISSING.create(property.getPropertyName());
                }
                value = Integer.parseInt(prop.trim());
            } catch (NumberFormatException e) {
                throw ConfigurationExceptionCodes.PROPERTY_NOT_AN_INTEGER.create(e, property.getPropertyName());
            }
        }
        return value;
    }

    public static Long getLong(final Property property) throws OXException {
        final Long value;
        switch (property) {
        case MaxFileUploadSize:
            value = L(getCurrentConfig().maxFileUploadSize);
            break;
        case MaxUploadIdleTimeMillis:
            value = L(getCurrentConfig().maxUploadIdleTimeMillis);
            break;
        case JMX_PORT:
            value = L(getCurrentConfig().jmxPort);
            break;
        case COOKIE_TTL:
            value = L(getCurrentConfig().cookieTTL);
            break;
        case MAX_BODY_SIZE:
            value = L(getCurrentConfig().maxBodySize);
            break;
        case DEFAULT_MAX_CONCURRENT_AJAX_REQUESTS:
            value = L(getCurrentConfig().defaultMaxConcurrentAJAXRequests);
            break;
        default:
            try {
                final String prop = getProperty(property.getPropertyName());
                if (prop == null) {
                    throw ConfigurationExceptionCodes.PROPERTY_MISSING.create(property.getPropertyName());
                }
                value = Long.valueOf(getProperty(property.getPropertyName()));
            } catch (NumberFormatException e) {
                throw ConfigurationExceptionCodes.PROPERTY_NOT_AN_INTEGER.create(property.getPropertyName());
            }
        }
        return value;
    }

    //TODO: Make the transition to the ServerProperty
    public static enum Property {
        /**
         * Upload directory.
         * <p>
         * Please prefer {@link ServerConfig#getTmpDir()} to obtain the upload directory.
         *
         * @see ServerConfig#getTmpDir()
         */
        UploadDirectory("UPLOAD_DIRECTORY", "/tmp/"),
        /**
         * Max upload file size.
         */
        @Deprecated
        MaxFileUploadSize("MAX_UPLOAD_FILE_SIZE", "10000"),
        /**
         * Enable/Disable SearchIterator's ResultSet prefetch.
         */
        PrefetchEnabled("PrefetchEnabled", Boolean.TRUE.toString()),
        /**
         * Default encoding.
         */
        DefaultEncoding("DefaultEncoding", "UTF-8"),
        /**
         * The maximum size of accepted uploads. May be overridden in specialized module configs and user settings.
         */
        MAX_UPLOAD_SIZE("MAX_UPLOAD_SIZE", "104857600"),
        /**
         * JMXPort
         */
        JMX_PORT("JMXPort", "9999"),
        /**
         * JMXBindAddress
         */
        JMX_BIND_ADDRESS("JMXBindAddress", "localhost"),
        /**
         * Max idle time for uploaded files in milliseconds
         */
        MaxUploadIdleTimeMillis("MAX_UPLOAD_IDLE_TIME_MILLIS", "300000"),
        /**
         * Number of characters a search pattern must contain to prevent slow search queries and big responses in large contexts.
         */
        MINIMUM_SEARCH_CHARACTERS("com.openexchange.MinimumSearchCharacters", "0"),
        /**
         * On session validation of every request the client IP address is compared with the client IP address used for the login request.
         * If this connfiguration parameter is set to <code>true</code> and the client IP addresses do not match the request will be denied.
         * Setting this parameter to <code>false</code> will only log the different client IP addresses with debug level.
         */
        IP_CHECK("com.openexchange.IPCheck", Boolean.TRUE.toString()),
        /**
        * Subnet mask for accepting IP-ranges.
        * Using CIDR-Notation for v4 and v6 or dotted decimal only for v4.
        * Examples:
        * com.openexchange.IPMaskV4=255.255.255.0
        * com.openexchange.IPMaskV4=/24
        * com.openexchange.IPMaskV6=/60
        */
        IP_MASK_V4("com.openexchange.IPMaskV4", ""),
        IP_MASK_V6("com.openexchange.IPMaskV6", ""),
        /**
         * The comma-separated list of client patterns that do bypass IP check
         */
        IP_CHECK_WHITELIST("com.openexchange.IPCheckWhitelist", ""),
        /**
         * Configures the path on the web server where the UI is located. This path is used to generate links directly into the UI. The
         * default conforms to the path where the UI is installed by the standard packages on the web server.
         */
        UI_WEB_PATH("com.openexchange.UIWebPath", "/appsuite/"),
        /**
         * The cookie time-to-live
         */
        COOKIE_TTL("com.openexchange.cookie.ttl", "1W"),
        /**
         * The Cookie HttpOnly flag
         */
        COOKIE_HTTP_ONLY("com.openexchange.cookie.httpOnly", Boolean.TRUE.toString()),
        /**
         * The fields used to calculate the hash value which is part of Cookie name.
         * <p>
         * This option only has effect if "com.openexchange.cookie.hash" option is set to "calculate".
         */
        COOKIE_HASH_FIELDS("com.openexchange.cookie.hash.fields", ""),
        /**
         * The method how to generate the hash value which is part of Cookie name
         */
        COOKIE_HASH("com.openexchange.cookie.hash", "calculate"),
        /**
         * Whether to force secure flag for Cookies
         */
        COOKIE_FORCE_HTTPS("com.openexchange.forceHTTPS", Boolean.FALSE.toString()),
        /**
         * Whether to force HTTPS protocol.
         */
        FORCE_HTTPS("com.openexchange.forceHTTPS", Boolean.FALSE.toString()),
        /**
         * The max. allowed size of a HTTP request
         *
         * @deprecated Use "com.openexchange.servlet.maxBodySize" instead
         */
        @Deprecated
        MAX_BODY_SIZE("MAX_BODY_SIZE", "104857600"),
        /**
         * The default value for max. concurrent AJAX requests.
         */
        DEFAULT_MAX_CONCURRENT_AJAX_REQUESTS("com.openexchange.defaultMaxConcurrentAJAXRequests", "100"),
        /**
         * The shard name of this server
         */
        SHARD_NAME("com.openexchange.server.shardName", "default")

        ;

        private final String propertyName;
        private final String defaultValue;

        private Property(final String propertyName, final String defaultValue) {
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
        }

        /**
         * Gets the property name; e.g. <code>"com.openexchange.IPCheckWhitelist"</code>
         *
         * @return The property name
         */
        public String getPropertyName() {
            return propertyName;
        }

        /**
         * Gets the default value to assume for this property if absent in "server.properties" files
         *
         * @return The default value
         */
        public String getDefaultValue() {
            return defaultValue;
        }
    }

    @Override
    public Interests getInterests() {
        return Reloadables.interestsForFiles("server.properties");
    }

}
