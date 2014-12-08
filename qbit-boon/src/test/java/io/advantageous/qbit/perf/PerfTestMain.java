package io.advantageous.qbit.perf;

import io.advantageous.qbit.client.Client;
import io.advantageous.qbit.client.ClientBuilder;
import io.advantageous.qbit.http.HttpClient;
import io.advantageous.qbit.http.HttpRequest;
import io.advantageous.qbit.http.HttpServer;
import io.advantageous.qbit.http.WebSocketMessage;
import io.advantageous.qbit.queue.ReceiveQueueListener;
import io.advantageous.qbit.queue.SendQueue;
import io.advantageous.qbit.queue.impl.BasicQueue;
import io.advantageous.qbit.server.ServiceServer;
import io.advantageous.qbit.server.ServiceServerBuilder;
import io.advantageous.qbit.service.Callback;
import io.advantageous.qbit.spi.FactorySPI;
import io.advantageous.qbit.spi.HttpClientFactory;
import io.advantageous.qbit.spi.HttpServerFactory;
import org.boon.core.Sys;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.boon.Boon.puts;

/**
 * Created by Richard on 12/7/14.
 */
public class PerfTestMain {



    static BasicQueue<WebSocketMessage> messages = new BasicQueue<>("websocket sim", 5, TimeUnit.MILLISECONDS, 5);



    static class MockHttpServer implements HttpServer {

        private Consumer<WebSocketMessage> webSocketMessageConsumer;
        private Consumer<HttpRequest> httpRequestConsumer;


        @Override
        public void setWebSocketMessageConsumer(Consumer<WebSocketMessage> webSocketMessageConsumer) {

            this.webSocketMessageConsumer = webSocketMessageConsumer;
        }

        @Override
        public void setHttpRequestConsumer(Consumer<HttpRequest> httpRequestConsumer) {

            this.httpRequestConsumer = httpRequestConsumer;
        }

        @Override
        public void start() {

            messages.startListener(new ReceiveQueueListener<WebSocketMessage>() {
                @Override
                public void receive(WebSocketMessage item) {

                    webSocketMessageConsumer.accept(item);
                }

                @Override
                public void empty() {

                }

                @Override
                public void limit() {

                }

                @Override
                public void shutdown() {

                }

                @Override
                public void idle() {

                }
            });
        }

        @Override
        public void stop() {

        }
    }
    static class MockHttpClient implements HttpClient {

        Consumer<Void> periodicFlushCallback;

        SendQueue<WebSocketMessage> sendQueue;

        Thread thread;

        ReentrantLock lock = new ReentrantLock();


        @Override
        public void sendHttpRequest(HttpRequest request) {

        }

        @Override
        public void sendWebSocketMessage(WebSocketMessage webSocketMessage) {

            try {
                lock.lock();

                sendQueue.send(webSocketMessage);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void periodicFlushCallback(Consumer<Void> periodicFlushCallback) {

            this.periodicFlushCallback = periodicFlushCallback;
        }

        @Override
        public void run() {
            sendQueue = messages.sendQueue();

             thread = new Thread(new Runnable() {
                @Override
                public void run() {

                    while(true) {
                        Sys.sleep(100);

                        periodicFlushCallback.accept(null);
                        sendQueue.flushSends();

                        if (thread.isInterrupted()) {
                            break;
                        }
                    }
                }
            });
            thread.start();

        }

        @Override
        public void flush() {

            periodicFlushCallback.accept(null);
        }

        @Override
        public void stop() {

            thread.interrupt();
        }
    }

    public static void main(String... args){

        FactorySPI.setHttpClientFactory(new HttpClientFactory() {
            @Override
            public HttpClient create(String host, int port, int pollTime, int requestBatchSize, int timeOutInMilliseconds, int poolSize, boolean autoFlush) {
                return new MockHttpClient();
            }
        });

        FactorySPI.setHttpServerFactory(new HttpServerFactory() {
            @Override
            public HttpServer create(String host, int port, boolean manageQueues, int pollTime, int requestBatchSize, int flushInterval) {
                return new MockHttpServer();
            }
        });



        ServiceServer server = new ServiceServerBuilder().setRequestBatchSize(500).build();
        server.initServices(new AdderService());
        server.start();


        puts("Server started");



        Client client = new ClientBuilder().setPollTime(10)
                .setAutoFlush(true).setFlushInterval(100).setRequestBatchSize(5).build();

        AdderClientInterface adder = client.createProxy(AdderClientInterface.class, "adderservice");

        client.run();


        puts("Client started");

        final long startTime = System.currentTimeMillis();

        for (int index = 0; index < 8_000_000; index++) {
            adder.add("name", 1);

            final int runNum = index;


            if (index % 200_000 == 0 ) {
                adder.sum(new Callback<Integer>() {
                    @Override
                    public void accept(Integer integer) {


                        final long endTime = System.currentTimeMillis();
                        puts("sum " + runNum, " SUM ", integer, "time", endTime - startTime);
                    }
                });
            }
        }

        client.flush();

        adder.sum(new Callback<Integer>() {
            @Override
            public void accept(Integer integer) {


                final long endTime = System.currentTimeMillis();
                puts("sum", integer, "time", endTime - startTime);
            }
        });


        adder.sum(new Callback<Integer>() {
            @Override
            public void accept(Integer integer) {


                final long endTime = System.currentTimeMillis();
                puts("sum", integer, "time", endTime - startTime);
            }
        });


        adder.sum(new Callback<Integer>() {
            @Override
            public void accept(Integer integer) {


                final long endTime = System.currentTimeMillis();
                puts("sum", integer, "time", endTime - startTime);
            }
        });



        adder.sum(new Callback<Integer>() {
            @Override
            public void accept(Integer integer) {


                final long endTime = System.currentTimeMillis();
                puts("FINAL 1 sum", integer, "time", endTime - startTime);

            }
        });
        adder.sum(new Callback<Integer>() {
            @Override
            public void accept(Integer integer) {


                final long endTime = System.currentTimeMillis();
                puts("FINAL 2 sum", integer, "time", endTime - startTime);
            }
        });


        client.flush();



        Sys.sleep(200_000);


        client.stop();
    }
}