package com.aierp.project;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity @Table(schema = "project", name = "project_member") @IdClass(ProjectMemberEntity.Key.class)
public class ProjectMemberEntity {
    @Id public UUID projectId;
    @Id public UUID userAccountId;
    @Enumerated(EnumType.STRING) public ProjectRole role;
    protected ProjectMemberEntity() { }
    public static class Key implements Serializable { public UUID projectId; public UUID userAccountId; }
}
