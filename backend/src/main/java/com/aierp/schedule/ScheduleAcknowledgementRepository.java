package com.aierp.schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScheduleAcknowledgementRepository extends JpaRepository<ScheduleAcknowledgementEntity,ScheduleAcknowledgementEntity.Key> {
    List<ScheduleAcknowledgementEntity> findByScheduleIdAndBusinessRevision(UUID scheduleId,long businessRevision);
}
