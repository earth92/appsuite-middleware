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

package com.openexchange.mailaccount.json.actions;

import static com.openexchange.mail.api.MailConfig.determinePasswordAndAuthType;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.json.JSONValue;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.crypto.CryptoErrorMessage;
import com.openexchange.exception.Category;
import com.openexchange.exception.OXException;
import com.openexchange.mail.api.AuthInfo;
import com.openexchange.mailaccount.KnownStatus;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountDescription;
import com.openexchange.mailaccount.MailAccountExceptionCodes;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.Status;
import com.openexchange.mailaccount.TransportAuth;
import com.openexchange.mailaccount.UnifiedInboxManagement;
import com.openexchange.mailaccount.json.ActiveProviderDetector;
import com.openexchange.mailaccount.json.MailAccountFields;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link StatusAction}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction(module = AbstractMailAccountAction.MODULE, type = RestrictedAction.Type.READ)
public final class StatusAction extends AbstractValidateMailAccountAction implements MailAccountFields {

    public static final String ACTION = "status";

    /**
     * Initializes a new {@link StatusAction}.
     */
    public StatusAction(ActiveProviderDetector activeProviderDetector) {
        super(activeProviderDetector);
    }

    @Override
    protected AJAXRequestResult innerPerform(final AJAXRequestData requestData, final ServerSession session, final JSONValue jVoid) throws OXException {
        MailAccountStorageService storageService = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class, true);
        List<OXException> warnings = new LinkedList<>();

        {
            int id = optionalIntParameter(AJAXServlet.PARAMETER_ID, -1, requestData);
            if (id >= 0) {
                if (MailAccount.DEFAULT_ID == id) {
                    // Primary is always allowed
                    return getStatusFor(id, session, storageService, warnings);
                }
                if (!session.getUserPermissionBits().isMultipleMailAccounts()) {
                    throw MailAccountExceptionCodes.NOT_ENABLED.create(Integer.valueOf(session.getUserId()), Integer.valueOf(session.getContextId()));
                }
                return getStatusFor(id, session, storageService, warnings);
            }
        }

        if (!session.getUserPermissionBits().isMultipleMailAccounts()) {
            // Only primary allowed
            return getStatusFor(MailAccount.DEFAULT_ID, session, storageService, warnings);
        }

        // Get status for all mail accounts
        MailAccount[] accounts = storageService.getUserMailAccounts(session.getUserId(), session.getContextId());
        JSONObject jStatuses = new JSONObject(accounts.length);

        for (MailAccount account : accounts) {
            Status status = determineAccountStatus(account, false, warnings, session);
            jStatuses.putSafe(String.valueOf(account.getId()), serialize(status, session.getUser().getLocale()));
        }
        return new AJAXRequestResult(jStatuses, "json").addWarnings(warnings);
    }

    private AJAXRequestResult getStatusFor(int id, final ServerSession session, MailAccountStorageService storageService, List<OXException> warnings) throws OXException {
        if (MailAccount.DEFAULT_ID != id && !session.getUserPermissionBits().isMultipleMailAccounts()) {
            UnifiedInboxManagement unifiedInboxManagement = ServerServiceRegistry.getInstance().getService(UnifiedInboxManagement.class);
            if ((null == unifiedInboxManagement) || (id != unifiedInboxManagement.getUnifiedINBOXAccountID(session))) {
                throw MailAccountExceptionCodes.NOT_ENABLED.create(Integer.valueOf(session.getUserId()), Integer.valueOf(session.getContextId()));
            }
        }

        Status status = determineAccountStatus(id, true, storageService, warnings, session);
        JSONObject jStatus = serialize(status, session.getUser().getLocale());
        return new AJAXRequestResult(new JSONObject(2).putSafe(Integer.toString(id), jStatus), "json").addWarnings(warnings);
    }

    private Status determineAccountStatus(int id, boolean singleRequested, MailAccountStorageService storageService, List<OXException> warnings, ServerSession session) throws OXException {
        MailAccount mailAccount = storageService.getMailAccount(id, session.getUserId(), session.getContextId());
        return determineAccountStatus(mailAccount, singleRequested, warnings, session);
    }

    private Status determineAccountStatus(MailAccount mailAccount, boolean singleRequested, List<OXException> warnings, ServerSession session) {
        try {
            if (isUnifiedINBOXAccount(mailAccount)) {
                // Treat as no hit
                if (singleRequested) {
                    throw MailAccountExceptionCodes.NOT_FOUND.create(Integer.valueOf(mailAccount.getId()), Integer.valueOf(session.getUserId()), Integer.valueOf(session.getContextId()));
                }
                return null;
            }

            if (mailAccount.isMailDisabled()) {
                return KnownStatus.DISABLED;
            }

            return checkStatus(mailAccount, session, false, warnings, false);
        } catch (OXException e) {
            return getStatusFor(mailAccount, e);
        }
    }

    /**
     * Validates specified account description.
     *
     * @param account The account to check
     * @param session The associated session
     * @param ignoreInvalidTransport
     * @param warnings The warnings list
     * @param errorOnDenied <code>true</code> to throw an error in case account description is denied (either by host or port); otherwise <code>false</code>
     * @return <code>true</code> for successful validation; otherwise <code>false</code>
     * @throws OXException If an severe error occurs
     */
    public static KnownStatus checkStatus(MailAccount account, ServerSession session, boolean ignoreInvalidTransport, List<OXException> warnings, boolean errorOnDenied) throws OXException {
        // Check for primary account
        if (MailAccount.DEFAULT_ID == account.getId()) {
            return KnownStatus.OK;
        }

        boolean ignoreTransport = ignoreInvalidTransport;

        MailAccountDescription accountDescription = new MailAccountDescription();
        accountDescription.setMailServer(account.getMailServer());
        accountDescription.setMailPort(account.getMailPort());
        accountDescription.setMailOAuthId(account.getMailOAuthId());
        accountDescription.setMailSecure(account.isMailSecure());
        accountDescription.setMailProtocol(account.getMailProtocol());
        accountDescription.setMailStartTls(account.isMailStartTls());
        accountDescription.setLogin(account.getLogin());
        accountDescription.setPrimaryAddress(account.getPrimaryAddress());
        try {
            AuthInfo authInfo = determinePasswordAndAuthType(account.getLogin(), session, account, true);
            accountDescription.setPassword(authInfo.getPassword());
            accountDescription.setAuthType(authInfo.getAuthType());
        } catch (OXException e) {
            if (!CryptoErrorMessage.BadPassword.equals(e)) {
                throw e;
            }
            return KnownStatus.INVALID_CREDENTIALS;
        }

        if (!isEmpty(account.getTransportServer())) {
            if (TransportAuth.NONE == account.getTransportAuth()) {
                return checkStatus(accountDescription, session, ignoreTransport, warnings, errorOnDenied);
            }

            accountDescription.setTransportServer(account.getTransportServer());
            accountDescription.setTransportPort(account.getTransportPort());
            accountDescription.setTransportOAuthId(account.getTransportOAuthId());
            accountDescription.setTransportSecure(account.isTransportSecure());
            accountDescription.setTransportProtocol(account.getTransportProtocol());
            accountDescription.setTransportStartTls(account.isTransportStartTls());

            if (TransportAuth.MAIL == account.getTransportAuth()) {
                accountDescription.setTransportLogin(accountDescription.getLogin());
                accountDescription.setTransportPassword(accountDescription.getPassword());
                accountDescription.setTransportAuthType(accountDescription.getAuthType());
                ignoreTransport = true;
            } else {
                String transportLogin = account.getTransportLogin();
                accountDescription.setTransportLogin(transportLogin);
                try {
                    AuthInfo authInfo = determinePasswordAndAuthType(transportLogin, session, account, false);
                    accountDescription.setTransportPassword(authInfo.getPassword());
                    accountDescription.setTransportAuthType(authInfo.getAuthType());
                } catch (OXException e) {
                    if (!CryptoErrorMessage.BadPassword.equals(e)) {
                        throw e;
                    }
                    return KnownStatus.INVALID_CREDENTIALS;
                }
            }
        }

        return checkStatus(accountDescription, session, ignoreTransport, warnings, errorOnDenied);
    }

    /**
     * Validates specified account description.
     *
     * @param accountDescription The account description
     * @param session The associated session
     * @param ignoreInvalidTransport
     * @param warnings The warnings list
     * @param errorOnDenied <code>true</code> to throw an error in case account description is denied (either by host or port); otherwise <code>false</code>
     * @return <code>true</code> for successful validation; otherwise <code>false</code>
     * @throws OXException If an severe error occurs
     */
    public static KnownStatus checkStatus(MailAccountDescription accountDescription, ServerSession session, boolean ignoreInvalidTransport, List<OXException> warnings, boolean errorOnDenied) throws OXException {
        // Check for primary account
        if (MailAccount.DEFAULT_ID == accountDescription.getId()) {
            return KnownStatus.OK;
        }
        // Validate mail server
        boolean validated = checkMailServerURL(accountDescription, session, warnings, errorOnDenied);
        // Failed?
        if (!validated) {
            KnownStatus status = testForCommunicationProblem(warnings, false, accountDescription);
            return null == status ? KnownStatus.INVALID_CREDENTIALS : status;
        }
        if (ignoreInvalidTransport) {
            // No need to check transport settings then
            return KnownStatus.OK;
        }
        // Now check transport server URL, if a transport server is present
        if (!isEmpty(accountDescription.getTransportServer())) {
            validated = checkTransportServerURL(accountDescription, session, warnings, errorOnDenied);
            if (!validated) {
                KnownStatus status = testForCommunicationProblem(warnings, true, accountDescription);
                return null == status ? KnownStatus.INVALID_CREDENTIALS : status;
            }
        }
        return KnownStatus.OK ;
    }

    protected static KnownStatus testForCommunicationProblem(List<OXException> warnings, boolean transport, MailAccountDescription accountDescription) {
        if (null != warnings && !warnings.isEmpty()) {
            OXException warning = warnings.get(0);
            if (indicatesCommunicationProblem(warning.getCause())) {
                OXException newWarning;
                if (transport) {
                    String login = accountDescription.getTransportLogin();
                    if (!seemsValid(login)) {
                        login = accountDescription.getLogin();
                    }
                    newWarning = MailAccountExceptionCodes.VALIDATE_FAILED_TRANSPORT.create(accountDescription.getTransportServer(), login);
                } else {
                    newWarning = MailAccountExceptionCodes.VALIDATE_FAILED_MAIL.create(accountDescription.getMailServer(), accountDescription.getLogin());
                }
                newWarning.setCategory(Category.CATEGORY_WARNING);
                warnings.clear();
                warnings.add(newWarning);
            } else if (indicatesSSLProblem(warning)) {
                warnings.add(warning);
                return KnownStatus.INVALID_SSL_CERTIFICATE;
            }
        }
        return null;
    }

    /**
     * Serializes an account status to JSON.
     *
     * @param status The status to serialize
     * @param locale The locale to use for translations
     * @return The serialized status as JSON object
     */
    private static JSONObject serialize(Status status, Locale locale) {
        if (null == status) {
            return null;
        }
        JSONObject jsonObject = new JSONObject(4);
        jsonObject.putSafe("status", status.getId());
        jsonObject.putSafe("message", status.getMessage(locale));
        return jsonObject;
    }

    /**
     * Optionally gets an error status for an encountered exception when trying to access the mail account.
     *
     * @param account The mail account being accessed
     * @param error The error that occurred
     * @return An appropriate error status
     */
    private static Status getStatusFor(MailAccount account, OXException error) {
        switch (error.getErrorCode()) {
            case "CRP-0001":
                // Wrong Password
                return KnownStatus.INVALID_CREDENTIALS;
            case "OAUTH-0042":
                // Required scopes are not authorized by user
                return KnownStatus.INACCESSIBLE(error);
            case "OAUTH-0043":
                // Required scopes are not available. Either not offered by service or explicitly disabled
                // (e.g. com.openexchange.oauth.modules.enabled.google=)
                return KnownStatus.UNSUPPORTED(error);
            case "OAUTH-0044":
                // OAuth provider has been disabled (e.g. com.openexchange.oauth.google=false)
                return KnownStatus.UNSUPPORTED(error);
            default:
                return KnownStatus.UNKNOWN(error);
        }
    }

}
