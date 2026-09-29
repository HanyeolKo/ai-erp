package com.aierp.schedule;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.UUID;
@Entity @Table(schema="schedule", name="schedule_property_value")
public class SchedulePropertyValueEntity {
    @Id public UUID id; public UUID projectId; public UUID scheduleId; public UUID propertyId;
    @Enumerated(EnumType.STRING) @Column(name="value_type") public SchedulePropertyEntity.PropertyType valueType;
    @Column(name="text_value") public String textValue; @Column(name="number_value") public BigDecimal numberValue;
    @Column(name="checkbox_value") public Boolean checkboxValue; @Column(name="date_value") public LocalDate dateValue; @Column(name="option_id") public UUID optionId;
    public SchedulePropertyValueEntity() {}
}
