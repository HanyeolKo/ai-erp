package com.aierp.schedule;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(schema="schedule",name="schedule_change")
public class ScheduleChangeEntity {
    @Id public UUID id; public UUID scheduleId; public long businessRevision;
    public String changeType; public UUID changedBy; public Instant createdAt;
}
