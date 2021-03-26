/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.ajax.login;

import java.io.IOException;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.writer.ResponseWriter;
import com.openexchange.exception.OXException;
import com.openexchange.login.LoginRampUpService;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.session.ServerSessionAdapter;


/**
 * {@link RampUp}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class RampUp extends AbstractLoginRequestHandler implements LoginRequestHandler {

    public RampUp(Set<LoginRampUpService> rampUp) {
        super(rampUp);
    }

    @Override
    public void handleRequest(HttpServletRequest req, HttpServletResponse resp, LoginRequestContext requestContext) throws IOException {

        AJAXServlet.setDefaultContentType(resp);
        final Response response = new Response();
        Session session = null;
        try {
            final SessiondService sessiondService = ServerServiceRegistry.getInstance().getService(SessiondService.class);
            if (null == sessiondService) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String sessionId = req.getParameter("session");
            if (null == sessionId) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing \"session\" parameter.");
                requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            session = sessiondService.getSession(sessionId);
            if (null == session) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "No such session.");
                requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            JSONObject json = new JSONObject(8);
            performRampUp(req, json, ServerSessionAdapter.valueOf(session), true);

            // Set data
            response.setData(json);

        } catch (OXException e) {
            response.setException(e);
        }

        // The magic spell to disable caching
        Tools.disableCaching(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
        AJAXServlet.setDefaultContentType(resp);
        try {
            if (response.hasError()) {
                requestContext.getMetricProvider().recordException(response.getException());
                ResponseWriter.write(response, resp.getWriter(), LoginServlet.localeFrom(session));
            } else {
                ((JSONObject) response.getData()).write(resp.getWriter());
                requestContext.getMetricProvider().recordSuccess();
            }
        } catch (JSONException e) {
            LoginServlet.sendError(resp);
            requestContext.getMetricProvider().recordHTTPStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
