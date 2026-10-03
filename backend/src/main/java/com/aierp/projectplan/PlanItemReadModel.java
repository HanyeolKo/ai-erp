package com.aierp.projectplan;

import com.aierp.projectplan.api.ProjectPlanController;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure, repository-free read context shared by snapshot and bounded work projections. */
final class PlanItemReadModel {
    private PlanItemReadModel() { }
    static Context context(List<PlanItemEntity> rows, List<PlanItemDependencyEntity> edges, boolean includeHierarchy) { return new Context(rows, edges, includeHierarchy); }
    static final class Context {
        private final List<PlanItemEntity> rows; private final Graph graph; private final boolean includeHierarchy;
        private Context(List<PlanItemEntity> rows, List<PlanItemDependencyEntity> edges, boolean includeHierarchy) { this.rows=List.copyOf(rows); this.graph=new Graph(rows,edges); this.includeHierarchy=includeHierarchy; }
        ProjectPlanController.ItemResponse response(PlanItemEntity item,LocalDate asOf,LocalDate manualStart,LocalDate manualEnd) {
            var pred=graph.predecessors.getOrDefault(item.id,Set.of());var succ=graph.successors.getOrDefault(item.id,Set.of());
            var blockers=pred.stream().filter(id->{var p=graph.byId.get(id);return p!=null&&p.state!=PlanItemState.DONE&&p.state!=PlanItemState.CANCELLED;}).toList();
            var scope=new ArrayList<PlanItemEntity>();scope.add(item);if(includeHierarchy)scope.addAll(descendants(item.id));
            return new ProjectPlanController.ItemResponse(item.id,item.projectId,item.parentId,item.kind,item.title,item.description,item.assigneeId,item.state,item.targetStart,item.targetEnd,item.deadline,item.sortOrder,safeLabels(item.labels),item.rowVersion,item.createdBy,item.updatedAt,new ArrayList<>(pred),new ArrayList<>(succ),blockers,summary(scope,asOf,item.targetStart,item.targetEnd));
        }
        private List<PlanItemEntity> descendants(UUID id){var result=new ArrayList<PlanItemEntity>();rows.stream().filter(i->id.equals(i.parentId)).forEach(child->{result.add(child);result.addAll(descendants(child.id));});return result;}
        private ProjectPlanController.Summary summary(List<PlanItemEntity> scope,LocalDate asOf,LocalDate manualStart,LocalDate manualEnd){var tasks=scope.stream().filter(i->i.kind==PlanItemKind.TASK&&i.state!=PlanItemState.CANCELLED).toList();long done=tasks.stream().filter(i->i.state==PlanItemState.DONE).count();long blocked=tasks.stream().filter(this::isBlocked).count();long overdue=tasks.stream().filter(i->i.state!=PlanItemState.DONE&&isOverdue(i,asOf)).count();long unplanned=tasks.stream().filter(i->i.state!=PlanItemState.DONE&&(i.targetStart==null||i.targetEnd==null)).count();long unassigned=tasks.stream().filter(i->i.state!=PlanItemState.DONE&&i.assigneeId==null).count();var forecast=forecast(tasks);boolean outside=forecast.start!=null&&manualStart!=null&&forecast.start.isBefore(manualStart)||forecast.end!=null&&manualEnd!=null&&forecast.end.isAfter(manualEnd);return new ProjectPlanController.Summary(tasks.size(),done,blocked,overdue,unplanned,unassigned,tasks.isEmpty()?null:done*100.0/tasks.size(),forecast.start,forecast.end,forecast.state,outside);}
        private boolean isBlocked(PlanItemEntity i){if(i.state==PlanItemState.DONE||i.state==PlanItemState.CANCELLED)return false;return i.state==PlanItemState.BLOCKED||graph.predecessors.getOrDefault(i.id,Set.of()).stream().map(graph.byId::get).anyMatch(p->p!=null&&p.state!=PlanItemState.DONE&&p.state!=PlanItemState.CANCELLED);}
        private static boolean isOverdue(PlanItemEntity i,LocalDate asOf){var d=i.deadline!=null?i.deadline:i.targetEnd;return d!=null&&d.isBefore(asOf);}
        private static Forecast forecast(List<PlanItemEntity> tasks){if(tasks.isEmpty())return new Forecast(null,null,ForecastState.EMPTY);if(tasks.stream().noneMatch(i->i.targetStart!=null||i.targetEnd!=null))return new Forecast(null,null,ForecastState.UNDATED);var starts=tasks.stream().map(i->i.targetStart).filter(Objects::nonNull).toList();var ends=tasks.stream().map(i->i.targetEnd).filter(Objects::nonNull).toList();var state=tasks.stream().anyMatch(i->i.targetStart==null||i.targetEnd==null)?ForecastState.INCOMPLETE:ForecastState.COMPLETE;return new Forecast(starts.stream().min(LocalDate::compareTo).orElse(null),ends.stream().max(LocalDate::compareTo).orElse(null),state);}
        private static List<String> safeLabels(List<String> labels){return labels==null?List.of():List.copyOf(labels);}
    }
    private record Forecast(LocalDate start,LocalDate end,ForecastState state){}
    private record Graph(Map<UUID,PlanItemEntity> byId,Map<UUID,Set<UUID>> predecessors,Map<UUID,Set<UUID>> successors){Graph(List<PlanItemEntity> rows,List<PlanItemDependencyEntity> edges){this(rows.stream().collect(Collectors.toMap(i->i.id,Function.identity(),(a,b)->a,LinkedHashMap::new)),new HashMap<>(),new HashMap<>());rows.forEach(i->{predecessors.put(i.id,new LinkedHashSet<>());successors.put(i.id,new LinkedHashSet<>());});edges.forEach(d->{if(byId.containsKey(d.itemId)&&byId.containsKey(d.predecessorId)){predecessors.get(d.itemId).add(d.predecessorId);successors.get(d.predecessorId).add(d.itemId);}});}}
}