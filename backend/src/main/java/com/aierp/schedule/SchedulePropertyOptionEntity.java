package com.aierp.schedule;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(schema="schedule", name="schedule_property_option")
public class SchedulePropertyOptionEntity {
    @Id public UUID id; public UUID propertyId; public String label; public String color; public int position; public boolean archived;
    public SchedulePropertyOptionEntity() {}
}
