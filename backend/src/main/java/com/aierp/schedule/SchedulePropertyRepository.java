package com.aierp.schedule;
import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface SchedulePropertyRepository extends JpaRepository<SchedulePropertyEntity,UUID> {
    List<SchedulePropertyEntity> findByProjectIdOrderByPositionAscIdAsc(UUID projectId); Optional<SchedulePropertyEntity> findByIdAndProjectId(UUID id,UUID projectId); long countByProjectId(UUID projectId);
    @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("update SchedulePropertyEntity p set p.rowVersion=p.rowVersion+1 where p.id=:id and p.rowVersion=:expected")
    int advanceVersion(@Param("id") UUID id,@Param("expected") long expected);
}
