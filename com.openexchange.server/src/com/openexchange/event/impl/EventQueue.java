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

package com.openexchange.event.impl;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.tasks.Task;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;

/**
 * {@link EventQueue} - The event queue.
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class EventQueue {

    /** The logger constant */
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EventQueue.class);

    private static final class EventQueueTimerTask implements Runnable {

        private final AtomicBoolean useFirst;

        private final List<EventObject> q1;

        private final List<EventObject> q2;

        private final AtomicBoolean closing;

        private final AtomicBoolean shutdown;

        private final ReentrantLock shutdownLock;

        private final Condition allEventsProcessed;

        private volatile ScheduledTimerTask scheduledTimerTask;

        public EventQueueTimerTask(final Condition allEventsProcessed, final ReentrantLock shutdownLock, final AtomicBoolean isFirst, final List<EventObject> queue1, final List<EventObject> queue2, final AtomicBoolean shutdownComplete, final AtomicBoolean shuttingDown) {
            super();
            this.allEventsProcessed = allEventsProcessed;
            this.shutdownLock = shutdownLock;
            this.useFirst = isFirst;
            this.q1 = queue1;
            this.q2 = queue2;
            this.shutdown = shutdownComplete;
            this.closing = shuttingDown;
        }

        @Override
        public void run() {
            try {
                if (useFirst.compareAndSet(true, false)) {
                    callEvent(q1);
                } else {
                    useFirst.set(true);
                    callEvent(q2);
                }
                if (closing.get() && q1.isEmpty() && q2.isEmpty()) {
                    scheduledTimerTask.cancel(false); // Stops this TimerTask
                    final TimerService timer = ServerServiceRegistry.getInstance().getService(TimerService.class);
                    if (timer != null) {
                        timer.purge(); // Remove canceled tasks
                    }
                    shutdownLock.lock();
                    try {
                        shutdown.set(true);
                        allEventsProcessed.signalAll();
                    } finally {
                        shutdownLock.unlock();
                    }
                }
            } catch (Exception t) {
                LOG.error("", t);
            }
        }

        public void setScheduledTimerTask(final ScheduledTimerTask scheduledTimerTask) {
            this.scheduledTimerTask = scheduledTimerTask;
        }
    }

    private static final AtomicBoolean isFirst = new AtomicBoolean(true);

    private static volatile boolean isInit;

    private static volatile boolean noDelay;

    private static List<EventObject> queue1;

    private static List<EventObject> queue2;

    private static int delay = 180000;

    private static volatile boolean isEnabled;

    /*
     * +++++++++++++++ Task Event Lists +++++++++++++++
     */

    private static final List<TaskEventInterface> taskEventList = new CopyOnWriteArrayList<TaskEventInterface>();

    private static final List<TaskEventInterface> noDelayTaskEventList = new CopyOnWriteArrayList<TaskEventInterface>();

    /*
     * +++++++++++++++ Contact Event Lists +++++++++++++++
     */

    private static final List<ContactEventInterface> contactEventList = new CopyOnWriteArrayList<ContactEventInterface>();

    private static final List<ContactEventInterface> noDelayContactEventList = new CopyOnWriteArrayList<ContactEventInterface>();

    /*
     * +++++++++++++++ Folder Event Lists +++++++++++++++
     */

    private static final List<FolderEventInterface> folderEventList = new CopyOnWriteArrayList<FolderEventInterface>();

    private static final List<FolderEventInterface> noDelayFolderEventList = new CopyOnWriteArrayList<FolderEventInterface>();

    /*
     * +++++++++++++++ Infostore Event Lists +++++++++++++++
     */
//
//    private static final List<InfostoreEventInterface> infostoreEventList = new ArrayList<InfostoreEventInterface>(4);
//
//    private static final List<InfostoreEventInterface> noDelayInfostoreEventList = new ArrayList<InfostoreEventInterface>(4);

    private static final AtomicBoolean shuttingDown = new AtomicBoolean();

    private static final ReentrantLock SHUTDOWN_LOCK = new ReentrantLock();

    private static final Condition ALL_EVENTS_PROCESSED = SHUTDOWN_LOCK.newCondition();

    private static final AtomicBoolean shutdownComplete = new AtomicBoolean();

    private static volatile ScheduledTimerTask timerTask;

    private static volatile EventDispatcher newEventDispatcher;

    private EventQueue() {
        super();
    }

    public static void setNewEventDispatcher(final EventDispatcher eventDispatcher) {
        newEventDispatcher = eventDispatcher;
    }

    /**
     * Initializes the {@link EventQueue}.
     *
     * @param config The configuration with which event queue is going to be configured
     */
    static void init(final EventConfig config) {
        delay = config.getEventQueueDelay();

        if (config.isEventQueueEnabled()) {
            LOG.info("Starting EventQueue");

            queue1 = new ArrayList<EventObject>();
            queue2 = new ArrayList<EventObject>();

            noDelay = (delay == 0);

            if (!noDelay) {
                final TimerService timer = ServerServiceRegistry.getInstance().getService(TimerService.class);
                if (timer != null) {
                    final EventQueueTimerTask task2schedule =
                        new EventQueueTimerTask(
                            ALL_EVENTS_PROCESSED,
                            SHUTDOWN_LOCK,
                            isFirst,
                            queue1,
                            queue2,
                            shutdownComplete,
                            shuttingDown);
                    final ScheduledTimerTask timerTask = timer.scheduleWithFixedDelay(task2schedule, delay, delay);
                    task2schedule.setScheduledTimerTask(timerTask);
                    EventQueue.timerTask = timerTask;
                }
            }
            isEnabled = true;
        } else {
            LOG.info("EventQueue is disabled");
        }
        isInit = true;
        shuttingDown.set(false);
    }

    public static void add(final EventObject eventObj) throws OXException {
        if (shuttingDown.get()) {
            LOG.info("Shutting down event system, so no events are accepted. Throwing Invalid State Exception");
            throw new OXException().setLogMessage("Event system is being shut down and therefore does not accept new events.");
        }
        if (null == eventObj) {
            LOG.warn("Skipping null event", new Throwable());
            return;
        }
        LOG.debug("add EventObject: {}", eventObj);

        if (!isEnabled) {
            return;
        }

        if (!isInit) {
            throw new OXException().setLogMessage("EventQueue not initialized!");
        }
        /*
         * Immediate invocation of non-delayed handlers...
         */
        event(eventObj, true);
        /*
         * ... and proceed with delayed handlers
         */
        if (noDelay || eventObj.isNoDelay()) {
            /*
             * Invoke delayed handlers immediately due to configuration or event attributes
             */
            event(eventObj);
        } else {
            /*
             * Enqueue for delayed execution
             */
            if (isFirst.get()) {
                queue1.add(eventObj);
            } else {
                queue2.add(eventObj);
            }
        }
    }

    static void callEvent(final List<EventObject> al) {
        for (int a = 0; a < al.size(); a++) {
            event(al.get(a));
        }

        al.clear();
    }

    static void event(final EventObject eventObj) {
        event(eventObj, false);
    }

    static void event(final EventObject eventObj, final boolean noDelay) {
        if (null == eventObj) {
            LOG.warn("Skipping null event", new Throwable());
            return;
        }
        final int module = eventObj.getModule();
        switch (module) {
        case Types.APPOINTMENT:
            LOG.error("invalid module: {}", I(module));
            break;
        case Types.CONTACT:
            contact(eventObj, noDelay ? noDelayContactEventList : contactEventList);
            break;
        case Types.TASK:
            task(eventObj, noDelay ? noDelayTaskEventList : taskEventList);
            break;
        case Types.FOLDER:
            folder(eventObj, noDelay ? noDelayFolderEventList : folderEventList);
            break;
//        case Types.INFOSTORE:
//            infostore(eventObj, noDelay ? noDelayInfostoreEventList : infostoreEventList);
//            break;
        default:
            LOG.error("invalid module: {}", I(module));
        }
    }

    protected static void contact(final EventObject eventObj, final List<ContactEventInterface> contactEventList) {
        if (contactEventList.isEmpty()) {
            return;
        }
        final int action = eventObj.getAction();
        final Contact contact = (Contact) eventObj.getObject();
        final Session session = eventObj.getSessionObject();
        switch (action) {
        case EventClient.CREATED:
            for (final ContactEventInterface next : contactEventList) {
                try {
                    next.contactCreated(contact, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.CHANGED:
            for (final ContactEventInterface next : contactEventList) {
                try {
                    next.contactModified(contact, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.DELETED:
            for (final ContactEventInterface next : contactEventList) {
                try {
                    next.contactDeleted(contact, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        default:
            LOG.error("invalid action for contact: {}", I(action));
        }
    }

    protected static void task(final EventObject eventObj, final List<TaskEventInterface> taskEventList) {
        if (taskEventList.isEmpty()) {
            return;
        }
        final int action = eventObj.getAction();
        final Task task = (Task) eventObj.getObject();
        final Session session = eventObj.getSessionObject();
        switch (action) {
        case EventClient.CREATED:
            for (final TaskEventInterface next : taskEventList) {
                try {
                    next.taskCreated(task, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.CHANGED:
            for (final TaskEventInterface next : taskEventList) {
                try {
                    next.taskModified(task, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.DELETED:
            for (final TaskEventInterface next : taskEventList) {
                try {
                    next.taskDeleted(task, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.CONFIRM_ACCEPTED:
            for (final TaskEventInterface next : taskEventList) {
                try {
                    next.taskAccepted(task, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.CONFIRM_DECLINED:
            for (final TaskEventInterface next : taskEventList) {
                try {
                    next.taskDeclined(task, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.CONFIRM_TENTATIVE:
            for (final TaskEventInterface next : taskEventList) {
                try {
                    next.taskTentativelyAccepted(task, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        default:
            LOG.error("invalid action for task: {}", I(action));
        }
    }

    protected static void folder(final EventObject eventObj, final List<FolderEventInterface> folderEventList) {
        if (folderEventList.isEmpty()) {
            return;
        }
        final int action = eventObj.getAction();
        final FolderObject folderObject = (FolderObject) eventObj.getObject();
        final Session session = eventObj.getSessionObject();
        switch (action) {
        case EventClient.CREATED:
            for (final FolderEventInterface next : folderEventList) {
                try {
                    next.folderCreated(folderObject, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.CHANGED:
            for (final FolderEventInterface next : folderEventList) {
                try {
                    next.folderModified(folderObject, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        case EventClient.DELETED:
            for (final FolderEventInterface next : folderEventList) {
                try {
                    next.folderDeleted(folderObject, session);
                } catch (Exception t) {
                    LOG.error("", t);
                }
            }
            break;
        default:
            LOG.error("invalid action for folder: {}", I(action));
        }
    }

//    protected static void infostore(final EventObject eventObj, final List<InfostoreEventInterface> infostoreEventList) {
//        if (infostoreEventList.isEmpty()) {
//            return;
//        }
//        final int action = eventObj.getAction();
//        final DocumentMetadata documentMetadata = (DocumentMetadata) eventObj.getObject();
//        final Session session = eventObj.getSessionObject();
//        switch (action) {
//        case EventClient.CREATED:
//            for (final InfostoreEventInterface next : infostoreEventList) {
//                try {
//                    next.infoitemCreated(documentMetadata, session);
//                } catch (Throwable t) {
//                    LOG.error("", t);
//                }
//            }
//            break;
//        case EventClient.CHANGED:
//            for (final InfostoreEventInterface next : infostoreEventList) {
//                try {
//                    next.infoitemModified(documentMetadata, session);
//                } catch (Throwable t) {
//                    LOG.error("", t);
//                }
//            }
//            break;
//        case EventClient.DELETED:
//            for (final InfostoreEventInterface next : infostoreEventList) {
//                try {
//                    next.infoitemDeleted(documentMetadata, session);
//                } catch (Throwable t) {
//                    LOG.error("", t);
//                }
//            }
//            break;
//        default:
//            LOG.error("invalid action for infostore: {}", action);
//        }
//    }

    public static void addTaskEvent(final TaskEventInterface event) {
        if (NoDelayEventInterface.class.isInstance(event)) {
            noDelayTaskEventList.add(event);
        } else {
            taskEventList.add(event);
        }
    }

    public static void addContactEvent(final ContactEventInterface event) {
        if (NoDelayEventInterface.class.isInstance(event)) {
            noDelayContactEventList.add(event);
        } else {
            contactEventList.add(event);
        }
    }

    public static void addFolderEvent(final FolderEventInterface event) {
        if (NoDelayEventInterface.class.isInstance(event)) {
            noDelayFolderEventList.add(event);
        } else {
            folderEventList.add(event);
        }
    }

    public static void removeTaskEvent(final TaskEventInterface event) {
        if (NoDelayEventInterface.class.isInstance(event)) {
            noDelayTaskEventList.remove(event);
        } else {
            taskEventList.remove(event);
        }
    }

    public static void removeContactEvent(final ContactEventInterface event) {
        if (NoDelayEventInterface.class.isInstance(event)) {
            noDelayContactEventList.remove(event);
        } else {
            contactEventList.remove(event);
        }
    }

    public static void removeFolderEvent(final FolderEventInterface event) {
        List<FolderEventInterface> toRemoveFrom = NoDelayEventInterface.class.isInstance(event) ? noDelayFolderEventList : folderEventList;
        toRemoveFrom.remove(event);
    }

    public static void addModernListener(final TaskEventInterface listener) {
        checkEventDispatcher();
        newEventDispatcher.addListener(listener);
    }

    private static void checkEventDispatcher() {
        if (null == newEventDispatcher) {
            throw new IllegalStateException("The event dispatcher must have been initialized before adding listeners.");
        }
    }

    /**
     * Stops execution of events after the next run, still delivers all remaining events. Method blocks until all remaining tasks have been
     * completed
     */
    // TODO: Do we want a timeout here?
    public static void stop() {
        SHUTDOWN_LOCK.lock();
        try {
            if (shutdownComplete.get()) {
                return;
            }
            shuttingDown.set(true);

            if (queue1.isEmpty() && queue2.isEmpty()) {
                // next run.
                return;
            }
            if (null != ServerServiceRegistry.getInstance().getService(TimerService.class)) {
                // TODO TimerService is gone. Maybe event queues must be processed without the task.
                if (!ALL_EVENTS_PROCESSED.await(2 * delay, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Task did not clean event queues on shutdown.");
                }
            }
        } catch (InterruptedException e) {
            // Restore the interrupted status; see http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html
            Thread.currentThread().interrupt();
            LOG.error("", e);
        } finally {
            // Just in case another Thread also stopped the queue, we have to
            // wake that one up as well
            try {
                signalAll(ALL_EVENTS_PROCESSED);
            } finally {
                SHUTDOWN_LOCK.unlock();
            }
        }
    }

    private static void signalAll(final Condition condition) {
        if (null != condition) {
            condition.signalAll();
        }
    }

    public static void clearAllListeners() {
        taskEventList.clear();
        contactEventList.clear();
        folderEventList.clear();
    }
}
