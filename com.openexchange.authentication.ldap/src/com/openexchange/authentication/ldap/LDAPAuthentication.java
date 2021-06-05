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

package com.openexchange.authentication.ldap;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.security.auth.login.LoginException;
import com.openexchange.authentication.Authenticated;
import com.openexchange.authentication.AuthenticationService;
import com.openexchange.authentication.ContextAndUserInfo;
import com.openexchange.authentication.LoginExceptionCodes;
import com.openexchange.authentication.LoginInfo;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.DefaultInterests;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;

/**
 * This class implements the login by using an LDAP for authentication.
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public class LDAPAuthentication implements AuthenticationService, Reloadable {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LDAPAuthentication.class);

    private static final class AuthenticatedImpl implements Authenticated {

        private final String userInfo;
        private final String contextInfo;

        AuthenticatedImpl(String userInfo, String contextInfo) {
            super();
            this.userInfo = userInfo;
            this.contextInfo = contextInfo;
        }

        @Override
        public String getContextInfo() {
            return contextInfo;
        }

        @Override
        public String getUserInfo() {
            return userInfo;
        }
    }

    private enum PropertyNames {
        BASE_DN("baseDN"),
        UID_ATTRIBUTE("uidAttribute"),
        LDAP_RETURN_FIELD("ldapReturnField"),
        ADS_NAME_BIND("adsBind"),
        BIND_ONLY("bindOnly"),
        LDAP_SCOPE("ldapScope"),
        SEARCH_FILTER("searchFilter"),
        BIND_DN("bindDN"),
        BIND_DN_PASSWORD("bindDNPassword"),
        PROXY_USER("proxyUser"),
        PROXY_DELIMITER("proxyDelimiter"),
        REFERRAL("referral"),
        USE_FULL_LOGIN_INFO("useFullLoginInfo");

        public final String name;

        private PropertyNames(String name) {
            this.name = name;
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    /** The OSGi service look-up */
    private final ServiceLookup services;

    /** Reference to the properties for the JNDI context as well as the configuration for LDAP authentication */
    private final AtomicReference<PropertiesAndConfig> propsAndConfigReference;

    /**
     * Default constructor.
     * @throws LoginException if setup fails.
     */
    public LDAPAuthentication(Properties props, ServiceLookup services) throws OXException {
        super();
        this.services = services;
        this.propsAndConfigReference = new AtomicReference<PropertiesAndConfig>(init(props));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Authenticated handleLoginInfo(LoginInfo loginInfo) throws OXException {
        ContextAndUserInfo cau = split(loginInfo.getUsername());
        String uid = cau.getUserInfo();
        String password = loginInfo.getPassword();
        if (uid.length() == 0 || password.length() == 0) {
            throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
        }

        PropertiesAndConfig propertiesAndConfig = propsAndConfigReference.get();
        Properties props = propertiesAndConfig.props;
        Config config = propertiesAndConfig.config;

        LOG.debug("Using full login info: {}", loginInfo.getUsername());
        String userInfoFromLdap = bind(config.useFullLoginInfo ? loginInfo.getUsername() : uid, password, props, config);
        LOG.info("User {} successfully authenticated.", config.useFullLoginInfo ? loginInfo.getUsername() : uid);
        return new AuthenticatedImpl(null == userInfoFromLdap ? cau.getUserInfo() : userInfoFromLdap, cau.getContextInfo());
    }

    @Override
    public Authenticated handleAutoLoginInfo(LoginInfo loginInfo) throws OXException {
        throw LoginExceptionCodes.NOT_SUPPORTED.create(LDAPAuthentication.class.getName());
    }

    /**
     * Tries to bind.
     * @param uid login name.
     * @param password password.
     * @throws LoginException if some problem occurs.
     */
    private String bind(String uid, String password, Properties props, Config config) throws OXException {
        String proxyAs = null;
        String uidToUse = uid;
        if (config.proxyUser != null && config.proxyDelimiter != null && uidToUse.contains(config.proxyDelimiter)) {
            proxyAs = uidToUse.substring(uidToUse.indexOf(config.proxyDelimiter) + config.proxyDelimiter.length(), uidToUse.length());
            uidToUse = uidToUse.substring(0, uidToUse.indexOf(config.proxyDelimiter));
            boolean foundProxy = false;
            for (String pu : Strings.splitByComma(config.proxyUser)) {
                if (pu.trim().equalsIgnoreCase(uidToUse)) {
                    foundProxy = true;
                }
            }
            if (!foundProxy) {
                LOG.error("none of the proxy user is matching");
                throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
            }
        }

        LdapContext context = null;
        String dn = null;
        try {
            String baseDN = config.baseDN;
            String uidAttribute = config.uidAttribute;

            String samAccountName = null;
            if (config.bindOnly) {
                // Whether or not to use the samAccountName search
                if (config.adsbind) {
                    int index = uidToUse.indexOf("\\");
                    if (index >= 0) {
                        samAccountName = uidToUse.substring(index + 1);
                    }
                    dn = uidToUse;
                } else {
                    dn = uidAttribute + '=' + uidToUse + ',' + baseDN;
                }
            } else {
                // get user dn from user
                final Properties aprops = (Properties) props.clone();
                aprops.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                String bindDN = config.bindDN;
                if (bindDN != null && bindDN.length() > 0) {
                    LOG.debug("Using bindDN={}", bindDN);
                    aprops.put(Context.SECURITY_PRINCIPAL, bindDN);
                    aprops.put(Context.SECURITY_CREDENTIALS, config.bindDNPassword);
                } else {
                    aprops.put(Context.SECURITY_AUTHENTICATION, "none");
                }
                context = new InitialLdapContext(aprops, null);
                final String filter = "(&" + config.searchFilter + "(" + uidAttribute + "=" + uidToUse + "))";
                LOG.debug("Using filter={}", filter);
                LOG.debug("BaseDN      ={}", baseDN);
                SearchControls cons = new SearchControls();
                cons.setSearchScope(config.searchScope);
                cons.setCountLimit(0);
                cons.setReturningAttributes(new String[] { "dn" });
                NamingEnumeration<SearchResult> res = null;
                try {
                    res = context.search(baseDN, filter, cons);
                    if (!res.hasMoreElements()) {
                        final String errortext = "No user found with " + uidAttribute + "=" + uidToUse;
                        LOG.error(errortext);
                        throw LoginExceptionCodes.INVALID_CREDENTIALS_MISSING_USER_MAPPING.create(uidToUse);
                    }

                    dn = res.nextElement().getNameInNamespace();
                    if (res.hasMoreElements()) {
                        final String errortext = "Found more than one user with " + uidAttribute + "=" + uidToUse;
                        LOG.error(errortext);
                        throw LoginExceptionCodes.INVALID_CREDENTIALS.create();
                    }
                } finally {
                    close(res);
                }
                context.close();
            }

            context = new InitialLdapContext(props, null);
            context.addToEnvironment(Context.REFERRAL, config.referral);
            context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
            context.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
            context.reconnect(null);

            String ldapReturnField = config.ldapReturnField;
            if (null == ldapReturnField || ldapReturnField.length() <= 0) {
                return null;
            }

            Attributes userDnAttributes;
            String puser = null;
            if (config.adsbind) {
                final SearchControls searchControls = new SearchControls();
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                searchControls.setCountLimit(0);
                searchControls.setReturningAttributes(new String[] { ldapReturnField });
                NamingEnumeration<SearchResult> search = null;
                NamingEnumeration<SearchResult> searchProxy = null;
                try {
                    if (null == samAccountName) {
                        if (proxyAs != null) {
                            search = context.search(baseDN, "(displayName=" + uidToUse + ")", searchControls);
                            searchProxy = context.search(baseDN, "(displayName=" + proxyAs + ")", searchControls);
                        } else {
                            search = context.search(baseDN, "(displayName=" + uidToUse + ")", searchControls);
                        }
                    } else {
                        search = context.search(baseDN, "(sAMAccountName=" + samAccountName + ")", searchControls);
                    }
                    if (null == search || !search.hasMoreElements()) {
                        LOG.error("No user with displayname {} found.", uidToUse);
                        throw LoginExceptionCodes.INVALID_CREDENTIALS_MISSING_USER_MAPPING.create(uidToUse);
                    }

                    SearchResult next = search.next();
                    userDnAttributes = next.getAttributes();
                    if (proxyAs != null && searchProxy != null) {
                        puser = (String) searchProxy.next().getAttributes().get(ldapReturnField).get();
                    }
                } finally {
                    close(search);
                    close(searchProxy);
                }
            } else {
                userDnAttributes = context.getAttributes(dn);
            }
            String attribute = (String) userDnAttributes.get(ldapReturnField).get();
            return proxyAs == null ? attribute : attribute + config.proxyDelimiter + puser;
        } catch (InvalidNameException e) {
            LOG.debug("Login failed for dn {}:", dn, e);
            throw LoginExceptionCodes.INVALID_CREDENTIALS.create(e);
        } catch (AuthenticationException e) {
            LOG.debug("Login failed for dn {}:", dn, e);
            throw LoginExceptionCodes.INVALID_CREDENTIALS.create(e);
        } catch (NamingException e) {
            LOG.error("", e);
            throw LoginExceptionCodes.COMMUNICATION.create(e);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    LOG.error("", e);
                }
            }
        }
    }

    /**
     * Initializes the properties and configuration for the ldap authentication.
     *
     * @return The properties for the JNDI context as well as the configuration for LDAP authentication to use
     * @throws LoginException If configuration fails.
     */
    private PropertiesAndConfig init(Properties props) throws OXException {
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        Config.Builder configBuilder = Config.builder();

        if (!props.containsKey(PropertyNames.UID_ATTRIBUTE.name)) {
            throw LoginExceptionCodes.MISSING_PROPERTY.create(PropertyNames.UID_ATTRIBUTE.name);
        }
        String uidAttribute = props.getProperty(PropertyNames.UID_ATTRIBUTE.name);
        configBuilder.withUidAttribute(uidAttribute);

        if (!props.containsKey(PropertyNames.BASE_DN.name)) {
            throw LoginExceptionCodes.MISSING_PROPERTY.create(PropertyNames.BASE_DN.name);
        }
        String baseDN = props.getProperty(PropertyNames.BASE_DN.name);
        configBuilder.withBaseDN(baseDN);

        final String url = props.getProperty(Context.PROVIDER_URL);
        if (null == url) {
            throw LoginExceptionCodes.MISSING_PROPERTY.create(Context.PROVIDER_URL);
        } else if (url.startsWith("ldaps")) {
            SSLSocketFactoryProvider factoryProvider = services.getOptionalService(SSLSocketFactoryProvider.class);
            if (null == factoryProvider) {
                throw ServiceExceptionCode.absentService(SSLSocketFactoryProvider.class);
            }
            props.put("java.naming.ldap.factory.socket", factoryProvider.getDefault().getClass().getName());
        }

        String ldapReturnField = props.getProperty(PropertyNames.LDAP_RETURN_FIELD.name);
        configBuilder.withLdapReturnField(ldapReturnField);

        boolean adsbind = Boolean.parseBoolean(props.getProperty(PropertyNames.ADS_NAME_BIND.name));
        configBuilder.withAdsbind(adsbind);

        if (!props.containsKey(PropertyNames.BIND_ONLY.name)) {
            throw LoginExceptionCodes.MISSING_PROPERTY.create(PropertyNames.BIND_ONLY.name);
        }
        boolean bindOnly = Boolean.parseBoolean(props.getProperty(PropertyNames.BIND_ONLY.name));
        configBuilder.withBindOnly(bindOnly);

        if (!props.containsKey(PropertyNames.LDAP_SCOPE.name)) {
            throw LoginExceptionCodes.MISSING_PROPERTY.create(PropertyNames.LDAP_SCOPE.name);
        }
        String ldapScope = props.getProperty(PropertyNames.LDAP_SCOPE.name);
        configBuilder.withLdapScope(ldapScope);

        int searchScope = SearchControls.SUBTREE_SCOPE;
        if ("subtree".equals(ldapScope)) {
            searchScope = SearchControls.SUBTREE_SCOPE;
        } else if ("onelevel".equals(ldapScope)) {
            searchScope = SearchControls.ONELEVEL_SCOPE;
        } else if ("base".equals(ldapScope)) {
            searchScope = SearchControls.OBJECT_SCOPE;
        } else {
            throw LoginExceptionCodes.UNKNOWN.create(PropertyNames.LDAP_SCOPE.name + " must be one of subtree, onelevel or base");
        }
        configBuilder.withSearchScope(searchScope);

        if (!props.containsKey(PropertyNames.SEARCH_FILTER.name)) {
            throw LoginExceptionCodes.MISSING_PROPERTY.create(PropertyNames.SEARCH_FILTER.name);
        }
        String searchFilter = props.getProperty(PropertyNames.SEARCH_FILTER.name);
        configBuilder.withSearchFilter(searchFilter);

        String bindDN = props.getProperty(PropertyNames.BIND_DN.name);
        configBuilder.withBindDN(bindDN);
        String bindDNPassword = props.getProperty(PropertyNames.BIND_DN_PASSWORD.name);
        configBuilder.withBindDNPassword(bindDNPassword);

        if (props.containsKey(PropertyNames.PROXY_USER.name)) {
            String proxyUser = props.getProperty(PropertyNames.PROXY_USER.name);
            configBuilder.withProxyUser(proxyUser);
        }

        if (props.containsKey(PropertyNames.PROXY_DELIMITER.name)) {
            String proxyDelimiter = props.getProperty(PropertyNames.PROXY_DELIMITER.name);
            configBuilder.withProxyDelimiter(proxyDelimiter);
        }

        if (!props.containsKey(PropertyNames.REFERRAL.name)) {
            throw LoginExceptionCodes.MISSING_PROPERTY.create(PropertyNames.REFERRAL.name);
        }
        String referral = props.getProperty(PropertyNames.REFERRAL.name);
        configBuilder.withReferral(referral);

        boolean useFullLoginInfo = Boolean.parseBoolean(props.getProperty(PropertyNames.USE_FULL_LOGIN_INFO.name));
        configBuilder.withUseFullLoginInfo(useFullLoginInfo);

        return new PropertiesAndConfig(props, configBuilder.build());
    }

    /**
     * Splits user name and context.
     *
     * @param loginInfo combined information separated by an <code>"@"</code> sign.
     * @return The context and user name.
     * @throws LoginException if no separator is found.
     */
    private ContextAndUserInfo split(String loginInfo) {
        int pos = loginInfo.lastIndexOf('@');
        return pos < 0 ? new ContextAndUserInfo(loginInfo) : new ContextAndUserInfo(loginInfo.substring(0, pos), loginInfo.substring(pos + 1));
    }

    @Override
    public void reloadConfiguration(ConfigurationService configService) {
        try {
            Properties properties = configService.getFile("ldapauth.properties");
            propsAndConfigReference.set(init(properties));
        } catch (Exception e) {
            LOG.error("Error reloading configuration for bundle com.openexchange.authentication.ldap: {}", e);
        }
    }

    @Override
    public Interests getInterests() {
        return DefaultInterests.builder().configFileNames("ldapauth.properties").build();
    }

    /**
     * Closes the supplied naming enumeration, swallowing a possible {@link NamingException}.
     *
     * @param namingEnumeration The naming operation to close, or <code>null</code> to do nothing for convenience
     */
    private static void close(NamingEnumeration<?> namingEnumeration) {
        if (null != namingEnumeration) {
            try {
                namingEnumeration.close();
            } catch (NamingException e) {
                LOG.warn("Error closing naming enumeration", e);
            }
        }
    }

}
