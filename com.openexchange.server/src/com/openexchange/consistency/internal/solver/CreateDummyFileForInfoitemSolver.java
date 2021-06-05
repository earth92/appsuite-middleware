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

package com.openexchange.consistency.internal.solver;

import static com.openexchange.java.Autoboxing.I;
import java.util.Set;
import com.openexchange.consistency.Entity;
import com.openexchange.exception.OXException;
import com.openexchange.filestore.FileStorage;
import com.openexchange.filestore.FileStorages;
import com.openexchange.filestore.Info;
import com.openexchange.filestore.QuotaFileStorage;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.infostore.database.impl.DatabaseImpl;
import com.openexchange.user.User;

/**
 * {@link CreateDummyFileForInfostoreItemSolver}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.8.0
 */
public class CreateDummyFileForInfoitemSolver extends CreateDummyFileSolver implements ProblemSolver {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CreateDummyFileForInfoitemSolver.class);

    private final DatabaseImpl database;
    private final User admin;

    /**
     * Initializes a new {@link CreateDummyFileForInfoitemSolver}.
     *
     * @param database The database
     * @param storage
     * @param admin
     */
    public CreateDummyFileForInfoitemSolver(final DatabaseImpl database, final FileStorage storage, User admin) {
        super(storage);
        this.database = database;
        this.admin = admin;
    }

    @Override
    public void solve(final Entity entity, final Set<String> problems) {
        /*
         * Here we operate in two stages. First we create a dummy entry in the filestore. Second we update the Entries in the database
         */
        for (final String old_identifier : problems) {
            try {
                Context context = entity.getContext();
                int fsOwner = database.getDocumentHolderFor(old_identifier, context);

                if (fsOwner < 0) {
                    LOG.warn("No document holder found for identifier {} in context {}. Assigning to context admin.", old_identifier, I(context.getContextId()));
                    fsOwner = admin.getId();
                }

                QuotaFileStorage storage = FileStorages.getQuotaFileStorageService().getQuotaFileStorage(fsOwner, context.getContextId(), Info.drive());
                String identifier = createDummyFile(storage);
                database.startTransaction();
                int changed = database.modifyDocument(old_identifier, identifier, "\nCaution! The file has changed", "text/plain", context);
                database.commit();
                if (changed == 1) {
                    LOG.info("Modified entry for identifier {} in context {} to new dummy identifier {}", old_identifier, I(context.getContextId()), identifier);
                }
            } catch (OXException e) {
                LOG.error("{}", e.getMessage(), e);
                try {
                    database.rollback();
                    return;
                } catch (OXException e1) {
                    LOG.debug("{}", e1.getMessage(), e1);
                }
            } catch (RuntimeException e) {
                LOG.error("{}", e.getMessage(), e);
                try {
                    database.rollback();
                    return;
                } catch (OXException e1) {
                    LOG.debug("{}", e1.getMessage(), e1);
                }
            } finally {
                try {
                    database.finish();
                } catch (OXException e) {
                    LOG.debug("{}", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public String description() {
        return "Create dummy file for infoitem";
    }
}
