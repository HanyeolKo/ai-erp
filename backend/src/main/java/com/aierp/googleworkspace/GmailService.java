package com.aierp.googleworkspace;

import com.aierp.identity.api.GoogleAccess;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GmailService {
    private static final JsonMapper JSON = new JsonMapper();
    private static final int MAX_RECIPIENTS = 20;
    private static final int MAX_BODY = 100_000;
    private static final int MAX_PROVIDER_PARTS = 100;
    private final GoogleAccess access;
    private final GoogleHttpClient http;
    private final MailSendRequestRepository sends;
    private final TransactionTemplate transactions;

    public GmailService(GoogleAccess access, GoogleHttpClient http, MailSendRequestRepository sends) {
        this(access, http, sends, null);
    }

    @Autowired
    public GmailService(GoogleAccess access, GoogleHttpClient http, MailSendRequestRepository sends,
                        org.springframework.transaction.PlatformTransactionManager tx) {
        this.access = access;
        this.http = http;
        this.sends = sends;
        this.transactions = tx == null ? null : new TransactionTemplate(tx);
    }

    public MessagesResponse messages(UUID user, String folder, String query, String pageToken) {
        var credential = access.credential(user, GoogleAccess.Feature.GMAIL);
        String selectedFolder = folder == null || folder.isBlank() ? "INBOX" : folder.toUpperCase(Locale.ROOT);
        if (!Set.of("INBOX", "SENT").contains(selectedFolder)) throw new IllegalArgumentException("FOLDER_INVALID");
        String text = query == null ? "" : query.trim();
        if (text.length() > 200) throw new IllegalArgumentException("QUERY_TOO_LONG");
        if (pageToken != null && pageToken.length() > 1000) throw new IllegalArgumentException("PAGE_TOKEN_INVALID");
        String gmailQuery = (selectedFolder.equals("INBOX") ? "in:inbox " : "in:sent ") + text;
        String uri = "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=" + encode(gmailQuery)
                + "&maxResults=20" + (pageToken == null || pageToken.isBlank() ? "" : "&pageToken=" + encode(pageToken));
        var listing = http.execute("GET", URI.create(uri), credential.accessToken(), null);
        check(listing);
        JsonNode root = parse(listing.body());
        var rows = new ArrayList<MessageSummary>();
        int count = 0;
        for (JsonNode item : root.path("messages")) {
            if (count++ >= MAX_RECIPIENTS) break;
            String id = item.path("id").asText();
            if (id.isBlank() || id.length() > 255 || !id.matches("[A-Za-z0-9_-]+")) continue;
            var metadata = http.execute("GET", URI.create(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + encode(id)
                            + "?format=metadata&metadataHeaders=Subject&metadataHeaders=From"
                            + "&metadataHeaders=To&metadataHeaders=Date"), credential.accessToken(), null);
            if (metadata.status() == 404) continue;
            check(metadata);
            rows.add(summary(parse(metadata.body()), id));
        }
        if (!access.isCurrent(user, credential.generation())) throw new GoogleAccess.GoogleAccessException(GoogleAccess.Status.REAUTH_REQUIRED);
        return new MessagesResponse(List.copyOf(rows), boundedToken(root.path("nextPageToken").asText(null)));
    }

    public MessageDetail message(UUID user, String id) {
        if (id == null || id.length() > 255 || !id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("MESSAGE_ID_INVALID");
        var credential = access.credential(user, GoogleAccess.Feature.GMAIL);
        var response = http.execute("GET", URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/" + id + "?format=full"), credential.accessToken(), null);
        check(response);
        if (!access.isCurrent(user, credential.generation())) throw new GoogleAccess.GoogleAccessException(GoogleAccess.Status.REAUTH_REQUIRED);
        return detail(parse(response.body()), id);
    }

    public SendReceipt send(UUID user, SendRequest request) {
        validate(request);
        String payloadHash = payloadHash(request);
        var key = new MailSendRequestEntity.Id(user, request.requestId());
        Claim claim = claim(key, payloadHash);
        if (claim == null) throw new IllegalStateException("SEND_CLAIM_RACE");
        MailSendRequestEntity row = claim.row();
        if (!claim.owner()) {
            if (row == null) throw new IllegalStateException("SEND_CLAIM_RACE");
            if (!Objects.equals(row.payloadHash, payloadHash)) throw new IllegalStateException("REQUEST_ID_PAYLOAD_MISMATCH");
            return receipt(row);
        }
        try {
            var credential = access.credential(user, GoogleAccess.Feature.GMAIL);
            row.credentialGeneration = credential.generation();
            String sender = access.status(user).accountEmail();
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime(request, sender).getBytes(StandardCharsets.UTF_8));
            // Keep this check immediately adjacent to the provider request;
            // the terminal transaction repeats it under the grant lock.
            if (!access.isCurrent(user, credential.generation())) throw new StaleProviderResult();
            var response = http.execute("POST", URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"), credential.accessToken(), "{\"raw\":\"" + raw + "\"}");
            if (response.status() == 401 || response.status() == 403 || response.status() == 429)
                GoogleHttpClient.requireSuccess(response);
            if (response.status() >= 400 && response.status() < 500)
                throw new GoogleAccess.GoogleAccessException("GOOGLE_INVALID_RESPONSE", com.aierp.platform.web.ExternalServiceFailure.Category.DEFINITIVE);
            if (response.status() < 200 || response.status() >= 300) GoogleHttpClient.requireSuccess(response);
            if (!access.isCurrent(user, credential.generation())) throw new StaleProviderResult();
            JsonNode body = parse(response.body());
            row.status = "SENT";
            row.messageId = bounded(body.path("id").asText(null), 255);
        } catch (com.aierp.platform.web.ExternalServiceFailure failure) {
            // A successful provider response with an unusable body may still
            // represent an accepted message, so it remains UNKNOWN. Explicit
            // client 4xx responses are classified as definitive below.
            boolean malformedSuccess = failure instanceof GoogleHttpClient.GoogleServiceException
                    && "GOOGLE_INVALID_RESPONSE".equals(failure.safeCode());
            row.status = malformedSuccess || failure.category() == com.aierp.platform.web.ExternalServiceFailure.Category.TEMPORARY
                    ? "UNKNOWN" : "FAILED";
        } catch (RuntimeException failure) {
            row.status = "UNKNOWN";
        }
        try {
            var persisted = finish(row);
            return persisted == null ? new SendReceipt(request.requestId(), "UNKNOWN", null) : persisted;
        } catch (RuntimeException persistenceFailure) { return new SendReceipt(request.requestId(), "UNKNOWN", null); }
    }

    @org.springframework.transaction.annotation.Transactional
    public SendReceipt receipt(UUID user, UUID requestId) {
        var key = new MailSendRequestEntity.Id(user, requestId);
        var row = (transactions == null ? sends.findById(key) : sends.lockById(key))
                .orElseThrow(() -> new java.util.NoSuchElementException("SEND_NOT_FOUND"));
        if ("SENDING".equals(row.status) && row.updatedAt != null && row.updatedAt.isBefore(Instant.now().minusSeconds(60))) {
            row.status = "UNKNOWN";
            row.updatedAt = Instant.now();
            sends.saveAndFlush(row);
        }
        return receipt(row);
    }

    private Claim claim(MailSendRequestEntity.Id key, String hash) {
        TransactionCallback<Claim> work = tx -> {
            int inserted = sends.insertClaim(key.userAccountId, key.requestId, hash);
            var row = (transactions == null ? sends.findById(key) : sends.lockById(key)).orElse(null);
            if (row == null) return null;
            return new Claim(row, inserted == 1);
        };
        return transactions == null ? work.doInTransaction(null) : transactions.execute(work);
    }

    private SendReceipt finish(MailSendRequestEntity row) {
        TransactionCallback<SendReceipt> work = tx -> {
            var current = (transactions == null ? sends.findById(row.id) : sends.lockById(row.id)).orElse(null);
            if (current == null || !"SENDING".equals(current.status)) return current == null ? null : receipt(current);
            // This joins the current transaction in the real authorization
            // service, retaining the grant lock until this receipt commits.
            // A demotion/disconnect can therefore only produce UNKNOWN.
            // Only a provider-success candidate needs the grant barrier. A
            // credential/configuration failure happened before any provider
            // attempt and must remain the definitive FAILED receipt instead
            // of being rewritten to UNKNOWN because generation 0 is stale.
            if (transactions != null && "SENT".equals(row.status)
                    && !access.isCurrentForCommit(row.id.userAccountId, row.credentialGeneration)) {
                current.status = "UNKNOWN";
                current.updatedAt = Instant.now();
                sends.saveAndFlush(current);
                return receipt(current);
            }
            current.status = row.status; current.messageId = row.messageId; current.credentialGeneration = row.credentialGeneration; current.updatedAt = Instant.now();
            sends.saveAndFlush(current);
            return receipt(current);
        };
        return transactions == null ? work.doInTransaction(null) : transactions.execute(work);
    }

    private static MessageSummary summary(JsonNode node, String id) {
        Map<String, String> headers = headers(node.path("payload").path("headers"));
        return new MessageSummary(id, headers.getOrDefault("Subject", ""), headers.getOrDefault("From", ""), addresses(headers.get("To")), bounded(node.path("snippet").asText(""), 2000), isoDate(node, headers.get("Date")), node.path("labelIds").toString().contains("UNREAD"));
    }

    private static MessageDetail detail(JsonNode node, String id) {
        Map<String, String> headers = headers(node.path("payload").path("headers"));
        Body body = new Body(); readPart(node.path("payload"), body, 0);
        return new MessageDetail(id, headers.getOrDefault("Subject", ""), headers.getOrDefault("From", ""), addresses(headers.get("To")), addresses(headers.get("Cc")), isoDate(node, headers.get("Date")), body.value(), body.truncated);
    }

    private static void readPart(JsonNode part, Body body, int depth) {
        if (depth > 8) { body.truncated = true; return; }
        if (body.parts++ >= MAX_PROVIDER_PARTS) { body.truncated = true; return; }
        String mimeType = part.path("mimeType").asText("").toLowerCase(Locale.ROOT);
        String data = part.path("body").path("data").asText(null);
        if (data != null && !data.isBlank() && (mimeType.equals("text/plain") || mimeType.equals("text/html"))) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
                if (mimeType.equals("text/plain")) body.appendPlain(decoded); else body.appendHtml(htmlToText(decoded));
            } catch (IllegalArgumentException malformed) { body.truncated = true; }
        }
        for (JsonNode child : part.path("parts")) readPart(child, body, depth + 1);
    }

    private static String htmlToText(String html) {
        return html.replaceAll("(?is)<script.*?</script>|<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("(?i)https?://[^\\s]+", " ")
                .replaceAll("&nbsp;", " ");
    }

    private static Map<String, String> headers(JsonNode nodes) {
        var result = new java.util.HashMap<String, String>();
        for (JsonNode header : nodes) {
            String name = header.path("name").asText("");
            String canonical = switch (name.toLowerCase(Locale.ROOT)) {
                case "subject" -> "Subject"; case "from" -> "From"; case "to" -> "To"; case "cc" -> "Cc"; case "date" -> "Date"; default -> "";
            };
            if (!canonical.isEmpty()) result.put(canonical, bounded(header.path("value").asText(""), 2000));
        }
        return result;
    }

    private static List<String> addresses(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).limit(MAX_RECIPIENTS).toList();
    }

    private static String isoDate(JsonNode node, String header) {
        JsonNode value = node.path("internalDate");
        if (value.isNumber()) return Instant.ofEpochMilli(value.asLong()).toString();
        if (value.isTextual()) {
            try { return Instant.ofEpochMilli(Long.parseLong(value.asText())).toString(); }
            catch (RuntimeException ignored) { }
            try { return Instant.parse(value.asText()).toString(); }
            catch (RuntimeException ignored) { }
        }
        if (header != null) { try { return ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toString(); } catch (RuntimeException ignored) { } }
        return Instant.EPOCH.toString();
    }

    private static void validate(SendRequest request) {
        if (request == null || request.requestId() == null) throw new IllegalArgumentException("REQUEST_ID_REQUIRED");
        List<String> to = request.to() == null ? List.of() : request.to();
        List<String> cc = request.cc() == null ? List.of() : request.cc();
        List<String> bcc = request.bcc() == null ? List.of() : request.bcc();
        List<String> recipients = new ArrayList<>(); recipients.addAll(to); recipients.addAll(cc); recipients.addAll(bcc);
        if (to.isEmpty()) throw new IllegalArgumentException("TO_REQUIRED");
        if (recipients.size() > MAX_RECIPIENTS) throw new IllegalArgumentException("RECIPIENTS_INVALID");
        for (String recipient : recipients) if (recipient == null || recipient.length() > 320 || recipient.contains("\r") || recipient.contains("\n") || !recipient.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("RECIPIENT_INVALID");
        if (request.subject() == null || request.subject().isBlank() || request.subject().length() > 200 || request.subject().contains("\r") || request.subject().contains("\n")) throw new IllegalArgumentException("SUBJECT_INVALID");
        if (request.body() == null || request.body().length() > MAX_BODY) throw new IllegalArgumentException("BODY_TOO_LARGE");
    }

    private static String mime(SendRequest request, String from) {
        try {
            var message = new MimeMessage(Session.getInstance(new Properties()));
            message.setFrom(new InternetAddress(from, true));
            setRecipients(message, Message.RecipientType.TO, request.to()); setRecipients(message, Message.RecipientType.CC, request.cc()); setRecipients(message, Message.RecipientType.BCC, request.bcc());
            message.setSubject(request.subject(), StandardCharsets.UTF_8.name()); message.setText(request.body(), StandardCharsets.UTF_8.name());
            var output = new ByteArrayOutputStream(); message.writeTo(output); return output.toString(StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalArgumentException("MIME_BUILD_FAILED", failure); }
    }

    private static void setRecipients(MimeMessage message, Message.RecipientType type, List<String> values) throws Exception {
        if (values == null || values.isEmpty()) return;
        var addresses = new InternetAddress[values.size()]; for (int i = 0; i < values.size(); i++) addresses[i] = new InternetAddress(values.get(i), true); message.setRecipients(type, addresses);
    }

    private static String payloadHash(SendRequest request) {
        try { String canonical = canonical(request.to()) + canonical(request.cc()) + canonical(request.bcc()) + length(request.subject()) + request.subject() + length(request.body()) + request.body(); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static String canonical(List<String> values) { if (values == null) return "0:"; return values.stream().map(value -> length(value) + value.trim().toLowerCase(Locale.ROOT)).collect(Collectors.joining("|", values.size() + ":", ";")); }
    private static String length(String value) { return (value == null ? 0 : value.length()) + ":"; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String boundedToken(String value) { return value == null || value.length() > 1000 ? null : value; }
    private static String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }
    private static JsonNode parse(String value) { try { return JSON.readTree(value); } catch (Exception failure) { throw new GoogleHttpClient.GoogleServiceException("PROVIDER_INVALID_RESPONSE", failure); } }
    private static void check(GoogleHttpClient.Response response) { GoogleHttpClient.requireSuccess(response); }
    private static SendReceipt receipt(MailSendRequestEntity row) { return new SendReceipt(row.id.requestId, row.status, row.messageId); }

    private static final class Body {
        private final StringBuilder plain = new StringBuilder(); private final StringBuilder html = new StringBuilder(); private int parts; private boolean truncated;
        private void appendPlain(String value) { append(plain, value); } private void appendHtml(String value) { append(html, value); }
        private void append(StringBuilder target, String value) { int room = MAX_BODY - target.length(); if (room <= 0) { truncated = true; return; } target.append(value, 0, Math.min(room, value.length())); if (value.length() > room) truncated = true; }
        private String value() { return (plain.length() > 0 ? plain : html).toString(); }
    }
    private static final class StaleProviderResult extends RuntimeException { }
    private record Claim(MailSendRequestEntity row, boolean owner) { }
    public record MessagesResponse(List<MessageSummary> messages, String nextPageToken) { }
    public record MessageSummary(String id, String subject, String from, List<String> to, String snippet, String internalDate, boolean unread) { }
    public record MessageDetail(String id, String subject, String from, List<String> to, List<String> cc, String date, String bodyText, boolean truncated) { }
    public record SendRequest(UUID requestId, List<String> to, List<String> cc, List<String> bcc, String subject, String body) { public String from() { return ""; } }
    public record SendReceipt(UUID requestId, String status, String messageId) { }
}
