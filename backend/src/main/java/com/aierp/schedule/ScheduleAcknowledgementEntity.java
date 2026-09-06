package com.aierp.schedule;
import jakarta.persistence.*;
import java.util.*;
import java.time.Instant;
import java.io.Serializable;
@Entity @Table(schema="schedule",name="schedule_acknowledgement")
@IdClass(ScheduleAcknowledgementEntity.Key.class)
public class ScheduleAcknowledgementEntity {
    @Id public UUID scheduleId;
    @Id public UUID userAccountId;
    @Id public long businessRevision;
    public Instant acknowledgedAt;
    public static class Key implements Serializable {
        public UUID scheduleId; public UUID userAccountId; public long businessRevision;
        public Key() {}
        public Key(UUID scheduleId,UUID userAccountId,long businessRevision) {this.scheduleId=scheduleId;this.userAccountId=userAccountId;this.businessRevision=businessRevision;}
        @Override public boolean equals(Object other) {return other instanceof Key k && Objects.equals(scheduleId,k.scheduleId) && Objects.equals(userAccountId,k.userAccountId) && businessRevision==k.businessRevision;}
        @Override public int hashCode() {return Objects.hash(scheduleId,userAccountId,businessRevision);}
    }
}
