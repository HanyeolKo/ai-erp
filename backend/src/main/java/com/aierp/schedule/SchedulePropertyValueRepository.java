package com.aierp.schedule;
import java.util.*; import org.springframework.data.jpa.repository.*;
public interface SchedulePropertyValueRepository extends JpaRepository<SchedulePropertyValueEntity,UUID> { List<SchedulePropertyValueEntity> findByProjectIdAndScheduleId(UUID projectId,UUID scheduleId); List<SchedulePropertyValueEntity> findByProjectIdAndScheduleIdIn(UUID projectId,Collection<UUID> ids); Optional<SchedulePropertyValueEntity> findByProjectIdAndScheduleIdAndPropertyId(UUID projectId,UUID scheduleId,UUID propertyId); }
