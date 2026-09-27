package com.aierp.projectplan;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(schema="project", name="plan_item",
    indexes={@Index(name="plan_item_project_order_idx",columnList="project_id,sort_order,id"),
             @Index(name="plan_item_project_parent_idx",columnList="project_id,parent_id")})
public class PlanItemEntity {
    @Id public UUID id;
    public UUID projectId;
    public UUID parentId;
    @Enumerated(EnumType.STRING) public PlanItemKind kind;
    public String title;
    public String description;
    public UUID assigneeId;
    @Enumerated(EnumType.STRING) public PlanItemState state;
    public LocalDate targetStart;
    public LocalDate targetEnd;
    public LocalDate deadline;
    public int sortOrder;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public List<String> labels;
    public long rowVersion;
    public UUID createdBy;
    public Instant updatedAt;
    public PlanItemEntity() { }
}
