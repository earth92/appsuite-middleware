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

package com.openexchange.groupware.alias.impl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import com.google.common.collect.ImmutableSet;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheKey;
import com.openexchange.caching.CacheService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.alias.UserAliasStorage;
import com.openexchange.lock.LockService;
import com.openexchange.server.services.ServerServiceRegistry;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * {@link CachingAliasStorage}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.0
 */
public class CachingAliasStorage implements UserAliasStorage {

    private static final String REGION_NAME = "UserAlias";

    static CacheKey newCacheKey(CacheService cacheService, int userId, int contextId) {
        return cacheService.newCacheKey(contextId, userId);
    }

    // -----------------------------------------------------------------------------------------------------------------------------

    /** Proxy attribute for the object implementing the persistent methods. */
    private final RdbAliasStorage delegate;

    /**
     * Initializes a new {@link CachingAliasStorage}.
     */
    public CachingAliasStorage(RdbAliasStorage delegate) {
        super();
        this.delegate = delegate;
    }

    @Override
    public void invalidateAliases(int contextId, int userId) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (null != cacheService) {
            Cache cache = cacheService.getCache(REGION_NAME);
            cache.remove(newCacheKey(cacheService, userId, contextId));
        }
    }

    @Override
    public Set<String> getAliases(int contextId) throws OXException {
        return delegate.getAliases(contextId);
    }

    @Override
    public Set<String> getAliases(int contextId, int userId) throws OXException {
        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getAliases(contextId, userId);
        }

        Cache cache = cacheService.getCache(REGION_NAME);
        CacheKey key = newCacheKey(cacheService, userId, contextId);
        Object object = cache.get(key);
        if (object instanceof Set) {
            return (Set<String>) object;
        }

        LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
        Lock lock = null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append("loadaliases-").append(contextId).append('-').append(userId).toString());
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof Set) {
                return (Set<String>) object;
            }

            ImmutableSet<String> aliases = delegate.getAliases(contextId, userId);
            cache.put(key, aliases, false);
            return aliases;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Set<String>> getAliases(int contextId, int... userIds) throws OXException {
        if (null == userIds) {
            return Collections.emptyList();
        }

        int length = userIds.length;
        if (length == 0) {
            return Collections.emptyList();
        }

        if (1 == length) {
            return Collections.<Set<String>> singletonList(getAliases(contextId, userIds[0]));
        }

        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (cacheService == null) {
            return delegate.getAliases(contextId, userIds);
        }

        Cache cache = cacheService.getCache(REGION_NAME);
        TIntObjectMap<Set<String>> map = new TIntObjectHashMap<>(length);
        TIntList toLoad = new TIntArrayList(length);
        for (int userId : userIds) {
            CacheKey key = newCacheKey(cacheService, userId, contextId);
            Object object = cache.get(key);
            if (object instanceof Set) {
                map.put(userId, (Set<String>) object);
            } else {
                toLoad.add(userId);
            }
        }

        if (!toLoad.isEmpty()) {
            TIntObjectMap<ImmutableSet<String>> loaded = delegate.getAliasesMapping(contextId, toLoad.toArray());
            map.putAll(loaded);

            LockService lockService = ServerServiceRegistry.getInstance().getService(LockService.class);
            TIntObjectIterator<ImmutableSet<String>> iterator = loaded.iterator();
            for (int i = loaded.size(); i-- > 0;) {
                iterator.advance();

                int userId = iterator.key();
                Lock lock = null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append("loadaliases-").append(contextId).append('-').append(userId).toString());
                lock.lock();
                try {
                    CacheKey key = newCacheKey(cacheService, userId, contextId);
                    Object object = cache.get(key);
                    if (!(object instanceof Set)) {
                        ImmutableSet<String> aliases = iterator.value();
                        cache.put(key, aliases, false);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        List<Set<String>> list = new ArrayList<>(length);
        for (int userId : userIds) {
            list.add(map.get(userId));
        }
        return list;
    }

    @Override
    public int getUserId(int contextId, String alias) throws OXException {
        return delegate.getUserId(contextId, alias);
    }

    @Override
    public void setAliases(Connection con, int contextId, int userId, Set<String> aliases) throws OXException {
        Set<String> setAliases = delegate.setAndGetAliases(con, contextId, userId, aliases);

        CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
        if (null != cacheService) {
            Cache cache = cacheService.getCache(REGION_NAME);
            CacheKey key = newCacheKey(cacheService, userId, contextId);
            ImmutableSet<String> newAliases = ImmutableSet.copyOf(setAliases);
            cache.put(key, newAliases, true);
        }
    }

    @Override
    public boolean createAlias(Connection con, int contextId, int userId, String alias) throws OXException {
        boolean success = delegate.createAlias(con, contextId, userId, alias);
        if (success) {
            CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
            if (null != cacheService) {
                Cache cache = cacheService.getCache(REGION_NAME);
                CacheKey key = newCacheKey(cacheService, userId, contextId);
                Object object = cache.get(key);
                if (object instanceof Set) {
                    ImmutableSet<String> newAliases = ImmutableSet.<String> builder().addAll((Set<String>) object).add(alias).build();
                    cache.put(key, newAliases, true);
                } else {
                    cache.put(key, ImmutableSet.<String> builder().add(alias).build(), true);
                }
            }
        }
        return success;
    }

    @Override
    public boolean updateAlias(Connection con, int contextId, int userId, String oldAlias, String newAlias) throws OXException {
        boolean success = delegate.updateAlias(con, contextId, userId, oldAlias, newAlias);
        if (success) {
            CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
            if (null != cacheService) {
                Cache cache = cacheService.getCache(REGION_NAME);
                CacheKey key = newCacheKey(cacheService, userId, contextId);
                Object object = cache.get(key);
                if (object instanceof Set) {
                    Set<String> newAliases = new LinkedHashSet<>((Set<String>) object);
                    newAliases.remove(oldAlias);
                    newAliases.add(newAlias);
                    cache.put(key, ImmutableSet.copyOf(newAliases), true);
                } else {
                    cache.remove(key);
                }
            }
        }
        return success;
    }

    @Override
    public boolean deleteAlias(Connection con, int contextId, int userId, String alias) throws OXException {
        boolean success = delegate.deleteAlias(con, contextId, userId, alias);
        if (success) {
            CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
            if (null != cacheService) {
                Cache cache = cacheService.getCache(REGION_NAME);
                CacheKey key = newCacheKey(cacheService, userId, contextId);
                Object object = cache.get(key);
                if (object instanceof Set) {
                    Set<String> newAliases = new LinkedHashSet<>((Set<String>) object);
                    newAliases.remove(alias);
                    cache.put(key, ImmutableSet.copyOf(newAliases), true);
                } else {
                    cache.remove(key);
                }
            }
        }
        return success;
    }

    @Override
    public boolean deleteAliases(Connection con, int contextId, int userId) throws OXException {
        boolean success = delegate.deleteAliases(con, contextId, userId);
        if (success) {
            CacheService cacheService = ServerServiceRegistry.getInstance().getService(CacheService.class);
            if (null != cacheService) {
                Cache cache = cacheService.getCache(REGION_NAME);
                CacheKey key = newCacheKey(cacheService, userId, contextId);
                Object object = cache.get(key);
                if (object instanceof Set) {
                    cache.put(key, ImmutableSet.<String> builder().build(), true);
                } else {
                    cache.remove(key);
                }
            }
        }
        return success;
    }

    @Override
    public List<Integer> getUserIdsByAliasDomain(int contextId, String domain) throws OXException {
        return delegate.getUserIdsByAliasDomain(contextId, domain);
    }
}
