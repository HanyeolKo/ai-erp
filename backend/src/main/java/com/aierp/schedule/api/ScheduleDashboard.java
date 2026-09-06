package com.aierp.schedule.api;
import com.aierp.schedule.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ScheduleDashboard {
    private final ScheduleService schedules;
    public ScheduleDashboard(ScheduleService schedules) {this.schedules=schedules;}
    public Summary summary(UUID project,UUID user) {
        return schedules.dashboard(project,user);
    }
    public record Summary(long scheduleCount,long pendingAcknowledgementCount,List<ScheduleController.ScheduleResponse> upcomingSchedules,List<ScheduleController.ScheduleResponse> actionQueue) {}
}
