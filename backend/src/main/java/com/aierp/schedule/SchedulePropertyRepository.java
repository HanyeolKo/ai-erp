package com.aierp.schedule;
import java.util.*; import org.springframework.data.jpa.repository.*;
public interface SchedulePropertyRepository extends JpaRepository<SchedulePropertyEntity,UUID> { List<SchedulePropertyEntity> findByProjectIdOrderByPositionAscIdAsc(UUID projectId); Optional<SchedulePropertyEntity> findByIdAndProjectId(UUID id,UUID projectId); long countByProjectId(UUID projectId); }
