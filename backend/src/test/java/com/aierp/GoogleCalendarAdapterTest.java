package com.aierp.calendarintegration;

import com.aierp.calendarintegration.CalendarAdapter;
import com.aierp.calendarintegration.GoogleCalendarAdapter;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.schedule.api.ScheduleLookup;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GoogleCalendarAdapterTest {
    final UUID user = UUID.randomUUID();
    final GoogleAccess access = mock(GoogleAccess.class);
    final ArrayDeque<GoogleCalendarAdapter.RawResponse> responses = new ArrayDeque<>();
    final ArrayDeque<HttpRequest> requests = new ArrayDeque<>();
    final GoogleCalendarAdapter.HttpTransport transport = request -> { requests.add(request); return responses.remove(); };
    final GoogleCalendarAdapter adapter = new GoogleCalendarAdapter(access, transport, URI.create("http://localhost:18765/calendar/v3"), JsonMapper.builder().build(), true);

    GoogleCalendarAdapterTest() {
        when(access.configured()).thenReturn(true);
        when(access.status(user)).thenReturn(new GoogleAccess.Connection("owner@example.test", Map.of(GoogleAccess.Feature.CALENDAR, GoogleAccess.Status.CONNECTED)));
        when(access.credential(user, GoogleAccess.Feature.CALENDAR)).thenReturn(new GoogleAccess.Credential("test-token", 7));
        when(access.isCurrent(user, 7)).thenReturn(true);
    }

    @Test void listFiltersWritableCalendarsAndCapsProviderPage() {
        responses.add(new GoogleCalendarAdapter.RawResponse(200, "{\"items\":[{\"id\":\"a@example.com\",\"summary\":\"A\",\"accessRole\":\"owner\"},{\"id\":\"b\",\"summary\":\"B\",\"accessRole\":\"writer\"},{\"id\":\"c\",\"summary\":\"C\",\"accessRole\":\"reader\"}],\"nextPageToken\":\"opaque token\"}", Map.of()));
        var page = adapter.calendars(user, null);
        assertThat(page.calendars()).extracting(CalendarAdapter.CalendarInfo::id).containsExactly("a@example.com", "b");
        assertThat(page.nextPageToken()).isEqualTo("opaque token");
        assertThat(requests.remove().uri().toString()).contains("maxResults=50");
    }

    @Test void localhostJdkHttpDoubleExercisesRealRequestPath() throws Exception {
        var server = HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/calendar/v3/users/me/calendarList", exchange -> {
            var body = "{\"items\":[{\"id\":\"primary\",\"summary\":\"Primary\",\"accessRole\":\"owner\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var local = new GoogleCalendarAdapter(access, GoogleCalendarAdapter.jdkTransportForTests(),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/calendar/v3"), JsonMapper.builder().build(), true);
            assertThat(local.calendars(user, null).calendars()).extracting(CalendarAdapter.CalendarInfo::id).containsExactly("primary");
        } finally { server.stop(0); }
    }

    @Test void insertUsesStableIdAndNoAttendeesOrNotifications() {
        var s = snapshot("CONFIRMED", 3);
        responses.add(new GoogleCalendarAdapter.RawResponse(404, "", Map.of()));
        responses.add(new GoogleCalendarAdapter.RawResponse(200, "{\"id\":\"provider-id\",\"etag\":\"v1\"}", Map.of("ETag", "v1")));
        var result = adapter.deliver(user, "primary", s, null);
        var get = requests.remove(); var post = requests.remove();
        assertThat(result.eventId()).isEqualTo(GoogleCalendarAdapter.deterministicEventId(s.projectId(), s.scheduleId()));
        assertThat(post.uri().toString()).contains("sendUpdates=none");
        assertThat(post.headers().firstValue("Authorization")).contains("Bearer test-token");
        assertThat(post.uri().toString()).doesNotContain("test-token");
    }

    @Test void localhostJdkTransportDoesNotFollowRedirects() throws Exception {
        var server = HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/calendar/v3/users/me/calendarList", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://localhost:1/redirected");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        try {
            var local = new GoogleCalendarAdapter(access, GoogleCalendarAdapter.jdkTransportForTests(),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/calendar/v3"), JsonMapper.builder().build(), true);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> local.calendars(user, null))
                .isInstanceOf(CalendarAdapter.PermanentFailure.class);
        } finally { server.stop(0); }
    }

    @Test void localhostJdkTransportRejectsOversizedBodyBeforeJsonAllocation() throws Exception {
        var server = HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/calendar/v3/users/me/calendarList", exchange -> {
            var body = new byte[2 * 1024 * 1024 + 1];
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var local = new GoogleCalendarAdapter(access, GoogleCalendarAdapter.jdkTransportForTests(),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/calendar/v3"), JsonMapper.builder().build(), true);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> local.calendars(user, null))
                .isInstanceOf(CalendarAdapter.PermanentFailure.class);
        } finally { server.stop(0); }
    }

    @Test void localhostJdkTransportCancelsAStalledBodyReadAtDeadline() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/calendar/v3/users/me/calendarList", exchange -> {
            try { Thread.sleep(75); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.sendResponseHeaders(200, 1);
            try { release.await(2, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var local = new GoogleCalendarAdapter(access, GoogleCalendarAdapter.jdkTransportForTests(java.time.Duration.ofMillis(100)),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/calendar/v3"), JsonMapper.builder().build(), true);
            var started = System.nanoTime();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> local.calendars(user, null))
                .isInstanceOf(CalendarAdapter.TransientFailure.class);
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(2000);
        } finally { release.countDown(); server.stop(0); }
    }

    @Test void cancelled404NeverCreatesAnEvent() {
        var s = snapshot("CANCELLED", 4);
        responses.add(new GoogleCalendarAdapter.RawResponse(404, "", Map.of()));
        var result = adapter.deliver(user, "primary", s, null);
        assertThat(result.eventId()).isEqualTo(GoogleCalendarAdapter.deterministicEventId(s.projectId(), s.scheduleId()));
        assertThat(requests).hasSize(1);
    }

    @Test void cancelled410IsDurableSuccessWithoutInsert() {
        var s = snapshot("CANCELLED", 6);
        responses.add(new GoogleCalendarAdapter.RawResponse(410, "", Map.of()));
        adapter.deliver(user, "primary", s, null);
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().method()).isEqualTo("GET");
    }

    @Test void cancellationAuditRemovesLateConfirmedInsertAfterInitial404() {
        var confirmed = snapshot("CONFIRMED", 2); // the request that may finish after its lease
        var cancelled = new ScheduleLookup.CalendarSnapshot(confirmed.scheduleId(), confirmed.projectId(), confirmed.title(),
            confirmed.description(), confirmed.startsAt(), confirmed.endsAt(), "CANCELLED", 3);
        // Cancellation first observes absence and never creates an event.
        responses.add(new GoogleCalendarAdapter.RawResponse(404, "", Map.of()));
        adapter.deliver(user, "primary", cancelled, null);
        assertThat(requests).hasSize(1); assertThat(requests.peek().method()).isEqualTo("GET");
        // The superseded confirmed request then completes with an insert.
        responses.add(new GoogleCalendarAdapter.RawResponse(404, "", Map.of()));
        responses.add(new GoogleCalendarAdapter.RawResponse(200, "{\"id\":\"late\",\"etag\":\"late-v1\"}", Map.of("ETag", "late-v1")));
        adapter.deliver(user, "primary", confirmed, null);
        // A later bounded cancellation audit finds the owned event and deletes conditionally.
        responses.add(new GoogleCalendarAdapter.RawResponse(200, "{\"status\":\"confirmed\",\"extendedProperties\":{"
            + "\"private\":{\"aierpProjectId\":\"" + confirmed.projectId() + "\",\"aierpScheduleId\":\"" + confirmed.scheduleId() + "\",\"aierpRevision\":\"2\"}}}", Map.of("ETag", "late-v1")));
        responses.add(new GoogleCalendarAdapter.RawResponse(204, "", Map.of()));
        adapter.deliver(user, "primary", cancelled, "late");
        assertThat(requests).extracting(HttpRequest::method).containsExactly("GET", "GET", "POST", "GET", "DELETE");
        assertThat(requests.peekLast().headers().firstValue("If-Match")).contains("late-v1");
    }

    @Test void updateChecksOwnershipAndUsesEtag() {
        var s = snapshot("CONFIRMED", 5);
        responses.add(new GoogleCalendarAdapter.RawResponse(200, "{\"id\":\"x\",\"extendedProperties\":{\"private\":{\"aierpProjectId\":\"" + s.projectId() + "\",\"aierpScheduleId\":\"" + s.scheduleId() + "\"}}}", Map.of("ETag", "old")));
        responses.add(new GoogleCalendarAdapter.RawResponse(200, "{\"id\":\"x\"}", Map.of("ETag", "new")));
        adapter.deliver(user, "primary", s, null);
        requests.remove();
        assertThat(requests.remove().headers().firstValue("If-Match")).contains("old");
    }

    private ScheduleLookup.CalendarSnapshot snapshot(String status, long revision) {
        return new ScheduleLookup.CalendarSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Title", "Description",
            java.time.Instant.parse("2026-09-07T10:00:00Z"), java.time.Instant.parse("2026-09-07T11:00:00Z"), status, revision);
    }
}
