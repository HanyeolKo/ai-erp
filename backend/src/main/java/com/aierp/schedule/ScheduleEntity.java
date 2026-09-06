package com.aierp.schedule;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="schedule",name="project_schedule")
public class ScheduleEntity {
    @Id public UUID id;
    public UUID projectId;
    public UUID createdBy;
    public String title;
    public String description;
    public Instant startsAt;
    public Instant endsAt;
    @Enumerated(EnumType.STRING) public Status status;
    @Version public long rowVersion;
    public long businessRevision;
    public Instant updatedAt;
    public enum Status { DRAFT, CONFIRMED, CANCELLED }
    public ScheduleEntity() {}
}
