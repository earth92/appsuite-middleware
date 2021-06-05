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

package gnu.trove;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import gnu.trove.function.TObjectFunction;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongObjectProcedure;
import gnu.trove.procedure.TLongProcedure;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.TLongSet;

/**
 * {@link ConcurrentTLongObjectHashMap}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ConcurrentTLongObjectHashMap<V> extends TLongObjectHashMap<V> {

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final TLongObjectHashMap<V> delegatee;

    /**
     * Initializes a new {@link ConcurrentTLongObjectHashMap}.
     */
    public ConcurrentTLongObjectHashMap() {
        super(1);
        delegatee = new TLongObjectHashMap<V>();
    }

    /**
     * Initializes a new {@link ConcurrentTLongObjectHashMap}.
     *
     * @param initialCapacity
     */
    public ConcurrentTLongObjectHashMap(final int initialCapacity) {
        super(1);
        delegatee = new TLongObjectHashMap<V>(initialCapacity);
    }

    /**
     * Initializes a new {@link ConcurrentTLongObjectHashMap}.
     *
     * @param map
     */
    public ConcurrentTLongObjectHashMap(final TLongObjectMap<? extends V> map) {
        super(1);
        delegatee = new TLongObjectHashMap<V>(map);
    }

    /**
     * Initializes a new {@link ConcurrentTLongObjectHashMap}.
     *
     * @param initialCapacity
     * @param loadFactor
     */
    public ConcurrentTLongObjectHashMap(final int initialCapacity, final float loadFactor) {
        super(1);
        delegatee = new TLongObjectHashMap<V>(initialCapacity, loadFactor);
    }

    /**
     * Initializes a new {@link ConcurrentTLongObjectHashMap}.
     *
     * @param initialCapacity
     * @param loadFactor
     * @param noEntryKey
     */
    public ConcurrentTLongObjectHashMap(final int initialCapacity, final float loadFactor, final long noEntryKey) {
        super(1);
        delegatee = new TLongObjectHashMap<V>(initialCapacity, loadFactor, noEntryKey);
    }

    @Override
    public int capacity() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.capacity();
        } finally {
            l.unlock();
        }
    }

    @Override
    public long getNoEntryValue() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.getNoEntryValue();
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.isEmpty();
        } finally {
            l.unlock();
        }
    }

    @Override
    public int size() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.size();
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean contains(final long val) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.contains(val);
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean forEach(final TLongProcedure procedure) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.forEach(procedure);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void ensureCapacity(final int desiredCapacity) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.ensureCapacity(desiredCapacity);
        } finally {
            l.unlock();
        }
    }

    @Override
    public long getNoEntryKey() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.getNoEntryKey();
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean containsKey(final long key) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.containsKey(key);
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean containsValue(final Object val) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.containsValue(val);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void compact() {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.compact();
        } finally {
            l.unlock();
        }
    }

    @Override
    public V get(final long key) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.get(key);
        } finally {
            l.unlock();
        }
    }

    @Override
    public V put(final long key, final V value) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            return delegatee.put(key, value);
        } finally {
            l.unlock();
        }
    }

    @Override
    public V putIfAbsent(final long key, final V value) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            return delegatee.putIfAbsent(key, value);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void setAutoCompactionFactor(final float factor) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            delegatee.setAutoCompactionFactor(factor);
        } finally {
            l.unlock();
        }
    }

    @Override
    public V remove(final long key) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            return delegatee.remove(key);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void putAll(final Map<? extends Long, ? extends V> map) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.putAll(map);
        } finally {
            l.unlock();
        }
    }

    @Override
    public float getAutoCompactionFactor() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.getAutoCompactionFactor();
        } finally {
            l.unlock();
        }
    }

    @Override
    public void putAll(final TLongObjectMap<? extends V> map) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.putAll(map);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void clear() {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.clear();
        } finally {
            l.unlock();
        }
    }

    @Override
    public TLongSet keySet() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.keySet();
        } finally {
            l.unlock();
        }
    }

    @Override
    public long[] keys() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.keys();
        } finally {
            l.unlock();
        }
    }

    @Override
    public long[] keys(final long[] dest) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.keys(dest);
        } finally {
            l.unlock();
        }
    }

    @Override
    public Collection<V> valueCollection() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.valueCollection();
        } finally {
            l.unlock();
        }
    }

    @Override
    public Object[] values() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.values();
        } finally {
            l.unlock();
        }
    }

    @Override
    public V[] values(final V[] dest) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.values(dest);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void tempDisableAutoCompaction() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            delegatee.tempDisableAutoCompaction();
        } finally {
            l.unlock();
        }
    }

    @Override
    public void reenableAutoCompaction(final boolean check_for_compaction) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.reenableAutoCompaction(check_for_compaction);
        } finally {
            l.unlock();
        }
    }

    @Override
    public TLongObjectIterator<V> iterator() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.iterator();
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean forEachKey(final TLongProcedure procedure) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.forEachKey(procedure);
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean forEachValue(final TObjectProcedure<? super V> procedure) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.forEachValue(procedure);
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean forEachEntry(final TLongObjectProcedure<? super V> procedure) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.forEachEntry(procedure);
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean retainEntries(final TLongObjectProcedure<? super V> procedure) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            return delegatee.retainEntries(procedure);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void transformValues(final TObjectFunction<V, V> function) {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.transformValues(function);
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean equals(final Object other) {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.equals(other);
        } finally {
            l.unlock();
        }
    }

    @Override
    public int hashCode() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.hashCode();
        } finally {
            l.unlock();
        }
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            delegatee.writeExternal(out);
        } finally {
            l.unlock();
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        final Lock l = readWriteLock.writeLock();
        l.lock();
        try {
            delegatee.readExternal(in);
        } finally {
            l.unlock();
        }
    }

    @Override
    public String toString() {
        final Lock l = readWriteLock.readLock();
        l.lock();
        try {
            return delegatee.toString();
        } finally {
            l.unlock();
        }
    }

}
