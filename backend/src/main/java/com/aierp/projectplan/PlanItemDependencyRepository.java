package com.aierp.projectplan;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanItemDependencyRepository extends JpaRepository<PlanItemDependencyEntity,PlanItemDependencyEntity.Key> {
    List<PlanItemDependencyEntity> findByItemId(UUID itemId);
    List<PlanItemDependencyEntity> findByItemIdIn(Collection<UUID> itemIds);
    List<PlanItemDependencyEntity> findByPredecessorId(UUID predecessorId);
}
