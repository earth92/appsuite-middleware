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

package com.openexchange.unifiedinbox.utility;

import static com.openexchange.unifiedinbox.utility.UnifiedInboxSynchronousQueueProvider.getInstance;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@link UnifiedInboxExecutors} - Factory and utility methods for {@link ExecutorService} for Unified Mail bundle.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class UnifiedInboxExecutors {

    /**
     * Initializes a new {@link UnifiedInboxExecutors}.
     */
    private UnifiedInboxExecutors() {
        super();
    }

    /*-
     * ##################################### FACTORY METHODS FOR LIMITED FIXED THREAD POOLS #####################################
     */

    /**
     * Creates a thread pool that reuses a fixed set of threads operating off a shared unbounded queue.
     *
     * @param numberOfThreads The number of threads in the pool
     * @return The newly created thread pool
     */
    public static ExecutorService newFixedThreadPool(final int numberOfThreads) {
        final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            numberOfThreads,
            numberOfThreads,
            0L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new UnifiedInboxThreadFactory());
        threadPool.prestartCoreThread();
        return threadPool;
    }

    /*-
     * ##################################### FACTORY METHODS FOR LIMITED CACHED THREAD POOLS #####################################
     */

    /**
     * Creates a thread pool that creates new threads as needed, but will reuse previously constructed threads when they are available.
     *
     * @param numberOfTasks The number of tasks that shall be executed by returned thread pool
     * @return The newly created thread pool
     */
    public static ExecutorService newCachedThreadPool(final int numberOfTasks) {
        final BlockingQueue<Runnable> queue = getInstance().newSynchronousQueue();
        final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            divide(numberOfTasks),
            numberOfTasks,
            1L,
            TimeUnit.SECONDS,
            queue,
            new UnifiedInboxThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy());
        threadPool.prestartAllCoreThreads();
        return threadPool;
    }

    /**
     * Creates a thread pool that creates new threads as needed, but will reuse previously constructed threads when they are available.
     *
     * @param numberOfTasks The number of tasks that shall be executed by returned thread pool
     * @param namePrefix The name prefix applied to newly created threads by returned thread pool
     * @return The newly created thread pool
     */
    public static ExecutorService newCachedThreadPool(final int numberOfTasks, final String namePrefix) {
        final BlockingQueue<Runnable> queue = getInstance().newSynchronousQueue();
        final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            divide(numberOfTasks),
            numberOfTasks,
            1L,
            TimeUnit.SECONDS,
            queue,
            new UnifiedInboxThreadFactory(namePrefix),
            new ThreadPoolExecutor.CallerRunsPolicy());
        threadPool.prestartAllCoreThreads();
        return threadPool;
    }

    private static int divide(final int number) {
        return (number / 2);
    }

    /*-
     * ##################################### FACTORY METHODS FOR UNLIMITED CACHED THREAD POOLS #####################################
     */

    private static volatile Integer CORE_SIZE;

    private static int getCoreSize() {
        Integer tmp = CORE_SIZE;
        if (null == tmp) {
            synchronized (UnifiedInboxExecutors.class) {
                tmp = CORE_SIZE;
                if (null == tmp) {
                    tmp = CORE_SIZE = Integer.valueOf(Runtime.getRuntime().availableProcessors() + 1);
                }
            }
        }
        return tmp.intValue();
    }

    /**
     * Creates a thread pool with unlimited max. pool size that creates new threads as needed, but will reuse previously constructed threads
     * when they are available.
     *
     * @return The newly created thread pool
     */
    public static ExecutorService newUnlimitedCachedThreadPool() {
        final BlockingQueue<Runnable> queue = getInstance().newSynchronousQueue();
        final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            getCoreSize(),
            Integer.MAX_VALUE,
            1L,
            TimeUnit.SECONDS,
            queue,
            new UnifiedInboxThreadFactory());
        threadPool.prestartAllCoreThreads();
        return threadPool;
    }

    /**
     * Creates a thread pool with unlimited max. pool size that creates new threads as needed, but will reuse previously constructed threads
     * when they are available.
     *
     * @param namePrefix The name prefix applied to newly created threads by returned thread pool
     * @return The newly created thread pool
     */
    public static ExecutorService newUnlimitedCachedThreadPool(final String namePrefix) {
        final BlockingQueue<Runnable> queue = getInstance().newSynchronousQueue();
        final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            getCoreSize(),
            Integer.MAX_VALUE,
            1L,
            TimeUnit.SECONDS,
            queue,
            new UnifiedInboxThreadFactory(namePrefix));
        threadPool.prestartAllCoreThreads();
        return threadPool;
    }

}
