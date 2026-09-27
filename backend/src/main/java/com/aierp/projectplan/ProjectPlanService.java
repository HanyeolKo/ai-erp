package com.aierp.projectplan;

import com.aierp.identity.api.IdentityProfiles;
import com.aierp.platform.web.ValidationFailure;
import com.aierp.project.api.ProjectAccess;
import com.aierp.projectplan.api.ProjectPlanController.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service @Transactional(readOnly=true)
public class ProjectPlanService {
    private final ProjectPlanRepository plans; private final PlanItemRepository items; private final PlanItemDependencyRepository dependencies;
    private final PlanItemHistoryRepository history; private final ProjectPlanHistoryRepository planHistory;
    private final PlanItemCreationRequestRepository requests; private final ProjectAccess access;
    public ProjectPlanService(ProjectPlanRepository plans,PlanItemRepository items,PlanItemDependencyRepository dependencies,
        PlanItemHistoryRepository history,ProjectPlanHistoryRepository planHistory,PlanItemCreationRequestRepository requests,ProjectAccess access) {
        this.plans=plans;this.items=items;this.dependencies=dependencies;this.history=history;this.planHistory=planHistory;this.requests=requests;this.access=access;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot snapshot(UUID projectId,UUID user,String kind,PlanItemState state,boolean includeCancelled,UUID assigneeId,boolean unassigned,
            UUID scopeId,LocalDate from,LocalDate to,String q,String label) {
        access.role(projectId,user);
        if(kind!=null&&!kind.isBlank()) Arrays.stream(kind.split(",")).map(String::trim).filter(s->!s.isBlank()).map(s->parseKind(s,"kind")).toList();
        if (from!=null && to!=null && from.isAfter(to)) throw new ValidationFailure("to","Must be on or after from");
        var plan=plans.findById(projectId).orElseGet(() -> virtualPlan(projectId));
        var all=items.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var graph=new Graph(all,dependencies.findByItemIdIn(all.stream().map(i->i.id).toList()));
        var matched=all.stream().filter(i -> matches(i,graph,kind,state,includeCancelled,assigneeId,unassigned,scopeId,from,to,q,label)).toList();
        var asOf=LocalDate.now(ZoneOffset.UTC);
        var responses=all.stream().map(i -> response(i,graph,all,asOf,plan.targetStart,plan.targetEnd)).toList();
        var byId=responses.stream().collect(Collectors.toMap(ItemResponse::id,Function.identity()));
        return new Snapshot(projectId,plan.rowVersion,plan.targetStart,plan.targetEnd,asOf,responses,matched.stream().map(i->i.id).toList(),
            summary(all,all,graph,asOf,plan.targetStart,plan.targetEnd),true,all.size());
    }

    @Transactional public Snapshot updateTarget(UUID projectId,TargetWrite input,UUID user) {
        access.lockProject(projectId); access.requirePlanWriter(projectId,user); validateDates(input.targetStart(),input.targetEnd());
        var plan=plans.lockByProjectId(projectId).orElseGet(() -> { var p=new ProjectPlanEntity();p.projectId=projectId;p.rowVersion=0;return p; });
        if(input.rowVersion()==null||input.rowVersion()<0) throw new ValidationFailure("rowVersion","rowVersion is required and must be non-negative");long expected=input.rowVersion(); if(plan.rowVersion!=expected) throw stale(projectId);
        if(input.reason()!=null&&input.reason().length()>500) throw new ValidationFailure("reason","Reason too long");
        if (Objects.equals(plan.targetStart,input.targetStart()) && Objects.equals(plan.targetEnd,input.targetEnd())) return snapshot(projectId,user,null,null,false,null,false,null,null,null,null,null);
        Map<String,Object> before=planValues(plan); plan.targetStart=input.targetStart();plan.targetEnd=input.targetEnd();plan.rowVersion++;plan.updatedAt=Instant.now();plans.saveAndFlush(plan);
        var h=new ProjectPlanHistoryEntity();h.id=UUID.randomUUID();h.projectId=projectId;h.actorId=user;h.occurredAt=Instant.now();h.rowVersion=plan.rowVersion;h.reason=input.reason();h.beforeValues=before;h.afterValues=planValues(plan);planHistory.save(h);
        return snapshot(projectId,user,null,null,false,null,false,null,null,null,null,null);
    }

    @Transactional public ItemResponse create(UUID projectId,ItemWrite input,UUID user) {
        access.lockProject(projectId);access.requirePlanWriter(projectId,user);validateInput(input,false);
        var all=items.findByProjectIdOrderBySortOrderAscIdAsc(projectId); validateHierarchy(input.kind(),input.parentId(),null,all,projectId);
        access.requirePlanAssignee(projectId,input.assigneeId());
        var predecessorIds=normalizeDependencies(input.predecessorIds());
        validateDependencies(projectId,null,input.kind(),predecessorIds,all);
        if(input.requestId()!=null) {
            String hash=hash(payload(input,predecessorIds)); var prior=requests.findByProjectIdAndActorIdAndRequestId(projectId,user,input.requestId());
            if(prior.isPresent()) { if(!hash.equals(prior.get().payloadHash)) throw new IllegalStateException("PLAN_CREATION_PAYLOAD_MISMATCH");
                var existing=items.findByIdAndProjectId(prior.get().itemId,projectId).orElseThrow(NoSuchElementException::new); return response(existing,new Graph(items.findByProjectIdOrderBySortOrderAscIdAsc(projectId),dependencies.findByItemIdIn(all.stream().map(i->i.id).toList())),all,LocalDate.now(ZoneOffset.UTC),null,null); }
        }
        var item=new PlanItemEntity();item.id=UUID.randomUUID();item.projectId=projectId;apply(item,input);item.createdBy=user;item.rowVersion=0;item.updatedAt=Instant.now();items.saveAndFlush(item);
        replaceDependencies(item.id,predecessorIds);recordHistory(item,user,input.reason(),null,values(item,predecessorIds));
        if(input.requestId()!=null) { var request=new PlanItemCreationRequestEntity();request.projectId=projectId;request.actorId=user;request.requestId=input.requestId();request.payloadHash=hash(payload(input,predecessorIds));request.itemId=item.id;request.createdAt=Instant.now();requests.save(request); }
        var latest=items.findByProjectIdOrderBySortOrderAscIdAsc(projectId);return response(item,new Graph(latest,dependencies.findByItemIdIn(latest.stream().map(i->i.id).toList())),latest,LocalDate.now(ZoneOffset.UTC),null,null);
    }

    @Transactional public ItemResponse update(UUID projectId,UUID itemId,ItemWrite input,UUID user) {
        access.lockProject(projectId);access.requirePlanWriter(projectId,user);validateInput(input,true);
        var item=items.lockByIdAndProjectId(itemId,projectId).orElseThrow(NoSuchElementException::new);long expected=Objects.requireNonNull(input.rowVersion(),"rowVersion");
        if(item.rowVersion!=expected) throw stale(itemId);var all=items.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        validateHierarchy(input.kind(),input.parentId(),item,all,projectId);access.requirePlanAssignee(projectId,input.assigneeId());var predecessorIds=normalizeDependencies(input.predecessorIds());validateDependencies(projectId,item.id,input.kind(),predecessorIds,all);
        var beforeDeps=dependencies.findByItemId(item.id).stream().map(d->d.predecessorId).toList();Map<String,Object> before=values(item,beforeDeps);apply(item,input);item.rowVersion++;item.updatedAt=Instant.now();items.saveAndFlush(item);replaceDependencies(item.id,predecessorIds);recordHistory(item,user,input.reason(),before,values(item,predecessorIds));
        var latest=items.findByProjectIdOrderBySortOrderAscIdAsc(projectId);return response(item,new Graph(latest,dependencies.findByItemIdIn(latest.stream().map(i->i.id).toList())),latest,LocalDate.now(ZoneOffset.UTC),null,null);
    }

    public HistoryPage history(UUID projectId,UUID itemId,int page,int limit,UUID user) {
        access.role(projectId,user);if(page<0 || limit<1 || limit>100) throw new ValidationFailure("limit","Limit must be between 1 and 100");
        if(items.findByIdAndProjectId(itemId,projectId).isEmpty()) throw new NoSuchElementException();
        var result=history.findByItemIdOrderByAtDescIdDesc(itemId,PageRequest.of(page,limit));
        return new HistoryPage(result.getContent().stream().map(h->new HistoryEntry(h.id,h.actorId,h.at,h.rowVersion,h.reason,h.beforeValues,h.afterValues)).toList(),result.hasNext(),page,limit);
    }

    private ItemResponse response(PlanItemEntity i,Graph graph,List<PlanItemEntity> all,LocalDate asOf,LocalDate projectStart,LocalDate projectEnd) {
        var pred=graph.predecessors.getOrDefault(i.id,Set.of());var succ=graph.successors.getOrDefault(i.id,Set.of());
        var blockers=pred.stream().filter(id -> {var p=graph.byId.get(id);return p!=null && p.state!=PlanItemState.DONE && p.state!=PlanItemState.CANCELLED;}).toList();
        var scope=new ArrayList<PlanItemEntity>();scope.add(i);scope.addAll(descendants(i.id,all));
        return new ItemResponse(i.id,i.projectId,i.parentId,i.kind,i.title,i.description,i.assigneeId,i.state,i.targetStart,i.targetEnd,i.deadline,i.sortOrder,safeLabels(i.labels),i.rowVersion,i.createdBy,i.updatedAt,
            new ArrayList<>(pred),new ArrayList<>(succ),blockers,summary(all,scope,graph,asOf,i.targetStart,i.targetEnd));
    }
    private Summary summary(List<PlanItemEntity> all,List<PlanItemEntity> scope,Graph graph,LocalDate asOf,LocalDate manualStart,LocalDate manualEnd) {
        var tasks=scope.stream().filter(i->i.kind==PlanItemKind.TASK && i.state!=PlanItemState.CANCELLED).toList();
        long done=tasks.stream().filter(i->i.state==PlanItemState.DONE).count();long blocked=tasks.stream().filter(i->isBlocked(i,graph)).count();
        long overdue=tasks.stream().filter(i->i.state!=PlanItemState.DONE && isOverdue(i,asOf)).count();long unplanned=tasks.stream().filter(i->i.state!=PlanItemState.DONE && i.targetStart==null||i.state!=PlanItemState.DONE && i.targetEnd==null).count();
        long unassigned=tasks.stream().filter(i->i.state!=PlanItemState.DONE && i.assigneeId==null).count();
        Forecast forecast=forecast(tasks);boolean outside=(forecast.start!=null && manualStart!=null && forecast.start.isBefore(manualStart))||(forecast.end!=null&&manualEnd!=null&&forecast.end.isAfter(manualEnd));
        return new Summary(tasks.size(),done,blocked,overdue,unplanned,unassigned,tasks.isEmpty()?null:done*100.0/tasks.size(),forecast.start,forecast.end,forecast.state,outside);
    }
    private boolean isBlocked(PlanItemEntity i,Graph g) { if(i.state==PlanItemState.DONE||i.state==PlanItemState.CANCELLED)return false; return i.state==PlanItemState.BLOCKED || g.predecessors.getOrDefault(i.id,Set.of()).stream().map(g.byId::get).anyMatch(p->p!=null&&p.state!=PlanItemState.DONE&&p.state!=PlanItemState.CANCELLED); }
    private boolean isOverdue(PlanItemEntity i,LocalDate now) { LocalDate d=i.deadline!=null?i.deadline:i.targetEnd;return d!=null&&d.isBefore(now); }
    private Forecast forecast(List<PlanItemEntity> tasks) { if(tasks.isEmpty()) return new Forecast(null,null,ForecastState.EMPTY);boolean any=tasks.stream().anyMatch(i->i.targetStart!=null||i.targetEnd!=null);if(!any)return new Forecast(null,null,ForecastState.UNDATED);var starts=tasks.stream().map(i->i.targetStart).filter(Objects::nonNull).toList();var ends=tasks.stream().map(i->i.targetEnd).filter(Objects::nonNull).toList();if(tasks.stream().anyMatch(i->i.targetStart==null||i.targetEnd==null))return new Forecast(starts.stream().min(LocalDate::compareTo).orElse(null),ends.stream().max(LocalDate::compareTo).orElse(null),ForecastState.INCOMPLETE);return new Forecast(starts.stream().min(LocalDate::compareTo).orElse(null),ends.stream().max(LocalDate::compareTo).orElse(null),ForecastState.COMPLETE); }
    private record Forecast(LocalDate start,LocalDate end,ForecastState state) { }
    private List<PlanItemEntity> descendants(UUID id,List<PlanItemEntity> all) {var result=new ArrayList<PlanItemEntity>();var children=all.stream().filter(i->id.equals(i.parentId)).toList();for(var child:children){result.add(child);result.addAll(descendants(child.id,all));}return result;}
    private boolean matches(PlanItemEntity i,Graph g,String kind,PlanItemState state,boolean includeCancelled,UUID assigneeId,boolean unassigned,UUID scopeId,LocalDate from,LocalDate to,String q,String label) {
        if(!includeCancelled && state==null && i.state==PlanItemState.CANCELLED)return false;if(state!=null&&i.state!=state)return false;if(kind!=null&&!kind.isBlank()){var kinds=Arrays.stream(kind.split(",")).map(String::trim).filter(s->!s.isBlank()).map(s->parseKind(s,"kind")).collect(Collectors.toSet());if(!kinds.contains(i.kind))return false;}if(assigneeId!=null&&!assigneeId.equals(i.assigneeId))return false;if(unassigned&&i.assigneeId!=null)return false;
        if(scopeId!=null&&!scopeId.equals(i.id)&&!isDescendantOf(i,scopeId,g))return false;if(q!=null&&!q.isBlank()&&!((i.title!=null&&i.title.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))||(i.description!=null&&i.description.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))))return false;if(label!=null&&!safeLabels(i.labels).contains(label))return false;
        if(from!=null||to!=null){boolean period=overlaps(i.targetStart,i.targetEnd,from,to),deadline=i.deadline!=null&&inRange(i.deadline,from,to);if(!period&&!deadline)return false;}return true;
    }
    private boolean inRange(LocalDate d,LocalDate from,LocalDate to){return (from==null||!d.isBefore(from))&&(to==null||!d.isAfter(to));}
    private boolean overlaps(LocalDate start,LocalDate end,LocalDate from,LocalDate to){if(start==null&&end==null)return false;LocalDate s=start==null?end:start,e=end==null?start:end;return (to==null||!s.isAfter(to))&&(from==null||!e.isBefore(from));}
    private boolean isDescendantOf(PlanItemEntity item,UUID scope,Graph g){var current=item;Set<UUID> seen=new HashSet<>();while(current!=null&&current.parentId!=null&&seen.add(current.id)){if(scope.equals(current.parentId))return true;current=g.byId.get(current.parentId);}return false;}
    private static List<String> safeLabels(List<String> labels){return labels==null?List.of():List.copyOf(labels);}
    private ProjectPlanEntity virtualPlan(UUID id){var p=new ProjectPlanEntity();p.projectId=id;p.rowVersion=0;return p;}
    private void validateInput(ItemWrite i,boolean update){if(i==null)throw new ValidationFailure("item","Item is required");if(i.kind()==null)throw new ValidationFailure("kind","Kind is required");if(i.state()==null)throw new ValidationFailure("state","State is required");if(i.title()==null||i.title().trim().length()<1||i.title().trim().length()>200)throw new ValidationFailure("title","Title must contain 1 to 200 characters");if(i.description()!=null&&i.description().length()>10000)throw new ValidationFailure("description","Description too long");if(i.labels()!=null&&(i.labels().size()>20||i.labels().stream().anyMatch(v->v==null||v.length()>50)))throw new ValidationFailure("labels","At most 20 labels of 50 characters are allowed");if(i.sortOrder()==null)throw new ValidationFailure("sortOrder","sortOrder is required");if(i.reason()!=null&&i.reason().length()>500)throw new ValidationFailure("reason","Reason too long");validateDates(i.targetStart(),i.targetEnd());if(update&&(i.rowVersion()==null||i.rowVersion()<0))throw new ValidationFailure("rowVersion","rowVersion is required and must be non-negative");}
    private void validateDates(LocalDate start,LocalDate end){if(start!=null&&end!=null&&start.isAfter(end))throw new ValidationFailure("targetEnd","Must be on or after targetStart");}
    private void validateHierarchy(PlanItemKind kind,UUID parentId,PlanItemEntity current,List<PlanItemEntity> all,UUID projectId){if(current!=null&&kind!=current.kind&&all.stream().anyMatch(i->current.id.equals(i.parentId)))throw new ValidationFailure("kind","Container with children cannot change kind");if(parentId==null)return;var parent=all.stream().filter(i->parentId.equals(i.id)).findFirst().orElseThrow(()->new ValidationFailure("parentId","Parent is not in this project"));if(parent.id.equals(current==null?null:current.id))throw new ValidationFailure("parentId","An item cannot parent itself");if(kind==PlanItemKind.TOPIC&&parent.kind!=PlanItemKind.EPIC)throw new ValidationFailure("parentId","TOPIC must be under EPIC");if((kind==PlanItemKind.TASK||kind==PlanItemKind.MILESTONE)&&(parent.kind!=PlanItemKind.EPIC&&parent.kind!=PlanItemKind.TOPIC))throw new ValidationFailure("parentId","Executable items must be under EPIC or TOPIC");if(kind==PlanItemKind.EPIC)throw new ValidationFailure("parentId","EPIC must be a root item");}
    private void validateDependencies(UUID projectId,UUID itemId,PlanItemKind kind,List<UUID> ids,List<PlanItemEntity> all){if(kind!=PlanItemKind.TASK&&kind!=PlanItemKind.MILESTONE&&!ids.isEmpty())throw new ValidationFailure("predecessorIds","Only TASK and MILESTONE may have dependencies");if(ids.size()>200)throw new ValidationFailure("predecessorIds","At most 200 predecessors are allowed");var by=all.stream().collect(Collectors.toMap(i->i.id,Function.identity()));if(itemId!=null&&kind!=PlanItemKind.TASK&&kind!=PlanItemKind.MILESTONE&&!dependencies.findByPredecessorId(itemId).isEmpty())throw new ValidationFailure("kind","An item with successors cannot become a container");for(UUID id:ids){var p=by.get(id);if(p==null)throw new ValidationFailure("predecessorIds","Dependency must be in this project");if(p.kind!=PlanItemKind.TASK&&p.kind!=PlanItemKind.MILESTONE)throw new ValidationFailure("predecessorIds","Only TASK and MILESTONE may be predecessors");}var edges=new HashMap<UUID,Set<UUID>>();all.forEach(i->edges.put(i.id,new HashSet<>()));dependencies.findByItemIdIn(all.stream().map(i->i.id).toList()).forEach(d->edges.get(d.itemId).add(d.predecessorId));if(itemId!=null){edges.put(itemId,new HashSet<>(ids));}if(hasCycle(edges))throw new ValidationFailure("predecessorIds","Dependency cycle is not allowed");}
    private boolean hasCycle(Map<UUID,Set<UUID>> edges){Set<UUID> visiting=new HashSet<>(),done=new HashSet<>();for(UUID id:edges.keySet())if(cycle(id,edges,visiting,done))return true;return false;}private boolean cycle(UUID id,Map<UUID,Set<UUID>> e,Set<UUID> v,Set<UUID> d){if(d.contains(id))return false;if(!v.add(id))return true;for(UUID next:e.getOrDefault(id,Set.of()))if(cycle(next,e,v,d))return true;v.remove(id);d.add(id);return false;}
    private void replaceDependencies(UUID itemId,List<UUID> ids){dependencies.deleteAll(dependencies.findByItemId(itemId));for(UUID id:ids)dependencies.save(new PlanItemDependencyEntity(itemId,id));}
    private void apply(PlanItemEntity item,ItemWrite i){item.parentId=i.parentId();item.kind=i.kind();item.title=i.title().trim();item.description=i.description();item.assigneeId=i.assigneeId();item.state=i.state();item.targetStart=i.targetStart();item.targetEnd=i.targetEnd();item.deadline=i.deadline();item.sortOrder=i.sortOrder();item.labels=i.labels()==null?List.of():List.copyOf(new LinkedHashSet<>(i.labels()));}
    private List<UUID> normalizeDependencies(List<UUID> ids){if(ids==null)return List.of();var result=new ArrayList<UUID>(new LinkedHashSet<>(ids));if(result.contains(null))throw new ValidationFailure("predecessorIds","Null dependency");return result;}
    private String payload(ItemWrite i,List<UUID> deps){var values=Arrays.asList(i.parentId(),i.kind(),i.title().trim(),i.description(),i.assigneeId(),i.state(),i.targetStart(),i.targetEnd(),i.deadline(),i.sortOrder(),safeLabels(i.labels()),deps);return values.stream().map(this::canonical).collect(Collectors.joining());}
    private String canonical(Object value){if(value==null)return "N;";if(value instanceof Collection<?> c)return "L"+c.size()+"["+c.stream().map(this::canonical).collect(Collectors.joining())+"];";if(value instanceof Enum<?> e)return "E"+e.getDeclaringClass().getName()+":"+e.name()+";";String s=value.toString();return "S"+value.getClass().getName()+":"+s.length()+":"+s+";";}
    private String hash(String value){try{var d=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(d);}catch(Exception e){throw new IllegalStateException(e);}}
    private void recordHistory(PlanItemEntity i,UUID actor,String reason,Map<String,Object> before,Map<String,Object> after){var h=new PlanItemHistoryEntity();h.id=UUID.randomUUID();h.projectId=i.projectId;h.itemId=i.id;h.actorId=actor;h.at=i.updatedAt;h.rowVersion=i.rowVersion;h.reason=reason;h.beforeValues=before;h.afterValues=after;history.save(h);}
    private Map<String,Object> values(PlanItemEntity i,List<UUID> predecessorIds){var m=new LinkedHashMap<String,Object>();m.put("parentId",i.parentId);m.put("kind",i.kind);m.put("title",i.title);m.put("description",i.description);m.put("assigneeId",i.assigneeId);m.put("state",i.state);m.put("targetStart",i.targetStart);m.put("targetEnd",i.targetEnd);m.put("deadline",i.deadline);m.put("sortOrder",i.sortOrder);m.put("labels",safeLabels(i.labels));m.put("predecessorIds",predecessorIds);m.put("rowVersion",i.rowVersion);return m;}
    private Map<String,Object> planValues(ProjectPlanEntity p){var m=new LinkedHashMap<String,Object>();m.put("targetStart",p.targetStart);m.put("targetEnd",p.targetEnd);m.put("rowVersion",p.rowVersion);return m;}
    private RuntimeException stale(UUID id){return new ObjectOptimisticLockingFailureException(ProjectPlanEntity.class,id);}
    private static PlanItemKind parseKind(String value,String field){try{return PlanItemKind.valueOf(value.toUpperCase(Locale.ROOT));}catch(Exception e){throw new ValidationFailure(field,"Unknown kind");}}
    private record Graph(Map<UUID,PlanItemEntity> byId,Map<UUID,Set<UUID>> predecessors,Map<UUID,Set<UUID>> successors){Graph(List<PlanItemEntity> rows,List<PlanItemDependencyEntity> ds){this(rows.stream().collect(Collectors.toMap(i->i.id,Function.identity(),(a,b)->a,LinkedHashMap::new)),new HashMap<>(),new HashMap<>());rows.forEach(i->{predecessors.put(i.id,new LinkedHashSet<>());successors.put(i.id,new LinkedHashSet<>());});ds.forEach(d->{if(byId.containsKey(d.itemId)&&byId.containsKey(d.predecessorId)){predecessors.get(d.itemId).add(d.predecessorId);successors.get(d.predecessorId).add(d.itemId);}});}}
}
