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

package com.openexchange.rest.services.metrics;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent.Type;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.uri.UriTemplate;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * {@link MetricsListener} - a {@link ApplicationEventListener} which records metrics for the rest servlets
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.4
 */
@Provider
public class MetricsListener implements ApplicationEventListener  {

    private static final String NAME = "appsuite.restapi.requests";

    @Override
    public void onEvent(ApplicationEvent event) {
        // do nothing
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return new RequestListener(System.currentTimeMillis());
    }

    /**
     *
     * {@link RequestListener}
     *
     * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
     * @since v7.10.4
     */
    private static class RequestListener implements RequestEventListener {

        private static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
        private static final String NOT_FOUND = "NOT_FOUND";
        private static final String INVALID = "INVALID";
        private final long start;

        /**
         * Initializes a new {@link MetricsListener.RequestListener}.
         *
         * @param startMillis The start time in milliseconds
         */
        public RequestListener(long startMillis) {
            super();
            this.start = startMillis;
        }

        @Override
        public void onEvent(RequestEvent event) {
            Type type = event.getType();
            if (type.equals(Type.FINISHED) || type.equals(Type.ON_EXCEPTION)) {
                if (type.equals(Type.FINISHED) && event.getException() != null) {
                    //A finished event with an exception should have been handled before in an exception event
                    return;
                }
                List<UriTemplate> templates = event.getContainerRequest().getUriInfo().getMatchedTemplates();
                String path = null;
                if (templates.isEmpty()) {
                    path = "/" + event.getUriInfo().getPath();
                } else {
                    path = contructPath(templates);
                }
                StatusType status = Status.INTERNAL_SERVER_ERROR;
                if (type.equals(Type.ON_EXCEPTION)) {
                    Throwable exception = event.getException();
                    if(exception instanceof WebApplicationException) {
                        WebApplicationException we = (WebApplicationException) exception;
                        if(we instanceof NotFoundException) {
                            Duration duration = Duration.ofMillis(System.currentTimeMillis() - start);
                            getTimer(INVALID, NOT_FOUND, 404).record(duration);
                            return;
                        }
                        status = we.getResponse().getStatusInfo();
                    }
                } else {
                    if(event.getContainerResponse() != null) {
                        status = event.getContainerResponse().getStatusInfo();
                    }
                }
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - start);
                getTimer(path, status.equals(Status.METHOD_NOT_ALLOWED) ? METHOD_NOT_ALLOWED : event.getContainerRequest().getMethod(), status.getStatusCode()).record(duration);
            }
        }

        /**
         * Constructs the path from the list of {@link UriTemplate}s
         *
         * @param templates The list of templates
         * @return The path
         */
        private String contructPath(List<UriTemplate> templates) {
            StringBuilder result = new StringBuilder();
            LinkedList<UriTemplate> linkedList = new LinkedList<>(templates);
            Collections.reverse(linkedList);
            linkedList.forEach((t) -> result.append(t.getTemplate()));
            return result.toString().replaceAll("//", "/");
        }

        /**
         * Gets the timer with the given values
         *
         * @param path The template path
         * @param method The method
         * @param status The status code
         * @return The Timer
         */
        private Timer getTimer(String path, String method, int status) {
            // @formatter:off
            return Timer.builder(NAME)
                        .tags("path", path, "method", method, "status", String.valueOf(status))
                        .description("Records the duration of rest calls.")
                        .serviceLevelObjectives(
                            Duration.ofMillis(50),
                            Duration.ofMillis(100),
                            Duration.ofMillis(150),
                            Duration.ofMillis(200),
                            Duration.ofMillis(250),
                            Duration.ofMillis(300),
                            Duration.ofMillis(400),
                            Duration.ofMillis(500),
                            Duration.ofMillis(750),
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(2),
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(10),
                            Duration.ofSeconds(30),
                            Duration.ofMinutes(1))
                        .register(Metrics.globalRegistry);
            // @formatter:on
        }
    }

}
