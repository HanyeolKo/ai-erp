package com.aierp.projectplan;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.project.api.ProjectAccess;
import com.aierp.project.api.ManagementMutationAccess;
import com.aierp.project.api.ManagementRequestCanonicalizer;
import com.aierp.project.api.ProjectAccess.ReadableProject;
import com.aierp.projectplan.api.ProjectManagementController.*;
import com.aierp.platform.web.ValidationFailure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectManagementService {
    private final TaskExecutionRepository executions;
    private final ManagementMutationAccess mutations;
    private final PlanItemRepository items;
    private final PlanItemDependencyRepository dependencies;
    private final ProjectAccess access;
    private final boolean enabled;

    public ProjectManagementService(TaskExecutionRepository executions,
            ManagementMutationAccess mutations, PlanItemRepository items,
            PlanItemDependencyRepository dependencies, ProjectAccess access,
            @Value("${app.project-management.enabled:true}") boolean enabled) {
        this.executions=executions; this.mutations=mutations;
        this.items=items; this.dependencies=dependencies; this.access=access; this.enabled=enabled;
    }

    public TaskExecutionBulk taskExecutions(UUID projectId, Collection<UUID> requested, UUID actor) {
        var project = access.readableProject(projectId, actor);
        var ids = normalizeIds(requested);
        if (ids.size() > 100) throw new ValidationFailure("itemIds", "At most 100 unique TASK IDs may be requested");
        if (ids.isEmpty()) return new TaskExecutionBulk(projectId,List.of(),true);
        var rows = items.findAllById(ids);
        var byId = rows.stream().collect(Collectors.toMap(i -> i.id, Function.identity()));
        if (byId.size() != ids.size() || rows.stream().anyMatch(i -> !projectId.equals(i.projectId) || i.kind != PlanItemKind.TASK))
            throw new NoSuchElementException("TASK_NOT_FOUND");
        var metadata = executions.findByItemIdIn(ids).stream().collect(Collectors.toMap(e -> e.itemId, Function.identity()));
        var result = ids.stream().map(id -> toTaskExecution(id, metadata.get(id), enabled && !"VIEWER".equals(project.role().name()))).toList();
        return new TaskExecutionBulk(projectId,result,true);
    }

    @Transactional
    public TaskExecution updateTaskExecution(UUID projectId, UUID itemId, TaskExecutionWrite input, UUID actor) {
        requireInput(input); validateTask(input); access.lockProject(projectId); access.requirePlanWriter(projectId,actor); requireEnabled();
        var item = items.lockByIdAndProjectId(itemId,projectId).orElseThrow(() -> new NoSuchElementException("TASK_NOT_FOUND"));
        if (item.kind != PlanItemKind.TASK) throw new NoSuchElementException("TASK_NOT_FOUND");
        var hash = hash(ManagementRequestCanonicalizer.taskExecution("TASK_EXECUTION_UPDATE", itemId,
                input.priority() == null ? null : input.priority().name(), input.completionCriterion(), input.rowVersion()));
        var prior = mutations.lock(projectId,actor,input.requestId());
        if (prior.isPresent()) { if(!hash.equals(prior.get().payloadHash()) || !"TASK_EXECUTION".equals(prior.get().resourceType()) || !itemId.equals(prior.get().resourceId())) throw new IllegalStateException("REQUEST_PAYLOAD_MISMATCH"); return taskExecution(itemId,projectId,actor); }
        var row = executions.lockByItemIdAndProjectId(itemId,projectId).orElseGet(() -> {
            var created = new TaskExecutionEntity(); created.itemId=itemId; created.projectId=projectId; created.rowVersion=0; return created;
        });
        requireVersion(row.rowVersion,input.rowVersion());
        var before = taskValues(row);
        row.priority=input.priority()==null?null:TaskExecutionPriority.valueOf(input.priority().name()); row.completionCriterion=trimOrNull(input.completionCriterion()); row.rowVersion++; row.updatedAt=Instant.now();
        executions.saveAndFlush(row);
        mutations.audit(projectId,actor,"TASK_EXECUTION",itemId,row.rowVersion,"TASK_EXECUTION_UPDATE",before,taskValues(row));
        mutations.saveReceipt(projectId,actor,input.requestId(),hash,"TASK_EXECUTION",itemId,row.rowVersion,"TASK_EXECUTION_UPDATE");
        return toTaskExecution(itemId,row,enabled && !"VIEWER".equals(access.readableProject(projectId,actor).role().name()));
    }

    private TaskExecution taskExecution(UUID itemId, UUID projectId, UUID actor) {
        var project = access.readableProject(projectId,actor);
        if (items.findByIdAndProjectId(itemId,projectId).filter(i -> i.kind == PlanItemKind.TASK).isEmpty()) throw new NoSuchElementException("TASK_NOT_FOUND");
        return toTaskExecution(itemId,executions.findByItemIdAndProjectId(itemId,projectId).orElse(null),enabled && !"VIEWER".equals(project.role().name()));
    }

    public WorkPage work(WorkRange range, ZoneId zone, UUID projectId, WorkAssignee assignee, int page, int limit, String query, UUID actor) {
        if (range == null) throw new ValidationFailure("range", "range is required");
        if (zone == null) throw new ValidationFailure("timeZone", "A valid IANA timeZone is required");
        if (page < 0) throw new ValidationFailure("page", "page must be non-negative");
        if (limit < 1 || limit > 100) throw new ValidationFailure("limit", "limit must be between 1 and 100");
        var normalizedQuery = query == null ? null : query.trim();
        if (normalizedQuery != null && normalizedQuery.length() > 200) throw new ValidationFailure("q", "q must be at most 200 characters");
        if (normalizedQuery != null && normalizedQuery.isBlank()) normalizedQuery=null;
        var projects = projectId == null ? access.readableProjects(actor) : List.of(access.readableProject(projectId,actor));
        var ids = projects.stream().map(ReadableProject::projectId).toList();
        var today = LocalDate.now(zone);
        var unassigned = assignee == WorkAssignee.UNASSIGNED;
        var pageRequest = PageRequest.of(page,limit);
        List<PlanItemEntity> rows; long total;
        if (range == WorkRange.ALL) { rows=ids.isEmpty()?List.of():items.findWorkAll(ids,actor,unassigned,normalizedQuery,today,pageRequest); total=ids.isEmpty()?0:items.countWorkAll(ids,actor,unassigned,normalizedQuery); }
        else {
            var from = range == WorkRange.TODAY ? today : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            var to = range == WorkRange.TODAY ? today : from.plusDays(6);
            rows=ids.isEmpty()?List.of():items.findWorkWindow(ids,actor,unassigned,normalizedQuery,from,to,today,pageRequest);
            total=ids.isEmpty()?0:items.countWorkWindow(ids,actor,unassigned,normalizedQuery,from,to,today);
        }
        var projectById=projects.stream().collect(Collectors.toMap(ReadableProject::projectId,Function.identity()));
        var rowIds=rows.stream().map(i->i.id).toList();
        var metadata=executions.findByItemIdIn(rowIds).stream().collect(Collectors.toMap(e->e.itemId,Function.identity()));
        if (rows.isEmpty()) return new WorkPage(List.of(),page,limit,false,total,true,0,today,zone.getId(),Instant.now());
        var incomingSlice=dependencies.findByItemIdIn(rowIds,PageRequest.of(0,10001));
        var incoming=incomingSlice.getContent();
        var edgeMap=incoming.stream().collect(Collectors.toMap(edge->edge.itemId+":"+edge.predecessorId,Function.identity(),(a,b)->a,LinkedHashMap::new));
        boolean relationOverflow=incomingSlice.hasNext() || incoming.size()>10000;
        var remaining=Math.max(0,10000-incoming.size());
        if (!relationOverflow) {
            // At the exact incoming cap the opposite direction is still unobserved.
            // Probe one outgoing edge so an additional unique relation cannot be reported complete.
            var outgoingSlice=dependencies.findByPredecessorIdIn(rowIds,PageRequest.of(0,Math.max(1,remaining+1)));
            outgoingSlice.getContent().forEach(edge->edgeMap.putIfAbsent(edge.itemId+":"+edge.predecessorId,edge));
            relationOverflow=outgoingSlice.hasNext() || edgeMap.size()>10000;
        }
        List<PlanItemDependencyEntity> edges=new ArrayList<>(edgeMap.values());
        var visibleProjectByItem=rows.stream().collect(Collectors.toMap(i->i.id,i->i.projectId,(a,b)->a));
        var neighborIdsByProject=new LinkedHashMap<UUID,LinkedHashSet<UUID>>();
        for (var edge: edges) {
            var visibleItemProject=visibleProjectByItem.get(edge.itemId);
            if (visibleItemProject!=null && !rowIds.contains(edge.predecessorId))
                neighborIdsByProject.computeIfAbsent(visibleItemProject,k->new LinkedHashSet<>()).add(edge.predecessorId);
            var visiblePredecessorProject=visibleProjectByItem.get(edge.predecessorId);
            if (visiblePredecessorProject!=null && !rowIds.contains(edge.itemId))
                neighborIdsByProject.computeIfAbsent(visiblePredecessorProject,k->new LinkedHashSet<>()).add(edge.itemId);
        }
        var relatedRows=new ArrayList<PlanItemEntity>(rows);
        for (var project: projects) {
            var missing=new ArrayList<>(neighborIdsByProject.getOrDefault(project.projectId(),new LinkedHashSet<>()));
            for (int offset=0; offset<missing.size(); offset+=100) {
                var batch=missing.subList(offset,Math.min(offset+100,missing.size()));
                var loaded=items.findByProjectIdAndIdIn(project.projectId(),batch);
                relatedRows.addAll(loaded);
            }
        }
        var relatedById=relatedRows.stream().collect(Collectors.toMap(i->i.id,Function.identity(),(a,b)->a));
        edges=edges.stream().filter(edge->{var item=relatedById.get(edge.itemId);var predecessor=relatedById.get(edge.predecessorId);return item!=null&&predecessor!=null&&Objects.equals(item.projectId,predecessor.projectId);}).toList();
        var readContext=PlanItemReadModel.context(relatedRows,edges,false);
        var workItems=rows.stream().map(i -> {
            var project=projectById.get(i.projectId); var execution=toTaskExecution(i.id,metadata.get(i.id),enabled && !"VIEWER".equals(project.role().name()));
            var response=readContext.response(i,today,i.targetStart,i.targetEnd);
            var blocking=response.blockerIds().isEmpty()?(i.state==PlanItemState.BLOCKED?"Task is marked BLOCKED":null):"Blocked by an incomplete predecessor";
            return new WorkItem(i.projectId,project.name(),project.role().name(),response,execution,null,blocking);
        }).toList();
        return new WorkPage(workItems,page,limit,(long)(page+1)*limit<total,relationOverflow?null:total,!relationOverflow,workItems.size(),today,zone.getId(),Instant.now());
    }

    public Receipt receipt(UUID projectId, UUID requestId, UUID actor) {
        access.readableProject(projectId,actor);
        var row=mutations.find(projectId,actor,requestId).orElseThrow(NoSuchElementException::new);
        return new Receipt(requestId,row.resourceType(),row.resourceId(),row.rowVersion(),row.status());
    }

    public HistoryPage history(UUID projectId, String resourceType, UUID resourceId, int page, int limit, UUID actor) {
        access.readableProject(projectId,actor);
        if (resourceType == null || resourceType.isBlank()) throw new ValidationFailure("resourceType","resourceType is required");
        if (resourceId == null) throw new ValidationFailure("resourceId","resourceId is required");
        if (page<0) throw new ValidationFailure("page","page must be non-negative");
        if (limit<1 || limit>100) throw new ValidationFailure("limit","limit must be between 1 and 100");
        var result=mutations.history(projectId,resourceType,resourceId,page,limit);
        return new HistoryPage(result.entries().stream().map(a -> new HistoryEntry(a.id(),a.actorId(),a.occurredAt(),a.resourceType(),a.resourceId(),a.rowVersion(),a.operation(),a.before(),a.after())).toList(),result.hasNext(),page,limit);
    }

    private void requireEnabled() { if (!enabled) throw new IllegalStateException("PROJECT_MANAGEMENT_DISABLED"); }
    private static void requireInput(Object input) { if (input==null) throw new ValidationFailure("request","Request body is required"); }
    private static void requireVersion(long current, Long expected) { if (expected==null || expected<0) throw new ValidationFailure("rowVersion","rowVersion is required and must be non-negative"); if(current!=expected) throw new org.springframework.orm.ObjectOptimisticLockingFailureException("STALE_ROW_VERSION",null); }
    private static void validateTask(TaskExecutionWrite input) { length(input.completionCriterion(),2000,"completionCriterion"); if(input.requestId()==null) throw new ValidationFailure("requestId","requestId is required"); }
    private static void length(String value,int max,String field) { if(value!=null && value.trim().length()>max) throw new ValidationFailure(field,field+" is too long"); }
    private static String trimOrNull(String value) { return ManagementRequestCanonicalizer.normalize(value); }
    private static Set<UUID> normalizeIds(Collection<UUID> ids) { return ids==null?Set.of():ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)); }
    private static Map<String,Object> taskValues(TaskExecutionEntity row) { var m=new LinkedHashMap<String,Object>();m.put("priority",row.priority==null?null:row.priority.name());m.put("completionCriterion",row.completionCriterion);m.put("rowVersion",row.rowVersion);return m; }
    private static TaskExecution toTaskExecution(UUID id,TaskExecutionEntity row,boolean canEdit) { return row==null?new TaskExecution(id,null,null,0,canEdit):new TaskExecution(id,row.priority==null?null:TaskPriority.valueOf(row.priority.name()),row.completionCriterion,row.rowVersion,canEdit); }
    private static com.aierp.projectplan.api.ProjectPlanController.ItemResponse originalItem(PlanItemEntity i,LocalDate today) { var s=new com.aierp.projectplan.api.ProjectPlanController.Summary(1,0,i.state==PlanItemState.BLOCKED?1:0,i.deadline!=null&&i.deadline.isBefore(today)?1:0,(i.targetStart==null&&i.targetEnd==null)?1:0,i.assigneeId==null?1:0,0.0,i.targetStart,i.targetEnd,ForecastState.INCOMPLETE,false); return new com.aierp.projectplan.api.ProjectPlanController.ItemResponse(i.id,i.projectId,i.parentId,i.kind,i.title,i.description,i.assigneeId,i.state,i.targetStart,i.targetEnd,i.deadline,i.sortOrder,i.labels==null?List.of():i.labels,i.rowVersion,i.createdBy,i.updatedAt,List.of(),List.of(),List.of(),s); }
    private static String blockingReason(PlanItemEntity item,List<PlanItemDependencyEntity> edges,Map<UUID,PlanItemEntity> predecessors) { if(edges.stream().map(e->predecessors.get(e.predecessorId)).filter(Objects::nonNull).anyMatch(p->p.state!=PlanItemState.DONE&&p.state!=PlanItemState.CANCELLED)) return "Blocked by an incomplete predecessor"; return item.state==PlanItemState.BLOCKED?"Task is marked BLOCKED":null; }
    private static String hash(String value) { try { var digest=MessageDigest.getInstance("SHA-256"); return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
