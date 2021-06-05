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

package com.openexchange.drive.events.internal;

import static com.openexchange.java.Autoboxing.L;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import com.openexchange.drive.DriveSession;
import com.openexchange.drive.events.DriveContentChange;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.IdAndName;
import com.openexchange.file.storage.composition.IDBasedFolderAccess;
import com.openexchange.file.storage.composition.IDBasedFolderAccessFactory;
import com.openexchange.java.ConcurrentHashSet;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;

/**
 * {@link FolderBuffer}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class FolderBuffer {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FolderBuffer.class);
    private static final String UNDEFINED_PUSH_TOKEN = "{undefined}";

    private final int consolidationTime;
    private final int maxDelayTime ;
    private final int defaultDelayTime;
    private final int contextID;
    private Set<String> folderIDs;
    private List<DriveContentChange> folderContentChanges;
    private boolean contentsChangedOnly;
    private String pushToken;
    private long lastEventTime;
    private long firstEventTime;

    /**
     * Initializes a new {@link FolderBuffer}.
     *
     * @param contextID The context ID
     * @param consolidationTime The consolidation time after which the buffer is considered to be ready to publish if no further
     *                          folders were added
     * @param maxDelayTime The maximum time after which the buffer is considered to be ready to publish, independently of the
     *                     consolidation interval
     * @param defaultDelayTime The (minimum) default delay time to wait after the first folder was added before being ready to publish
     */
    public FolderBuffer(int contextID, int consolidationTime, int maxDelayTime, int defaultDelayTime) {
        super();
        this.contextID = contextID;
        this.consolidationTime = consolidationTime;
        this.maxDelayTime = maxDelayTime;
        this.defaultDelayTime = defaultDelayTime;
        this.contentsChangedOnly = true;
    }

    /**
     * Gets a value indicating whether this buffer is ready to publish or not, based on the configured delay- and consolidation times.
     *
     * @return <code>true</code> if the event is due, <code>false</code>, otherwise
     */
    public synchronized boolean isReady() {
        if (null == folderIDs) {
            return false; // no event added yet
        }
        long now = System.currentTimeMillis();
        long timeSinceFirstEvent = now - firstEventTime;
        LOG.trace("isDue(): now={}, firstEventTime={}, lastEventTime={}, timeSinceFirstEvent={}, timeSinceLastEvent={}", L(now), L(firstEventTime), L(lastEventTime), L(timeSinceFirstEvent), L((now - lastEventTime)));
        if (timeSinceFirstEvent > maxDelayTime) {
            return true; // max delay time exceeded
        }
        if (timeSinceFirstEvent > defaultDelayTime && now - lastEventTime > consolidationTime) {
            return true; // consolidation time since last event passed, and default delay time exceeded
        }
        return false;
    }

    /**
     * Gets the context ID.
     *
     * @return The context ID
     */
    public int getContexctID() {
        return this.contextID;
    }

    /**
     * Gets the client push token if all events in this buffer were originating from the same client.
     *
     * @return The client push token, or <code>null</code> if not unique
     */
    public synchronized String getPushToken() {
        String pushToken = this.pushToken;
        return UNDEFINED_PUSH_TOKEN.equals(pushToken) ? null : pushToken;
    }

    public synchronized void add(Session session, String folderID, List<String> folderPath, boolean contentsChanged) {
        if (session.getContextId() != this.contextID) {
            throw new IllegalArgumentException("session not in this context");
        }
        /*
         * prepare access
         */
        lastEventTime = System.currentTimeMillis();
        Set<String> folderIDs = this.folderIDs;
        if (null == folderIDs) {
            firstEventTime = lastEventTime;
            folderIDs = new ConcurrentHashSet<String>();
            this.folderIDs = folderIDs;
        }
        /*
         * add folder and all parent folders, resolve to root if not already known
         */
        List<IdAndName> path2Root = null;
        if (folderIDs.add(folderID)) {
            if (null != folderPath) {
                folderIDs.addAll(folderPath);
            } else {
                path2Root = resolveToRoot(folderID, session);
                folderIDs.addAll(getIds(path2Root));
            }
        }
        /*
         * track separately if event denotes a change of a folder's contents
         */
        if (contentsChanged) {
            List<DriveContentChange> folderContentChanges = this.folderContentChanges;
            if (null == folderContentChanges) {
                folderContentChanges = new ArrayList<DriveContentChange>();
                this.folderContentChanges = folderContentChanges;
                folderContentChanges.add(new DriveContentChangeImpl(folderID, path2Root));
            } else if (false == folderContentChanges.stream().anyMatch(c -> folderID.equals(c.getFolderId()))) {
                if (null == path2Root) {
                    path2Root = resolveToRoot(folderID, session);
                }
                folderContentChanges.add(new DriveContentChangeImpl(folderID, path2Root));
            }
        } else {
            contentsChangedOnly = false;
        }
        /*
         * check for client push token
         */
        String pushToken = (String)session.getParameter(DriveSession.PARAMETER_PUSH_TOKEN);
        if (Strings.isNotEmpty(pushToken)) {
            if (null == this.pushToken) {
                this.pushToken = pushToken; // use push token from event
            } else if (false == this.pushToken.equals(pushToken)) {
                this.pushToken = UNDEFINED_PUSH_TOKEN; // different push token - reset to undefined
            }
        } else {
            this.pushToken = UNDEFINED_PUSH_TOKEN; // no push token - reset to undefined
        }
    }

    public synchronized Set<String> getFolderIDs() {
        return folderIDs;
    }

    public synchronized List<DriveContentChange> getFolderContentChanges() {
        return folderContentChanges;
    }

    public synchronized boolean isContentsChangedOnly() {
        return contentsChangedOnly;
    }

    private static List<String> getIds(List<IdAndName> idAndNames) {
        if (null == idAndNames || idAndNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<String>(idAndNames.size());
        for (IdAndName idAndName : idAndNames) {
            ids.add(idAndName.getId());
        }
        return ids;
    }

    private static List<IdAndName> resolveToRoot(String folderID, Session session) {
        List<IdAndName> idAndNames = new ArrayList<IdAndName>();
        try {
            IDBasedFolderAccess folderAccess = DriveEventServiceLookup.getService(IDBasedFolderAccessFactory.class, true).createAccess(session);
            FileStorageFolder[] path2DefaultFolder = folderAccess.getPath2DefaultFolder(folderID);
            for (FileStorageFolder folder : path2DefaultFolder) {
                idAndNames.add(new IdAndName(folder.getId(), folder.getName()));
            }
        } catch (OXException e) {
            LOG.debug("Error resolving path to rootfolder from event", e);
        }
        return idAndNames;
    }

}
