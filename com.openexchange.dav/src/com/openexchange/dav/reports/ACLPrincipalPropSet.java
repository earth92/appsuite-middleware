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

package com.openexchange.dav.reports;

import static com.openexchange.java.Autoboxing.I;
import javax.servlet.http.HttpServletResponse;
import org.jdom2.Element;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.actions.PROPFINDAction;
import com.openexchange.dav.mixins.PrincipalURL;
import com.openexchange.dav.principals.groups.GroupPrincipalResource;
import com.openexchange.dav.principals.users.UserPrincipalResource;
import com.openexchange.dav.resources.DAVCollection;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.Permission;
import com.openexchange.group.Group;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.user.User;
import com.openexchange.user.UserService;
import com.openexchange.webdav.action.WebdavRequest;
import com.openexchange.webdav.action.WebdavResponse;
import com.openexchange.webdav.protocol.Protocol;
import com.openexchange.webdav.protocol.WebdavPath;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;
import com.openexchange.webdav.xml.resources.ResourceMarshaller;

/**
 * {@link ACLPrincipalPropSet}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class ACLPrincipalPropSet extends PROPFINDAction {

    public static final String NAMESPACE = Protocol.DEFAULT_NAMESPACE;
    public static final String NAME = "acl-principal-prop-set";

    /**
     * Initializes a new {@link ACLPrincipalPropSet}.
     *
     * @param protocol The underlying DAV protocol
     */
    public ACLPrincipalPropSet(DAVProtocol protocol) {
        super(protocol);
    }

    @Override
    public void perform(WebdavRequest request, WebdavResponse res) throws WebdavProtocolException {
        if (0 != request.getDepth(0) || false == request.getResource().isCollection()) {
            throw WebdavProtocolException.generalError(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
        Element multistatusElement = prepareMultistatusElement();
        /*
         * marshal resources based on folder permissions
         */
        ResourceMarshaller marshaller = getMarshaller(request, optRequestBody(request), new WebdavPath().toString());
        if (DAVCollection.class.isInstance(request.getCollection())) {
            DAVCollection collection = (DAVCollection) request.getCollection();
            Context context = collection.getFactory().getContext();
            for (Permission permission : collection.getPermissions()) {
                try {
                    WebdavResource resource;
                    if (permission.isGroup()) {
                        Group group = collection.getFactory().requireService(GroupService.class).getGroup(context, permission.getEntity());
                        WebdavPath url = new WebdavPath(PrincipalURL.forGroup(group.getIdentifier(), collection.getFactory().requireService(ConfigViewFactory.class)));
                        resource = new GroupPrincipalResource(collection.getFactory(), group, url);
                    } else {
                        User user = collection.getFactory().requireService(UserService.class).getUser(permission.getEntity(), context);
                        WebdavPath url = new WebdavPath(PrincipalURL.forUser(user.getId(), collection.getFactory().requireService(ConfigViewFactory.class)));
                        resource = new UserPrincipalResource(collection.getFactory(), user, url);
                    }
                    multistatusElement.addContent(marshaller.marshal(resource));
                } catch (OXException e) {
                    org.slf4j.LoggerFactory.getLogger(ACLPrincipalPropSet.class).warn("Error marshalling ACL resource for permission entity {}", I(permission.getEntity()), e);
                }
            }
            sendMultistatusResponse(res, multistatusElement);
        }
    }

}
