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

package com.openexchange.mailaccount.internal;

import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.mailaccount.Attribute;
import com.openexchange.mailaccount.Event;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountDescription;
import com.openexchange.mailaccount.MailAccountExceptionCodes;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.TransportAccount;
import com.openexchange.mailaccount.TransportAccountDescription;
import com.openexchange.mailaccount.UpdateProperties;
import com.openexchange.session.Session;

/**
 * {@link SanitizingStorageService}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
final class SanitizingStorageService implements MailAccountStorageService {

    private static final int URI_ERROR_NUMBER = MailAccountExceptionCodes.URI_PARSE_FAILED.getNumber();

    private static final String PREFIX = MailAccountExceptionCodes.URI_PARSE_FAILED.getPrefix();

    private final MailAccountStorageService storageService;

    /**
     * Initializes a new {@link SanitizingStorageService}.
     */
    SanitizingStorageService(final MailAccountStorageService storageService) {
        super();
        this.storageService = storageService;
    }

    private static boolean isURIError(final OXException candidate) {
        return PREFIX.equals(candidate.getPrefix()) && URI_ERROR_NUMBER == candidate.getCode();
    }

    @Override
    public void invalidateMailAccount(final int id, final int user, final int cid) throws OXException {
        storageService.invalidateMailAccount(id, user, cid);
    }

    @Override
    public void invalidateMailAccounts(final int user, final int cid) throws OXException {
        storageService.invalidateMailAccounts(user, cid);
    }

    @Override
    public boolean incrementFailedMailAuthCount(int accountId, int userId, int contextId, Exception optReason) throws OXException {
        return storageService.incrementFailedMailAuthCount(accountId, userId, contextId, optReason);
    }

    @Override
    public boolean incrementFailedTransportAuthCount(int accountId, int userId, int contextId, Exception optReason) throws OXException {
        return storageService.incrementFailedTransportAuthCount(accountId, userId, contextId, optReason);
    }

    @Override
    public MailAccount getRawMailAccount(int id, int userId, int cid) throws OXException {
        try {
            return storageService.getRawMailAccount(id, userId, cid);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(userId, cid, storageService);
            return storageService.getRawMailAccount(id, userId, cid);
        }
    }

    @Override
    public int acquireId(int userId, Context ctx) throws OXException {
        return storageService.acquireId(userId, ctx);
    }

    @Override
    public String getDefaultFolderPrefix(Session session) throws OXException {
        return storageService.getDefaultFolderPrefix(session);
    }

    @Override
    public char getDefaultSeparator(Session session) throws OXException {
        return storageService.getDefaultSeparator(session);
    }

    @Override
    public boolean existsMailAccount(int id, int userId, int contextId) throws OXException {
        return storageService.existsMailAccount(id, userId, contextId);
    }

    @Override
    public MailAccount getMailAccount(final int id, final int user, final int cid) throws OXException {
        try {
            return storageService.getMailAccount(id, user, cid);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(user, cid, storageService);
            return storageService.getMailAccount(id, user, cid);
        }
    }

    @Override
    public MailAccount getMailAccount(int id, int user, int cid, Connection con) throws OXException {
        try {
            return storageService.getMailAccount(id, user, cid, con);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(user, cid, storageService);
            return storageService.getMailAccount(id, user, cid, con);
        }
    }

    @Override
    public TransportAccount[] getUserTransportAccounts(final int user, final int cid) throws OXException {
        try {
            return storageService.getUserTransportAccounts(user, cid);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(user, cid, storageService);
            return storageService.getUserTransportAccounts(user, cid);
        }
    }

    @Override
    public TransportAccount[] getUserTransportAccounts(final int user, final int cid, final Connection con) throws OXException {
        try {
            return storageService.getUserTransportAccounts(user, cid, con);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(user, cid, storageService);
            return storageService.getUserTransportAccounts(user, cid, con);
        }
    }

    @Override
    public MailAccount[] getUserMailAccounts(final int user, final int cid) throws OXException {
        try {
            return storageService.getUserMailAccounts(user, cid);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(user, cid, storageService);
            return storageService.getUserMailAccounts(user, cid);
        }
    }

    @Override
    public MailAccount[] getUserMailAccounts(final int user, final int cid, final Connection con) throws OXException {
        try {
            return storageService.getUserMailAccounts(user, cid, con);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(user, cid, storageService);
            return storageService.getUserMailAccounts(user, cid, con);
        }
    }

    @Override
    public List<MailAccount> getUserMailAccounts(int contextId) throws OXException {
        try {
            return storageService.getUserMailAccounts(contextId);
        } catch (OXException e) {
            if (!isURIError(e)) {
                throw e;
            }
            Sanitizer.sanitize(contextId, storageService);
            return storageService.getUserMailAccounts(contextId);
        }
    }

    @Override
    public MailAccount getDefaultMailAccount(final int user, final int cid) throws OXException {
        return storageService.getDefaultMailAccount(user, cid);
    }

    @Override
    public void enableMailAccount(int accountId, int userId, int contextId) throws OXException {
        storageService.enableMailAccount(accountId, userId, contextId);
    }

    @Override
    public void enableMailAccount(int accountId, int userId, int contextId, Connection con) throws OXException {
        storageService.enableMailAccount(accountId, userId, contextId, con);
    }

    @Override
    public void updateMailAccount(final MailAccountDescription mailAccount, final Set<Attribute> attributes, final int user, final int cid, final Session session) throws OXException {
        storageService.updateMailAccount(mailAccount, attributes, user, cid, session);
    }

    @Override
    public void updateMailAccount(final MailAccountDescription mailAccount, final Set<Attribute> attributes, final int user, final int cid, final Session session, final Connection con, final boolean changePrimary) throws OXException {
        storageService.updateMailAccount(mailAccount, attributes, user, cid, session, con, changePrimary);
    }

    @Override
    public void updateMailAccount(MailAccountDescription mailAccount, Set<Attribute> attributes, int userId, int contextId, UpdateProperties updateProperties) throws OXException {
        storageService.updateMailAccount(mailAccount, attributes, userId, contextId, updateProperties);
    }

    @Override
    public void updateMailAccount(final MailAccountDescription mailAccount, final int user, final int cid, final Session session) throws OXException {
        storageService.updateMailAccount(mailAccount, user, cid, session);
    }

    @Override
    public int insertMailAccount(final MailAccountDescription mailAccount, final int user, final Context ctx, final Session session) throws OXException {
        return storageService.insertMailAccount(mailAccount, user, ctx, session);
    }

    @Override
    public int insertMailAccount(final MailAccountDescription mailAccount, final int user, final Context ctx, final Session session, final Connection con) throws OXException {
        return storageService.insertMailAccount(mailAccount, user, ctx, session, con);
    }

    @Override
    public void clearFullNamesForMailAccount(final int id, final int user, final int cid) throws OXException {
        storageService.clearFullNamesForMailAccount(id, user, cid);
    }

    @Override
    public void clearFullNamesForMailAccount(final int id, final int[] indexes, final int user, final int cid) throws OXException {
        storageService.clearFullNamesForMailAccount(id, indexes, user, cid);
    }

    @Override
    public boolean setFullNamesForMailAccount(int id, int[] indexes, String[] fullNames, int user, int cid) throws OXException {
        return storageService.setFullNamesForMailAccount(id, indexes, fullNames, user, cid);
    }

    @Override
    public boolean setNamesForMailAccount(int id, int[] indexes, String[] names, int userId, int contextId) throws OXException {
        return storageService.setNamesForMailAccount(id, indexes, names, userId, contextId);
    }

    @Override
    public void propagateEvent(Event event, int id, Map<String, Object> eventProps, int userId, int contextId) throws OXException {
        storageService.propagateEvent(event, id, eventProps, userId, contextId);
    }

    @Override
    public boolean deleteMailAccount(final int id, final Map<String, Object> properties, final int user, final int cid) throws OXException {
        return storageService.deleteMailAccount(id, properties, user, cid);
    }

    @Override
    public boolean deleteMailAccount(final int id, final Map<String, Object> properties, final int user, final int cid, final boolean deletePrimary) throws OXException {
        return storageService.deleteMailAccount(id, properties, user, cid, deletePrimary);
    }

    @Override
    public boolean deleteMailAccount(final int id, final Map<String, Object> properties, final int user, final int cid, final boolean deletePrimary, final Connection con) throws OXException {
        return storageService.deleteMailAccount(id, properties, user, cid, deletePrimary, con);
    }

    @Override
    public void deleteAllMailAccounts(int userId, int contextId, Connection connection) throws OXException {
        storageService.deleteAllMailAccounts(userId, contextId, connection);
    }

    @Override
    public MailAccount[] resolveLogin(final String login, final int cid) throws OXException {
        return storageService.resolveLogin(login, cid);
    }

    @Override
    public MailAccount[] resolveLogin(final String login, final String serverUrl, final int cid) throws OXException {
        return storageService.resolveLogin(login, serverUrl, cid);
    }

    @Override
    public MailAccount[] resolvePrimaryAddr(final String primaryAddress, final int cid) throws OXException {
        return storageService.resolvePrimaryAddr(primaryAddress, cid);
    }

    @Override
    public int getByPrimaryAddress(final String primaryAddress, final int user, final int cid) throws OXException {
        return storageService.getByPrimaryAddress(primaryAddress, user, cid);
    }

    @Override
    public int getTransportByPrimaryAddress(final String primaryAddress, final int user, final int cid) throws OXException {
        return storageService.getTransportByPrimaryAddress(primaryAddress, user, cid);
    }

    @Override
    public TransportAccount getTransportByReference(String reference, int userId, int contextId) throws OXException {
        return storageService.getTransportByReference(reference, userId, contextId);
    }

    @Override
    public int[] getByHostNames(final Collection<String> hostNames, final int user, final int cid) throws OXException {
        return storageService.getByHostNames(hostNames, user, cid);
    }

    @Override
    public void migratePasswords(final String oldSecret, final String newSecret, final Session session) throws OXException {
        storageService.migratePasswords(oldSecret, newSecret, session);
    }

    @Override
    public void cleanUp(final String secret, final Session session) throws OXException {
        storageService.cleanUp(secret, session);
    }

    @Override
    public void removeUnrecoverableItems(final String secret, final Session session) throws OXException {
        storageService.removeUnrecoverableItems(secret, session);
    }

    @Override
    public boolean hasAccounts(final Session session) throws OXException {
        return storageService.hasAccounts(session);
    }

    @Override
    public int insertTransportAccount(TransportAccountDescription transportAccount, int userId, Context ctx, Session session) throws OXException {
        return storageService.insertTransportAccount(transportAccount, userId, ctx, session);
    }

    @Override
    public void deleteTransportAccount(int id, int userId, int contextId) throws OXException {
        storageService.deleteTransportAccount(id, userId, contextId);
    }

    @Override
    public void deleteTransportAccount(int id, int userId, int contextId, Connection con) throws OXException {
        storageService.deleteTransportAccount(id, userId, contextId, con);
    }

    @Override
    public TransportAccount getTransportAccount(int accountId, int userId, int contextId, Connection con) throws OXException {
        return storageService.getTransportAccount(accountId, userId, contextId, con);
    }

    @Override
    public void updateTransportAccount(TransportAccountDescription transportAccount, int userId, int cid, Session session) throws OXException {
        storageService.updateTransportAccount(transportAccount, userId, cid, session);
    }

    @Override
    public void updateTransportAccount(TransportAccountDescription transportAccount, Set<Attribute> attributes, int userId, int contextId, Session session) throws OXException {
        storageService.updateTransportAccount(transportAccount, attributes, userId, contextId, session);
    }

    @Override
    public void updateTransportAccount(TransportAccountDescription transportAccount, Set<Attribute> attributes, int userId, int contextId, UpdateProperties updateProperties) throws OXException {
        storageService.updateTransportAccount(transportAccount, attributes, userId, contextId, updateProperties);
    }

    @Override
    public TransportAccount getTransportAccount(int accountId, int userId, int contextId) throws OXException {
        return storageService.getTransportAccount(accountId, userId, contextId);
    }

}
