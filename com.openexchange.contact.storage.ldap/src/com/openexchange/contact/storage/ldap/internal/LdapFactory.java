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

package com.openexchange.contact.storage.ldap.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.SortKey;
import com.openexchange.contact.storage.ldap.LdapExceptionCodes;
import com.openexchange.contact.storage.ldap.config.LdapConfig;
import com.openexchange.contact.storage.ldap.config.LdapConfig.AuthType;
import com.openexchange.contact.storage.ldap.config.LdapConfig.SearchScope;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.openexchange.session.Session;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link LdapFactory}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class LdapFactory  {

    private final LdapConfig config;
    private Hashtable<String, String> environment = null;
    private final Map<String, String> userDNs = new ConcurrentHashMap<String, String>();

    public LdapFactory(LdapConfig config) {
        super();
        this.config = config;
    }

    /**
     * Creates a LDAP context.
     *
     * @param session
     * @return
     * @throws OXException
     */
    public LdapContext createContext(Session session) throws OXException {
        return createContext(session, config.getAuthtype());
    }

    /**
     * Constructs an array of request results from the supplied values.
     *
     * @param sortKeys
     * @param cookie
     * @param deleted
     * @return
     * @throws IOException
     */
    public Control[] createRequestControls(SortKey[] sortKeys, byte[] cookie, boolean deleted) throws OXException {
        List<Control> controls = new ArrayList<Control>();
        if (deleted) {
            controls.add(new DeletedControl());
        }
        try {
            if (0 < config.getPagesize()) {
                controls.add(new PagedResultsControl(config.getPagesize(), cookie, Control.CRITICAL));
            }
            if (null != sortKeys && 0 < sortKeys.length) {
//                controls.add(new SortControl(sortKeys, Control.NONCRITICAL));
                controls.add(new SortControl(sortKeys, Control.CRITICAL));
            }
        } catch (IOException e) {
            throw LdapExceptionCodes.LDAP_ERROR.create(e, e.getMessage());
        }
        return controls.toArray(new Control[controls.size()]);
    }

    /**
     * Constructs search controls from the supplied values.
     *
     * @param attributeNames
     * @param limit
     * @return
     */
    public SearchControls createSearchControls(String[] attributeNames, int limit) {
        return createSearchControls(config.getSearchScope(), attributeNames, limit);
    }

    public SearchControls createSearchControls(SearchScope scope, String[] attributeNames, int limit) {
        SearchControls searchControls = new SearchControls();
        if (null != scope) {
            searchControls.setSearchScope(scope.getValue());
        }
        searchControls.setCountLimit(0 < limit ? limit : 0);
        searchControls.setReturningAttributes(attributeNames);
        return searchControls;
    }

    private Hashtable<String, String> getEnvironment() {
        if (null == this.environment) {
            environment = new Hashtable<String, String>();
            environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            environment.put(Context.PROVIDER_URL, config.getUri());
            if (config.isTrustAllCerts() && config.getUri().startsWith("ldaps://")) {
                SSLSocketFactoryProvider factoryProvider = LdapServiceLookup.get().getOptionalService(SSLSocketFactoryProvider.class);
                if (null == factoryProvider) {
                    throw new IllegalStateException("Missing " + SSLSocketFactoryProvider.class.getSimpleName() + " service. Bundle \"com.openexchange.net.ssl\" not started?");
                }
                environment.put("java.naming.ldap.factory.socket", factoryProvider.getDefault().getClass().getName());
            }
            if (null != config.getReferrals()) {
                environment.put(Context.REFERRAL, config.getReferrals().getValue());
            }
            if (null != config.getDerefAliases()) {
                environment.put("java.naming.ldap.derefAliases", config.getDerefAliases().getValue());
            }
            if (config.isConnectionPooling()) {
                environment.put("com.sun.jndi.ldap.connect.pool", "true");
                if (0 < config.getPooltimeout()) {
                    environment.put("com.sun.jndi.ldap.connect.pool.timeout", String.valueOf(config.getPooltimeout()));
                }
            } else {
                environment.put("com.sun.jndi.ldap.connect.pool", "false");
            }
        }
        return environment;
    }

    private LdapContext createContext(Session session, AuthType authType) throws OXException {
        InitialLdapContext ldapContext = null;
        try {
            ldapContext = new InitialLdapContext(getEnvironment(), null);
            LdapContext authenticatedLdapContext = authenticate(ldapContext, authType, session);
            ldapContext = null;
            return authenticatedLdapContext;
        } catch (NamingException e) {
            throw LdapExceptionCodes.INITIAL_LDAP_ERROR.create(e, e.getMessage());
        } finally {
            if (null != ldapContext) {
                try {
                    ldapContext.close();
                } catch (Exception e) {
                    // Ignore...
                }
            }
        }
    }

    private LdapContext authenticate(LdapContext context, AuthType authType, Session session) throws OXException, NamingException {
        switch (authType) {
        case ADMINDN:
            context.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
            context.addToEnvironment(Context.SECURITY_PRINCIPAL, config.getAdminDN());
            context.addToEnvironment(Context.SECURITY_CREDENTIALS, config.getAdminBindPW());
            break;
        case USER:
            if (null == session) {
                throw new IllegalArgumentException("Need valid session for AuthType 'USER'");
            }
            context.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
            context.addToEnvironment(Context.SECURITY_PRINCIPAL, getUserBindDN(getUserLoginSource(session)));
            context.addToEnvironment(Context.SECURITY_CREDENTIALS, session.getPassword());
            break;
        case ANONYMOUS:
            environment.put(Context.SECURITY_AUTHENTICATION, "none");
            break;
        default:
            throw new IllegalArgumentException("Unknown AuthType: " + authType);
        }
        return context;
    }

    private static User getUser(int contextID, int userID) throws OXException {
        return LdapServiceLookup.getService(UserService.class).getUser(userID,
            LdapServiceLookup.getService(ContextService.class).getContext(contextID));
    }

    private String getUserLoginSource(Session session) throws OXException {
        User user = getUser(session.getContextId(), session.getUserId());
        switch (config.getUserLoginSource()) {
        case LOGIN:
            String imapLogin = user.getImapLogin();
            if (null == imapLogin) {
                throw LdapExceptionCodes.IMAP_LOGIN_NULL.create(user.getLoginInfo());
            }
            return imapLogin;
        case MAIL:
            String mail = user.getMail();
            if (null == mail) {
                throw LdapExceptionCodes.PRIMARY_MAIL_NULL.create(user.getLoginInfo());
            }
            return mail;
        case NAME:
            return user.getLoginInfo();
        default:
            throw LdapExceptionCodes.WRONG_OR_MISSING_CONFIG_VALUE.create(config.getUserLoginSource());
        }
    }

    private String getUserBindDN(String username) throws NamingException, OXException {
        String userBindDN = this.userDNs.get(username);
        if (null == userBindDN) {
            userBindDN = searchUserBindDN(username);
            userDNs.put(username, userBindDN);
        }
        return userBindDN;
    }

    private String searchUserBindDN(String username) throws NamingException, OXException {
        LdapContext context = createContext(null, config.getUserAuthType());
        SearchScope searchScope = null != config.getUserSearchScope() ? config.getUserSearchScope() : config.getSearchScope();
        SearchControls searchControls = createSearchControls(searchScope, new String[] { "dn" }, 0);
        String searchFilter = null != config.getUserSearchFilter() ? config.getUserSearchFilter() : config.getSearchfilter();
        String filter = "(&" + searchFilter + "(" + config.getUserSearchAttribute() + "=" +
            Tools.escapeLDAPSearchFilter(username) + "))";
        String baseDN = null != config.getUserSearchBaseDN() ? config.getUserSearchBaseDN() : config.getBaseDN();
        NamingEnumeration<SearchResult> results = null;
        try {
            results = context.search(baseDN, filter, searchControls);
            if (null == results || false == results.hasMore()) {
                throw LdapExceptionCodes.NO_USER_RESULTS.create(username);
            }
            String userDN = results.next().getNameInNamespace();
            if (results.hasMore()) {
                throw LdapExceptionCodes.TOO_MANY_USER_RESULTS.create();
            }
            return userDN;
        } finally {
            Tools.close(results);
            Tools.close(context);
        }
    }

    /**
     * This class is used to be able to view deleted object in an Active Directory
     *
     * @author <a href="mailto:dennis.sieben@open-xchange.com">Dennis Sieben</a>
     */
    private static final class DeletedControl implements Control {

        /**
         * For serialization
         */
        private static final long serialVersionUID = -3548239536056697658L;

        protected DeletedControl() {
            super();
        }

        @Override
        public byte[] getEncodedValue() {
            return new byte[] {};
        }

        @Override
        public String getID() {
            return "1.2.840.113556.1.4.417";
        }

        @Override
        public boolean isCritical() {
            return true;
        }
    }

}
