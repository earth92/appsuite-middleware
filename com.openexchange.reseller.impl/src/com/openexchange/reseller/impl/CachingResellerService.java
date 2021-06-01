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

package com.openexchange.reseller.impl;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheService;
import com.openexchange.exception.OXException;
import com.openexchange.lock.LockService;
import com.openexchange.reseller.ResellerService;
import com.openexchange.reseller.data.ResellerAdmin;
import com.openexchange.reseller.data.ResellerCapability;
import com.openexchange.reseller.data.ResellerConfigProperty;
import com.openexchange.reseller.data.ResellerTaxonomy;
import com.openexchange.server.ServiceLookup;

/**
 * {@link CachingResellerService} - Wraps reseller service implementation with caches for fast look-up.
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.5
 */
public class CachingResellerService implements ResellerService {

    /** Caches a reverse index for the contextId-resellerId. Stores {@link ResellerAdmin}s */
    public static final String RESELLER_CONTEXT_NAME = "ResellerContext";

    /** Stores {@link Set<ResellerCapability>} entries cached by resellerId. */
    private static final String CAPABILITIES_REGION_NAME = "CapabilitiesReseller";

    /** Stores {@link Map<String, ResellerConfigProperty>} entries cached by resellerId. */
    private static final String CONFIGURATION_REGION_NAME = "ConfigurationReseller";

    /** Stores {@link Set<ResellerTaxonomy>} entries cached by resellerId. */
    private static final String TAXONOMIES_REGION_NAME = "TaxonomiesReseller";

    /** Stores {@link ResellerAdmin} entries cached by resellerId. */
    private static final String RESELLER_ADMIN_NAME = "ResellerAdmin";

    /** The default reseller admin for contexts that are not assigned to any reseller. */
    private static ResellerAdmin DEFAULT = ResellerAdmin.builder().id(I(-1)).parentId(I(-1)).name("default").build();

    // -------------------------------------------------------------------------------------------------------------------------------------

    private final ResellerServiceImpl delegate;
    private final ServiceLookup services;

    /**
     * Initializes a new {@link CachingResellerService}.
     */
    public CachingResellerService(ServiceLookup services, ResellerServiceImpl delegate) {
        super();
        this.services = services;
        this.delegate = delegate;
    }

    @Override
    public ResellerAdmin getReseller(int contextId) throws OXException {
        Cache cache = getCacheService().getCache(RESELLER_CONTEXT_NAME);
        Integer key = I(contextId);
        Object candidate = cache.get(key);
        if (candidate instanceof ResellerAdmin) {
            return ResellerAdmin.class.cast(candidate);
        }

        Lock lock = optSelfCleaningLockFor("getReseller-" + contextId);
        lock.lock();
        try {
            candidate = cache.get(key);
            if (candidate instanceof ResellerAdmin) {
                return ResellerAdmin.class.cast(candidate);
            }

            ResellerAdmin resellerAdmin = delegate.getReseller(contextId);
            cache.put(key, resellerAdmin, false);
            return resellerAdmin;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ResellerAdmin getResellerById(int resellerId) throws OXException {
        Cache cache = getCacheService().getCache(RESELLER_ADMIN_NAME);
        Integer key = I(resellerId);
        Object candidate = cache.get(key);
        if (candidate instanceof ResellerAdmin) {
            return ResellerAdmin.class.cast(candidate);
        }
        Lock lock = optSelfCleaningLockFor("getResellerById-" + resellerId);
        lock.lock();
        try {
            candidate = cache.get(key);
            if (candidate instanceof ResellerAdmin) {
                return ResellerAdmin.class.cast(candidate);
            }

            ResellerAdmin admin = delegate.getResellerById(resellerId);
            cache.put(key, admin, false);
            return admin;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ResellerAdmin getResellerByName(String resellerName) throws OXException {
        return delegate.getResellerByName(resellerName);
    }

    @Override
    public List<ResellerAdmin> getResellerAdminPath(int contextId) throws OXException {
        return delegate.getResellerAdminPath(contextId);
    }

    @Override
    public List<ResellerAdmin> getSubResellers(int parentId) throws OXException {
        return delegate.getSubResellers(parentId);
    }

    @Override
    public List<ResellerAdmin> getAll() throws OXException {
        return delegate.getAll();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<ResellerCapability> getCapabilities(int resellerId) throws OXException {
        Cache cache = getCacheService().getCache(CAPABILITIES_REGION_NAME);
        Integer key = I(resellerId);
        Object object = cache.get(key);
        if (object instanceof Set) {
            return Set.class.cast(object);
        }
        Lock lock = optSelfCleaningLockFor("getResellerCapabilities-" + resellerId);
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof Set) {
                return Set.class.cast(object);
            }
            Set<ResellerCapability> capas = delegate.getCapabilities(resellerId);
            cache.put(key, (Serializable) capas, false);
            return capas;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Set<ResellerCapability> getCapabilitiesByContext(int contextId) throws OXException {
        ResellerValue resellerValue = optResellerValue(contextId);
        if (DEFAULT.getId().equals(resellerValue.getResellerId())) {
            return ImmutableSet.of();
        }
        Integer resellerId = resellerValue.getResellerId();
        Integer parentId = resellerValue.getParentId();

        Set<ResellerCapability> capabilities = getCapabilities(i(resellerId));
        if (parentId == null || parentId.intValue() == 0) {
            // Context is assigned to the root reseller, just return capas
            return capabilities;
        }

        // Traverse the admin path to get all capabilities for the context
        capabilities = new HashSet<>(capabilities);
        do {
            ResellerAdmin resellerAdmin = getResellerById(i(parentId));
            capabilities.addAll(getCapabilities(i(resellerAdmin.getId())));
            parentId = resellerAdmin.getParentId();
        } while (parentId != null && parentId.intValue() > 0);
        return capabilities;
    }

    @Override
    public ResellerConfigProperty getConfigProperty(int resellerId, String key) throws OXException {
        return getAllConfigProperties(resellerId).get(key);
    }

    @Override
    public ResellerConfigProperty getConfigPropertyByContext(int contextId, String key) throws OXException {
        return getAllConfigPropertiesByContext(contextId).get(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, ResellerConfigProperty> getAllConfigProperties(int resellerId) throws OXException {
        Cache cache = getCacheService().getCache(CONFIGURATION_REGION_NAME);
        Integer key = I(resellerId);
        Object object = cache.get(key);
        if (object instanceof Map) {
            return Map.class.cast(object);
        }

        Lock lock = optSelfCleaningLockFor("getAllConfigProperties-" + resellerId);
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof Map) {
                return Map.class.cast(object);
            }
            Map<String, ResellerConfigProperty> props = delegate.getAllConfigProperties(resellerId);
            cache.put(key, (Serializable) props, false);
            return props;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<String, ResellerConfigProperty> getAllConfigPropertiesByContext(int contextId) throws OXException {
        ResellerValue resellerValue = optResellerValue(contextId);
        if (DEFAULT.getId().equals(resellerValue.getResellerId())) {
            return ImmutableMap.of();
        }
        Integer resellerId = resellerValue.getResellerId();
        Integer parentId = resellerValue.getParentId();

        Map<String, ResellerConfigProperty> properties = getAllConfigProperties(i(resellerId));
        if (parentId == null || parentId.intValue() == 0) {
            // Context is assigned to the root reseller, just return props
            return properties;
        }

        // Traverse the admin path to get all properties for the context
        properties = new HashMap<>(properties);
        do {
            ResellerAdmin resellerAdmin = getResellerById(i(parentId));
            properties.putAll(getAllConfigProperties(i(resellerAdmin.getId())));
            parentId = resellerAdmin.getParentId();
        } while (parentId != null && parentId.intValue() > 0);
        return properties;
    }

    @Override
    public Map<String, ResellerConfigProperty> getConfigProperties(int resellerId, Set<String> keys) throws OXException {
        if (keys == null) {
            return Collections.emptyMap();
        }
        int numberOfKeys = keys.size();
        if (numberOfKeys <= 0) {
            return Collections.emptyMap();
        }
        Map<String, ResellerConfigProperty> properties = getAllConfigProperties(resellerId);
        Map<String, ResellerConfigProperty> ret = null;
        for (String key : keys) {
            ResellerConfigProperty property = properties.get(key);
            if (property != null) {
                if (ret == null) {
                    ret = new HashMap<>(numberOfKeys);
                }
                ret.put(key, property);
            }
        }
        return ret == null ? Collections.emptyMap() : ret;
    }

    @Override
    public Map<String, ResellerConfigProperty> getConfigPropertiesByContext(int contextId, Set<String> keys) throws OXException {
        if (keys == null) {
            return Collections.emptyMap();
        }
        int numberOfKeys = keys.size();
        if (numberOfKeys <= 0) {
            return Collections.emptyMap();
        }
        Map<String, ResellerConfigProperty> configuration = getAllConfigPropertiesByContext(contextId);
        Map<String, ResellerConfigProperty> ret = null;
        for (String key : keys) {
            ResellerConfigProperty property = configuration.get(key);
            if (property != null) {
                if (ret == null) {
                    ret = new HashMap<>(numberOfKeys);
                }
                ret.put(key, property);
            }
        }
        return ret == null ? Collections.emptyMap() : ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<ResellerTaxonomy> getTaxonomies(int resellerId) throws OXException {
        Cache cache = getCacheService().getCache(TAXONOMIES_REGION_NAME);
        Integer key = I(resellerId);
        Object object = cache.get(key);
        if (object instanceof Set) {
            return Set.class.cast(object);
        }

        Lock lock = optSelfCleaningLockFor("getTaxonomies-" + resellerId);
        lock.lock();
        try {
            object = cache.get(key);
            if (object instanceof Map) {
                return Set.class.cast(object);
            }
            Set<ResellerTaxonomy> taxonomies = delegate.getTaxonomies(resellerId);
            cache.put(key, (Serializable) taxonomies, false);
            return taxonomies;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Set<ResellerTaxonomy> getTaxonomiesByContext(int contextId) throws OXException {
        ResellerValue resellerValue = optResellerValue(contextId);
        if (DEFAULT.getId().equals(resellerValue.getResellerId())) {
            return ImmutableSet.of();
        }
        Integer resellerId = resellerValue.getResellerId();
        Integer parentId = resellerValue.getParentId();

        Set<ResellerTaxonomy> taxonomies = getTaxonomies(i(resellerId));
        if (parentId == null || parentId.intValue() == 0) {
            // Context is assigned to the root reseller, just return taxonomies
            return taxonomies;
        }

        // Traverse the admin path to get all taxonomies for the context
        taxonomies = new HashSet<>(taxonomies);
        do {
            ResellerAdmin resellerAdmin = getResellerById(i(parentId));
            taxonomies.addAll(getTaxonomies(i(resellerAdmin.getId())));
            parentId = resellerAdmin.getParentId();
        } while (parentId != null && parentId.intValue() > 0);
        return taxonomies;
    }

    ////////////////////////////////// HELPERS ///////////////////////////

    /**
     * Optionally gets the {@link LockService} lock or dummy lock if the service is absent.
     *
     * @param identifier The lock identifier
     * @return the {@link Lock} or <code>null</code> if the service is absent.
     * @throws OXException If lock cannot be returned
     */
    private Lock optSelfCleaningLockFor(String lockId) throws OXException {
        LockService lockService = services.getOptionalService(LockService.class);
        return null == lockService ? LockService.EMPTY_LOCK : lockService.getSelfCleaningLockFor(new StringBuilder(32).append(lockId).append("-").toString());
    }

    /**
     * Optionally retrieves the cached {@link ResellerValue} for the specified context
     *
     * @param contextId The context identifier
     * @return The {@link ResellerValue}
     * @throws OXException if an error is occurred
     */
    private ResellerValue optResellerValue(int contextId) throws OXException {
        ResellerAdmin resellerAdmin = optReseller(contextId);
        return new ResellerValue(resellerAdmin.getId(), resellerAdmin.getParentId());
    }

    /**
     * Optionally retrieves the cached {@link ResellerAdmin} for the specified context
     *
     * @param contextId the context identifier
     * @return The reseller admin
     * @throws OXException if an error is occurred
     */
    private ResellerAdmin optReseller(int contextId) throws OXException {
        Cache cache = getCacheService().getCache(RESELLER_CONTEXT_NAME);
        Integer key = I(contextId);
        Object candidate = cache.get(key);
        if (candidate instanceof ResellerAdmin) {
            return ResellerAdmin.class.cast(candidate);
        }

        Lock lock = optSelfCleaningLockFor("optReseller-" + contextId);
        lock.lock();
        try {
            candidate = cache.get(key);
            if (candidate instanceof ResellerAdmin) {
                return ResellerAdmin.class.cast(candidate);
            }

            ResellerAdmin resellerAdmin = delegate.optResellerAdmin(contextId, null);
            if (null == resellerAdmin) {
                // Yes, fall-back to the DEFAULT reseller admin, as we don't want to
                // look-up the DB for the same unassigned context.
                resellerAdmin = DEFAULT;
            }
            cache.put(key, resellerAdmin, false);
            return resellerAdmin;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the {@link CacheService}
     *
     * @return the {@link CacheService}
     * @throws OXException if the service is absent.
     */
    private CacheService getCacheService() throws OXException {
        return services.getServiceSafe(CacheService.class);
    }
}
