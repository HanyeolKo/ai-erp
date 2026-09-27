package com.aierp.projectplan;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(schema="project", name="plan_item_history")
public class PlanItemHistoryEntity {
    @Id public UUID id;
    public UUID projectId;
    public UUID itemId;
    public UUID actorId;
    @Column(name="at") public Instant at;
    public long rowVersion;
    public String reason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public Map<String,Object> beforeValues;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public Map<String,Object> afterValues;
    public PlanItemHistoryEntity() { }
}
