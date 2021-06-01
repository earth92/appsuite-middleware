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

package com.openexchange.imap.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.mail.MessagingException;
import javax.mail.internet.idn.IDNA;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import com.openexchange.exception.OXException;
import com.openexchange.imap.IMAPCapabilities;
import com.openexchange.imap.IMAPException;
import com.openexchange.imap.acl.ACLExtension;
import com.openexchange.imap.acl.ACLExtensionFactory;
import com.openexchange.imap.cache.CapabilitiesCache;
import com.openexchange.imap.cache.CapabilitiesCache.CapabilitiesResponse;
import com.openexchange.mail.api.IMailProperties;
import com.openexchange.mail.api.MailCapabilities;
import com.openexchange.mail.api.MailConfig;
import com.openexchange.mail.api.UrlInfo;
import com.openexchange.mail.config.MailConfigException;
import com.openexchange.session.Session;
import com.openexchange.tools.net.URIDefaults;
import com.openexchange.tools.net.URIParser;
import com.sun.mail.imap.IMAPStore;

/**
 * {@link IMAPConfig} - The IMAP configuration.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class IMAPConfig extends MailConfig {

    private static final String PROTOCOL_IMAP_SECURE = "imaps";

    // private final int accountId;

    private volatile IMAPCapabilities imapCapabilities;

    private volatile Map<String, String> capabilities;

    private volatile ACLExtension aclExtension;

    private int imapPort;

    private String imapServer;

    private boolean secure;

    private Boolean acl;

    private IIMAPProperties mailProperties;

    private InetAddress imapServerAddress;

    private InetSocketAddress imapServerSocketAddress;

    private IMAPStore imapStore;

    private final Map<String, Object> params;

    /**
     * Default constructor
     *
     * @param accountId The account identifier
     */
    public IMAPConfig(int accountId) {
        super();
        this.accountId = accountId;
        params = new NonBlockingHashMap<String, Object>(4);
    }

    /**
     * Gets the optional IMAP store.
     *
     * @return The IMAP store
     */
    public IMAPStore optImapStore() {
        return imapStore;
    }

    /**
     * Drops the IMAP store reference.
     */
    public void dropImapStore() {
        imapStore = null;
    }

    /**
     * Sets specified parameter. If value is <code>null</code> a remove is performed.
     *
     * @param name The name
     * @param value The value
     */
    public void setParameter(String name, Object value) {
        if (null == value) {
            params.remove(name);
        } else {
            params.put(name, value);
        }
    }

    /**
     * Gets the named parameter.
     *
     * @param name The name
     * @param clazz The parameter's type
     * @return The value
     */
    public <V> V getParameter(String name, Class<? extends V> clazz) {
        return clazz.cast(params.get(name));
    }

    @Override
    public MailCapabilities getCapabilities() {
        final IMAPCapabilities capabilities = imapCapabilities;
        return capabilities == null ? MailCapabilities.EMPTY_CAPS : capabilities;
    }

    /**
     * Gets the IMAP capabilities.
     *
     * @return The IMAP capabilities
     */
    public IMAPCapabilities getImapCapabilities() {
        return imapCapabilities;
    }

    /**
     * Gets the (unmodifiable) map view of the IMAP capabilities.
     *
     * @return The (unmodifiable) map containing IMAP capabilities
     */
    public Map<String, String> asMap() {
        return capabilities == null ? Collections.<String, String> emptyMap() : capabilities;
    }

    /**
     * Gets the ACL extension appropriate for this IMAP configuration.
     *
     * @return The ACL extension
     */
    public ACLExtension getACLExtension() {
        if (null == aclExtension) {
            synchronized (this) {
                if (null == aclExtension) {
                    aclExtension = ACLExtensionFactory.getInstance().getACLExtension(this);
                }
            }
        }
        return aclExtension;
    }

    /**
     * Gets the imapPort
     *
     * @return the imapPort
     */
    @Override
    public int getPort() {
        return imapPort;
    }

    @Override
    public void setPort(int imapPort) {
        this.imapPort = imapPort;
    }

    /**
     * Gets the imapServer
     *
     * @return the imapServer
     */
    @Override
    public String getServer() {
        return imapServer;
    }

    @Override
    public void setServer(String imapServer) {
        this.imapServer = null == imapServer ? null : IDNA.toUnicode(imapServer);
    }

    /**
     * Initializes IMAP server's capabilities if not done, yet
     *
     * @param imapStore The IMAP store from which to fetch the capabilities
     * @param session The session possibly caching capabilities information
     * @throws OXException If IMAP capabilities cannot be initialized
     */
    @SuppressWarnings("unchecked")
    public void initializeCapabilities(IMAPStore imapStore, Session session) throws OXException {
        if (imapCapabilities == null) {
            synchronized (this) {
                this.imapStore = imapStore;
                if (imapCapabilities != null) {
                    return;
                }
                try {
                    final CapabilitiesResponse response = CapabilitiesCache.getCapabilitiesResponse(imapStore, this, session, accountId);

                    IMAPCapabilities imapCaps = response.getImapCapabilities();
                    Map<String, String> caps = imapStore.getCapabilities();

                    BoolCapVal supportsACLs = IMAPProperties.getInstance().getSupportsACLs();
                    if (BoolCapVal.FALSE.equals(supportsACLs)) {
                        imapCaps.setACL(false);
                        caps = new HashMap<String, String>(caps);
                        caps.remove("ACL");
                        caps = Collections.unmodifiableMap(caps);
                    } else if (BoolCapVal.TRUE.equals(supportsACLs)) {
                        imapCaps.setACL(true);
                        caps = new HashMap<String, String>(caps);
                        caps.put("ACL", "ACL");
                        caps = Collections.unmodifiableMap(caps);
                    }

                    imapCapabilities = imapCaps;
                    capabilities = caps;
                    aclExtension = response.getAclExtension();
                } catch (MessagingException e) {
                    throw MailConfigException.create(e);
                }
            }
        }
    }

    /**
     * Checks if IMAP search is configured and corresponding capability is available.
     *
     * @return <code>true</code> if IMAP search is configured and corresponding capability is available; otherwise <code>false</code>
     */
    public boolean isImapSearch() {
        final boolean imapSearch = IMAPProperties.getInstance().isImapSearch();
        final IMAPCapabilities capabilities = imapCapabilities;
        return (capabilities != null) ? (imapSearch && (capabilities.hasIMAP4rev1() || capabilities.hasIMAP4())) : imapSearch;
    }

    /**
     * Checks if IMAP search is configured to be forced and corresponding capability is available.
     *
     * @return <code>true</code> if IMAP search is configured to be forced and corresponding capability is available; otherwise <code>false</code>
     */
    public boolean forceImapSearch() {
        final boolean forceImapSearch = IMAPProperties.getInstance().forceImapSearch();
        final IMAPCapabilities capabilities = imapCapabilities;
        return (capabilities != null) ? (forceImapSearch && (capabilities.hasIMAP4rev1() || capabilities.hasIMAP4())) : forceImapSearch;
    }

    /**
     * Checks if IMAP sort is configured and corresponding capability is available.
     *
     * @return <code>true</code> if IMAP sort is configured and corresponding capability is available; otherwise <code>false</code>
     */
    public boolean isImapSort() {
        final boolean imapSort = IMAPProperties.getInstance().isImapSort();
        final IMAPCapabilities capabilities = imapCapabilities;
        return (capabilities != null) ? (imapSort && capabilities.hasSort()) : imapSort;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    /**
     * Sets the ACL flag.
     *
     * @param acl <code>true</code> if ACL extension is supported; otherwise <code>false</code>
     */
    public void setAcl(boolean acl) {
        this.acl = Boolean.valueOf(acl);
    }

    /**
     * Checks if ACLs are supported
     *
     * @return <code>true</code> if ACLs are supported; otherwise <code>false</code>
     */
    public boolean isSupportsACLs() {
        final Boolean acl = this.acl;
        if (null != acl) {
            return acl.booleanValue();
        }
        final BoolCapVal supportsACLs = IMAPProperties.getInstance().getSupportsACLs();
        final Map<String, String> capabilities = this.capabilities;
        if (capabilities != null && BoolCapVal.AUTO.equals(supportsACLs)) {
            return capabilities.containsKey("ACL");
        }
        return BoolCapVal.TRUE.equals(supportsACLs) ? true : false;
    }

    @Override
    protected void parseServerURL(UrlInfo urlInfo) throws OXException {
        final URI uri;
        try {
            uri = URIParser.parse(urlInfo.getServerURL(), URIDefaults.IMAP);
        } catch (URISyntaxException e) {
            throw IMAPException.Code.URI_PARSE_FAILED.create(e, urlInfo.getServerURL());
        }
        secure = PROTOCOL_IMAP_SECURE.equals(uri.getScheme());
        imapServer = uri.getHost();
        imapPort = uri.getPort();
        requireTls = urlInfo.isRequireStartTls();
    }

    /**
     * Gets the Internet Protocol (IP) address of the IMAP server.
     *
     * @return The Internet Protocol (IP) address of the IMAP server.
     * @throws OXException If IMAP server cannot be resolved
     */
    public InetAddress getImapServerAddress() throws OXException {
        if (null == imapServerAddress) {
            try {
                imapServerAddress = InetAddress.getByName(imapServer);
                // TODO: Touch address for proper equality check?
                // imapServerAddress.toString();
            } catch (UnknownHostException e) {
                throw IMAPException.Code.UNKNOWN_HOST_ERROR.create(e, e.getMessage());
            }
        }
        return imapServerAddress;
    }

    /**
     * Gets the socket address (internet address + port) of the IMAP server.
     *
     * @return The socket address (internet address + port) of the IMAP server.
     * @throws OXException If IMAP server cannot be resolved
     */
    public InetSocketAddress getImapServerSocketAddress() throws OXException {
        if (null == imapServerSocketAddress) {
            imapServerSocketAddress = new InetSocketAddress(getImapServerAddress(), imapPort);
        }
        return imapServerSocketAddress;
    }

    @Override
    public IMailProperties getMailProperties() {
        return mailProperties;
    }

    public IIMAPProperties getIMAPProperties() {
        return mailProperties;
    }

    @Override
    public void setMailProperties(IMailProperties mailProperties) {
        this.mailProperties = (IIMAPProperties) mailProperties;
    }

}
