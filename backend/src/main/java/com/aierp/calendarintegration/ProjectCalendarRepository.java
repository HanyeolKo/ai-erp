package com.aierp.calendarintegration;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
public interface ProjectCalendarRepository extends JpaRepository<ProjectCalendarEntity,UUID> {
    Optional<ProjectCalendarEntity> findByProjectId(UUID projectId);
    @Query("select c.projectId from ProjectCalendarEntity c where c.backfillPending = true order by c.projectId asc")
    List<UUID> findBackfillProjectIds(Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProjectCalendarEntity c where c.projectId = :projectId")
    Optional<ProjectCalendarEntity> findByProjectIdForUpdate(UUID projectId);
}
