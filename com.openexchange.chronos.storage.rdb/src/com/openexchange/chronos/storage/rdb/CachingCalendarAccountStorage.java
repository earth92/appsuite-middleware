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

package com.openexchange.chronos.storage.rdb;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.I2i;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheKey;
import com.openexchange.caching.CacheService;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.DefaultCalendarAccount;
import com.openexchange.chronos.storage.CalendarAccountStorage;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.exception.OXException;

/**
 * {@link CachingCalendarAccountStorage}
 *
 * @author <a href="mailto:Jan-Oliver.Huhn@open-xchange.com">Jan-Oliver Huhn</a>
 * @since v7.10
 */
public class CachingCalendarAccountStorage implements CalendarAccountStorage {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CachingCalendarAccountStorage.class);
    private static final String REGION_NAME = "CalendarAccount";

    private final RdbCalendarAccountStorage delegate;
    private final int contextId;
    private final CacheService cacheService;
    private final Cache cache;

    /**
     * Initializes a new {@link CachingCalendarAccountStorage}.
     *
     * @param contextId The context identifier
     * @param delegate The underlying persistent account storage
     * @param cacheSerivce A reference to the cache service
     */
    public CachingCalendarAccountStorage(RdbCalendarAccountStorage delegate, int contextId, CacheService cacheService) throws OXException {
        super();
        this.delegate = delegate;
        this.contextId = contextId;
        this.cacheService = cacheService;
        this.cache = cacheService.getCache(REGION_NAME);
    }

    @Override
    public int nextId() throws OXException {
        return delegate.nextId();
    }

    @Override
    public void insertAccount(CalendarAccount account) throws OXException {
        delegate.insertAccount(account);
        invalidateAccount(account.getUserId(), -1);
    }

    @Override
    public void insertAccount(CalendarAccount account, int maxAccounts) throws OXException {
        delegate.insertAccount(account, maxAccounts);
        invalidateAccount(account.getUserId(), -1);
    }

    @Override
    public void updateAccount(CalendarAccount account, long clientTimestamp) throws OXException {
        delegate.updateAccount(account, clientTimestamp);
        invalidateAccount(account.getUserId(), account.getAccountId());
    }

    @Override
    public void deleteAccount(int userId, int accountId, long clientTimestamp) throws OXException {
        delegate.deleteAccount(userId, accountId, clientTimestamp);
        invalidateAccount(userId, accountId);
    }

    @Override
    public CalendarAccount loadAccount(int userId, int accountId) throws OXException {
        if (bypassCache()) {
            return delegate.loadAccount(userId, accountId);
        }
        CacheKey key = getAccountKey(userId, accountId);
        CalendarAccount account = optClonedAccount(cache.get(key));
        if (null == account) {
            account = delegate.loadAccount(userId, accountId);
            if (null != account) {
                cache.put(key, clone(account), false);
            }
        }
        return account;
    }

    @Override
    public CalendarAccount[] loadAccounts(int userId, int[] accountIds) throws OXException {
        if (bypassCache()) {
            return delegate.loadAccounts(userId, accountIds);
        }
        List<Integer> accountsToLoad = new ArrayList<Integer>(accountIds.length);
        CalendarAccount[] accounts = new CalendarAccount[accountIds.length];
        for (int i = 0; i < accountIds.length; i++) {
            CacheKey key = getAccountKey(userId, accountIds[i]);
            CalendarAccount account = optClonedAccount(cache.get(key));
            if (null == account) {
                accountsToLoad.add(I(accountIds[i]));
            } else {
                accounts[i] = account;
            }
        }
        if (0 < accountsToLoad.size()) {
            for (CalendarAccount account : delegate.loadAccounts(userId, I2i(accountsToLoad))) {
                if (null == account) {
                    continue;
                }
                cache.put(getAccountKey(userId, account.getAccountId()), clone(account), false);
                for (int i = 0; i < accountIds.length; i++) {
                    if (accountIds[i] == account.getAccountId()) {
                        accounts[i] = account;
                        break;
                    }
                }
            }
        }
        return accounts;
    }

    @Override
    public List<CalendarAccount> loadAccounts(int userId) throws OXException {
        if (bypassCache()) {
            return delegate.loadAccounts(userId);
        }
        /*
         * try and get accounts via cached account id list for user
         */
        CacheKey accountIdsKey = getAccountIdsKey(userId);
        int[] accountIds = optClonedAccountIds(cache.get(accountIdsKey));
        if (null != accountIds) {
            List<CalendarAccount> accounts = new ArrayList<CalendarAccount>(accountIds.length);
            for (CalendarAccount account : loadAccounts(userId, accountIds)) {
                if (null == account) {
                    /*
                     * stale reference in cached user's account list, invalidate & try again
                     */
                    LOG.warn("Detected stale reference in account list for user {} in context {}, invalidating cache.", I(userId), I(contextId));
                    cache.remove(accountIdsKey);
                    return loadAccounts(userId);
                }
                accounts.add(account);
            }
            return accounts;
        }
        /*
         * get account list from storage & put into cache
         */
        List<CalendarAccount> accounts = delegate.loadAccounts(userId);
        accountIds = new int[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            CalendarAccount account = accounts.get(i);
            accountIds[i] = account.getAccountId();
            cache.put(getAccountKey(userId, account.getAccountId()), clone(account), false);
        }
        cache.put(accountIdsKey, accountIds, false);
        return accounts;
    }

    @Override
    public List<CalendarAccount> loadAccounts(int[] userIds, String providerId) throws OXException {
        if (null == userIds || 0 == userIds.length) {
            return Collections.emptyList();
        }
        if (bypassCache()) {
            return delegate.loadAccounts(userIds, providerId);
        }
        List<CalendarAccount> matchingAccounts = new ArrayList<CalendarAccount>();
        /*
         * try and get matching accounts for user from cache
         */
        List<Integer> usersToLoad = new ArrayList<Integer>(userIds.length);
        for (int userId : userIds) {
            List<CalendarAccount> cachedAccounts = optCachedAccounts(userId);
            if (null == cachedAccounts) {
                usersToLoad.add(I(userId));
            } else {
                for (CalendarAccount cachedAccount : cachedAccounts) {
                    if (cachedAccount.getProviderId().equals(providerId)) {
                        matchingAccounts.add(cachedAccount);
                    }
                }
            }
        }
        /*
         * load matching accounts of remaining users from storage
         */
        if (0 < usersToLoad.size()) {
            matchingAccounts.addAll(delegate.loadAccounts(I2i(usersToLoad), providerId));
        }
        return matchingAccounts;
    }

    @Override
    public CalendarAccount loadAccount(int userId, String providerId) throws OXException {
        //TODO: from cache / put result in cache?
        return delegate.loadAccount(userId, providerId);
    }

    @Override
    public List<CalendarAccount> loadAccounts(int userId, String... providerIds) throws OXException {
        return delegate.loadAccounts(userId, providerIds);
    }

    @Override
    public List<CalendarAccount> loadAccounts(String... providerIds) throws OXException {
        return delegate.loadAccounts(providerIds);
    }

    @Override
    public void invalidateAccount(int userId, int accountId) throws OXException {
        if (-1 == accountId) {
            cache.remove(getAccountIdsKey(userId));
        } else {
            cache.remove(Arrays.asList(new Serializable[] { getAccountIdsKey(userId), getAccountKey(userId, accountId) }));
        }
    }

    private CacheKey getAccountKey(int userId, int accountId) {
        String[] keys = new String[] { String.valueOf(userId), String.valueOf(accountId) };
        return cacheService.newCacheKey(contextId, keys);
    }

    private CacheKey getAccountIdsKey(int userId) {
        return cacheService.newCacheKey(contextId, userId);
    }

    private boolean bypassCache() {
        return DBTransactionPolicy.NO_TRANSACTIONS.equals(delegate.getTransactionPolicy());
    }

    private List<CalendarAccount> optCachedAccounts(int userId) throws OXException {
        CacheKey accountIdsKey = getAccountIdsKey(userId);
        int[] accountIds = optClonedAccountIds(cache.get(accountIdsKey));
        if (null == accountIds) {
            return null;
        }
        List<CalendarAccount> accounts = new ArrayList<CalendarAccount>(accountIds.length);
        for (int accountId : accountIds) {
            CacheKey key = getAccountKey(userId, accountId);
            CalendarAccount account = optClonedAccount(cache.get(key));
            if (null == account) {
                return null;
            }
            accounts.add(account);
        }
        return accounts;
    }

    private static CalendarAccount optClonedAccount(Object cachedAccount) throws OXException {
        return null != cachedAccount && CalendarAccount.class.isInstance(cachedAccount) ? clone((CalendarAccount) cachedAccount) : null;
    }

    private static int[] optClonedAccountIds(Object cachedAccountIds) {
        return null != cachedAccountIds && int[].class.isInstance(cachedAccountIds) ? clone((int[]) cachedAccountIds) : null;
    }

    private static CalendarAccount clone(CalendarAccount account) throws OXException {
        try {
            JSONObject internalConfig = null == account.getInternalConfiguration() ? null : new JSONObject(account.getInternalConfiguration().toString());
            JSONObject userConfig = null == account.getUserConfiguration() ? null : new JSONObject(account.getUserConfiguration().toString());
            return new DefaultCalendarAccount(account.getProviderId(), account.getAccountId(), account.getUserId(), internalConfig, userConfig, account.getLastModified());
        } catch (JSONException e) {
            throw CalendarExceptionCodes.DB_ERROR.create(e, e.getMessage());
        }
    }

    private static int[] clone(int[] accountIds) {
        return accountIds.clone();
    }

}
