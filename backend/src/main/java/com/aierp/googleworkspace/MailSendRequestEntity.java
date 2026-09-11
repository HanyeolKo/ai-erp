package com.aierp.googleworkspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Objects;
@Entity @Table(schema="google_workspace",name="mail_send_request")
public class MailSendRequestEntity {
 @EmbeddedId public Id id; public String payloadHash; public String status; public String messageId; public long credentialGeneration; public Instant createdAt; public Instant updatedAt;
 @Embeddable public static class Id { public UUID userAccountId; public UUID requestId; public Id(){} public Id(UUID u,UUID r){userAccountId=u;requestId=r;} @Override public boolean equals(Object o){return o instanceof Id x&&Objects.equals(userAccountId,x.userAccountId)&&Objects.equals(requestId,x.requestId);} @Override public int hashCode(){return Objects.hash(userAccountId,requestId);} }
}
