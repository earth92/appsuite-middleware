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

package com.openexchange.dav.actions;

import static com.openexchange.dav.DAVTools.removePrefixFromPath;
import static com.openexchange.webdav.protocol.Protocol.DAV_NS;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import com.openexchange.dav.DAVFactory;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.resources.DAVResource;
import com.openexchange.framework.request.RequestContextHolder;
import com.openexchange.groupware.notify.hostname.HostData;
import com.openexchange.groupware.notify.hostname.HostnameService;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.session.Session;
import com.openexchange.webdav.action.AbstractAction;
import com.openexchange.webdav.action.WebdavRequest;
import com.openexchange.webdav.action.WebdavResponse;
import com.openexchange.webdav.loader.LoadingHints;
import com.openexchange.webdav.protocol.Protocol;
import com.openexchange.webdav.protocol.WebdavFactory;
import com.openexchange.webdav.protocol.WebdavPath;
import com.openexchange.webdav.protocol.WebdavProperty;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;
import com.openexchange.webdav.xml.resources.PropertiesMarshaller;
import com.openexchange.webdav.xml.resources.PropfindAllPropsMarshaller;
import com.openexchange.webdav.xml.resources.PropfindPropNamesMarshaller;
import com.openexchange.webdav.xml.resources.PropfindResponseMarshaller;
import com.openexchange.webdav.xml.resources.RecursiveMarshaller;
import com.openexchange.webdav.xml.resources.ResourceMarshaller;

/**
 * {@link DAVAction}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.8.1
 */
public abstract class DAVAction extends AbstractAction {

    protected final Protocol protocol;

    /**
     * Initializes a new {@link DAVAction}.
     *
     * @param protocol The underlying protocol
     */
    public DAVAction(Protocol protocol) {
        super();
        this.protocol = protocol;
    }

    /**
     * Gets the request body document from the supplied WebDAV request, throwing an appropriate exception if there is none or parsing fails.
     *
     * @param request The WebDAV request
     * @return The response body
     */
    protected Document requireRequestBody(WebdavRequest request) throws WebdavProtocolException {
        try {
            Document document = request.getBodyAsDocument();
            if (null == document) {
                throw WebdavProtocolException.Code.GENERAL_ERROR.create(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
            }
            return document;
        } catch (JDOMException | IOException e) {
            throw WebdavProtocolException.Code.GENERAL_ERROR.create(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST, e);
        }
    }

    /**
     * Gets the root element of the request body from the supplied WebDAV request, throwing an appropriate exception if there is none or
     * parsing fails.
     *
     * @param request The WebDAV request
     * @param namespace The expected namespace of the root element
     * @param name The expected name of the root element
     * @return The root element
     */
    protected Element requireRootElement(WebdavRequest request, Namespace namespace, String name) throws WebdavProtocolException {
        Element rootElement = requireRequestBody(request).getRootElement();
        if (null == rootElement || false == rootElement.getNamespace().equals(namespace) || false == rootElement.getName().equals(name)) {
            throw WebdavProtocolException.generalError(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
        return rootElement;
    }

    /**
     * Optionally gets the root element of the request body from the supplied WebDAV request.
     * parsing fails.
     *
     * @param request The WebDAV request
     * @param namespace The expected namespace of the root element
     * @param name The expected name of the root element
     * @return The root element, or <code>null</code> if there is none or no document could be parsed
     */
    protected Element optRootElement(WebdavRequest request, Namespace namespace, String name) {
        Document requestBody = optRequestBody(request);
        if (null != requestBody) {
            Element rootElement = requestBody.getRootElement();
            if (null != rootElement && rootElement.getNamespace().equals(namespace) && rootElement.getName().equals(name)) {
                return rootElement;
            }
        }
        return null;
    }

    /**
     * Optionally extracts the request body document from a WebDAV request.
     *
     * @param request The WebDAV request
     * @return The response body, or <code>null</code> if there is none or no document could be parsed
     */
    protected Document optRequestBody(WebdavRequest request) {
        try {
            return request.getBodyAsDocument();
        } catch (JDOMException | IOException e) {
            org.slf4j.LoggerFactory.getLogger(DAVAction.class).debug("Error getting WebDAV request body", e);
            return null;
        }
    }

    /**
     * Gets the WebDAV resource targeted by the supplied WebDAV request, throwing appropriate exceptions in case no suitable resource is
     * found.
     *
     * @param request The request to get the resource for
     * @return The resource
     */
    protected DAVResource requireResource(WebdavRequest request) throws WebdavProtocolException {
        return requireResource(request, DAVResource.class);
    }

    /**
     * Gets the WebDAV resource targeted by the supplied WebDAV request, throwing appropriate exceptions in case no suitable resource is
     * found.
     *
     * @param request The request to get the resource for
     * @param clazz The target resource's type
     * @return The resource
     */
    protected <T> T requireResource(WebdavRequest request, Class<T> clazz) throws WebdavProtocolException {
        WebdavResource resource = request.getResource();
        if (null == resource) {
            throw WebdavProtocolException.Code.GENERAL_ERROR.create(request.getUrl(), HttpServletResponse.SC_NOT_FOUND);
        }
        try {
            return clazz.cast(resource);
        } catch (ClassCastException e) {
            throw WebdavProtocolException.Code.GENERAL_ERROR.create(request.getUrl(), HttpServletResponse.SC_CONFLICT);
        }
    }

    /**
     * Parses the request's content length header.
     *
     * @param request The request to parse the content length for
     * @return The content length, or <code>-1</code> if not set or parsing fails
     */
    protected long getContentLength(WebdavRequest request) {
        String value = request.getHeader("Content-Length");
        if (Strings.isNotEmpty(value)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                org.slf4j.LoggerFactory.getLogger(DAVAction.class).warn("Error parsing \"Content-Length\" header", e);
            }
        }
        return -1;
    }

    /**
     * Gets the request's content type, based on the supplied <code>Content-Type</code> header, falling back to a content type indicated
     * by the resource name.
     *
     * @param request The request to get the content type for
     * @return The content-type or <code>application/octet-stream</code> if no other could be detected
     */
    protected String getContentType(WebdavRequest request) {
        String value = request.getHeader("Content-Type");
        return Strings.isEmpty(value) ? MimeType2ExtMap.getContentType(request.getUrl().name(), "application/octet-stream") : value;
    }

    /**
     * Gets a resource marshaller appropriate for the supplied request.
     *
     * @param request The WebDAV request
     * @param requestBody The request body document, or <code>null</code> if not available
     * @return The resource marshaller
     */
    protected ResourceMarshaller getMarshaller(WebdavRequest request, Document requestBody) {
        return getMarshaller(request, requestBody, request.getURLPrefix());
    }

    /**
     * Gets a resource marshaller appropriate for the supplied request.
     *
     * @param request The WebDAV request
     * @param requestBody The request body document, or <code>null</code> if not available
     * @param urlPrefix The URL prefix to use for marshalling
     * @return The resource marshaller
     */
    protected ResourceMarshaller getMarshaller(WebdavRequest request, Document requestBody, String urlPrefix) {
        /*
         * prepare loading hints
         */
        int depth = request.getDepth(0);
        LoadingHints loadingHints = new LoadingHints();
        loadingHints.setDepth(depth);
        loadingHints.setUrl(request.getUrl());
        /*
         * create appropriate response marshaller
         */
        PropertiesMarshaller marshaller;
        if (null == requestBody || null != requestBody.getRootElement().getChild("allprop", DAVProtocol.DAV_NS)) {
            /*
             * marshal all properties
             */
            loadingHints.setProps(LoadingHints.Property.ALL);
            marshaller = new PropfindAllPropsMarshaller(urlPrefix, request.getCharset());
        } else if (null != requestBody.getRootElement().getChild("propname", DAVProtocol.DAV_NS)) {
            /*
             * marshal all property names
             */
            loadingHints.setProps(LoadingHints.Property.ALL);
            marshaller = new PropfindPropNamesMarshaller(urlPrefix, request.getCharset());
        } else {
            /*
             * marshal specific properties
             */
            loadingHints.setProps(LoadingHints.Property.SOME);
            PropfindResponseMarshaller responseMarshaller = new PropfindResponseMarshaller(urlPrefix, request.getCharset(), request.isBrief());
            for (Element requestedProps : requestBody.getRootElement().getChildren("prop", DAVProtocol.DAV_NS)){
                for (Element requestedProperty : requestedProps.getChildren()) {
                    loadingHints.addProperty(requestedProperty.getNamespaceURI(), requestedProperty.getName());
                    WebdavProperty property = new WebdavProperty(requestedProperty.getNamespaceURI(), requestedProperty.getName());
                    property.setChildren(requestedProperty.getChildren());
                    responseMarshaller.addProperty(property);
                }
            }
            marshaller = responseMarshaller;
        }
        /*
         * pre-load as needed
         */
        preLoad(loadingHints);
        /*
         * wrap into recursive marshaller if needed
         */
        return 0 == depth ? marshaller : new RecursiveMarshaller(marshaller, depth, protocol.getRecursiveMarshallingLimit());
    }

    /**
     * Sends a XML response document.
     *
     * @param response The WebDAV response to write to
     * @param responseBody The response body document
     * @param status The HTTP status code to use
     */
    protected void sendXMLResponse(WebdavResponse response, Document responseBody, int status) {
        try {
            response.setStatus(status);
            response.setContentType("text/xml; charset=UTF-8");
            new XMLOutputter(Format.getPrettyFormat()).output(responseBody, response.getOutputStream());
        } catch (IOException e) {
            if ("Connection reset by peer".equals(e.getMessage())) {
                org.slf4j.LoggerFactory.getLogger(DAVAction.class).debug("Error sending WebDAV response", e);
            } else {
                org.slf4j.LoggerFactory.getLogger(DAVAction.class).warn("Error sending WebDAV response", e);
            }
        }
    }

    /**
     * Prepares an XML element ready to be used as root element for a multistatus response, containing any additionally defined
     * namespace declarations of the underlying protocol.
     *
     * @return A new multistatus element
     */
    protected Element prepareMultistatusElement() {
        Element multistatusElement = new Element("multistatus", DAV_NS);
        for (Namespace namespace : protocol.getAdditionalNamespaces()) {
            multistatusElement.addNamespaceDeclaration(namespace);
        }
        return multistatusElement;
    }

    /**
     * Sends a multistatus response.
     *
     * @param response The WebDAV response to write to
     * @param multistatusElement The root element for the multistatus response
     */
    protected void sendMultistatusResponse(WebdavResponse response, Element multistatusElement) {
        sendXMLResponse(response, new Document(multistatusElement), Protocol.SC_MULTISTATUS);
    }

    protected HostData getHostData(WebdavRequest request) throws WebdavProtocolException {
        /*
         * get host data from request context or session parameter
         */
        com.openexchange.framework.request.RequestContext requestContext = RequestContextHolder.get();
        if (null != requestContext) {
            return requestContext.getHostData();
        }
        WebdavFactory factory = request.getFactory();
        if (DAVFactory.class.isInstance(factory)) {
            Session session = ((DAVFactory) factory).getSession();
            if (null != session) {
                HostData hostData = (HostData) session.getParameter(HostnameService.PARAM_HOST_DATA);
                if (null != hostData) {
                    return hostData;
                }
            }
        }
        throw WebdavProtocolException.generalError(request.getUrl(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Optionally sets a response header if the supplied value reference is not <code>null</code>.
     *
     * @param header The name of the header name to set
     * @param value The value to set, or <code>null</code> to do nothing
     * @param response The response to set the header in
     */
    protected void setHeaderOpt(String header, Object value, WebdavResponse response) {
        setHeaderOpt(header, value, false, response);
    }

    /**
     * Optionally sets a response header if the supplied value reference is not <code>null</code>.
     *
     * @param header The name of the header name to set
     * @param value The value to set, or <code>null</code> to do nothing
     * @param quote <code>true</code> to put quotation marks around the value, <code>false</code> to set the value as-is
     * @param response The response to set the header in
     */
    protected void setHeaderOpt(String header, Object value, boolean quote, WebdavResponse response) {
        if (null != value) {
            response.setHeader(header, quote ? Strings.quote(String.valueOf(value), true) : String.valueOf(value));
        }
    }

    /**
     * Gets the decoded WebDAV paths from all <code>DAV:href</code> children of the supplied parent element.
     *
     * @param request The underlying WebDAV request
     * @param parentElement The parent element to extract the paths of the children from
     * @return The extracted WebDAV paths, or an empty list if there are none
     */
    protected List<WebdavPath> getHrefPaths(WebdavRequest request, Element parentElement) {
        List<Element> children = null == parentElement ? null : parentElement.getChildren("href", DAV_NS);
        if (null == children || children.isEmpty()) {
            return Collections.emptyList();
        }
        List<WebdavPath> paths = new ArrayList<WebdavPath>(children.size());
        for (Element element : children) {
            paths.add(getPath(request, element.getText()));
        }
        return paths;
    }

    /**
     * Gets the WebDAV path from the supplied <code>href</code> value.
     *
     * @param request The underlying WebDAV request
     * @param href The <code>href</code> value to get the WebDAV path from
     * @return The WebDAV path
     */
    protected WebdavPath getPath(WebdavRequest request, String href) {
        String path;
        try {
            path = new URI(href).getPath();
        } catch (URISyntaxException e) {
            org.slf4j.LoggerFactory.getLogger(DAVAction.class).warn("Error instantiating an URI from {}", href, e);
            path = href;
        }
        String urlPrefix = request.getURLPrefix();
        path = removePrefixFromPath(urlPrefix, path);
        return new WebdavPath(path);
    }

}
