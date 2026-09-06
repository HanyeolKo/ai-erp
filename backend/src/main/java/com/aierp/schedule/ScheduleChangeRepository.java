package com.aierp.schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.domain.Pageable;
public interface ScheduleChangeRepository extends JpaRepository<ScheduleChangeEntity,UUID> {
    List<ScheduleChangeEntity> findByScheduleId(UUID scheduleId, Pageable page);
}
