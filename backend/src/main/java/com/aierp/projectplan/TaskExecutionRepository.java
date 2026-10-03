package com.aierp.projectplan;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, UUID> {
    List<TaskExecutionEntity> findByItemIdIn(Collection<UUID> itemIds);
    Optional<TaskExecutionEntity> findByItemIdAndProjectId(UUID itemId, UUID projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TaskExecutionEntity e where e.itemId = :itemId and e.projectId = :projectId")
    Optional<TaskExecutionEntity> lockByItemIdAndProjectId(UUID itemId, UUID projectId);
}
