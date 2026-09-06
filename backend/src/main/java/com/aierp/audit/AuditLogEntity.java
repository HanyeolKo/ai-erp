package com.aierp.audit;
import jakarta.persistence.*;
import java.util.*;
import java.time.Instant;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
@Entity @Table(schema="audit",name="audit_log") @Immutable
public class AuditLogEntity {
    @Id public UUID id; public UUID actorId; public String action;
    public String aggregateType; public UUID aggregateId;
    @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> payload;
    public Instant occurredAt;
}
