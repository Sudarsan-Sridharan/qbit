package io.advantageous.qbit.http.server.impl;

import io.advantageous.qbit.GlobalConstants;
import io.advantageous.qbit.concurrent.ExecutorContext;
import io.advantageous.qbit.http.request.HttpRequest;
import io.advantageous.qbit.http.server.HttpServer;
import io.advantageous.qbit.http.websocket.WebSocket;
import io.advantageous.qbit.http.websocket.WebSocketMessage;
import io.advantageous.qbit.http.websocket.WebSocketSender;
import io.advantageous.qbit.system.QBitSystemManager;
import io.advantageous.qbit.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.advantageous.qbit.concurrent.ScheduledExecutorBuilder.scheduledExecutorBuilder;
import static io.advantageous.qbit.http.websocket.WebSocketMessageBuilder.webSocketMessageBuilder;
import static org.boon.Boon.puts;
import static org.boon.Boon.sputs;

/**
 * Created by rhightower on 2/12/15.
 */
public class SimpleHttpServer implements HttpServer {
    private final Logger logger = LoggerFactory.getLogger(SimpleHttpServer.class);
    private final boolean debug = false || GlobalConstants.DEBUG || logger.isDebugEnabled();
    private Consumer<WebSocketMessage> webSocketMessageConsumer = webSocketMessage -> {};
    private Consumer<WebSocketMessage> webSocketCloseMessageConsumer = webSocketMessage -> {};
    private Consumer<HttpRequest> httpRequestConsumer = request -> {};
    private Consumer<Void> requestIdleConsumer = aVoid -> {};

    private Consumer<WebSocket> webSocketConsumer = webSocket -> {

        defaultWebSocketHandler(webSocket);

    };


    private Consumer<Void> webSocketIdleConsumer = aVoid -> {};
    private Predicate<HttpRequest> shouldContinueHttpRequest = request -> true;
    private ExecutorContext executorContext;
    private final QBitSystemManager systemManager;
    private final int flushInterval;

    public SimpleHttpServer(QBitSystemManager systemManager, int flushInterval) {
        this.systemManager = systemManager;
        this.flushInterval = flushInterval;
    }

    public SimpleHttpServer() {
        this.systemManager = null;
        flushInterval = 50;
    }

    /**
     * Main entry point.
     * @param request request to handle
     */
    public void handleRequest(final HttpRequest request) {
        if (debug) {
            puts("HttpServer::handleRequest", request);
            logger.debug("HttpServer::handleRequest" + request);
        }
        if (shouldContinueHttpRequest.test(request)) {
            httpRequestConsumer.accept(request);
        }
    }


    public void handleWebSocketMessage(final WebSocketMessage webSocketMessage) {
        webSocketMessageConsumer.accept(webSocketMessage);
    }


    public void handleWebSocketClosedMessage(WebSocketMessage webSocketMessage) {
        webSocketCloseMessageConsumer.accept(webSocketMessage);
    }

    @Override
    public void setShouldContinueHttpRequest(Predicate<HttpRequest> predicate) {
        this.shouldContinueHttpRequest = predicate;
    }

    @Override
    public void setWebSocketMessageConsumer(final Consumer<WebSocketMessage> webSocketMessageConsumer) {
        this.webSocketMessageConsumer = webSocketMessageConsumer;
    }

    @Override
    public void setWebSocketCloseConsumer(Consumer<WebSocketMessage> webSocketCloseMessageConsumer) {
        this.webSocketCloseMessageConsumer = webSocketCloseMessageConsumer;
    }

    @Override
    public void setHttpRequestConsumer(Consumer<HttpRequest> httpRequestConsumer) {
        this.httpRequestConsumer = httpRequestConsumer;
    }

    @Override
    public void setHttpRequestsIdleConsumer(Consumer<Void> idleConsumer) {
        this.requestIdleConsumer = idleConsumer;
    }

    @Override
    public void setWebSocketIdleConsume(Consumer<Void> idleConsumer) {
        this.webSocketIdleConsumer = idleConsumer;
    }


    @Override
    public void start() {

        if (debug) {
            puts("HttpServer Started");
            logger.debug("HttpServer Started");
        }

        startPeriodicFlush();
    }

    private void startPeriodicFlush() {
        if (executorContext!=null) {
            throw new IllegalStateException("Can't call start twice");
        }

        executorContext = scheduledExecutorBuilder()
                .setThreadName("HttpServer")
                .setInitialDelay(flushInterval)
                .setPeriod(flushInterval)
                .setDescription("HttpServer Periodic Flush")
                .setRunnable(() -> {
                    requestIdleConsumer.accept(null);
                    webSocketIdleConsumer.accept(null);
                })
                .build();

        executorContext.start();
    }

    @Override
    public void stop() {


        if (systemManager!=null) systemManager.serviceShutDown();

        if (debug) {
            puts("HttpServer Stopped");
            logger.debug("HttpServer Stopped");
        }

        if (executorContext!=null) {
            executorContext.stop();
        }
    }

    public void handleWebSocketQueueIdle() {
        webSocketIdleConsumer.accept(null);
    }


    public void handleRequestQueueIdle() {
        requestIdleConsumer.accept(null);
    }

    public void handleOpenWebSocket(final WebSocket webSocket) {
        this.webSocketConsumer.accept(webSocket);
    }


    private void defaultWebSocketHandler(final WebSocket webSocket) {


        webSocket.setTextMessageConsumer(webSocketMessageIn -> {

            final WebSocketMessage webSocketMessage = webSocketMessageBuilder()
                    .setMessage(webSocketMessageIn)
                    .setUri(webSocket.uri())
                    .setRemoteAddress(webSocket.remoteAddress())
                    .setTimestamp(Timer.timer().now()).setSender(
                            message -> {
                                if (webSocket.isOpen()) {
                                    webSocket.sendText(message);
                                }

                            }).build();
            handleWebSocketMessage(webSocketMessage);

        });


        webSocket.setBinaryMessageConsumer(webSocketMessageIn -> {

            final WebSocketMessage webSocketMessage = webSocketMessageBuilder()
                    .setMessage(webSocketMessageIn)
                    .setUri(webSocket.uri())
                    .setRemoteAddress(webSocket.remoteAddress())
                    .setTimestamp(Timer.timer().now()).setSender(
                            new WebSocketSender() {
                                @Override
                                public void sendText(String message) {
                                    webSocket.sendBinary(message.getBytes(StandardCharsets.UTF_8));
                                }

                                @Override
                                public void sendBytes(byte[] message) {
                                    webSocket.sendBinary(message);
                                }
                            }

                    ).build();
            handleWebSocketMessage(webSocketMessage);

        });



        webSocket.setCloseConsumer(new Consumer<Void>() {
            @Override
            public void accept(Void aVoid) {

                long time = Timer.timer().now();

                final WebSocketMessage webSocketMessage = webSocketMessageBuilder()

                        .setUri(webSocket.uri())
                        .setRemoteAddress(webSocket.remoteAddress())
                        .setTimestamp(time).build();


                handleWebSocketClosedMessage(webSocketMessage);

            }
        });


        webSocket.setErrorConsumer(new Consumer<Exception>() {
            @Override
            public void accept(Exception e) {
                logger.error("Error with WebSocket handling", e);
            }
        });

    }
}
