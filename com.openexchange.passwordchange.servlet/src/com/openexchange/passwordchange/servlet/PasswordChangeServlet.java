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

package com.openexchange.passwordchange.servlet;

import static com.openexchange.tools.servlet.http.Tools.copyHeaders;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.SessionServlet;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.writer.ResponseWriter;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.login.multifactor.MultifactorLoginService;
import com.openexchange.passwordchange.BasicPasswordChangeService;
import com.openexchange.passwordchange.PasswordChangeEvent;
import com.openexchange.passwordchange.PasswordChangeService;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link PasswordChangeServlet}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class PasswordChangeServlet extends SessionServlet {

    private static final long serialVersionUID = 3129607149739575803L;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PasswordChangeServlet.class);

    private final transient ServiceLookup services;

    /**
     * Initializes a new {@link PasswordChangeServlet}
     *
     * @param services The service look-up
     */
    public PasswordChangeServlet(ServiceLookup services) {
        super();
        this.services = services;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try {
            Tools.checkNonExistence(req, PARAMETER_PASSWORD);
        } catch (OXException oxException) {
            handleException(req, resp, oxException);
            return;
        }

        resp.setContentType(CONTENTTYPE_JSON);
        /*
         * The magic spell to disable caching
         */
        Tools.disableCaching(resp);
        try {
            actionGet(req, resp);
        } catch (OXException e) {
            LOGGER.error("PasswordChangeServlet.doGet()", e);
            handleException(req, resp, e);
        }
    }

    @Override
    protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try {
            Tools.checkNonExistence(req, PARAMETER_PASSWORD);
        } catch (OXException oxException) {
            handleException(req, resp, oxException);
            return;
        }

        resp.setContentType(CONTENTTYPE_JSON);
        /*
         * The magic spell to disable caching
         */
        Tools.disableCaching(resp);
        try {
            actionPut(req, resp);
        } catch (OXException e) {
            LOGGER.error("PasswordChangeServlet.doPut()", e);
            handleException(req, resp, e);
        } catch (JSONException e) {
            LOGGER.error("PasswordChangeServlet.doPut()", e);
            final Response response = new Response();
            response.setException(PasswordChangeServletExceptionCode.JSON_ERROR.create(e, e.getMessage()));
            final PrintWriter writer = resp.getWriter();
            try {
                ResponseWriter.write(response, resp.getWriter(), localeFrom(getSessionObject(req)));
            } catch (JSONException e1) {
                final ServletException se = new ServletException(e1);
                se.initCause(e1);
                throw se;
            }
            writer.flush();
        }
    }

    private void handleException(final HttpServletRequest req, final HttpServletResponse resp, final OXException e) throws IOException, ServletException {
        final ServerSession session = getSessionObject(req);
        final Response response = new Response(session);
        response.setException(e);
        final PrintWriter writer = resp.getWriter();
        try {
            ResponseWriter.write(response, resp.getWriter(), localeFrom(session));
        } catch (JSONException e1) {
            final ServletException se = new ServletException(e1);
            se.initCause(e1);
            throw se;
        }
        writer.flush();
    }

    private void actionPut(final HttpServletRequest req, final HttpServletResponse resp) throws OXException, JSONException, IOException {
        final String actionStr = checkStringParam(req, PARAMETER_ACTION);
        if (actionStr.equalsIgnoreCase(AJAXServlet.ACTION_UPDATE)) {
            actionPutUpdate(req, resp);
        } else {
            throw PasswordChangeServletExceptionCode.UNSUPPORTED_ACTION.create(actionStr, "PUT");
        }
    }

    private void actionGet(final HttpServletRequest req, final HttpServletResponse resp) throws OXException {
        resp.setContentType(CONTENTTYPE_JSON);
        final String actionStr = checkStringParam(req, PARAMETER_ACTION);
        throw PasswordChangeServletExceptionCode.UNSUPPORTED_ACTION.create(actionStr, "GET");
    }

    protected void actionPutUpdate(final HttpServletRequest req, final HttpServletResponse resp) throws JSONException, IOException {
        final Response response = new Response();
        final Session session = getSessionObject(req);
        try {
            /*
             * get context & user
             */
            ContextService contextService = services.getService(ContextService.class);
            if (contextService == null) {
                throw ServiceExceptionCode.absentService(ContextService.class);
            }
            UserService userService = services.getService(UserService.class);
            if (null == userService) {
                throw ServiceExceptionCode.absentService(UserService.class);
            }
            Context context = contextService.getContext(session.getContextId());
            User user = userService.getUser(session.getUserId(), context);

            // Construct JSON object from request's body data and check mandatory fields
            String oldPw;
            String newPw;
            {
                JSONObject jBody = new JSONObject(getBody(req));
                String paramOldPw = "old_password";
                String paramNewPw = "new_password";
                if (!jBody.has(paramNewPw) || jBody.isNull(paramNewPw) && false == user.isGuest()) {
                    throw PasswordChangeServletExceptionCode.MISSING_PARAM.create(paramNewPw);
                }
                if (!jBody.has(paramOldPw) || jBody.isNull(paramOldPw) && false == user.isGuest()) {
                    throw PasswordChangeServletExceptionCode.MISSING_PARAM.create(paramOldPw);
                }

                newPw = jBody.isNull(paramNewPw) ? null : jBody.getString(paramNewPw);
                oldPw = jBody.isNull(paramOldPw) ? null : jBody.getString(paramOldPw);
            }

            // Perform password change
            if (user.isGuest()) {
                BasicPasswordChangeService passwordChangeService = services.getService(BasicPasswordChangeService.class);
                if (passwordChangeService == null) {
                    throw ServiceExceptionCode.absentService(BasicPasswordChangeService.class);
                }

                Map<String, List<String>> headers = copyHeaders(req);
                com.openexchange.authentication.Cookie[] cookies = Tools.getCookieFromHeader(req);

                passwordChangeService.perform(new PasswordChangeEvent(session, context, newPw, oldPw, headers, cookies, req.getRemoteAddr()));
            } else {
                PasswordChangeService passwordChangeService = services.getService(PasswordChangeService.class);
                if (passwordChangeService == null) {
                    throw ServiceExceptionCode.absentService(PasswordChangeService.class);
                }

                Map<String, List<String>> headers = copyHeaders(req);
                com.openexchange.authentication.Cookie[] cookies = Tools.getCookieFromHeader(req);

                checkMultifactor(session);

                passwordChangeService.perform(new PasswordChangeEvent(session, context, newPw, oldPw, headers, cookies, req.getRemoteAddr()));
            }
        } catch (OXException e) {
            LOGGER.error("", e);
            response.setException(e);
        }
        /*
         * Close response and flush print writer
         */
        response.setData(JSONObject.NULL);
        // response.addWarning(PasswordChangeServletExceptionCode.PW_CHANGE_SUCCEEDED.create());
        response.setTimestamp(null);
        ResponseWriter.write(response, resp.getWriter(), localeFrom(session));
    }

    private void checkMultifactor (Session session) throws OXException {
        MultifactorLoginService mfService = services.getOptionalService(MultifactorLoginService.class);
        if (mfService != null) {
            mfService.checkRecentMultifactorAuthentication(session);
        }
    }

    private static String checkStringParam(final HttpServletRequest req, final String paramName) throws OXException {
        final String paramVal = req.getParameter(paramName);
        if ((paramVal == null) || (paramVal.length() == 0) || "null".equals(paramVal)) {
            throw PasswordChangeServletExceptionCode.MISSING_PARAM.create(paramName);
        }
        return paramVal;
    }

}
