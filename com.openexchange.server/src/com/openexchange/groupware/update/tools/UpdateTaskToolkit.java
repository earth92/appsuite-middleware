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

package com.openexchange.groupware.update.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import com.google.common.collect.ImmutableSet;
import com.openexchange.database.Databases;
import com.openexchange.database.SchemaInfo;
import com.openexchange.databaseold.Database;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.NamespaceAwareUpdateTask;
import com.openexchange.groupware.update.SchemaStore;
import com.openexchange.groupware.update.SchemaUpdateState;
import com.openexchange.groupware.update.TaskInfo;
import com.openexchange.groupware.update.UpdateExceptionCodes;
import com.openexchange.groupware.update.UpdateTaskV2;
import com.openexchange.groupware.update.internal.DynamicSet;
import com.openexchange.groupware.update.internal.ExcludedSet;
import com.openexchange.groupware.update.internal.UpdateExecutor;
import com.openexchange.groupware.update.internal.UpdateProcess;
import com.openexchange.threadpool.ThreadPools;

/**
 * {@link UpdateTaskToolkit} - Toolkit for update tasks.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class UpdateTaskToolkit {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {

        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UpdateTaskToolkit.class);
    }

    /**
     * Initializes a new {@link UpdateTaskToolkit}.
     */
    private UpdateTaskToolkit() {
        super();
    }

    /**
     * Force (re-)run of update task denoted by given class name
     *
     * @param className The update task's class name
     * @param schemaName A valid schema name
     * @throws OXException If update task cannot be performed
     */
    public static void forceUpdateTask(final String className, final String schemaName) throws OXException {
        forceUpdateTask0(getUpdateTask(className), getInfoBySchemaName(schemaName));
    }

    /**
     * Force (re-)run of update task denoted by given class name
     *
     * @param className The update task's class name
     * @param contextId The context identifier
     * @throws OXException If update task cannot be performed
     */
    public static void forceUpdateTask(final String className, final int contextId) throws OXException {
        forceUpdateTask0(getUpdateTask(className), contextId);
    }

    /**
     * Force (re-)run of update task denoted by given class name. This method should only be called when holding <code>LOCK</code>.
     *
     * @param task The update task
     * @param contextId The context identifier
     * @throws OXException If update task cannot be performed
     */
    private static void forceUpdateTask0(final UpdateTaskV2 task, int contextId) throws OXException {
        final List<UpdateTaskV2> taskList = new ArrayList<UpdateTaskV2>(1);
        taskList.add(task);
        new UpdateExecutor(getSchema(contextId), contextId, taskList).execute();
    }

    /**
     * Force (re-)run of update task denoted by given class name. This method should only be called when holding <code>LOCK</code>.
     *
     * @param task The update task
     * @param contextId The context identifier
     * @throws OXException If update task cannot be performed
     */
    private static void forceUpdateTask0(final UpdateTaskV2 task, SchemaInfo schemaInfo) throws OXException {
        final List<UpdateTaskV2> taskList = new ArrayList<UpdateTaskV2>(1);
        taskList.add(task);
        new UpdateExecutor(getSchema(schemaInfo), taskList).execute();
    }

    /**
     * Force (re-)run of update task denoted by given class name on all schemas.
     *
     * @param className The update task's class name
     * @throws OXException If update task cannot be performed
     */
    public static void forceUpdateTaskOnAllSchemas(final String className) throws OXException {
        // Get update task by class name
        UpdateTaskV2 updateTask = getUpdateTask(className);

        // Get all available schemas
        Collection<SchemaInfo> schemas = getAllSchemas(Optional.empty());

        // ... and iterate them
        for (SchemaInfo schemaInfo : schemas) {
            forceUpdateTask0(updateTask, schemaInfo);
        }
    }

    /**
     * Runs the update process on all available schemas
     *
     * @param throwExceptionOnFailure Whether a possible exception is supposed to abort process
     * @return A status text reference
     * @throws OXException If update process fails
     */
    public static UpdateTaskToolkitJob<Void> runUpdateOnAllSchemas(final boolean throwExceptionOnFailure) throws OXException {
        // Get all available schemas
        final Collection<SchemaInfo> schemas = getAllSchemas(Optional.empty());
        final int total = schemas.size();

        // Status text
        final AtomicReference<String> statusText = new AtomicReference<String>("Attempting to update " + total + " schemas in total...");

        // Task...
        Callable<Void> task = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                // Iterate schemas
                int count = 0;
                StringBuilder sb = new StringBuilder(32);
                Map<String, Queue<TaskInfo>> totalFailures = new HashMap<String, Queue<TaskInfo>>(32);
                for (SchemaInfo schema : schemas) {
                    UpdateProcess updateProcess = new UpdateProcess(schema.getPoolId(), schema.getSchema(), true, throwExceptionOnFailure);
                    if (throwExceptionOnFailure) {
                        try {
                            updateProcess.runUpdate();
                        } catch (OXException e) {
                            LoggerHolder.LOG.error("", e);
                            statusText.set(e.getPlainLogMessage());
                            throw e;
                        } catch (Exception e) {
                            LoggerHolder.LOG.error("", e);
                            statusText.set(e.getMessage());
                            throw e;
                        }
                    } else {
                        updateProcess.run();

                        // Check possible failures
                        Queue<TaskInfo> failures = updateProcess.getFailures();
                        if (null != failures && !failures.isEmpty()) {
                            for (TaskInfo taskInfo : failures) {
                                Queue<TaskInfo> schemaFailures = totalFailures.get(taskInfo.getSchema());
                                if (null == schemaFailures) {
                                    schemaFailures = new LinkedList<TaskInfo>();
                                    totalFailures.put(taskInfo.getSchema(), schemaFailures);
                                }
                                schemaFailures.offer(taskInfo);
                            }
                        }
                    }

                    count++;
                    if (count < total) {
                        sb.setLength(0);
                        sb.append("Processed ").append(count).append(" of ").append(total).append(" schemas.");
                        statusText.set(sb.toString());
                    }
                }

                // Completed...
                sb.setLength(0);
                sb.append("Processed ").append(total).append(" of ").append(total).append(" schemas.");

                // Append failure information (if any)
                if (!totalFailures.isEmpty()) {
                    sb.append("\\R\\R");
                    boolean first = true;
                    for (Map.Entry<String, Queue<TaskInfo>> failureEntry : totalFailures.entrySet()) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append("\\R\\R");
                        }
                        sb.append("The following update task(s) failed on schema \"").append(failureEntry.getKey()).append("\": \\R");
                        boolean firstTaskInfo = true;
                        for (TaskInfo taskInfo : failureEntry.getValue()) {
                            if (firstTaskInfo) {
                                firstTaskInfo = false;
                            } else {
                                sb.append("\\R");
                            }
                            sb.append(' ').append(taskInfo.getTaskName()).append(" (schema=").append(taskInfo.getSchema()).append(')');
                        }
                    }
                }

                // Set new status text
                statusText.set(sb.toString());
                return null;
            }
        };

        // Submit & return
        UpdateTaskToolkitJob<Void> job = new UpdateTaskToolkitJob<>(task, statusText);
        ThreadPools.getThreadPool().submit(ThreadPools.task(job, "RunAllUpdate"));
        return job;
    }

    /**
     * Returns an unmodifiable {@link Map} with all {@link NamespaceAwareUpdateTask}s
     *
     * @return an unmodifiable {@link Map} with all {@link NamespaceAwareUpdateTask}s
     */
    public static Map<String, Set<String>> getNamespaceAwareUpdateTasks() {
        Map<String, Set<String>> namespaceAware = new HashMap<>();

        for (UpdateTaskV2 updateTask : DynamicSet.getInstance().getTaskSet()) {
            NamespaceAwareUpdateTask annotation = updateTask.getClass().getAnnotation(NamespaceAwareUpdateTask.class);
            if (annotation == null) {
                continue;
            }
            Set<String> set = namespaceAware.get(annotation.namespace());
            if (set == null) {
                set = new HashSet<>();
            }
            set.add(updateTask.getClass().getName());
            namespaceAware.put(annotation.namespace(), set);
        }
        return Collections.unmodifiableMap(namespaceAware);
    }

    /**
     * Returns an unmodifiable {@link Set} with all registered update tasks
     *
     * @return an unmodifiable {@link Set} with all registered update tasks
     */
    public static Set<String> getRegisteredUpdateTasks() {
        Set<UpdateTaskV2> tasks = DynamicSet.getInstance().getTaskSet();
        ImmutableSet.Builder<String> registered = ImmutableSet.builderWithExpectedSize(tasks.size());
        for (UpdateTaskV2 updateTask : tasks) {
            registered.add(updateTask.getClass().getName());
        }
        return registered.build();
    }

    /**
     * Returns an unmodifiable {@link Set} with all excluded update tasks
     *
     * @return an unmodifiable {@link Set} with all excluded update tasks
     */
    public static Set<String> getExcludedUpdateTasks() {
        return Collections.unmodifiableSet(ExcludedSet.getInstance().getTaskSet());
    }

    /**
     * Gets all available schemas.
     *
     * @param optSchema The optional schema name to query; if absent all schemas are queried
     * @return A list containing schemas.
     * @throws OXException If an error occurs
     */
    private static Collection<SchemaInfo> getAllSchemas(Optional<String> optSchema) throws OXException {
        // Determine all DB schemas that are currently in use
        Connection con = Database.get(false);
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            // Grab database schemas
            if (optSchema.isPresent()) {
                stmt = con.prepareStatement("SELECT db_pool_id, schemaname FROM contexts_per_dbschema WHERE schemaname=?");
                stmt.setString(1, optSchema.get());
            } else {
                stmt = con.prepareStatement("SELECT db_pool_id, schemaname FROM contexts_per_dbschema");
            }
            rs = stmt.executeQuery();
            if (false == rs.next()) {
                // No database schema in use
                return Collections.emptyList();
            }

            Set<SchemaInfo> l = new LinkedHashSet<>();
            do {
                l.add(SchemaInfo.valueOf(rs.getInt(1), rs.getString(2)));
            } while (rs.next());
            return l;
        } catch (SQLException e) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw UpdateExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
            Database.back(false, con);
        }
    }

    /**
     * Gets the schema information (pool and name) for specified schema name
     *
     * @param schemaName The schema name to look-up
     * @return The schema information
     * @throws OXException If schema information cannot be returned (none or multiple schemas found)
     */
    public static SchemaInfo getInfoBySchemaName(String schemaName) throws OXException {
        Collection<SchemaInfo> schemas = getAllSchemas(Optional.of(schemaName));

        int size = schemas.size();
        if (size == 1) {
            return schemas.iterator().next();
        }

        throw (size == 0 ? UpdateExceptionCodes.UNKNOWN_SCHEMA.create(schemaName) : UpdateExceptionCodes.FOUND_MULTIPLE_SCHEMAS.create(schemaName, schemas));
    }

    /**
     * Load update task by class name.
     *
     * @param className name of the update task class.
     * @return the update task class.
     * @throws OXException if the update task class can not be determined.
     */
    private static UpdateTaskV2 getUpdateTask(final String className) throws OXException {
        Set<UpdateTaskV2> taskList = DynamicSet.getInstance().getTaskSet();
        for (UpdateTaskV2 task : taskList) {
            if (task.getClass().getName().equals(className)) {
                return task;
            }
        }
        throw UpdateExceptionCodes.UNKNOWN_TASK.create(className);
    }

    /*
     * ++++++++++++++++++++++++++++ ++ + HELPER METHODS + ++ ++++++++++++++++++++++++++++
     */

    private static SchemaUpdateState getSchema(final int contextId) throws OXException {
        return SchemaStore.getInstance().getSchema(contextId);
    }

    private static SchemaUpdateState getSchema(final SchemaInfo schemaInfo) throws OXException {
        return SchemaStore.getInstance().getSchema(schemaInfo.getPoolId(), schemaInfo.getSchema());
    }

}
