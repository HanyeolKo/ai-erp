package com.aierp.schedule;
import jakarta.persistence.*; import java.util.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity @Table(schema="schedule", name="schedule_saved_view")
public class ScheduleSavedViewEntity {
    @Id public UUID id; public UUID projectId; public String name; @Enumerated(EnumType.STRING) public Scope scope; public UUID ownerId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public Map<String,Object> config; @Version public long rowVersion; public boolean archived;
    public enum Scope { PERSONAL, SHARED }
    public ScheduleSavedViewEntity() {}
}
