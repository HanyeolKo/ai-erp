package com.aierp.schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipantEntity,UUID> {
    List<ScheduleParticipantEntity> findByScheduleId(UUID scheduleId);
    List<ScheduleParticipantEntity> findByScheduleIdIn(Collection<UUID> scheduleIds);
    @Query("select p.memberUserAccountId as userId, count(p) as pendingCount from ScheduleParticipantEntity p, ScheduleEntity s "
        + "where p.scheduleId = s.id and s.projectId = :projectId and s.businessRevision > 0 and p.memberUserAccountId is not null "
        + "and not exists (select a.scheduleId from ScheduleAcknowledgementEntity a where a.scheduleId = s.id and a.userAccountId = p.memberUserAccountId and a.businessRevision = s.businessRevision) "
        + "group by p.memberUserAccountId order by p.memberUserAccountId")
    List<PendingCount> pendingCounts(UUID projectId, Pageable page);
    interface PendingCount { UUID getUserId(); long getPendingCount(); }
    void deleteByScheduleId(UUID scheduleId);
}
