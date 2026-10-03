package com.aierp.projectplan;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface PlanItemRepository extends JpaRepository<PlanItemEntity,UUID> {
    List<PlanItemEntity> findByProjectIdOrderBySortOrderAscIdAsc(UUID projectId);
    List<PlanItemEntity> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);
    Optional<PlanItemEntity> findByIdAndProjectId(UUID id, UUID projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PlanItemEntity i where i.id = :id and i.projectId = :projectId")
    Optional<PlanItemEntity> lockByIdAndProjectId(UUID id, UUID projectId);
    @Query("select i from PlanItemEntity i left join TaskExecutionEntity e on e.itemId = i.id and e.projectId = i.projectId "
        + "where i.projectId in :projectIds and i.kind = com.aierp.projectplan.PlanItemKind.TASK "
        + "and i.state not in (com.aierp.projectplan.PlanItemState.DONE, com.aierp.projectplan.PlanItemState.CANCELLED) "
        + "and ((:unassigned = true and i.assigneeId is null) or (:unassigned = false and i.assigneeId = :actor)) "
        + "and (:query is null or lower(i.title) like lower(concat('%', :query, '%')) or lower(coalesce(i.description,'')) like lower(concat('%', :query, '%'))) "
        + "and ((i.deadline is not null and i.deadline < :asOfDate) or (coalesce(i.targetStart,i.targetEnd) <= :toDate and coalesce(i.targetEnd,i.targetStart) >= :fromDate) or (i.deadline is not null and i.deadline >= :fromDate and i.deadline <= :toDate)) "
        + "order by case when i.state = com.aierp.projectplan.PlanItemState.BLOCKED or (i.deadline is not null and i.deadline < :asOfDate) or exists (select d.itemId from PlanItemDependencyEntity d, PlanItemEntity p where d.itemId = i.id and p.id = d.predecessorId and p.projectId = i.projectId and p.state not in (com.aierp.projectplan.PlanItemState.DONE, com.aierp.projectplan.PlanItemState.CANCELLED)) then 0 else 1 end, "
        + "case when e.priority = com.aierp.projectplan.TaskExecutionPriority.HIGH then 0 when e.priority = com.aierp.projectplan.TaskExecutionPriority.MEDIUM then 1 when e.priority = com.aierp.projectplan.TaskExecutionPriority.LOW then 2 else 3 end, "
        + "case when i.deadline is null then 1 else 0 end, i.deadline asc, i.title asc, i.id asc")
    List<PlanItemEntity> findWorkWindow(Collection<UUID> projectIds, UUID actor, boolean unassigned, String query,
                                        java.time.LocalDate fromDate, java.time.LocalDate toDate, java.time.LocalDate asOfDate,
                                        org.springframework.data.domain.Pageable page);
    @Query("select count(i) from PlanItemEntity i where i.projectId in :projectIds and i.kind = com.aierp.projectplan.PlanItemKind.TASK "
        + "and i.state not in (com.aierp.projectplan.PlanItemState.DONE, com.aierp.projectplan.PlanItemState.CANCELLED) "
        + "and ((:unassigned = true and i.assigneeId is null) or (:unassigned = false and i.assigneeId = :actor)) "
        + "and (:query is null or lower(i.title) like lower(concat('%', :query, '%')) or lower(coalesce(i.description,'')) like lower(concat('%', :query, '%'))) "
        + "and ((i.deadline is not null and i.deadline < :asOfDate) or (coalesce(i.targetStart,i.targetEnd) <= :toDate and coalesce(i.targetEnd,i.targetStart) >= :fromDate) or (i.deadline is not null and i.deadline >= :fromDate and i.deadline <= :toDate))")
    long countWorkWindow(Collection<UUID> projectIds, UUID actor, boolean unassigned, String query,
                         java.time.LocalDate fromDate, java.time.LocalDate toDate, java.time.LocalDate asOfDate);
    @Query("select i from PlanItemEntity i left join TaskExecutionEntity e on e.itemId = i.id and e.projectId = i.projectId "
        + "where i.projectId in :projectIds and i.kind = com.aierp.projectplan.PlanItemKind.TASK "
        + "and i.state not in (com.aierp.projectplan.PlanItemState.DONE, com.aierp.projectplan.PlanItemState.CANCELLED) "
        + "and ((:unassigned = true and i.assigneeId is null) or (:unassigned = false and i.assigneeId = :actor)) "
        + "and (:query is null or lower(i.title) like lower(concat('%', :query, '%')) or lower(coalesce(i.description,'')) like lower(concat('%', :query, '%'))) "
        + "order by case when i.state = com.aierp.projectplan.PlanItemState.BLOCKED or (i.deadline is not null and i.deadline < :asOfDate) or exists (select d.itemId from PlanItemDependencyEntity d, PlanItemEntity p where d.itemId = i.id and p.id = d.predecessorId and p.projectId = i.projectId and p.state not in (com.aierp.projectplan.PlanItemState.DONE, com.aierp.projectplan.PlanItemState.CANCELLED)) then 0 else 1 end, "
        + "case when e.priority = com.aierp.projectplan.TaskExecutionPriority.HIGH then 0 when e.priority = com.aierp.projectplan.TaskExecutionPriority.MEDIUM then 1 when e.priority = com.aierp.projectplan.TaskExecutionPriority.LOW then 2 else 3 end, "
        + "case when i.deadline is null then 1 else 0 end, i.deadline asc, i.title asc, i.id asc")
    List<PlanItemEntity> findWorkAll(Collection<UUID> projectIds, UUID actor, boolean unassigned, String query,
                                     java.time.LocalDate asOfDate, org.springframework.data.domain.Pageable page);
    @Query("select count(i) from PlanItemEntity i where i.projectId in :projectIds and i.kind = com.aierp.projectplan.PlanItemKind.TASK "
        + "and i.state not in (com.aierp.projectplan.PlanItemState.DONE, com.aierp.projectplan.PlanItemState.CANCELLED) "
        + "and ((:unassigned = true and i.assigneeId is null) or (:unassigned = false and i.assigneeId = :actor)) "
        + "and (:query is null or lower(i.title) like lower(concat('%', :query, '%')) or lower(coalesce(i.description,'')) like lower(concat('%', :query, '%')))")
    long countWorkAll(Collection<UUID> projectIds, UUID actor, boolean unassigned, String query);
}
