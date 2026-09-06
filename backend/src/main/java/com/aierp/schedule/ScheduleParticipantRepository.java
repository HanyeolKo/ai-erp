package com.aierp.schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipantEntity,UUID> {
    List<ScheduleParticipantEntity> findByScheduleId(UUID scheduleId);
    void deleteByScheduleId(UUID scheduleId);
}
