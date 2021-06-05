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

import static com.openexchange.dav.DAVProtocol.CALENDARSERVER_NS;
import static com.openexchange.webdav.protocol.Protocol.DAV_NS;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.ResourceId;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.contact.ContactFieldOperand;
import com.openexchange.contact.ContactService;
import com.openexchange.contact.SortOptions;
import com.openexchange.dav.DAVFactory;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.actions.PROPFINDAction;
import com.openexchange.dav.mixins.PrincipalURL;
import com.openexchange.dav.principals.groups.GroupPrincipalCollection;
import com.openexchange.dav.principals.groups.GroupPrincipalResource;
import com.openexchange.dav.principals.resources.ResourcePrincipalCollection;
import com.openexchange.dav.principals.resources.ResourcePrincipalResource;
import com.openexchange.dav.principals.users.UserPrincipalCollection;
import com.openexchange.dav.principals.users.UserPrincipalResource;
import com.openexchange.exception.OXException;
import com.openexchange.group.Group;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.Contact;
import com.openexchange.java.Strings;
import com.openexchange.resource.Resource;
import com.openexchange.resource.ResourceService;
import com.openexchange.search.CompositeSearchTerm;
import com.openexchange.search.CompositeSearchTerm.CompositeOperation;
import com.openexchange.search.SingleSearchTerm;
import com.openexchange.search.SingleSearchTerm.SingleOperation;
import com.openexchange.search.internal.operands.ConstantOperand;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.user.User;
import com.openexchange.user.UserService;
import com.openexchange.webdav.action.WebdavRequest;
import com.openexchange.webdav.action.WebdavResponse;
import com.openexchange.webdav.protocol.WebdavPath;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.xml.resources.ResourceMarshaller;

/**
 * {@link PrinicpalPropertySearchReport}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class PrinicpalPropertySearchReport extends PROPFINDAction {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PrinicpalPropertySearchReport.class);

    public static final String NAMESPACE = DAV_NS.getURI();
    public static final String NAME = "principal-property-search";

    /**
     * Pattern to check for valid e-mail addresses (needed for the Max OS client)
     */
    private static final Pattern RFC_2822_SIMPLIFIED_PATTERN = Pattern.compile("[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Initializes a new {@link PrinicpalPropertySearchReport}.
     *
     * @param protocol The principal protocol
     */
    public PrinicpalPropertySearchReport(DAVProtocol protocol) {
        super(protocol);
    }

    @Override
    public void perform(WebdavRequest request, WebdavResponse response) throws WebdavProtocolException {
        /*
         * get request body
         */
        Document requestBody = optRequestBody(request);
        if (null == requestBody) {
            throw WebdavProtocolException.generalError(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
        Element applyToPrincipalCollectionSet = requestBody.getRootElement().getChild("apply-to-principal-collection-set", DAV_NS);
        List<Element> propertySearches = requestBody.getRootElement().getChildren("property-search", DAV_NS);
        DAVFactory factory = (DAVFactory) request.getFactory();
        /*
         * search matching users
         */
        Set<User> users = new HashSet<User>();
        if (applyData(applyToPrincipalCollectionSet, UserPrincipalCollection.NAME, request)) {
            /*
             * prepare composite search term
             */
            CompositeSearchTerm orTerm = new CompositeSearchTerm(CompositeOperation.OR);
            for (Element propertySearch : propertySearches) {
                Element matchElement = propertySearch.getChild("match", DAV_NS);
                if (null == matchElement || Strings.isEmpty(matchElement.getText())) {
                    continue;
                }
                String pattern = getPattern(matchElement);
                Element prop = propertySearch.getChild("prop", DAV_NS);
                if (null != prop) {
                    for (Element element : prop.getChildren()) {
                        /*
                         * create a corresponding search term for each supported property
                         */
                        if ("displayname".equals(element.getName()) && DAV_NS.equals(element.getNamespace())) {
                            SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
                            term.addOperand(new ContactFieldOperand(ContactField.DISPLAY_NAME));
                            term.addOperand(new ConstantOperand<String>(pattern));
                            orTerm.addSearchTerm(term);
                        } else if ("first-name".equals(element.getName()) && DAVProtocol.CALENDARSERVER_NS.equals(element.getNamespace())) {
                            SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
                            term.addOperand(new ContactFieldOperand(ContactField.GIVEN_NAME));
                            term.addOperand(new ConstantOperand<String>(pattern));
                            orTerm.addSearchTerm(term);
                        } else if ("last-name".equals(element.getName()) && DAVProtocol.CALENDARSERVER_NS.equals(element.getNamespace())) {
                            SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
                            term.addOperand(new ContactFieldOperand(ContactField.SUR_NAME));
                            term.addOperand(new ConstantOperand<String>(pattern));
                            orTerm.addSearchTerm(term);
                        } else if ("email-address-set".equals(element.getName()) && DAVProtocol.CALENDARSERVER_NS.equals(element.getNamespace())) {
                            CompositeSearchTerm emailTerm = new CompositeSearchTerm(CompositeOperation.OR);
                            for (ContactField emailField : new ContactField[] { ContactField.EMAIL1, ContactField.EMAIL2, ContactField.EMAIL3 }) {
                                SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
                                term.addOperand(new ContactFieldOperand(emailField));
                                term.addOperand(new ConstantOperand<String>(pattern));
                                emailTerm.addSearchTerm(term);
                            }
                            orTerm.addSearchTerm(emailTerm);
                        } else if ("calendar-user-address-set".equals(element.getName()) && DAVProtocol.CAL_NS.equals(element.getNamespace())) {
                            int userID = extractPrincipalID(pattern, CalendarUserType.INDIVIDUAL, factory);
                            if (-1 != userID) {
                                SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
                                term.addOperand(new ContactFieldOperand(ContactField.INTERNAL_USERID));
                                term.addOperand(new ConstantOperand<Integer>(Integer.valueOf(userID)));
                                orTerm.addSearchTerm(term);
                            } else if (pattern.startsWith("mailto:")) {
                                SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
                                term.addOperand(new ContactFieldOperand(ContactField.EMAIL1));
                                term.addOperand(new ConstantOperand<String>(pattern.substring(7)));
                                orTerm.addSearchTerm(term);
                            }
                        }
                    }
                }
            }
            /*
             * perform search
             */
            if (null != orTerm.getOperands() && 0 < orTerm.getOperands().length) {
                SearchIterator<Contact> searchIterator = null;
                try {
                    searchIterator = factory.requireService(ContactService.class).searchUsers(factory.getSessionObject(), orTerm, new ContactField[] { ContactField.INTERNAL_USERID, ContactField.CONTEXTID }, SortOptions.EMPTY);
                    while (searchIterator.hasNext()) {
                        try {
                            Contact contact = searchIterator.next();
                            users.add(factory.requireService(UserService.class).getUser(contact.getInternalUserId(), contact.getContextId()));
                        } catch (OXException e) {
                            LOG.warn("error resolving user", e);
                        }
                    }
                } catch (OXException e) {
                    LOG.warn("error searching users", e);
                } finally {
                    SearchIterators.close(searchIterator);
                }
            }
        }
        /*
         * search matching groups
         */
        Set<Group> groups = new HashSet<Group>();
        if (applyData(applyToPrincipalCollectionSet, GroupPrincipalCollection.NAME, request)) {
            for (Element propertySearch : propertySearches) {
                Element matchElement = propertySearch.getChild("match", DAV_NS);
                if (null == matchElement || Strings.isEmpty(matchElement.getText())) {
                    continue;
                }
                String pattern = getPattern(matchElement);
                Element prop = propertySearch.getChild("prop", DAV_NS);
                if (null != prop) {
                    for (Element element : prop.getChildren()) {
                        /*
                         * search by displayname only
                         */
                        if ("displayname".equals(element.getName()) && DAV_NS.equals(element.getNamespace())) {
                            try {
                                Group[] foundGroups = factory.getServiceSafe(GroupService.class).searchGroups(factory.getSession(), pattern, false);
                                if (null != foundGroups) {
                                    groups.addAll(Arrays.asList(foundGroups));
                                }
                            } catch (OXException e) {
                                LOG.warn("error searching groups", e);
                            }
                        } else if ("calendar-user-address-set".equals(element.getName()) && DAVProtocol.CAL_NS.equals(element.getNamespace())) {
                            int groupID = extractPrincipalID(pattern, CalendarUserType.GROUP, factory);
                            if (-1 != groupID) {
                                try {
                                    Group group = factory.getServiceSafe(GroupService.class).getGroup(factory.getContext(), groupID);
                                    if (null != group) {
                                        groups.add(group);
                                    }
                                } catch (OXException e) {
                                    LOG.warn("error searching groups", e);
                                }
                            }
                        }
                    }
                }
            }
        }
        /*
         * search matching resources
         */
        Set<Resource> resources = new HashSet<Resource>();
        if (applyData(applyToPrincipalCollectionSet, ResourcePrincipalCollection.NAME, request)) {
            for (Element propertySearch : propertySearches) {
                Element matchElement = propertySearch.getChild("match", DAV_NS);
                if (null == matchElement || Strings.isEmpty(matchElement.getText())) {
                    continue;
                }
                String pattern = getPattern(matchElement);
                Element prop = propertySearch.getChild("prop", DAV_NS);
                if (prop == null) {
                    continue;
                }
                for (Element element : prop.getChildren()) {
                    /*
                     * search by displayname or mail address
                     */
                    if ("displayname".equals(element.getName()) && DAV_NS.equals(element.getNamespace())) {
                        try {
                            Resource[] foundResources = factory.requireService(ResourceService.class).searchResources(pattern, factory.getContext());
                            if (null != foundResources && 0 < foundResources.length) {
                                resources.addAll(Arrays.asList(foundResources));
                            }
                        } catch (OXException e) {
                            LOG.warn("error searching resources", e);
                        }
                    } else if ("email-address-set".equals(element.getName()) && CALENDARSERVER_NS.equals(element.getNamespace())) {
                        try {
                            Resource[] foundResources = factory.requireService(ResourceService.class).searchResourcesByMail(pattern, factory.getContext());
                            if (null != foundResources && 0 < foundResources.length) {
                                resources.addAll(Arrays.asList(foundResources));
                            }
                        } catch (OXException e) {
                            LOG.warn("error searching resources", e);
                        }
                    } else if ("calendar-user-address-set".equals(element.getName()) && DAVProtocol.CAL_NS.equals(element.getNamespace())) {
                        int resourceID = extractPrincipalID(pattern, CalendarUserType.RESOURCE, factory);
                        if (-1 != resourceID) {
                            try {
                                Resource resource = factory.requireService(ResourceService.class).getResource(resourceID, factory.getContext());
                                if (null != resource) {
                                    resources.add(resource);
                                }
                            } catch (OXException e) {
                                LOG.warn("error searching resources", e);
                            }
                        } else {
                            try {
                                Resource[] foundResources = factory.requireService(ResourceService.class).searchResourcesByMail(pattern, factory.getContext());
                                if (null != foundResources && 0 < foundResources.length) {
                                    resources.addAll(Arrays.asList(foundResources));
                                }
                            } catch (OXException e) {
                                LOG.warn("error searching resources", e);
                            }
                        }
                    }
                }
            }
        }
        /*
         * marshal response
         */
        ConfigViewFactory configViewFactory;
        try {
            configViewFactory = factory.requireService(ConfigViewFactory.class);
        } catch (OXException e) {
            throw DAVProtocol.protocolException(request.getUrl(), e);
        }
        Element multistatusElement = prepareMultistatusElement();
        ResourceMarshaller marshaller = getMarshaller(request, requestBody, new WebdavPath().toString());
        for (User user : users) {
            if (Strings.isEmpty(user.getMail()) || false == RFC_2822_SIMPLIFIED_PATTERN.matcher(user.getMail()).matches()) {
                // skip, since the Mac OS client gets into trouble when the TLD is missing in the mail address
                continue;
            }
            WebdavPath url = new WebdavPath(PrincipalURL.forUser(user.getId(), configViewFactory));
            multistatusElement.addContent(marshaller.marshal(new UserPrincipalResource(factory, user, url)));
        }
        for (Group group : groups) {
            WebdavPath url = new WebdavPath(PrincipalURL.forGroup(group.getIdentifier(), configViewFactory));
            multistatusElement.addContent(marshaller.marshal(new GroupPrincipalResource(factory, group, url)));
        }
        for (Resource resource : resources) {
            WebdavPath url = new WebdavPath(PrincipalURL.forResource(resource.getIdentifier(), configViewFactory));
            multistatusElement.addContent(marshaller.marshal(new ResourcePrincipalResource(factory, resource, url)));
        }
        sendMultistatusResponse(response, multistatusElement);
    }

    private boolean applyData(Element applyToPrincipalCollectionSet, String collectionName, WebdavRequest request) {
        return null != applyToPrincipalCollectionSet || collectionName.equals(request.getUrl().name()) || 0 == request.getUrl().size();
    }

    /**
     * Gets the search pattern indicated by the supplied <code>match</code> element.
     *
     * @param matchElement The match element to extract the pattern from
     * @return The search pattern
     */
    private static String getPattern(Element matchElement) {
        String match = matchElement.getText();
        Attribute matchTypeAttribute = matchElement.getAttribute("match-type");
        if (null == matchTypeAttribute || Strings.isEmpty(matchTypeAttribute.getValue())) {
            return match + '*'; // default to "starts-with"
        }
        switch (matchTypeAttribute.getValue()) {
            case "equals":
                return match;
            case "contains":
                return '*' + match + '*';
            case "ends-with":
                return '*' + match;
            default:
                return match + '*';
        }
    }

    /**
     * Tries to extract the targeted principal identifier directly from the supplied pattern as used in a
     * <code>calendar-user-address-set</code> property search.
     *
     * @param pattern The pattern to match
     * @param cuType The calendar user type to match
     * @return The principal identifier, or <code>-1</code> if none could be extracted
     */
    private static int extractPrincipalID(String pattern, CalendarUserType cuType, DAVFactory factory) {
        String trimmedPattern = Strings.trimStart(Strings.trimEnd(pattern, '*'), '*');
        /*
         * try principal URL
         */
        PrincipalURL principalURL = PrincipalURL.parse(trimmedPattern, factory.getService(ConfigViewFactory.class));
        if (null != principalURL && cuType.equals(principalURL.getType())) {
            return principalURL.getPrincipalID();
        }
        /*
         * try resource ID
         */
        ResourceId resourceId = ResourceId.parse(trimmedPattern);
        if (null != resourceId && resourceId.getCalendarUserType().equals(cuType)) {
            return resourceId.getEntity();
        }
        return -1;
    }

}
