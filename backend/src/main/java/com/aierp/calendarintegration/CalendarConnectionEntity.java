package com.aierp.calendarintegration;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(schema="calendar_integration",name="calendar_connection")
public class CalendarConnectionEntity {
    @Id public UUID id; public UUID userAccountId; public String status;public Instant updatedAt;
}
