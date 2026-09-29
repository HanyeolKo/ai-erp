package com.aierp.schedule;
import java.util.*; import org.springframework.data.jpa.repository.*;
public interface SchedulePropertyOptionRepository extends JpaRepository<SchedulePropertyOptionEntity,UUID> { List<SchedulePropertyOptionEntity> findByPropertyIdOrderByPositionAscIdAsc(UUID propertyId); List<SchedulePropertyOptionEntity> findByPropertyIdIn(Collection<UUID> ids); Optional<SchedulePropertyOptionEntity> findByIdAndPropertyId(UUID id,UUID propertyId); }
