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

package com.openexchange.caching.events.internal;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import com.openexchange.caching.events.CacheEvent;
import com.openexchange.caching.events.CacheListener;

/**
 * {@link CacheEventQueue}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class CacheEventQueue extends AbstractQueue<StampedCacheEvent> implements BlockingQueue<StampedCacheEvent> {

    private transient final ReentrantLock lock = new ReentrantLock();
    private final PriorityQueue<StampedCacheEvent> q = new PriorityQueue<StampedCacheEvent>();

    /**
     * Thread designated to wait for the element at the head of the queue. This variant of the Leader-Follower pattern
     * (http://www.cs.wustl.edu/~schmidt/POSA/POSA2/) serves to minimize unnecessary timed waiting. When a thread becomes the leader, it
     * waits only for the next delay to elapse, but other threads await indefinitely. The leader thread must signal some other thread before
     * returning from take() or poll(...), unless some other thread becomes leader in the interim. Whenever the head of the queue is
     * replaced with an element with an earlier expiration time, the leader field is invalidated by being reset to null, and some waiting
     * thread, but not necessarily the current leader, is signalled. So waiting threads must be prepared to acquire and lose leadership
     * while waiting.
     */
    private Thread leader = null;

    /**
     * Condition signalled when a newer element becomes available at the head of the queue or a new thread may need to become leader.
     */
    private final Condition available = lock.newCondition();

    /**
     * Creates a new <tt>DelayQueue</tt> that is initially empty.
     */
    public CacheEventQueue() {
        super();
    }

    /**
     * Creates a <tt>DelayQueue</tt> initially containing the elements of the given collection of {@link Delayed} instances.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any of its elements are null
     */
    public CacheEventQueue(Collection<? extends StampedCacheEvent> c) {
        super();
        this.addAll(c);
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param StampedCacheEvent the element to add
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    @Override
    public boolean add(StampedCacheEvent e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param event The event to add
     * @return <tt>true</tt> if event is newly added to queue; otherwise <code>false</code> if aggregated to an existing event
     * @throws NullPointerException if the specified event is null
     */
    public boolean offerIfAbsentElseReset(List<CacheListener> listeners, Object sender, CacheEvent event, boolean fromRemote) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // Check if event can be aggregated to an existing event
            {
                StampedCacheEvent prev = null;
                for (Iterator<StampedCacheEvent> it = q.iterator(); null == prev && it.hasNext();) {
                    StampedCacheEvent next = it.next();
                    if (StampedCacheEvent.POISON != next && next.event.aggregate(event)) {
                        // Successfully aggregated to an existing event
                        prev = next;
                        it.remove();
                    }
                }
                if (null != prev) {
                    prev.reset();
                    q.offer(prev);
                    return false;
                }
            }

            StampedCacheEvent e = new StampedCacheEvent(listeners, sender, event, fromRemote);
            q.offer(e);
            if (q.peek() == e) {
                leader = null;
                available.signal();
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param e the element to add
     * @return <tt>true</tt>
     * @throws NullPointerException if the specified element is null
     */
    @Override
    public boolean offer(StampedCacheEvent e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            q.offer(e);
            if (q.peek() == e) {
                leader = null;
                available.signal();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element into this delay queue. As the queue is unbounded this method will never block.
     *
     * @param e the element to add
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public void put(StampedCacheEvent e) {
        offer(e);
    }

    /**
     * Inserts the specified element into this delay queue. As the queue is unbounded this method will never block.
     *
     * @param e the element to add
     * @param timeout This parameter is ignored as the method never blocks
     * @param unit This parameter is ignored as the method never blocks
     * @return <tt>true</tt>
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public boolean offer(StampedCacheEvent e, long timeout, TimeUnit unit) {
        return offer(e);
    }

    /**
     * Retrieves and removes the head of this queue, or returns <tt>null</tt> if this queue has no elements with an expired delay.
     *
     * @return the head of this queue, or <tt>null</tt> if this queue has no elements with an expired delay
     */
    @Override
    public StampedCacheEvent poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                StampedCacheEvent first = q.peek();
                if (first == null || first.getDelay(TimeUnit.NANOSECONDS) > 0) {
                    return null;
                }
                first = q.poll();

                com.openexchange.caching.events.Condition eventCondition = first.optCondition();
                if (null != eventCondition) {
                    int peeked = eventCondition.peekShouldDeliver();
                    if (peeked < 0) {
                        // Condition not yet available, reschedule and repeat
                        first.forceReset();
                        q.offer(first);
                        continue;
                    }

                    if (peeked == 0) {
                        // Discard... Event is not supposed to be propagated
                        continue;
                    }
                }

                return first;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary until an element with an expired delay is available on this queue.
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public StampedCacheEvent take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (;;) {
                StampedCacheEvent first = q.peek();
                if (first == null) {
                    available.await();
                } else {
                    long delay = first.getDelay(TimeUnit.NANOSECONDS);
                    if (delay <= 0) {
                        // Found expired element
                        first = q.poll();

                        com.openexchange.caching.events.Condition eventCondition = first.optCondition();
                        if (null != eventCondition) {
                            int peeked = eventCondition.peekShouldDeliver();
                            if (peeked < 0) {
                                // Condition not yet available, reschedule and repeat
                                first.forceReset();
                                q.offer(first);
                                continue;
                            }

                            if (peeked == 0) {
                                // Discard... Event is not supposed to be propagated
                                continue;
                            }
                        }

                        return first;
                    } else if (leader != null) {
                        available.await();
                    } else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            available.awaitNanos(delay);
                        } finally {
                            if (leader == thisThread) {
                                leader = null;
                            }
                        }
                    }
                }
            }
        } finally {
            if (leader == null && q.peek() != null) {
                available.signal();
            }
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary until an element with an expired delay is available on this queue,
     * or the specified wait time expires.
     *
     * @return the head of this queue, or <tt>null</tt> if the specified waiting time elapses before an element with an expired delay
     *         becomes available
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public StampedCacheEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (;;) {
                StampedCacheEvent first = q.peek();
                if (first == null) {
                    if (nanos <= 0) {
                        return null;
                    }
                    nanos = available.awaitNanos(nanos);
                } else {
                    long delay = first.getDelay(TimeUnit.NANOSECONDS);
                    if (delay <= 0) {
                        // Found expired element
                        first = q.poll();

                        com.openexchange.caching.events.Condition eventCondition = first.optCondition();
                        if (null != eventCondition) {
                            int peeked = eventCondition.peekShouldDeliver();
                            if (peeked < 0) {
                                // Condition not yet available, reschedule and repeat
                                first.forceReset();
                                q.offer(first);
                                continue;
                            }

                            if (peeked == 0) {
                                // Discard... Event is not supposed to be propagated
                                continue;
                            }
                        }

                        return first;
                    }
                    if (nanos <= 0) {
                        return null;
                    }
                    if (nanos < delay || leader != null) {
                        nanos = available.awaitNanos(nanos);
                    } else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            long timeLeft = available.awaitNanos(delay);
                            nanos -= delay - timeLeft;
                        } finally {
                            if (leader == thisThread) {
                                leader = null;
                            }
                        }
                    }
                }
            }
        } finally {
            if (leader == null && q.peek() != null) {
                available.signal();
            }
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
    public StampedCacheEvent peek() {
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
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    @Override
    public int drainTo(Collection<? super StampedCacheEvent> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = 0;
            for (;;) {
                StampedCacheEvent first = q.peek();
                if (first == null || first.getDelay(TimeUnit.NANOSECONDS) > 0) {
                    break;
                }
                first = q.poll();

                com.openexchange.caching.events.Condition eventCondition = first.optCondition();
                if (null != eventCondition) {
                    int peeked = eventCondition.peekShouldDeliver();
                    if (peeked < 0) {
                        // Condition not yet available, reschedule and repeat
                        first.forceReset();
                        q.offer(first);
                        continue;
                    }

                    if (peeked == 0) {
                        // Discard... Event is not supposed to be propagated
                        continue;
                    }
                }

                c.add(first);
                ++n;
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    @Override
    public int drainTo(Collection<? super StampedCacheEvent> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        if (maxElements <= 0) {
            return 0;
        }
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = 0;
            while (n < maxElements) {
                StampedCacheEvent first = q.peek();
                if (first == null || first.getDelay(TimeUnit.NANOSECONDS) > 0) {
                    break;
                }
                first = q.poll();

                com.openexchange.caching.events.Condition eventCondition = first.optCondition();
                if (null != eventCondition) {
                    int peeked = eventCondition.peekShouldDeliver();
                    if (peeked < 0) {
                        // Condition not yet available, reschedule and repeat
                        first.forceReset();
                        q.offer(first);
                        continue;
                    }

                    if (peeked == 0) {
                        // Discard... Event is not supposed to be propagated
                        continue;
                    }
                }

                c.add(first);
                ++n;
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this delay queue. The queue will be empty after this call returns. Elements with an
     * unexpired delay are not waited for; they are simply discarded from the queue.
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
    @Override
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
     * <p>
     * The following code can be used to dump a delay queue into a newly allocated array of <tt>Delayed</tt>:
     *
     * <pre>
     *
     * Delayed[] a = q.toArray(new Delayed[0]);
     * </pre>
     *
     * Note that <tt>toArray(new Object[0])</tt> is identical in function to <tt>toArray()</tt>.
     *
     * @param a the array into which the elements of the queue are to be stored, if it is big enough; otherwise, a new array of the same
     *            runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array is not a supertype of the runtime type of every element in
     *             this queue
     * @throws NullPointerException if the specified array is null
     */
    @Override
    public <T> T[] toArray(T[] a) {
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
    public boolean remove(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.remove(o);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over all the elements (both expired and unexpired) in this queue. The iterator does not return the elements in
     * any particular order.
     * <p>
     * The returned iterator is a "weakly consistent" iterator that will never throw {@link java.util.ConcurrentModificationException
     * ConcurrentModificationException}, and guarantees to traverse elements as they existed upon construction of the iterator, and may (but
     * is not guaranteed to) reflect any modifications subsequent to construction.
     *
     * @return an iterator over the elements in this queue
     */
    @Override
    public Iterator<StampedCacheEvent> iterator() {
        return new Itr(toArray(), q, lock);
    }

    /**
     * Snapshot iterator that works off copy of underlying q array.
     */
    private static class Itr implements Iterator<StampedCacheEvent> {

        final ReentrantLock lock;
        final PriorityQueue<StampedCacheEvent> q;
        final Object[] array; // Array of all elements
        int cursor; // index of next element to return;
        int lastRet; // index of last element, or -1 if no such

        Itr(Object[] array, PriorityQueue<StampedCacheEvent> q, ReentrantLock lock) {
            super();
            this.q = q;
            this.lock = lock;
            lastRet = -1;
            this.array = array;
        }

        @Override
        public boolean hasNext() {
            return cursor < array.length;
        }

        @Override
        public StampedCacheEvent next() {
            if (cursor >= array.length) {
                throw new NoSuchElementException();
            }
            lastRet = cursor;
            return (StampedCacheEvent) array[cursor++];
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            Object x = array[lastRet];
            lastRet = -1;
            // Traverse underlying queue to find == element,
            // not just a .equals element.
            lock.lock();
            try {
                for (Iterator<StampedCacheEvent> it = q.iterator(); it.hasNext();) {
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
