package com.aierp.schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScheduleChangeRepository extends JpaRepository<ScheduleChangeEntity,UUID> {
    List<ScheduleChangeEntity> findByScheduleIdOrderByCreatedAtAsc(UUID scheduleId);
}
