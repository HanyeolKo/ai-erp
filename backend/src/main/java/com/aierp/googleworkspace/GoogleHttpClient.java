package com.aierp.googleworkspace;

import com.aierp.platform.web.ExternalServiceFailure;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/** Fixed-host Google transport. Tests may explicitly allow a loopback host. */
public interface GoogleHttpClient {
    Response execute(String method, URI uri, String accessToken, String body);
    record Response(int status, String body) {}

    /**
     * The provider exchange has one deadline, including response headers and every byte of
     * the response body. Body bytes are accumulated only up to {@code maxResponseBytes}.
     * The default allowlist deliberately excludes loopback; tests use the explicit overload.
     */
    @Component
    final class Default implements GoogleHttpClient {
        public static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(10);
        public static final int DEFAULT_MAX_RESPONSE_BYTES = 2_000_000;
        private static final Set<String> GOOGLE_HOSTS = Set.of("www.googleapis.com", "gmail.googleapis.com");
        private static final java.util.concurrent.ThreadFactory DAEMON_THREADS = runnable -> {
            var thread = new Thread(runnable, "google-http-body");
            thread.setDaemon(true);
            return thread;
        };

        private final HttpClient client;
        private final Duration deadline;
        private final int maxResponseBytes;
        private final Set<String> allowedHosts;
        private final ExecutorService bodyReader;

        public Default() {
            this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(DEFAULT_DEADLINE).build(), DEFAULT_DEADLINE,
                    DEFAULT_MAX_RESPONSE_BYTES, GOOGLE_HOSTS);
        }

        public Default(Duration deadline, int maxResponseBytes) {
            this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(deadline).build(), deadline, maxResponseBytes, GOOGLE_HOSTS);
        }

        /** Constructor for tests using a real local HTTP server. */
        public Default(HttpClient client, Duration deadline, int maxResponseBytes, Set<String> allowedHosts) {
            if (client == null || deadline == null || deadline.isZero() || deadline.isNegative())
                throw new IllegalArgumentException("GOOGLE_HTTP_DEADLINE_INVALID");
            if (maxResponseBytes <= 0) throw new IllegalArgumentException("GOOGLE_RESPONSE_LIMIT_INVALID");
            if (allowedHosts == null || allowedHosts.isEmpty()) throw new IllegalArgumentException("GOOGLE_HOST_ALLOWLIST_REQUIRED");
            this.client = client;
            this.deadline = deadline;
            this.maxResponseBytes = maxResponseBytes;
            this.allowedHosts = allowedHosts.stream().filter(java.util.Objects::nonNull)
                    .map(host -> host.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
            this.bodyReader = Executors.newCachedThreadPool(DAEMON_THREADS);
        }

        public Default(HttpClient client, Duration deadline, int maxResponseBytes) {
            this(client, deadline, maxResponseBytes, GOOGLE_HOSTS);
        }

        public Default(Duration deadline, int maxResponseBytes, Set<String> allowedHosts) {
            this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(deadline).build(), deadline, maxResponseBytes, allowedHosts);
        }

        @Override
        public Response execute(String method, URI uri, String token, String body) {
            validateUri(uri);
            if (token == null || token.isBlank()) throw new GoogleServiceException("GOOGLE_REAUTH_REQUIRED");
            long expiresAt = System.nanoTime() + deadline.toNanos();
            CompletableFuture<HttpResponse<InputStream>> pending = null;
            try {
                var builder = HttpRequest.newBuilder(uri)
                        .timeout(deadline)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json");
                if (body != null) builder.header("Content-Type", "application/json");
                var request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
                pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
                HttpResponse<InputStream> result = await(pending, remainingNanos(expiresAt));
                String responseBody;
                InputStream input = result.body();
                try {
                    responseBody = readBody(input, remainingNanos(expiresAt));
                } catch (Exception failure) {
                    closeAsync(input);
                    throw failure;
                }
                closeAsync(input);
                // Keep status and the bounded body available to callers. They decide whether
                // a 404 is an ignorable item miss or a request failure via requireSuccess.
                return new Response(result.statusCode(), responseBody);
            } catch (GoogleServiceException failure) {
                throw failure;
            } catch (TimeoutException timeout) {
                if (pending != null) pending.cancel(true);
                throw new GoogleServiceException("GOOGLE_PROVIDER_UNAVAILABLE", timeout);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new GoogleServiceException("GOOGLE_PROVIDER_UNAVAILABLE", interrupted);
            } catch (Exception failure) {
                throw new GoogleServiceException("GOOGLE_PROVIDER_UNAVAILABLE", failure);
            }
        }

        private String readBody(InputStream input, long timeoutNanos) throws Exception {
            Future<String> read = bodyReader.submit(() -> {
                var output = new ByteArrayOutputStream(Math.min(maxResponseBytes, 16 * 1024));
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > maxResponseBytes - total) throw new GoogleServiceException("GOOGLE_RESPONSE_TOO_LARGE");
                    output.write(buffer, 0, count);
                    total += count;
                }
                return output.toString(java.nio.charset.StandardCharsets.UTF_8);
            });
            try {
                return read.get(timeoutNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                read.cancel(true);
                throw timeout;
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception exception) throw exception;
                throw failure;
            }
        }

        private static <T> T await(CompletableFuture<T> pending, long timeoutNanos)
                throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return pending.get(timeoutNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                pending.cancel(true);
                throw timeout;
            }
        }

        private long remainingNanos(long expiresAt) throws TimeoutException {
            long remaining = expiresAt - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("GOOGLE_HTTP_DEADLINE");
            return remaining;
        }

        // A broken provider stream is allowed to make close block. Never let cleanup
        // extend the caller-visible exchange deadline.
        private void closeAsync(InputStream input) {
            if (input == null) return;
            bodyReader.execute(() -> { try { input.close(); } catch (Exception ignored) { } });
        }

        private void validateUri(URI uri) {
            if (uri == null || uri.getHost() == null || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT)))
                throw new GoogleServiceException("GOOGLE_PROVIDER_UNAVAILABLE");
            // Loopback is only accepted by the explicit test constructor. Production Google calls remain HTTPS.
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !isLoopback(uri.getHost()))
                throw new GoogleServiceException("GOOGLE_PROVIDER_UNAVAILABLE");
        }

        private static boolean isLoopback(String host) {
            return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1");
        }

        private static GoogleServiceException classify(Response response) {
            int status = response.status();
            String body = response.body() == null ? "" : response.body().toLowerCase(Locale.ROOT);
            if (status == 401) return new GoogleServiceException("GOOGLE_REAUTH_REQUIRED");
            if (status >= 500) return new GoogleServiceException("GOOGLE_PROVIDER_UNAVAILABLE");
            if (status == 429) return new GoogleServiceException("GOOGLE_QUOTA_EXCEEDED");
            if (status == 403 && containsAny(body, "ratelimitexceeded", "userratelimitexceeded", "dailylimitexceeded", "quotaexceeded", "quota exceeded"))
                return new GoogleServiceException("GOOGLE_QUOTA_EXCEEDED");
            if (status == 403 && containsAny(body, "accessnotconfigured", "api_disabled", "api disabled", "has not been used"))
                return new GoogleServiceException("GOOGLE_API_DISABLED");
            if (status == 403)
                return new GoogleServiceException("GOOGLE_PERMISSION_REQUIRED");
            return new GoogleServiceException("GOOGLE_PROVIDER_UNAVAILABLE");
        }

        private static boolean containsAny(String value, String... needles) {
            for (String needle : needles) if (value.contains(needle)) return true;
            return false;
        }
    }

    /** Shared safe response checker for Gmail, Drive, and Calendar adapters. */
    static void requireSuccess(Response response) {
        if (response == null) throw new GoogleServiceException("GOOGLE_INVALID_RESPONSE");
        if (response.status() >= 200 && response.status() < 300) return;
        throw Default.classify(response);
    }

    class GoogleServiceException extends ExternalServiceFailure {
        public GoogleServiceException(String code) { this(code, null); }
        public GoogleServiceException(String code, Throwable cause) {
            super(category(code), canonical(code));
            if (cause != null) initCause(cause);
        }
        private static String canonical(String code) {
            return switch (code) {
                case "PROVIDER_UNAVAILABLE", "GOOGLE_PROVIDER_UNAVAILABLE", "PROVIDER_RESPONSE_TIMEOUT" -> "GOOGLE_PROVIDER_UNAVAILABLE";
                case "PROVIDER_RESPONSE_TOO_LARGE", "GOOGLE_RESPONSE_TOO_LARGE" -> "GOOGLE_RESPONSE_TOO_LARGE";
                case "PROVIDER_INVALID_RESPONSE", "GOOGLE_INVALID_RESPONSE" -> "GOOGLE_INVALID_RESPONSE";
                case "GOOGLE_PERMISSION_REQUIRED", "GOOGLE_REAUTH_REQUIRED", "GOOGLE_API_DISABLED", "GOOGLE_QUOTA_EXCEEDED" -> code;
                default -> "EXTERNAL_SERVICE_FAILURE";
            };
        }
        private static Category category(String code) {
            return switch (canonical(code)) {
                case "GOOGLE_REAUTH_REQUIRED" -> Category.REAUTH_REQUIRED;
                case "GOOGLE_PERMISSION_REQUIRED" -> Category.PERMISSION_REQUIRED;
                case "GOOGLE_API_DISABLED" -> Category.CONFIGURATION_REQUIRED;
                case "GOOGLE_INVALID_RESPONSE" -> Category.DEFINITIVE;
                default -> Category.TEMPORARY;
            };
        }
    }
}
