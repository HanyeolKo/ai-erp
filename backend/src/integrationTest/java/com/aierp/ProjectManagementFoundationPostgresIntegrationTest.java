package com.aierp;

import com.aierp.googleworkspace.GoogleHttpClient;
import com.aierp.identity.api.GoogleAuthorizationService;
import com.aierp.project.api.ProjectManagementDefinitionAccess;
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
                execute(db,"INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",legacyUser,"v10@example.test","V10");execute(db,"INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",legacyGroup,"V10 Group");execute(db,"INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",legacyProject,legacyGroup,"V10 Project");execute(db,"INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",legacyProject,legacyUser);execute(db,"INSERT INTO project.project_plan(project_id,row_version,target_start,target_end) VALUES (?,7,DATE '2030-01-01',DATE '2030-01-31')",legacyProject);execute(db,"INSERT INTO project.plan_item(id,project_id,kind,title,state,target_start,target_end,sort_order,labels,row_version,created_by) VALUES (?,?,'TASK','V10 TASK','IN_PROGRESS',DATE '2030-01-02',DATE '2030-01-05',4,'[\"legacy\"]'::jsonb,8,?)",legacyItem,legacyProject,legacyUser);execute(db,"INSERT INTO schedule.project_schedule(id,project_id,created_by,title,starts_at,ends_at,status,business_revision) VALUES (?,?,?,'V10 Schedule',TIMESTAMPTZ '2030-01-01 10:00:00+00',TIMESTAMPTZ '2030-01-01 11:00:00+00','CONFIRMED',3)",legacySchedule,legacyProject,legacyUser);execute(db,"INSERT INTO schedule.schedule_saved_view(id,project_id,name,scope,owner_id,config) VALUES (?,?,'Legacy view','PERSONAL',?, '{}'::jsonb)",legacyView,legacyProject,legacyUser);
                assertThat(scalar(db,"SELECT title FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo("V10 TASK");
            }
            Flyway.configure().dataSource(databaseUrl,POSTGRES.getUsername(),POSTGRES.getPassword()).locations("classpath:db/migration").target("11").load().migrate();
            try(var db=DriverManager.getConnection(databaseUrl,POSTGRES.getUsername(),POSTGRES.getPassword())) { assertThat(scalar(db,"SELECT title FROM project.plan_item WHERE id=?",legacyItem)).isEqualTo("V10 TASK");assertThat(scalar(db,"SELECT target_start FROM project.project_plan WHERE project_id=?",legacyProject)).isEqualTo(java.sql.Date.valueOf("2030-01-01"));assertThat(scalar(db,"SELECT count(*) FROM schedule.schedule_saved_view WHERE id=?",legacyView)).isEqualTo(1L);assertThat(scalar(db,"SELECT count(*) FROM project.management_definition")).isEqualTo(0L);assertThat(scalar(db,"SELECT count(*) FROM project.task_execution")).isEqualTo(0L); }
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
        var monday=today.with(java.time.DayOfWeek.MONDAY);var sunday=monday.plusDays(6);var previousSunday=UUID.randomUUID();var followingMonday=UUID.randomUUID();insertTask(previousSunday,"Previous Sunday",PlanItemState.READY,null,monday.minusDays(1),null);insertTask(followingMonday,"Following Monday",PlanItemState.READY,null,monday.plusDays(7),null);var week=management.work(WorkRange.WEEK,ZoneOffset.UTC,project,WorkAssignee.MINE,0,100,null,user);var weekIds=week.items().stream().map(row->row.item().id()).toList();assertThat(weekIds).doesNotContain(previousSunday,followingMonday);
    }

    private void insertTask(UUID id,String title,PlanItemState state,LocalDate start,LocalDate end,LocalDate deadline){
        jdbc.update("INSERT INTO project.plan_item(id,project_id,kind,title,state,target_start,target_end,deadline,assignee_id,sort_order,labels,row_version,created_by) VALUES (?,?,'TASK',?,?,?,?,?,?,0,'[]'::jsonb,0,?)",id,project,title,state.name(),start,end,deadline,user,user);
    }
    private static void execute(Connection db,String sql,Object... values)throws SQLException{try(var statement=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.executeUpdate();}}
    private static Object scalar(Connection db,String sql,Object... values)throws SQLException{try(var statement=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);try(var result=statement.executeQuery()){return result.next()?result.getObject(1):null;}}}
    private static String identifier(String value){return "\""+value.replace("\"","\"\"")+"\"";}
}

