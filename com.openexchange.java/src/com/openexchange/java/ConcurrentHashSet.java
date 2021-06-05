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

package com.openexchange.java;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link ConcurrentHashSet} - This class implements the <tt>Set</tt> interface, backed by a map (actually a <tt>ConcurrentHashMap</tt>
 * instance). It makes no guarantees as to the iteration order of the set; in particular, it does not guarantee that the order will remain
 * constant over time. This class does not permit the <tt>null</tt> element.
 * <p>
 * Moreover it supports full concurrency of retrievals and adjustable expected concurrency for updates.
 * <p>
 * Extends common <tt>Set</tt> methods by:
 * <ul>
 * <li>{@link #addIfAbsent(Object)}</li>
 * <li>{@link #removeIfPresent(Object)}</li>
 * </ul>
 * <p>
 * &nbsp;
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ConcurrentHashSet<E> extends AbstractSet<E> implements Cloneable, Serializable {

    private static final long serialVersionUID = -2608324279213322648L;

    /**
     * Dummy value to associate with an Object in the backing map.
     */
    private static final Object PRESENT = new Object();

    /**
     * The default initial number of table slots for this table. Used when not otherwise specified in constructor.
     */
    private static int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The default load factor for this table. Used when not otherwise specified in constructor.
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The default number of concurrency control segments.
     **/
    private static final int DEFAULT_SEGMENTS = 16;

    /*-
     * ############################# Members #############################
     */

    /**
     * The backing concurrent map.
     */
    private ConcurrentMap<E, Object> map;

    private transient Set<E> keys;

    /**
     * Creates a new, empty set with the specified initial capacity, load factor, and concurrency level.
     *
     * @param initialCapacity The initial capacity. The implementation performs internal sizing to accommodate this many elements.
     * @param loadFactor The load factor threshold, used to control resizing. Resizing may be performed when the average number of elements
     *            per bin exceeds this threshold.
     * @param concurrencyLevel The estimated number of concurrently updating threads. The implementation performs internal sizing to try to
     *            accommodate this many threads.
     * @throws IllegalArgumentException If the initial capacity is negative or the load factor or concurrencyLevel are non-positive.
     */
    public ConcurrentHashSet(final int initialCapacity, final float loadFactor, final int concurrencyLevel) {
        super();
        map = new ConcurrentHashMap<E, Object>(initialCapacity, loadFactor, concurrencyLevel);
        keys = map.keySet();
    }

    /**
     * Creates a new, empty set with the specified initial capacity, and with default load factor and concurrency level.
     *
     * @param initialCapacity the initial capacity. The implementation performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of elements is negative.
     */
    public ConcurrentHashSet(final int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
    }

    /**
     * Creates a new, empty set with a default initial capacity, load factor, and concurrency level.
     */
    public ConcurrentHashSet() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
    }

    /**
     * Creates a new set with the same elements as the given set. The set is created with a capacity of twice the number of elements in the
     * given map or <code>11</code> (whichever is greater), and a default load factor and concurrency level.
     *
     * @param t the map
     */
    public ConcurrentHashSet(final Set<? extends E> t) {
        this(Math.max((int) (t.size() / DEFAULT_LOAD_FACTOR) + 1, 11), DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
        addAll(t);
    }

    @Override
    public Iterator<E> iterator() {
        return keys.iterator();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return keys.containsAll(c);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || keys.equals(o);
    }

    @Override
    public int hashCode() {
        return keys.hashCode();
    }

    @Override
    public boolean add(final E o) {
        return map.put(o, PRESENT) == null;
    }

    /**
     * If the specified element is not already contained, add it to set. This is equivalent to
     *
     * <pre>
     * if (!set.contains(e))
     * return set.add(e);
     * else
     * return false
     * </pre>
     *
     * except that the action is performed atomically.
     *
     * @param e element to be added
     * @return <code>true</code> if element has been added; otherwise <code>false</code>
     */
    public boolean addIfAbsent(final E e) {
        return map.putIfAbsent(e, PRESENT) == null;
    }

    @Override
    public boolean remove(final Object o) {
        return map.remove(o) == PRESENT;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return keys.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return keys.retainAll(c);
    }

    /**
     * Removes the entry for a key only if currently mapped to a given value. This is equivalent to
     *
     * <pre>
     * if (set.contains(o)) {
     * set.remove(o);
     * return true;
     * } else {
     * return false;
     * }
     * </pre>
     *
     * except that the action is performed atomically.
     *
     * @param o element to remove
     * @return <tt>true</tt> if the value was removed
     */
    public boolean removeIfPresent(final Object o) {
        return map.remove(o, PRESENT);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Object[] toArray() {
        return keys.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return keys.toArray(a);
    }

    @Override
    public String toString() {
        return keys.toString();
    }

    @Override
    public Object clone() {
        try {
            @SuppressWarnings("unchecked") final ConcurrentHashSet<E> newSet = (ConcurrentHashSet<E>) super.clone();
            newSet.map.putAll(map);
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new InternalError("Clone fialed although Cloneable is implemented.");
        }
    }

    private void writeObject(final java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();

        // Write out size
        s.writeInt(map.size());

        // Write out all elements in the proper order.
        for (final Iterator<E> i = map.keySet().iterator(); i.hasNext();) {
            s.writeObject(i.next());
        }
    }

    private void readObject(final java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        map = new ConcurrentHashMap<E, Object>();

        // Read in size
        final int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++) {
            @SuppressWarnings("unchecked") final E e = (E) s.readObject();
            map.put(e, PRESENT);
        }
    }

}
