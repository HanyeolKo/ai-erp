package com.aierp;

import com.aierp.googleworkspace.GoogleHttpClient;
import com.aierp.identity.api.GoogleAuthorizationService;
import com.aierp.projectplan.*;
import com.aierp.projectplan.api.ProjectPlanController.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/** CI Testcontainers coverage for additive V9 project-plan persistence and semantics. */
@SpringBootTest @Testcontainers(disabledWithoutDocker=false)
class ProjectPlanPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:8.2.9")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);registry.add("spring.data.redis.url",()->"redis://%s:%d".formatted(REDIS.getHost(),REDIS.getMappedPort(6379)));}
    @Autowired JdbcTemplate jdbc; @Autowired ProjectPlanService service;
    @MockitoBean GoogleAuthorizationService googleAuthorization; @MockitoBean GoogleHttpClient googleHttp;
    @MockitoSpyBean ProjectPlanRepository planRepository;
    UUID project,user,other,assignee; int sort;
    @BeforeEach void fixture(){user=UUID.randomUUID();other=UUID.randomUUID();assignee=UUID.randomUUID();project=UUID.randomUUID();var group=UUID.randomUUID();sort=0;jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",user,user+"@example.test","Plan User");jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",other,other+"@example.test","Other User");jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",assignee,assignee+"@example.test","Plan Assignee");jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",group,"Plan Group");jdbc.update("INSERT INTO \"group\".group_member(group_id,user_account_id) VALUES (?,?)",group,user);jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",project,group,"Plan Project");jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MEMBER')",project,user);jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MEMBER')",project,assignee);}

    @Test void persistsNestedForecastDependencyHistoryFullFiltersAndVersionConflicts(){
        var target=service.updateTarget(project,new TargetWrite(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),0L,"initial"),user);assertThat(target.rowVersion()).isEqualTo(1);
        var epic=service.create(project,write(null,PlanItemKind.EPIC,"Epic",PlanItemState.BACKLOG,null,null,List.of()),user);
        var topic=service.create(project,write(epic.id(),PlanItemKind.TOPIC,"Topic",PlanItemState.BACKLOG,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),List.of()),user);
        var leaf=service.create(project,write(topic.id(),PlanItemKind.TASK,"Leaf",PlanItemState.IN_PROGRESS,LocalDate.of(2026,10,5),LocalDate.of(2026,10,10),List.of()),user);
        var predecessor=service.create(project,write(null,PlanItemKind.TASK,"Predecessor",PlanItemState.IN_PROGRESS,null,null,List.of()),user);
        var dependent=service.create(project,write(null,PlanItemKind.TASK,"Dependent",PlanItemState.READY,null,null,List.of(predecessor.id())),user);
        var requestId=UUID.randomUUID();var idemInput=new ItemWrite(null,PlanItemKind.TASK,"Idempotent",null,null,PlanItemState.READY,null,null,null,sort++,List.of(),null,List.of(),requestId,null);
        var idemFirst=service.create(project,idemInput,user);var idemReplay=service.create(project,idemInput,user);assertThat(idemReplay.id()).isEqualTo(idemFirst.id());assertThat(jdbc.queryForObject("SELECT count(*) FROM project.plan_item_history WHERE item_id=?",Integer.class,idemFirst.id())).isEqualTo(1);
        var changed=new ItemWrite(null,PlanItemKind.TASK,"Changed",null,null,PlanItemState.READY,null,null,null,idemInput.sortOrder(),List.of(),null,List.of(),requestId,null);assertThatThrownBy(()->service.create(project,changed,user)).isInstanceOf(IllegalStateException.class).hasMessage("PLAN_CREATION_PAYLOAD_MISMATCH");
        var same=service.update(project,dependent.id(),writeUpdate(dependent, List.of(predecessor.id()),dependent.rowVersion()),user);var removed=service.update(project,same.id(),writeUpdate(same,List.of(),same.rowVersion()),user);
        var snapshot=service.snapshot(project,user,null,null,false,null,false,null,null,null,null,null);var topicRow=snapshot.items().stream().filter(i->i.id().equals(topic.id())).findFirst().orElseThrow();
        assertThat(snapshot.complete()).isTrue();assertThat(topicRow.summary().forecastState()).isEqualTo(ForecastState.COMPLETE);assertThat(topicRow.summary().forecastEnd()).isEqualTo(LocalDate.of(2026,10,10));assertThat(snapshot.summary().forecastEnd()).isEqualTo(LocalDate.of(2026,10,10));
        assertThat(snapshot.items()).hasSize(6);assertThat(snapshot.items().stream().filter(i->i.kind()==PlanItemKind.TASK).count()).isEqualTo(4);
        var historyRows=jdbc.queryForList("SELECT before_values::text,after_values::text FROM project.plan_item_history WHERE item_id=? ORDER BY at",dependent.id());assertThat(historyRows).hasSize(3);assertThat(historyRows.get(1).get("after_values").toString()).contains(predecessor.id().toString());assertThat(historyRows.get(2).get("after_values").toString()).contains("predecessorIds");assertThat(historyRows.get(2).get("after_values").toString()).doesNotContain(predecessor.id().toString());
        for(int i=0;i<21;i++)service.create(project,write(null,PlanItemKind.TASK,"Filter "+i,PlanItemState.READY,null,null,List.of()),user);
        assertThat(service.snapshot(project,user,"TASK",null,false,null,false,null,null,null,"Filter",null).matchedIds()).hasSize(21);
        assertThatThrownBy(()->service.updateTarget(project,new TargetWrite(LocalDate.of(2026,10,2),null,0L,"stale"),user)).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        var invalidAssignee=new ItemWrite(null,PlanItemKind.TASK,"Invalid assignee",null,other,PlanItemState.READY,null,null,null,sort++,List.of(),null,List.of(),null,null);
        assertThatThrownBy(()->service.create(project,invalidAssignee,user)).isInstanceOf(com.aierp.platform.web.ValidationFailure.class).hasMessageContaining("Assignee");
        jdbc.update("UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?",project,user);assertThatThrownBy(()->service.create(project,write(null,PlanItemKind.TASK,"Denied",PlanItemState.READY,null,null,List.of()),user)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.snapshot(project,other,null,null,false,null,false,null,null,null,null,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(googleAuthorization,googleHttp);
    }

    @Test void concurrentItemWritesWithSameVersionHaveOneWinnerAndOneHistoryRow() throws Exception {
        var item=service.create(project,write(null,PlanItemKind.TASK,"Concurrent",PlanItemState.READY,null,null,List.of()),user);
        var revision=item.rowVersion();
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> update=()->{gate.await();try {
                service.update(project,item.id(),writeUpdate(item,List.of(),revision),user);return true;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException conflict) {return false;}};
            var first=executor.submit(update);var second=executor.submit(update);gate.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT row_version FROM project.plan_item WHERE id=?",Long.class,item.id())).isEqualTo(revision+1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.plan_item_history WHERE item_id=?",Integer.class,item.id())).isEqualTo(2);
        verifyNoInteractions(googleAuthorization,googleHttp);
    }

    @Test void idempotentReplaySkipsCurrentAssigneeValidationButNewRequestDoesNot(){
        var requestId=UUID.randomUUID();
        var input=new ItemWrite(null,PlanItemKind.TASK,"Assigned replay",null,assignee,PlanItemState.READY,null,null,null,sort++,List.of(),null,List.of(),requestId,null);
        var first=service.create(project,input,user);
        jdbc.update("UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?",project,assignee);
        var replay=service.create(project,input,user);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.plan_item_history WHERE item_id=?",Integer.class,first.id())).isEqualTo(1);
        var changed=new ItemWrite(null,PlanItemKind.TASK,"Changed replay",null,assignee,PlanItemState.READY,null,null,null,input.sortOrder(),List.of(),null,List.of(),requestId,null);
        assertThatThrownBy(()->service.create(project,changed,user)).isInstanceOf(IllegalStateException.class).hasMessage("PLAN_CREATION_PAYLOAD_MISMATCH");
        var newRequest=new ItemWrite(null,PlanItemKind.TASK,"New request",null,assignee,PlanItemState.READY,null,null,null,sort++,List.of(),null,List.of(),UUID.randomUUID(),null);
        assertThatThrownBy(()->service.create(project,newRequest,user)).isInstanceOf(com.aierp.platform.web.ValidationFailure.class).hasMessageContaining("Assignee");
        verifyNoInteractions(googleAuthorization,googleHttp);
    }

    @Test void projectLockMakesCommittedRoleRevocationWinBeforePlanWrite() throws Exception {
        service.create(project,write(null,PlanItemKind.TASK,"Before revocation",PlanItemState.READY,null,null,List.of()),user);
        jdbc.update("UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?",project,user);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.plan_item WHERE project_id=?",Integer.class,project)).isEqualTo(1);
        jdbc.update("UPDATE project.project_member SET role='MEMBER' WHERE project_id=? AND user_account_id=?",project,user);
        try(var connection=jdbc.getDataSource().getConnection();var lock=connection.prepareStatement("SELECT id FROM project.project WHERE id=? FOR UPDATE")) {
            connection.setAutoCommit(false);lock.setObject(1,project);try(var rows=lock.executeQuery()){assertThat(rows.next()).isTrue();}
            try(var executor=Executors.newSingleThreadExecutor()) {
                var pending=executor.submit(()->{try {service.create(project,write(null,PlanItemKind.TASK,"After revocation",PlanItemState.READY,null,null,List.of()),user);return null;}catch(Throwable failure){return failure;}});
                jdbc.update("UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?",project,user);
                connection.commit();
                assertThat(pending.get(15,TimeUnit.SECONDS)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.plan_item WHERE project_id=?",Integer.class,project)).isEqualTo(1);
        verifyNoInteractions(googleAuthorization,googleHttp);
    }

    @Test void repeatableReadSnapshotDoesNotMixPlanAndItemVersions() throws Exception {
        service.updateTarget(project,new TargetWrite(LocalDate.of(2026,10,1),LocalDate.of(2026,10,10),0L,"A"),user);
        var item=service.create(project,write(null,PlanItemKind.TASK,"Snapshot A",PlanItemState.READY,LocalDate.of(2026,10,3),LocalDate.of(2026,10,5),List.of()),user);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation->{var result=invocation.callRealMethod();if(Thread.currentThread().getName().equals("project-plan-snapshot-reader")){entered.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("snapshot barrier timeout");}return result;}).when(planRepository).findById(project);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var reader=executor.submit(()->{Thread.currentThread().setName("project-plan-snapshot-reader");return service.snapshot(project,user,null,null,false,null,false,null,null,null,null,null);});
            assertThat(entered.await(15,TimeUnit.SECONDS)).isTrue();
            updatePlanAndItemAtomically(item.id());
            release.countDown();
            var snapshot=reader.get(15,TimeUnit.SECONDS);
            assertThat(snapshot.targetStart()).isEqualTo(LocalDate.of(2026,10,1));
            assertThat(snapshot.targetEnd()).isEqualTo(LocalDate.of(2026,10,10));
            var task=snapshot.items().stream().filter(row->row.id().equals(item.id())).findFirst().orElseThrow();
            assertThat(task.targetStart()).isEqualTo(LocalDate.of(2026,10,3));
            assertThat(task.targetEnd()).isEqualTo(LocalDate.of(2026,10,5));
        } finally {release.countDown();org.mockito.Mockito.reset(planRepository);}
        assertThat(jdbc.queryForObject("SELECT target_start FROM project.project_plan WHERE project_id=?",LocalDate.class,project)).isEqualTo(LocalDate.of(2026,11,1));
        assertThat(jdbc.queryForObject("SELECT target_start FROM project.plan_item WHERE id=?",LocalDate.class,item.id())).isEqualTo(LocalDate.of(2026,11,3));
        verifyNoInteractions(googleAuthorization,googleHttp);
    }

    private void updatePlanAndItemAtomically(UUID itemId) throws Exception {
        try(var connection=jdbc.getDataSource().getConnection();var plan=connection.prepareStatement("UPDATE project.project_plan SET target_start=?,target_end=?,row_version=row_version+1 WHERE project_id=?");var item=connection.prepareStatement("UPDATE project.plan_item SET target_start=?,target_end=?,row_version=row_version+1 WHERE id=?")) {
            connection.setAutoCommit(false);
            plan.setObject(1,LocalDate.of(2026,11,1));plan.setObject(2,LocalDate.of(2026,11,10));plan.setObject(3,project);plan.executeUpdate();
            item.setObject(1,LocalDate.of(2026,11,3));item.setObject(2,LocalDate.of(2026,11,5));item.setObject(3,itemId);item.executeUpdate();
            connection.commit();
        }
    }
    private ItemWrite write(UUID parent,PlanItemKind kind,String title,PlanItemState state,LocalDate start,LocalDate end,List<UUID> deps){return new ItemWrite(parent,kind,title,null,null,state,start,end,null,sort++,List.of(),null,deps,UUID.randomUUID(),null);}
    private ItemWrite writeUpdate(ItemResponse current,List<UUID> deps,long version){return new ItemWrite(current.parentId(),current.kind(),current.title(),current.description(),current.assigneeId(),current.state(),current.targetStart(),current.targetEnd(),current.deadline(),current.sortOrder(),current.labels(),version,deps,null,"dependency change");}
}
