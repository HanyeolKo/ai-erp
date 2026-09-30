package com.aierp.schedule.api;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.schedule.ScheduleEntity;
import com.aierp.schedule.SchedulePropertyEntity.PropertyType;
import java.time.*; import java.util.*;
import tools.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects/{projectId}/schedule-workspace")
public class ScheduleWorkspaceController {
    private final com.aierp.schedule.ScheduleWorkspaceService service;
    public ScheduleWorkspaceController(com.aierp.schedule.ScheduleWorkspaceService service) { this.service=service; }
    @GetMapping public Workspace workspace(@PathVariable UUID projectId, Authentication auth) { return service.workspace(projectId,user(auth)); }
    @PostMapping("/properties") public Property property(@PathVariable UUID projectId,@RequestBody PropertyWrite body,Authentication auth) { return service.createProperty(projectId,body,user(auth)); }
    @PatchMapping("/properties/{id}") public Property property(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody PropertyWrite body,Authentication auth) { return service.updateProperty(projectId,id,body,user(auth)); }
    @PostMapping("/views") public View view(@PathVariable UUID projectId,@RequestBody ViewWrite body,Authentication auth) { return service.createView(projectId,body,user(auth)); }
    @PatchMapping("/views/{id}") public View view(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody ViewWrite body,Authentication auth) { return service.updateView(projectId,id,body,user(auth)); }
    @PatchMapping("/dashboard") public Dashboard dashboard(@PathVariable UUID projectId,@RequestBody DashboardWrite body,Authentication auth) { return service.dashboard(projectId,body,user(auth)); }
    @PostMapping("/query") public QueryResult query(@PathVariable UUID projectId,@RequestBody Query body,Authentication auth) { return service.query(projectId,body,user(auth)); }
    @GetMapping("/records/{id}") public Record record(@PathVariable UUID projectId,@PathVariable UUID id,Authentication auth) { return service.record(projectId,id,user(auth)); }
    @PostMapping("/records") public Record record(@PathVariable UUID projectId,@RequestBody RecordWrite body,Authentication auth) { return service.createRecord(projectId,body,user(auth)); }
    @PatchMapping("/records/{id}") public Record record(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody RecordWrite body,Authentication auth) { return service.updateRecord(projectId,id,body,user(auth)); }
    @PatchMapping("/records/{id}/values") public Record values(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody ValuesWrite body,Authentication auth) { return service.updateValues(projectId,id,body,user(auth)); }
    private UUID user(Authentication a) { if(a.getPrincipal() instanceof ApplicationPrincipal p) return p.userId(); throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED"); }

    public record Workspace(List<Property> properties,List<View> views,String dashboardViewId,long dashboardRowVersion) {}
    public record Property(UUID id,String name,PropertyType type,int position,boolean archived,long rowVersion,List<Option> options) {}
    public record Option(UUID id,String label,String color,boolean archived) {}
    public record PropertyWrite(String name,PropertyType type,Integer position,Boolean archived,Long rowVersion,List<OptionWrite> options) {}
    public record OptionWrite(UUID id,String label,String color,Boolean archived) {}
    public enum ViewType { CALENDAR,CARDS,LIST }
    public enum Scope { BUILTIN,PERSONAL,SHARED }
    public enum Direction { ASC,DESC }
    public record View(String id,String name,Scope scope,UUID ownerId,long rowVersion,boolean archived,ViewConfig config) {}
    public record ViewWrite(String name,Scope scope,ViewConfig config,Long rowVersion,Boolean archived) {}
    public record ViewConfig(ViewType type,List<Filter> filters,List<SortSpec> sorts,UUID groupBy,UUID legendBy,List<String> visibleFields) {}
    public enum Operator { EQ,NE,CONTAINS,GT,GTE,LT,LTE,IS_EMPTY,IS_NOT_EMPTY }
    public record Filter(String field,Operator operator,JsonNode value) {}
    public record SortSpec(String field,Direction direction) {}
    public record DashboardWrite(String viewId,Long rowVersion) {}
    public record Dashboard(String dashboardViewId,long dashboardRowVersion) {}
    public record Query(ViewConfig config,Instant from,Instant to,Integer page,Integer size) {}
    public record QueryResult(List<Record> records,long total,boolean hasMore,int page,int size,List<Group> groups,Instant queriedAt) {}
    public record Group(UUID optionId,String label,String color,long count) {}
    public record Record(ScheduleController.ScheduleResponse schedule,Map<UUID,JsonNode> values) {}
    public record RecordWrite(ScheduleController.Write schedule,Map<UUID,JsonNode> values) {}
    public record ValuesWrite(Long rowVersion,Map<UUID,JsonNode> values) {}
}
