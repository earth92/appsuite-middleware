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

package com.openexchange.caching.internal;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.caching.Cache;
import com.openexchange.caching.CacheElement;
import com.openexchange.caching.CacheKey;
import com.openexchange.caching.CacheStatistics;
import com.openexchange.caching.ElementAttributes;
import com.openexchange.caching.ThreadLocalConditionHolder;
import com.openexchange.caching.events.CacheEvent;
import com.openexchange.caching.events.CacheEventService;
import com.openexchange.caching.events.CacheListener;
import com.openexchange.caching.events.Condition;
import com.openexchange.caching.events.ConditionalCacheEvent;
import com.openexchange.exception.OXException;

/**
 * {@link NotifyingCache}
 *
 * {@link Cache} implementation that notifies listeners about specific cache operations on it's delegate, and performs the necessary
 * invalidation operations upon event retrieval.
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class NotifyingCache extends AbstractCache implements CacheListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotifyingCache.class);

    private final Cache delegate;
    private final String region;
    private final CacheEventService eventService;
    private final boolean notifyOnLocalOperations;

    /**
     * Initializes a new {@link NotifyingCache}.
     *
     * @param region The cache region name
     * @param delegate The underlying cache
     * @param eventService A reference to the cache event service
     * @param notifyOnLocalOperations Whether to notify on local-only operations, too, or not
     */
    public NotifyingCache(String region, Cache delegate, CacheEventService eventService, boolean notifyOnLocalOperations) {
        super();
        this.region = region;
        this.delegate = delegate;
        this.eventService = eventService;
        this.notifyOnLocalOperations = notifyOnLocalOperations;
        if (null != eventService && (notifyOnLocalOperations || false == isLocal())) {
            eventService.addListener(region, this);
        }
    }

    /**
     * Initializes a new {@link NotifyingCache}.
     *
     * @param region The cache region name
     * @param delegate The underlying cache
     * @param eventService A reference to the cache listener eventService
     */
    public NotifyingCache(String region, Cache delegate, CacheEventService notifier) {
        this(region, delegate, notifier, false);
    }

    /**
     * Gets a value indicating whether notifications on local-only operations are enabled or not.
     *
     * @return <code>true</code>, if local operations lead to notifications, <code>false</code>, otherwise
     */
    public boolean isNotifyOnLocalOperations() {
        return this.notifyOnLocalOperations;
    }

    @Override
    public boolean isDistributed() {
        return delegate.isDistributed();
    }

    @Override
    public boolean isReplicated() {
        return delegate.isReplicated();
    }

    @Override
    public Collection<Serializable> values() {
        return delegate.values();
    }

    @Override
    public boolean isLocal() {
        return delegate.isLocal();
    }

    @Override
    public void clear() throws OXException {
        delegate.clear();
        fireClear();
    }

    @Override
    public void localClear() throws OXException {
        delegate.localClear();
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    @Override
    public Object get(Serializable key) {
        return delegate.get(key);
    }

    @Override
    public CacheElement getCacheElement(Serializable key) {
        return delegate.getCacheElement(key);
    }

    @Override
    public ElementAttributes getDefaultElementAttributes() throws OXException {
        return delegate.getDefaultElementAttributes();
    }

    @Override
    public Object getFromGroup(Serializable key, String group) {
        return delegate.getFromGroup(key, group);
    }

    @Override
    public void invalidateGroup(String group) {
        delegate.invalidateGroup(group);
        fireInvalidateGroup(group);
    }

    @Override
    public void put(Serializable key, Serializable obj) throws OXException {
        put(key, obj, true);
    }

    @Override
    public void put(Serializable key, Serializable obj, boolean invalidate) throws OXException {
        delegate.put(key, obj, false);
        if (invalidate) {
            fireInvalidate(key);
        }
    }

    @Override
    public void put(Serializable key, Serializable val, ElementAttributes attr) throws OXException {
        put(key, val, attr, true);
    }

    @Override
    public void put(Serializable key, Serializable val, ElementAttributes attr, boolean invalidate) throws OXException {
        delegate.put(key, val, attr, false);
        if (invalidate) {
            fireInvalidate(key);
        }
    }

    @Override
    public void putInGroup(Serializable key, String groupName, Object value, ElementAttributes attr) throws OXException {
        putInGroup(key, groupName, value, attr, true);
    }

    @Override
    public void putInGroup(Serializable key, String groupName, Object value, ElementAttributes attr, boolean invalidate) throws OXException {
        delegate.putInGroup(key, groupName, value, attr, false);
        if (invalidate) {
            fireInvalidate(key, groupName);
        }
    }

    @Override
    public void putInGroup(Serializable key, String groupName, Serializable value) throws OXException {
        putInGroup(key, groupName, value, true);
    }

    @Override
    public void putInGroup(Serializable key, String groupName, Serializable value, boolean invalidate) throws OXException {
        delegate.putInGroup(key, groupName, value, false);
        if (invalidate) {
            fireInvalidate(key, groupName);
        }
    }

    @Override
    public void putSafe(Serializable key, Serializable value) throws OXException {
        delegate.putSafe(key, value);
    }

    @Override
    public void remove(Serializable key) throws OXException {
        delegate.remove(key);
        fireInvalidate(key);
    }

    @Override
    public void remove(List<Serializable> keys) throws OXException {
        delegate.remove(keys);
        fireInvalidate(keys);
    }

    @Override
    public void localRemove(Serializable key) throws OXException {
        delegate.localRemove(key);
    }

    @Override
    public void localPut(Serializable key, Serializable value) throws OXException {
        delegate.localPut(key, value);
    }

    @Override
    public void removeFromGroup(Serializable key, String group) {
        delegate.removeFromGroup(key, group);
        fireInvalidate(key, group);
    }

    @Override
    public void removeFromGroup(List<Serializable> keys, String group) {
        delegate.removeFromGroup(keys, group);
        fireInvalidate(keys, group);
    }

    @Override
    public void localRemoveFromGroup(Serializable key, String group) {
        delegate.localRemoveFromGroup(key, group);
    }

    @Override
    public void setDefaultElementAttributes(ElementAttributes attr) throws OXException {
        delegate.setDefaultElementAttributes(attr);
    }

    @Override
    public CacheStatistics getStatistics() {
        return delegate.getStatistics();
    }

    @Override
    public CacheKey newCacheKey(int contextId, int objectId) {
        return delegate.newCacheKey(contextId, objectId);
    }

    @Override
    public CacheKey newCacheKey(int contextId, String... objs) {
        return delegate.newCacheKey(contextId, objs);
    }

    @Override
    public Set<?> getGroupKeys(String group) throws OXException {
        return delegate.getGroupKeys(group);
    }

    @Override
    public Set<String> getGroupNames() throws OXException {
        return delegate.getGroupNames();
    }

    @Override
    public Set<?> getAllKeys() throws OXException {
        return delegate.getAllKeys();
    }

    @Override
    public Set<?> getKeysInRange(int start, int end) throws OXException {
        return delegate.getKeysInRange(start, end);
    }

    @Override
    public void onEvent(Object sender, CacheEvent cacheEvent, boolean fromRemote) {
        if (fromRemote && sender != this && null != cacheEvent) {
            LOG.debug("Received remote cache event: {}", cacheEvent);
            try {
                switch (cacheEvent.getOperation()) {
                case INVALIDATE_GROUP:
                    delegate.invalidateGroup(cacheEvent.getGroupName());
                    break;
                case INVALIDATE:
                    if (null != cacheEvent.getGroupName()) {
                        delegate.removeFromGroup(cacheEvent.getKeys(), cacheEvent.getGroupName());
                    } else {
                        delegate.remove(cacheEvent.getKeys());
                    }
                    break;
                case CLEAR:
                    delegate.clear();
                    break;
                default:
                    LOG.warn("Unknown cache event operation: {}", cacheEvent.getOperation());
                }
            } catch (OXException e) {
                LOG.error("Error handling cache event: {}", cacheEvent, e);
            }
        }
    }

    private void fireInvalidateGroup(String groupName) {
        if (notifyOnLocalOperations || false == isLocal()) {
            CacheEventService eventService = this.eventService;
            if (null != eventService) {
                CacheEvent event = CacheEvent.INVALIDATE_GROUP(region, groupName);
                LOG.debug("fireInvalidateGroup: {}", event);
                notify(event, eventService);
            }
        }
    }

    private void fireInvalidate(Serializable key) {
        fireInvalidate(key, null);
    }

    private void fireInvalidate(List<Serializable> keys) {
        fireInvalidate(keys, null);
    }

    private void fireInvalidate(Serializable key, String groupName) {
        if (notifyOnLocalOperations || false == isLocal()) {
            CacheEventService eventService = this.eventService;
            if (null != eventService) {
                CacheEvent event = CacheEvent.INVALIDATE(region, groupName, key);
                LOG.debug("fireInvalidate: {}", event);
                notify(event, eventService);
            }
        }
    }

    private void fireInvalidate(List<Serializable> keys, String groupName) {
        if (notifyOnLocalOperations || false == isLocal()) {
            CacheEventService eventService = this.eventService;
            if (null != eventService) {
                CacheEvent event = CacheEvent.INVALIDATE(region, groupName, keys);
                LOG.debug("fireInvalidate: {}", event);
                notify(event, eventService);
            }
        }
    }

    private void fireClear() {
        if (notifyOnLocalOperations || false == isLocal()) {
            CacheEventService eventService = this.eventService;
            if (null != eventService) {
                CacheEvent event = CacheEvent.CLEAR(region);
                LOG.debug("fireClear: {}", event);
                notify(event, eventService);
            }
        }
    }

    private void notify(CacheEvent event, CacheEventService eventService) {
        if (event instanceof ConditionalCacheEvent) {
            eventService.notify(this, event, false);
        } else {
            Condition condition = ThreadLocalConditionHolder.getInstance().getCondition();
            eventService.notify(this, null == condition ? event : new ConditionalCacheEvent(event, condition), false);
        }
    }

    @Override
    public String toString() {
        return "NotifyingCache [region=" + region + ", isLocal=" + isLocal() + "]";
    }
}
