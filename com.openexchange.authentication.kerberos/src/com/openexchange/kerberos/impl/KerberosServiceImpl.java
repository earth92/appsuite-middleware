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

package com.openexchange.kerberos.impl;

import java.security.PrivilegedActionException;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import com.openexchange.exception.OXException;
import com.openexchange.kerberos.ClientPrincipal;
import com.openexchange.kerberos.KerberosExceptionCodes;
import com.openexchange.kerberos.KerberosService;

/**
 * {@link KerberosServiceImpl}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public final class KerberosServiceImpl implements KerberosService {

    private static final GSSManager MANAGER = GSSManager.getInstance();
    private final String moduleName;
    private final String userModuleName;
    private Subject serviceSubject;
    private LoginContext lc;

    public KerberosServiceImpl(String moduleName, String userModuleName) {
        super();
        this.moduleName = moduleName;
        this.userModuleName = userModuleName;
    }

    /*
     * The LoginContext is kept for the whole uptime of the backend. If the encapsulated ticket times out, a new ticket is fetched
     * automatically.
     */

    public void login() throws OXException {
        try {
            lc = new LoginContext(moduleName);
            // login (effectively populating the Subject)
            lc.login();
            // get the Subject that represents the service
            serviceSubject = lc.getSubject();
        } catch (javax.security.auth.login.LoginException e) {
            throw KerberosExceptionCodes.LOGIN_FAILED.create(e, e.getMessage());
        }
    }

    public void logout() throws OXException {
        try {
            lc.logout();
        } catch (LoginException e) {
            throw KerberosExceptionCodes.LOGOUT_FAILED.create(e, e.getMessage());
        } finally {
            lc = null;
        }
    }

    /**
     * This is a try to just verify the client ticket. The delegation ticket should be fetched in a second step. But due to bad described
     * API we are not able to separate those steps. It is completely unclear how a {@link GSSContext} should be initialized to do certain
     * steps in communication with the Kerberos server.
     *
     * @param ticket
     * @return
     * @throws OXException
     */
    public ClientPrincipal verifyTicket(byte[] ticket) throws OXException {
        final ClientTicketVerifier decoder = new ClientTicketVerifier(MANAGER, ticket);
        final ClientPrincipal principal;
        try {
            principal = Subject.doAs(serviceSubject, decoder);
        } catch (PrivilegedActionException e) {
            final Exception nested = e.getException();
            if (nested instanceof GSSException) {
                final GSSException ge = (GSSException) nested;
                throw KerberosExceptionCodes.TICKET_WRONG.create(ge, ge.getMessage());
            }
            throw KerberosExceptionCodes.UNKNOWN.create(e, e.getMessage());
        }
        return principal;
    }

    @Override
    public ClientPrincipal verifyAndDelegate(byte[] ticket) throws OXException {
        if (ticket.length == 0) {
            throw KerberosExceptionCodes.TICKET_WRONG.create("Ticket was empty.");
        }

        final ForwardedTGTDelegateGenerator generator = new ForwardedTGTDelegateGenerator(MANAGER, ticket);
        final ClientPrincipal principal;
        try {
            principal = Subject.doAs(serviceSubject, generator);
        } catch (PrivilegedActionException e) {
            final Exception nested = e.getException();
            if (nested instanceof OXException) {
                throw (OXException) nested;
            }
            if (nested instanceof GSSException) {
                final GSSException ge = (GSSException) nested;
                throw KerberosExceptionCodes.TICKET_WRONG.create(ge, ge.getMessage());
            }
            throw KerberosExceptionCodes.UNKNOWN.create(e, e.getMessage());
        }
        return principal;
    }

    @Override
    public ClientPrincipal authenticate(String username, String password) throws OXException {
        final ClientPrincipalImpl principal;
        Subject mysubject = new Subject();
        LoginContext userLc;
        try {
            userLc = new LoginContext(userModuleName, mysubject, new KerberosCallbackHandler(username, password));
            userLc.login();
            principal = new ClientPrincipalImpl();
            principal.setClientSubject(userLc.getSubject());
            principal.setDelegateSubject(userLc.getSubject());
        } catch (LoginException e) {
            // If an exception is caused here, it's likely the ~/.java.login.config file is wrong
            throw KerberosExceptionCodes.UNKNOWN.create(e, e.getMessage());
        }
        return principal;
    }

    /**
     * This is a try to just renew the delegation ticket. Just the delegation ticket should be renewed. But due to bad described
     * API we are not able to separate those steps. It is completely unclear how a {@link GSSContext} should be initialized to do certain
     * steps in communication with the Kerberos server.
     *
     * @param ticket
     * @return
     * @throws OXException
     */
    @Override
    public ClientPrincipal renewDelegateTicket(Subject subject) throws OXException {
        Set<GSSCredential> credentials = subject.getPrivateCredentials(GSSCredential.class);
        for (GSSCredential credential : credentials) {
            final DelegateTicketRenewer renewer = new DelegateTicketRenewer(MANAGER, credential);
            try {
                return Subject.doAs(subject, renewer);
            } catch (PrivilegedActionException e) {
                final Exception nested = e.getException();
                if (nested instanceof OXException) {
                    throw (OXException) nested;
                }
                if (nested instanceof GSSException) {
                    final GSSException ge = (GSSException) nested;
                    throw KerberosExceptionCodes.TICKET_WRONG.create(ge, ge.getMessage());
                }
                throw KerberosExceptionCodes.UNKNOWN.create(e, e.getMessage());
            }
        }
        return null;
    }
}
