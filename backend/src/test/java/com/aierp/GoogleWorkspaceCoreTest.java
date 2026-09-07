package com.aierp;

import com.aierp.googleworkspace.*;
import com.aierp.identity.GoogleTokenVault;
import com.aierp.identity.GoogleAuthorizationEntity;
import com.aierp.identity.GoogleAuthorizationRepository;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.GoogleAuthorizationService;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.googleworkspace.api.GmailController;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GoogleWorkspaceCoreTest {
    @Test void gmailControllerExposesSafeArrayAndIsoDtoShape() throws Exception {
        var access = mock(GoogleAccess.class);
        var transport = mock(GoogleHttpClient.class);
        var user = UUID.randomUUID();
        when(access.credential(user, GoogleAccess.Feature.GMAIL)).thenReturn(new GoogleAccess.Credential("access", 8));
        when(access.isCurrent(user, 8)).thenReturn(true);
        when(transport.execute(eq("GET"), any(java.net.URI.class), eq("access"), isNull())).thenAnswer(invocation -> {
            String uri = invocation.getArgument(1).toString();
            if (uri.contains("format=metadata")) return new GoogleHttpClient.Response(200, "{\"snippet\":\"hello\",\"internalDate\":1700000000000,\"payload\":{\"headers\":[{\"name\":\"To\",\"value\":\"one@example.test, two@example.test\"}]}}");
            return new GoogleHttpClient.Response(200, "{\"messages\":[{\"id\":\"m1\"}],\"nextPageToken\":null}");
        });
        var controller = new GmailController(new GmailService(access, transport, mock(MailSendRequestRepository.class)));
        var authentication = new TestingAuthenticationToken(new ApplicationPrincipal(user, "user@example.test", true), "");
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/v1/google/mail/messages").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].to[0]").value("one@example.test"))
                .andExpect(jsonPath("$.messages[0].to[1]").value("two@example.test"))
                .andExpect(jsonPath("$.messages[0].internalDate").value("2023-11-14T22:13:20Z"));
    }

    @Test void driveFilesConsumesARealLocalHttpProviderAndKeepsTwentyRows() throws Exception {
        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/drive", exchange -> {
            StringBuilder body = new StringBuilder("{\"files\":[");
            for (int i = 0; i < 25; i++) {
                if (i > 0) body.append(',');
                body.append("{\"id\":\"file-").append(i).append("\",\"name\":\"File ").append(i)
                        .append("\",\"mimeType\":\"text/plain\",\"modifiedTime\":\"2026-09-07T00:00:00Z\"}");
            }
            body.append("],\"nextPageToken\":\"next\"}");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        provider.start();
        try {
            var access = mock(GoogleAccess.class);
            var credential = new GoogleAccess.Credential("access", 2);
            when(access.credential(any(), eq(GoogleAccess.Feature.DRIVE))).thenReturn(credential);
            when(access.isCurrent(any(), eq(2L))).thenReturn(true);
            var local = new GoogleHttpClient() {
                @Override public Response execute(String method, java.net.URI ignored, String token, String body) {
                    try {
                        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + provider.getAddress().getPort() + "/drive"))
                                .header("Authorization", "Bearer " + token).GET().build();
                        var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                        return new Response(response.statusCode(), response.body());
                    } catch (Exception failure) { throw new AssertionError(failure); }
                }
            };
            var service = new DriveService(access, local, mock(DriveReferenceRepository.class), mock(com.aierp.project.api.ProjectAccess.class));
            var result = service.files(UUID.randomUUID(), "", null);
            assertThat(result.files()).hasSize(20);
            assertThat(result.nextPageToken()).isEqualTo("next");
        } finally {
            provider.stop(0);
        }
    }

    @Test void sameSendKeyReplaysReceiptWithoutASecondProviderPost() {
        var access = mock(GoogleAccess.class);
        var transport = mock(GoogleHttpClient.class);
        var sends = mock(MailSendRequestRepository.class);
        var user = UUID.randomUUID();
        var requestId = UUID.randomUUID();
        var stored = new AtomicReference<MailSendRequestEntity>();
        when(sends.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(sends.saveAndFlush(any())).thenAnswer(invocation -> { stored.set(invocation.getArgument(0)); return invocation.getArgument(0); });
        when(access.credential(user, GoogleAccess.Feature.GMAIL)).thenReturn(new GoogleAccess.Credential("access", 5));
        when(access.isCurrent(user, 5)).thenReturn(true);
        when(access.status(user)).thenReturn(new GoogleAccess.Connection("sender@example.test", Map.of(GoogleAccess.Feature.GMAIL, GoogleAccess.Status.CONNECTED)));
        when(transport.execute(eq("POST"), any(java.net.URI.class), eq("access"), contains("\"raw\"")))
                .thenReturn(new GoogleHttpClient.Response(200, "{\"id\":\"provider-1\"}"));
        var service = new GmailService(access, transport, sends);
        var request = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "제목", "본문");

        assertThat(service.send(user, request).status()).isEqualTo("SENT");
        assertThat(service.send(user, request)).isEqualTo(new GmailService.SendReceipt(requestId, "SENT", "provider-1"));
        verify(transport, times(1)).execute(eq("POST"), any(java.net.URI.class), eq("access"), contains("\"raw\""));
    }

    @Test void concurrentSameKeyUsesDatabaseUniquenessAsTheSingleProviderClaim() throws Exception {
        var access = mock(GoogleAccess.class);
        var transport = mock(GoogleHttpClient.class);
        var sends = mock(MailSendRequestRepository.class);
        var user = UUID.randomUUID();
        var requestId = UUID.randomUUID();
        var rows = new ConcurrentHashMap<MailSendRequestEntity.Id, MailSendRequestEntity>();
        when(sends.findById(any())).thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(0))));
        when(sends.saveAndFlush(any())).thenAnswer(invocation -> {
            var row = (MailSendRequestEntity) invocation.getArgument(0);
            if (rows.putIfAbsent(row.id, row) != null) throw new org.springframework.dao.DataIntegrityViolationException("duplicate claim");
            return row;
        });
        when(access.credential(user, GoogleAccess.Feature.GMAIL)).thenReturn(new GoogleAccess.Credential("access", 6));
        when(access.isCurrent(user, 6)).thenReturn(true);
        when(access.status(user)).thenReturn(new GoogleAccess.Connection("sender@example.test", Map.of(GoogleAccess.Feature.GMAIL, GoogleAccess.Status.CONNECTED)));
        var providerPosts = new AtomicInteger();
        when(transport.execute(eq("POST"), any(java.net.URI.class), eq("access"), contains("\"raw\"")))
                .thenAnswer(invocation -> { providerPosts.incrementAndGet(); return new GoogleHttpClient.Response(200, "{\"id\":\"provider-2\"}"); });
        var service = new GmailService(access, transport, sends);
        var request = new GmailService.SendRequest(requestId, List.of("to@example.test"), List.of(), List.of(), "제목", "본문");
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<GmailService.SendReceipt> first = pool.submit(() -> service.send(user, request));
            java.util.concurrent.Future<GmailService.SendReceipt> second = pool.submit(() -> service.send(user, request));
            assertThat(first.get().status()).isIn("SENDING", "SENT");
            assertThat(second.get().status()).isIn("SENDING", "SENT");
        }
        assertThat(providerPosts.get()).isEqualTo(1);
    }

    @Test void gmailMapsRecipientsAndPrefersPlainTextMimeParts() {
        var access = mock(GoogleAccess.class);
        var transport = mock(GoogleHttpClient.class);
        var sends = mock(MailSendRequestRepository.class);
        var user = UUID.randomUUID();
        when(access.credential(user, GoogleAccess.Feature.GMAIL))
                .thenReturn(new GoogleAccess.Credential("access", 4));
        when(access.isCurrent(user, 4)).thenReturn(true);
        when(transport.execute(eq("GET"), any(java.net.URI.class), eq("access"), isNull()))
                .thenAnswer(invocation -> {
                    String uri = invocation.getArgument(1).toString();
                    if (uri.contains("format=metadata")) return new GoogleHttpClient.Response(200, "{\"snippet\":\"snippet\",\"internalDate\":\"1700000000000\",\"payload\":{\"headers\":[{\"name\":\"To\",\"value\":\"one@example.test, two@example.test\"},{\"name\":\"From\",\"value\":\"sender@example.test\"}]}}");
                    if (uri.contains("format=full")) return new GoogleHttpClient.Response(200, "{\"internalDate\":1700000000000,\"payload\":{\"headers\":[{\"name\":\"To\",\"value\":\"one@example.test, two@example.test\"},{\"name\":\"Cc\",\"value\":\"copy@example.test\"}],\"mimeType\":\"multipart/alternative\",\"parts\":[{\"mimeType\":\"text/html\",\"body\":{\"data\":\"PGI+aHRtbDwvYj4=\"}},{\"mimeType\":\"text/plain\",\"body\":{\"data\":\"cGxhaW4=\"}}]}}");
                    return new GoogleHttpClient.Response(200, "{\"messages\":[{\"id\":\"m1\"}]}");
                });
        var service = new GmailService(access, transport, sends);

        var messages = service.messages(user, "INBOX", "", null);
        var detail = service.message(user, "m1");

        assertThat(messages.messages()).singleElement().satisfies(row -> {
            assertThat(row.to()).containsExactly("one@example.test", "two@example.test");
            assertThat(row.internalDate()).isEqualTo("2023-11-14T22:13:20Z");
        });
        assertThat(detail.to()).containsExactly("one@example.test", "two@example.test");
        assertThat(detail.cc()).containsExactly("copy@example.test");
        assertThat(detail.bodyText()).isEqualTo("plain");
        assertThat(detail.date()).isEqualTo("2023-11-14T22:13:20Z");
    }

    @Test void credentialAndRefreshClaimsNeverExposeSecretsInText() {
        var credential=new GoogleAccess.Credential("access-secret",7);
        assertThat(credential.toString()).doesNotContain("access-secret").contains("REDACTED");
        var claim=new com.aierp.identity.api.GoogleAuthorizationService.RefreshClaim(UUID.randomUUID(),UUID.randomUUID(),3,"refresh-secret","subject");
        assertThat(claim.toString()).doesNotContain("refresh-secret");
    }

    @Test void vaultBindsCiphertextToAccountAndSubject() {
        var environment=new MockEnvironment().withProperty("GOOGLE_TOKEN_ENCRYPTION_KEY",Base64.getEncoder().encodeToString(new byte[32]));
        var vault=new GoogleTokenVault(environment); var user=UUID.randomUUID();
        var ciphertext=vault.encrypt("token-value",user,"subject-a");
        assertThat(ciphertext).doesNotContain("token-value");
        assertThat(vault.decrypt(ciphertext,user,"subject-a")).isEqualTo("token-value");
        assertThatThrownBy(()->vault.decrypt(ciphertext,user,"subject-b")).hasMessageContaining("TOKEN_VAULT_FAILURE");
    }

    @Test void driveCanonicalLinkRejectsProviderUrlInjection() {
        assertThat(DriveService.safeUrl("file_ABC-123")).isEqualTo("https://drive.google.com/open?id=file_ABC-123");
        assertThatThrownBy(()->DriveService.safeUrl("https://evil.test/?token=secret")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void gmailRequiresToRecipientBeforeAnyProviderCall() {
        var access=mock(GoogleAccess.class); var transport=mock(GoogleHttpClient.class); var sends=mock(MailSendRequestRepository.class);
        var service=new GmailService(access,transport,sends);
        var request=new GmailService.SendRequest(UUID.randomUUID(),List.of(),List.of("cc@example.test"),List.of(),"Subject","Body");
        assertThatThrownBy(()->service.send(UUID.randomUUID(),request)).hasMessage("TO_REQUIRED");
        verifyNoInteractions(access,transport,sends);
    }

    @Test void connectionStatusUsesAnUnlockedReadPath() {
        var repository=mock(GoogleAuthorizationRepository.class);var vault=mock(GoogleTokenVault.class);var user=UUID.randomUUID();
        when(repository.findByUserAccountId(user)).thenReturn(Optional.empty());
        new GoogleAuthorizationService(repository,vault).status(user);
        verify(repository).findByUserAccountId(user);verify(repository,never()).lockByUserAccountId(user);
    }

    @Test void calendarCapabilityRequiresBothRequiredScopes() {
        var repository=mock(GoogleAuthorizationRepository.class);var vault=mock(GoogleTokenVault.class);var user=UUID.randomUUID();var grant=new GoogleAuthorizationEntity();grant.userAccountId=user;grant.accountEmail="person@example.test";grant.accessTokenCiphertext="cipher";grant.status="CONNECTED";grant.grantedScopes="https://www.googleapis.com/auth/calendar.calendarlist.readonly";
        when(repository.findByUserAccountId(user)).thenReturn(Optional.of(grant));var service=new GoogleAuthorizationService(repository,vault);
        assertThat(service.status(user).status(GoogleAccess.Feature.CALENDAR)).isEqualTo(GoogleAccess.Status.PERMISSION_REQUIRED);
        grant.grantedScopes += " https://www.googleapis.com/auth/calendar.events";
        assertThat(service.status(user).status(GoogleAccess.Feature.CALENDAR)).isEqualTo(GoogleAccess.Status.CONNECTED);
    }
}
