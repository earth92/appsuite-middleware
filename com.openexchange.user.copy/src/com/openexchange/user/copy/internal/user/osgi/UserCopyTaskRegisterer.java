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

package com.openexchange.user.copy.internal.user.osgi;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import com.openexchange.database.DatabaseService;
import com.openexchange.osgi.Tools;
import com.openexchange.user.UserService;
import com.openexchange.user.copy.CopyUserTaskService;
import com.openexchange.user.copy.internal.user.UserCopyTask;


/**
 * {@link UserCopyTaskRegisterer}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class UserCopyTaskRegisterer implements ServiceTrackerCustomizer<Object, Object> {

    private final BundleContext context;
    private final Lock lock = new ReentrantLock();
    private UserService userService;
    private DatabaseService databaseService;
    private ServiceRegistration<CopyUserTaskService> reg;

    /**
     * Initializes a new {@link UserCopyTaskRegisterer}.
     *
     * @param context The bundle context
     */
    public UserCopyTaskRegisterer(final BundleContext context) {
        super();
        this.context = context;
    }

    /**
     * Gets the associated filter expression
     *
     * @return The filter
     * @throws InvalidSyntaxException If filter cannot be generated
     */
    public Filter getFilter() throws InvalidSyntaxException {
        return Tools.generateServiceFilter(context, UserService.class, DatabaseService.class);
    }

    private boolean allAvailable() {
        return (null != userService && null != databaseService);
    }

    @Override
    public Object addingService(final ServiceReference<Object> reference) {
        Object service = context.getService(reference);
        lock.lock();
        try {
            if (UserService.class.isInstance(service)) {
                this.userService = (UserService) service;
            } else if (DatabaseService.class.isInstance(service)) {
                this.databaseService = (DatabaseService) service;
            } else {
                // Huh...?
                context.ungetService(reference);
                return null;
            }

            if (allAvailable()) {
                init();
            }
        } finally {
            lock.unlock();
        }
        return service;
    }

    @Override
    public void modifiedService(final ServiceReference<Object> reference, final Object service) {
        // Nothing
    }

    @Override
    public void removedService(final ServiceReference<Object> reference, final Object service) {
        boolean someServiceMissing = false;
        lock.lock();
        try {
            if (UserService.class.isInstance(service)) {
                if (this.userService != null) {
                    this.userService = null;
                    someServiceMissing = true;
                }
            } else if (DatabaseService.class.isInstance(service)) {
                if (this.databaseService != null) {
                    this.databaseService = null;
                    someServiceMissing = true;
                }
            }

            if (null != reg && someServiceMissing) {
                stop();
            }
        } finally {
            lock.unlock();
        }
        context.ungetService(reference);
    }

    private void init() {
        if (null != reg) {
            // Already registered
            return;
        }

        try {
            reg = context.registerService(CopyUserTaskService.class, new UserCopyTask(userService, databaseService), null);
        } catch (Exception e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserCopyTaskRegisterer.class);
            logger.warn("Failed start-up for {}", context.getBundle().getSymbolicName(), e);
        }

    }

    private void stop() {
        if (null == reg) {
            // Already unregistered
            return;
        }

        reg.unregister();
        reg = null;
    }

}
