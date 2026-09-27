package com.aierp;

import com.aierp.googleworkspace.GoogleHttpClient;
import com.aierp.identity.api.GoogleAuthorizationService;
import com.aierp.projectplan.*;
import com.aierp.projectplan.api.ProjectPlanController.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
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
import static org.mockito.Mockito.verifyNoInteractions;

/** CI Testcontainers coverage for additive V9 project-plan persistence and semantics. */
@SpringBootTest @Testcontainers(disabledWithoutDocker=false)
class ProjectPlanPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:8.2.9")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);registry.add("spring.data.redis.url",()->"redis://%s:%d".formatted(REDIS.getHost(),REDIS.getMappedPort(6379)));}
    @Autowired JdbcTemplate jdbc; @Autowired ProjectPlanService service;
    @MockitoBean GoogleAuthorizationService googleAuthorization; @MockitoBean GoogleHttpClient googleHttp;
    UUID project,user,other; int sort;
    @BeforeEach void fixture(){user=UUID.randomUUID();other=UUID.randomUUID();project=UUID.randomUUID();var group=UUID.randomUUID();sort=0;jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",user,user+"@example.test","Plan User");jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",other,other+"@example.test","Other User");jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",group,"Plan Group");jdbc.update("INSERT INTO \"group\".group_member(group_id,user_account_id) VALUES (?,?)",group,user);jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",project,group,"Plan Project");jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MEMBER')",project,user);}

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
        jdbc.update("UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?",project,user);assertThatThrownBy(()->service.create(project,write(null,PlanItemKind.TASK,"Denied",PlanItemState.READY,null,null,List.of()),user)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.snapshot(project,other,null,null,false,null,false,null,null,null,null,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(googleAuthorization,googleHttp);
    }
    private ItemWrite write(UUID parent,PlanItemKind kind,String title,PlanItemState state,LocalDate start,LocalDate end,List<UUID> deps){return new ItemWrite(parent,kind,title,null,null,state,start,end,null,sort++,List.of(),null,deps,UUID.randomUUID(),null);}
    private ItemWrite writeUpdate(ItemResponse current,List<UUID> deps,long version){return new ItemWrite(current.parentId(),current.kind(),current.title(),current.description(),current.assigneeId(),current.state(),current.targetStart(),current.targetEnd(),current.deadline(),current.sortOrder(),current.labels(),version,deps,null,"dependency change");}
}
