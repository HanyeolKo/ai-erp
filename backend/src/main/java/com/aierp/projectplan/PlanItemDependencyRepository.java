package com.aierp.projectplan;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanItemDependencyRepository extends JpaRepository<PlanItemDependencyEntity,PlanItemDependencyEntity.Key> {
    List<PlanItemDependencyEntity> findByItemId(UUID itemId);
    List<PlanItemDependencyEntity> findByItemIdIn(Collection<UUID> itemIds);
    Slice<PlanItemDependencyEntity> findByItemIdIn(Collection<UUID> itemIds, Pageable pageable);
    List<PlanItemDependencyEntity> findByPredecessorId(UUID predecessorId);
    Slice<PlanItemDependencyEntity> findByPredecessorIdIn(Collection<UUID> predecessorIds, Pageable pageable);
    Slice<PlanItemDependencyEntity> findByItemIdInOrPredecessorIdIn(Collection<UUID> itemIds, Collection<UUID> predecessorIds, Pageable pageable);
}
