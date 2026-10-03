package com.aierp.project;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "project", name = "management_definition")
public class ProjectManagementDefinitionEntity {
    @Id public UUID projectId;
    @Column(length = 2000) public String purpose;
    @Column(name = "success_criteria", length = 2000) public String successCriteria;
    public UUID responsibleManagerId;
    @Enumerated(EnumType.STRING) @Column(length = 16) public Health health;
    @Column(name = "health_reason", length = 500) public String healthReason;
    public LocalDate healthAsOf;
    public long rowVersion;
    public Instant updatedAt;
    public enum Health { ON_TRACK, WATCH, AT_RISK }
}
