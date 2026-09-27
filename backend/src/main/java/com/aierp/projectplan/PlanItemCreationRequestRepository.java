package com.aierp.projectplan;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanItemCreationRequestRepository extends JpaRepository<PlanItemCreationRequestEntity,PlanItemCreationRequestEntity.Key> {
    Optional<PlanItemCreationRequestEntity> findByProjectIdAndActorIdAndRequestId(UUID projectId,UUID actorId,UUID requestId);
}
