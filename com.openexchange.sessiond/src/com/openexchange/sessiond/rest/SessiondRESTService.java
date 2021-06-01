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

package com.openexchange.sessiond.rest;

import java.util.Collection;
import java.util.function.Supplier;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.rest.services.CommonMediaType;
import com.openexchange.rest.services.JAXRSService;
import com.openexchange.rest.services.annotation.Role;
import com.openexchange.rest.services.annotation.RoleAllowed;
import com.openexchange.server.ServiceLookup;
import com.openexchange.sessiond.SessionFilter;
import com.openexchange.sessiond.SessiondService;

/**
 * {@link SessiondRESTService}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.2
 */
@RoleAllowed(Role.MASTER_ADMIN_AUTHENTICATED)
@Path("/admin/v1/close-sessions")
public class SessiondRESTService extends JAXRSService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessiondRESTService.class);

    /**
     * Initialises a new {@link SessiondRESTService}.
     *
     * @param services The {@link ServiceLookup} instance
     */
    public SessiondRESTService(ServiceLookup services) {
        super(services);
    }

    /**
     * Closes the sessions specified by the ids in the JSON payload
     *
     * @param global whether to perform a cluster-wide or local clean-up. Defaults to <code>true</code>
     * @param payload the payload containing the session identifiers
     * @return
     *         <ul>
     *         <li><b>200</b>: if the sessions were closed successfully</li>
     *         <li><b>400</b>: if the client issued a bad request</li>
     *         <li><b>401</b>: if the client was not authenticated</li>
     *         <li><b>403</b>: if the client was not authorised</li>
     *         <li><b>500</b>: if any server side error is occurred</li>
     *         </ul>
     */
    @POST
    @Path("/by-id")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.APPLICATION_JSON, CommonMediaType.APPLICATION_PROBLEM_JSON })
    public Response closeSessionsById(@QueryParam("global") Boolean global, JSONObject payload) {
        return perform(() -> closeSessions(global, createFilter(getPayloadValues(payload, SessiondRESTField.SESSION_IDS), SessionFilterType.SESSION)));
    }

    /**
     * Closes the sessions that belong to the specified contexts specified by the identifiers in the JSON payload
     *
     * @param global whether to perform a cluster-wide or local clean-up. Defaults to <code>true</code>
     * @param payload the payload containing the context identifiers
     * @return
     *         <ul>
     *         <li><b>200</b>: if the sessions were closed successfully</li>
     *         <li><b>400</b>: if the client issued a bad request</li>
     *         <li><b>401</b>: if the client was not authenticated</li>
     *         <li><b>403</b>: if the client was not authorised</li>
     *         <li><b>500</b>: if any server side error is occurred</li>
     *         </ul>
     */
    @POST
    @Path("/by-context")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.APPLICATION_JSON, CommonMediaType.APPLICATION_PROBLEM_JSON })
    public Response closeSessionsByContextId(@QueryParam("global") Boolean global, JSONObject payload) {
        return perform(() -> closeSessions(global, createFilter(getPayloadValues(payload, SessiondRESTField.CONTEXT_IDS), SessionFilterType.CONTEXT)));
    }

    /**
     * Closes the sessions that belong to the specified users specified by the contextId/userId tuple in the JSON payload
     *
     * @param global whether to perform a cluster-wide or local clean-up. Defaults to <code>true</code>
     * @param payload the payload containing the context identifiers
     * @return
     *         <ul>
     *         <li><b>200</b>: if the sessions were closed successfully</li>
     *         <li><b>400</b>: if the client issued a bad request</li>
     *         <li><b>401</b>: if the client was not authenticated</li>
     *         <li><b>403</b>: if the client was not authorised</li>
     *         <li><b>500</b>: if any server side error is occurred</li>
     *         </ul>
     */
    @POST
    @Path("/by-user")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.APPLICATION_JSON, CommonMediaType.APPLICATION_PROBLEM_JSON })
    public Response closeSessionsByUserId(@QueryParam("global") Boolean global, JSONObject payload) {
        return perform(() -> closeSessions(global, createFilter(getPayloadValues(payload, SessiondRESTField.USERS), SessionFilterType.USER)));
    }

    /////////////////////////////////////////// HELPERS /////////////////////////////////////////////

    /**
     * Performs the action
     *
     * @param supplier The {@link Supplier} to perform the action
     * @return
     *         <ul>
     *         <li><b>200</b>: if the action was performed successfully</li>
     *         <li><b>400</b>: if the client issued a bad request</li>
     *         <li><b>401</b>: if the client was not authenticated</li>
     *         <li><b>403</b>: if the client was not authorised</li>
     *         <li><b>500</b>: if any server side error is occurred</li>
     *         </ul>
     */
    private Response perform(Supplier<Response> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException e) {
            LOGGER.debug("", e);
            return Response.status(400).type(CommonMediaType.APPLICATION_PROBLEM_JSON_TYPE).entity(parse(e, 400)).build();
        } catch (Exception e) {
            LOGGER.debug("", e);
            return Response.status(500).build();
        }
    }

    /**
     * Retrieves from the specified payload the requested values array.
     *
     * @param payload The payload
     * @param restField The field to request
     * @return The {@link JSONArray} with the values
     * @throws IllegalArgumentException if the payload is either <code>null</code> or empty, or if the values array
     *             is either <code>null</code> or empty.
     */
    private JSONArray getPayloadValues(JSONObject payload, SessiondRESTField restField) {
        checkPayload(payload);
        JSONArray array = payload.optJSONArray(restField.getFieldName());
        if (array == null || array.isEmpty()) {
            throw new IllegalArgumentException("Missing values array: '" + restField.getFieldName() + "'.");
        }
        return array;
    }

    /**
     * Checks whether the payload is <code>null</code> or empty.
     *
     * @param payload The payload to check
     * @throws IllegalArgumentException if the payload is either <code>null</code> or empty.
     */
    private void checkPayload(JSONObject payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Missing request payload.");
        }
    }

    /**
     * Creates a {@link SessionFilter} of the specified {@link SessionFilterType}
     *
     * @param array The array containing the values for the {@link SessionFilter}
     * @param sessionFilterType The {@link SessionFilterType}
     * @return The new {@link SessionFilter}
     * @throws IllegalArgumentException If filter expression is invalid
     */
    private SessionFilter createFilter(JSONArray array, SessionFilterType sessionFilterType) {
        StringBuilder filter = new StringBuilder(64);
        if (array.length() > 1) {
            filter.append("(");
            filter.append("|");
        }
        for (int index = 0; index < array.length(); index++) {
            sessionFilterType.apply(filter, array.opt(index));
        }
        if (array.length() > 1) {
            filter.append(")");
        }
        return SessionFilter.create(filter.toString());
    }

    /**
     * Closes the sessions (either globally or locally) that meet the criteria of the specified filter.
     *
     * @param global Whether to close sessions on the entire cluster or on the local node.
     * @param sessionFilter The {@link SessionFilter}
     * @return The {@link Response} with the outcome, 200 if the operation succeeded, 500 if it failed.
     */
    private Response closeSessions(Boolean global, SessionFilter sessionFilter) {
        try {
            if (global == null) {
                global = Boolean.valueOf(true);
            }
            SessiondService sessionService = SessiondService.SERVICE_REFERENCE.get();
            Collection<String> sessions = global.booleanValue() ? sessionService.removeSessionsGlobally(sessionFilter) : sessionService.removeSessions(sessionFilter);
            log(sessions, sessionFilter, global.booleanValue());
            return Response.ok(parse(sessions)).build();
        } catch (OXException e) {
            LOGGER.error("{}", e.getMessage(), e);
            return Response.status(500).build();
        }
    }

    /**
     * 
     * @param sessions
     * @return
     */
    private Object parse(Collection<String> sessions) {
        try {
            JSONArray array = new JSONArray(sessions.size());
            for (String s : sessions) {
                array.put(s);
            }
            JSONObject j = new JSONObject();
            j.put("closed", array);
            return j;
        } catch (JSONException e) {
            LOGGER.error("", e);
            return new JSONObject();
        }
    }

    /**
     * Decides whether to log the filter used to clear the sessions as well as the cleared sessions
     *
     * @param sessions The cleared sessions
     * @param filter The filter use to clear the sessions
     * @param global whether a global clear was invoked
     */
    private void log(Collection<String> sessions, SessionFilter filter, boolean global) {
        if (false == LOGGER.isDebugEnabled()) {
            return;
        }
        if (sessions.isEmpty()) {
            LOGGER.debug("No sessions were cleared {} via REST invocation with filter '{}'.", global ? "globally" : "locally", filter.toString());
            return;
        }
        StringBuilder b = new StringBuilder();
        for (String s : sessions) {
            b.append(s).append(",");
        }
        b.setLength(b.length() - 1);
        LOGGER.debug("Cleared sessions {} via REST invocation with filter '{}', {}", global ? "globally" : "locally", filter.toString(), sessions);
    }

    /**
     * Parses the specified {@link Exception} to a {@link JSONObject} that conforms with the
     * <code>RFC-7807</code>.
     *
     * @param e The {@link Exception} to parse
     * @return The {@link JSONObject} with the exception
     * @see <a href="https://tools.ietf.org/html/rfc7807">RFC-7807</a>
     */
    private JSONObject parse(Exception e, int statusCode) {
        try {
            // At the moment we lack proper documentation and/or code logic
            // to either include the 'type', 'instance' or the 'detail' fields.
            // - For the 'type' and 'instance' fields the documentation framework
            // needs to be adjusted in order to consider and include generic models
            // for the problem types and publish them at doc.ox.com.
            // - For the 'detail' field maybe the display message of the OXException ought
            // to do it, though for other non-OXExceptions there isn't much that
            // can be done other than explicitly analysing them or assigning them specific extra
            // details regarding their error type: e.g.
            //   * for IOExceptions:   'An I/O error was occurred. That's all we know'
            //   * for JSONExceptions: 'A JSON error was occurred due to xyz'
            //   * etc.
            JSONObject j = new JSONObject();
            j.put("title", e.getMessage());
            j.put("status", statusCode);
            return j;
        } catch (JSONException x) {
            LOGGER.error("", e);
            return new JSONObject();
        }
    }

    /**
     * {@link SessionFilterType} - Defines a {@link SessionFilter} type and incorporates
     * the logic to append to one.
     */
    private enum SessionFilterType {
        /**
         * Creates a {@link SessionFilter} with a {@link SessionFilter#SESSION_ID}
         */
        SESSION() {

            @Override
            void apply(StringBuilder filterBuilder, Object object) {
                if (false == (object instanceof String)) {
                    return;
                }
                String sessionId = (String) object;
                if (Strings.isEmpty(sessionId)) {
                    return;
                }
                filterBuilder.append("(").append(SessionFilter.SESSION_ID).append("=").append(object).append(")");
            }
        },

        /**
         * Creates a {@link SessionFilter} with a {@link SessionFilter#CONTEXT_ID}
         */
        CONTEXT() {

            @Override
            void apply(StringBuilder filterBuilder, Object object) {
                if (false == (object instanceof Integer)) {
                    return;
                }
                filterBuilder.append("(").append(SessionFilter.CONTEXT_ID).append("=").append(object).append(")");
            }
        },

        /**
         * Creates a {@link SessionFilter} with a {@link SessionFilter#CONTEXT_ID} and {@link SessionFilter#USER_ID}
         */
        USER() {

            @Override
            void apply(StringBuilder filterBuilder, Object object) {
                if (false == (object instanceof JSONObject)) {
                    return;
                }
                JSONObject user = (JSONObject) object;
                if (user.isEmpty()) {
                    return;
                }
                filterBuilder.append("(&(").append(SessionFilter.CONTEXT_ID).append("=").append(user.optInt(SessiondRESTField.CONTEXT_ID.getFieldName())).append(")");
                filterBuilder.append("(").append(SessionFilter.USER_ID).append("=").append(user.optInt(SessiondRESTField.USER_ID.getFieldName())).append("))");
            }
        };

        /**
         * Applies the value of the specified object to the specified filter builder
         *
         * @param filterBuilder The filter builder
         * @param object The object
         */
        abstract void apply(StringBuilder filterBuilder, Object object);
    }
}
