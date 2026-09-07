package com.aierp.calendarintegration;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(schema="calendar_integration",name="project_calendar")
public class ProjectCalendarEntity {
    @Id public UUID id;public UUID projectId;public UUID calendarConnectionId;public String externalCalendarId;
    /** Current ERP account that owns the binding; cleared on disconnect. */
    public UUID bindingOwner;
    /** Incremented for every bind/disconnect so late HTTP cannot complete against a new target. */
    public long bindingGeneration;
    public String calendarName;
    public UUID backfillCursor;
    public boolean backfillPending;
}
