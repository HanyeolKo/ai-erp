package com.aierp.schedule;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(schema="schedule",name="schedule_participant")
public class ScheduleParticipantEntity {
    @Id public UUID id;
    public UUID scheduleId;
    public UUID memberUserAccountId;
    public String externalEmail;
}
