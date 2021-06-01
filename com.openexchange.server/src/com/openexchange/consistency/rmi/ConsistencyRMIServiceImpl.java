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

package com.openexchange.consistency.rmi;

import static com.openexchange.java.Autoboxing.I;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.consistency.ConsistencyService;
import com.openexchange.consistency.Entity;
import com.openexchange.consistency.RepairAction;
import com.openexchange.consistency.RepairPolicy;
import com.openexchange.exception.OXException;
import com.openexchange.server.ServiceLookup;

/**
 * {@link ConsistencyRMIServiceImpl}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.1
 */
public class ConsistencyRMIServiceImpl implements ConsistencyRMIService {

    private static final Logger LOG = LoggerFactory.getLogger(ConsistencyRMIServiceImpl.class);
    private final ServiceLookup services;

    /**
     * Initialises a new {@link ConsistencyRMIServiceImpl}.
     */
    public ConsistencyRMIServiceImpl(ServiceLookup services) {
        super();
        this.services = services;
    }

    @Override
    public List<String> checkOrRepairConfigDB(boolean repair) throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: {} inconsistent configdb", repair ? "Repair" : "List");
            return service.checkOrRepairConfigDB(repair);
        });
    }

    @Override
    public List<String> listMissingFilesInContext(int contextId) throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: Listing missing files in context {}", I(contextId));
            return service.listMissingFilesInContext(contextId);
        });
    }

    @Override
    public Map<ConsistencyEntity, List<String>> listMissingFilesInFilestore(int filestoreId) throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: Listing missing files in filestore {}", I(filestoreId));
            return convertMap(service.listMissingFilesInFilestore(filestoreId));
        });
    }

    @Override
    public Map<ConsistencyEntity, List<String>> listMissingFilesInDatabase(int databaseId) throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: List missing files in database {}", I(databaseId));
            return convertMap(service.listMissingFilesInDatabase(databaseId));
        });
    }

    @Override
    public Map<ConsistencyEntity, List<String>> listAllMissingFiles() throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: List all missing files");
            return convertMap(service.listAllMissingFiles());
        });
    }

    @Override
    public List<String> listUnassignedFilesInContext(int contextId) throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: List all unassigned files in context {}", I(contextId));
            return service.listUnassignedFilesInContext(contextId);
        });
    }

    @Override
    public Map<ConsistencyEntity, List<String>> listUnassignedFilesInFilestore(int filestoreId) throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: List all unassigned files in filestore {}", I(filestoreId));
            return convertMap(service.listMissingFilesInFilestore(filestoreId));
        });
    }

    @Override
    public Map<ConsistencyEntity, List<String>> listUnassignedFilesInDatabase(int databaseId) throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: List all unassigned files in database {}", I(databaseId));
            return convertMap(service.listUnassignedFilesInDatabase(databaseId));
        });
    }

    @Override
    public Map<ConsistencyEntity, List<String>> listAllUnassignedFiles() throws RemoteException {
        return handle((service) -> {
            LOG.info("RMI invocation for: List all unassigned files");
            return convertMap(service.listAllUnassignedFiles());
        });
    }

    @Override
    public void repairFilesInContext(int contextId, String repairPolicy, String repairAction) throws RemoteException {
        handle((service) -> {
            LOG.info("RMI invocation for: Repair files in context {} with repair policy {} and repair action {}", I(contextId), repairPolicy, repairAction);
            service.repairFilesInContext(contextId, RepairPolicy.valueOf(repairPolicy.toUpperCase()), RepairAction.valueOf(repairAction.toUpperCase()));
            return null;
        });
    }

    @Override
    public void repairFilesInFilestore(int filestoreId, String repairPolicy, String repairAction) throws RemoteException {
        handle((service) -> {
            LOG.info("RMI invocation for: Repair files in filestore {} with repair policy {} and repair action {}", I(filestoreId), repairPolicy, repairAction);
            service.repairFilesInFilestore(filestoreId, RepairPolicy.valueOf(repairPolicy.toUpperCase()), RepairAction.valueOf(repairAction.toUpperCase()));
            return null;
        });
    }

    @Override
    public void repairFilesInDatabase(int databaseId, String repairPolicy, String repairAction) throws RemoteException {
        handle((service) -> {
            LOG.info("RMI invocation for: Repair files in database {} with repair policy {} and repair action {}", I(databaseId), repairPolicy, repairAction);
            service.repairFilesInDatabase(databaseId, RepairPolicy.valueOf(repairPolicy.toUpperCase()), RepairAction.valueOf(repairAction.toUpperCase()));
            return null;
        });
    }

    @Override
    public void repairAllFiles(String repairPolicy, String repairAction) throws RemoteException {
        handle((service) -> {
            LOG.info("RMI invocation for: Repair files with repair policy {} and repair action {}", repairPolicy, repairAction);
            service.repairAllFiles(RepairPolicy.valueOf(repairPolicy.toUpperCase()), RepairAction.valueOf(repairAction.toUpperCase()));
            return null;
        });
    }

    //////////////////////////////////////// HELPERS //////////////////////////////////////

    /**
     * Converts an Entity objects to {@link ConsistencyEntity} objects
     *
     * @param entity The entity object to convert
     * @return the {@link ConsistencyEntity}
     */
    private ConsistencyEntity toConsistencyEntity(Entity entity) {
        switch (entity.getType()) {
            case Context:
                return new ConsistencyEntity(entity.getContext().getContextId());
            case User:
                return new ConsistencyEntity(entity.getContext().getContextId(), entity.getUser().getId());
            default:
                throw new IllegalArgumentException("Unknown entity type: " + entity.getType());
        }
    }

    /**
     * Converts the keys of the specified {@link Map} from {@link Entity} to {@link ConsistencyEntity}
     *
     * @param entities The map to convert
     * @return The converted map
     */
    private Map<ConsistencyEntity, List<String>> convertMap(Map<Entity, List<String>> entities) {
        return entities.entrySet().stream().collect(Collectors.toMap(e -> toConsistencyEntity(e.getKey()), e -> e.getValue()));
    }

    /**
     * Applies the given {@link ConsistencyWorker} and handles errors
     *
     * @param w The worker
     * @return The output from the worker
     * @throws RemoteException If {@link OXException} happens
     */
    private <T> T handle(ConsistencyPerformer<T> performer) throws RemoteException {
        try {
            return performer.perform(services.getServiceSafe(ConsistencyService.class));
        } catch (OXException e) {
            LOG.error("", e);
            final Exception wrapMe = new Exception(e.getMessage());
            throw new RemoteException(e.getMessage(), wrapMe);
        } catch (RuntimeException e) {
            LOG.error("", e);
            throw e;
        }
    }

    /**
     *
     * {@link ConsistencyPerformer}
     *
     * @param <T> - The outcome of the operation
     */
    @FunctionalInterface
    private interface ConsistencyPerformer<T> {

        /**
         * Performs the denoted consistency operation
         *
         * @param service The {@link ConsistencyService}
         * @return The outcome of the operation
         * @throws OXException if an error is occurred
         */
        T perform(ConsistencyService service) throws OXException;
    }
}
