package com.aierp.projectplan;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface PlanItemRepository extends JpaRepository<PlanItemEntity,UUID> {
    List<PlanItemEntity> findByProjectIdOrderBySortOrderAscIdAsc(UUID projectId);
    Optional<PlanItemEntity> findByIdAndProjectId(UUID id, UUID projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PlanItemEntity i where i.id = :id and i.projectId = :projectId")
    Optional<PlanItemEntity> lockByIdAndProjectId(UUID id, UUID projectId);
}
