package com.aierp;

import com.aierp.googleworkspace.GoogleHttpClient;
import com.aierp.identity.api.GoogleAuthorizationService;
import com.aierp.project.api.ProjectManagementDefinitionAccess;
import com.aierp.project.api.ManagementMutationAccess;
import com.aierp.project.api.ProjectAccess;
import com.aierp.projectplan.*;
import com.aierp.projectplan.api.ProjectManagementController.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

/** CI-only PostgreSQL coverage for V11 schema, actor-scoped receipts, audit, and FK/version behavior. */
@SpringBootTest @Testcontainers(disabledWithoutDocker=false)
class ProjectManagementFoundationPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:8.2.9")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.data.redis.url",()->"redis://%s:%d".formatted(REDIS.getHost(),REDIS.getMappedPort(6379)));r.add("spring.flyway.locations",()->"classpath:db/migration,classpath:db/integration-migration");}
    @Autowired JdbcTemplate jdbc; @Autowired ProjectManagementDefinitionAccess definitions; @Autowired ProjectManagementService management;
    @Autowired TaskExecutionRepository executions; @Autowired PlanItemRepository planItems; @Autowired PlanItemDependencyRepository dependencies; @Autowired ManagementMutationAccess mutations; @Autowired ProjectAccess projectAccess; @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean GoogleAuthorizationService googleAuthorization; @MockitoBean GoogleHttpClient googleHttp;
    UUID project,user,group,item;
    @BeforeEach void fixture(){user=UUID.randomUUID();project=UUID.randomUUID();group=UUID.randomUUID();item=UUID.randomUUID();jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",user,user+"@example.test","PM User");jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",group,"PM Group");jdbc.update("INSERT INTO \"group\".group_member(group_id,user_account_id) VALUES (?,?)",group,user);jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",project,group,"PM Project");jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",project,user);jdbc.update("INSERT INTO project.plan_item(id,project_id,kind,title,state,sort_order,labels,row_version,created_by) VALUES (?,?,'TASK','Task','READY',0,'[]'::jsonb,0,?)",item,project,user);}
    @Test void v11PersistsDefinitionTaskMetadataAuditAndIdempotentReceipt(){
        var request=UUID.randomUUID();var written=definitions.update(project,new ProjectManagementDefinitionAccess.DefinitionWrite("Purpose","Criteria",user,"WATCH","Reason",LocalDate.of(2026,10,3),0L,request),user);assertThat(written.rowVersion()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT purpose FROM project.management_definition WHERE project_id=?",String.class,project)).isEqualTo("Purpose");
        var taskRequest=UUID.randomUUID();var task=management.updateTaskExecution(project,item,new TaskExecutionWrite(TaskPriority.HIGH,"Done",0L,taskRequest),user);assertThat(task.rowVersion()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT priority FROM project.task_execution WHERE item_id=?",String.class,item)).isEqualTo("HIGH");assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_audit WHERE project_id=?",Integer.class,project)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE project_id=?",Integer.class,project)).isEqualTo(2);
        var replay=management.updateTaskExecution(project,item,new TaskExecutionWrite(TaskPriority.HIGH,"Done",0L,taskRequest),user);assertThat(replay.rowVersion()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_audit WHERE resource_id=?",Integer.class,item)).isEqualTo(1);
        assertThatThrownBy(()->management.updateTaskExecution(project,item,new TaskExecutionWrite(TaskPriority.LOW,"Changed",0L,taskRequest),user)).isInstanceOf(IllegalStateException.class).hasMessage("REQUEST_PAYLOAD_MISMATCH");
    }

    @Test void legacyPlanRowsSurviveV11AndCompositeTaskForeignKeyRejectsCrossProjectMetadata(){
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.plan_item WHERE id=?",Integer.class,item)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.task_execution WHERE item_id=?",Integer.class,item)).isZero();
        var otherProject=UUID.randomUUID(); var otherGroup=UUID.randomUUID(); var otherItem=UUID.randomUUID();
        jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",otherGroup,"Other");
        jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",otherProject,otherGroup,"Other Project");
        jdbc.update("INSERT INTO project.plan_item(id,project_id,kind,title,state,sort_order,labels,row_version,created_by) VALUES (?,?,'TASK','Other','READY',0,'[]'::jsonb,0,?)",otherItem,otherProject,user);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO project.task_execution(item_id,project_id) VALUES (?,?)",item,otherProject)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void populatedV10MigratesToV11WithoutChangingLegacyPlanOrWorkspaceRows() throws Exception {
        var database="aierp_v10_to_v11_"+UUID.randomUUID().toString().replace("-","");
        var adminUrl="jdbc:postgresql://%s:%d/postgres".formatted(POSTGRES.getHost(),POSTGRES.getMappedPort(5432));
        var databaseUrl="jdbc:postgresql://%s:%d/%s".formatted(POSTGRES.getHost(),POSTGRES.getMappedPort(5432),database);
        try(var admin=DriverManager.getConnection(adminUrl,POSTGRES.getUsername(),POSTGRES.getPassword())){admin.createStatement().execute("CREATE DATABASE "+identifier(database));}
        var legacyUser=UUID.randomUUID();var legacyGroup=UUID.randomUUID();var legacyProject=UUID.randomUUID();var legacyItem=UUID.randomUUID();var legacySchedule=UUID.randomUUID();var legacyView=UUID.randomUUID();
        try {
            Flyway.configure().dataSource(databaseUrl,POSTGRES.getUsername(),POSTGRES.getPassword()).locations("classpath:db/migration").target("10").load().migrate();
        try(var db=DriverManager.getConnection(databaseUrl,POSTGRES.getUsername(),POSTGRES.getPassword())) {
                execute(db,"INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",legacyUser,"v10@example.test","V10");execute(db,"INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",legacyGroup,"V10 Group");execute(db,"INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",legacyProject,legacyGroup,"V10 Project");execute(db,"INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",legacyProject,legacyUser);execute(db,"INSERT INTO project.project_plan(project_id,row_version,target_start,target_end) VALUES (?,7,DATE '2030-01-01',DATE '2030-01-31')",legacyProject);execute(db,"INSERT INTO project.plan_item(id,project_id,kind,title,state,target_start,target_end,sort_order,labels,row_version,created_by) VALUES (?,?,'TASK','V10 TASK','IN_PROGRESS',DATE '2030-01-02',DATE '2030-01-05',4,'[\"legacy\"]'::jsonb,8,?)",legacyItem,legacyProject,legacyUser);execute(db,"INSERT INTO schedule.project_schedule(id,project_id,created_by,title,starts_at,ends_at,status,business_revision) VALUES (?,?,?,'V10 Schedule',TIMESTAMPTZ '2030-01-01 10:00:00+00',TIMESTAMPTZ '2030-01-01 11:00:00+00','CONFIRMED',3)",legacySchedule,legacyProject,legacyUser);execute(db,"INSERT INTO schedule.schedule_saved_view(id,project_id,name,scope,owner_id,config) VALUES (?,?,'Legacy view','PERSONAL',?, '{\"columns\":[\"title\",\"status\"],\"density\":\"compact\"}'::jsonb)",legacyView,legacyProject,legacyUser);
                assertThat(scalar(db,"SELECT title FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo("V10 TASK");
            }
            Flyway.configure().dataSource(databaseUrl,POSTGRES.getUsername(),POSTGRES.getPassword()).locations("classpath:db/migration").target("11").load().migrate();
            try(var db=DriverManager.getConnection(databaseUrl,POSTGRES.getUsername(),POSTGRES.getPassword())) {
                assertThat(scalar(db,"SELECT title FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo("V10 TASK");
                assertThat(scalar(db,"SELECT state FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo("IN_PROGRESS");
                assertThat(scalar(db,"SELECT target_start FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo(java.sql.Date.valueOf("2030-01-02"));
                assertThat(scalar(db,"SELECT target_end FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo(java.sql.Date.valueOf("2030-01-05"));
                assertThat(scalar(db,"SELECT labels FROM project.plan_item WHERE id=?",legacyItem).toString()).contains("legacy");
                assertThat(scalar(db,"SELECT row_version FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo(8L);
                assertThat(scalar(db,"SELECT target_start FROM project.project_plan WHERE project_id=?",legacyProject)).isEqualTo(java.sql.Date.valueOf("2030-01-01"));
                assertThat(scalar(db,"SELECT target_end FROM project.project_plan WHERE project_id=?",legacyProject)).isEqualTo(java.sql.Date.valueOf("2030-01-31"));
                assertThat(scalar(db,"SELECT row_version FROM project.project_plan WHERE project_id=?",legacyProject)).isEqualTo(7L);
                assertThat(scalar(db,"SELECT id FROM schedule.project_schedule WHERE id=?",legacySchedule)).isEqualTo(legacySchedule);
                assertThat(scalar(db,"SELECT title FROM schedule.project_schedule WHERE id=?",legacySchedule)).isEqualTo("V10 Schedule");
                assertThat(scalar(db,"SELECT status FROM schedule.project_schedule WHERE id=?",legacySchedule)).isEqualTo("CONFIRMED");
                assertThat(scalar(db,"SELECT business_revision FROM schedule.project_schedule WHERE id=?",legacySchedule)).isEqualTo(3L);
                assertThat(scalar(db,"SELECT starts_at FROM schedule.project_schedule WHERE id=?",legacySchedule).toString()).contains("2030-01-01 10:00:00");
                assertThat(scalar(db,"SELECT ends_at FROM schedule.project_schedule WHERE id=?",legacySchedule).toString()).contains("2030-01-01 11:00:00");
                assertThat(scalar(db,"SELECT scope FROM schedule.schedule_saved_view WHERE id=?",legacyView)).isEqualTo("PERSONAL");
                assertThat(scalar(db,"SELECT owner_id FROM schedule.schedule_saved_view WHERE id=?",legacyView)).isEqualTo(legacyUser);
                assertThat(scalar(db,"SELECT config = ?::jsonb FROM schedule.schedule_saved_view WHERE id=?","{\"columns\":[\"title\",\"status\"],\"density\":\"compact\"}",legacyView)).isEqualTo(true);
                assertThat(scalar(db,"SELECT count(*) FROM project.management_definition")).isEqualTo(0L);assertThat(scalar(db,"SELECT count(*) FROM project.task_execution")).isEqualTo(0L);
            }
        } finally { try(var admin=DriverManager.getConnection(adminUrl,POSTGRES.getUsername(),POSTGRES.getPassword())){admin.createStatement().execute("DROP DATABASE IF EXISTS "+identifier(database));} }
    }

    @Test void revokedActorCannotReplayAndConcurrentIdenticalRequestCreatesOneReceiptAndAudit() throws Exception {
        var request=UUID.randomUUID(); var input=new TaskExecutionWrite(TaskPriority.HIGH,"Done",0L,request);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Long> write=()->{gate.await();return management.updateTaskExecution(project,item,input,user).rowVersion();};
            var first=executor.submit(write);var second=executor.submit(write);gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactly(1L,1L);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE project_id=? AND request_id=?",Integer.class,project,request)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_audit WHERE project_id=? AND resource_id=?",Integer.class,project,item)).isEqualTo(1);
        jdbc.update("UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?",project,user);
        assertThatThrownBy(()->management.updateTaskExecution(project,item,input,user)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void sourceAndLedgerRollbackTogetherWhenLedgerWriteFailsAfterSourceWrite(){
        var seedRequest=UUID.randomUUID();management.updateTaskExecution(project,item,new TaskExecutionWrite(TaskPriority.HIGH,"Before failure",0L,seedRequest),user);
        var before=jdbc.queryForMap("SELECT priority,completion_criterion,row_version,updated_at FROM project.task_execution WHERE item_id=?",item);
        var beforeAudit=jdbc.queryForObject("SELECT count(*) FROM project.management_audit WHERE project_id=?",Integer.class,project);
        var beforeReceipts=jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE project_id=?",Integer.class,project);
        var failureRequest=UUID.randomUUID();var constraint="pm_test_receipt_reject_"+failureRequest.toString().replace("-","");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.update("ALTER TABLE project.management_mutation_receipt ADD CONSTRAINT \""+constraint+"\" CHECK (request_id <> '"+failureRequest+"'::uuid)"));
        try {
            assertThatThrownBy(() -> management.updateTaskExecution(project,item,new TaskExecutionWrite(TaskPriority.LOW,"After failure",1L,failureRequest),user)).isInstanceOf(Throwable.class);
            assertThat(jdbc.queryForMap("SELECT priority,completion_criterion,row_version,updated_at FROM project.task_execution WHERE item_id=?",item)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_audit WHERE project_id=?",Integer.class,project)).isEqualTo(beforeAudit);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE project_id=?",Integer.class,project)).isEqualTo(beforeReceipts);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE request_id=?",Integer.class,failureRequest)).isZero();
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.update("ALTER TABLE project.management_mutation_receipt DROP CONSTRAINT IF EXISTS \""+constraint+"\""));
        }
    }

    @Test void disabledServiceCannotReplayStoredReceipt(){
        var request=UUID.randomUUID();
        management.updateTaskExecution(project,item,new TaskExecutionWrite(TaskPriority.HIGH,"Replay guard",0L,request),user);
        var disabled=new ProjectManagementService(executions,mutations,planItems,dependencies,projectAccess,false);
         assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> disabled.updateTaskExecution(project,item,new TaskExecutionWrite(TaskPriority.HIGH,"Replay guard",0L,request),user)))
                .isInstanceOf(IllegalStateException.class).hasMessage("PROJECT_MANAGEMENT_DISABLED");
         assertThat(jdbc.queryForObject("SELECT row_version FROM project.task_execution WHERE item_id=?",Long.class,item)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_audit WHERE resource_id=?",Integer.class,item)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE request_id=?",Integer.class,request)).isEqualTo(1);
    }

    @Test void workPageExcludesUnrelatedAndRevokedProjectsBeforeCount(){
        var otherProject=UUID.randomUUID(); var otherGroup=UUID.randomUUID(); var otherItem=UUID.randomUUID();
         jdbc.update("UPDATE project.plan_item SET assignee_id=?, deadline=CURRENT_DATE, description=? WHERE id=?",user,"base task description",item);
        jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",otherGroup,"Unrelated");
        jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",otherProject,otherGroup,"Unrelated Project");
         jdbc.update("INSERT INTO project.plan_item(id,project_id,kind,title,description,state,assignee_id,deadline,sort_order,labels,row_version,created_by) VALUES (?,?,'TASK','Hidden','other task description','READY',?,CURRENT_DATE,0,'[]'::jsonb,0,?)",otherItem,otherProject,user,user);
        var scoped=management.work(WorkRange.ALL,ZoneOffset.UTC,null,WorkAssignee.MINE,0,50,null,user);
        assertThat(scoped.items()).extracting(row->row.projectId()).containsOnly(project);
        assertThat(scoped.totalCount()).isEqualTo((long)scoped.items().size());
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MEMBER')",otherProject,user);
         var beforeRevokeAll=management.work(WorkRange.ALL,ZoneOffset.UTC,null,WorkAssignee.MINE,0,1,null,user);
         assertThat(beforeRevokeAll.totalCount()).isEqualTo(2L);assertThat(beforeRevokeAll.items()).hasSize(1);assertThat(beforeRevokeAll.hasNext()).isTrue();
         var beforeRevokeToday=management.work(WorkRange.TODAY,ZoneOffset.UTC,null,WorkAssignee.MINE,0,50,null,user);
         assertThat(beforeRevokeToday.totalCount()).isEqualTo(2L);assertThat(beforeRevokeToday.items()).extracting(row->row.projectId()).contains(project,otherProject);
         var titleMatch=management.work(WorkRange.ALL,ZoneOffset.UTC,null,WorkAssignee.MINE,0,50,"Task",user);
         assertThat(titleMatch.totalCount()).isEqualTo(1L);assertThat(titleMatch.items()).extracting(row->row.item().id()).containsExactly(item);
         var descriptionMatch=management.work(WorkRange.ALL,ZoneOffset.UTC,null,WorkAssignee.MINE,0,50,"other task description",user);
         assertThat(descriptionMatch.totalCount()).isEqualTo(1L);assertThat(descriptionMatch.items()).extracting(row->row.item().id()).containsExactly(otherItem);
        jdbc.update("DELETE FROM project.project_member WHERE project_id=? AND user_account_id=?",otherProject,user);
         var revoked=management.work(WorkRange.ALL,ZoneOffset.UTC,null,WorkAssignee.MINE,0,1,null,user);
         assertThat(revoked.totalCount()).isEqualTo(1L);assertThat(revoked.items()).extracting(row->row.projectId()).containsExactly(project);assertThat(revoked.hasNext()).isFalse();assertThat(revoked.complete()).isTrue();
         var revokedEmpty=management.work(WorkRange.ALL,ZoneOffset.UTC,null,WorkAssignee.MINE,1,1,null,user);
         assertThat(revokedEmpty.items()).isEmpty();assertThat(revokedEmpty.totalCount()).isEqualTo(1L);assertThat(revokedEmpty.complete()).isTrue();assertThat(revokedEmpty.observedCount()).isZero();assertThat(revokedEmpty.hasNext()).isFalse();
        assertThatThrownBy(() -> management.work(WorkRange.ALL,ZoneOffset.UTC,otherProject,WorkAssignee.MINE,0,50,null,user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void concurrentDistinctRequestsHaveOneVersionWinnerAndNoLoserLedgerRows() throws Exception {
        var firstRequest=UUID.randomUUID(); var secondRequest=UUID.randomUUID();
        var firstInput=new TaskExecutionWrite(TaskPriority.HIGH,"First",0L,firstRequest);
        var secondInput=new TaskExecutionWrite(TaskPriority.LOW,"Second",0L,secondRequest);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Object> first=()->{gate.await();try{return management.updateTaskExecution(project,item,firstInput,user);}catch(Throwable failure){return failure;}};
            Callable<Object> second=()->{gate.await();try{return management.updateTaskExecution(project,item,secondInput,user);}catch(Throwable failure){return failure;}};
            var one=executor.submit(first);var two=executor.submit(second);gate.countDown();
            var results=List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
            assertThat(results.stream().filter(result->result instanceof TaskExecution).count()).isEqualTo(1);
            assertThat(results.stream().filter(result->result instanceof org.springframework.orm.ObjectOptimisticLockingFailureException).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT row_version FROM project.task_execution WHERE item_id=?",Integer.class,item)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_audit WHERE project_id=? AND resource_id=?",Integer.class,project,item)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE project_id=?",Integer.class,project)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE project_id=? AND request_id=?",Integer.class,project,firstRequest)
                + jdbc.queryForObject("SELECT count(*) FROM project.management_mutation_receipt WHERE project_id=? AND request_id=?",Integer.class,project,secondRequest)).isEqualTo(1);
    }

    @Test void personalWorkUsesInclusivePointsMatchingCountAndDependencyAttentionBeforePaging(){
        var predecessor=UUID.randomUUID();var dependent=UUID.randomUUID();var endOnlyToday=UUID.randomUUID();var startOnlyToday=UUID.randomUUID();var endOnlyPast=UUID.randomUUID();var startOnlyPast=UUID.randomUUID();var enclosing=UUID.randomUUID();var overdue=UUID.randomUUID();var today=LocalDate.now(ZoneOffset.UTC);
        insertTask(predecessor,"Predecessor",PlanItemState.IN_PROGRESS,null,null,null);insertTask(dependent,"Dependent",PlanItemState.READY,today,today,null);insertTask(endOnlyToday,"End only today",PlanItemState.READY,null,today,null);insertTask(startOnlyToday,"Start only today",PlanItemState.READY,today,null,null);insertTask(endOnlyPast,"End only past",PlanItemState.READY,null,today.minusDays(1),null);insertTask(startOnlyPast,"Start only past",PlanItemState.READY,today.minusDays(7),null,null);insertTask(enclosing,"Enclosing",PlanItemState.READY,today.minusDays(1),today.plusDays(1),null);insertTask(overdue,"Overdue",PlanItemState.READY,null,null,today.minusDays(1));
        jdbc.update("INSERT INTO project.plan_item_dependency(item_id,predecessor_id) VALUES (?,?)",dependent,predecessor);jdbc.update("INSERT INTO project.task_execution(item_id,project_id,priority) VALUES (?,?,?)",dependent,project,"LOW");
        var all=management.work(WorkRange.ALL,ZoneOffset.UTC,project,WorkAssignee.MINE,0,50,null,user);assertThat(all.items().get(0).item().id()).isEqualTo(dependent);assertThat(all.items().get(0).blockingReason()).isEqualTo("Blocked by an incomplete predecessor");
        var todayWork=management.work(WorkRange.TODAY,ZoneOffset.UTC,project,WorkAssignee.MINE,0,50,null,user);var todayIds=todayWork.items().stream().map(row->row.item().id()).toList();assertThat(todayIds).contains(endOnlyToday,startOnlyToday,dependent,enclosing,overdue).doesNotContain(endOnlyPast,startOnlyPast);assertThat(todayWork.totalCount()).isEqualTo((long)todayIds.size());
        var monday=today.with(java.time.DayOfWeek.MONDAY);var sunday=monday.plusDays(6);var previousSunday=UUID.randomUUID();var followingMonday=UUID.randomUUID();var mondayTask=UUID.randomUUID();var sundayTask=UUID.randomUUID();insertTask(previousSunday,"Previous Sunday",PlanItemState.READY,null,monday.minusDays(1),null);insertTask(followingMonday,"Following Monday",PlanItemState.READY,null,monday.plusDays(7),null);insertTask(mondayTask,"Monday boundary",PlanItemState.READY,monday,monday,null);insertTask(sundayTask,"Sunday boundary",PlanItemState.READY,sunday,sunday,null);var week=management.work(WorkRange.WEEK,ZoneOffset.UTC,project,WorkAssignee.MINE,0,100,null,user);var weekIds=week.items().stream().map(row->row.item().id()).toList();assertThat(weekIds).contains(mondayTask,sundayTask).doesNotContain(previousSunday,followingMonday);assertThat(week.totalCount()).isEqualTo((long)weekIds.size());
    }

    private void insertTask(UUID id,String title,PlanItemState state,LocalDate start,LocalDate end,LocalDate deadline){
        jdbc.update("INSERT INTO project.plan_item(id,project_id,kind,title,state,target_start,target_end,deadline,assignee_id,sort_order,labels,row_version,created_by) VALUES (?,?,'TASK',?,?,?,?,?,?,0,'[]'::jsonb,0,?)",id,project,title,state.name(),start,end,deadline,user,user);
    }
    private static void execute(Connection db,String sql,Object... values)throws SQLException{try(var statement=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.executeUpdate();}}
    private static Object scalar(Connection db,String sql,Object... values)throws SQLException{try(var statement=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);try(var result=statement.executeQuery()){return result.next()?result.getObject(1):null;}}}
    private static String identifier(String value){return "\""+value.replace("\"","\"\"")+"\"";}
}

