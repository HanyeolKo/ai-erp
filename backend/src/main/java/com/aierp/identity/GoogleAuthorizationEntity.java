package com.aierp.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(schema="identity", name="google_authorization")
public class GoogleAuthorizationEntity {
    @Id public UUID userAccountId;
    public String subject;
    public String accountEmail;
    @Column(columnDefinition="text") public String accessTokenCiphertext;
    @Column(columnDefinition="text") public String refreshTokenCiphertext;
    @Column(columnDefinition="text") public String grantedScopes;
    public Instant expiresAt;
    public String status;
    public long generation;
    public UUID refreshClaimToken;
    public Instant refreshLeaseUntil;
    public Instant createdAt;
    public Instant updatedAt;
}
