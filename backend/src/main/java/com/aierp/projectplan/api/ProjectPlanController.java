package com.aierp.projectplan.api;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.projectplan.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects/{projectId}/plan")
public class ProjectPlanController {
    private final ProjectPlanService plans;
    public ProjectPlanController(ProjectPlanService plans) { this.plans=plans; }

    @GetMapping public Snapshot get(@PathVariable UUID projectId, @RequestParam(required=false) String kind,
        @RequestParam(required=false) PlanItemState state, @RequestParam(defaultValue="false") boolean includeCancelled,
        @RequestParam(required=false) UUID assigneeId, @RequestParam(defaultValue="false") boolean unassigned,
        @RequestParam(required=false) UUID scopeId, @RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to, @RequestParam(required=false) String q,
        @RequestParam(required=false) String label, Authentication auth) {
        return plans.snapshot(projectId,user(auth),kind,state,includeCancelled,assigneeId,unassigned,scopeId,from,to,q,label);
    }
    @PatchMapping public Snapshot updateTarget(@PathVariable UUID projectId,@RequestBody TargetWrite body,Authentication auth) {
        return plans.updateTarget(projectId,body,user(auth));
    }
    @PostMapping("/items") public ItemResponse create(@PathVariable UUID projectId,@RequestBody ItemWrite body,Authentication auth) {
        return plans.create(projectId,body,user(auth));
    }
    @PatchMapping("/items/{itemId}") public ItemResponse update(@PathVariable UUID projectId,@PathVariable UUID itemId,
        @RequestBody ItemWrite body,Authentication auth) { return plans.update(projectId,itemId,body,user(auth)); }
    @GetMapping("/items/{itemId}/history") public HistoryPage history(@PathVariable UUID projectId,@PathVariable UUID itemId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int limit,Authentication auth) {
        return plans.history(projectId,itemId,page,limit,user(auth));
    }
    private static UUID user(Authentication auth) { if (auth.getPrincipal() instanceof ApplicationPrincipal p) return p.userId(); throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED"); }

    public record TargetWrite(LocalDate targetStart,LocalDate targetEnd,Long rowVersion,String reason) { }
    public record ItemWrite(UUID parentId,PlanItemKind kind,String title,String description,UUID assigneeId,PlanItemState state,
        LocalDate targetStart,LocalDate targetEnd,LocalDate deadline,Integer sortOrder,List<String> labels,Long rowVersion,
        List<UUID> predecessorIds,UUID requestId,String reason) { }
    public record Summary(long taskCount,long doneCount,long blockedCount,long overdueCount,long unplannedCount,long unassignedCount,
        Double progressPercent,LocalDate forecastStart,LocalDate forecastEnd,ForecastState forecastState,boolean outsideTarget) { }
    public record ItemResponse(UUID id,UUID projectId,UUID parentId,PlanItemKind kind,String title,String description,UUID assigneeId,
        PlanItemState state,LocalDate targetStart,LocalDate targetEnd,LocalDate deadline,int sortOrder,List<String> labels,long rowVersion,
        UUID createdBy,Instant updatedAt,List<UUID> predecessorIds,List<UUID> successorIds,List<UUID> blockerIds,Summary summary) { }
    public record Snapshot(UUID projectId,long rowVersion,LocalDate targetStart,LocalDate targetEnd,LocalDate asOfDate,List<ItemResponse> items,
        List<UUID> matchedIds,Summary summary,boolean complete,long totalCount) { }
    public record HistoryEntry(UUID id,UUID actorId,Instant occurredAt,long version,String reason,Map<String,Object> before,Map<String,Object> after) { }
    public record HistoryPage(List<HistoryEntry> entries,boolean hasNext,int page,int limit) { }
}
