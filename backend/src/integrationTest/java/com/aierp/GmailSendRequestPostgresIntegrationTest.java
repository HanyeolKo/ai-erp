package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aierp.googleworkspace.GmailService;
import com.aierp.googleworkspace.GoogleHttpClient;
import com.aierp.googleworkspace.MailSendRequestEntity;
import com.aierp.googleworkspace.MailSendRequestRepository;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.GoogleAuthorizationService;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real Gmail service and repository against PostgreSQL. In-memory
 * fixtures cannot exercise ON CONFLICT waiting, row locks, or terminal races.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class GmailSendRequestPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2.9"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/integration-migration");
    }

    @Autowired GmailService service;
    @Autowired MailSendRequestRepository sends;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GoogleAuthorizationService access;
    @MockitoBean GoogleHttpClient http;
    private UUID user;

    @BeforeEach
    void prepare() {
        sends.deleteAll();
        user = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",
                user, "gmail-%s@example.test".formatted(user), "Gmail Test");
        when(access.credential(user, GoogleAccess.Feature.GMAIL)).thenReturn(new GoogleAccess.Credential("access", 1));
        when(access.isCurrent(user, 1)).thenReturn(true);
        when(access.isCurrentForCommit(user, 1)).thenReturn(true);
        when(access.status(user)).thenReturn(new GoogleAccess.Connection("sender@example.test",
                Map.of(GoogleAccess.Feature.GMAIL, GoogleAccess.Status.CONNECTED)));
    }

    @Test
    void concurrentSameKeyProducesOneProviderPostAndOneFinalReceipt() throws Exception {
        var requestId = UUID.randomUUID();
        var request = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "Subject", "Body");
        var posts = new AtomicInteger();
        var providerEntered = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        when(http.execute(eq("POST"), any(), eq("access"), contains("\"raw\""))).thenAnswer(invocation -> {
            posts.incrementAndGet();
            providerEntered.countDown();
            releaseProvider.await(20, TimeUnit.SECONDS);
            return new GoogleHttpClient.Response(200, "{\"id\":\"provider-1\"}");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.send(user, request));
            assertThat(providerEntered.await(20, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> service.send(user, request));
            assertThat(second.get(20, TimeUnit.SECONDS).status()).isEqualTo("SENDING");
            releaseProvider.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).status()).isEqualTo("SENT");
        } finally {
            releaseProvider.countDown();
        }
        assertThat(posts).hasValue(1);
        assertThat(service.receipt(user, requestId).status()).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_workspace.mail_send_request WHERE user_account_id=? AND request_id=?", Integer.class, user, requestId)).isEqualTo(1);
    }

    @Test
    void staleGrantCannotCommitSuccessAfterProviderReturns() {
        var requestId = UUID.randomUUID();
        var request = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "Subject", "Body");
        when(http.execute(eq("POST"), any(), eq("access"), contains("\"raw\"")))
                .thenReturn(new GoogleHttpClient.Response(200, "{\"id\":\"provider-2\"}"));
        when(access.isCurrentForCommit(user, 1)).thenReturn(false);

        assertThat(service.send(user, request).status()).isEqualTo("UNKNOWN");
        assertThat(service.receipt(user, requestId).status()).isEqualTo("UNKNOWN");
        verify(http, times(1)).execute(eq("POST"), any(), eq("access"), contains("\"raw\""));
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejectedWithoutOverwritingTheClaim() {
        var requestId = UUID.randomUUID();
        var original = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "Subject", "Body");
        var mismatch = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "Changed", "Body");
        when(http.execute(eq("POST"), any(), eq("access"), contains("\"raw\"")))
                .thenReturn(new GoogleHttpClient.Response(200, "{\"id\":\"provider-3\"}"));

        assertThat(service.send(user, original).status()).isEqualTo("SENT");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(user, mismatch))
                .isInstanceOf(IllegalStateException.class).hasMessage("REQUEST_ID_PAYLOAD_MISMATCH");
        verify(http, times(1)).execute(eq("POST"), any(), eq("access"), contains("\"raw\""));
        assertThat(service.receipt(user, requestId).status()).isEqualTo("SENT");
    }

    @Test
    void credentialFailureBeforeProviderAttemptStaysDefinitiveFailed() {
        var requestId = UUID.randomUUID();
        var request = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "Subject", "Body");
        when(access.credential(user, GoogleAccess.Feature.GMAIL))
                .thenThrow(new GoogleAccess.GoogleAccessException(GoogleAccess.Status.REAUTH_REQUIRED));

        assertThat(service.send(user, request).status()).isEqualTo("FAILED");
        assertThat(service.receipt(user, requestId).status()).isEqualTo("FAILED");
        verifyNoInteractions(http);
    }

    @Test
    void ageoutIsTerminalAndDoesNotRegressAfterLateFinish() throws Exception {
        var requestId = UUID.randomUUID();
        var request = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "Subject", "Body");
        var providerEntered = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        when(http.execute(eq("POST"), any(), eq("access"), contains("\"raw\""))).thenAnswer(invocation -> {
            providerEntered.countDown();
            releaseProvider.await(20, TimeUnit.SECONDS);
            return new GoogleHttpClient.Response(200, "{\"id\":\"provider-late\"}");
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var sending = pool.submit(() -> service.send(user, request));
            assertThat(providerEntered.await(20, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE google_workspace.mail_send_request SET updated_at=? WHERE user_account_id=? AND request_id=?",
                    Timestamp.from(Instant.now().minusSeconds(61)), user, requestId);
            assertThat(service.receipt(user, requestId).status()).isEqualTo("UNKNOWN");
            releaseProvider.countDown();
            assertThat(sending.get(20, TimeUnit.SECONDS).status()).isEqualTo("UNKNOWN");
        } finally {
            releaseProvider.countDown();
        }
        assertThat(service.receipt(user, requestId).status()).isEqualTo("UNKNOWN");
    }
}
