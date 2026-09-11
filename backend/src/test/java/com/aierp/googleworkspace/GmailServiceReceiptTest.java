package com.aierp.googleworkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aierp.identity.api.GoogleAccess;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class GmailServiceReceiptTest {
    @Test
    void ageoutLeavesAnAlreadyTerminalReceiptUntouched() {
        var user = UUID.randomUUID();
        var requestId = UUID.randomUUID();
        var row = row(user, requestId, "SENT", Instant.now().minusSeconds(120));
        var sends = mock(MailSendRequestRepository.class);
        when(sends.findById(any())).thenReturn(Optional.of(row));

        var service = new GmailService(mock(GoogleAccess.class), mock(GoogleHttpClient.class), sends);

        assertThat(service.receipt(user, requestId).status()).isEqualTo("SENT");
        verify(sends, never()).saveAndFlush(any());
    }

    @Test
    void quotaResponseIsAnUnknownReceiptAndIsNeverRetried() {
        var fixture = fixture(new GoogleHttpClient.Response(429, "{\"reason\":\"rateLimitExceeded\"}"));

        assertThat(fixture.service.send(fixture.user, fixture.request).status()).isEqualTo("UNKNOWN");
        verify(fixture.http, times(1)).execute(eq("POST"), any(), eq("access"), contains("\"raw\""));
    }

    @Test
    void malformedSuccessfulProviderBodyRemainsUnknown() {
        var fixture = fixture(new GoogleHttpClient.Response(200, "not-json"));

        assertThat(fixture.service.send(fixture.user, fixture.request).status()).isEqualTo("UNKNOWN");
    }

    private static Fixture fixture(GoogleHttpClient.Response response) {
        var user = UUID.randomUUID();
        var requestId = UUID.randomUUID();
        var request = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "Subject", "Body");
        var access = mock(GoogleAccess.class);
        var http = mock(GoogleHttpClient.class);
        var sends = mock(MailSendRequestRepository.class);
        var rows = new ConcurrentHashMap<MailSendRequestEntity.Id, MailSendRequestEntity>();
        when(sends.findById(any())).thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(0))));
        when(sends.insertClaim(any(), any(), any())).thenAnswer(invocation -> {
            var id = new MailSendRequestEntity.Id(invocation.getArgument(0), invocation.getArgument(1));
            var row = row(id.userAccountId, id.requestId, "SENDING", Instant.now());
            return rows.putIfAbsent(id, row) == null ? 1 : 0;
        });
        when(sends.saveAndFlush(any())).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, MailSendRequestEntity.class);
            rows.put(saved.id, saved);
            return saved;
        });
        when(access.credential(user, GoogleAccess.Feature.GMAIL)).thenReturn(new GoogleAccess.Credential("access", 1));
        when(access.isCurrent(user, 1)).thenReturn(true);
        when(access.status(user)).thenReturn(new GoogleAccess.Connection("sender@example.test",
                Map.of(GoogleAccess.Feature.GMAIL, GoogleAccess.Status.CONNECTED)));
        when(http.execute(eq("POST"), any(), eq("access"), contains("\"raw\""))).thenReturn(response);
        return new Fixture(user, request, new GmailService(access, http, sends), http);
    }

    private static MailSendRequestEntity row(UUID user, UUID requestId, String status, Instant updatedAt) {
        var row = new MailSendRequestEntity();
        row.id = new MailSendRequestEntity.Id(user, requestId);
        row.payloadHash = "a".repeat(64);
        row.status = status;
        row.updatedAt = updatedAt;
        row.createdAt = updatedAt;
        return row;
    }

    private record Fixture(UUID user, GmailService.SendRequest request, GmailService service, GoogleHttpClient http) { }
}
