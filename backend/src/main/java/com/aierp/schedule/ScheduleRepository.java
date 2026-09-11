package com.aierp.schedule;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
public interface ScheduleRepository extends JpaRepository<ScheduleEntity,UUID> {
    @Query("select s from ScheduleEntity s where s.projectId = :projectId and s.endsAt > :from and s.startsAt < :to")
    List<ScheduleEntity> findWindow(UUID projectId, Instant from, Instant to, Pageable page);
    @Query("select s from ScheduleEntity s where s.projectId = :projectId and s.status <> com.aierp.schedule.ScheduleEntity.Status.CANCELLED and s.endsAt > :from and s.startsAt < :to")
    List<ScheduleEntity> findUpcoming(UUID projectId, Instant from, Instant to, Pageable page);
    @Query("select s from ScheduleEntity s where s.projectId = :projectId and s.businessRevision > 0 "
        + "and exists (select p.id from ScheduleParticipantEntity p where p.scheduleId = s.id and p.memberUserAccountId = :user) "
        + "and not exists (select a.scheduleId from ScheduleAcknowledgementEntity a where a.scheduleId = s.id and a.userAccountId = :user and a.businessRevision = s.businessRevision)")
    List<ScheduleEntity> findPendingForUser(UUID projectId, UUID user, Pageable page);
    long countByProjectId(UUID projectId);
    boolean existsByIdAndProjectId(UUID id, UUID projectId);
    Optional<ScheduleEntity> findByIdAndProjectId(UUID id,UUID projectId);
    /** Calendar-owned export read: only durable, business-visible revisions in UUID order. */
    @Query("select s from ScheduleEntity s where s.projectId = :projectId and s.businessRevision > 0 "
        + "and s.status in (com.aierp.schedule.ScheduleEntity.Status.CONFIRMED, com.aierp.schedule.ScheduleEntity.Status.CANCELLED) "
        + "and (:cursor is null or s.id > :cursor) order by s.id asc")
    List<ScheduleEntity> findCalendarExportPage(UUID projectId, UUID cursor, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleEntity s where s.id = :id and s.projectId = :projectId")
    Optional<ScheduleEntity> lockByIdAndProjectId(UUID id,UUID projectId);
}
