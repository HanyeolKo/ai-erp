package com.aierp.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;

class GoogleRefreshTransportTest {
    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); if (executor != null) executor.shutdownNow(); }

    @Test
    void localProviderBodyIsBoundedBeforeJsonParsing() throws Exception {
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/token", exchange -> {
            byte[] body=new byte[1_000_001];
            exchange.sendResponseHeaders(200,body.length);
            try (var output=exchange.getResponseBody()) { output.write(body); }
        });
        executor=Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
        var env=new MockEnvironment().withProperty("GOOGLE_CLIENT_ID","client").withProperty("GOOGLE_CLIENT_SECRET","secret");
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var transport=new GoogleTokenRefreshTransportClient(env,client,new URI("http://localhost:"+server.getAddress().getPort()+"/token"));

        var result=transport.refresh("refresh");

        assertEquals(502,result.status());
        assertNull(result.accessToken());
    }

    @Test
    void localProviderThatStopsAfterHeadersIsCancelledByTotalDeadline() throws Exception {
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/token", exchange -> {
            exchange.sendResponseHeaders(200,0);
            try (var output=exchange.getResponseBody()) {
                output.write('{'); output.flush();
                Thread.sleep(30_000);
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        executor=Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        var env=new MockEnvironment().withProperty("GOOGLE_CLIENT_ID","client").withProperty("GOOGLE_CLIENT_SECRET","secret");
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var transport=new GoogleTokenRefreshTransportClient(env,client,new URI("http://localhost:"+server.getAddress().getPort()+"/token"));

        long started=System.nanoTime();
        var result=transport.refresh("refresh");
        long elapsed=Duration.ofNanos(System.nanoTime()-started).toSeconds();

        assertEquals(0,result.status());
        assertTrue(elapsed <= 12,"body read exceeded total deadline: "+elapsed+"s");
    }
}
