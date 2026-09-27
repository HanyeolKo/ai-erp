package com.aierp.projectplan;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanItemHistoryRepository extends JpaRepository<PlanItemHistoryEntity,UUID> {
    Page<PlanItemHistoryEntity> findByItemIdOrderByAtDescIdDesc(UUID itemId, Pageable page);
}
