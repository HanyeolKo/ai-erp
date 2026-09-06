package com.aierp.identity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="identity",name="google_identity")
public class GoogleIdentityEntity {
    @Id public UUID id; public UUID userAccountId; public String subject; public String email;
    public Instant createdAt; public Instant updatedAt;
}
