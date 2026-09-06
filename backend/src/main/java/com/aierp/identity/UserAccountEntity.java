package com.aierp.identity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="identity",name="user_account")
public class UserAccountEntity {
    @Id public UUID id; public String email; public String displayName;
    public Instant emailVerifiedAt; public Instant createdAt; public Instant updatedAt;
}
