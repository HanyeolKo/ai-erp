package com.aierp.schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
public interface ScheduleAcknowledgementRepository extends JpaRepository<ScheduleAcknowledgementEntity,ScheduleAcknowledgementEntity.Key> {
    List<ScheduleAcknowledgementEntity> findByScheduleIdAndBusinessRevision(UUID scheduleId,long businessRevision);
    @Query("select a from ScheduleAcknowledgementEntity a, ScheduleEntity s where a.scheduleId = s.id and s.id in :scheduleIds and a.businessRevision = s.businessRevision")
    List<ScheduleAcknowledgementEntity> findCurrentByScheduleIds(Collection<UUID> scheduleIds);
}
