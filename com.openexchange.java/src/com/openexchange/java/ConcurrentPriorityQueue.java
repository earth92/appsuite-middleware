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

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link ConcurrentPriorityQueue}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class ConcurrentPriorityQueue<E extends Comparable<E>> extends AbstractQueue<E> {

    transient final ReentrantLock lock = new ReentrantLock();
    final PriorityQueue<E> q = new PriorityQueue<E>();

    /**
     * Creates a new <tt>ConcurrentPriorityQueue</tt> that is initially empty.
     */
    public ConcurrentPriorityQueue() {
        super();
    }

    /**
     * Creates a <tt>ConcurrentPriorityQueue</tt> initially containing the elements of the given collection of {@link Delayed} instances.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any of its elements are null
     */
    public ConcurrentPriorityQueue(final Collection<? extends E> c) {
        super();
        this.addAll(c);
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param e the element to add
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    @Override
    public boolean add(final E e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param e the element to add
     * @return <tt>true</tt>
     * @throws NullPointerException if the specified element is <code>null</code>
     */
    @Override
    public boolean offer(final E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.offer(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, or returns <tt>null</tt> if this queue has no elements with an expired delay.
     *
     * @return the head of this queue, or <tt>null</tt> if this queue has no elements with an expired delay
     */
    @Override
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.poll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves, but does not remove, the head of this queue, or returns <tt>null</tt> if this queue is empty. Unlike <tt>poll</tt>, if no
     * expired elements are available in the queue, this method returns the element that will expire next, if one exists.
     *
     * @return the head of this queue, or <tt>null</tt> if this queue is empty.
     */
    @Override
    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.peek();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this delay queue. The queue will be empty after this call returns.
     */
    @Override
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            q.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Always returns <tt>Integer.MAX_VALUE</tt> because a <tt>DelayQueue</tt> is not capacity constrained.
     *
     * @return <tt>Integer.MAX_VALUE</tt>
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    /**
     * Returns an array containing all of the elements in this queue. The returned array elements are in no particular order.
     * <p>
     * The returned array will be "safe" in that no references to it are maintained by this queue. (In other words, this method must
     * allocate a new array). The caller is thus free to modify the returned array.
     * <p>
     * This method acts as bridge between array-based and collection-based APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    @Override
    public Object[] toArray() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue; the runtime type of the returned array is that of the specified array.
     * The returned array elements are in no particular order. If the queue fits in the specified array, it is returned therein. Otherwise,
     * a new array is allocated with the runtime type of the specified array and the size of this queue.
     * <p>
     * If this queue fits in the specified array with room to spare (i.e., the array has more elements than this queue), the element in the
     * array immediately following the end of the queue is set to <tt>null</tt>.
     * <p>
     * Like the {@link #toArray()} method, this method acts as bridge between array-based and collection-based APIs. Further, this method
     * allows precise control over the runtime type of the output array, and may, under certain circumstances, be used to save allocation
     * costs.
     *
     * @param a the array into which the elements of the queue are to be stored, if it is big enough; otherwise, a new array of the same
     *            runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array is not a supertype of the runtime type of every element in
     *             this queue
     * @throws NullPointerException if the specified array is null
     */
    @Override
    public <T> T[] toArray(final T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a single instance of the specified element from this queue, if it is present, whether or not it has expired.
     */
    @Override
    public boolean remove(final Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.remove(o);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over all the elements in this queue. The iterator does not return the elements in any particular order. The
     * returned <tt>Iterator</tt> is a "weakly consistent" iterator that will never throw {@link ConcurrentModificationException}, and
     * guarantees to traverse elements as they existed upon construction of the iterator, and may (but is not guaranteed to) reflect any
     * modifications subsequent to construction.
     *
     * @return an iterator over the elements in this queue
     */
    @Override
    public Iterator<E> iterator() {
        return new Itr(toArray());
    }

    /**
     * Snapshot iterator that works off copy of underlying q array.
     */
    private class Itr implements Iterator<E> {

        final Object[] array; // Array of all elements
        int cursor; // index of next element to return;
        int lastRet; // index of last element, or -1 if no such

        Itr(final Object[] array) {
            lastRet = -1;
            this.array = array;
        }

        @Override
        public boolean hasNext() {
            return cursor < array.length;
        }

        @Override
        public E next() {
            if (cursor >= array.length) {
                throw new NoSuchElementException();
            }
            lastRet = cursor;
            return (E) array[cursor++];
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            final Object x = array[lastRet];
            lastRet = -1;
            // Traverse underlying queue to find == element,
            // not just a .equals element.
            lock.lock();
            try {
                for (final Iterator<E> it = q.iterator(); it.hasNext();) {
                    if (it.next() == x) {
                        it.remove();
                        return;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

}
