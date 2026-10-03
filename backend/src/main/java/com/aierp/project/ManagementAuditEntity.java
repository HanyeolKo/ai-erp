package com.aierp.project;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(schema="project", name="management_audit", indexes=@Index(name="management_audit_resource_idx",columnList="project_id,resource_type,resource_id,occurred_at,id"))
public class ManagementAuditEntity {
    @Id public UUID id; public UUID projectId; public UUID actorId; public Instant occurredAt;
    @Column(length=32) public String resourceType; public UUID resourceId; public long rowVersion; @Column(length=80) public String operation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public Map<String,Object> beforeValues;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public Map<String,Object> afterValues;
}
