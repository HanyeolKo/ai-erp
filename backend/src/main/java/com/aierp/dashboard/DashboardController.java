package com.aierp.dashboard;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.project.api.ProjectAccess;
import com.aierp.schedule.api.*;
import com.aierp.calendarintegration.api.CalendarDashboard;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/projects")
public class DashboardController {
    private final ProjectAccess projects;
    private final ScheduleDashboard schedules;
    private final CalendarDashboard calendars;
    public DashboardController(ProjectAccess projects,ScheduleDashboard schedules,CalendarDashboard calendars) {this.projects=projects;this.schedules=schedules;this.calendars=calendars;}
    @GetMapping("/{projectId}/dashboard")
    public Response dashboard(@PathVariable UUID projectId,Authentication auth) {
        if(!(auth.getPrincipal() instanceof ApplicationPrincipal principal)) throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");
        var role=projects.role(projectId,principal.userId());
        var summary=schedules.summary(projectId,principal.userId());
        return new Response(projectId,projects.memberIds(projectId).size(),summary.scheduleCount(),summary.pendingAcknowledgementCount(),calendars.riskCount(projectId),summary.upcomingSchedules(),"VIEWER".equals(role)?List.of():summary.actionQueue());
    }
    public record Response(UUID projectId,int memberCount,int scheduleCount,long pendingAcknowledgementCount,long calendarRiskCount,
            List<ScheduleController.ScheduleResponse> upcomingSchedules,List<ScheduleController.ScheduleResponse> actionQueue) {}
}
