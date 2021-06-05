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

package com.openexchange.pooling;

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.L;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the object pool.
 * @param <T> type of pooled objects.
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public class ReentrantLockPool<T> implements Pool<T>, Runnable {

    static final Logger LOG = LoggerFactory.getLogger(ReentrantLockPool.class);

    private int maxIdle;
    private long maxIdleTime;
    private volatile int maxActive;
    private long maxWait;
    private long maxLifeTime;
    private ExhaustedActions exhaustedAction;
    private boolean testOnActivate;
    private boolean alwaysCheckOnActivate;
    private boolean testOnDeactivate;
    private boolean testOnIdle;
    private boolean testThreads;
    private final PoolableLifecycle<T> lifecycle;
    private final long[] useTimes = new long[1000];

    protected final PoolImplData<T> data = new PoolImplData<T>();

    protected final ReentrantLock lock = new ReentrantLock(true);
    protected final Condition idleAvailable = lock.newCondition();

    private boolean running = true;
    private int useTimePointer;
    private final AtomicBoolean brokenCreate = new AtomicBoolean();

    /**
     * The longest time an object has been used.
     */
    private long maxUseTime;

    /**
     * The shortest time an object has been used.
     */
    private long minUseTime = Long.MAX_VALUE;

    /**
     * Number of broken objects. An object is broken if one of {@link PoolableLifecycle#activate(PooledData)},
     * {@link PoolableLifecycle#deactivate(PooledData)} or {@link PoolableLifecycle#validate(PooledData)} returns <code>false</code>.
     */
    private int numBroken;

    /**
     * Keeps the time stamp when the last warning was logged. Warnings should only be logged once a minute.
     */
    private long lastWarning;

    /**
     * Default constructor.
     * @param lifecycle Implementation of the interface for handling the life cycle of pooled objects.
     * @param config Configuration of the pool parameters.
     */
    public ReentrantLockPool(final PoolableLifecycle<T> lifecycle, final PoolConfig config) {
        super();
        setConfig(config);
        this.lifecycle = lifecycle;
    }

    protected PoolableLifecycle<T> getLifecycle() {
        return lifecycle;
    }

    protected void setConfig(PoolConfig config) {
        lock.lock();
        try {
            maxIdle = config.maxIdle;
            maxIdleTime = config.maxIdleTime;
            maxActive = config.maxActive;
            maxWait = config.maxWait;
            maxLifeTime = config.maxLifeTime;
            exhaustedAction = config.exhaustedAction;
            testOnActivate = config.testOnActivate;
            testOnDeactivate = config.testOnDeactivate;
            testOnIdle = config.testOnIdle;
            testThreads = config.testThreads;
            alwaysCheckOnActivate = config.alwaysCheckOnActivate;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void back(final T pooled) throws PoolingException {
        if (null == pooled) {
            throw new PoolingException("A null reference was returned to pool.");
        }
        final long startTime = System.currentTimeMillis();
        // checks
        final PooledData<T> metaData;
        lock.lock();
        try {
            metaData = data.getActive(pooled);
        } finally {
            lock.unlock();
        }
        // object of this pool?
        if (null == metaData) {
            throw new PoolingException("Object \"" + pooled + "\" does not belong to this pool.");
        }
        // reuseable?
        boolean poolable;
        if (running) {
            if (testOnDeactivate) {
                poolable = lifecycle.validate(metaData, false);
            } else {
                poolable = lifecycle.deactivate(metaData);
            }
            if (!poolable) {
                numBroken++;
            }
            poolable &= !metaData.isDeprecated();
            poolable &= (maxLifeTime <= 0 || metaData.getLiveTime() < maxLifeTime);
        } else {
            poolable = false;
        }
        // return to pool
        boolean destroy = !poolable;
        lock.lock();
        try {
            if (testThreads) {
                data.removeByThread(metaData);
            }
            // statistics
            final long useTime = metaData.getTimeDiff();
            useTimes[useTimePointer++] = useTime;
            useTimePointer = useTimePointer % useTimes.length;
            maxUseTime = Math.max(maxUseTime, useTime);
            minUseTime = Math.min(minUseTime, useTime);
            // update meta data
            metaData.resetTrace();
            metaData.touch();
            data.removeActive(metaData);
            idleAvailable.signal();
            if (maxIdle > 0 && data.numIdle() >= maxIdle) {
                destroy = true;
            } else if (poolable) {
                data.addIdle(metaData);
            }
        } finally {
            lock.unlock();
        }
        if (destroy) {
            LOG.trace("Destroying object.");
            lifecycle.destroy(metaData.getPooled());
        }
        LOG.trace("Back time: {}", L(getWaitTime(startTime)));
    }

    @Override
    public T get() throws PoolingException {
        final long startTime = System.currentTimeMillis();
        while (running) {
            PooledData<T> retval;
            boolean created = false;
            lock.lock();
            try {
                if (testThreads) {
                    final PooledData<T> other;
                    final Thread thread = Thread.currentThread();
                    other = data.getByThread(thread);
                    if (other != null && thread.equals(other.getThread())) {
                        if (LOG.isDebugEnabled()) {
                            PoolingException e = new PoolingException("Found thread using two objects. First get.");
                            StackTraceElement[] trace = other.getTrace();
                            if (null != trace) {
                                e.setStackTrace(trace);
                            }
                            LOG.debug(e.getMessage(), e);
                            e = new PoolingException("Found thread using two objects. Second get.");
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                }
                retval = data.popIdle();
                if (null == retval) {
                    int maxActive = this.maxActive;
                    if (maxActive > 0 && data.numActive() >= maxActive) {
                        // now we are getting in trouble. no more idle objects, a maximum number of active is defined and we reached this
                        // border.
                        switch (exhaustedAction) {
                        case GROW:
                            break;
                        case FAIL:
                            throw new PoolingException("Pool exhausted.");
                        case BLOCK:
                            final String threadName = Thread.currentThread().getName();
                            final boolean writeWarning = System.currentTimeMillis() > (lastWarning + 60000L);
                            if (writeWarning) {
                                logThreads(data.getActive());
                                lastWarning = System.currentTimeMillis();
                                final PoolingException warn = new PoolingException("Thread " + threadName
                                    + " is sent to sleep until an object in the pool is available. " + data.numActive()
                                    + " objects are already in use.");
                                LOG.warn(warn.getMessage(), warn);
                            }
                            final long sleepStartTime = System.currentTimeMillis();
                            boolean timedOut = false;
                            try {
                                if (maxWait > 0) {
                                    timedOut = !idleAvailable.await(maxWait - getWaitTime(startTime), TimeUnit.MILLISECONDS);
                                } else {
                                    idleAvailable.await();
                                }
                            } catch (InterruptedException e) {
                                // Restore the interrupted status; see http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html
                                Thread.currentThread().interrupt();
                                LOG.error("Thread {} was interrupted.", threadName, e);
                            }
                            if (writeWarning) {
                                final PoolingException warn = new PoolingException("Thread " + threadName + " slept for "
                                    + getWaitTime(sleepStartTime) + "ms.");
                                LOG.warn(warn.getMessage(), warn);
                            }
                            if (timedOut) {
                                idleAvailable.signal();
                                throw new PoolingException("Wait time exceeded. Active: " + data.numActive() + ", Idle: " + data.numIdle()
                                    + ", Waiting: " + lock.getWaitQueueLength(idleAvailable) + ", Time: " + getWaitTime(startTime));
                            }
                            continue;
                        default:
                            throw new IllegalStateException("Unknown exhausted action: " + exhaustedAction);
                        }
                    }
                }
                if (null == retval) {
                    if (brokenCreate.get() && data.getCreating() > 0) {
                        throw new PoolingException("Not trying to create a pooled object in broken create state.");
                    }
                    data.addCreating();
                }
            } finally {
                lock.unlock();
            }
            // create
            if (null == retval) {
                LOG.trace("Creating object.");
                final T pooled;
                try {
                    pooled = createPooledObject();
                    brokenCreate.set(false);
                } catch (Exception e) {
                    brokenCreate.set(true);
                    lock.lock();
                    try {
                        data.removeCreating();
                        idleAvailable.signal();
                    } finally {
                        lock.unlock();
                    }
                    throw new PoolingException("Cannot create pooled object.", e);
                }
                retval = new PooledData<T>(pooled);
                lock.lock();
                try {
                    data.addActive(retval);
                    data.removeCreating();
                } finally {
                    lock.unlock();
                }
                created = true;
            }
            // LifeCycle
            if (!lifecycle.activate(retval, alwaysCheckOnActivate) || (testOnActivate && !lifecycle.validate(retval, true))) {
                lock.lock();
                try {
                    data.removeActive(retval);
                    idleAvailable.signal();
                } finally {
                    lock.unlock();
                }
                numBroken++;
                lifecycle.destroy(retval.getPooled());
                if (created) {
                    throw new PoolingException("Problem while creating new object.");
                }
                continue;
            }
            final Thread thread = Thread.currentThread();
            retval.setThread(thread);
            retval.touch();
            if (testThreads) {
                retval.setTrace(thread.getStackTrace());
                lock.lock();
                try {
                    data.addByThread(retval);
                } finally {
                    lock.unlock();
                }
            }
            LOG.trace("Get time: {}, Created: {}", L(getWaitTime(startTime)), B(created));
            return retval.getPooled();
        }
        throw new PoolingException("Pool has been stopped.");
    }

    /**
     * Creates a new object of this pool
     *
     * @return The new object
     * @throws Exception
     */
    protected T createPooledObject() throws Exception {
        return lifecycle.create();
    }

    private static final long getWaitTime(final long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    private static <T> void logThreads(Collection<PooledData<T>> active) {
        Logger log = LoggerFactory.getLogger(ReentrantLockPool.class.getName() + ".logThreads");
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("All available objects in the pool are in use. Dumping now threads using the objects.");
        for (PooledData<T> pooled : active) {
            Thread thread = pooled.getThread();
            PoolingException e = new PoolingException("All available objects in the pool are in use. Thread" + thread.getName() + " is using one.");
            StackTraceElement[] trace = pooled.getTrace();
            if (null != trace) {
                e.setStackTrace(trace);
            } else {
                e.setStackTrace(thread.getStackTrace());
            }
            log.debug(e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        lock.lock();
        try {
            running = false;
        } finally {
            lock.unlock();
        }
    }

    public boolean isStopped() {
        lock.lock();
        try {
            return !running;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return data.isIdleEmpty() && data.isActiveEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getNumIdle() {
        lock.lock();
        try {
            return data.numIdle();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getNumActive() {
        lock.lock();
        try {
            return data.numActive();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getPoolSize() {
        lock.lock();
        try {
            return data.numActive() + data.numIdle();
        } finally {
            lock.unlock();
        }
    }

    public int getNumWaiting() {
        lock.lock();
        try {
            return lock.getWaitQueueLength(idleAvailable);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getMaxUseTime() {
        return maxUseTime;
    }

    @Override
    public long getMinUseTime() {
        return minUseTime;
    }

    @Override
    public int getNumBroken() {
        return numBroken;
    }

    @Override
    public void resetMaxUseTime() {
        maxUseTime = 0;
    }

    @Override
    public void resetMinUseTime() {
        minUseTime = Long.MAX_VALUE;
    }

    public double getAvgUseTime() {
        double retval = 0;
        for (final long useTime : useTimes) {
            retval += useTime;
        }
        return retval / useTimes.length;
    }

    private final Runnable cleaner = new Runnable() {
        @Override
        public void run() {
            try {
                final Thread thread = Thread.currentThread();
                final String origName = thread.getName();
                thread.setName("PoolCleaner");
                ReentrantLockPool.this.run();
                thread.setName(origName);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }
    };

    public Runnable getCleanerTask() {
        return cleaner;
    }

    @Override
    public void run() {
        final long startTime = System.currentTimeMillis();
        LOG.trace("Starting cleaner run.");
        List<PooledData<T>> toCheck = null;
        List<PooledData<T>> removed = null;
        List<PooledData<T>> notReturned = null;
        lock.lock();
        try {
            int idleSize = data.numIdle();
            boolean remove = true;
            for (int index = 0; remove && index < idleSize;) {
                final PooledData<T> metaData = data.getIdle(index);
                remove = (
                        // timeout
                        maxIdleTime > 0 && metaData.getTimeDiff() > maxIdleTime
                    ) || (
                        maxLifeTime > 0 && metaData.getLiveTime() > maxLifeTime
                    );
                if (remove) {
                    data.removeIdle(index);
                    idleSize = data.numIdle();
                    if (removed == null) {
                        removed = new ArrayList<PooledData<T>>();
                    }
                    removed.add(metaData);
                } else if (testOnIdle) {
                    // Validation check must be done outside lock.
                    data.removeIdle(index);
                    idleSize = data.numIdle();
                    if (toCheck == null) {
                        toCheck = new ArrayList<PooledData<T>>();
                    }
                    toCheck.add(metaData);
                } else {
                    index++;
                }
            }
            final Iterator<PooledData<T>> iter = data.listActive();
            while (iter.hasNext()) {
                final PooledData<T> metaData = iter.next();
                if (metaData.getTimeDiff() > maxIdleTime) {
                    if (notReturned == null) {
                        notReturned = new ArrayList<PooledData<T>>();
                    }
                    notReturned.add(metaData);
                    iter.remove();
                    idleAvailable.signal();
                }
            }
        } finally {
            lock.unlock();
        }
        if (toCheck != null ) {
            for (PooledData<T> metaData : toCheck) {
                if (lifecycle.activate(metaData, false) && lifecycle.validate(metaData, true)) {
                    lock.lock();
                    try {
                        data.addIdle(metaData);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    if (removed == null) {
                        removed = new ArrayList<PooledData<T>>();
                    }
                    removed.add(metaData);
                }
            }
        }
        if (removed != null) {
            for (final PooledData<T> metaData : removed) {
                lifecycle.destroy(metaData.getPooled());
            }
        }
        if (notReturned != null) {
            StringBuilder sb = new StringBuilder(64);
            for (final PooledData<T> metaData : notReturned) {
                if (sb.length() > 0) {
                    sb.setLength(0);
                }
                sb.append(lifecycle.getObjectName());
                sb.append(" object has not been returned to the pool. Check further messages to make sure the object was terminated.");
                sb.append(" UseTime: ").append(metaData.getTimeDiff());
                sb.append(" Object: ").append(metaData.getPooled());
                final PoolingException e = new PoolingException(sb.toString());
                if (testThreads && null != metaData.getTrace()) {
                    e.setStackTrace(metaData.getTrace());
                }
                LOG.error(e.getMessage(), e);
            }
        }
        LOG.trace("Clean run ending. Time: {}", L(getWaitTime(startTime)));
    }

    /**
     * Gets the maxActive
     *
     * @return The maxActive
     */
    public int getMaxActive() {
        return maxActive;
    }
}
