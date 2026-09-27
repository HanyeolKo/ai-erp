package com.aierp;

import com.aierp.project.api.ProjectAccess;
import com.aierp.projectplan.*;
import com.aierp.projectplan.api.ProjectPlanController.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.aierp.platform.web.ValidationFailure;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectPlanServiceTest {
    @Mock ProjectPlanRepository plans; @Mock PlanItemRepository items; @Mock PlanItemDependencyRepository dependencies;
    @Mock PlanItemHistoryRepository history; @Mock ProjectPlanHistoryRepository planHistory; @Mock PlanItemCreationRequestRepository requests; @Mock ProjectAccess access;
    ProjectPlanService service; final UUID project=UUID.randomUUID(); final UUID user=UUID.randomUUID();
    @BeforeEach void setUp(){service=new ProjectPlanService(plans,items,dependencies,history,planHistory,requests,access);lenient().when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(List.of());lenient().when(dependencies.findByItemIdIn(any())).thenReturn(List.of());lenient().when(access.role(project,user)).thenReturn("MEMBER");}

    @Test void snapshotUsesWholeProjectAndExcludesCancelledByDefault(){
        var done=item(PlanItemKind.TASK,PlanItemState.DONE,LocalDate.of(2026,9,1),LocalDate.of(2026,9,2));
        var cancelled=item(PlanItemKind.TASK,PlanItemState.CANCELLED,LocalDate.of(2026,9,3),LocalDate.of(2026,9,4));
        var unfinished=item(PlanItemKind.TASK,PlanItemState.IN_PROGRESS,null,null);var rows=List.of(done,cancelled,unfinished);
        when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(rows);when(plans.findById(project)).thenReturn(Optional.empty());
        var snapshot=service.snapshot(project,user,null,null,false,null,false,null,null,null,null,null);
        assertThat(snapshot.complete()).isTrue();assertThat(snapshot.totalCount()).isEqualTo(3);assertThat(snapshot.matchedIds()).containsExactly(done.id,unfinished.id);
        assertThat(snapshot.summary().taskCount()).isEqualTo(2);assertThat(snapshot.summary().doneCount()).isEqualTo(1);assertThat(snapshot.summary().forecastState()).isEqualTo(ForecastState.INCOMPLETE);
    }

    @Test void dependencyBlockerAndDirectSuccessorAreExposed(){
        var predecessor=item(PlanItemKind.TASK,PlanItemState.IN_PROGRESS,null,null);var task=item(PlanItemKind.TASK,PlanItemState.READY,null,null);var rows=List.of(predecessor,task);
        when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(rows);when(plans.findById(project)).thenReturn(Optional.empty());when(dependencies.findByItemIdIn(any())).thenReturn(List.of(new PlanItemDependencyEntity(task.id,predecessor.id)));
        var snapshot=service.snapshot(project,user,null,null,false,null,false,null,null,null,null,null);var response=snapshot.items().stream().filter(i->i.id().equals(task.id)).findFirst().orElseThrow();var predResponse=snapshot.items().stream().filter(i->i.id().equals(predecessor.id)).findFirst().orElseThrow();
        assertThat(response.blockerIds()).containsExactly(predecessor.id);assertThat(response.predecessorIds()).containsExactly(predecessor.id);assertThat(predResponse.successorIds()).containsExactly(task.id);
    }

    @Test void changingContainerToLeafWithChildrenIsRejected(){
        var epic=item(PlanItemKind.EPIC,PlanItemState.BACKLOG,null,null);var child=item(PlanItemKind.TOPIC,PlanItemState.BACKLOG,null,null);child.parentId=epic.id;when(items.lockByIdAndProjectId(epic.id,project)).thenReturn(Optional.of(epic));when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(List.of(epic,child));
        var input=new ItemWrite(null,PlanItemKind.TASK,"x",null,null,PlanItemState.BACKLOG,null,null,null,0,List.of(),0L,List.of(),null,null);
        assertThatThrownBy(()->service.update(project,epic.id,input,user)).isInstanceOf(ValidationFailure.class).hasMessageContaining("children");
    }

    @Test void dependencyCycleIsRejectedBeforePersistence(){
        var first=item(PlanItemKind.TASK,PlanItemState.READY,null,null);var second=item(PlanItemKind.TASK,PlanItemState.READY,null,null);var rows=List.of(first,second);when(items.lockByIdAndProjectId(second.id,project)).thenReturn(Optional.of(second));when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(rows);when(dependencies.findByItemIdIn(any())).thenReturn(List.of(new PlanItemDependencyEntity(first.id,second.id)));
        var input=new ItemWrite(null,PlanItemKind.TASK,"second",null,null,PlanItemState.READY,null,null,null,0,List.of(),0L,List.of(first.id),null,null);
        assertThatThrownBy(()->service.update(project,second.id,input,user)).isInstanceOf(ValidationFailure.class).hasMessageContaining("cycle");verify(items,never()).saveAndFlush(any());
    }

    @Test void stalePlanVersionCannotOverwrite(){
        var plan=new ProjectPlanEntity();plan.projectId=project;plan.rowVersion=2;when(plans.lockByProjectId(project)).thenReturn(Optional.of(plan));
        var input=new TargetWrite(LocalDate.of(2026,10,1),null,1L,"reason");assertThatThrownBy(()->service.updateTarget(project,input,user)).isInstanceOf(ObjectOptimisticLockingFailureException.class);verify(plans,never()).saveAndFlush(any());
    }

    @Test void ineligibleAssigneeIsAFieldValidationFailure(){
        doThrow(new ValidationFailure("assigneeId","Assignee must be an active project MANAGER or MEMBER")).when(access).requirePlanAssignee(eq(project),any());
        var input=new ItemWrite(null,PlanItemKind.TASK,"Assigned",null,UUID.randomUUID(),PlanItemState.READY,null,null,null,0,List.of(),null,List.of(),null,null);
        assertThatThrownBy(()->service.create(project,input,user)).isInstanceOf(ValidationFailure.class).hasMessageContaining("active project");
    }

    @Test void invalidKindIsRejectedEvenWhenProjectHasNoItems(){
        assertThatThrownBy(()->service.snapshot(project,user,"NOT_A_KIND",null,false,null,false,null,null,null,null,null))
            .isInstanceOf(ValidationFailure.class).hasMessageContaining("Unknown kind");
    }

    @Test void progressUsesDoneTasksAndPartialForecastRetainsKnownBounds(){
        var done=item(PlanItemKind.TASK,PlanItemState.DONE,LocalDate.of(2026,10,1),LocalDate.of(2026,10,2));
        var startOnly=item(PlanItemKind.TASK,PlanItemState.IN_PROGRESS,LocalDate.of(2026,10,3),null);
        var endOnly=item(PlanItemKind.TASK,PlanItemState.READY,null,LocalDate.of(2026,10,10));
        when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(List.of(done,startOnly,endOnly));
        when(plans.findById(project)).thenReturn(Optional.empty());
        var summary=service.snapshot(project,user,null,null,false,null,false,null,null,null,null,null).summary();
        assertThat(summary.progressPercent()).isEqualTo(100.0/3.0);
        assertThat(summary.forecastState()).isEqualTo(ForecastState.INCOMPLETE);
        assertThat(summary.forecastStart()).isEqualTo(LocalDate.of(2026,10,1));
        assertThat(summary.forecastEnd()).isEqualTo(LocalDate.of(2026,10,10));
    }

    @Test void emptyProjectHasNoProgressDenominator(){
        when(plans.findById(project)).thenReturn(Optional.empty());
        var summary=service.snapshot(project,user,null,null,false,null,false,null,null,null,null,null).summary();
        assertThat(summary.taskCount()).isZero();
        assertThat(summary.progressPercent()).isNull();
        assertThat(summary.forecastState()).isEqualTo(ForecastState.EMPTY);
    }

    @Test void dateFilterMatchesTargetPeriodOrDeadlineAndStateOverridesCancellationDefault(){
        var period=item(PlanItemKind.TASK,PlanItemState.READY,LocalDate.of(2026,10,5),LocalDate.of(2026,10,6));
        var deadline=item(PlanItemKind.TASK,PlanItemState.READY,null,null);deadline.deadline=LocalDate.of(2026,10,7);
        var cancelled=item(PlanItemKind.TASK,PlanItemState.CANCELLED,LocalDate.of(2026,10,5),LocalDate.of(2026,10,6));
        when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(List.of(period,deadline,cancelled));
        when(plans.findById(project)).thenReturn(Optional.empty());
        var filtered=service.snapshot(project,user,null,null,false,null,false,null,LocalDate.of(2026,10,5),LocalDate.of(2026,10,7),null,null);
        assertThat(filtered.matchedIds()).containsExactly(period.id,deadline.id);
        var cancelledState=service.snapshot(project,user,null,PlanItemState.CANCELLED,false,null,false,null,null,null,null,null);
        assertThat(cancelledState.matchedIds()).containsExactly(cancelled.id);
    }

    @Test void incomingDependencyPreventsChangingTaskIntoContainer(){
        var predecessor=item(PlanItemKind.TASK,PlanItemState.READY,null,null);
        var current=item(PlanItemKind.TASK,PlanItemState.READY,null,null);
        when(items.lockByIdAndProjectId(current.id,project)).thenReturn(Optional.of(current));
        when(items.findByProjectIdOrderBySortOrderAscIdAsc(project)).thenReturn(List.of(predecessor,current));
        when(dependencies.findByPredecessorId(current.id)).thenReturn(List.of(new PlanItemDependencyEntity(predecessor.id,current.id)));
        var input=new ItemWrite(null,PlanItemKind.EPIC,"container",null,null,PlanItemState.READY,null,null,null,0,List.of(),0L,List.of(),null,null);
        assertThatThrownBy(()->service.update(project,current.id,input,user)).isInstanceOf(ValidationFailure.class).hasMessageContaining("successors");
        verify(items,never()).saveAndFlush(any());
    }

    @Test void staleItemVersionCannotOverwrite(){
        var current=item(PlanItemKind.TASK,PlanItemState.READY,null,null);current.rowVersion=2;
        when(items.lockByIdAndProjectId(current.id,project)).thenReturn(Optional.of(current));
        var input=new ItemWrite(null,PlanItemKind.TASK,"stale",null,null,PlanItemState.READY,null,null,null,0,List.of(),1L,List.of(),null,null);
        assertThatThrownBy(()->service.update(project,current.id,input,user)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(items,never()).saveAndFlush(any());
    }

    private PlanItemEntity item(PlanItemKind kind,PlanItemState state,LocalDate start,LocalDate end){var i=new PlanItemEntity();i.id=UUID.randomUUID();i.projectId=project;i.kind=kind;i.state=state;i.title=kind.name();i.sortOrder=0;i.targetStart=start;i.targetEnd=end;i.labels=List.of();i.rowVersion=0;i.createdBy=user;i.updatedAt=Instant.now();return i;}
}
