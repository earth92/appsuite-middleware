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

import static com.openexchange.database.Databases.closeSQLStuff;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONValue;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.database.Databases;
import com.openexchange.databaseold.Database;
import com.openexchange.exception.Category;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.jslob.DefaultJSlob;
import com.openexchange.jslob.JSlobId;
import com.openexchange.mail.MailSessionCache;
import com.openexchange.mail.mime.MimeMailExceptionCode;
import com.openexchange.mail.utils.MailPasswordUtil;
import com.openexchange.mailaccount.Attribute;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountDescription;
import com.openexchange.mailaccount.MailAccountExceptionCodes;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.TransportAuth;
import com.openexchange.mailaccount.json.ActiveProviderDetector;
import com.openexchange.mailaccount.json.MailAccountFields;
import com.openexchange.mailaccount.json.parser.DefaultMailAccountParser;
import com.openexchange.mailaccount.json.writer.DefaultMailAccountWriter;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link UpdateAction}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction(module = AbstractMailAccountAction.MODULE, type = RestrictedAction.Type.WRITE)
public final class UpdateAction extends AbstractMailAccountAction implements MailAccountFields {

    public static final String ACTION = AJAXServlet.ACTION_UPDATE;

    /**
     * Initializes a new {@link UpdateAction}.
     */
    public UpdateAction(ActiveProviderDetector activeProviderDetector) {
        super(activeProviderDetector);
    }

    private static final EnumSet<Attribute> DEFAULT = EnumSet.of(Attribute.ARCHIVE_FULLNAME_LITERAL, Attribute.ARCHIVE_LITERAL, Attribute.CONFIRMED_HAM_FULLNAME_LITERAL, Attribute.CONFIRMED_HAM_LITERAL, Attribute.CONFIRMED_SPAM_FULLNAME_LITERAL, Attribute.CONFIRMED_SPAM_LITERAL, Attribute.DRAFTS_FULLNAME_LITERAL, Attribute.DRAFTS_LITERAL, Attribute.SENT_FULLNAME_LITERAL, Attribute.SENT_LITERAL, Attribute.SPAM_FULLNAME_LITERAL, Attribute.SPAM_LITERAL, Attribute.TRASH_FULLNAME_LITERAL, Attribute.TRASH_LITERAL);

    private static final Set<Attribute> WEBMAIL_ALLOWED = EnumSet.of(Attribute.ID_LITERAL, Attribute.PERSONAL_LITERAL, Attribute.REPLY_TO_LITERAL, Attribute.UNIFIED_INBOX_ENABLED_LITERAL, Attribute.ARCHIVE_LITERAL, Attribute.ARCHIVE_FULLNAME_LITERAL, Attribute.SENT_LITERAL, Attribute.SENT_FULLNAME_LITERAL, Attribute.TRASH_LITERAL, Attribute.TRASH_FULLNAME_LITERAL, Attribute.SPAM_LITERAL, Attribute.SPAM_FULLNAME_LITERAL, Attribute.DRAFTS_LITERAL, Attribute.DRAFTS_FULLNAME_LITERAL);

    @Override
    protected AJAXRequestResult innerPerform(final AJAXRequestData requestData, final ServerSession session, final JSONValue jData) throws OXException, JSONException {
        if (null == jData) {
            throw AjaxExceptionCodes.MISSING_REQUEST_BODY.create();
        }

        MailAccountDescription accountDescription = new MailAccountDescription();
        List<OXException> warnings = new LinkedList<>();
        Set<Attribute> fieldsToUpdate = DefaultMailAccountParser.getInstance().parse(accountDescription, jData.toObject(), warnings);

        if (fieldsToUpdate.contains(Attribute.TRANSPORT_AUTH_LITERAL)) {
            TransportAuth transportAuth = accountDescription.getTransportAuth();
            if (TransportAuth.MAIL.equals(transportAuth) || TransportAuth.NONE.equals(transportAuth)) {
                fieldsToUpdate.add(Attribute.TRANSPORT_LOGIN_LITERAL);
                fieldsToUpdate.add(Attribute.TRANSPORT_PASSWORD_LITERAL);
                accountDescription.setTransportLogin(null);
                accountDescription.setTransportPassword(null);
            }
        }

        int id = accountDescription.getId();
        if (-1 == id) {
            throw AjaxExceptionCodes.MISSING_PARAMETER.create(MailAccountFields.ID);
        }

        if (fieldsToUpdate.contains(Attribute.LOGIN_LITERAL)) {
            final String login = accountDescription.getLogin();
            if (isEmpty(login)) {
                throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create(MailAccountFields.LOGIN, null == login ? "null" : login);
            }
        }
        if (fieldsToUpdate.contains(Attribute.PASSWORD_LITERAL)) {
            final String pw = accountDescription.getPassword();
            if (MailAccount.DEFAULT_ID != id && isEmpty(pw)) {
                throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create(MailAccountFields.PASSWORD, null == pw ? "null" : pw);
            }
        }
        if (fieldsToUpdate.contains(Attribute.MAIL_URL_LITERAL)) {
            final String server = accountDescription.getMailServer();
            if (MailAccount.DEFAULT_ID != id && isEmpty(server)) {
                throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create(MailAccountFields.MAIL_URL, null == server ? "null" : server);
            }
        }

        final int contextId = session.getContextId();

        // Check attributes to update
        {
            final Set<Attribute> notAllowed = EnumSet.copyOf(fieldsToUpdate);
            notAllowed.removeAll(WEBMAIL_ALLOWED);
            if (!session.getUserPermissionBits().isMultipleMailAccounts() && (!isDefaultMailAccount(accountDescription) || (!notAllowed.isEmpty()))) {
                throw MailAccountExceptionCodes.NOT_ENABLED.create(Integer.valueOf(session.getUserId()), Integer.valueOf(contextId));
            }
        }

        // Acquire storage service
        final MailAccountStorageService storageService = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class, true);

        // Get & check the account to update
        final MailAccount toUpdate = storageService.getMailAccount(id, session.getUserId(), contextId);
        if (isUnifiedINBOXAccount(toUpdate)) {
            // Treat as no hit
            throw MailAccountExceptionCodes.NOT_FOUND.create(Integer.valueOf(id), Integer.valueOf(session.getUserId()), Integer.valueOf(contextId));
        }

        // Check whether to clear POP3 account's time stamp
        boolean clearStamp = false;

        if (id != MailAccount.DEFAULT_ID) {
            // Don't check for POP3 account due to access restrictions (login only allowed every n minutes)
            boolean pop3 = Strings.toLowerCase(accountDescription.getMailProtocol()).startsWith("pop3");

            if (fieldsToUpdate.contains(Attribute.MAIL_SERVER_LITERAL) || fieldsToUpdate.contains(Attribute.MAIL_URL_LITERAL)) {
                String mailServerURL = toUpdate.generateMailServerURL();
                if (!mailServerURL.equals(accountDescription.generateMailServerURL())) {
                    if (!pop3 && !ValidateAction.checkMailServerURL(accountDescription, session, warnings, true)) {
                        OXException warning = MimeMailExceptionCode.CONNECT_ERROR.create(accountDescription.getMailServer(), accountDescription.getLogin());
                        warning.setCategory(Category.CATEGORY_WARNING);
                        warnings.add(0, warning);
                    }
                    clearStamp |= (pop3 && !toUpdate.getMailServer().equals(accountDescription.getMailServer()));
                }
            }
            if (fieldsToUpdate.contains(Attribute.TRANSPORT_SERVER_LITERAL) || fieldsToUpdate.contains(Attribute.TRANSPORT_URL_LITERAL)) {
                String transportServerURL = toUpdate.generateTransportServerURL();
                if (transportServerURL == null || !transportServerURL.equals(accountDescription.generateTransportServerURL())) {
                    if (!pop3 && !ValidateAction.checkTransportServerURL(accountDescription, session, warnings, true)) {
                        String transportLogin = accountDescription.getTransportLogin();
                        OXException warning = MimeMailExceptionCode.CONNECT_ERROR.create(accountDescription.getTransportServer(), transportLogin == null ? accountDescription.getLogin() : transportLogin);
                        warning.setCategory(Category.CATEGORY_WARNING);
                        warnings.add(0, warning);
                    }
                    clearStamp |= (pop3 && (toUpdate.getTransportServer() == null || !toUpdate.getTransportServer().equals(accountDescription.getTransportServer())));
                }
            }

        }
        /*-
         * Check standard folder names against full names
         *
        if (false == isPop3(toUpdate) && false == toUpdate.isMailDisabled()) {
            fillMailConfig(accountDescription, fieldsToUpdate, toUpdate, session);
            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> mailAccess = getMailAccess(accountDescription, session, warnings);
            Tools.checkNames(accountDescription, fieldsToUpdate, Tools.getSeparator(mailAccess));
        }
         */

        // Update
        MailAccount updatedAccount = null;
        {
            final Connection wcon = Database.get(contextId, true);
            int rollback = 0;
            try {
                Databases.startTransaction(wcon);
                rollback = 1;

                // Invoke update
                storageService.updateMailAccount(accountDescription, fieldsToUpdate, session.getUserId(), contextId, session, wcon, false);

                // Clear standard folder information from session caches
                {
                    boolean standardFolderChanged = false;
                    for (final Iterator<Attribute> it = fieldsToUpdate.iterator(); !standardFolderChanged && it.hasNext();) {
                        if (DEFAULT.contains(it.next())) {
                            standardFolderChanged = true;
                        }
                    }
                    if (standardFolderChanged) {
                        MailSessionCache.removeDefaultFolderInformationFrom(id, session.getUserId(), contextId);
                    }
                }

                // Reload
                updatedAccount = storageService.getMailAccount(id, session.getUserId(), contextId, wcon);

                // Any standard folders changed?
                if ((null != updatedAccount) && (fieldsToUpdate.removeAll(DEFAULT))) {
                    updatedAccount = checkFullNames(updatedAccount, storageService, session, wcon, null);
                }

                // Clear POP3 account's time stamp
                if (clearStamp) {
                    PreparedStatement stmt = null;
                    try {
                        // Delete possibly existing mapping
                        stmt = wcon.prepareStatement("DELETE FROM user_mail_account_properties WHERE cid = ? AND user = ? AND id = ? AND name = ?");
                        int pos = 1;
                        stmt.setInt(pos++, contextId);
                        stmt.setInt(pos++, session.getUserId());
                        stmt.setInt(pos++, id);
                        stmt.setString(pos++, "pop3.lastaccess");
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        throw MailAccountExceptionCodes.SQL_ERROR.create(e, e.getMessage());
                    } finally {
                        closeSQLStuff(null, stmt);
                    }
                }

                wcon.commit();
                rollback = 2;
            } catch (SQLException e) {
                throw MailAccountExceptionCodes.SQL_ERROR.create(e, e.getMessage());
            } catch (RuntimeException e) {
                throw MailAccountExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
            } finally {
                if (rollback > 0) {
                    if (rollback == 1) {
                        Databases.rollback(wcon);
                    }
                    Databases.autocommit(wcon);
                }
                Database.back(contextId, true, wcon);
            }
        }

        // Check for possible meta information
        {
            final JSONObject jBody = jData.toObject();
            if (jBody.hasAndNotNull(META)) {
                final JSONObject jMeta = jBody.optJSONObject(META);
                getStorage().store(new JSlobId(JSLOB_SERVICE_ID, Integer.toString(id), session.getUserId(), session.getContextId()), new DefaultJSlob(jMeta));
            }
        }

        // Write to JSON structure
        final JSONObject jsonAccount;
        if (null == updatedAccount) {
            MailAccount mailAccount = storageService.getMailAccount(id, session.getUserId(), contextId);
            mailAccount = checkSpamInfo(mailAccount, session);
            jsonAccount = DefaultMailAccountWriter.write(mailAccount);
        } else {
            updatedAccount = checkSpamInfo(updatedAccount, session);
            jsonAccount = DefaultMailAccountWriter.write(updatedAccount);
        }

        return new AJAXRequestResult(jsonAccount).addWarnings(warnings);
    }

    /**
     * Fills the provided {@link MailAccountDescription} with already existing data if they are not existing in fieldsToUpdate
     *
     * @param accountDescription
     * @param fieldsToUpdate
     * @param toUpdate
     * @param session
     * @throws OXException
     */
    private void fillMailConfig(MailAccountDescription accountDescription, Set<Attribute> fieldsToUpdate, MailAccount toUpdate, Session session) throws OXException {
        if (!fieldsToUpdate.contains(Attribute.LOGIN_LITERAL)) {
            accountDescription.setLogin(toUpdate.getLogin());
        }
        if (!fieldsToUpdate.contains(Attribute.PASSWORD_LITERAL)) {
            String password = toUpdate.getPassword();
            if (toUpdate.isDefaultAccount() || password == null) {
                password = session.getPassword();
            } else {
                password = MailPasswordUtil.decrypt(password, session, toUpdate.getId(), toUpdate.getLogin(), toUpdate.getMailServer());
            }
            accountDescription.setPassword(password);
        }
        if (!fieldsToUpdate.contains(Attribute.MAIL_PORT_LITERAL)) {
            accountDescription.setMailPort(toUpdate.getMailPort());
        }
        if (!fieldsToUpdate.contains(Attribute.MAIL_SECURE_LITERAL)) {
            accountDescription.setMailSecure(toUpdate.isMailSecure());
        }
        if (!fieldsToUpdate.contains(Attribute.MAIL_SERVER_LITERAL)) {
            accountDescription.setMailServer(toUpdate.getMailServer());
        }
    }

    private boolean isPop3(MailAccount account) {
        if (null == account) {
            return false;
        }
        if (Strings.toLowerCase(account.getMailProtocol()).startsWith("pop3")) {
            return true;
        }

        return false;
    }

}
