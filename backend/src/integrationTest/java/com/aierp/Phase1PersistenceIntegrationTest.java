package com.aierp;

import com.aierp.dashboard.DashboardController;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.GoogleAuthorizationService;
import com.aierp.identity.api.IdentityProvisioning;
import com.aierp.identity.UserAccountRepository;
import com.aierp.project.*;
import com.aierp.project.api.InvitationController.InviteRequest;
import com.aierp.project.api.ProjectController;
import com.aierp.schedule.*;
import com.aierp.schedule.api.ScheduleController.*;
import com.aierp.calendarintegration.*;
import java.time.Instant;
import java.time.Duration;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.flywaydb.core.Flyway;
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
        r.add("google.workspace.calendar.dispatch-delay-ms", () -> "86400000");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;
    @Autowired ScheduleService schedules;
    @Autowired InvitationService invitations;
    @Autowired IdentityProvisioning identities;
    @Autowired UserAccountRepository userAccounts;
    @Autowired CalendarService calendars;
    @Autowired com.aierp.project.api.ProjectController projects;
    @Autowired CalendarDispatchWorker calendarDispatchWorker;
    @Autowired ProjectShareInvitationService shareInvitations;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemberRepository projectMembers;
    @Autowired DashboardController dashboard;
    @MockitoBean CalendarAdapter adapter;
    @MockitoBean GoogleAuthorizationService access;
    @MockitoSpyBean ProjectMemberRepository projectMemberPersistence;
    @MockitoSpyBean ProjectCreationRequestRepository creationRequestPersistence;
    @MockitoSpyBean ProjectRepository projectPersistence;
    UUID project,user,group;
    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM calendar_integration.calendar_projection");
        project=UUID.randomUUID();user=UUID.randomUUID();group=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.user_account(id,email,display_name,email_verified_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",user,user + "@example.test","Phase1 User");
        jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",group,"Group");
        jdbc.update("INSERT INTO \"group\".group_member(group_id,user_account_id,role) VALUES (?,?,?)",group,user,"OWNER");
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
        when(access.configured()).thenReturn(true);
        when(access.status(user)).thenReturn(new GoogleAccess.Connection("manager@"
            + user + ".example.test", Map.of(GoogleAccess.Feature.CALENDAR, GoogleAccess.Status.CONNECTED)));
        var credential=new GoogleAccess.Credential("dispatch-token",7);
        when(access.credential(user,GoogleAccess.Feature.CALENDAR)).thenReturn(credential);
        when(access.isCurrent(user,credential.generation())).thenReturn(true);
        when(adapter.configured()).thenReturn(true);
        when(adapter.writableCalendar(user,"primary-phase1")).thenReturn(new CalendarAdapter.CalendarInfo("primary-phase1","Primary Phase 1"));
        calendars.bind(project,user,"primary-phase1");
        clearInvocations(adapter);

        when(adapter.deliver(any(UUID.class), any(), any(), any(), any(java.util.function.BooleanSupplier.class))).thenThrow(new RuntimeException("external failure"));
        assertThat(calendars.retry(project,draft.id(),user).status()).isEqualTo("PENDING");
        assertThat(calendars.projection(project,draft.id(),user).status()).isEqualTo("PENDING");
        verify(adapter, never()).deliver(any(CalendarProjectionEntity.class));
        verify(adapter, never()).deliver(any(UUID.class), any(), any(), any(), any(java.util.function.BooleanSupplier.class));

        assertThat(calendarDispatchWorker.dispatchOnce()).isTrue();
        assertThat(calendars.projection(project,draft.id(),user).status()).isEqualTo("PENDING");
        assertThat(calendars.projection(project,draft.id(),user).retryClassification()).isEqualTo("TRANSIENT");
        verify(adapter).deliver(any(UUID.class), any(), any(), any(), any(java.util.function.BooleanSupplier.class));
        assertThat(schedules.detail(project,draft.id(),user).businessRevision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_log WHERE aggregate_id=?",Integer.class,draft.id())).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND published_at IS NOT NULL",Integer.class,draft.id())).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification.notification WHERE user_account_id=?",Integer.class,user)).isPositive();
    }
    @Test void projectCreationPersistsProjectAndCreatorManagerAndSupportsRead() {
        var owner=UUID.randomUUID();
        var creationGroup=UUID.randomUUID();
        jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",creationGroup,"Creation Group");
        jdbc.update("INSERT INTO \"group\".group_member(group_id,user_account_id,role) VALUES (?,?,?)",creationGroup,owner,"OWNER");
        var auth = new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(owner,"owner@example.test",true),null,List.of());
        var response = projects.create(new ProjectController.CreateRequest(creationGroup,"Build Team"),auth);

        assertThat(response.id()).isNotNull();assertThat(response.role()).isEqualTo(ProjectRole.MANAGER);
        assertThat(projectRepository.findById(response.id())).isPresent();
        assertThat(projectMembers.findByProjectIdAndUserAccountId(response.id(),owner).orElseThrow().role).isEqualTo(ProjectRole.MANAGER);
        var list = projects.list(auth,null,null);
        assertThat(list).extracting(ProjectController.ProjectResponse::id).contains(response.id());
        assertThat(dashboard.dashboard(response.id(),auth).projectId()).isEqualTo(response.id());
    }
    @Test void directProjectCreationIsAtomicAndRequestReplayReturnsOneProject() throws Exception {
        var owner=UUID.randomUUID();
        var requestId=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(owner,"direct@example.test",true),null,List.of());
        var first=projects.create(new ProjectController.CreateRequest(null,"Direct Project",requestId),auth);
        var replay=projects.create(new ProjectController.CreateRequest(null,"Direct Project",requestId),auth);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_creation_request WHERE actor_id=? AND request_id=?",Integer.class,owner,requestId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"group\".group_member WHERE user_account_id=? AND role='OWNER'",Integer.class,owner)).isEqualTo(1);
        assertThatThrownBy(() -> projects.create(new ProjectController.CreateRequest(null,"Different",requestId),auth)).isInstanceOf(IllegalStateException.class);
    }
    @Test void concurrentSameUserRequestReplayCreatesOneGroupProjectAndRequest() throws Exception {
        var owner=UUID.randomUUID(); var requestId=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(owner,"replay@example.test",true),null,List.of());
        var gate=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<ProjectController.ProjectResponse> create=()->{ gate.await(); return projects.create(new ProjectController.CreateRequest(null,"Concurrent Project",requestId),auth); };
            var a=executor.submit(create); var b=executor.submit(create);
            var one=a.get(20,TimeUnit.SECONDS); var two=b.get(20,TimeUnit.SECONDS);
            assertThat(one.id()).isEqualTo(two.id());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_creation_request WHERE actor_id=? AND request_id=?",Integer.class,owner,requestId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project WHERE id IN (SELECT project_id FROM project.project_creation_request WHERE actor_id=? AND request_id=?)",Integer.class,owner,requestId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"group\".group_member WHERE user_account_id=? AND role='OWNER'",Integer.class,owner)).isEqualTo(1);
    }
    @Test void sameRequestKeyAcrossActorsCreatesIndependentProjects() throws Exception {
        var first=UUID.randomUUID(); var second=UUID.randomUUID(); var requestId=UUID.randomUUID();
        var firstAuth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(first,"first@example.test",true),null,List.of());
        var secondAuth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(second,"second@example.test",true),null,List.of());
        var gate=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> { gate.await(); return projects.create(new ProjectController.CreateRequest(null,"First",requestId),firstAuth); });
            var b=executor.submit(() -> { gate.await(); return projects.create(new ProjectController.CreateRequest(null,"Second",requestId),secondAuth); });
            assertThat(a.get(20,TimeUnit.SECONDS).id()).isNotEqualTo(b.get(20,TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_creation_request WHERE request_id=?",Integer.class,requestId)).isEqualTo(2);
    }
    @Test void directCreationRollbackRemovesAllFiveBootstrapRecordsAfterIntentionalFailure() {
        var owner=UUID.randomUUID(); var requestId=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(owner,"rollback@example.test",true),null,List.of());
        doAnswer(invocation -> {
            var saved=delegate(invocation);
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM \"group\".erp_group WHERE name='Rollback Direct'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM \"group\".group_member WHERE user_account_id=? AND role='OWNER'",Integer.class,owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project WHERE name='Rollback Direct'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE user_account_id=? AND role='MANAGER'",Integer.class,owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_creation_request WHERE actor_id=? AND request_id=?",Integer.class,owner,requestId)).isEqualTo(1);
            throw new IllegalStateException("intentional bootstrap rollback");
        }).when(creationRequestPersistence).save(any(ProjectCreationRequestEntity.class));
        assertThatThrownBy(() -> projects.create(new ProjectController.CreateRequest(null,"Rollback Direct",requestId),auth))
            .isInstanceOf(IllegalStateException.class).hasMessage("intentional bootstrap rollback");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project WHERE name='Rollback Direct'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"group\".erp_group WHERE name='Rollback Direct'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"group\".group_member WHERE user_account_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE user_account_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_creation_request WHERE actor_id=? AND request_id=?",Integer.class,owner,requestId)).isZero();
    }
    @Test void reusableShareInvitationAllowsTwoAccountsAndIdempotentSameAccountJoin() {
        var manager=new ApplicationPrincipal(user,"manager@example.test",true);
        var invitation=shareInvitations.rotate(project,manager);
        assertThat(invitation.code()).matches("[0-9A-Z]{16}").doesNotContain("I","L","O","U");
        assertThat(invitation.expiresAt()).isBetween(Instant.now().plus(Duration.ofDays(6).plusHours(23)),Instant.now().plus(Duration.ofDays(7).plusMinutes(1)));
        var firstUser=new ApplicationPrincipal(UUID.randomUUID(),"first@example.test",true);
        var secondUser=new ApplicationPrincipal(UUID.randomUUID(),"second@example.test",true);
        var viewerId=UUID.randomUUID();
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'VIEWER')",project,viewerId);
        assertThat(shareInvitations.join(invitation.code(),manager).role()).isEqualTo(ProjectRole.MANAGER);
        assertThat(shareInvitations.join(invitation.code(),new ApplicationPrincipal(viewerId,"viewer@example.test",true)).role()).isEqualTo(ProjectRole.VIEWER);
        assertThat(shareInvitations.join(invitation.code(),firstUser).role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(shareInvitations.join(invitation.code(),firstUser).role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(shareInvitations.join(invitation.code(),secondUser).role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=? AND user_account_id IN (?,?)",Integer.class,project,firstUser.userId(),secondUser.userId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project)).isEqualTo(2);
    }
    @Test void concurrentSameUserSharedJoinIsIdempotentWithOneMembershipAndEvent() throws Exception {
        var manager=new ApplicationPrincipal(user,"manager@example.test",true);
        var participant=new ApplicationPrincipal(UUID.randomUUID(),"same@example.test",true);
        var invitation=shareInvitations.rotate(project,manager);
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<ProjectShareInvitationService.ProjectControllerResponse> join=() -> {
                barrier.await(5,TimeUnit.SECONDS);
                return shareInvitations.join(invitation.code(),participant);
            };
            var first=executor.submit(join); var second=executor.submit(join);
            var firstResponse=first.get(20,TimeUnit.SECONDS);
            var secondResponse=second.get(20,TimeUnit.SECONDS);
            assertThat(firstResponse.id()).isEqualTo(project);
            assertThat(secondResponse.id()).isEqualTo(project);
            assertThat(firstResponse.role()).isEqualTo(ProjectRole.MEMBER);
            assertThat(secondResponse.role()).isEqualTo(ProjectRole.MEMBER);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=? AND user_account_id=?",Integer.class,project,participant.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project)).isEqualTo(1);
    }
    @Test void sharePermissionsExpiryRotationRevokeAndProfileReadBoundsAreEnforced() {
        var manager=new ApplicationPrincipal(user,"manager@example.test",true);
        var memberId=UUID.randomUUID(); var viewerId=UUID.randomUUID();
        jdbc.update("UPDATE identity.user_account SET email=?, display_name=? WHERE id=?", "manager@example.test","Project Manager",user);
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MEMBER'),(?,?,'VIEWER')",project,memberId,project,viewerId);
        var member=new ApplicationPrincipal(memberId,"member@example.test",true); var viewer=new ApplicationPrincipal(viewerId,"viewer@example.test",true); var outsider=new ApplicationPrincipal(UUID.randomUUID(),"outside@example.test",true);
        var code=shareInvitations.rotate(project,manager).code();
        assertThat(shareInvitations.preview(" "+code.substring(0,4).toLowerCase(Locale.ROOT)+"-"+code.substring(4,8)+" "+code.substring(8,12)+"-"+code.substring(12),member).projectName()).isEqualTo("Project");
        var unverifiedId=UUID.randomUUID();
        var membersBeforeUnverified=jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=?",Integer.class,project);
        var eventsBeforeUnverified=jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project);
        assertThatThrownBy(() -> shareInvitations.join(code,new ApplicationPrincipal(unverifiedId,"unverified@example.test",false))).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=?",Integer.class,project)).isEqualTo(membersBeforeUnverified);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project)).isEqualTo(eventsBeforeUnverified);
        assertThat(shareInvitations.preview(code,member).inviterName()).isEqualTo("Project Manager");
        jdbc.update("UPDATE identity.user_account SET display_name=? WHERE id=?","manager@example.test",user);
        assertThat(shareInvitations.preview(code,member).inviterName()).isEqualTo("프로젝트 관리자");
        assertThatThrownBy(() -> shareInvitations.rotate(project,member)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> shareInvitations.current(project,viewer)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> shareInvitations.revoke(project,outsider)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> shareInvitations.preview("ß"+code.substring(1),outsider)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shareInvitations.preview(code.substring(0,8)+"\t"+code.substring(8),outsider)).isInstanceOf(IllegalArgumentException.class);
        var invitationBeforeGet=jdbc.queryForMap("SELECT code,created_at,expires_at,revoked_at,invited_by FROM project.project_share_invitation WHERE project_id=?",project);
        assertThat(shareInvitations.current(project,manager).state()).isEqualTo(ProjectShareInvitationService.State.ACTIVE);
        assertThat(jdbc.queryForMap("SELECT code,created_at,expires_at,revoked_at,invited_by FROM project.project_share_invitation WHERE project_id=?",project)).isEqualTo(invitationBeforeGet);
        jdbc.update("UPDATE project.project_share_invitation SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE project_id=?",project);
        var expired=shareInvitations.preview(code,outsider);
        assertThat(expired.state()).isEqualTo(ProjectShareInvitationService.State.EXPIRED);
        assertThat(expired.projectId()).isNull(); assertThat(expired.projectName()).isNull(); assertThat(expired.inviterName()).isNull(); assertThat(expired.role()).isNull(); assertThat(expired.alreadyMember()).isFalse();
        var rotated=shareInvitations.rotate(project,manager);
        assertThatThrownBy(() -> shareInvitations.preview(code,outsider)).isInstanceOf(NoSuchElementException.class);
        shareInvitations.revoke(project,manager);
        var revoked=shareInvitations.preview(rotated.code(),outsider);
        assertThat(revoked.state()).isEqualTo(ProjectShareInvitationService.State.REVOKED);
        assertThat(revoked.projectId()).isNull(); assertThat(revoked.projectName()).isNull();
        assertThatThrownBy(() -> shareInvitations.preview(rotated.code(),new ApplicationPrincipal(UUID.randomUUID(),"unverified@example.test",false))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void projectLockWaiterRechecksRotatedAndRevokedCodeOnlyAfterObservedDatabaseWait() throws Exception {
        var manager=new ApplicationPrincipal(user,"manager@example.test",true);
        var participant=new ApplicationPrincipal(UUID.randomUUID(),"participant@example.test",true);
        var executor=Executors.newFixedThreadPool(1);
        var workerPid=new AtomicReference<Integer>();
        doAnswer(invocation -> { workerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class)); return delegate(invocation); }).when(projectPersistence).lockById(project);
        try {
            var rotated=shareInvitations.rotate(project,manager).code();
            var eventsBefore=jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project);
            try (var held=holdProjectLock()) {
                var join=executor.submit(() -> shareInvitations.join(rotated,participant));
                awaitProjectLockWait(workerPid);
                try (var update=held.prepareStatement("UPDATE project.project_share_invitation SET code=? WHERE project_id=?")) {
                    update.setString(1,"7K3M9F2D6R8TWX4C"); update.setObject(2,project); update.executeUpdate();
                }
                held.commit();
                assertThatThrownBy(() -> join.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(NoSuchElementException.class);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=? AND user_account_id=?",Integer.class,project,participant.userId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project)).isEqualTo(eventsBefore);
            assertThat(jdbc.queryForObject("SELECT code FROM project.project_share_invitation WHERE project_id=?",String.class,project)).isEqualTo("7K3M9F2D6R8TWX4C");

            var revoked=shareInvitations.rotate(project,manager).code();
            try (var held=holdProjectLock()) {
                workerPid.set(null);
                var join=executor.submit(() -> shareInvitations.join(revoked,participant));
                awaitProjectLockWait(workerPid);
                try (var update=held.prepareStatement("UPDATE project.project_share_invitation SET revoked_at=CURRENT_TIMESTAMP WHERE project_id=?")) {
                    update.setObject(1,project); update.executeUpdate();
                }
                held.commit();
                assertThatThrownBy(() -> join.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=? AND user_account_id=?",Integer.class,project,participant.userId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project)).isEqualTo(eventsBefore);

            var expired=shareInvitations.rotate(project,manager).code();
            try (var held=holdProjectLock()) {
                workerPid.set(null);
                var join=executor.submit(() -> shareInvitations.join(expired,participant));
                awaitProjectLockWait(workerPid);
                try (var update=held.prepareStatement("UPDATE project.project_share_invitation SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE project_id=?")) {
                    update.setObject(1,project); update.executeUpdate();
                }
                held.commit();
                assertThatThrownBy(() -> join.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=? AND user_account_id=?",Integer.class,project,participant.userId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE aggregate_id=? AND event_type='INVITATION_ACCEPTED'",Integer.class,project)).isEqualTo(eventsBefore);
        } finally { stopExecutor(executor); }
    }
    private void awaitProjectLockWait(AtomicReference<Integer> workerPid) throws Exception {
        var deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while (System.nanoTime()<deadline) {
            var pid=workerPid.get();
            if (pid!=null) {
                var rows=jdbc.queryForList("SELECT wait_event_type,wait_event FROM pg_stat_activity WHERE pid=?",pid);
                if (!rows.isEmpty() && "Lock".equals(rows.get(0).get("wait_event_type"))) return;
            }
            Thread.sleep(25);
        }
        fail("worker transaction did not appear in pg_stat_activity waiting on a database Lock");
    }
    private static Object delegate(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }
    private static void stopExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5,TimeUnit.SECONDS)).as("race worker must terminate after holder releases its lock").isTrue();
    }
    private Connection holdProjectLock() throws SQLException {
        var connection=dataSource.getConnection(); connection.setAutoCommit(false);
        try (var statement=connection.prepareStatement("SELECT id FROM project.project WHERE id=? FOR UPDATE")) { statement.setObject(1,project); statement.executeQuery(); }
        return connection;
    }
    @Test void managerWithoutGroupMembershipCanIssueProjectShareInvitation() {
        var manager=UUID.randomUUID(); var privateProject=UUID.randomUUID();
        jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",privateProject,group,"Private project");
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",privateProject,manager);
        var invitation=shareInvitations.rotate(privateProject,new ApplicationPrincipal(manager,"nogroup@example.test",true));
        assertThat(invitation.state()).isEqualTo(ProjectShareInvitationService.State.ACTIVE);
    }
    @Test void legacyEmailInvitationSucceedsForProjectManagerWithoutGroupMembership() {
        var manager=UUID.randomUUID(); var privateProject=UUID.randomUUID();
        jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",privateProject,group,"Private legacy project");
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",privateProject,manager);
        var invitation=invitations.invite(group,new InviteRequest(privateProject,"legacy-member@example.test"),new ApplicationPrincipal(manager,"legacy-manager@example.test",true));
        assertThat(invitation.projectId()).isEqualTo(privateProject);
        assertThat(invitation.status()).isEqualTo(ProjectInvitationEntity.Status.PENDING);
    }
    @Test void demotedActorCannotRotateOrRevokeAfterObservedProjectLockWait() throws Exception {
        var actor=UUID.randomUUID();
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",project,actor);
        var actorPrincipal=new ApplicationPrincipal(actor,"actor@example.test",true);
        var manager=new ApplicationPrincipal(user,"manager@example.test",true);
        var executor=Executors.newSingleThreadExecutor();
        var workerPid=new AtomicReference<Integer>();
        doAnswer(invocation -> { workerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class)); return delegate(invocation); }).when(projectPersistence).lockById(project);
        try {
            var invitation=shareInvitations.rotate(project,manager);
            var before=jdbc.queryForMap("SELECT code,created_at,expires_at,revoked_at,invited_by FROM project.project_share_invitation WHERE project_id=?",project);
            try (var held=holdProjectLock()) {
                var rotate=executor.submit(() -> shareInvitations.rotate(project,actorPrincipal));
                awaitProjectLockWait(workerPid);
                demote(held,actor);
                held.commit();
                assertThatThrownBy(() -> rotate.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(AccessDeniedException.class);
            }
            assertThat(jdbc.queryForMap("SELECT code,created_at,expires_at,revoked_at,invited_by FROM project.project_share_invitation WHERE project_id=?",project)).isEqualTo(before);

            jdbc.update("UPDATE project.project_member SET role='MANAGER' WHERE project_id=? AND user_account_id=?",project,actor);
            invitation=shareInvitations.rotate(project,manager);
            before=jdbc.queryForMap("SELECT code,created_at,expires_at,revoked_at,invited_by FROM project.project_share_invitation WHERE project_id=?",project);
            try (var held=holdProjectLock()) {
                workerPid.set(null);
                var revoke=executor.submit(() -> { shareInvitations.revoke(project,actorPrincipal); return true; });
                awaitProjectLockWait(workerPid);
                demote(held,actor);
                held.commit();
                assertThatThrownBy(() -> revoke.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(AccessDeniedException.class);
            }
            assertThat(jdbc.queryForMap("SELECT code,created_at,expires_at,revoked_at,invited_by FROM project.project_share_invitation WHERE project_id=?",project)).isEqualTo(before);
        } finally { stopExecutor(executor); }
    }
    private void demote(Connection held,UUID actor) throws SQLException {
        try (var update=held.prepareStatement("UPDATE project.project_member SET role='MEMBER' WHERE project_id=? AND user_account_id=?")) {
            update.setObject(1,project); update.setObject(2,actor); update.executeUpdate();
        }
    }
    @Test void profileReadIsBoundedAtTwoHundredIds() {
        var ids=java.util.stream.IntStream.range(0,201).mapToObj(i -> UUID.randomUUID()).toList();
        var profiles=new com.aierp.identity.api.IdentityProfiles(userAccounts);
        assertThat(profiles.find(ids.subList(0,200))).isEmpty();
        assertThatThrownBy(() -> profiles.find(ids)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void concurrentSelfDemotionsRetainOneManager() throws Exception {
        var other=UUID.randomUUID();
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",project,other);
        var gate=new CountDownLatch(1);
        var first=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"manager@example.test",true),null,List.of());
        var second=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(other,"other@example.test",true),null,List.of());
        try(var executor=Executors.newFixedThreadPool(2)) {
            // Each actor demotes itself; one serialized transaction must fail at the last-manager guard.
            var a=executor.submit(() -> { gate.await(); try { projects.changeRole(project,user,new ProjectController.RoleRequest(ProjectRole.MEMBER),first); return true; } catch (IllegalStateException conflict) { if ("LAST_MANAGER_REQUIRED".equals(conflict.getMessage())) return false; throw conflict; }});
            var b=executor.submit(() -> { gate.await(); try { projects.changeRole(project,other,new ProjectController.RoleRequest(ProjectRole.MEMBER),second); return true; } catch (IllegalStateException conflict) { if ("LAST_MANAGER_REQUIRED".equals(conflict.getMessage())) return false; throw conflict; }});
            gate.countDown(); assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project.project_member WHERE project_id=? AND role='MANAGER'",Integer.class,project)).isEqualTo(1);
    }
    @Test void projectCreationDoesNotPersistWhenGroupRoleCannotCreate() {
        var blocked=UUID.randomUUID();
        jdbc.update("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",blocked,"Blocked Group");
        jdbc.update("INSERT INTO \"group\".group_member(group_id,user_account_id,role) VALUES (?,?,?)",blocked,user,"MEMBER");
        var blockedBefore=projectRepository.count();
        var auth = new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"blocked@example.test",true),null,List.of());
        assertThatThrownBy(() -> projects.create(new ProjectController.CreateRequest(blocked,"Should Be Rejected"),auth))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(projectRepository.count()).isEqualTo(blockedBefore);
    }
    @Test void v6UpgradeKeepsLegacyRowsNullAndRetainsProjectRoles() throws Exception {
        var databaseName="aierp_v6_"+UUID.randomUUID().toString().replace("-","");
        var adminUrl="jdbc:postgresql://"+postgres.getHost()+":"+postgres.getMappedPort(5432)+"/postgres";
        var databaseUrl="jdbc:postgresql://"+postgres.getHost()+":"+postgres.getMappedPort(5432)+"/"+databaseName;
        try (var admin=DriverManager.getConnection(adminUrl,postgres.getUsername(),postgres.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE \""+databaseName+"\"");
        }
        try {
            Flyway.configure().dataSource(databaseUrl,postgres.getUsername(),postgres.getPassword()).target("5").load().migrate();
            var legacyUser=UUID.randomUUID();var legacyGroup=UUID.randomUUID();var legacyProject=UUID.randomUUID();
            try (var connection=DriverManager.getConnection(databaseUrl,postgres.getUsername(),postgres.getPassword())) {
                try (var group=connection.prepareStatement("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)");
                     var membership=connection.prepareStatement("INSERT INTO \"group\".group_member(group_id,user_account_id) VALUES (?,?)");
                     var project=connection.prepareStatement("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)");
                     var projectMember=connection.prepareStatement("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'VIEWER')")) {
                    group.setObject(1,legacyGroup);group.setString(2,"Legacy Group");group.executeUpdate();
                    membership.setObject(1,legacyGroup);membership.setObject(2,legacyUser);membership.executeUpdate();
                    project.setObject(1,legacyProject);project.setObject(2,legacyGroup);project.setString(3,"Legacy Project");project.executeUpdate();
                    projectMember.setObject(1,legacyProject);projectMember.setObject(2,legacyUser);projectMember.executeUpdate();
                }
            }
            Flyway.configure().dataSource(databaseUrl,postgres.getUsername(),postgres.getPassword()).target("6").load().migrate();
            try (var connection=DriverManager.getConnection(databaseUrl,postgres.getUsername(),postgres.getPassword())) {
                try (var role=connection.prepareStatement("SELECT role FROM \"group\".group_member WHERE group_id=? AND user_account_id=?")) {
                    role.setObject(1,legacyGroup);role.setObject(2,legacyUser);
                    try (var result=role.executeQuery()) { assertThat(result.next()).isTrue();assertThat(result.getObject(1)).isNull(); }
                }
                try (var retained=connection.prepareStatement("SELECT role FROM project.project_member WHERE project_id=? AND user_account_id=?")) {
                    retained.setObject(1,legacyProject);retained.setObject(2,legacyUser);
                    try (var result=retained.executeQuery()) { assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("VIEWER"); }
                }
                assertThatThrownBy(() -> connection.createStatement().executeUpdate("UPDATE \"group\".group_member SET role='INVALID' WHERE group_id='"+legacyGroup+"' AND user_account_id='"+legacyUser+"'"))
                    .isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).isEqualTo("23514"));
            }
        } finally {
            try (var admin=DriverManager.getConnection(adminUrl,postgres.getUsername(),postgres.getPassword())) {
                admin.createStatement().execute("DROP DATABASE IF EXISTS \""+databaseName+"\"");
            }
        }
    }
    @Test void v6ToV7UpgradeRetainsExplicitRolesAndLegacyEmailInvitation() throws Exception {
        var databaseName="aierp_v6_to_v7_"+UUID.randomUUID().toString().replace("-","");
        var adminUrl="jdbc:postgresql://"+postgres.getHost()+":"+postgres.getMappedPort(5432)+"/postgres";
        var databaseUrl="jdbc:postgresql://"+postgres.getHost()+":"+postgres.getMappedPort(5432)+"/"+databaseName;
        try (var admin=DriverManager.getConnection(adminUrl,postgres.getUsername(),postgres.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE \""+databaseName+"\"");
        }
        var legacyUser=UUID.randomUUID(); var legacyGroup=UUID.randomUUID(); var legacyProject=UUID.randomUUID(); var invitationId=UUID.randomUUID();
        var token="legacy-"+UUID.randomUUID();
        try {
            Flyway.configure().dataSource(databaseUrl,postgres.getUsername(),postgres.getPassword()).target("6").load().migrate();
            try (var connection=DriverManager.getConnection(databaseUrl,postgres.getUsername(),postgres.getPassword())) {
                try (var group=connection.prepareStatement("INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)");
                     var membership=connection.prepareStatement("INSERT INTO \"group\".group_member(group_id,user_account_id,role) VALUES (?,?,'OWNER')");
                     var project=connection.prepareStatement("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)");
                     var projectMember=connection.prepareStatement("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'VIEWER')");
                     var invitation=connection.prepareStatement("INSERT INTO project.project_invitation(id,project_id,email,token,status,expires_at,invited_by) VALUES (?,?,?,?,'PENDING',?,?)")) {
                    group.setObject(1,legacyGroup); group.setString(2,"Legacy Group"); group.executeUpdate();
                    membership.setObject(1,legacyGroup); membership.setObject(2,legacyUser); membership.executeUpdate();
                    project.setObject(1,legacyProject); project.setObject(2,legacyGroup); project.setString(3,"Legacy Project"); project.executeUpdate();
                    projectMember.setObject(1,legacyProject); projectMember.setObject(2,legacyUser); projectMember.executeUpdate();
                    invitation.setObject(1,invitationId); invitation.setObject(2,legacyProject); invitation.setString(3,"legacy@example.test"); invitation.setString(4,token); invitation.setTimestamp(5,Timestamp.from(Instant.parse("2099-01-01T00:00:00Z"))); invitation.setObject(6,legacyUser); invitation.executeUpdate();
                }
            }
            Flyway.configure().dataSource(databaseUrl,postgres.getUsername(),postgres.getPassword()).target("7").load().migrate();
            try (var connection=DriverManager.getConnection(databaseUrl,postgres.getUsername(),postgres.getPassword())) {
                try (var role=connection.prepareStatement("SELECT role FROM \"group\".group_member WHERE group_id=? AND user_account_id=?")) {
                    role.setObject(1,legacyGroup); role.setObject(2,legacyUser);
                    try (var result=role.executeQuery()) { result.next(); assertThat(result.getString(1)).isEqualTo("OWNER"); }
                }
                try (var retained=connection.prepareStatement("SELECT role FROM project.project_member WHERE project_id=? AND user_account_id=?")) {
                    retained.setObject(1,legacyProject); retained.setObject(2,legacyUser);
                    try (var result=retained.executeQuery()) { result.next(); assertThat(result.getString(1)).isEqualTo("VIEWER"); }
                }
                try (var retained=connection.prepareStatement("SELECT email,token,status,invited_by FROM project.project_invitation WHERE id=?")) {
                    retained.setObject(1,invitationId);
                    try (var result=retained.executeQuery()) { result.next(); assertThat(result.getString(1)).isEqualTo("legacy@example.test"); assertThat(result.getString(2)).isEqualTo(token); assertThat(result.getString(3)).isEqualTo("PENDING"); assertThat(result.getObject(4)).isEqualTo(legacyUser); }
                }
                try (var creationRequests=connection.createStatement().executeQuery("SELECT count(*) FROM project.project_creation_request")) { creationRequests.next(); assertThat(creationRequests.getInt(1)).isZero(); }
                try (var shareInvitations=connection.createStatement().executeQuery("SELECT count(*) FROM project.project_share_invitation")) { shareInvitations.next(); assertThat(shareInvitations.getInt(1)).isZero(); }
            }
        } finally {
            try (var admin=DriverManager.getConnection(adminUrl,postgres.getUsername(),postgres.getPassword())) { admin.createStatement().execute("DROP DATABASE IF EXISTS \""+databaseName+"\""); }
        }
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
