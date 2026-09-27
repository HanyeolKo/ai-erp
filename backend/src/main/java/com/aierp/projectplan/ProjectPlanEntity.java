package com.aierp.projectplan;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(schema="project", name="project_plan")
public class ProjectPlanEntity {
    @Id public UUID projectId;
    public long rowVersion;
    public LocalDate targetStart;
    public LocalDate targetEnd;
    public Instant updatedAt;
    public ProjectPlanEntity() { }
}
