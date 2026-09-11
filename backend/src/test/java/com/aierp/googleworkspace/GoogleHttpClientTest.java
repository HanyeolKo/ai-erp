package com.aierp.googleworkspace;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import com.aierp.platform.web.ExternalServiceFailure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleHttpClientTest {
    @Test
    void realTransportBoundsAnOversizedResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/body", exchange -> {
            byte[] body = new byte[128];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = local(server, Duration.ofSeconds(2), 32);
            assertThatThrownBy(() -> client.execute("GET", endpoint(server, "/body"), "token", null))
                    .isInstanceOf(ExternalServiceFailure.class)
                    .hasMessage("GOOGLE_RESPONSE_TOO_LARGE");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void realTransportAppliesOneDeadlineToAStalledBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write('a');
            exchange.getResponseBody().flush();
            try { Thread.sleep(1_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var started = System.nanoTime();
            var client = local(server, Duration.ofMillis(100), 1_000);
            assertThatThrownBy(() -> client.execute("GET", endpoint(server, "/stall"), "token", null))
                    .isInstanceOf(ExternalServiceFailure.class)
                    .hasMessage("GOOGLE_PROVIDER_UNAVAILABLE");
            assertThat(System.nanoTime() - started).isLessThan(TimeUnit.MILLISECONDS.toNanos(700));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerFailuresExposeSafeDistinctCategories() {
        assertThatThrownBy(() -> GoogleHttpClient.requireSuccess(new GoogleHttpClient.Response(401, "secret")))
                .isInstanceOf(ExternalServiceFailure.class).hasMessage("GOOGLE_REAUTH_REQUIRED")
                .extracting(e -> ((ExternalServiceFailure) e).category())
                .isEqualTo(ExternalServiceFailure.Category.REAUTH_REQUIRED);
        assertThatThrownBy(() -> GoogleHttpClient.requireSuccess(new GoogleHttpClient.Response(403, "accessNotConfigured")))
                .hasMessage("GOOGLE_API_DISABLED");
        assertThatThrownBy(() -> GoogleHttpClient.requireSuccess(new GoogleHttpClient.Response(403, "insufficientPermissions")))
                .hasMessage("GOOGLE_PERMISSION_REQUIRED");
        assertThatThrownBy(() -> GoogleHttpClient.requireSuccess(new GoogleHttpClient.Response(429, "quotaExceeded")))
                .hasMessage("GOOGLE_QUOTA_EXCEEDED");
    }

    private static GoogleHttpClient.Default local(HttpServer server, Duration deadline, int limit) {
        return new GoogleHttpClient.Default(HttpClient.newHttpClient(), deadline, limit, Set.of("127.0.0.1"));
    }

    private static java.net.URI endpoint(HttpServer server, String path) {
        return java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
