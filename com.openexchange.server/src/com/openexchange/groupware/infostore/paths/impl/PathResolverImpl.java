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

package com.openexchange.groupware.infostore.paths.impl;

import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.openexchange.cache.impl.FolderCacheManager;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.InfostoreFacade;
import com.openexchange.groupware.infostore.Resolved;
import com.openexchange.groupware.infostore.WebdavFolderAliases;
import com.openexchange.groupware.infostore.webdav.URLCache;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.webdav.protocol.WebdavPath;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

public class PathResolverImpl extends AbstractPathResolver implements URLCache {
    private Mode MODE;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PathResolverImpl.class);

    private final ThreadLocal<Map<WebdavPath,Resolved>> resolveCache = new ThreadLocal<Map<WebdavPath,Resolved>>();
    private final ThreadLocal<TIntObjectMap<WebdavPath>> docPathCache = new ThreadLocal<TIntObjectMap<WebdavPath>>();
    private final ThreadLocal<TIntObjectMap<WebdavPath>> folderPathCache = new ThreadLocal<TIntObjectMap<WebdavPath>>();

    private final InfostoreFacade database;
    private WebdavFolderAliases aliases;

    public PathResolverImpl(final DBProvider provider, final InfostoreFacade database) {
        setProvider(provider);
        this.database =database;
    }

    public PathResolverImpl(final InfostoreFacade database) {
        this.database = database;
    }

    @Override
    public void setProvider(final DBProvider provider) {
        super.setProvider(provider);
        MODE = new CACHE_MODE(provider);
    }

    @Override
    public WebdavPath getPathForDocument(final int relativeToFolder, final int documentId, ServerSession session) throws OXException {
        final TIntObjectMap<WebdavPath> cache = docPathCache.get();
        final Map<WebdavPath, Resolved> resCache = resolveCache.get();
        if (cache.containsKey(documentId)) {
            return relative(relativeToFolder, cache.get(documentId), session);
        }

        final DocumentMetadata dm = database.getDocumentMetadata(-1, documentId, InfostoreFacade.CURRENT_VERSION, session);
        if (dm.getFileName() == null || dm.getFileName().equals("")) {
            throw InfostoreExceptionCodes.DOCUMENT_CONTAINS_NO_FILE.create(Integer.valueOf(documentId));
        }
        final WebdavPath path = getPathForFolder(FolderObject.SYSTEM_ROOT_FOLDER_ID, (int)dm.getFolderId(),session).dup().append(dm.getFileName());

        cache.put(documentId, path);
        resCache.put(path, new ResolvedImpl(path, documentId, true));
        return relative(relativeToFolder,path, session);

    }

    @Override
    public WebdavPath getPathForFolder(final int relativeToFolder, final int folderId, ServerSession session) throws OXException {
        if (folderId == FolderObject.SYSTEM_INFOSTORE_FOLDER_ID) {
            return new WebdavPath();
        }
        if (folderId == relativeToFolder) {
            return new WebdavPath();
        }

        final Map<WebdavPath, Resolved> resCache = resolveCache.get();
        final TIntObjectMap<WebdavPath> cache = folderPathCache.get();
        if (cache.containsKey(folderId)) {
            return relative(relativeToFolder, cache.get(folderId), session);
        }

        final List<FolderObject> path = new ArrayList<FolderObject>();
        FolderObject folder = getFolder(folderId, session.getContext());
        path.add(folder);
        while (folder != null) {
            if (folder.getParentFolderID() == FolderObject.SYSTEM_ROOT_FOLDER_ID) {
                folder = null;
            } else {
                folder = getFolder(folder.getParentFolderID(), session.getContext());
                path.add(folder);
            }
        }


        final int length = path.size();
        final WebdavPath thePath = new WebdavPath();
        for(int i = length-1; i > -1; i--) {
            folder = path.get(i);
            String folderName = folder.getFolderName();
            if (aliases != null)  {
                final String alias = aliases.getAlias(folder.getObjectID());
                if (alias != null) {
                    folderName = alias;
                }
            }
            thePath.append(folderName);
            final WebdavPath current = thePath.dup();
            cache.put(folder.getObjectID(), current);
            resCache.put(current, new ResolvedImpl(current, folder.getObjectID(), false));
        }


        return relative(relativeToFolder, thePath, session);
    }

    @Override
    public Resolved resolve(final int relativeToFolder, final WebdavPath path, ServerSession session) throws OXException {

        final Map<WebdavPath, Resolved> cache = resolveCache.get();

        final WebdavPath absolutePath = absolute(relativeToFolder, path, session);

        if (cache.containsKey(absolutePath)) {
            return cache.get(absolutePath);
        }

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        final WebdavPath relpath = getPathForFolder(0, relativeToFolder, session);

        Resolved resolved = new ResolvedImpl(relpath,relativeToFolder, false);
        cache.put(resolved.getPath(), resolved);

        final WebdavPath current = new WebdavPath();
        try {
            int parentId = relativeToFolder;
            int compCount = 0;
            for(final String component : path) {
                compCount++;
                final boolean last = compCount == path.size();
                current.append(component);

                tryAlias(component, parentId, current, cache);
                resolved = cache.get(current);
                if (resolved != null) {
                    parentId = resolved.getId();
                }

                if (resolved == null) {
                    if (con == null) {
                        con = getReadConnection(session.getContext());
                    }
                    stmt = con.prepareStatement("SELECT folder.fuid, folder.fname, folder.type, folder.default_flag, folder.created_from FROM oxfolder_tree AS folder JOIN oxfolder_tree AS parent ON (folder.parent = parent.fuid AND folder.cid = parent.cid) WHERE folder.cid = ? and parent.fuid = ? and folder.fname = ?");
                    stmt.setInt(1, session.getContextId());

                    stmt.setInt(2, parentId);
                    stmt.setString(3, component);

                    rs = stmt.executeQuery();
                    boolean found = false;
                    int folderid = 0;
                    while (rs.next()) {
                        final String fname = rs.getString(2);
                        final int folderType = rs.getInt(3);
                        final boolean defaultFlag = rs.getInt(4) == 1;
                        final int createdFrom = rs.getInt(5);

                        if (folderType == FolderObject.TRASH && defaultFlag && createdFrom != session.getUserId()) {
                            //Do not consider special trash folder of other users in order to prevent ambiguous matching (DUPLICATE_SUBFOLDER).
                            continue;
                        }

                        if (fname.equals(component)) {
                            if ( found ) {
                                final OXException e = InfostoreExceptionCodes.DUPLICATE_SUBFOLDER.create(I(parentId), component, I(session.getContextId()));
                                LOG.warn(e.toString(), e);
                            }
                            folderid = rs.getInt(1);
                            found = true;
                        }
                    }
                    stmt.close();
                    if (!found) {
                        if (last) {
                            // Maybe infoitem?
                            stmt.close();
                            stmt = con.prepareStatement("SELECT info.id, doc.filename FROM infostore AS info JOIN infostore_document AS doc ON (info.cid = doc.cid AND info.id = doc.infostore_id AND doc.version_number = info.version) WHERE info.cid = ? AND info.folder_id = ? AND doc.filename = ?");
                            stmt.setInt(1, session.getContextId());
                            stmt.setInt(2, parentId);
                            stmt.setString(3, component);
                            rs.close();
                            rs = stmt.executeQuery();
                            found = false;
                            int id = 0;
                            while (rs.next()) {
                                final String name = rs.getString(2);
                                if (name.equals(component)) {
                                    if (found) {
                                        final OXException e = InfostoreExceptionCodes.DUPLICATE_SUBFOLDER.create(I(parentId), component, I(session.getContextId()));
                                        LOG.warn(e.toString(), e);
                                    }
                                    found = true;
                                    id = rs.getInt(1);
                                }
                            }
                            if (found) {
                                resolved = new ResolvedImpl(current,id, true);
                                cache.put(resolved.getPath(), resolved);
                                return resolved;
                            }
                        }
                        throw OXException.notFound("");
                    }
                    final int nextStep = folderid;
                    rs.close();
                    parentId = nextStep;
                    final Resolved res = new ResolvedImpl(current, parentId, false);
                    cache.put(res.getPath(), res);
                }
            }
            return new ResolvedImpl(current,parentId, false);
        } catch (SQLException x) {
            throw InfostoreExceptionCodes.SQL_PROBLEM.create(x, stmt != null ? stmt.toString() : "");
        } finally {
            close(stmt,rs);
            releaseReadConnection(session.getContext(),con);
        }
    }

    private void tryAlias(final String component, final int parentId, final WebdavPath current, final Map<WebdavPath, Resolved> cache) {
        if (aliases == null) {
            return;
        }
        final int aliasId = aliases.getId(component, parentId);
        if (WebdavFolderAliases.NOT_REGISTERED == aliasId) {
            return;
        }
        final Resolved res = new ResolvedImpl(current, aliasId, false);
        cache.put(res.getPath(), res);
    }

    @Override
    public void invalidate(final WebdavPath url, final int id , final Type type) {

        resolveCache.get().remove(url);
        switch (type) {
        case COLLECTION :
            folderPathCache.get().remove(id);break;
        case RESOURCE : docPathCache.get().remove(id); break;
        default : throw new IllegalArgumentException("Unknown Type "+type);
        }
    }


    @Override
    public void finish() throws OXException {
        clearCache();
        super.finish();
    }

    public void clearCache() {
        resolveCache.set(new HashMap<WebdavPath,Resolved>());
        docPathCache.set(new TIntObjectHashMap<WebdavPath>());
        folderPathCache.set(new TIntObjectHashMap<WebdavPath>());
    }

    @Override
    public void startTransaction() throws OXException {
        super.startTransaction();
        resolveCache.set(new HashMap<WebdavPath,Resolved>());
        docPathCache.set(new TIntObjectHashMap<WebdavPath>());
        folderPathCache.set(new TIntObjectHashMap<WebdavPath>());
    }

    /*@Override
    public void commit() throws TransactionException {
        super.commit();
    }*/

    /*@Override
    public void rollback() throws TransactionException {
        super.rollback();
    }*/

    private FolderObject getFolder(final int folderid, final Context ctx) throws OXException {
        return MODE.getFolder(folderid, ctx);
    }

    public void setAliases(final WebdavFolderAliases aliases) {
        this.aliases = aliases;
    }

    static interface Mode {
        public FolderObject getFolder(int folderid, Context ctx) throws OXException;
    }

    private final class CACHE_MODE implements Mode {

        private final DBProvider provider;

        public CACHE_MODE(final DBProvider provider) {
            this.provider = provider;
        }

        @Override
        public FolderObject getFolder(final int folderid, final Context ctx) throws OXException {
            try {
                Connection readCon = null;
                try {
                    readCon = provider.getReadConnection(ctx);
                    if (FolderCacheManager.isEnabled()) {
                        return FolderCacheManager.getInstance().getFolderObject(folderid, true, ctx, readCon);
                    }
                    return FolderObject.loadFolderObjectFromDB(folderid, ctx, readCon);
                } finally {
                    provider.releaseReadConnection(ctx, readCon);
                }
            } catch (OXException e) {
                MODE = new NORMAL_MODE();
                return MODE.getFolder(folderid, ctx);
            }
        }
    }

    private static final class NORMAL_MODE implements Mode {

        @Override
        public FolderObject getFolder(final int folderid, final Context ctx) throws OXException {
            return FolderObject.loadFolderObjectFromDB(folderid, ctx);
        }

    }
}
