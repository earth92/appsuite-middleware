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

package com.openexchange.http.requestwatcher.internal;

import static com.eaio.util.text.HumanTime.exactly;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.openexchange.config.ConfigurationService;
import com.openexchange.http.requestwatcher.osgi.services.RequestRegistryEntry;
import com.openexchange.http.requestwatcher.osgi.services.RequestTrace;
import com.openexchange.http.requestwatcher.osgi.services.RequestWatcherService;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.sessiond.SessiondServiceExtended;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;

/**
 * {@link RequestWatcherServiceImpl}
 *
 * @author <a href="mailto:marc.arens@open-xchange.com">Marc Arens</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class RequestWatcherServiceImpl implements RequestWatcherService {

    /** The logger. */
    protected static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RequestWatcherServiceImpl.class);

    /** The request number */
    private static final AtomicLong NUMBER = new AtomicLong();

    /** The decimal format to use when printing milliseconds */
    protected static final NumberFormat MILLIS_FORMAT = newNumberFormat();

    /** The accompanying lock for shared decimal format */
    protected static final Lock MILLIS_FORMAT_LOCK = new ReentrantLock();

    /**
     * Creates a new {@code DecimalFormat} instance.
     *
     * @return The format instance
     */
    protected static NumberFormat newNumberFormat() {
        NumberFormat f = NumberFormat.getInstance(Locale.US);
        if (f instanceof DecimalFormat) {
            DecimalFormat df = (DecimalFormat) f;
            df.applyPattern("#,##0");
        }
        return f;
    }

    // --------------------------------------------------------------------------------------------------------------------------

    /** Navigable set, entries ordered by age(youngest first), weakly consistent iterator */
    private final ConcurrentSkipListSet<RequestRegistryEntry> requestRegistry;

    /** The watcher task */
    private volatile ScheduledTimerTask requestWatcherTask;

    /**
     * Initializes a new {@link RequestWatcherServiceImpl}
     *
     * @param configService The configuration service used for initialization
     * @param timerService The timer service used for initialization
     */
    public RequestWatcherServiceImpl(ConfigurationService configService, TimerService timerService) {
        super();
        requestRegistry = new ConcurrentSkipListSet<RequestRegistryEntry>();
        // Get Configuration
        boolean isWatcherEnabled = configService.getBoolProperty("com.openexchange.requestwatcher.isEnabled", true);
        int watcherFrequency = configService.getIntProperty("com.openexchange.requestwatcher.frequency", 30000);
        int requestMaxAge = configService.getIntProperty("com.openexchange.requestwatcher.maxRequestAge", 60000);
        if (isWatcherEnabled) {
            // Create ScheduledTimerTask to watch requests
            ConcurrentSkipListSet<RequestRegistryEntry> requestRegistry = this.requestRegistry;
            Watcher task = new Watcher(requestRegistry, requestMaxAge);
            ScheduledTimerTask requestWatcherTask = timerService.scheduleAtFixedRate(task, requestMaxAge, watcherFrequency);
            this.requestWatcherTask = requestWatcherTask;
        }
    }

    @Override
    public RequestRegistryEntry registerRequest(HttpServletRequest request, HttpServletResponse response, Thread thread, Map<String, String> propertyMap) {
        RequestRegistryEntry registryEntry = new RequestRegistryEntry(NUMBER.incrementAndGet(), request, thread, propertyMap);
        requestRegistry.add(registryEntry);
        return registryEntry;
    }

    @Override
    public boolean unregisterRequest(RequestRegistryEntry registryEntry) {
        return requestRegistry.remove(registryEntry);
    }

    @Override
    public boolean stopWatching() {
        ScheduledTimerTask requestWatcherTask = this.requestWatcherTask;
        if (null != requestWatcherTask) {
            boolean canceled = requestWatcherTask.cancel();
            this.requestWatcherTask = null;
            return canceled;
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------------- //

    private final static class Watcher implements Runnable {

        private final String lineSeparator;
        private final ConcurrentSkipListSet<RequestRegistryEntry> requestRegistry;
        private final int requestMaxAge;
        private final String propSessionId = LogProperties.Name.SESSION_SESSION_ID.getName();

        /**
         * Initializes a new {@link Watcher}.
         */
        Watcher(ConcurrentSkipListSet<RequestRegistryEntry> requestRegistry, int requestMaxAge) {
            super();
            this.lineSeparator = Strings.getLineSeparator();
            this.requestRegistry = requestRegistry;
            this.requestMaxAge = requestMaxAge;
        }

        /**
         * Start at the end of the navigable Set to get the oldest request first. Then proceed to the younger requests. Stop
         * processing at the first yet valid request.
         */
        @Override
        public void run() {
            try {
                boolean debugEnabled = LOG.isDebugEnabled();
                Iterator<RequestRegistryEntry> descendingEntryIterator = requestRegistry.descendingIterator();
                StringBuilder sb = new StringBuilder(256);
                boolean stillOldRequestsLeft = true;
                while (stillOldRequestsLeft && descendingEntryIterator.hasNext()) {
                    // Debug logging
                    if (debugEnabled) {
                        sb.setLength(0);
                        List<Object> args = new ArrayList<>();
                        for (RequestRegistryEntry entry : requestRegistry) {
                            sb.append("{}RegisteredThreads:{}    age: ").append(entry.getAge()).append(" ms").append(
                                ", thread: ").append(entry.getThreadInfo());
                            args.add(lineSeparator);
                            args.add(lineSeparator);
                        }
                        final String entries = sb.toString();
                        if (!entries.isEmpty()) {
                            LOG.debug(sb.toString(), args.toArray(new Object[args.size()]));
                        }
                    }

                    // Check entry's age
                    RequestRegistryEntry entry = descendingEntryIterator.next();
                    if (entry.getAge() > requestMaxAge) {
                        sb.setLength(0);
                        boolean interrupted = handleEntry(entry, sb);
                        if (interrupted) {
                            requestRegistry.remove(entry);
                        }
                    } else {
                        stillOldRequestsLeft = false;
                    }
                }
            } catch (Exception e) {
                LOG.error("Request watcher run failed", e);
            }
        }

        private boolean handleEntry(RequestRegistryEntry entry, StringBuilder logBuilder) {
            // Age info
            AgeInfo ageInfo = newAgeInfo(entry.getAge(), requestMaxAge);

            // Get trace for associated thread's trace
            Throwable trace = new RequestTrace(ageInfo.sAge, ageInfo.sMaxAge, entry.getThread().getName());
            boolean interrupt;
            {
                StackTraceElement[] stackTrace = entry.getStackTrace();
                interrupt = interrupt(stackTrace, entry);
                if (dontLog(stackTrace)) {
                    if (interrupt) {
                        entry.getThread().interrupt();
                        return true;
                    }
                    return false;
                }
                trace.setStackTrace(stackTrace);
            }
            try {
                logBuilder.append("Request with age ").append(ageInfo.sAge).append("ms (").append(exactly(entry.getAge(), true)).append(") exceeds max. age of ").append(ageInfo.sMaxAge).append("ms (").append(exactly(requestMaxAge, true)).append(").") ;
            } catch (Exception e) {
                LOG.trace("", e);
                logBuilder.append("Request with age ").append(ageInfo.sAge).append("ms exceeds max. age of ").append(ageInfo.sMaxAge).append("ms.");
            }

            // Append log properties from the ThreadLocal to logBuilder
            List<Object> args = new ArrayList<>();
            if (false == appendLogProperties(entry, logBuilder, args)) {
                // Turns out to be an invalid registry entry -- already interrupted at this point
                return true;
            }

            // Check if request's thread is supposed to be interrupted
            if (interrupt) {
                logBuilder.append("{}").append("Associated thread will be interrupted!");
                args.add(lineSeparator);
                args.add(trace);
                LOG.info(logBuilder.toString(), args.toArray(new Object[args.size()]));
                entry.getThread().interrupt();
                return true;
            }

            // Non-interrupted entry
            args.add(trace);
            LOG.info(logBuilder.toString(), args.toArray(new Object[args.size()]));
            return false;
        }

        /**
         * For debugging
         *
         * @param trace The {@link StackTraceElement}
         * @param entry The {@link RequestRegistryEntry}
         */
        private boolean interrupt(StackTraceElement[] trace, RequestRegistryEntry entry) {
            /*-
            StackTraceElement traceElement = trace[0];

            // Kept in socket read and exceeded doubled max. request age
            if (traceElement.isNativeMethod() && "socketRead0".equals(traceElement.getMethodName()) && entry.getAge() > (requestMaxAge << 1)) {
                return true;
            }
            */

            // TODO: More interruptible traces?
            return false;
        }

        private boolean appendLogProperties(RequestRegistryEntry entry, StringBuilder logBuilder, List<Object> args) {
            Map<String, String> propertyMap = entry.getPropertyMap();
            if (null != propertyMap) {
                // Sort the properties for readability
                Map<String, String> sorted = new TreeMap<String, String>();
                for (Entry<String, String> propertyEntry : propertyMap.entrySet()) {
                    String propertyName = propertyEntry.getKey();
                    String value = propertyEntry.getValue();
                    if (null != value) {
                        if (propSessionId.equals(propertyName) && !isValidSession(value)) {
                            // Non-existent or elapsed session
                            entry.getThread().interrupt();
                            requestRegistry.remove(entry);
                            return false;
                        }
                        sorted.put(propertyName, value);
                    }
                }
                logBuilder.append(" Request's properties:{}");
                args.add(lineSeparator);

                // And add them to the logBuilder
                Iterator<Entry<String, String>> it = sorted.entrySet().iterator();
                if (it.hasNext()) {
                    String prefix = "  ";
                    Map.Entry<String, String> propertyEntry = it.next();
                    logBuilder.append(prefix).append(propertyEntry.getKey()).append('=').append(propertyEntry.getValue());
                    while (it.hasNext()) {
                        propertyEntry = it.next();
                        logBuilder.append("{}").append(prefix).append(propertyEntry.getKey()).append('=').append(propertyEntry.getValue());
                        args.add(lineSeparator);
                    }
                }
            }
            return true;
        }

        private boolean isValidSession(String sessionId) {
            SessiondService sessiondService = SessiondService.SERVICE_REFERENCE.get();
            return sessiondService instanceof SessiondServiceExtended ? ((SessiondServiceExtended) sessiondService).isActive(sessionId) : true;
        }

        private boolean dontLog(StackTraceElement[] trace) {
            for (StackTraceElement ste : trace) {
                String className = ste.getClassName();
                if (null != className) {
                    if (className.startsWith("org.apache.commons.fileupload.MultipartStream$ItemInputStream")) {
                        // A long-running file upload. Ignore
                        return true;
                    }
                }
            }
            return false;
        }

    } // End of class Watcher

    /**
     * Creates a new age info for given arguments.
     *
     * @param age The current age
     * @param requestMaxAge The age threshold
     * @return The gae info
     */
    protected static AgeInfo newAgeInfo(long age, int requestMaxAge) {
        if (MILLIS_FORMAT_LOCK.tryLock()) {
            try {
                return new AgeInfo(MILLIS_FORMAT.format(age), MILLIS_FORMAT.format(requestMaxAge));
            } finally {
                MILLIS_FORMAT_LOCK.unlock();
            }
        }

        // Use thread-specific DecimalFormat instance
        NumberFormat format = newNumberFormat();
        return new AgeInfo(format.format(age), format.format(requestMaxAge));
    }

    private final static class AgeInfo {

        final String sAge;
        final String sMaxAge;

        AgeInfo(String sAge, String sMaxAge) {
            super();
            this.sAge = sAge;
            this.sMaxAge = sMaxAge;
        }
    }

}
