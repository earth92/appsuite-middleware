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

package com.openexchange.push.impl.balancing.reschedulerpolicy;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.instance.EndpointQualifier;
import com.openexchange.config.ConfigurationService;
import com.openexchange.event.CommonEvent;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.UpdaterEventConstants;
import com.openexchange.java.BufferingQueue;
import com.openexchange.push.PushManagerExtendedService;
import com.openexchange.push.PushUser;
import com.openexchange.push.impl.PushManagerRegistry;
import com.openexchange.push.impl.balancing.reschedulerpolicy.portable.PortableCheckForExtendedServiceCallable;
import com.openexchange.push.impl.balancing.reschedulerpolicy.portable.PortableDropAllPermanentListenerCallable;
import com.openexchange.push.impl.balancing.reschedulerpolicy.portable.PortableDropPermanentListenerCallable;
import com.openexchange.push.impl.balancing.reschedulerpolicy.portable.PortablePlanRescheduleCallable;
import com.openexchange.push.impl.balancing.reschedulerpolicy.portable.PortableStartPermanentListenerCallable;
import com.openexchange.push.impl.jobqueue.PermanentListenerJob;
import com.openexchange.push.impl.osgi.Services;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;


/**
 * {@link PermanentListenerRescheduler} - Reschedules permanent listeners equally among cluster nodes, whenever cluster members do change.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.6.2
 */
public class PermanentListenerRescheduler implements ServiceTrackerCustomizer<HazelcastInstance, HazelcastInstance>, MembershipListener, EventHandler {

    /** The logger constant */
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PermanentListenerRescheduler.class);

    // -------------------------------------------------------------------------------------------------------------------------------

    private static volatile Long delayDuration;

    private static long delayDuration() {
        Long tmp = delayDuration;
        if (null == tmp) {
            synchronized (PermanentListenerRescheduler.class) {
                tmp = delayDuration;
                if (null == tmp) {
                    int defaultValue = 5000;
                    ConfigurationService service = Services.optService(ConfigurationService.class);
                    if (null == service) {
                        return defaultValue;
                    }
                    tmp = Long.valueOf(service.getIntProperty("com.openexchange.push.reschedule.delayDuration", defaultValue));
                    delayDuration = tmp;
                }
            }
        }
        return tmp.longValue();
    }

    private static volatile Long timerFrequency;

    private static long timerFrequency() {
        Long tmp = timerFrequency;
        if (null == tmp) {
            synchronized (PermanentListenerRescheduler.class) {
                tmp = timerFrequency;
                if (null == tmp) {
                    int defaultValue = 2000;
                    ConfigurationService service = Services.optService(ConfigurationService.class);
                    if (null == service) {
                        return defaultValue;
                    }
                    tmp = Long.valueOf(service.getIntProperty("com.openexchange.push.reschedule.timerFrequency", defaultValue));
                    timerFrequency = tmp;
                }
            }
        }
        return tmp.longValue();
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    private final PushManagerRegistry pushManagerRegistry;
    private final BundleContext context;
    private final AtomicReference<UUID> registrationIdRef;
    private final AtomicReference<HazelcastInstance> hzInstancerRef;
    private final BufferingQueue<ReschedulePlan> rescheduleQueue;
    private final ReschedulePolicy policy;
    private ScheduledTimerTask scheduledTimerTask; // Accessed synchronized
    private boolean stopped; // Accessed synchronized

    /**
     * Initializes a new {@link PermanentListenerRescheduler}.
     *
     * @param pushManagerRegistry The associated push manager registry
     * @param context The bundle context
     */
    public PermanentListenerRescheduler(PushManagerRegistry pushManagerRegistry, BundleContext context) {
        super();
        this.rescheduleQueue = new BufferingQueue<ReschedulePlan>(delayDuration()); // 5sec default delay;
        this.hzInstancerRef = new AtomicReference<HazelcastInstance>();
        registrationIdRef = new AtomicReference<UUID>();
        this.pushManagerRegistry = pushManagerRegistry;
        this.context = context;
        this.policy = ReschedulePolicy.MASTER;
    }

    /**
     * Stops this rescheduler.
     */
    public void stop() {
        synchronized (this) {
            rescheduleQueue.clear();
            cancelTimerTask();
            stopped = true;
        }
    }

    /**
     * Cancels the currently running timer task (if any).
     */
    protected void cancelTimerTask() {
        ScheduledTimerTask scheduledTimerTask = this.scheduledTimerTask;
        if (null != scheduledTimerTask) {
            scheduledTimerTask.cancel();
            this.scheduledTimerTask = null;
            LOG.info("Canceled timer task for rescheduling checks");
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    @Override
    public void memberAdded(MembershipEvent membershipEvent) {
        try {
            planReschedule(true, "Cluster member added: " + membershipEvent.getMember().getAddress());
        } catch (Exception e) {
            LOG.error("Failed to plan rescheduling", e);
        }
    }

    @Override
    public void memberRemoved(MembershipEvent membershipEvent) {
        try {
            planReschedule(true, "Cluster member removed: " + membershipEvent.getMember().getAddress());
        } catch (Exception e) {
            LOG.error("Failed to plan rescheduling", e);
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    @Override
    public void handleEvent(Event event) {
        if (UpdaterEventConstants.TOPIC.equals(event.getTopic())) {
            if (ReschedulePolicy.MASTER.equals(policy)) {
                try {
                    planReschedule(true, "Update tasks executed.");
                } catch (Exception e) {
                    LOG.error("Failed to plan rescheduling", e);
                }
            } else {
                // Only handle if locally received
                if (event.containsProperty(CommonEvent.PUBLISH_MARKER)) {
                    try {
                        planReschedule(true, "Update tasks executed.");
                    } catch (Exception e) {
                        LOG.error("Failed to plan rescheduling", e);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    /**
     * Plan to reschedule.
     *
     * @param remotePlan <code>true</code> for remote rescheduling; otherwise <code>false</code>
     * @throws OXException If timer service is absent
     */
    public void planReschedule(boolean remotePlan, String reason) throws OXException {
        synchronized (this) {
            // Stopped
            if (stopped) {
                return;
            }

            // Check time task is alive
            ScheduledTimerTask scheduledTimerTask = this.scheduledTimerTask;
            if (null == scheduledTimerTask) {
                // Initialize timer task
                TimerService timerService = Services.requireService(TimerService.class);
                Runnable timerTask = new Runnable() {

                    @Override
                    public void run() {
                        checkReschedule();
                    }
                };
                long delay = timerFrequency(); // 2sec delay
                this.scheduledTimerTask = timerService.scheduleWithFixedDelay(timerTask, delay, delay);
                LOG.info("Initialized new timer task for rescheduling checks");
            }

            // Plan rescheduling
            if (remotePlan) {
                rescheduleQueue.offerOrReplace(ReschedulePlan.getInstance(true));
                LOG.info("Planned rescheduling including remote plan. Reason: {}", reason);
            } else {
                boolean added = rescheduleQueue.offerIfAbsentElseReset(ReschedulePlan.getInstance(false));
                if (added) {
                    LOG.info("Planned rescheduling with local-only plan. Reason: {}", reason);
                }
            }
        }
    }

    /**
     * Checks for an available rescheduling.
     */
    protected void checkReschedule() {
        ReschedulePlan plan;
        synchronized (this) {
            // Stopped
            if (stopped) {
                return;
            }

            plan = rescheduleQueue.poll();

            if (rescheduleQueue.isEmpty()) {
                // No more planned rescheduling operations
                cancelTimerTask();
            }
        }

        // Schedule plan (if any)
        if (null != plan) {
            boolean remotePlan = plan.isRemotePlan();
            doReschedule(this.policy, remotePlan);
            LOG.info("Triggered rescheduling of permanent listeners {}", remotePlan ? "incl. remote rescheduling" : "local-only");
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    /**
     * Reschedules permanent listeners among available cluster members.
     *
     * @param policy The rescheduling policy to obey
     * @param remotePlan Whether to plan rescheduling on remote members or not
     */
    private void doReschedule(ReschedulePolicy policy, boolean remotePlan) {
        reschedule(hzInstancerRef.get(), policy, remotePlan);
    }

    /**
     * Reschedules permanent listeners among available cluster members.
     *
     * @param allMembers All available cluster members
     * @param hzInstance The associated Hazelcast instance
     * @param policy The rescheduling policy to obey
     * @param remotePlan Whether to plan rescheduling on remote members or not
     */
    private void reschedule(HazelcastInstance hzInstance, ReschedulePolicy policy, boolean remotePlan) {
        if (null == hzInstance) {
            LOG.warn("Aborted rescheduling of permanent listeners as passed HazelcastInstance is null.");
            return;
        }

        Cluster cluster = hzInstance.getCluster();
        Set<Member> allMembers = cluster.getMembers();

        // Determine push users to distribute among cluster members
        List<PushUser> allPushUsers;
        try {
            allPushUsers = pushManagerRegistry.getUsersWithPermanentListeners();
            if (allPushUsers.isEmpty()) {
                // No such users at all
                return;
            }
        } catch (Exception e) {
            LOG.warn("Failed to distribute permanent listeners among cluster nodes", e);
            return;
        }

        // Acquire optional thread pool
        ThreadPoolService threadPool = Services.optService(ThreadPoolService.class);
        if (null == threadPool) {
            // Perform with current thread
            try {
                ThreadPools.execute(new ReschedulerTask(allMembers, allPushUsers, hzInstance, pushManagerRegistry, policy, remotePlan));
            } catch (Exception e) {
                LOG.warn("Failed to distribute permanent listeners among cluster nodes", e);
            }
        } else {
            // Submit task to thread pool
            threadPool.submit(new ReschedulerTask(allMembers, allPushUsers, hzInstance, pushManagerRegistry, policy, remotePlan));
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    @Override
    public HazelcastInstance addingService(ServiceReference<HazelcastInstance> reference) {
        HazelcastInstance hzInstance = context.getService(reference);
        try {
            Cluster cluster = hzInstance.getCluster();
            UUID registrationId = cluster.addMembershipListener(this);
            registrationIdRef.set(registrationId);

            hzInstancerRef.set(hzInstance);

            planReschedule(true, "Hazelcast instance appeared");

            return hzInstance;
        } catch (Exception e) {
            LOG.warn("Failed to distribute permanent listeners among cluster nodes", e);
        }
        context.ungetService(reference);
        return null;
    }

    @Override
    public void modifiedService(ServiceReference<HazelcastInstance> reference, HazelcastInstance hzInstance) {
        // Nothing
    }

    @Override
    public void removedService(ServiceReference<HazelcastInstance> reference, HazelcastInstance hzInstance) {
        UUID registrationId = registrationIdRef.get();
        if (null != registrationId) {
            try {
                hzInstance.getCluster().removeMembershipListener(registrationId);
            } catch (Exception e) {
                // Ignore
                LOG.debug("Failed to remove membership listener", e);
            }
            registrationIdRef.set(null);
        }

        hzInstancerRef.set(null);
        context.ungetService(reference);
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    private static class ReschedulerTask extends AbstractTask<Void> {

        private final Set<Member> allMembers;
        private final List<PushUser> allPushUsers;
        private final HazelcastInstance hzInstance;
        private final PushManagerRegistry pushManagerRegistry;
        private final ReschedulePolicy policy;
        private final boolean remotePlan;

        /**
         * Initializes a new {@link ReschedulerTask}.
         */
        ReschedulerTask(Set<Member> allMembers, List<PushUser> allPushUsers, HazelcastInstance hzInstance, PushManagerRegistry pushManagerRegistry, ReschedulePolicy policy, boolean remotePlan) {
            super();
            this.allMembers = allMembers;
            this.allPushUsers = allPushUsers;
            this.hzInstance = hzInstance;
            this.pushManagerRegistry = pushManagerRegistry;
            this.policy = policy;
            this.remotePlan = remotePlan;
        }

        @Override
        public Void call() {
            try {
                // Get local member
                Member localMember = hzInstance.getCluster().getLocalMember();

                // Determine other cluster members
                Set<Member> otherMembersInCluster = getOtherMembers(allMembers, localMember);

                int numberOfOtherMembersInCluster = otherMembersInCluster.size();
                if (numberOfOtherMembersInCluster <= 0) {
                    // No other cluster members - assign all available permanent listeners to this node
                    LOG.info("Going to apply all push users to local member (no other members available): {}", localMember);
                    List<PermanentListenerJob> startedOnes = pushManagerRegistry.applyInitialListeners(allPushUsers, true, 0L);
                    LOG.info("{} now runs permanent listeners for {} users", localMember, I(startedOnes.size()));
                    return null;
                }

                // Identify those members having at least one "PushManagerExtendedService" instance
                List<Member> capableMembers = new ArrayList<Member>(numberOfOtherMembersInCluster + 1);
                capableMembers.add(localMember);
                boolean allUsersStarted = false;
                {
                    IExecutorService executor = hzInstance.getExecutorService("default");
                    Map<Member, Future<String>> futureMap = executor.submitToMembers(new PortableCheckForExtendedServiceCallable(localMember.getUuid(), localMember.getVersion().toString()), otherMembersInCluster);
                    for (Map.Entry<Member, Future<String>> entry : futureMap.entrySet()) {
                        Member member = entry.getKey();
                        Future<String> future = entry.getValue();
                        // Check Future's return value
                        int retryCount = 3;
                        NextTry: while (retryCount-- > 0) {
                            try {
                                JSONObject jNodeInfo = new JSONObject(future.get());
                                retryCount = 0;
                                allUsersStarted |= jNodeInfo.getBoolean(PortableCheckForExtendedServiceCallable.NODE_INFO_ALL_USERS_STARTED);
                                boolean isCapable = jNodeInfo.getBoolean(PortableCheckForExtendedServiceCallable.NODE_INFO_PERMANENT_PUSH_ALLOWED);
                                if (isCapable) {
                                    capableMembers.add(member);
                                    LOG.info("Cluster member \"{}\" also runs a resource-acquiring {}, hence considered as companion.", member, PushManagerExtendedService.class.getSimpleName());
                                } else {
                                    LOG.info("Cluster member \"{}\" does not run a resource-acquiring {}, hence ignored.", member, PushManagerExtendedService.class.getSimpleName());
                                }
                            } catch (InterruptedException e) {
                                // Interrupted - Keep interrupted state
                                Thread.currentThread().interrupt();
                                LOG.warn("Interrupted while distributing permanent listeners among cluster nodes", e);
                                return null;
                            } catch (CancellationException e) {
                                // Canceled
                                LOG.warn("Canceled while distributing permanent listeners among cluster nodes", e);
                                return null;
                            } catch (ExecutionException e) {
                                Throwable cause = e.getCause();

                                // Check for Hazelcast timeout
                                if (!(cause instanceof com.hazelcast.core.OperationTimeoutException)) {
                                    if (cause instanceof com.hazelcast.core.MemberLeftException) {
                                        // Target member left cluster
                                        retryCount = 0;
                                        LOG.info("Member \"{}\" has left cluster, hence ignored for rescheduling computation.", member);
                                        cancelFutureSafe(future);
                                        continue NextTry;
                                    }
                                    if (cause instanceof IOException) {
                                        throw ((IOException) cause);
                                    }
                                    if (cause instanceof RuntimeException) {
                                        throw ((RuntimeException) cause);
                                    }
                                    if (cause instanceof Error) {
                                        throw (Error) cause;
                                    }
                                    throw new IllegalStateException("Not unchecked", cause == null ? e : cause);
                                }

                                // Timeout while awaiting remote result
                                if (retryCount > 0) {
                                    LOG.info("Timeout while awaiting remote result from cluster member \"{}\". Retry...", member);
                                } else {
                                    // No further retry
                                    LOG.info("Giving up awaiting remote result from cluster member \"{}\", hence ignored for rescheduling computation.", member);
                                    cancelFutureSafe(future);
                                }
                            }
                        }
                    }
                }

                // Check capable members
                if (capableMembers.isEmpty() && !allUsersStarted) {
                    // No other cluster members - assign all available permanent listeners to this node
                    LOG.info("Going to apply all push users to local member (no other capable member available): {}", localMember);
                    List<PermanentListenerJob> startedOnes = pushManagerRegistry.applyInitialListeners(allPushUsers, true, 0L);
                    LOG.info("{} now runs permanent listeners for {} users", localMember, I(startedOnes.size()));
                    return null;
                }

                // First, sort by UUID
                Collections.sort(capableMembers, new Comparator<Member>() {

                    @Override
                    public int compare(Member m1, Member m2) {
                        return toString(m1).compareTo(toString(m2));
                    }

                    private String toString(Member m) {
                        InetSocketAddress addr = m.getSocketAddress(EndpointQualifier.MEMBER);
                        return addr == null ? m.toString() : new StringBuilder(24).append(addr.getAddress().getHostAddress()).append(':').append(addr.getPort()).toString();
                    }
                });

                if (false == remotePlan) { // <-- Called from remote node to perform a new reschedule; see PortablePlanRescheduleCallable
                    // Determine the position of this cluster node (ReschedulePolicy.PER_NODE.equals(policy)
                    int pos = 0;
                    while (!localMember.getUuid().equals(capableMembers.get(pos).getUuid())) {
                        pos = pos + 1;
                    }

                    // Determine the permanent listeners for this node
                    List<PushUser> ps = new LinkedList<PushUser>();
                    int numMembers = capableMembers.size();
                    int numPushUsers = allPushUsers.size();
                    for (int i = 0; i < numPushUsers; i++) {
                        if ((i % numMembers) == pos) {
                            ps.add(allPushUsers.get(i));
                        }
                    }

                    // Apply newly calculated initial permanent listeners
                    List<PermanentListenerJob> startedOnes = pushManagerRegistry.applyInitialListeners(ps, false, TimeUnit.NANOSECONDS.convert(2L, TimeUnit.SECONDS));
                    int numberOfStartedOnes = startedOnes.size();

                    LOG.info("{} now runs permanent listeners for {} users", localMember, I(numberOfStartedOnes));

                    // For safety reason, request explicit drop on other nodes for push users started on this node
                    List<PushUser> pushUsers = new ArrayList<>(numberOfStartedOnes);
                    for (PermanentListenerJob job : startedOnes) {
                        pushUsers.add(job.getPushUser());
                    }
                    new DropPushUserTask(pushUsers, capableMembers, hzInstance).run();
                    return null;
                }

                // Remote plan by what policy?
                long _2secNanos = TimeUnit.NANOSECONDS.convert(2L, TimeUnit.SECONDS);
                if (ReschedulePolicy.PER_NODE.equals(policy)) {
                    LOG.info("Going to distribute permanent listeners among cluster nodes: {}", capableMembers);

                    // Check if required to also plan a rescheduling at remote members
                    IExecutorService executor = hzInstance.getExecutorService("default");
                    executor.submitToMembers(new PortablePlanRescheduleCallable(localMember.getUuid()), capableMembers);

                    // Determine the position of this cluster node
                    int pos = 0;
                    while (!localMember.getUuid().equals(capableMembers.get(pos).getUuid())) {
                        pos = pos + 1;
                    }

                    // Determine the permanent listeners for this node
                    List<PushUser> ps = new LinkedList<PushUser>();
                    int numMembers = capableMembers.size();
                    int numPushUsers = allPushUsers.size();
                    for (int i = 0; i < numPushUsers; i++) {
                        if ((i % numMembers) == pos) {
                            ps.add(allPushUsers.get(i));
                        }
                    }

                    // Apply newly calculated initial permanent listeners
                    List<PermanentListenerJob> startedOnes = pushManagerRegistry.applyInitialListeners(ps, false, _2secNanos);
                    LOG.info("{} now runs permanent listeners for {} users", localMember, I(startedOnes.size()));
                } else {
                    Member master = capableMembers.get(0);
                    if (localMember.getUuid().equals(master.getUuid())) {
                        // Local member is the master
                        LOG.info("Going to distribute permanent listeners among cluster nodes, because I am the master \"{}\": {}", master, capableMembers);

                        // Request to stop on remote nodes
                        IExecutorService executor = hzInstance.getExecutorService("default");
                        Map<Member, Future<Boolean>> futureMap = executor.submitToMembers(new PortableDropAllPermanentListenerCallable(master.getUuid()), capableMembers);

                        // Stop all on local node, too
                        PushManagerRegistry.getInstance().stopAllPermanentListenerForReschedule(); // No reconnect since we are going to restart them in cluster

                        // Await remote nodes to stop permanent listeners
                        for (Map.Entry<Member, Future<Boolean>> entry : futureMap.entrySet()) {
                            Member member = entry.getKey();
                            Future<Boolean> future = entry.getValue();
                            // Check Future's return value
                            int retryCount = 3;
                            while (retryCount-- > 0) {
                                try {
                                    future.get().booleanValue();
                                    retryCount = 0;
                                } catch (InterruptedException e) {
                                    // Interrupted - Keep interrupted state
                                    Thread.currentThread().interrupt();
                                    LOG.warn("Interrupted while requesting to drop permanent listeners on cluster nodes", e);
                                    return null;
                                } catch (CancellationException e) {
                                    // Canceled
                                    LOG.warn("Canceled while requesting to drop permanent listeners on cluster nodes", e);
                                    return null;
                                } catch (ExecutionException e) {
                                    Throwable cause = e.getCause();

                                    // Check for Hazelcast timeout
                                    if (!(cause instanceof com.hazelcast.core.OperationTimeoutException)) {
                                        if (cause instanceof IOException) {
                                            throw ((IOException) cause);
                                        }
                                        if (cause instanceof RuntimeException) {
                                            throw ((RuntimeException) cause);
                                        }
                                        if (cause instanceof Error) {
                                            throw (Error) cause;
                                        }
                                        throw new IllegalStateException("Not unchecked", cause);
                                    }

                                    // Timeout while awaiting remote result
                                    if (retryCount > 0) {
                                        LOG.info("Timeout while requesting to drop permanent listeners on cluster member \"{}\". Retry...", member);
                                    } else {
                                        // No further retry
                                        LOG.info("Giving up requesting to drop permanent listeners on cluster member \"{}\".", member);
                                        cancelFutureSafe(future);
                                    }
                                }
                            }
                        }

                        // Safety park to ensure all resources are released
                        LockSupport.parkNanos(_2secNanos);

                        int pos = 0;
                        int numMembers = capableMembers.size();
                        int numPushUsers = allPushUsers.size();

                        List<PushUser> myList = null;
                        for (Member candidate : capableMembers) {
                            List<PushUser> ps = new LinkedList<PushUser>();
                            for (int i = 0; i < numPushUsers; i++) {
                                if ((i % numMembers) == pos) {
                                    ps.add(allPushUsers.get(i));
                                }
                            }

                            if (localMember.getUuid().equals(candidate.getUuid())) {
                                myList = ps;
                            } else {
                                executor.submitToMember(new PortableStartPermanentListenerCallable(ps, _2secNanos) , candidate);
                                LOG.info("Requested to start the {} permanent listeners on member \"{}\"", Integer.valueOf(ps.size()), candidate);
                            }
                            pos++;
                        }

                        // Apply newly calculated initial permanent listeners
                        List<PermanentListenerJob> startedOnes = pushManagerRegistry.applyInitialListeners(null == myList ? Collections.<PushUser>emptyList() : myList, false, _2secNanos);
                        LOG.info("This cluster member \"{}\" now runs permanent listeners for {} users", localMember, Integer.valueOf(startedOnes.size()));
                    } else {
                        LOG.info("Awaiting the permanent listeners to start as dictated by master \"{}\"", master);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to distribute permanent listeners among cluster nodes", e);
            }
            return null;
        }
    } // End of class ReschedulerTask

    static Set<Member> getOtherMembers(Set<Member> allMembers, Member localMember) {
        Set<Member> otherMembers = new LinkedHashSet<Member>(allMembers);
        if (!otherMembers.remove(localMember)) {
            LOG.warn("Couldn't remove local member from cluster members.");
        }
        return otherMembers;
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    private static class DropPushUserTask implements Runnable {

        private final HazelcastInstance hzInstance;
        private final Collection<Member> otherMembers;
        private final List<PushUser> pushUsers;

        DropPushUserTask(List<PushUser> pushUsers, Collection<Member> otherMembers, HazelcastInstance hzInstance) {
            super();
            this.pushUsers = pushUsers;
            this.otherMembers = otherMembers;
            this.hzInstance = hzInstance;
        }

        @Override
        public void run() {
            try {
                IExecutorService executor = hzInstance.getExecutorService("default");
                Map<Member, Future<Boolean>> futureMap = executor.submitToMembers(new PortableDropPermanentListenerCallable(pushUsers), otherMembers);
                for (Map.Entry<Member, Future<Boolean>> entry : futureMap.entrySet()) {
                    Member member = entry.getKey();
                    Future<Boolean> future = entry.getValue();
                    // Check Future's return value
                    int retryCount = 3;
                    while (retryCount-- > 0) {
                        try {
                            boolean dropped = future.get().booleanValue();
                            retryCount = 0;
                            if (dropped) {
                                LOG.info("Successfully requested to drop locally running push users on cluster member \"{}\".", member);
                            } else {
                                LOG.info("Failed to drop locally running push users on cluster member \"{}\".", member);
                            }
                        } catch (InterruptedException e) {
                            // Interrupted - Keep interrupted state
                            Thread.currentThread().interrupt();
                            LOG.warn("Interrupted while dropping locally running push users on cluster nodes", e);
                            return;
                        } catch (CancellationException e) {
                            // Canceled
                            LOG.warn("Canceled while dropping locally running push users on cluster nodes", e);
                            return;
                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause();

                            // Check for Hazelcast timeout
                            if (!(cause instanceof com.hazelcast.core.OperationTimeoutException)) {
                                if (cause instanceof IOException) {
                                    throw ((IOException) cause);
                                }
                                if (cause instanceof RuntimeException) {
                                    throw ((RuntimeException) cause);
                                }
                                if (cause instanceof Error) {
                                    throw (Error) cause;
                                }
                                throw new IllegalStateException("Not unchecked", cause);
                            }

                            // Timeout while awaiting remote result
                            if (retryCount > 0) {
                                LOG.info("Timeout while awaiting remote result from cluster member \"{}\". Retry...", member);
                            } else {
                                // No further retry
                                LOG.info("Giving up awaiting remote result from cluster member \"{}\".", member);
                                cancelFutureSafe(future);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to stop permanent listener for locally running push users on other cluster nodes", e);
            }
        } // End of run() method

    } // End of class DropPushUserTask

    static <T> void cancelFutureSafe(Future<T> future) {
        if (null != future) {
            try { future.cancel(true); } catch (Exception e) {/*Ignore*/}
        }
    }

}
