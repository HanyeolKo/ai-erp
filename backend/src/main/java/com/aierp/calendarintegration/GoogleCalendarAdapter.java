package com.aierp.calendarintegration;

import com.aierp.identity.api.GoogleAccess;
import com.aierp.schedule.api.ScheduleLookup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Google Calendar v3 adapter. The transport is injectable so tests use a localhost HTTP double. */
@Component
@ConditionalOnProperty(name = "APP_GOOGLE_WORKSPACE_ENABLED", havingValue = "true")
public final class GoogleCalendarAdapter implements CalendarAdapter {
    private static final String CALENDAR_SCOPE_HOST = "https://www.googleapis.com/calendar/v3";
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private final GoogleAccess access;
    private final HttpTransport transport;
    private final URI baseUri;
    private final JsonMapper mapper;
    private final boolean enabled;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleCalendarAdapter(GoogleAccess access, Environment environment) {
        this(access, new JdkHttpTransport(), URI.create(CALENDAR_SCOPE_HOST),
            JsonMapper.builder().build(), Boolean.parseBoolean(environment.getProperty("APP_GOOGLE_WORKSPACE_ENABLED", "false")));
    }

    GoogleCalendarAdapter(GoogleAccess access, HttpTransport transport, URI baseUri, JsonMapper mapper, boolean enabled) {
        this.access = access;
        this.transport = transport;
        this.baseUri = requireGoogleHost(baseUri, transport);
        this.mapper = mapper;
        this.enabled = enabled;
    }

    /** Package-scoped production-equivalent transport for localhost HTTP acceptance tests. */
    static HttpTransport jdkTransportForTests() {
        return jdkTransportForTests(Duration.ofSeconds(10));
    }
    static HttpTransport jdkTransportForTests(Duration responseDeadline) {
        var jdk = new JdkHttpTransport(responseDeadline);
        return jdk::send;
    }

    @Override public boolean configured() { return enabled && access.configured(); }

    /** Legacy signature cannot carry account/binding/schedule barriers; worker-only delivery is required. */
    @Override public String deliver(CalendarProjectionEntity projection) {
        throw new IllegalStateException("CLAIM_CONTEXT_REQUIRED");
    }

    @Override public CalendarPage calendars(UUID userId, String pageToken) {
        var response = request(userId, "GET", "/users/me/calendarList", query("maxResults", "50", "pageToken", boundedToken(pageToken),
            "fields", "items(id,summary,accessRole),nextPageToken"), null, null);
        var values = new ArrayList<CalendarInfo>();
        for (var item : response.body().path("items")) {
            if (values.size() >= 50) break;
            var role = item.path("accessRole").asText("");
            if (("owner".equals(role) || "writer".equals(role)) && item.hasNonNull("id")) {
                values.add(new CalendarInfo(item.path("id").asText(), truncate(item.path("summary").asText("내 캘린더"), 255)));
            }
        }
        return new CalendarPage(values, boundedTokenOrNull(response.body().path("nextPageToken").asText(null)));
    }

    @Override public void validateWritable(UUID userId, String calendarId) {
        writableCalendar(userId, calendarId);
    }

    @Override public CalendarInfo writableCalendar(UUID userId, String calendarId) {
        var id = validCalendarId(calendarId);
        var item = request(userId, "GET", "/users/me/calendarList/" + encodePath(id), query("fields", "id,summary,accessRole"), null, null).body();
        var role = item.path("accessRole").asText("");
        if (!"owner".equals(role) && !"writer".equals(role)) throw new PermissionRequired();
        return new CalendarInfo(id, truncate(item.path("summary").asText("내 캘린더"), 255));
    }

    @Override public DeliveryResult deliver(UUID userId, String calendarId, ScheduleLookup.CalendarSnapshot schedule, String previousEventId) {
        return deliver(userId, calendarId, schedule, previousEventId, () -> true);
    }

    @Override public DeliveryResult deliver(UUID userId, String calendarId, ScheduleLookup.CalendarSnapshot schedule, String previousEventId, java.util.function.BooleanSupplier guard) {
        if (!guard.getAsBoolean()) throw new StaleClaim();
        var eventId = deterministicEventId(schedule.projectId(), schedule.scheduleId());
        var id = validCalendarId(calendarId);
        var path = "/calendars/" + encodePath(id) + "/events/" + eventId;
        var existing = requestAllowNotFound(userId, path, null, guard);
        if (!guard.getAsBoolean()) throw new StaleClaim();
        if (schedule.cancelled()) {
            if (existing.status() == 404 || existing.status() == 410) return new DeliveryResult(eventId, null);
            if ("cancelled".equalsIgnoreCase(existing.body().path("status").asText())) return new DeliveryResult(eventId, existing.etag());
            verifyOwned(existing.body(), schedule);
            if (!guard.getAsBoolean()) throw new StaleClaim();
            if (existing.etag() == null || existing.etag().isBlank()) throw new PermanentFailure();
            try {
                var deleted = request(userId, "DELETE", path, query("sendUpdates", "none"), null, existing.etag(), guard);
                return new DeliveryResult(eventId, deleted.etag());
            } catch (NotFound gone) { return new DeliveryResult(eventId, null); }
        }
        var body = eventBody(schedule, eventId);
        if (existing.status() == 404 || existing.status() == 410) {
            Response inserted;
            try {
                if (!guard.getAsBoolean()) throw new StaleClaim();
                inserted = request(userId, "POST", "/calendars/" + encodePath(id) + "/events",
                    query("sendUpdates", "none"), body.toString(), null, guard);
            } catch (Conflict conflict) {
                if (!guard.getAsBoolean()) throw new StaleClaim();
                var matching = request(userId, "GET", path, Map.of(), null, null, guard);
                verifyOwned(matching.body(), schedule);
                if (!guard.getAsBoolean()) throw new StaleClaim();
                if (matching.etag() == null || matching.etag().isBlank()) throw new PermanentFailure();
                inserted = request(userId, "PATCH", path, query("sendUpdates", "none"), body.toString(), matching.etag(), guard);
            }
            return new DeliveryResult(eventId, inserted.etag());
        }
        verifyOwned(existing.body(), schedule);
        if (!guard.getAsBoolean()) throw new StaleClaim();
        if (existing.etag() == null || existing.etag().isBlank()) throw new PermanentFailure();
        var updated = request(userId, "PATCH", path, query("sendUpdates", "none"), body.toString(), existing.etag(), guard);
        return new DeliveryResult(eventId, updated.etag());
    }

    /** Stable lower-case base32hex event ID, accepted by the Calendar API and independent of title/time. */
    public static String deterministicEventId(UUID projectId, UUID scheduleId) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                (projectId + ":" + scheduleId).getBytes(StandardCharsets.UTF_8));
            final char[] alphabet = "0123456789abcdefghijklmnopqrstuv".toCharArray();
            var out = new StringBuilder("erp");
            int buffer = 0, bits = 0;
            for (byte value : digest) {
                buffer = (buffer << 8) | (value & 0xff); bits += 8;
                while (bits >= 5) { bits -= 5; out.append(alphabet[(buffer >>> bits) & 31]); }
            }
            if (bits > 0) out.append(alphabet[(buffer << (5 - bits)) & 31]);
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private JsonNode eventBody(ScheduleLookup.CalendarSnapshot schedule, String eventId) {
        var privateProperties = Map.of("aierpProjectId", schedule.projectId().toString(),
            "aierpScheduleId", schedule.scheduleId().toString(), "aierpRevision", Long.toString(schedule.businessRevision()));
        var start = Map.of("dateTime", schedule.startsAt().toString(), "timeZone", "UTC");
        var end = Map.of("dateTime", schedule.endsAt().toString(), "timeZone", "UTC");
        var values = new LinkedHashMap<String,Object>();
        values.put("id", eventId); values.put("summary", truncate(schedule.title(), 1024));
        if (schedule.description() != null) values.put("description", truncate(schedule.description(), 20000));
        values.put("start", start); values.put("end", end);
        values.put("extendedProperties", Map.of("private", privateProperties));
        return mapper.valueToTree(values);
    }

    private void verifyOwned(JsonNode event, ScheduleLookup.CalendarSnapshot schedule) {
        var privateProperties = event.path("extendedProperties").path("private");
        if (!schedule.projectId().toString().equals(privateProperties.path("aierpProjectId").asText())
            || !schedule.scheduleId().toString().equals(privateProperties.path("aierpScheduleId").asText())) {
            throw new PermanentFailure();
        }
        var revision = privateProperties.path("aierpRevision").asLong(-1);
        if (revision > schedule.businessRevision()) throw new PermanentFailure();
    }

    private Response requestAllowNotFound(UUID userId, String path, String body, java.util.function.BooleanSupplier guard) {
        try { return request(userId, "GET", path, Map.of(), body, null, guard); }
        catch (NotFound failure) { return new Response(failure.status, mapper.createObjectNode(), null); }
    }

    private Response request(UUID userId, String method, String path, Map<String,String> query, String body, String etag) {
        return request(userId, method, path, query, body, etag, () -> true);
    }
    private Response request(UUID userId, String method, String path, Map<String,String> query, String body, String etag, java.util.function.BooleanSupplier guard) {
        if (!configured()) throw new ConfigurationRequired();
        var state = access.status(userId).status(GoogleAccess.Feature.CALENDAR);
        if (state == GoogleAccess.Status.REAUTH_REQUIRED) throw new ReauthorizationRequired();
        if (state == GoogleAccess.Status.PERMISSION_REQUIRED) throw new PermissionRequired();
        if (state != GoogleAccess.Status.CONNECTED) throw new ConfigurationRequired();
        var credential = access.credential(userId, GoogleAccess.Feature.CALENDAR);
        if (!guard.getAsBoolean()) throw new StaleClaim();
        var uri = uri(path, query);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + credential.accessToken())
            .header("Accept", "application/json");
        if (etag != null && !etag.isBlank()) request.header("If-Match", etag);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.method(method, HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        try {
            var result = transport.send(request.build());
            if (result.body().getBytes(StandardCharsets.UTF_8).length > MAX_BODY) throw new PermanentFailure();
            var json = result.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(result.body());
            if (result.status() == 401) throw new ReauthorizationRequired();
            if (result.status() == 403) throw new PermissionRequired();
            if (result.status() == 404 || result.status() == 410) throw new NotFound(result.status());
            if (result.status() == 409) throw new Conflict();
            if (result.status() == 429 || result.status() >= 500) throw new TransientFailure();
            if (result.status() < 200 || result.status() >= 300) throw new PermanentFailure();
            return new Response(result.status(), json, result.header("ETag"));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new TransientFailure();
        } catch (IOException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("PROVIDER_RESPONSE_TOO_LARGE")) throw new PermanentFailure();
            throw new TransientFailure();
        }
    }

    private URI uri(String path, Map<String,String> query) {
        var value = baseUri.toString().replaceAll("/$", "") + path;
        if (!query.isEmpty()) value += "?" + query.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).reduce((a,b)->a+"&"+b).orElse("");
        return URI.create(value);
    }
    private static Map<String,String> query(String... values) { var result = new LinkedHashMap<String,String>(); for (int i=0;i+1<values.length;i+=2) if(values[i+1]!=null&&!values[i+1].isBlank()) result.put(values[i],values[i+1]); return result; }
    private static String boundedToken(String value) { if (value == null || value.isBlank()) return null; if (value.length()>512 || value.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) throw new IllegalArgumentException("PAGE_TOKEN_INVALID"); return value; }
    private static String boundedTokenOrNull(String value) { return value == null ? null : boundedToken(value); }
    private static String validCalendarId(String id) { if(id==null || id.isBlank() || id.length()>255 || !id.matches("[A-Za-z0-9._%+@-]+")) throw new IllegalArgumentException("CALENDAR_ID_INVALID"); return id; }
    private static String encodePath(String value) { return encode(value).replace("%40", "@"); }
    private static String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private static String truncate(String value, int max) { if(value==null) return ""; return value.length()<=max?value:value.substring(0,max); }
    private static URI requireGoogleHost(URI uri, HttpTransport transport) { if(uri==null || (!"https".equalsIgnoreCase(uri.getScheme()) && !(transport.getClass() != JdkHttpTransport.class && "http".equalsIgnoreCase(uri.getScheme())))) throw new IllegalArgumentException("CALENDAR_HOST_INVALID"); return uri; }

    public interface HttpTransport { RawResponse send(HttpRequest request) throws IOException, InterruptedException; }
    public record RawResponse(int status, String body, Map<String,String> headers) {
        public String header(String name) { return headers == null ? null : headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(null); }
    }
    private record Response(int status, JsonNode body, String etag) { }
    private static final class NotFound extends RuntimeException { final int status; NotFound(int status) { this.status=status; } }
    private static final class Conflict extends RuntimeException { }
    private static final class JdkHttpTransport implements HttpTransport {
        private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        private final Duration responseDeadline;
        private JdkHttpTransport() { this(Duration.ofSeconds(10)); }
        private JdkHttpTransport(Duration responseDeadline) { this.responseDeadline = responseDeadline; }
        @Override public RawResponse send(HttpRequest request) throws IOException, InterruptedException {
            // One absolute deadline covers header acquisition and the bounded body read. The
            // response-body subscriber is cancellable if either phase stalls.
            var deadline = System.nanoTime() + responseDeadline.toNanos();
            var responseFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> response;
            try {
                response = responseFuture.get(remainingNanos(deadline), TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                responseFuture.cancel(true);
                throw new IOException("PROVIDER_RESPONSE_TIMEOUT", timeout);
            } catch (InterruptedException interrupted) {
                responseFuture.cancel(true);
                throw interrupted;
            } catch (ExecutionException failure) {
                var cause = failure.getCause();
                if (cause instanceof IOException io) throw io;
                throw new IOException("PROVIDER_RESPONSE_READ_FAILED", cause);
            }
            var remaining = remainingNanos(deadline);
            if (remaining <= 0) {
                try { response.body().close(); } catch (IOException ignored) { }
                throw new IOException("PROVIDER_RESPONSE_TIMEOUT");
            }
            var bytes = boundedBytes(response.body(), MAX_BODY, Duration.ofNanos(remaining));
            return new RawResponse(response.statusCode(), new String(bytes, StandardCharsets.UTF_8), response.headers().map().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().findFirst().orElse(""))));
        }
        private static long remainingNanos(long deadline) {
            return Math.max(0, deadline - System.nanoTime());
        }
        private static byte[] boundedBytes(InputStream stream, int max, Duration deadline) throws IOException, InterruptedException {
            // InputStream.read() is blocking and HttpClient's request timeout does not bound the
            // response body. Read it on a daemon worker and close/cancel that worker on the same
            // total deadline, while readLimited enforces the byte cap before allocation grows.
            ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
                var thread = new Thread(task, "google-calendar-response-reader");
                thread.setDaemon(true); return thread;
            });
            Future<byte[]> future = reader.submit(() -> readLimited(stream, max));
            try {
                return future.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                future.cancel(true);
                try { stream.close(); } catch (IOException ignored) { }
                throw new IOException("PROVIDER_RESPONSE_TIMEOUT", timeout);
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                try { stream.close(); } catch (IOException ignored) { }
                throw interrupted;
            } catch (ExecutionException failure) {
                var cause = failure.getCause();
                if (cause instanceof IOException io) throw io;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IOException("PROVIDER_RESPONSE_READ_FAILED", cause);
            } finally {
                reader.shutdownNow();
            }
        }
        private static byte[] readLimited(InputStream stream, int max) throws IOException {
            try (stream) {
                var out = new java.io.ByteArrayOutputStream(Math.min(max, 8192));
                var buffer = new byte[8192]; int total = 0, read;
                while ((read = stream.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    if (read > max - total) throw new IOException("PROVIDER_RESPONSE_TOO_LARGE");
                    total += read; out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        }
    }
}
