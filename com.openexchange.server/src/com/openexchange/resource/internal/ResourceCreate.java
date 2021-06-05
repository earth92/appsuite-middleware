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

package com.openexchange.resource.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Dictionary;
import java.util.Hashtable;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.impl.IDGenerator;
import com.openexchange.groupware.userconfiguration.UserConfigurationStorage;
import com.openexchange.resource.Resource;
import com.openexchange.resource.ResourceEventConstants;
import com.openexchange.resource.ResourceExceptionCode;
import com.openexchange.server.impl.DBPool;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.user.User;

/**
 * {@link ResourceCreate} - Performs insertion of a {@link Resource resource}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ResourceCreate extends AbstractResourcePerformer {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ResourceCreate.class);

    private final User user;

    private final Context ctx;

    private final Resource resource;

    /**
     * Initializes a new {@link ResourceCreate}
     *
     * @param ctx The context
     * @param resource The resource to insert
     * @throws OXException
     */
    ResourceCreate(final User user, final Context ctx, final Resource resource) throws OXException {
        super();
        this.user = user;
        this.ctx = ctx;
        this.resource = resource;
    }

    /**
     * Performs the insert.
     * <ol>
     * <li>At first permission is checked</li>
     * <li>All necessary checks are performed: data completeness, data validation, and check for duplicate resources.</li>
     * <li>Then the transaction-bounded insert into storage takes place</li>
     * <li>At last, the insert is propagated to system (cache invalidation, etc.)</li>
     * </ol>
     *
     * @throws OXException If insert fails
     */
    void perform() throws OXException {
        allow();
        check();
        insert();
        propagate();
        sentEvent();
    }

    /**
     * Check permission
     *
     * @throws OXException If permission is not granted
     */
    private void allow() throws OXException {
        /*
         * At the moment security service is not used for timing reasons but is ought to be used later on
         */
        if (!UserConfigurationStorage.getInstance().getUserConfiguration(user.getId(), ctx).isEditResource()) {
            throw ResourceExceptionCode.PERMISSION.create(Integer.valueOf(ctx.getContextId()));
        }
        /*
         * TODO: Remove statements above and replace with commented call below
         */
        // checkBySecurityService();
    }

    /**
     * Check permission: Invoke {@link BundleAccessSecurityService#checkPermission(String[], String) checkPermission()} on
     * {@link BundleAccessSecurityService security service}
     *
     * @throws OXException If permission is not granted
     */
    // private void checkBySecurityService() throws OXException {
    // final BundleAccessSecurityService securityService = ServerServiceRegistry.getInstance().getService(
    // BundleAccessSecurityService.class);
    // if (securityService == null) {
    // throw new OXException(new OXException(OXException.Code.SERVICE_UNAVAILABLE,
    // BundleAccessSecurityService.class.getName()));
    // }
    // final Set<String> permissions = user.getAttributes().get("permission");
    // try {
    // securityService.checkPermission(permissions == null ? null : permissions.toArray(new String[permissions
    // .size()]), PATH);
    // } catch (BundleAccessException e) {
    // throw new OXException(e);
    // }
    // }
    /**
     * This method performs all necessary checks before creating a resource.
     *
     * @throws OXException if a problem was detected during checks.
     */
    private void check() throws OXException {
        if (null == resource) {
            throw ResourceExceptionCode.NULL.create();
        }
        /*
         * Check mandatory fields: identifier, displayName, and mail
         */
        if (com.openexchange.java.Strings.isEmpty(resource.getSimpleName())) {
            throw ResourceExceptionCode.MANDATORY_FIELD_NAME.create();
        } else if (com.openexchange.java.Strings.isEmpty(resource.getDisplayName())) {
            throw ResourceExceptionCode.MANDATORY_FIELD_DISPLAY_NAME.create();
        } else if (com.openexchange.java.Strings.isEmpty(resource.getMail())) {
            throw ResourceExceptionCode.MANDATORY_FIELD_MAIL.create();
        }
        /*
         * Check for invalid values
         */
        if (!ResourceTools.validateResourceIdentifier(resource.getSimpleName())) {
            throw ResourceExceptionCode.INVALID_RESOURCE_IDENTIFIER.create(resource.getSimpleName());
        }
        if (!ResourceTools.validateResourceEmail(resource.getMail())) {
            throw ResourceExceptionCode.INVALID_RESOURCE_MAIL.create(resource.getMail());
        }
        if (storage.searchResources(resource.getSimpleName(), ctx).length > 0) {
            throw ResourceExceptionCode.RESOURCE_CONFLICT.create(resource.getSimpleName());
        }
        if (storage.searchResourcesByMail(resource.getMail(), ctx).length > 0) {
            throw ResourceExceptionCode.RESOURCE_CONFLICT_MAIL.create(resource.getMail());
        }

    }

    /**
     * Inserts all data for the resource into the database.
     *
     * @throws OXException
     */
    private void insert() throws OXException {
        final Connection con = DBPool.pickupWriteable(ctx);
        try {
            con.setAutoCommit(false);
            insert(con);
            con.commit();
        } catch (SQLException e) {
            Databases.rollback(con);
            throw ResourceExceptionCode.SQL_ERROR.create(e);
        } finally {
            try {
                con.setAutoCommit(true);
            } catch (SQLException e) {
                LOG.error("Problem setting autocommit to true.", e);
            }
            DBPool.closeWriterSilent(ctx, con);
        }
    }

    /**
     * Propagates insertion to system: Possible cache invalidation, etc.
     */
    private void propagate() {
        // TODO: Check if any caches should be invalidated
    }

    /**
     * This method calls the plain insert methods.
     *
     * @param con writable database connection in transaction or not.
     * @throws OXException if some problem occurs.
     */
    void insert(final Connection con) throws OXException {
        try {
            final int id = IDGenerator.getId(ctx.getContextId(), Types.PRINCIPAL, con);
            resource.setIdentifier(id);
            storage.insertResource(ctx, con, resource);
        } catch (SQLException e) {
            throw ResourceExceptionCode.SQL_ERROR.create(e);
        }
    }

    private void sentEvent() {
        final EventAdmin eventAdmin = ServerServiceRegistry.getInstance().getService(EventAdmin.class);
        if (null != eventAdmin) {
            final Dictionary<String, Object> dict = new Hashtable<String, Object>(4);
            dict.put(ResourceEventConstants.PROPERTY_CONTEXT_ID, Integer.valueOf(ctx.getContextId()));
            dict.put(ResourceEventConstants.PROPERTY_USER_ID, Integer.valueOf(user.getId()));
            dict.put(ResourceEventConstants.PROPERTY_RESOURCE_ID, Integer.valueOf(resource.getIdentifier()));
            eventAdmin.postEvent(new Event(ResourceEventConstants.TOPIC_CREATE, dict));
        }
    }
}
