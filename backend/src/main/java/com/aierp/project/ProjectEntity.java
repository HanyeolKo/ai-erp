package com.aierp.project;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity @Table(schema = "project", name = "project")
public class ProjectEntity {
    @Id public UUID id;
    public UUID groupId;
    public String name;
    protected ProjectEntity() { }
    public ProjectEntity(UUID id, UUID groupId, String name) { this.id = id; this.groupId = groupId; this.name = name; }
}
