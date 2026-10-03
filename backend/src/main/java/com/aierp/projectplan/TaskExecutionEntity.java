package com.aierp.projectplan;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "project", name = "task_execution")
public class TaskExecutionEntity {
    @Id public UUID itemId;
    public UUID projectId;
    @Enumerated(EnumType.STRING) @Column(length = 8) public TaskExecutionPriority priority;
    @Column(name = "completion_criterion", length = 2000) public String completionCriterion;
    public long rowVersion;
    public Instant updatedAt;
}
