package com.aierp.schedule;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface ScheduleRepository extends JpaRepository<ScheduleEntity,UUID> {
    List<ScheduleEntity> findByProjectId(UUID projectId);
    Optional<ScheduleEntity> findByIdAndProjectId(UUID id,UUID projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleEntity s where s.id = :id and s.projectId = :projectId")
    Optional<ScheduleEntity> lockByIdAndProjectId(UUID id,UUID projectId);
}
