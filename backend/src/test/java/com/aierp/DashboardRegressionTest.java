package com.aierp;
import com.aierp.project.*;
import com.aierp.project.api.ProjectController;
import com.aierp.identity.api.ApplicationPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class DashboardRegressionTest {
    @Test void viewerDashboardNeverOffersAcknowledgementActions() {
        UUID project=UUID.randomUUID(),user=UUID.randomUUID();
        var access=mock(com.aierp.project.api.ProjectAccess.class);
        when(access.role(project,user)).thenReturn("VIEWER");
        var schedules=mock(com.aierp.schedule.api.ScheduleDashboard.class);
        var schedule=new com.aierp.schedule.api.ScheduleController.ScheduleResponse(UUID.randomUUID(),project,"Planning",com.aierp.schedule.ScheduleEntity.Status.CONFIRMED,1,1,java.time.Instant.now(),java.time.Instant.now().plusSeconds(3600),null,user,List.of(),List.of());
        when(schedules.summary(project,user)).thenReturn(new com.aierp.schedule.api.ScheduleDashboard.Summary(1,1,List.of(schedule),List.of(schedule)));
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"a@example.test",true),null,List.of());
        var response=new com.aierp.dashboard.DashboardController(access,schedules,mock(com.aierp.calendarintegration.api.CalendarDashboard.class)).dashboard(project,auth);
        assertThat(response.actionQueue()).isEmpty();
    }
    @Test void newProjectDashboardIncludesZeroScheduleTotalsAndEmptyActionQueue() {
        var projects=mock(ProjectRepository.class);var members=mock(ProjectMemberRepository.class);
        UUID project=UUID.randomUUID(),user=UUID.randomUUID();
        var member=new ProjectMemberEntity();member.role=ProjectRole.MANAGER;
        when(members.findByProjectIdAndUserAccountId(project,user)).thenReturn(Optional.of(member));
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"a@example.test",true),null,List.of());
        var service=mock(com.aierp.schedule.ScheduleService.class);
        var result=new tools.jackson.databind.json.JsonMapper().valueToTree(new com.aierp.dashboard.DashboardController(new com.aierp.project.api.ProjectAccess(members),new com.aierp.schedule.api.ScheduleDashboard(service),mock(com.aierp.calendarintegration.api.CalendarDashboard.class)).dashboard(project,auth));
        assertThat(result.path("scheduleCount").asInt(-1)).isZero();
        assertThat(result.path("actionQueue").isArray()).isTrue();
    }
}
