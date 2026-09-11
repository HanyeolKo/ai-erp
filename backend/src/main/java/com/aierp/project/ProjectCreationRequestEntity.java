package com.aierp.project;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(schema="project", name="project_creation_request",
    uniqueConstraints=@UniqueConstraint(name="project_creation_request_actor_key", columnNames={"actorId","requestId"}))
public class ProjectCreationRequestEntity {
    @Id public UUID id;
    public UUID actorId;
    public UUID requestId;
    public String normalizedName;
    public UUID requestedGroupId;
    public UUID projectId;
    public Instant createdAt;
    public ProjectCreationRequestEntity() { }
}
