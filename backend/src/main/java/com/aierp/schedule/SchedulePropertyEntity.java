package com.aierp.schedule;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(schema="schedule", name="schedule_property")
public class SchedulePropertyEntity {
    @Id public UUID id; public UUID projectId; public String name;
    @Enumerated(EnumType.STRING) @Column(name="property_type") public PropertyType propertyType;
    public int position; public boolean archived; @Version public long rowVersion;
    public enum PropertyType { TEXT, NUMBER, CHECKBOX, DATE, SINGLE_SELECT }
    public SchedulePropertyEntity() {}
}
