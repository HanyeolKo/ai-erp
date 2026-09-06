package com.aierp.calendarintegration;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(schema="calendar_integration",name="project_calendar")
public class ProjectCalendarEntity {
    @Id public UUID id;public UUID projectId;public UUID calendarConnectionId;public String externalCalendarId;
}
