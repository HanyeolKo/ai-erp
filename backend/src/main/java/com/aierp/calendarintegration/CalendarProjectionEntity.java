package com.aierp.calendarintegration;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="calendar_integration",name="calendar_projection")
public class CalendarProjectionEntity {
    @Id public UUID id;public UUID scheduleId;public UUID projectCalendarId;
    public String status;public String retryClassification;public String externalEventId;
    public Instant lastAttemptAt;public Instant updatedAt;public long businessRevision;
    @Version public long rowVersion;
}
