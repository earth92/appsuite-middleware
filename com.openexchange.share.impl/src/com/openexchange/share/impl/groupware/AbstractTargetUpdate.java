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

package com.openexchange.share.impl.groupware;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.openexchange.exception.OXException;
import com.openexchange.server.ServiceLookup;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.core.HandlerParameters;
import com.openexchange.share.core.ModuleHandler;
import com.openexchange.share.groupware.TargetProxy;
import com.openexchange.share.groupware.TargetUpdate;


/**
 * {@link AbstractTargetUpdate}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.8.0
 */
public abstract class AbstractTargetUpdate implements TargetUpdate {

    protected final ServiceLookup services;

    protected final ModuleExtensionRegistry<ModuleHandler> handlers;

    private Map<ShareTarget, TargetProxy> proxies;

    private Map<Integer, List<ShareTarget>> objectsByModule;

    private List<ShareTarget> folderTargets;


    protected AbstractTargetUpdate(ServiceLookup services, ModuleExtensionRegistry<ModuleHandler> handlers) {
        super();
        this.services = services;
        this.handlers = handlers;
        this.proxies = Collections.emptyMap();
    }

    @Override
    public void fetch(Collection<ShareTarget> targets) throws OXException {
        objectsByModule = new HashMap<Integer, List<ShareTarget>>();
        folderTargets = new LinkedList<ShareTarget>();
        for (ShareTarget target : targets) {
            if (target.isFolder()) {
                folderTargets.add(target);
            } else {
                int module = target.getModule();
                List<ShareTarget> targetList = objectsByModule.get(I(module));
                if (targetList == null) {
                    targetList = new LinkedList<ShareTarget>();
                    objectsByModule.put(I(module), targetList);
                }

                targetList.add(target);
            }
        }

        proxies = prepareProxies(folderTargets, objectsByModule);
    }

    @Override
    public TargetProxy get(ShareTarget target) {
        if (target == null) {
            return null;
        }
        return proxies.get(target);
    }

    @Override
    public void run() throws OXException {
        if (objectsByModule == null || folderTargets == null) {
            throw new IllegalStateException("fetch() must be called on TargetHandler before update!");
        }

        List<TargetProxy> foldersToUpdate = new LinkedList<TargetProxy>();
        List<TargetProxy> foldersToTouch = new LinkedList<TargetProxy>();
        for (ShareTarget target : folderTargets) {
            TargetProxy proxy = get(target);
            if (proxy.wasModified()) {
                foldersToUpdate.add(proxy);
            } else if (proxy.wasTouched()) {
                foldersToTouch.add(proxy);
            }
        }
        updateFolders(foldersToUpdate);
        if (0 < foldersToTouch.size()) {
            touchFolders(foldersToTouch);
        }

        for (Map.Entry<Integer, List<ShareTarget>> moduleEntry : objectsByModule.entrySet()) {
            List<ShareTarget> targets = moduleEntry.getValue();
            List<TargetProxy> modified = new ArrayList<TargetProxy>(targets.size());
            List<TargetProxy> touched = new ArrayList<TargetProxy>(targets.size());
            for (ShareTarget target : targets) {
                TargetProxy proxy = get(target);
                if (proxy.wasModified()) {
                    modified.add(proxy);
                } else if (proxy.wasTouched()) {
                    touched.add(proxy);
                }
            }

            int module = moduleEntry.getKey().intValue();
            if (!modified.isEmpty()) {
                ModuleHandler handler = handlers.get(module);
                handler.updateObjects(modified, getHandlerParameters());
            }
            if (!touched.isEmpty()) {
                ModuleHandler handler = handlers.get(module);
                handler.touchObjects(touched, getHandlerParameters());
            }
        }
    }

    @Override
    public void close() {
        // Nothing...
    }

    protected abstract Map<ShareTarget, TargetProxy> prepareProxies(List<ShareTarget> folderTargets, Map<Integer, List<ShareTarget>> objectsByModule) throws OXException;

    protected abstract void updateFolders(List<TargetProxy> proxies) throws OXException;

    protected abstract void touchFolders(List<TargetProxy> proxies) throws OXException;

    protected abstract HandlerParameters getHandlerParameters();

}
