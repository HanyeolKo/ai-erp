package com.aierp.project;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(schema="project", name="project_share_invitation")
public class ProjectShareInvitationEntity {
    @Id public UUID projectId;
    @Column(nullable=false, unique=true, length=16) public String code;
    @Column(nullable=false) public UUID invitedBy;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public Instant expiresAt;
    public Instant revokedAt;
    protected ProjectShareInvitationEntity() { }
}
