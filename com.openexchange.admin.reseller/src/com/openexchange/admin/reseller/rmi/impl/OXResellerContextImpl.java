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

package com.openexchange.admin.reseller.rmi.impl;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.admin.plugin.hosting.storage.interfaces.OXContextStorageInterface;
import com.openexchange.admin.plugins.OXContextPluginInterfaceExtended;
import com.openexchange.admin.plugins.PluginException;
import com.openexchange.admin.reseller.daemons.ClientAdminThreadExtended;
import com.openexchange.admin.reseller.rmi.OXResellerTools;
import com.openexchange.admin.reseller.rmi.OXResellerTools.ClosureInterface;
import com.openexchange.admin.reseller.rmi.dataobjects.CustomField;
import com.openexchange.admin.reseller.rmi.dataobjects.ResellerAdmin;
import com.openexchange.admin.reseller.rmi.dataobjects.Restriction;
import com.openexchange.admin.reseller.rmi.exceptions.OXResellerException;
import com.openexchange.admin.reseller.rmi.exceptions.OXResellerException.Code;
import com.openexchange.admin.reseller.rmi.extensions.OXContextExtensionImpl;
import com.openexchange.admin.reseller.storage.interfaces.OXResellerStorageInterface;
import com.openexchange.admin.reseller.storage.mysqlStorage.ResellerContextFilter;
import com.openexchange.admin.reseller.storage.mysqlStorage.ResellerExtensionLoader;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Credentials;
import com.openexchange.admin.rmi.dataobjects.MaintenanceReason;
import com.openexchange.admin.rmi.dataobjects.User;
import com.openexchange.admin.rmi.dataobjects.UserModuleAccess;
import com.openexchange.admin.rmi.exceptions.InvalidDataException;
import com.openexchange.admin.rmi.exceptions.NoSuchObjectException;
import com.openexchange.admin.rmi.exceptions.StorageException;
import com.openexchange.admin.rmi.extensions.OXCommonExtension;
import com.openexchange.admin.storage.interfaces.OXToolStorageInterface;
import com.openexchange.admin.tools.AdminCache;
import com.openexchange.tools.pipesnfilters.Filter;

/**
 * @author <a href="mailto:carsten.hoeger@open-xchange.com">Carsten Hoeger</a>
 */
public class OXResellerContextImpl implements OXContextPluginInterfaceExtended {

    private static AdminCache cache = null;

    private OXResellerStorageInterface oxresell = null;

    /**
     * @throws StorageException
     */
    public OXResellerContextImpl() throws StorageException {
        cache = ClientAdminThreadExtended.cache;
        oxresell = OXResellerStorageInterface.getInstance();
    }

    @Override
    public void change(final Context ctx, final Credentials auth) throws PluginException {
        if (!cache.isMasterAdmin(auth, false)) {
            checkOwnerShipAndSetSid(ctx, auth);
        }
        final OXContextExtensionImpl firstExtensionByName = (OXContextExtensionImpl) ctx.getFirstExtensionByName(OXContextExtensionImpl.class.getName());
        if (null != firstExtensionByName) {
            final Restriction[] restrictions = firstExtensionByName.getRestriction();
            try {
                checkRestrictionsPerContext(OXResellerTools.array2HashSet(restrictions), this.oxresell);
            } catch (StorageException e) {
                throw new PluginException(e);
            } catch (InvalidDataException e) {
                throw new PluginException(e);
            } catch (OXResellerException e) {
                throw new PluginException(e.getMessage());
            }
        }
        applyRestrictionsPerContext(ctx);
        try {
            oxresell.writeCustomId(ctx);
            oxresell.updateModifyTimestamp(ctx);
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void changeModuleAccess(final Context ctx, final UserModuleAccess access, final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }

        checkOwnerShipAndSetSid(ctx, auth);

        try {
            oxresell.updateModifyTimestamp(ctx);
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void changeModuleAccess(final Context ctx, final String access_combination_name, final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }

        checkOwnerShipAndSetSid(ctx, auth);

        try {
            oxresell.updateModifyTimestamp(ctx);
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void changeCapabilities(Context ctx, Set<String> capsToAdd, Set<String> capsToRemove, Set<String> capsToDrop, Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }

        checkOwnerShipAndSetSid(ctx, auth);

        try {
            oxresell.updateModifyTimestamp(ctx);
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void changeQuota(Context ctx, String module, long quotaValue, Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }

        checkOwnerShipAndSetSid(ctx, auth);

        try {
            oxresell.updateModifyTimestamp(ctx);
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public Context postCreate(final Context ctx, final User admin_user, final UserModuleAccess access, final Credentials auth) throws PluginException {
        try {
            oxresell.generateCreateTimestamp(ctx);
        } catch (StorageException e1) {
            throw new PluginException(e1);
        }
        applyRestrictionsPerContext(ctx);
        if (cache.isMasterAdmin(auth, false)) {
            try {
                oxresell.writeCustomId(ctx);
            } catch (StorageException e) {
                throw new PluginException(e);
            }
            return ctx;
        }
        try {
            oxresell.checkPerSubadminRestrictions(
                auth,
                access,
                true,
                Restriction.MAX_CONTEXT_PER_SUBADMIN,
                Restriction.MAX_OVERALL_CONTEXT_QUOTA_PER_SUBADMIN,
                Restriction.MAX_OVERALL_USER_PER_SUBADMIN,
                Restriction.MAX_OVERALL_USER_PER_SUBADMIN_BY_MODULEACCESS_PREFIX
                );
            oxresell.writeCustomId(ctx);
            oxresell.ownContextToAdmin(ctx, auth);
        } catch (StorageException e) {
            try {
                // own context to subadmin; if we don't do that, the deletion
                // in the cleanup of postCreate will deny to delete
                oxresell.ownContextToAdmin(ctx, auth);
            } catch (StorageException e1) {
                throw new PluginException(e1);
            }
            throw new PluginException(e);
        }
        return ctx;
    }

    @Override
    public Context preCreate(final Context ctx, final User admin_user, final Credentials auth) throws PluginException {
        final OXContextExtensionImpl firstExtensionByName = (OXContextExtensionImpl) ctx.getFirstExtensionByName(OXContextExtensionImpl.class.getName());
        if (null != firstExtensionByName) {
            final Restriction[] restrictions = firstExtensionByName.getRestriction();
            try {
                checkRestrictionsPerContext(OXResellerTools.array2HashSet(restrictions), this.oxresell);
            } catch (StorageException e) {
                throw new PluginException(e);
            } catch (InvalidDataException e) {
                throw new PluginException(e);
            } catch (OXResellerException e) {
                throw new PluginException(e.getMessage());
            }
        }
        return ctx;
    }

    @Override
    public void delete(final Context ctx, final Credentials auth) throws PluginException {
        undoableDelete(ctx, auth);
    }

    @Override
    public Map<String, Object> undoableDelete(Context ctx, Credentials auth) throws PluginException {
        boolean ismasteradmin = cache.isMasterAdmin(auth, false);
        try {
            Map<String, Object> undoInfo = new HashMap<String, Object>(4);
            if (ismasteradmin) {
                Restriction[] restrictions = oxresell.applyRestrictionsToContext(null, ctx);
                CustomField[] customFields = oxresell.deleteCustomFields(ctx);
                undoInfo.put("reseller.restrictions", restrictions);
                undoInfo.put("reseller.customFields", customFields);
                final ResellerAdmin owner = oxresell.getContextOwner(ctx);
                if (0 == owner.getId().intValue()) {
                    // context does not belong to anybody, so it is save to be removed
                    return undoInfo;
                }
                // context belongs to somebody, so we must remove the ownership
                int sid = oxresell.unownContextFromAdmin(ctx, owner);
                undoInfo.put("reseller.subadmin", Integer.valueOf(sid));
            } else {
                if (!oxresell.checkOwnsContextAndSetSid(ctx, auth)) {
                    throw new PluginException("ContextID " + ctx.getId() + " does not belong to " + auth.getLogin());
                }
                Restriction[] restrictions = oxresell.applyRestrictionsToContext(null, ctx);
                CustomField[] customFields = oxresell.deleteCustomFields(ctx);
                int sid = oxresell.unownContextFromAdmin(ctx, auth);
                undoInfo.put("reseller.restrictions", restrictions);
                undoInfo.put("reseller.customFields", customFields);
                undoInfo.put("reseller.subadmin", Integer.valueOf(sid));
            }
            return undoInfo;
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void undelete(Context ctx, Map<String, Object> undoInfo) throws PluginException {
        try {
            Restriction[] restrictions = (Restriction[]) undoInfo.get("reseller.restrictions");
            CustomField[] customFields = (CustomField[]) undoInfo.get("reseller.customFields");
            Integer sid = (Integer) undoInfo.get("reseller.subadmin");
            oxresell.restore(ctx, null == sid ? 0 : sid.intValue(), restrictions, customFields);
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void disable(final Context ctx, final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }
        checkOwnerShipAndSetSid(ctx, auth);
    }

    @Override
    public void disableAll(final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }

        try {
            final MaintenanceReason reason = new MaintenanceReason(I(42));
            final OXContextStorageInterface oxctx = OXContextStorageInterface.getInstance();
            final ResellerAdmin adm = oxresell.getData(new ResellerAdmin[]{new ResellerAdmin(auth.getLogin())})[0];
            oxctx.disableAll(reason, "context2subadmin", "WHERE context2subadmin.cid=context.cid AND context2subadmin.sid=" + adm.getId());
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void downgrade(final Context ctx, final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }
        checkOwnerShipAndSetSid(ctx, auth);
    }

    @Override
    public void enable(final Context ctx, final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }
        checkOwnerShipAndSetSid(ctx, auth);
    }

    @Override
    public void enableAll(final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }
        try {
            final OXContextStorageInterface oxctx = OXContextStorageInterface.getInstance();
            final ResellerAdmin adm = oxresell.getData(new ResellerAdmin[]{new ResellerAdmin(auth.getLogin())})[0];
            oxctx.enableAll("context2subadmin", "WHERE context2subadmin.cid=context.cid AND context2subadmin.sid=" + adm.getId());
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    @Override
    public String getAccessCombinationName(final Context ctx, final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return null;
        }
        checkOwnerShipAndSetSid(ctx, auth);
        return null;
    }

    @Override
    public List<OXCommonExtension> getData(final List<Context> ctxs, final Credentials auth) throws PluginException {
        boolean masterAdmin = cache.isMasterAdmin(auth, false);
        List<OXCommonExtension> retval = new ArrayList<OXCommonExtension>(ctxs.size());
        for (final Context ctx : ctxs) {
            if (masterAdmin) {
                try {
                    final OXContextExtensionImpl ctxext = new OXContextExtensionImpl(oxresell.getContextOwner(ctx), oxresell.getRestrictionsFromContext(ctx));
                    ctxext.setCustomid(oxresell.getCustomId(ctx));
                    retval.add(ctxext);
                } catch (StorageException e) {
                    throw new PluginException(e);
                }
            } else {
                checkOwnerShipAndSetSid(ctx, auth);
                try {
                    final OXContextExtensionImpl contextExtension = (OXContextExtensionImpl) ctx.getFirstExtensionByName(OXContextExtensionImpl.class.getName());
                    final ResellerAdmin[] data = oxresell.getData(new ResellerAdmin[] { new ResellerAdmin(contextExtension.getSid()) });
                    contextExtension.setOwner(data[0]);
                    contextExtension.setRestriction(oxresell.getRestrictionsFromContext(ctx));
                    contextExtension.setCustomid(oxresell.getCustomId(ctx));
                    retval.add(contextExtension);
                    ctx.removeExtension(contextExtension);
                } catch (StorageException e) {
                    throw new PluginException(e);
                }
            }
        }
        return retval;
    }

    @Override
    public UserModuleAccess getModuleAccess(final Context ctx, final Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return null;
        }
        checkOwnerShipAndSetSid(ctx, auth);
        return null;
    }

    /**
     *
     * @see com.openexchange.admin.plugin.hosting.plugins.OXContextPluginInterface#list(java.lang.String,
     * com.openexchange.admin.plugin.hosting.rmi.dataobjects.Credentials)
     */
    @Override
    public Filter<Context, Context> list(final String search_pattern, final Credentials auth) throws PluginException {
        return new ResellerExtensionLoader(cache);
    }

    @Override
    public Filter<Integer, Integer> filter(final Credentials auth) throws PluginException {
        try {
            if (ClientAdminThreadExtended.cache.isMasterAdmin(auth, false) ) {
                return null;
            }

            ResellerAdmin adm = null;
            adm = (this.oxresell.getData(new ResellerAdmin[]{ new ResellerAdmin(auth.getLogin()) } ))[0];
            return new ResellerContextFilter(cache, adm);
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    private void applyRestrictionsPerContext(final Context ctx) throws PluginException {
        // Handle the extension...
        final OXContextExtensionImpl firstExtensionByName = (OXContextExtensionImpl) ctx.getFirstExtensionByName(OXContextExtensionImpl.class.getName());
        if (null != firstExtensionByName) {
            final Restriction[] restrictions = firstExtensionByName.getRestriction();
            try {
                this.oxresell.applyRestrictionsToContext(restrictions, ctx);
            } catch (StorageException e) {
                throw new PluginException(e);
            }
        }
    }

    /**
     * Check whether context is owned by owner specified in {@link Credentials}. Throw {@link PluginException} if not.
     *
     * @param ctx
     * @param auth
     * @throws PluginException
     */
    private void checkOwnerShipAndSetSid(final Context ctx, final Credentials auth) throws PluginException {
        try {
            if (!oxresell.checkOwnsContextAndSetSid(ctx, auth)) {
                throw new PluginException("ContextID " + ctx.getId() + " does not belong to " + auth.getLogin());
            }
        } catch (StorageException e) {
            throw new PluginException(e);
        }
    }

    /**
     * Check whether creator supplied any {@link Restriction} and check if those exist within the database. If, add the corresponding
     * Restriction id. Check whether Restrictions can be applied to context. If not, throw {@link InvalidDataException} or
     * {@link StorageException} if there are no Restrictions defined within the database. Check whether Restrictions contain duplicate
     * Restriction entries and throws {@link InvalidDataException} if that is the case.
     *
     * @param restrictions
     * @param storageInterface TODO
     * @throws StorageException
     * @throws InvalidDataException
     * @throws OXResellerException
     */
    private void checkRestrictionsPerContext(final HashSet<Restriction> restrictions, OXResellerStorageInterface storageInterface) throws StorageException, InvalidDataException, OXResellerException {
        final Map<String, Restriction> validRestrictions = storageInterface.listRestrictions("*");
        if (validRestrictions == null || validRestrictions.size() <= 0) {
            throw new OXResellerException(Code.UNABLE_TO_LOAD_AVAILABLE_RESTRICTIONS_FROM_DATABASE);
        }

        if (null != restrictions) {
            OXResellerTools.checkRestrictions(restrictions, validRestrictions, "context", new ClosureInterface() {
                @Override
                public boolean checkAgainstCorrespondingRestrictions(final String rname) {
                    return !(rname.equals(Restriction.MAX_USER_PER_CONTEXT) || rname.startsWith(Restriction.MAX_USER_PER_CONTEXT_BY_MODULEACCESS_PREFIX));
                }
            });
        }
    }

    @Override
    public Boolean checkMandatoryMembersContextCreate(Context ctx) throws PluginException {
        return new Boolean(true);
    }

    @Override
    public void exists(Context ctx, Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }
        if (null == ctx.getId()) {
            try {
                ctx.setId(Integer.valueOf(OXToolStorageInterface.getInstance().getContextIDByContextname(ctx.getName())));
            } catch (StorageException e) {
                throw new PluginException(e);
            } catch (NoSuchObjectException e) {
                return;
            }
        }
        checkOwnerShipAndSetSid(ctx, auth);
    }

    @Override
    public void existsInServer(Context ctx, Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }
        if (null == ctx.getId()) {
            try {
                ctx.setId(Integer.valueOf(OXToolStorageInterface.getInstance().getContextIDByContextname(ctx.getName())));
            } catch (StorageException e) {
                throw new PluginException(e);
            } catch (NoSuchObjectException e) {
                return;
            }
        }
        checkOwnerShipAndSetSid(ctx, auth);
    }

    @Override
    public void getAdminId(Context ctx, Credentials auth) throws PluginException {
        if (cache.isMasterAdmin(auth, false)) {
            return;
        }
        checkOwnerShipAndSetSid(ctx, auth);
    }
}
