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

import static com.openexchange.database.Databases.autocommit;
import static com.openexchange.database.Databases.rollback;
import static com.openexchange.mail.utils.ProviderUtility.toSocketAddrString;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheKey;
import com.openexchange.caching.CacheService;
import com.openexchange.database.DatabaseService;
import com.openexchange.databaseold.Database;
import com.openexchange.event.CommonEvent;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.cache.memory.FolderMap;
import com.openexchange.folderstorage.cache.memory.FolderMapManagement;
import com.openexchange.folderstorage.outlook.OutlookFolderStorage;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.lock.LockService;
import com.openexchange.mail.dataobjects.MailFolder;
import com.openexchange.mail.utils.MailFolderUtility;
import com.openexchange.mail.utils.StorageUtility;
import com.openexchange.mailaccount.Attribute;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountDescription;
import com.openexchange.mailaccount.MailAccountExceptionCodes;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.TransportAccount;
import com.openexchange.mailaccount.TransportAccountDescription;
import com.openexchange.mailaccount.UpdateProperties;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.Task;
import com.openexchange.threadpool.ThreadPools;

/**
 * {@link CachingMailAccountStorage} - The caching implementation of mail account storage.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
final class CachingMailAccountStorage implements MailAccountStorageService {

    // --------------------------------------- Constants for event --------------------------------------------------------------

    private static final String PROP_DRAFTS_FULL_NAME = "com.openexchange.mailaccount.draftsFullName";
    private static final String PROP_SENT_FULL_NAME = "com.openexchange.mailaccount.sentFullName";
    private static final String PROP_SPAM_FULL_NAME = "com.openexchange.mailaccount.spamFullName";
    private static final String PROP_TRASH_FULL_NAME = "com.openexchange.mailaccount.trashFullName";
    private static final String PROP_CONFIRMED_SPAM_FULL_NAME = "com.openexchange.mailaccount.confirmedSpamFullName";
    private static final String PROP_CONFIRMED_HAM_FULL_NAME = "com.openexchange.mailaccount.confirmedHamFullName";

    private static final String PROP_DRAFTS_NAME = "com.openexchange.mailaccount.draftsName";
    private static final String PROP_SENT_NAME = "com.openexchange.mailaccount.sentName";
    private static final String PROP_SPAM_NAME = "com.openexchange.mailaccount.spamName";
    private static final String PROP_TRASH_NAME = "com.openexchange.mailaccount.trashName";
    private static final String PROP_CONFIRMED_SPAM_NAME = "com.openexchange.mailaccount.confirmedSpamName";
    private static final String PROP_CONFIRMED_HAM_NAME = "com.openexchange.mailaccount.confirmedHamName";

    private static final String PROP_ACCOUNT_ID = "com.openexchange.mailaccount.accountId";
    private static final String PROP_USER_ID = "com.openexchange.mailaccount.userId";
    private static final String PROP_CONTEXT_ID = "com.openexchange.mailaccount.contextId";

    private static final String TOPIC_CHANGED_DEFAULT_FOLDERS = "com/openexchange/mailaccount/changeddefaultfolders";

    // ---------------------------------------------------------------------------------------------------------------------------

    private static final String REGION_NAME = "MailAccount";

    /**
     * Proxy attribute for the object implementing the persistent methods.
     */
    private final RdbMailAccountStorage delegate;

    /**
     * Initializes a new {@link CachingMailAccountStorage}.
     *
     * @param delegate The database-backed delegate storage
     */
    CachingMailAccountStorage(final RdbMailAccountStorage delegate) {
        super();
        this.delegate = delegate;
    }

    RdbMailAccountStorage getDelegate() {
        return delegate;
    }

    private void postChangedDefaultFolders(int id, int[] indexes, String[] values, boolean isFullName, boolean distributeRemotely, int userId, int contextId) {
        EventAdmin eventAdmin = ServerServiceRegistry.getInstance().getService(EventAdmin.class);
        if (null != eventAdmin) {
            Map<String, Object> properties = new HashMap<String, Object>(9);
            properties.put(PROP_CONTEXT_ID, Integer.valueOf(contextId));
            properties.put(PROP_USER_ID, Integer.valueOf(userId));
            properties.put(PROP_ACCOUNT_ID, Integer.valueOf(id));

            if (distributeRemotely) {
                properties.put(CommonEvent.PUBLISH_MARKER, Boolean.TRUE);
            }

            if (null != indexes && null != values) {
                for (int i = 0; i < indexes.length; i++) {
                    switch (indexes[i]) {
                        case StorageUtility.INDEX_DRAFTS:
                            properties.put(isFullName ? PROP_DRAFTS_FULL_NAME : PROP_DRAFTS_NAME, values[i]);
                            break;
                        case StorageUtility.INDEX_SENT:
                            properties.put(isFullName ? PROP_SENT_FULL_NAME : PROP_SENT_NAME, values[i]);
                            break;
                        case StorageUtility.INDEX_SPAM:
                            properties.put(isFullName ? PROP_SPAM_FULL_NAME : PROP_SPAM_NAME, values[i]);
                            break;
                        case StorageUtility.INDEX_TRASH:
                            properties.put(isFullName ? PROP_TRASH_FULL_NAME : PROP_TRASH_NAME, values[i]);
                            break;
                        case StorageUtility.INDEX_CONFIRMED_SPAM:
                            properties.put(isFullName ? PROP_CONFIRMED_SPAM_FULL_NAME : PROP_CONFIRMED_SPAM_NAME, values[i]);
                            break;
                        case StorageUtility.INDEX_CONFIRMED_HAM:
                            properties.put(isFullName ? PROP_CONFIRMED_HAM_FULL_NAME : PROP_CONFIRMED_HAM_NAME, values[i]);
                            break;
                        default:
                            break;
                    }
                }
            }

            eventAdmin.postEvent(new Event(TOPIC_CHANGED_DEFAULT_FOLDERS, properties));
        }
    }

    static CacheKey newCacheKey(CacheService cacheService, int id, int userId, int contextId) {
        return cacheService.newCacheKey(contextId, Integer.toString(id), Integer.toString(userId));
    }

    static CacheKey accountsCacheKey(CacheService cacheService, int userId, int contextId) {
        return cacheService.newCacheKey(contextId, Integer.toString(userId));
    }

    @Override
    public void invalidateMailAccount(int id, int userId, int contextId) throws OXException {
        final CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (null != cacheService) {
            Cache cache = cacheService.getCache(REGION_NAME);
            cache.remove(newCacheKey(cacheService, id, userId, contextId));
            cache.remove(accountsCacheKey(cacheService, userId, contextId));
            cache.invalidateGroup(Integer.toString(contextId));
        }
        final FolderMap folderMap = FolderMapManagement.getInstance().optFor(userId, contextId);
        if (null != folderMap) {
            String rootId = MailFolderUtility.prepareFullname(id, MailFolder.ROOT_FOLDER_ID);
            folderMap.remove(rootId, FolderStorage.REAL_TREE_ID);
            folderMap.remove(rootId, OutlookFolderStorage.OUTLOOK_TREE_ID);
        }
    }

    @Override
    public void invalidateMailAccounts(int userId, int contextId) throws OXException {
        final CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (null != cacheService) {
            final DatabaseService db = ServerServiceRegistry.getInstance().getService(DatabaseService.class);
            final Connection con = db.getWritable(contextId);
            final int[] ids;
            try {
                ids = delegate.getUserMailAccountIDs(userId, contextId, con);
            } finally {
                db.backWritableAfterReading(contextId, con);
            }

            Cache cache = cacheService.getCache(REGION_NAME);
            cache.remove(accountsCacheKey(cacheService, userId, contextId));
            for (final int id : ids) {
                cache.remove(newCacheKey(cacheService, id, userId, contextId));
                cache.invalidateGroup(Integer.toString(contextId));
            }
        }

        FolderMapManagement.getInstance().dropFor(userId, contextId);
    }

    @Override
    public void clearFullNamesForMailAccount(int id, int userId, int contextId) throws OXException {
        delegate.clearFullNamesForMailAccount(id, userId, contextId);
        invalidateMailAccount(id, userId, contextId);

        postChangedDefaultFolders(id, null, null, false, true, userId, contextId);
    }

    @Override
    public void clearFullNamesForMailAccount(int id, int[] indexes, int userId, int contextId) throws OXException {
        delegate.clearFullNamesForMailAccount(id, indexes, userId, contextId);
        invalidateMailAccount(id, userId, contextId);

        postChangedDefaultFolders(id, null, null, false, true, userId, contextId);
    }

    @Override
    public boolean setFullNamesForMailAccount(int id, int[] indexes, String[] fullNames, int userId, int contextId) throws OXException {
        boolean modified = delegate.setFullNamesForMailAccount(id, indexes, fullNames, userId, contextId);
        if (modified) {
            invalidateMailAccount(id, userId, contextId);
            postChangedDefaultFolders(id, indexes, fullNames, true, true, userId, contextId);
        }

        return modified;
    }

    @Override
    public boolean setNamesForMailAccount(int id, int[] indexes, String[] names, int userId, int contextId) throws OXException {
        boolean modified = delegate.setNamesForMailAccount(id, indexes, names, userId, contextId);
        if (modified) {
            invalidateMailAccount(id, userId, contextId);
            postChangedDefaultFolders(id, indexes, names, false, true, userId, contextId);
        }

        return modified;
    }

    @Override
    public void propagateEvent(com.openexchange.mailaccount.Event event, int id, Map<String, Object> eventProps, int userId, int contextId) throws OXException {
        delegate.propagateEvent(event, id, eventProps, userId, contextId);
    }

    @Override
    public boolean deleteMailAccount(int id, Map<String, Object> properties, int userId, int contextId, boolean deletePrimary, Connection con) throws OXException {
        dropSessionParameter(userId, contextId);

        boolean deleted = delegate.deleteMailAccount(id, properties, userId, contextId, deletePrimary, con);
        invalidateMailAccount(id, userId, contextId);
        return deleted;
    }

    @Override
    public void deleteAllMailAccounts(int userId, int contextId, Connection connection) throws OXException {
        dropSessionParameter(userId, contextId);

        delegate.deleteAllMailAccounts(userId, contextId, connection);
        invalidateMailAccounts(userId, contextId);
    }

    @Override
    public boolean deleteMailAccount(int id, Map<String, Object> properties, int userId, int contextId, boolean deletePrimary) throws OXException {
        dropSessionParameter(userId, contextId);

        boolean deleted = delegate.deleteMailAccount(id, properties, userId, contextId, deletePrimary);
        invalidateMailAccount(id, userId, contextId);
        return deleted;
    }

    @Override
    public boolean deleteMailAccount(int id, Map<String, Object> properties, int userId, int contextId) throws OXException {
        dropSessionParameter(userId, contextId);

        boolean deleted = delegate.deleteMailAccount(id, properties, userId, contextId);
        invalidateMailAccount(id, userId, contextId);
        return deleted;
    }

    private void dropSessionParameter(final int userId, final int contextId) {
        Task<Void> task = new AbstractTask<Void>() {

            @Override
            public Void call() {
                SessiondService service = ServerServiceRegistry.getInstance().getService(SessiondService.class);
                if (null != service) {
                    for (Session session : service.getSessions(userId, contextId)) {
                        session.setParameter("com.openexchange.mailaccount.unifiedMailEnabled", null);
                    }
                }
                return null;
            }
        };
        ThreadPools.getThreadPool().submit(task);
    }

    @Override
    public int acquireId(int userId, Context ctx) throws OXException {
        return delegate.acquireId(userId, ctx);
    }

    @Override
    public String getDefaultFolderPrefix(Session session) throws OXException {
        return delegate.getDefaultFolderPrefix(session);
    }

    @Override
    public char getDefaultSeparator(Session session) throws OXException {
        return delegate.getDefaultSeparator(session);
    }

    @Override
    public boolean incrementFailedMailAuthCount(int accountId, int userId, int contextId, Exception optReason) throws OXException {
        boolean disabled = delegate.incrementFailedMailAuthCount(accountId, userId, contextId, optReason);
        if (disabled) {
            invalidateMailAccount(accountId, userId, contextId);
        }
        return disabled;
    }

    @Override
    public boolean incrementFailedTransportAuthCount(int accountId, int userId, int contextId, Exception optReason) throws OXException {
        boolean disabled = delegate.incrementFailedTransportAuthCount(accountId, userId, contextId, optReason);
        if (disabled) {
            invalidateMailAccount(accountId, userId, contextId);
        }
        return disabled;
    }

    @Override
    public MailAccount getDefaultMailAccount(int userId, int contextId) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getDefaultMailAccount(userId, contextId);
        }
        Cache cache = cacheService.getCache(REGION_NAME);
        Object object = cache.get(newCacheKey(cacheService, MailAccount.DEFAULT_ID, userId, contextId));
        if (object instanceof MailAccount) {
            return (MailAccount) object;
        }

        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append("mailaccount-").append(contextId).append('-').append(userId).append('-').append(MailAccount.DEFAULT_ID).toString());
        lock.lock();
        try {
            object = cache.get(newCacheKey(cacheService, MailAccount.DEFAULT_ID, userId, contextId));
            if (object instanceof MailAccount) {
                return (MailAccount) object;
            }

            MailAccount defaultMailAccount = delegate.getDefaultMailAccount(userId, contextId);
            cache.put(newCacheKey(cacheService, MailAccount.DEFAULT_ID, userId, contextId), defaultMailAccount, false);
            return defaultMailAccount;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean existsMailAccount(int id, int userId, int contextId) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.existsMailAccount(id, userId, contextId);
        }

        Cache cache = cacheService.getCache(REGION_NAME);
        CacheKey key = newCacheKey(cacheService, id, userId, contextId);
        Object object = cache.get(key);
        if (object instanceof MailAccount) {
            return true;
        }

        // Check in listing of account IDs
        key = accountsCacheKey(cacheService, userId, contextId);
        object = cache.get(key);
        if (object instanceof int[]) {
            // Rely on listing of account IDs...
            int[] ids = (int[]) object;
            boolean exists = false;
            for (int i = ids.length; !exists && i-- > 0;) {
                exists = (ids[i] == id);
            }
            return exists;
        }

        return delegate.existsMailAccount(id, userId, contextId);
    }

    @Override
    public MailAccount getMailAccount(int id, int userId, int contextId) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getMailAccount(id, userId, contextId);
        }

        Cache cache = cacheService.getCache(REGION_NAME);
        CacheKey key = newCacheKey(cacheService, id, userId, contextId);
        Object object = cache.get(key);
        if (object instanceof MailAccount) {
            return (MailAccount) object;
        }

        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append("mailaccount-").append(contextId).append('-').append(userId).append('-').append(id).toString());
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof MailAccount) {
                return (MailAccount) object;
            }

            MailAccount mailAccount = delegate.getMailAccount(id, userId, contextId);
            cache.put(key, mailAccount, false);
            return mailAccount;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getByPrimaryAddress(String primaryAddress, int userId, int contextId) throws OXException {
        return delegate.getByPrimaryAddress(primaryAddress, userId, contextId);
    }

    @Override
    public int getTransportByPrimaryAddress(String primaryAddress, int userId, int contextId) throws OXException {
        return delegate.getTransportByPrimaryAddress(primaryAddress, userId, contextId);
    }

    @Override
    public TransportAccount getTransportByReference(String reference, int userId, int contextId) throws OXException {
        return delegate.getTransportByReference(reference, userId, contextId);
    }

    @Override
    public int[] getByHostNames(Collection<String> hostNames, int userId, int contextId) throws OXException {
        return delegate.getByHostNames(hostNames, userId, contextId);
    }

    @Override
    public MailAccount[] getUserMailAccounts(int userId, int contextId, Connection con) throws OXException {
        int[] ids = delegate.getUserMailAccountIDs(userId, contextId, con);
        MailAccount[] accounts = new MailAccount[ids.length];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = getMailAccount0(ids[i], userId, contextId, con);
        }
        return accounts;
    }

    @Override
    public TransportAccount[] getUserTransportAccounts(int userId, int contextId, Connection con) throws OXException {
        int[] ids = delegate.getUserTransportAccountIDs(userId, contextId, con);
        TransportAccount[] accounts = new TransportAccount[ids.length];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = getTransportAccount(ids[i], userId, contextId, con);
        }
        return accounts;
    }

    @Override
    public List<MailAccount> getUserMailAccounts(int contextId) throws OXException {
        return delegate.getUserMailAccounts(contextId);
    }

    @Override
    public MailAccount getRawMailAccount(int id, int userId, int contextId) throws OXException {
        return delegate.getRawMailAccount(id, userId, contextId);
    }

    @Override
    public MailAccount getMailAccount(int id, int userId, int contextId, Connection con) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getMailAccount(id, userId, contextId, con);
        }

        CacheKey key = newCacheKey(cacheService, id, userId, contextId);
        Cache cache = cacheService.getCache(REGION_NAME);
        Object object = cache.get(key);
        if (object instanceof MailAccount) {
            return (MailAccount) object;
        }

        MailAccount mailAccount = delegate.getMailAccount(id, userId, contextId, con);
        cache.put(key, mailAccount, false);
        return mailAccount;
    }

    private MailAccount getMailAccount0(int id, int userId, int contextId, Connection con) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getMailAccount(id, userId, contextId, con);
        }

        Cache cache = cacheService.getCache(REGION_NAME);
        CacheKey key = newCacheKey(cacheService, id, userId, contextId);
        Object object = cache.get(key);
        if (object instanceof MailAccount) {
            return (MailAccount) object;
        }

        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append("mailaccount-").append(contextId).append('-').append(userId).append('-').append(id).toString());
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof MailAccount) {
                return (MailAccount) object;
            }

            RdbMailAccountStorage d = delegate;
            cache.put(key, d.getMailAccount(id, userId, contextId), false);
            return d.getMailAccount(id, userId, contextId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public MailAccount[] getUserMailAccounts(int userId, int contextId) throws OXException {
        int[] ids;
        {
            CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
            if (cacheService == null) {
                ids = delegate.getUserMailAccountIDs(userId, contextId);
            } else {
                Cache cache = cacheService.getCache(REGION_NAME);
                CacheKey key = accountsCacheKey(cacheService, userId, contextId);
                Object object = cache.get(key);
                if (object instanceof int[]) {
                    ids = (int[]) object;
                } else {
                    ids = delegate.getUserMailAccountIDs(userId, contextId);
                    cache.put(key, ids, false);
                }
            }
        }

        MailAccount[] accounts = new MailAccount[ids.length];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = getMailAccount(ids[i], userId, contextId);
        }
        return accounts;
    }

    @Override
    public TransportAccount[] getUserTransportAccounts(int userId, int contextId) throws OXException {
        int[] ids = delegate.getUserTransportAccountIDs(userId, contextId);

        TransportAccount[] accounts = new TransportAccount[ids.length];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = getTransportAccount(ids[i], userId, contextId);
        }
        return accounts;
    }

    @Override
    public MailAccount[] resolveLogin(String login, int contextId) throws OXException {
        int[][] idsAndUsers = resolveFromCache(login, contextId, new FromDelegate() {

            @Override
            public int[][] getFromDelegate(String pattern, int contextId) throws OXException {
                return getDelegate().resolveLogin2IDs(pattern, contextId);
            }
        }, CachedResolveType.LOGIN);
        MailAccount[] accounts = new MailAccount[idsAndUsers.length];
        for (int i = 0; i < accounts.length; i++) {
            int[] idAndUser = idsAndUsers[i];
            accounts[i] = getMailAccount(idAndUser[0], idAndUser[1], contextId);
        }
        return accounts;
    }

    @Override
    public MailAccount[] resolveLogin(String login, String serverUrl, int contextId) throws OXException {
        int[][] idsAndUsers = resolveFromCache(login, contextId, new FromDelegate() {

            @Override
            public int[][] getFromDelegate(String pattern, int contextId) throws OXException {
                return getDelegate().resolveLogin2IDs(pattern, contextId);
            }
        }, CachedResolveType.LOGIN);
        List<MailAccount> l = new ArrayList<MailAccount>(idsAndUsers.length);
        for (int[] idAndUser : idsAndUsers) {
            MailAccount candidate = getMailAccount(idAndUser[0], idAndUser[1], contextId);
            if (serverUrl.equals(toSocketAddrString(candidate.generateMailServerURL(), 143))) {
                l.add(candidate);
            }
        }
        return l.toArray(new MailAccount[l.size()]);
    }

    @Override
    public void updateMailAccount(MailAccountDescription mailAccount, Set<Attribute> attributes, int userId, int contextId, Session session) throws OXException {
        if (null != session) {
            session.setParameter("com.openexchange.mailaccount.unifiedMailEnabled", null);
        }
        dropSessionParameter(userId, contextId);

        Connection con = Database.get(contextId, true);
        int rollback = 0;
        try {
            con.setAutoCommit(false);
            rollback = 1;
            updateMailAccount(mailAccount, attributes, userId, contextId, session, con, false);
            con.commit();
            rollback = 2;
        } catch (SQLException e) {
            throw MailAccountExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw MailAccountExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    rollback(con);
                }
                autocommit(con);
            }
            Database.back(contextId, true, con);
        }
    }

    @Override
    public void updateMailAccount(MailAccountDescription mailAccount, Set<Attribute> attributes, int userId, int contextId, Session session, Connection con, boolean changePrimary) throws OXException {
        UpdateProperties updateProperties = new UpdateProperties.Builder().setChangePrimary(changePrimary).setChangeProtocol(false).setCon(con).setSession(session).build();
        updateMailAccount(mailAccount, attributes, userId, contextId, updateProperties);
    }

    @Override
    public void updateMailAccount(MailAccountDescription mailAccount, Set<Attribute> attributes, int userId, int contextId, UpdateProperties updateProperties) throws OXException {
        Connection con = updateProperties == null ? null : updateProperties.getCon();
        Session session = updateProperties == null ? null : updateProperties.getSession();

        if (null != session) {
            session.setParameter("com.openexchange.mailaccount.unifiedMailEnabled", null);
        }
        dropSessionParameter(userId, contextId);

        delegate.updateMailAccount(mailAccount, attributes, userId, contextId, updateProperties);
        invalidateMailAccount(mailAccount.getId(), userId, contextId);

        if (null != con) {
            CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
            if (cacheService != null) {
                Cache cache = cacheService.getCache(REGION_NAME);
                CacheKey key = newCacheKey(cacheService, mailAccount.getId(), userId, contextId);
                MailAccount macc = delegate.getMailAccount(mailAccount.getId(), userId, contextId, con);
                cache.put(key, macc, false);
            }
        }
    }

    @Override
    public void updateMailAccount(MailAccountDescription mailAccount, int userId, int contextId, Session session) throws OXException {
        if (null != session) {
            session.setParameter("com.openexchange.mailaccount.unifiedMailEnabled", null);
        }
        dropSessionParameter(userId, contextId);

        MailAccount changedAccount = delegate.updateAndReturnMailAccount(mailAccount, userId, contextId, session);
        invalidateMailAccount(mailAccount.getId(), userId, contextId);

        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService != null) {
            Cache cache = cacheService.getCache(REGION_NAME);
            CacheKey key = newCacheKey(cacheService, mailAccount.getId(), userId, contextId);
            cache.put(key, changedAccount, false);
        }
    }

    @Override
    public void enableMailAccount(int accountId, int userId, int contextId) throws OXException {
        delegate.enableMailAccount(accountId, userId, contextId);
        invalidateMailAccount(accountId, userId, contextId);
    }

    @Override
    public void enableMailAccount(int accountId, int userId, int contextId, Connection con) throws OXException {
        delegate.enableMailAccount(accountId, userId, contextId, con);
        invalidateMailAccount(accountId, userId, contextId);
    }

    @Override
    public int insertMailAccount(MailAccountDescription mailAccount, int userId, Context ctx, Session session) throws OXException {
        int id = delegate.insertMailAccount(mailAccount, userId, ctx, session);
        invalidateMailAccount(id, userId, ctx.getContextId());
        return id;
    }

    @Override
    public int insertMailAccount(MailAccountDescription mailAccount, int userId, Context ctx, Session session, Connection con) throws OXException {
        int id = delegate.insertMailAccount(mailAccount, userId, ctx, session, con);
        invalidateMailAccount(id, userId, ctx.getContextId());
        return id;
    }

    @Override
    public MailAccount[] resolvePrimaryAddr(String primaryAddress, int contextId) throws OXException {
        int[][] idsAndUsers = resolveFromCache(primaryAddress, contextId, new FromDelegate() {

            @Override
            public int[][] getFromDelegate(String pattern, int contextId) throws OXException {
                return getDelegate().resolvePrimaryAddr2IDs(pattern, contextId);
            }
        }, CachedResolveType.PRIMARY_ADDRESS);
        List<MailAccount> l = new ArrayList<MailAccount>(idsAndUsers.length);
        for (int[] idAndUser : idsAndUsers) {
            l.add(getMailAccount(idAndUser[0], idAndUser[1], contextId));
        }
        return l.toArray(new MailAccount[l.size()]);
    }

    private static interface FromDelegate {

        int[][] getFromDelegate(String pattern, int contextId) throws OXException;
    }

    private static int[][] resolveFromCache(String pattern, int contextId, FromDelegate delegate, CachedResolveType type) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (null == cacheService) {
            return delegate.getFromDelegate(pattern, contextId);
        }
        Cache cache;
        try {
            cache = cacheService.getCache(REGION_NAME);
        } catch (OXException e) {
            cache = null;
        }
        if (null == cache) {
            return delegate.getFromDelegate(pattern, contextId);
        }
        int[][] idsAndUsers;
        CacheKey key = cacheService.newCacheKey(type.ordinal(), pattern);
        int[][] tmp;
        try {
            tmp = (int[][]) cache.getFromGroup(key, Integer.toString(contextId));
        } catch (ClassCastException e) {
            tmp = null;
        }
        if (null == tmp) {
            idsAndUsers = delegate.getFromDelegate(pattern, contextId);
            cache.putInGroup(key, Integer.toString(contextId), idsAndUsers, false);
        } else {
            idsAndUsers = tmp;
        }
        return idsAndUsers;
    }

    @Override
    public void migratePasswords(String oldSecret, String newSecret, Session session) throws OXException {
        delegate.migratePasswords(oldSecret, newSecret, session);
        invalidateMailAccounts(session.getUserId(), session.getContextId());
    }

    @Override
    public boolean hasAccounts(Session session) throws OXException {
        return delegate.hasAccounts(session);
    }

    @Override
    public void cleanUp(String secret, Session session) throws OXException {
        delegate.cleanUp(secret, session);
    }

    @Override
    public void removeUnrecoverableItems(String secret, Session session) throws OXException {
        delegate.removeUnrecoverableItems(secret, session);
    }

    @Override
    public int insertTransportAccount(TransportAccountDescription transportAccount, int userId, Context ctx, Session session) throws OXException {
        int id = delegate.insertTransportAccount(transportAccount, userId, ctx, session);
        invalidateMailAccount(id, userId, ctx.getContextId());
        return id;
    }

    @Override
    public void deleteTransportAccount(int id, int userId, int contextId) throws OXException {
        dropSessionParameter(userId, contextId);
        delegate.deleteTransportAccount(id, userId, contextId);
        invalidateMailAccount(id, userId, contextId);
    }

    @Override
    public void deleteTransportAccount(int id, int userId, int contextId, Connection con) throws OXException {
        dropSessionParameter(userId, contextId);
        delegate.deleteTransportAccount(id, userId, contextId, con);
        invalidateMailAccount(id, userId, contextId);
    }

    @Override
    public TransportAccount getTransportAccount(int accountId, int userId, int contextId, Connection con) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getTransportAccount(accountId, userId, contextId, con);
        }

        Cache cache = cacheService.getCache(REGION_NAME);
        CacheKey key = newCacheKey(cacheService, accountId, userId, contextId);
        Object object = cache.get(key);
        if (object instanceof TransportAccount) {
            return (TransportAccount) object;
        }

        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append("mailaccount-").append(contextId).append('-').append(userId).append('-').append(accountId).toString());
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof TransportAccount) {
                return (TransportAccount) object;
            }

            TransportAccount transportAccount = delegate.getTransportAccount(accountId, userId, contextId, con);
            cache.put(key, transportAccount, false);
            return transportAccount;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateTransportAccount(TransportAccountDescription transportAccount, int userId, int contextId, Session session) throws OXException {
        TransportAccount changedAccount = delegate.updateAndReturnTransportAccount(transportAccount, userId, contextId, session);
        invalidateMailAccount(transportAccount.getId(), userId, contextId);

        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService != null) {
            Cache cache = cacheService.getCache(REGION_NAME);
            CacheKey key = newCacheKey(cacheService, transportAccount.getId(), userId, contextId);
            cache.put(key, changedAccount, false);
        }
    }

    @Override
    public void updateTransportAccount(TransportAccountDescription transportAccount, Set<Attribute> attributes, int userId, int contextId, Session session) throws OXException {
        delegate.updateTransportAccount(transportAccount, attributes, userId, contextId, session);

        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService != null) {
            Cache cache = cacheService.getCache(REGION_NAME);
            CacheKey key = newCacheKey(cacheService, transportAccount.getId(), userId, contextId);
            Object object = cache.get(key);
            if (null != object) {
                cache.remove(key);
            }
        }
    }

    @Override
    public void updateTransportAccount(TransportAccountDescription transportAccount, Set<Attribute> attributes, int userId, int contextId, UpdateProperties updateProperties) throws OXException {
        delegate.updateTransportAccount(transportAccount, attributes, userId, contextId, updateProperties);

        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService != null) {
            Cache cache = cacheService.getCache(REGION_NAME);
            CacheKey key = newCacheKey(cacheService, transportAccount.getId(), userId, contextId);
            Object object = cache.get(key);
            if (null != object) {
                cache.remove(key);
            }
        }
    }

    @Override
    public TransportAccount getTransportAccount(int accountId, int userId, int contextId) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getTransportAccount(accountId, userId, contextId);
        }

        Cache cache = cacheService.getCache(REGION_NAME);
        CacheKey key = newCacheKey(cacheService, accountId, userId, contextId);
        Object object = cache.get(key);
        if (object instanceof TransportAccount) {
            return (TransportAccount) object;
        }

        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append("mailaccount-").append(contextId).append('-').append(userId).append('-').append(accountId).toString());
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof TransportAccount) {
                return (TransportAccount) object;
            }

            TransportAccount transportAccount = delegate.getTransportAccount(accountId, userId, contextId);
            cache.put(key, transportAccount, false);
            return transportAccount;
        } finally {
            lock.unlock();
        }
    }

}
