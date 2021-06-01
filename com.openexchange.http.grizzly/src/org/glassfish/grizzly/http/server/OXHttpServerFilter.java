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

package org.glassfish.grizzly.http.server;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.ShutdownEvent;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.util.HtmlHelper;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.Futures;
import com.openexchange.exception.ExceptionUtils;
import com.openexchange.http.grizzly.GrizzlyConfig;


/**
 * {@link OXHttpServerFilter}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.4
 */
public class OXHttpServerFilter extends HttpServerFilter {

    private final static Logger LOGGER = Grizzly.logger(HttpHandler.class);

    /**
     * The {@link CompletionHandler} to be used to make sure the response data
     * have been flushed
     */
    private final FlushResponseHandler flushResponseHandler = new FlushResponseHandler();

    /**
     * Attribute, which holds the current HTTP Request in progress associated
     * with an HttpContext
     */
    private final Attribute<Request> httpRequestInProgress;

    /**
     * Delay queue to control suspended request/response processing timeouts
     */
    private final DelayedExecutor.DelayQueue<Response.SuspendTimeout> suspendedResponseQueue;

    /**
     * Root {@link HttpHandler}
     */
    private volatile HttpHandler httpHandler;

    /**
     * The flag, which indicates if the server is currently in the shutdown phase
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    /**
     * CompletionHandler to be notified, when shutdown could be gracefully completed
     */
    private AtomicReference<FutureImpl<HttpServerFilter>> shutdownCompletionFuture;

    /**
     * The number of requests, which are currently in process.
     */
    private final AtomicInteger activeRequestsCounter = new AtomicInteger();

    /**
     * The backing Grizzly configuration
     */
    private final GrizzlyConfig grizzlyConfig;

    /**
     * The HTTP request guard
     */
    private final HttpRequestGuard guard;


    // ------------------------------------------------------------ Constructors

    public OXHttpServerFilter(GrizzlyConfig grizzlyConfig, final ServerFilterConfiguration config, final DelayedExecutor delayedExecutor) {
        super(config, delayedExecutor);
        this.grizzlyConfig = grizzlyConfig;
        this.guard = HttpRequestGuard.guardFor(grizzlyConfig.getMaxNumberOfConcurrentRequests());
        suspendedResponseQueue = Response.createDelayQueue(delayedExecutor);
        httpRequestInProgress = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("HttpServerFilter.Request");
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    @Override
    public void setHttpHandler(final HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
    }

    // ----------------------------------------------------- Methods from Filter

    @Override
    public NextAction handleRead(final FilterChainContext ctx)
          throws IOException {

        // every message coming to HttpServerFilter#handleRead has to have
        // HttpContext associated with the FilterChainContext
        assert HttpContext.get(ctx) != null;

        final Object message = ctx.getMessage();
        final Connection<?> connection = ctx.getConnection();

        if (false == HttpPacket.isHttp(message)) {
            // this code will be run, when we resume the context
            // We're finishing the request processing
            final Response response = (Response) message;
            final Request request = response.getRequest();
            return afterService(ctx, connection, request, response);
        }
        
        // Otherwise cast message to a HttpContent
        final HttpContent httpContent = (HttpContent) message;
        final HttpContext context = httpContent.getHttpHeader().getProcessingState().getHttpContext();
        Request handlerRequest = httpRequestInProgress.get(context);

        if (handlerRequest != null) {
            // We're working with suspended HTTP request
            try {
                ctx.suspend();
                final NextAction action = ctx.getSuspendAction();

                if (!handlerRequest.getInputBuffer().append(httpContent)) {
                    // we don't want this thread/context to reset
                    // OP_READ on Connection

                    // we have enough data? - terminate filter chain execution
                    ctx.completeAndRecycle();
                } else {
                    ctx.resume(ctx.getStopAction());
                }

                return action;
            } finally {
                httpContent.recycle();
            }
        }
        // It's a new HTTP request
        final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
        final HttpResponsePacket response = request.getResponse();
        ServerFilterConfiguration config = getConfiguration();

        handlerRequest = OXRequest.create(grizzlyConfig);
        handlerRequest.parameters.setLimit(config.getMaxRequestParameters());
        httpRequestInProgress.set(context, handlerRequest);
        final Response handlerResponse = handlerRequest.getResponse();

        handlerRequest.initialize(request, ctx, this);
        handlerResponse.initialize(handlerRequest, response,
                ctx, suspendedResponseQueue, this);

        if (config.isGracefulShutdownSupported()) {
            activeRequestsCounter.incrementAndGet();
            handlerRequest.addAfterServiceListener(flushResponseHandler);
        }

        HttpServerProbeNotifier.notifyRequestReceive(this, connection,
                handlerRequest);

        boolean wasSuspended = false;

        try {
            ctx.setMessage(handlerResponse);

            if (shuttingDown.get()) { // if we're in the shutting down phase - serve shutdown page and exit
                handlerResponse.getResponse().getProcessingState().setError(true);
                HtmlHelper.setErrorAndSendErrorPage(
                        handlerRequest, handlerResponse,
                        config.getDefaultErrorPageGenerator(),
                        503, HttpStatus.SERVICE_UNAVAILABLE_503.getReasonPhrase(),
                        "The server is being shutting down...", null);
            } else if (!config.isPassTraceRequest()
                    && request.getMethod() == Method.TRACE) {
                onTraceRequest(handlerRequest, handlerResponse);
            } else if (!checkMaxPostSize(request.getContentLength())) {
                handlerResponse.getResponse().getProcessingState().setError(true);
                HtmlHelper.setErrorAndSendErrorPage(
                        handlerRequest, handlerResponse,
                        config.getDefaultErrorPageGenerator(),
                        400, HttpStatus.BAD_REQUEST_400.getReasonPhrase(),
                        "The request payload size exceeds the max post size limitation", null);
            } else {
                if (guard.mayHandle(handlerRequest)) {
                    final HttpHandler httpHandlerLocal = httpHandler;
                    if (httpHandlerLocal != null) {
                        wasSuspended = !httpHandlerLocal.doHandle(
                            handlerRequest, handlerResponse);
                    }
                } else {
                    handlerResponse.getResponse().getProcessingState().setError(true);
                    HtmlHelper.setErrorAndSendErrorPage(
                            handlerRequest, handlerResponse,
                            config.getDefaultErrorPageGenerator(),
                            503, "Service Temporary Unavailable",
                            "The server is temporary overloaded...", null);
                }
            }
        } catch (Exception t) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVER_FILTER_HTTPHANDLER_INVOCATION_ERROR(), t);

            request.getProcessingState().setError(true);

            if (!response.isCommitted()) {
                    HtmlHelper.setErrorAndSendErrorPage(
                            handlerRequest, handlerResponse,
                            config.getDefaultErrorPageGenerator(),
                            500, HttpStatus.INTERNAL_SERVER_ERROR_500.getReasonPhrase(),
                            HttpStatus.INTERNAL_SERVER_ERROR_500.getReasonPhrase(),
                            t);
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVER_FILTER_UNEXPECTED(), t);
            throw new IllegalStateException(t);
        }

        if (!wasSuspended) {
            return afterService(ctx, connection, handlerRequest, handlerResponse);
        }
        return ctx.getSuspendAction();
    }

    /**
     * Override the default implementation to notify the {@link ReadHandler},
     * if available, of any read error that has occurred during processing.
     *
     * @param ctx event processing {@link FilterChainContext}
     * @param error error, which occurred during <tt>FilterChain</tt> execution
     */
    @Override
    public void exceptionOccurred(final FilterChainContext ctx,
            final Throwable error) {
        final HttpContext context = HttpContext.get(ctx);
        if (context != null) {
            final Request request = httpRequestInProgress.get(context);

            if (request != null) {
                final ReadHandler handler = request.getInputBuffer().getReadHandler();
                if (handler != null) {
                    handler.onError(error);
                }
            }
        }
    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx, final FilterChainEvent event) throws IOException {
        if (event.type() == ShutdownEvent.TYPE) {
            if (shuttingDown.compareAndSet(false, true)) {
                final ShutdownEvent shutDownEvent = (ShutdownEvent) event;
                final FutureImpl<HttpServerFilter> future = Futures.createSafeFuture();


                shutDownEvent.addShutdownTask(new Callable<Filter>() {
                    @Override
                    public Filter call() throws Exception {
                        return future.get();
                    }
                });

                shutdownCompletionFuture = new AtomicReference<>(future);
                if (activeRequestsCounter.get() == 0) {
                    future.result(this);
                }
            }
        }
        return ctx.getInvokeAction();
    }

    // --------------------------------------------------------- Private Methods

    private NextAction afterService(
            final FilterChainContext ctx,
            final Connection<?> connection,
            final Request request,
            final Response response) {

        final HttpContext context = request.getRequest()
                .getProcessingState().getHttpContext();

        httpRequestInProgress.remove(context);
        response.finish();
        request.onAfterService();

        HttpServerProbeNotifier.notifyRequestComplete(this, connection, response);

        final HttpRequestPacket httpRequest = request.getRequest();
        final boolean isBroken = httpRequest.isContentBroken();

        // Suspend state is cancelled - it means normal processing might have
        // been broken. We don't want to reuse Request and Response in this state,
        // cause there still might be threads referencing them.
        if (response.suspendState != Response.SuspendState.CANCELLED) {
            response.recycle();
            request.recycle();
        }

        if (isBroken) {
            // if content is broken - we're not able to distinguish
            // the end of the message - so stop processing any input data on
            // this connection (connection is being closed by
            // {@link org.glassfish.grizzly.http.HttpServerFilter#handleEvent(...)}
            final NextAction suspendNextAction = ctx.getSuspendAction();
            ctx.completeAndRecycle();
            return suspendNextAction;
        }

        return ctx.getStopAction();
    }

    /**
     * Will be called, once HTTP request processing is complete and response is
     * flushed.
     */
    private void onRequestCompleteAndResponseFlushed() {
        final int count = activeRequestsCounter.decrementAndGet();
        if (count == 0 && shuttingDown.get()) {
            final FutureImpl<HttpServerFilter> shutdownFuture =
                shutdownCompletionFuture != null
                ? shutdownCompletionFuture.getAndSet(null)
                : null;

            if (shutdownFuture != null) {
                shutdownFuture.result(this);
            }
        }
    }

    /**
     * @param requestContentLength
     * @return <tt>true</tt> if request content-length doesn't exceed
     *      the max post size limit, or <tt>false</tt> otherwise
     */
    private boolean checkMaxPostSize(final long requestContentLength) {
        final long maxPostSize = getConfiguration().getMaxPostSize();
        return requestContentLength <= 0 || maxPostSize < 0 ||
                maxPostSize >= requestContentLength;
    }

    /**
     * The {@link CompletionHandler} to be used to make sure the response data
     * have been flushed.
     */
    @SuppressWarnings("synthetic-access")
    private final class FlushResponseHandler extends EmptyCompletionHandler<Object> implements AfterServiceListener{

        private final FilterChainEvent event = TransportFilter.createFlushEvent(this);

        FlushResponseHandler() {
            super();
        }
        
        @Override
        public void cancelled() {
            onRequestCompleteAndResponseFlushed();
        }

        @Override
        public void failed(final Throwable throwable) {
            onRequestCompleteAndResponseFlushed();
        }

        @Override
        public void completed(final Object result) {
            onRequestCompleteAndResponseFlushed();
        }

        @Override
        public void onAfterService(final Request request) {
            // same as request.getContext().flush(this), but less garbage
            request.getContext().notifyDownstream(event);
        }
    }

}
