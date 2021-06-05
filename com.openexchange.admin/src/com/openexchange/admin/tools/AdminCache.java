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

package com.openexchange.admin.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DataTruncation;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.osgi.framework.BundleContext;
import com.openexchange.admin.exceptions.OXGenericException;
import com.openexchange.admin.properties.AdminProperties;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Credentials;
import com.openexchange.admin.rmi.dataobjects.PasswordMechObject;
import com.openexchange.admin.rmi.dataobjects.UserModuleAccess;
import com.openexchange.admin.rmi.exceptions.PoolException;
import com.openexchange.admin.rmi.exceptions.StorageException;
import com.openexchange.admin.services.AdminServiceRegistry;
import com.openexchange.admin.storage.sqlStorage.OXAdminPoolDBPool;
import com.openexchange.admin.storage.sqlStorage.OXAdminPoolInterface;
import com.openexchange.caching.CacheService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.database.Databases;
import com.openexchange.database.JdbcProperties;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.password.mechanism.PasswordDetails;
import com.openexchange.password.mechanism.PasswordMech;
import com.openexchange.password.mechanism.PasswordMechRegistry;
import com.openexchange.tools.sql.DBUtils;

/**
 * {@link AdminCache}
 */
public class AdminCache {

    private static final AtomicReference<BundleContext> BUNDLE_CONTEXT = new AtomicReference<>();

    /**
     * Gets the <tt>BundleContext</tt>.
     *
     * @return The <tt>BundleContext</tt> or <code>null</code>
     */
    public static BundleContext getBundleContext() {
        return BUNDLE_CONTEXT.get();
    }

    /**
     * Atomically sets the <tt>BundleContext</tt> to the given updated <tt>BundleContext</tt> reference if the current value <tt>==</tt> the expected value.
     *
     * @param expect the expected <tt>BundleContext</tt>
     * @param update the new <tt>BundleContext</tt>
     * @return <code>true</code> if successful. <code>false</code> return indicates that the actual <tt>ConfigurationService</tt> was not equal to the expected <tt>ConfigurationService</tt>.
     */
    public static boolean compareAndSetBundleContext(BundleContext expect, BundleContext update) {
        return BUNDLE_CONTEXT.compareAndSet(expect, update);
    }

    /**
     * Sets the <tt>BundleContext</tt>.
     *
     * @param service The <tt>BundleContext</tt> to set
     */
    public static void setBundleContext(BundleContext service) {
        BUNDLE_CONTEXT.set(service);
    }

    private static final AtomicReference<ConfigurationService> CONF_SERVICE = new AtomicReference<>();
    private static final AtomicReference<CacheService> CACHE_SERVICE = new AtomicReference<>();

    /**
     * Gets the <tt>ConfigurationService</tt>.
     *
     * @return The <tt>ConfigurationService</tt> or <code>null</code>
     */
    public static ConfigurationService getConfigurationService() {
        return CONF_SERVICE.get();
    }

    /**
     * Atomically sets the <tt>ConfigurationService</tt> to the given updated <tt>ConfigurationService</tt> reference if the current value <tt>==</tt> the expected value.
     *
     * @param expect the expected <tt>ConfigurationService</tt>
     * @param update the new <tt>ConfigurationService</tt>
     * @return <code>true</code> if successful. <code>false</code> return indicates that the actual <tt>ConfigurationService</tt> was not equal to the expected <tt>ConfigurationService</tt>.
     */
    public static boolean compareAndSetConfigurationService(ConfigurationService expect, ConfigurationService update) {
        return CONF_SERVICE.compareAndSet(expect, update);
    }

    /**
     * Sets the <tt>ConfigurationService</tt>.
     *
     * @param service The <tt>ConfigurationService</tt> to set
     */
    public static void setConfigurationService(ConfigurationService service) {
        CONF_SERVICE.set(service);
    }

    /**
     * Gets the <tt>CacheService</tt>.
     *
     * @return The <tt>CacheService</tt> or <code>null</code>
     */
    public static CacheService getCacheService() {
        return CACHE_SERVICE.get();
    }

    /**
     * Atomically sets the <tt>CacheService</tt> to the given updated <tt>CacheService</tt> reference if the current value <tt>==</tt> the expected value.
     *
     * @param expect the expected <tt>CacheService</tt>
     * @param update the new <tt>CacheService</tt>
     * @return <code>true</code> if successful. <code>false</code> return indicates that the actual <tt>CacheService</tt> was not equal to the expected <tt>CacheService</tt>.
     */
    public static boolean compareAndSetCacheService(CacheService expect, CacheService update) {
        return CACHE_SERVICE.compareAndSet(expect, update);
    }

    /**
     * Sets the <tt>CacheService</tt>.
     *
     * @param service The <tt>CacheService</tt> to set
     */
    public static void setCacheService(CacheService service) {
        CACHE_SERVICE.set(service);
    }

    public final static String DATA_TRUNCATION_ERROR_MSG = "Data too long for column(s)";

    private PropertyHandler prop = null;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminCache.class);

    private OXAdminPoolInterface pool = null;

    /**
     * toggle auth for hosting features like create context etc. where master auth is needed
     */
    private boolean masterAuthenticationDisabled = false;

    /**
     * togle auth when a context based method authentication is need like create user in a context
     */
    private boolean contextAuthenticationDisabled = false;

    private boolean allowMasterOverride = false;

    // master credentials for authenticating master admin
    private Credentials masterCredentials = null;

    private Hashtable<Integer, Credentials> adminCredentialsCache = null;

    private Hashtable<Integer, String> adminAuthMechCache = null;

    // the patterns for parsing the sql scripts
    public static final String PATTERN_REGEX_NORMAL = "(" + "CREATE\\s+TABLE|" + "DELETE|" + "UPDATE|" + "ALTER|" + "DROP|" + "SET|" + "RENAME)" + " (.*?)\\s*;";

    public static final String PATTERN_REGEX_DELIMITER = "DELIMITER (.*?)\\n";

    public static final String PATTERN_REGEX_FUNCTION = "CREATE\\s+(FUNCTION|PROCEDURE) (.*?)END\\s*//";

    private HashMap<String, UserModuleAccess> named_access_combinations = null;

    /**
     * Initialises a new {@link AdminCache}.
     */
    public AdminCache() {
        super();
    }

    /**
     * Initialises the cache
     *
     * @param service The {@link ConfigurationService}
     * @throws OXGenericException if an error is occurred
     */
    public void initCache(ConfigurationService service) throws OXGenericException {
        this.prop = new PropertyHandler(System.getProperties());
        configureAuthentication(); // disabling authentication mechs
        readAndSetMasterCredentials(service);
        log.info("Init Cache");
        initPool();
        this.adminCredentialsCache = new Hashtable<>();
        this.adminAuthMechCache = new Hashtable<>();
    }

    /**
     * return default {@link UserModuleAccess} if not specified upon creation
     *
     * @return
     */
    public static UserModuleAccess getDefaultUserModuleAccess() {
        UserModuleAccess ret = new UserModuleAccess();
        ret.disableAll();
        ret.setWebmail(true);
        ret.setContacts(true);
        return ret;
    }

    public synchronized HashMap<String, UserModuleAccess> getAccessCombinationNames() {
        return named_access_combinations;
    }

    /**
     * @deprecated use {@link #getNamedAccessCombination(String, boolean)}
     */
    @Deprecated
    public synchronized UserModuleAccess getNamedAccessCombination(String name) {
        return getNamedAccessCombination(name, true);
    }

    /**
     * Gets the access combination by given name
     *
     * @param name The name
     * @param contextAdmin Whether this call is performed by context administrator
     * @return The access combination or <code>null</code>
     */
    public synchronized UserModuleAccess getNamedAccessCombination(String name, boolean contextAdmin) {
        UserModuleAccess retval = named_access_combinations.get(name);
        if (null == retval) {
            return null;
        }
        if (!contextAdmin) {
            // publicFolderEditable can only be applied to the context administrator.
            retval.setPublicFolderEditable(false);
        }
        return retval;
    }

    public synchronized boolean existsNamedAccessCombination(String name) {
        return named_access_combinations.containsKey(name);
    }

    /**
     * Gets the name for the specified access combination
     *
     * @param accessCombination The access combination
     * @return The name access combination, or <code>null</code> if none found
     */
    public synchronized String getNameForAccessCombination(UserModuleAccess accessCombination) {
        for (Entry<String, UserModuleAccess> entry : named_access_combinations.entrySet()) {
            if (entry.getValue().equals(accessCombination)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized void reinitAccessCombinations() throws ClassNotFoundException, OXGenericException {
        named_access_combinations = null;
        initAccessCombinations();
    }

    public synchronized void initAccessCombinations() throws ClassNotFoundException, OXGenericException {
        if (named_access_combinations != null) {
            return;
        }
        try {
            log.info("Processing access combinations...");
            named_access_combinations = getAccessCombinations(loadValidAccessModules(), loadAccessCombinations());
            log.info("Access combinations processed!");
        } catch (ClassNotFoundException e) {
            log.error("Error loading access modules and methods!", e);
            throw e;
        } catch (OXGenericException e) {
            log.error("Error processing access combinations config file!!", e);
            throw e;
        }
    }

    private HashMap<String, UserModuleAccess> getAccessCombinations(HashMap<String, Method> module_method_mapping, Properties access_props) throws OXGenericException {
        HashMap<String, UserModuleAccess> combis = new HashMap<>();
        // Now check if predefined combinations are valid
        Enumeration<Object> predefined_access_combinations = access_props.keys();
        while (predefined_access_combinations.hasMoreElements()) {
            String predefined_combination_name = (String) predefined_access_combinations.nextElement();
            String predefined_modules = (String) access_props.get(predefined_combination_name);
            if (predefined_modules != null) {
                UserModuleAccess us = new UserModuleAccess();
                us.disableAll();
                us.setGlobalAddressBookDisabled(false); // by default this is enabled.
                String[] modules = Strings.isEmpty(predefined_modules) ? new String[0] : predefined_modules.split(" *, *");
                for (String module : modules) {
                    module = module.trim();
                    Method meth = module_method_mapping.get(module);
                    if (null == meth) {
                        log.error("Predefined combination \"{}\" contains invalid module \"{}\" ", predefined_combination_name, module);
                        // AS DEFINED IN THE CONTEXT WIDE ACCES SPECIFICAION ,
                        // THE SYSTEM WILL STOP IF IT FINDS AN INVALID
                        // CONFIGURATION!

                        // TODO: Lets stop the admin daemon
                        throw new OXGenericException("Invalid access combinations found in config file!");
                    }
                    try {
                        meth.invoke(us, Boolean.TRUE);
                    } catch (IllegalArgumentException e) {
                        log.error("Illegal argument passed to method!", e);
                    } catch (IllegalAccessException e) {
                        log.error("Illegal access!", e);
                    } catch (InvocationTargetException e) {
                        log.error("Invocation target error!", e);
                    }
                }
                // add moduleaccess object to hashmap/list identified by
                // combinations name
                combis.put(predefined_combination_name, us);
            }
        }

        return combis;
    }

    private HashMap<String, Method> loadValidAccessModules() throws ClassNotFoundException {

        // If we wanna blacklist some still unused modules, add them here!
        HashSet<String> BLACKLIST = new HashSet<>();
        // BLACKLIST.add("");

        try {

            // Load the available combinations directly from the
            // usermoduleaccess object
            Class<?> tmp = Class.forName(UserModuleAccess.class.getCanonicalName());
            Method methlist[] = tmp.getDeclaredMethods();
            HashMap<String, Method> module_method_mapping = new HashMap<>();

            log.debug("Listing available modules for use in access combinations...");
            for (Method method : methlist) {
                String meth_name = method.getName();
                if (meth_name.startsWith("set")) {
                    // remember all valid modules and its set methods
                    String module_name = meth_name.substring(3, meth_name.length()).toLowerCase();
                    if (!BLACKLIST.contains(module_name)) {
                        module_method_mapping.put(module_name, method);
                        log.debug(module_name);
                    }
                }
            }
            log.debug("End of list for access combinations!");
            return module_method_mapping;
        } catch (ClassNotFoundException e1) {
            log.error("UserModuleAccess class not found!Exiting application!", e1);
            throw e1;
        }
    }

    private static Properties loadAccessCombinations() throws OXGenericException {
        // Load properties from file , if does not exists use fall-back properties!
        ConfigurationService service = AdminServiceRegistry.getInstance().getService(ConfigurationService.class);
        if (null == service) {
            throw new IllegalStateException("Absent service: " + ConfigurationService.class.getName());
        }
        try {
            return service.getFile("ModuleAccessDefinitions.properties");
        } catch (Exception e) {
            throw new OXGenericException("ModuleAccessDefinitions.properties file cannot be opened for reading!", e);
        }
    }

    protected void initPool() {
        this.pool = new OXAdminPoolDBPool();
    }

    protected void initPool(OXAdminPoolInterface pool) {
        this.pool = pool;
    }

    public OXAdminPoolInterface getPool() {
        return pool;
    }

    public Credentials getMasterCredentials() {
        return this.masterCredentials;
    }

    public PropertyHandler getProperties() {
        return this.prop;
    }

    public Connection getConnectionForContext(int context_id) throws PoolException {
        return this.pool.getConnectionForContext(context_id);
    }

    public Connection getConnectionForContextNoTimeout(int contextId) throws PoolException {
        return this.pool.getConnectionForContextNoTimeout(contextId);
    }

    public boolean pushConnectionForContext(int context_id, Connection con) throws PoolException {
        if (con == null) {
            return true;
        }
        return this.pool.pushConnectionForContext(context_id, con);
    }

    public boolean pushConnectionForContextAfterReading(int context_id, Connection con) throws PoolException {
        if (con == null) {
            return true;
        }
        return this.pool.pushConnectionForContextAfterReading(context_id, con);
    }

    public boolean pushConnectionForContextNoTimeout(int contextId, Connection con) throws PoolException {
        return this.pool.pushConnectionForContextNoTimeout(contextId, con);
    }

    /**
     * Use {@link #getReadConnectionForConfigDB()} or {@link #getWriteConnectionForConfigDB()} instead.
     */
    @Deprecated
    public Connection getConnectionForConfigDB() throws PoolException {
        return this.pool.getConnectionForConfigDB();
    }

    public Connection getReadConnectionForConfigDB() throws PoolException {
        return this.pool.getReadConnectionForConfigDB();
    }

    public Connection getWriteConnectionForConfigDB() throws PoolException {
        return this.pool.getWriteConnectionForConfigDB();
    }

    public Connection getWriteConnectionForConfigDBNoTimeout() throws PoolException {
        return this.pool.getWriteConnectionForConfigDBNoTimeout();
    }

    @Deprecated
    public boolean pushConnectionForConfigDB(Connection con) throws PoolException {
        return this.pool.pushConnectionForConfigDB(con);
    }

    public boolean pushWriteConnectionForConfigDB(Connection con) throws PoolException {
        return this.pool.pushWriteConnectionForConfigDB(con);
    }

    public boolean pushReadConnectionForConfigDB(Connection con) throws PoolException {
        return this.pool.pushReadConnectionForConfigDB(con);
    }

    public boolean pushWriteConnectionForConfigDBNoTimeout(Connection con) throws PoolException {
        return this.pool.pushWriteConnectionForConfigDBNoTimeout(con);
    }

    public int getServerId() throws PoolException {
        return this.pool.getServerId();
    }

    /**
     * @return the adminCredentials
     */
    public final Credentials getAdminCredentials(Context ctx) {
        return this.adminCredentialsCache.get(ctx.getId());
    }

    /**
     * @return authMech
     */
    public final String getAdminAuthMech(Context ctx) {
        return this.adminAuthMechCache.get(ctx.getId());
    }

    /**
     * @param adminCredentials the adminCredentials to set
     */
    public final void setAdminCredentials(Context ctx, String authMech, Credentials adminCredentials) {
        // only set if authentication is enabled
        if (this.contextAuthenticationDisabled) {
            return;
        }
        this.adminCredentialsCache.put(ctx.getId(), adminCredentials);
        this.adminAuthMechCache.put(ctx.getId(), authMech);
    }

    /**
     * @param ctx
     */
    public final void removeAdminCredentials(Context ctx) {
        if (this.adminCredentialsCache.containsKey(ctx.getId())) {
            this.adminCredentialsCache.remove(ctx.getId());
        }
        if (this.adminAuthMechCache.containsKey(ctx.getId())) {
            this.adminAuthMechCache.remove(ctx.getId());
        }
    }

    public boolean masterAuthenticationDisabled() {
        return masterAuthenticationDisabled;
    }

    public boolean contextAuthenticationDisabled() {
        return contextAuthenticationDisabled;
    }

    public static Connection getSimpleSqlConnection(String url, String user, String password, String driver) throws SQLException, ClassNotFoundException {
        Class.forName(driver);
        // give database some time to react (in seconds)
        DriverManager.setLoginTimeout(120);

        JdbcProperties jdbcProperties = AdminServiceRegistry.getInstance().getService(JdbcProperties.class);

        String urlToUse = url;
        java.util.Properties defaults = jdbcProperties.getJdbcPropertiesCopy();
        if (null == defaults) {
            defaults = new java.util.Properties();
            defaults.setProperty("useSSL", "false");
        } else {
            urlToUse = Databases.removeParametersFromJdbcUrl(urlToUse);
        }

        if (user != null) {
            defaults.put("user", user);
        }
        if (password != null) {
            defaults.put("password", password);
        }

        return DriverManager.getConnection(urlToUse, defaults);
    }

    private static final Pattern pattern = Pattern.compile("[\\?\\&]([\\p{ASCII}&&[^=\\&]]*)=([\\p{ASCII}&&[^=\\&]]*)");

    public Connection getSimpleSQLConnectionWithoutTimeout(String url, String user, String password, String driver) throws SQLException, ClassNotFoundException {
        Class.forName(driver);
        DriverManager.setLoginTimeout(120);

        JdbcProperties jdbcProperties = AdminServiceRegistry.getInstance().getService(JdbcProperties.class);

        String urlToUse = url;
        Properties defaults = jdbcProperties.getJdbcPropertiesCopy();
        if (null == defaults) {
            defaults = new Properties();
            defaults.setProperty("useSSL", "false");

            int paramStart = urlToUse.indexOf('?');
            if (paramStart >= 0) {
                Matcher matcher = pattern.matcher(urlToUse);
                urlToUse = urlToUse.substring(0, paramStart);
                while (matcher.find()) {
                    String name = matcher.group(1);
                    String value = matcher.group(2);
                    if (name != null && name.length() > 0 && value != null && value.length() > 0 && !Strings.asciiLowerCase(name).endsWith("timeout")) {
                        try {
                            defaults.put(name, URLDecoder.decode(value, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            // Should not happen for UTF-8.
                            log.error("", e);
                        }
                    }
                }
            }
        } else {
            urlToUse = Databases.removeParametersFromJdbcUrl(urlToUse);

            for (Iterator<Entry<Object, Object>> it = defaults.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Object, Object> property = it.next();
                String name = property.getKey().toString();
                String value = property.getValue().toString();
                if (Strings.isEmpty(name) || Strings.isEmpty(value) || Strings.asciiLowerCase(name).endsWith("timeout")) {
                    it.remove();
                }
            }
        }

        defaults.put("user", user);
        defaults.put("password", password);

        return DriverManager.getConnection(urlToUse, defaults);
    }

    public void closeSimpleConnection(Connection con) {
        if (con == null) {
            return;
        }
        try {
            con.close();
        } catch (Exception ecp) {
            log.warn("Error closing simple CONNECTION!", ecp);
        }
    }

    /**
     * Parses the truncated fields out of the DataTruncation exception and
     * transforms this to a StorageException.
     */
    public final static StorageException parseDataTruncation(DataTruncation dt) {
        String[] fields = DBUtils.parseTruncatedFields(dt);
        StringBuilder sFields = new StringBuilder();
        sFields.append("Data too long for underlying storage!Error field(s): ");
        for (String field : fields) {
            sFields.append(field);
            sFields.append(",");
        }
        sFields.deleteCharAt(sFields.length() - 1);

        StorageException st = new StorageException(sFields.toString());
        st.setStackTrace(dt.getStackTrace());
        return st;
    }

    /**
     * Encrypts password of a {@link PasswordMechObject} using the mechanism given in <code>passwordMech</code>.
     * If <code>passwordMech</code> is <code>null</code>, the default mechanism as configured
     * in <code>User.properties</code> is used and set in the {@link PasswordMechObject} instance.
     *
     * @param user The {@link PasswordMechObject}
     * @return the encrypted password
     * @throws StorageException If encoding fails
     */
    public PasswordDetails encryptPassword(PasswordMechObject user) throws StorageException {
        PasswordMech passwordMech = getPasswordMechanism(user);
        try {
            return passwordMech.encode(user.getPassword());
        } catch (OXException e) {
            throw StorageException.wrapForRMI("Unable to encode password.", e);
        }
    }

    /**
     * Gets the password mechanism.
     *
     * @param user The {@link PasswordMechObject}
     * @return The password mechanism. If the {@link PasswordMechObject} contains an unknown password mechanism, then
     *         the {@link PasswordMechRegistry#getDefault()} mechanism is returned.
     * @throws StorageException If password mechanism is unknown
     */
    public PasswordMech getPasswordMechanism(PasswordMechObject user) throws StorageException {
        String passwordMechIdentifier = user.getPasswordMech();
        if (Strings.isEmpty(passwordMechIdentifier) || "null".equals(Strings.toLowerCase(passwordMechIdentifier))) {
            passwordMechIdentifier = getProperties().getUserProp(AdminProperties.User.DEFAULT_PASSWORD_MECHANISM, "{SHA-256}");
        }

        try {
            PasswordMechRegistry mechRegistry = AdminServiceRegistry.getInstance().getService(PasswordMechRegistry.class, true);
            PasswordMech passwordMech = mechRegistry.get(passwordMechIdentifier);
            if (passwordMech == null) {
                passwordMech = mechRegistry.getDefault();
                log.warn("Unknown password mechanism defined: '{}'. Will use default: '{}'", passwordMechIdentifier, passwordMech.getIdentifier());
            }
            user.setPasswordMech(passwordMech.getIdentifier());
            return passwordMech;
        } catch (OXException e) {
            log.error("Unable to get password mechanism.", e);
            throw StorageException.wrapForRMI("Unable to get password mechanism.", e);
        }
    }

    /**
     * Configures the authentication mechanisms
     */
    private void configureAuthentication() {
        log.debug("Configuring authentication mechanisms ...");

        String master_auth_disabled = this.prop.getProp("MASTER_AUTHENTICATION_DISABLED", "false"); // fallback is auth
        String context_auth_disabled = this.prop.getProp("CONTEXT_AUTHENTICATION_DISABLED", "false"); // fallback is auth

        masterAuthenticationDisabled = Boolean.parseBoolean(master_auth_disabled);
        log.debug("MasterAuthentication mechanism disabled: {}", Boolean.valueOf(masterAuthenticationDisabled));

        contextAuthenticationDisabled = Boolean.parseBoolean(context_auth_disabled);
        log.debug("ContextAuthentication mechanism disabled: {}", Boolean.valueOf(contextAuthenticationDisabled));

        allowMasterOverride = Boolean.parseBoolean(this.prop.getProp("MASTER_ACCOUNT_OVERRIDE", "false"));
        log.debug("Master override: {}", Boolean.valueOf(allowMasterOverride));
    }

    private void readAndSetMasterCredentials(ConfigurationService service) throws OXGenericException {
        Credentials masterCredentials = readMasterCredentials(service);
        if (null == masterCredentials) {
            OXGenericException genericException = new OXGenericException("No master credentials defined!");
            log.warn("", genericException);
        } else {
            this.masterCredentials = masterCredentials;
            log.debug("Master credentials successfully set!");
        }
    }

    /**
     * Reads the master credentials
     *
     * @param service The {@link ConfigurationService} from which to read the master credentials file
     * @return The master {@link Credentials} or <code>null</code> if the file is not found
     * @throws OXGenericException if an invalid mpasswd format is detected
     */
    private Credentials readMasterCredentials(ConfigurationService service) throws OXGenericException {
        File file = service.getFileByName("mpasswd");
        if (null == file) {
            return null;
        }

        try (BufferedReader bf = new BufferedReader(new FileReader(file), 2048)) {
            String line = null;
            while ((line = bf.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                if (line.indexOf(':') < 0) {
                    continue;
                }
                // Ok seems to be a line with user:pass entry
                String[] user_pass_combination = line.split(":");
                if (user_pass_combination.length < 3) {
                    throw new OXGenericException("Invalid mpasswd format.");
                }
                Credentials masterCredentials = new Credentials(user_pass_combination[0], user_pass_combination[2]);
                masterCredentials.setPasswordMech(user_pass_combination[1]);
                if (user_pass_combination.length == 4) {
                    String salt = user_pass_combination[3];
                    if (Strings.isNotEmpty(salt)) {
                        masterCredentials.setSalt(Base64.getUrlDecoder().decode(salt));
                    }
                }
                return masterCredentials;
            }
        } catch (IOException e) {
            throw new OXGenericException("Error processing master auth file: mpasswd", e);
        }
        return null;
    }

    @Deprecated
    public void closeConfigDBSqlStuff(Connection con, PreparedStatement stmt, ResultSet rs) {
        if (null != rs) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("Error closing resultset", e);
            }
        }
        closeConfigDBSqlStuff(con, stmt);
    }

    public void closeConfigDBSqlStuff(PreparedStatement stmt, ResultSet rs) {
        if (null != rs) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("Error closing resultset", e);
            }
        }
        closeReadConfigDBSqlStuff(null, stmt);
    }

    public void closeReadConfigDBSqlStuff(Connection con, PreparedStatement stmt, ResultSet rs) {
        if (null != rs) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("Error closing resultset", e);
            }
        }
        closeReadConfigDBSqlStuff(con, stmt);
    }

    public void closeWriteConfigDBSqlStuff(Connection con, PreparedStatement stmt, ResultSet rs) {
        if (null != rs) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("Error closing resultset", e);
            }
        }
        closeWriteConfigDBSqlStuff(con, stmt);
    }

    @Deprecated
    public void closeConfigDBSqlStuff(Connection con, PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                log.error("Error closing statement", e);
            }
        }
        if (con != null) {
            try {
                pushConnectionForConfigDB(con);
            } catch (PoolException exp) {
                log.error("Pool Error pushing connection to pool!", exp);
            }
        }
    }

    public void closeReadConfigDBSqlStuff(Connection con, PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                log.error("Error closing statement", e);
            }
        }
        if (con != null) {
            try {
                pushReadConnectionForConfigDB(con);
            } catch (PoolException exp) {
                log.error("Pool Error pushing connection to pool!", exp);
            }
        }
    }

    public void closeWriteConfigDBSqlStuff(Connection con, PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                log.error("Error closing statement", e);
            }
        }
        if (con != null) {
            try {
                pushWriteConnectionForConfigDB(con);
            } catch (PoolException exp) {
                log.error("Pool Error pushing connection to pool!", exp);
            }
        }
    }

    public void closeContextSqlStuff(Connection con, int context_id) {
        closeContextSqlStuff(con, context_id, false);
    }

    public void closeContextSqlStuff(Connection con, int context_id, boolean afterReading) {
        if (con != null) {
            try {
                if (afterReading) {
                    pushConnectionForContextAfterReading(context_id, con);
                } else {
                    pushConnectionForContext(context_id, con);
                }
            } catch (PoolException exp) {
                log.error("Pool Error pushing connection to pool!", exp);
            }
        }
    }

    public boolean isMasterAdmin(Credentials auth) {
        return isMasterAdmin(auth, true);
    }

    public boolean isMasterAdmin(Credentials auth, boolean considerMasterAuthenticationDisabled) {
        if (considerMasterAuthenticationDisabled && masterAuthenticationDisabled) {
            // Considered as master since master authentication is disabled.
            return true;
        }

        Credentials masterCredentials = getMasterCredentials();
        return masterCredentials != null && masterCredentials.getLogin().equals(auth.getLogin());
    }

    /**
     * Reloads the master {@link Credentials} from the specified {@link ConfigurationService}
     *
     * @param configService the {@link ConfigurationService}
     * @throws OXGenericException if no master credentials are defined
     */
    public void reloadMasterCredentials(ConfigurationService configService) throws OXGenericException {
        Credentials credentials = readMasterCredentials(configService);
        if (this.masterCredentials == null) {
            if (credentials != null) {
                this.masterCredentials = credentials;
                log.debug("Master credentials successfully set!");
            }
        } else if (!this.masterCredentials.equals(credentials)) {
            this.masterCredentials = credentials;
            if (null == this.masterCredentials) {
                OXGenericException genericException = new OXGenericException("No master credentials defined!");
                log.warn("", genericException);
            } else {
                log.debug("Master credentials successfully set!");
            }
        }
    }

    public boolean isAllowMasterOverride() {
        return allowMasterOverride;
    }
}
