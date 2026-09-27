package com.aierp.projectplan;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(schema="project", name="project_plan_history")
public class ProjectPlanHistoryEntity {
    @Id public UUID id; public UUID projectId; public UUID actorId; @Column(name="at") public Instant occurredAt;
    public long rowVersion; public String reason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public Map<String,Object> beforeValues;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public Map<String,Object> afterValues;
    public ProjectPlanHistoryEntity() { }
}
