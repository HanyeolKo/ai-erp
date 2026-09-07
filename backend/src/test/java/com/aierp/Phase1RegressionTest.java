package com.aierp;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.project.*;
import com.aierp.project.api.ProjectController;
import com.aierp.schedule.*;
import com.aierp.schedule.api.ScheduleController;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase1RegressionTest {
    final UUID project = UUID.randomUUID(), user = UUID.randomUUID(), id = UUID.randomUUID();
    final ScheduleRepository schedules = mock(ScheduleRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"a@example.test",true),null,List.of());
    ScheduleEntity schedule;
    ScheduleController controller;
    @BeforeEach void setup() {
        var member = new ProjectMemberEntity(); member.projectId=project;member.userAccountId=user;member.role=ProjectRole.MANAGER;
        when(members.findByProjectIdAndUserAccountId(project,user)).thenReturn(Optional.of(member));
        schedule=new ScheduleEntity();schedule.id=id;schedule.projectId=project;schedule.createdBy=user;schedule.title="Planning";schedule.startsAt=Instant.parse("2026-09-06T10:00:00Z");schedule.endsAt=schedule.startsAt.plusSeconds(3600);schedule.status=ScheduleEntity.Status.CONFIRMED;schedule.businessRevision=1;
        when(schedules.findByIdAndProjectId(id,project)).thenReturn(Optional.of(schedule));
        when(schedules.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        controller=new ScheduleController(new ScheduleService(schedules,mock(ScheduleParticipantRepository.class),mock(ScheduleAcknowledgementRepository.class),mock(ScheduleChangeRepository.class),new com.aierp.project.api.ProjectAccess(members),mock(com.aierp.platform.events.EventJournal.class)));
    }
    @Test void sessionPrincipalSurvivesSerialization() throws Exception {
        var bytes=new ByteArrayOutputStream();new ObjectOutputStream(bytes).writeObject(auth.getPrincipal());
        assertThat(new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject()).isEqualTo(auth.getPrincipal());
    }
    @Test void noOpConfirmedEditDoesNotCreateNewBusinessRevision() {
        assertThat(controller.patch(project,id,new ScheduleController.Write("Planning",schedule.startsAt,schedule.endsAt,0),auth).businessRevision()).isEqualTo(1);
    }
    @Test void cancelledCannotBeConfirmedAgain() {
        schedule.status=ScheduleEntity.Status.CANCELLED;
        assertThatThrownBy(()->controller.confirm(project,id,new ScheduleController.Revision(0),auth)).isInstanceOf(IllegalStateException.class);
    }
    @Test void repeatedConfirmIsConflict() {
        assertThatThrownBy(()->controller.confirm(project,id,new ScheduleController.Revision(0),auth)).isInstanceOf(IllegalStateException.class);
    }
    @Test void invalidTitleIsRejected() {
        assertThatThrownBy(()->controller.patch(project,id,new ScheduleController.Write(" ",schedule.startsAt,schedule.endsAt,0),auth)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nullRoleIsRejected() {
        var projects=mock(ProjectRepository.class);
        assertThatThrownBy(()->new ProjectController(projects,members,mock(com.aierp.group.api.GroupAccess.class)).changeRole(project,user,new ProjectController.RoleRequest(null),auth)).isInstanceOf(IllegalArgumentException.class);
    }
}
