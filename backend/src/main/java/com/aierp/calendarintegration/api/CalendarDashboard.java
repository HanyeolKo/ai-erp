package com.aierp.calendarintegration.api;
import com.aierp.calendarintegration.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
@Service
public class CalendarDashboard {
    private final ProjectCalendarRepository calendars;
    private final CalendarProjectionRepository projections;
    public CalendarDashboard(ProjectCalendarRepository calendars,CalendarProjectionRepository projections) {this.calendars=calendars;this.projections=projections;}
    public long riskCount(UUID projectId) {
        return calendars.findByProjectId(projectId).map(c->projections.findByProjectCalendarId(c.id).stream().filter(p->!p.status.equals("SYNCED")).count()).orElse(0L);
    }
}
