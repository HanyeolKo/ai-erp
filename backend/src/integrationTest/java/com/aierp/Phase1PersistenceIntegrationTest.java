package com.aierp;
import com.aierp.identity.api.*;
import com.aierp.project.*;
import com.aierp.project.api.InvitationController.InviteRequest;
import com.aierp.schedule.*;
import com.aierp.schedule.api.ScheduleController.*;
import com.aierp.calendarintegration.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Testcontainers
class Phase1PersistenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18.6");
    @Container static final GenericContainer<?> redis=new GenericContainer<>("redis:8.2.9").withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
        r.add("spring.data.redis.url",()->"redis://"+redis.getHost()+":"+redis.getMappedPort(6379));
        r.add("spring.flyway.locations",()->"classpath:db/migration,classpath:db/integration-migration");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ScheduleService schedules;
    @Autowired InvitationService invitations;
    @Autowired IdentityProvisioning identities;
    @Autowired CalendarService calendars;
    @MockitoBean CalendarAdapter adapter;
    UUID project,user,group;
    @BeforeEach void fixture() {
        project=UUID.randomUUID();user=UUID.randomUUID();group=UUID.randomUUID();
        jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",group,"Group");
        jdbc.update("INSERT INTO \"group\".group_member(group_id,user_account_id) VALUES (?,?)",group,user);
        jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",project,group,"Project");
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",project,user);
    }
    @Test void postgresEntitiesPersistRevisionAcknowledgementEventsAndExternalFailureIsolation() {
        var draft=schedules.create(project,new Write("Planning",Instant.now().plusSeconds(3600),Instant.now().plusSeconds(7200),null,"Notes",List.of(user),List.of("external@example.test")),user);
        assertThat(draft.businessRevision()).isZero();
        var confirmed=schedules.confirm(project,draft.id(),new Revision(draft.rowVersion()),user);
        assertThat(confirmed.businessRevision()).isEqualTo(1);
        var acknowledged=schedules.acknowledge(project,draft.id(),new Acknowledge(1L),user);
        assertThat(acknowledged.rowVersion()).isEqualTo(confirmed.rowVersion());
        assertThat(acknowledged.participants().stream().filter(p->user.equals(p.memberUserId())).findFirst().orElseThrow().acknowledged()).isTrue();
        var changed=schedules.update(project,draft.id(),new Write("Changed",confirmed.startsAt(),confirmed.endsAt(),confirmed.rowVersion(),"Notes",List.of(user),List.of("external@example.test")),user);
        assertThat(changed.businessRevision()).isEqualTo(2);assertThat(changed.participants().stream().anyMatch(Participant::acknowledged)).isFalse();
        when(adapter.configured()).thenReturn(true);when(adapter.deliver(any())).thenThrow(new RuntimeException("external failure"));
        assertThat(calendars.retry(project,draft.id(),user).status()).isEqualTo("FAILED");
        assertThat(schedules.detail(project,draft.id(),user).businessRevision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_log WHERE aggregate_id=?",Integer.class,draft.id())).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND published_at IS NOT NULL",Integer.class,draft.id())).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification.notification WHERE user_account_id=?",Integer.class,user)).isPositive();
    }
    @Test void concurrentInvitationResolutionHasOneWinnerAndPreservesManagerRole() throws Exception {
        var principal=new ApplicationPrincipal(user,"manager@example.test",true);
        var invitation=invitations.invite(group,new InviteRequest(project,"Manager@Example.Test"),principal);
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> resolve=()->{gate.await();try {invitations.resolve(invitation.token(),principal,true);return true;}catch(IllegalStateException conflict){return false;}};
            var one=executor.submit(resolve);var two=executor.submit(resolve);gate.countDown();
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT role FROM project.project_member WHERE project_id=? AND user_account_id=?",String.class,project,user)).isEqualTo("MANAGER");
    }
    @Test void expiredPendingInvitationCanBeSafelyReinvited() {
        var principal=new ApplicationPrincipal(user,"manager@example.test",true);
        var first=invitations.invite(group,new InviteRequest(project,"member@example.test"),principal);
        jdbc.update("UPDATE project.project_invitation SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 day' WHERE id=?",first.id());
        var second=invitations.invite(group,new InviteRequest(project,"member@example.test"),principal);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT status FROM project.project_invitation WHERE id=?",String.class,first.id())).isEqualTo("EXPIRED");
    }
    @Test void concurrentScheduleWritesRejectExactlyOneStaleVersion() throws Exception {
        var draft=schedules.create(project,new Write("Planning",Instant.now().plusSeconds(3600),Instant.now().plusSeconds(7200),null,null,List.of(),List.of()),user);
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> update=()->{gate.await();try {schedules.confirm(project,draft.id(),new Revision(draft.rowVersion()),user);return true;}catch(org.springframework.orm.ObjectOptimisticLockingFailureException|IllegalStateException conflict){return false;}};
            var one=executor.submit(update);var two=executor.submit(update);gate.countDown();
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(schedules.detail(project,draft.id(),user).businessRevision()).isEqualTo(1);
    }
    @Test void subjectProvisioningUsesStablePersistedAccount() {
        var subject=UUID.randomUUID().toString();var email=UUID.randomUUID()+"@example.test";
        var first=identities.provision(subject,email,true,"Original");var second=identities.provision(subject,email,true,"Updated");
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(jdbc.queryForObject("SELECT display_name FROM identity.user_account WHERE id=?",String.class,first.userId())).isEqualTo("Updated");
    }
}
