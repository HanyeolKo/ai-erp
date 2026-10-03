package com.aierp.projectplan.api;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.projectplan.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ProjectManagementController {
    private final ProjectManagementService service;
    public ProjectManagementController(ProjectManagementService service) { this.service=service; }
    @GetMapping("/projects/{projectId}/management/tasks") public TaskExecutionBulk tasks(@PathVariable UUID projectId,@RequestParam(required=false) String itemIds,Authentication auth) { return service.taskExecutions(projectId,parseIds(itemIds),user(auth)); }
    @PatchMapping("/projects/{projectId}/management/tasks/{itemId}") public TaskExecution task(@PathVariable UUID projectId,@PathVariable UUID itemId,@RequestBody TaskExecutionWrite body,Authentication auth) { return service.updateTaskExecution(projectId,itemId,body,user(auth)); }
    @GetMapping("/me/work") public WorkPage work(@RequestParam(defaultValue="today") String range,@RequestParam(defaultValue="UTC") String timeZone,@RequestParam(required=false) UUID projectId,@RequestParam(defaultValue="mine") String assignee,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int limit,@RequestParam(required=false) String q,Authentication auth) {
        WorkRange r; WorkAssignee a; ZoneId zone;
        try { r=WorkRange.valueOf(range.trim().toUpperCase(Locale.ROOT)); } catch(Exception e) { throw new com.aierp.platform.web.ValidationFailure("range","range must be today, week, or all"); }
        try { a=WorkAssignee.valueOf(assignee.trim().toUpperCase(Locale.ROOT)); } catch(Exception e) { throw new com.aierp.platform.web.ValidationFailure("assignee","assignee must be mine or unassigned"); }
        try { zone=ZoneId.of(timeZone); } catch(Exception e) { throw new com.aierp.platform.web.ValidationFailure("timeZone","timeZone must be a valid IANA ZoneId"); }
        return service.work(r,zone,projectId,a,page,limit,q,user(auth));
    }
    @GetMapping("/projects/{projectId}/management/requests/{requestId}") public Receipt receipt(@PathVariable UUID projectId,@PathVariable UUID requestId,Authentication auth) { return service.receipt(projectId,requestId,user(auth)); }
    @GetMapping("/projects/{projectId}/management/history") public HistoryPage history(@PathVariable UUID projectId,@RequestParam String resourceType,@RequestParam UUID resourceId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int limit,Authentication auth) { return service.history(projectId,resourceType,resourceId,page,limit,user(auth)); }
    private static Collection<UUID> parseIds(String raw) { if(raw==null||raw.isBlank()) return List.of(); try { return Arrays.stream(raw.split(",")).map(String::trim).filter(s->!s.isBlank()).map(UUID::fromString).toList(); } catch(Exception e) { throw new com.aierp.platform.web.ValidationFailure("itemIds","itemIds must contain UUIDs"); } }
    private static UUID user(Authentication auth) { return principal(auth).userId(); }
    private static ApplicationPrincipal principal(Authentication auth) { if(auth!=null&&auth.getPrincipal() instanceof ApplicationPrincipal p) return p; throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED"); }

    public enum TaskPriority { HIGH, MEDIUM, LOW }
    public enum WorkRange { TODAY, WEEK, ALL }
    public enum WorkAssignee { MINE, UNASSIGNED }
    public record TaskExecution(UUID itemId,TaskPriority priority,String completionCriterion,long rowVersion,boolean canEdit) { }
    public record TaskExecutionWrite(TaskPriority priority,String completionCriterion,Long rowVersion,UUID requestId) { }
    public record TaskExecutionBulk(UUID projectId,List<TaskExecution> items,boolean complete) { public TaskExecutionBulk { items=List.copyOf(items); } }
    public record NextSchedule(UUID scheduleId,String title,Instant startsAt,Instant endsAt,String status) { }
    public record WorkItem(UUID projectId,String projectName,String projectRole,ProjectPlanController.ItemResponse item,TaskExecution execution,NextSchedule nextSchedule,String blockingReason) { }
    public record WorkPage(List<WorkItem> items,int page,int limit,boolean hasNext,Long totalCount,boolean complete,long observedCount,LocalDate asOfDate,String timeZone,Instant observedAt) { public WorkPage { items=List.copyOf(items); } }
    public record Receipt(UUID requestId,String resourceType,UUID resourceId,long rowVersion,String status) { }
    public record HistoryEntry(UUID id,UUID actorId,Instant occurredAt,String resourceType,UUID resourceId,long rowVersion,String operation,Map<String,Object> before,Map<String,Object> after) { }
    public record HistoryPage(List<HistoryEntry> entries,boolean hasNext,int page,int limit) { public HistoryPage { entries=List.copyOf(entries); } }
}
