package com.aierp.calendarintegration;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(schema="calendar_integration",name="calendar_projection")
public class CalendarProjectionEntity {
    @Id public UUID id;public UUID scheduleId;public UUID projectCalendarId;
    public String status;public String retryClassification;public String externalEventId;
    public Instant lastAttemptAt;public Instant updatedAt;public long businessRevision;
    public UUID claimToken;public Instant leaseUntil;public long deliveredRevision;
    /** Bounded local audit window for uncertain CANCELLED provider operations. */
    public Instant reconcileUntil;public Instant nextReconcileAt;
    @Version public long rowVersion;
}
