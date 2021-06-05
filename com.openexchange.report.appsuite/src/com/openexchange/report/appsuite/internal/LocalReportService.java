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

package com.openexchange.report.appsuite.internal;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.java.util.UUIDs;
import com.openexchange.report.appsuite.ContextReport;
import com.openexchange.report.appsuite.ContextReportCumulator;
import com.openexchange.report.appsuite.ReportExceptionCodes;
import com.openexchange.report.appsuite.ReportFinishingTouches;
import com.openexchange.report.appsuite.ReportSystemHandler;
import com.openexchange.report.appsuite.jobs.AnalyzeContextBatch;
import com.openexchange.report.appsuite.serialization.Report;
import com.openexchange.report.appsuite.serialization.ReportConfigs;
import com.openexchange.report.appsuite.storage.ChunkingUtilities;
import com.openexchange.report.appsuite.storage.ContextLoader;
import com.openexchange.user.UserExceptionCode;

/**
 * {@link LocalReportService}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @author <a href="mailto:vitali.sjablow@open-xchange.com">Vitali Sjablow</a>
 * @since v7.8.2
 */
public class LocalReportService extends AbstractReportService {

    //--------------------Class Attributes--------------------

    /** The logger constant */
    static final Logger LOG = org.slf4j.LoggerFactory.getLogger(LocalReportService.class);

    /**
     * Cache has concurrency level 20 as 20 threads will be able to update the cache concurrently.
     *
     * 'Implementations of this interface are expected to be thread-safe, and can be safely accessed by multiple concurrent threads.' from http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/cache/Cache.html
     */
    private static final Cache<String, Map<String, Report>> reportCache = CacheBuilder.newBuilder().concurrencyLevel(ReportProperties.getMaxThreadPoolSize()).expireAfterAccess(180, TimeUnit.MINUTES).<String, Map<String, Report>> build();

    private static final Cache<String, Map<String, Report>> failedReportCache = CacheBuilder.newBuilder().concurrencyLevel(ReportProperties.getMaxThreadPoolSize()).expireAfterAccess(180, TimeUnit.MINUTES).<String, Map<String, Report>> build();

    private static AtomicReference<ExecutorService> EXECUTOR_SERVICE_REF = new AtomicReference<>();

    final AtomicInteger threadNumber = new AtomicInteger();

    //--------------------Public override methods--------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public Report getLastReport(String reportType) {
        LOG.info("Get the latest report of type: {}", reportType);
        Map<String, Report> finishedReports = reportCache.asMap().get(REPORTS_KEY);
        if (finishedReports == null) {
            return null;
        }
        return finishedReports.get(reportType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Report[] getPendingReports(String reportType) {
        LOG.info("Get all pending reports of type: {}", reportType);
        Map<String, Report> pendingReports = reportCache.asMap().get(PENDING_REPORTS_PRE_KEY + reportType);
        if (pendingReports == null) {
            return null;
        }
        return pendingReports.values().toArray(new Report[pendingReports.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flushPending(String uuid, String reportType) {
        LOG.info("Remove pending report of type: {} and id: {}" ,reportType, uuid);
        Map<String, Report> pendingReports = reportCache.asMap().get(PENDING_REPORTS_PRE_KEY + reportType);
        pendingReports.remove(uuid);
        List<Runnable> shutdownNow = EXECUTOR_SERVICE_REF.get().shutdownNow();
        LOG.info("Report generation for report type {} with UUID {} canceled. Canceled {} planned threads.", reportType, uuid, I(shutdownNow.size()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finishContext(ContextReport contextReport) throws OXException {
        String reportType = contextReport.getType();
        Map<String, Report> pendingReports = reportCache.asMap().get(PENDING_REPORTS_PRE_KEY + reportType);

        if ((pendingReports == null) || (pendingReports.isEmpty())) {
            //stopped (and removed) in the meanwhile
            return;
        }

        Report report = pendingReports.get(contextReport.getUUID());
        if (report == null) {
            // Somebody cancelled the report, so just discard the result
            throw ReportExceptionCodes.REPORT_GENERATION_CANCELED.create();
        }
        // Run all applicable cumulators to add the context report results to the global report
        for (ContextReportCumulator cumulator : Services.getContextReportCumulators()) {
            if (cumulator.appliesTo(reportType)) {
                Collection<Object> reportValues = report.getNamespace(Report.MACDETAIL).values();
                if (reportValues.size() >= ReportProperties.getMaxChunkSize()) {
                    synchronized (report) {
                        cumulator.merge(contextReport, report);
                    }
                } else {
                    cumulator.merge(contextReport, report);
                }

            }
        }
        pendingReports.put(report.getUUID(), report);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void abortContextReport(String uuid, String reportType) {
        Map<String, Report> pendingReports = reportCache.asMap().get(PENDING_REPORTS_PRE_KEY + reportType);
        if ((pendingReports == null) || (pendingReports.isEmpty())) {
            //stopped (and removed) in the meanwhile
            return;
        }

        Report report = pendingReports.get(uuid);
        if (report == null) {
            return;
        }
        pendingReports.put(report.getUUID(), report);
    }

    @Override
    public void abortGeneration(String uuid, String reportType, String reason) {
        LOG.info("Abort report generation with the uuid: {} and type: {} with the given reason: {}", uuid, reportType, reason);
        Map<String, Report> pendingReports = reportCache.asMap().get(PENDING_REPORTS_PRE_KEY + reportType);
        Report stoppedReport = pendingReports.get(uuid);
        if (stoppedReport == null) { // already removed from pending reports
            return;
        }
        stoppedReport.set("error", reportType, reason);

        Map<String, Report> stoppedPendingReports = failedReportCache.asMap().get(REPORTS_ERROR_KEY + reportType);
        if (stoppedPendingReports == null) {
            stoppedPendingReports = new HashMap<>();
        }
        stoppedPendingReports.put(reportType, stoppedReport);
        failedReportCache.asMap().put(REPORTS_ERROR_KEY + reportType, stoppedPendingReports);

        if (EXECUTOR_SERVICE_REF.get() != null && !EXECUTOR_SERVICE_REF.get().isShutdown()) {
            EXECUTOR_SERVICE_REF.get().shutdownNow();
        }

        pendingReports.remove(uuid);
        ChunkingUtilities.removeAllReportParts(uuid);
        LOG.info("Report generation stopped due to an error. Solve the following error and start report again: {}", reason);
    }

    @Override
    public Report getLastErrorReport(String reportType) {
        LOG.info("Get the last error report of type: {}", reportType);
        Map<String, Report> errorReports = failedReportCache.asMap().get(REPORTS_ERROR_KEY + reportType);
        if (errorReports == null) {
            return null;
        }
        return errorReports.get(reportType);
    }

    @Override
    public String run(ReportConfigs reportConfig) throws OXException {
        LOG.info("Start creation of a report with the type: {}", reportConfig.getType());
        Map<String, Report> pendingReports = reportCache.asMap().get(PENDING_REPORTS_PRE_KEY + reportConfig.getType());
        if (pendingReports == null) {
            pendingReports = new HashMap<>();
        }

        if (!pendingReports.isEmpty()) {
            // Yes, there is a report running, so retrieve its UUID and return it.
            return pendingReports.keySet().iterator().next();
        }

        // No, we have to set up a  new report
        String uuid = UUIDs.getUnformattedString(UUID.randomUUID());
        LOG.debug("Report uuid is: {}", uuid);

        // Load all contextIds & Set up an AnalyzeContextBatch instance for every chunk of contextIds
        List<Integer> allContextIds = Services.getService(ContextService.class).getAllContextIds();

        // Set up the report instance
        Report report = new Report(uuid, System.currentTimeMillis(), reportConfig);
        report.setStorageFolderPath(ReportProperties.getStoragePath());
        report.setNumberOfTasks(allContextIds.size());
        pendingReports.put(uuid, report);
        reportCache.asMap().put(PENDING_REPORTS_PRE_KEY + reportConfig.getType(), pendingReports);

        // Abort report, if no contexts given
        if (allContextIds.isEmpty()) {
            abortGeneration(uuid, reportConfig.getType(), "No contexts to process.");
        }

        try {
            report.setOperatingSystemName(System.getProperty("os.name"));
        } catch (SecurityException e) {
            LOG.warn("Operating system name cannot be read for report: {}", e.getMessage());
        }
        try {
            report.setOperatingSystemVersion(System.getProperty("os.version"));
        } catch (SecurityException e) {
            LOG.warn("Operating system version cannot be read for report: {}", e.getMessage());
        }
        try {
            report.setJavaVersion(System.getProperty("java.version"));
        } catch (SecurityException e) {
            LOG.warn("Java version cannot be read for report: {}", e.getMessage());
        }
        try {
            report.setJavaVendor(System.getProperty("java.vendor"));
        } catch (SecurityException e) {
            LOG.warn("Java vendor cannot be read for report: {}", e.getMessage());
        }

        report.setDistribution(ReportInformation.getDistributionName());

        report.setDatabaseVersion(ReportInformation.getDatabaseVersion());

        List<String> installedPackages = ReportInformation.getInstalledPackages();
            for (String pkg : installedPackages) {
                report.addInstalledOXPackage(pkg);
            }

        List<ThirdPartyAPI> configuredAPIsOAuth = ReportInformation.getConfiguredThirdPartyAPIsViaOAuth();
            for (ThirdPartyAPI api : configuredAPIsOAuth) {
                report.addConfiguredThirdPartyAPIOAuth(api.getDisplayName());
            }

        List<ThirdPartyAPI> configuredAPIsOthers = ReportInformation.getConfiguredThirdPartyAPIsNotOAuth();
            for (ThirdPartyAPI api : configuredAPIsOthers) {
                report.addConfiguredThirdPartyAPIOthers(api.getDisplayName());
            }

        setUpContextAnalyzer(allContextIds, report);
        return uuid;
    }

    //---------------------------------------------- Private helper methods -------------------------------------------------------

    private void setUpContextAnalyzer(final List<Integer> allContextIds, final Report report) {
        LOG.info("Setup context analyzer to process all contexts for report with uuid: {}", report.getUUID());
        new Thread() {

            @Override
            public void run() {
                List<List<Integer>> contextsInSameSchema;
                try {
                    contextsInSameSchema = getContextsInSameSchemas(allContextIds);
                    processAllContexts(report, contextsInSameSchema);
                } catch (OXException e) {
                    LOG.error("Unable to strat multithreaded context processing, abort report generation ", e);
                    abortGeneration(report.getUUID(), report.getType(), "Unable to distribute context processing on multiple threads");
                }
            };
        }.start();
    }

    List<List<Integer>> getContextsInSameSchemas(List<Integer> allContextIds) throws OXException {
        List<List<Integer>> contextsInSchemas = new ArrayList<>();
        try {
            while (!allContextIds.isEmpty()) {
                List<Integer> currentSchemaIds;
                Integer firstContextId = allContextIds.get(0);

                currentSchemaIds = ContextLoader.getAllContextIdsInSameSchema(firstContextId.intValue());
                if (currentSchemaIds.isEmpty()) {
                    currentSchemaIds.add(firstContextId);
                }
                contextsInSchemas.add(currentSchemaIds);
                allContextIds.removeAll(currentSchemaIds);
            }
        } catch (OXException e) {
            if (UserExceptionCode.SQL_ERROR.equals(e)) {
                throw ReportExceptionCodes.UNABLE_TO_RETRIEVE_ALL_CONTEXT_IDS.create(e.getCause());
            }
            throw e;
        }
        return contextsInSchemas;
    }

    void processAllContexts(Report report, List<List<Integer>> contextsInSchemas) throws OXException {
        LOG.info("Start processing all contexts of all schemas. schemas in total: {} report uuid: {}", I(contextsInSchemas.size()), report.getUUID());
        ExecutorService reportSchemaThreadPool = Executors.newFixedThreadPool(ReportProperties.getMaxThreadPoolSize(), new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                int threadNum;
                while ((threadNum = threadNumber.incrementAndGet()) <= 0) {
                    if (threadNumber.compareAndSet(threadNum, 1)) {
                        threadNum = 1;
                    } else {
                        threadNum = threadNumber.incrementAndGet();
                    }
                }
                return new Thread(r, getThreadName(threadNum));
            }
        });

        CompletionService<Integer> schemaProcessor = new ExecutorCompletionService<>(reportSchemaThreadPool);

        EXECUTOR_SERVICE_REF.compareAndSet(null, reportSchemaThreadPool);
        if (EXECUTOR_SERVICE_REF.get().isShutdown()) {
            EXECUTOR_SERVICE_REF.set(reportSchemaThreadPool);
        }
        for (List<Integer> singleSchemaContexts : contextsInSchemas) {
            schemaProcessor.submit(new AnalyzeContextBatch(report.getUUID(), report, singleSchemaContexts));
        }
        for (int i = 0; i < contextsInSchemas.size(); i++) {
            try {
                Future<Integer> finishedContexts = schemaProcessor.take();
                Integer finishedAmount = finishedContexts.get();
                LOG.debug("Context processing finished for another schema and report: {} contexts processed: {}", report.getUUID(), finishedAmount);
                report.setTaskState(report.getNumberOfTasks(), report.getNumberOfPendingTasks() - finishedAmount.intValue());
                if (report.getNumberOfPendingTasks() <= 0) {
                    finishUpReport(report);
                }
            } catch (InterruptedException e) {
                throw ReportExceptionCodes.THREAD_WAS_INTERRUPTED.create(e);
            } catch (ExecutionException e) {
                throw ReportExceptionCodes.UABLE_TO_RETRIEVE_THREAD_RESULT.create(e);
            }
        }
    }

    static String getThreadName(int threadNumber) {
        return LocalReportService.class.getSimpleName() + "-" + threadNumber;
    }

    private void finishUpReport(Report report) throws OXException {
        LOG.info("Finish report with uuid: {}", report.getUUID());
        for (ReportSystemHandler handler : Services.getSystemHandlers()) {
            if (handler.appliesTo(report.getType())) {
                handler.runSystemReport(report);
            }
        }

        // And perform the finishing touches
        for (ReportFinishingTouches handler : Services.getFinishingTouches()) {
            if (handler.appliesTo(report.getType())) {
                handler.finish(report);
            }
        }

        // We are done. Dump Report
        report.setStopTime(System.currentTimeMillis());
        report.getNamespace("configs").put("com.openexchange.report.appsuite.ReportService", this.getClass().getSimpleName());

        Map<String, Report> finishedReports = reportCache.asMap().get(REPORTS_KEY);
        if (finishedReports == null) {
            finishedReports = new HashMap<>();
        }
        finishedReports.put(report.getType(), report);
        reportCache.asMap().put(REPORTS_KEY, finishedReports);

        // Clean up resources
        reportCache.asMap().get(PENDING_REPORTS_PRE_KEY + report.getType()).remove(report.getUUID());
    }
}
