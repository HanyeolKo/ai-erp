package com.aierp.calendarintegration;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface CalendarProjectionRepository extends JpaRepository<CalendarProjectionEntity,UUID> {
    long countByProjectCalendarIdAndStatusNot(UUID projectCalendarId, String status);
    Optional<CalendarProjectionEntity> findByScheduleIdAndProjectCalendarId(UUID scheduleId,UUID projectCalendarId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CalendarProjectionEntity p where p.id = :id")
    Optional<CalendarProjectionEntity> lockById(UUID id);
}
