package com.aierp.projectplan;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface ProjectPlanRepository extends JpaRepository<ProjectPlanEntity,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProjectPlanEntity p where p.projectId = :projectId")
    Optional<ProjectPlanEntity> lockByProjectId(UUID projectId);
}
