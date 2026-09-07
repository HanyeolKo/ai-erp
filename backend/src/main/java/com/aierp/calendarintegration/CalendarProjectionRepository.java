package com.aierp.calendarintegration;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
public interface CalendarProjectionRepository extends JpaRepository<CalendarProjectionEntity,UUID> {
    long countByProjectCalendarIdAndStatusNot(UUID projectCalendarId, String status);
    Optional<CalendarProjectionEntity> findByScheduleIdAndProjectCalendarId(UUID scheduleId,UUID projectCalendarId);
    List<CalendarProjectionEntity> findByProjectCalendarId(UUID projectCalendarId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CalendarProjectionEntity p where p.id = :id")
    Optional<CalendarProjectionEntity> lockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CalendarProjectionEntity p where (p.status = 'PENDING' or (p.reconcileUntil is not null and p.reconcileUntil > :now and p.nextReconcileAt is not null and p.nextReconcileAt <= :now)) and (p.leaseUntil is null or p.leaseUntil < :now) order by p.updatedAt asc")
    List<CalendarProjectionEntity> claimable(Instant now, org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update CalendarProjectionEntity p set p.claimToken = null, p.leaseUntil = null, p.rowVersion = p.rowVersion + 1 where p.projectCalendarId = :calendarId")
    int invalidateClaims(UUID calendarId);
}
