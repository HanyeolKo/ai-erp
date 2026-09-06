package com.aierp.project;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="project",name="project_invitation")
public class ProjectInvitationEntity {
    @Id public UUID id; public UUID projectId; public String email; public String token;
    @Enumerated(EnumType.STRING) public Status status;
    public Instant expiresAt; public UUID invitedBy; public Instant resolvedAt;
    public enum Status { PENDING, ACCEPTED, REJECTED, EXPIRED }
}
