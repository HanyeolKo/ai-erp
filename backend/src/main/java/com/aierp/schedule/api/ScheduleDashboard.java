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
        var all=schedules.list(project,user);
        Instant now=Instant.now(),until=now.plus(Duration.ofDays(14));
        var upcoming=all.stream().filter(s->s.status()!=ScheduleEntity.Status.CANCELLED && s.endsAt().isAfter(now) && s.startsAt().isBefore(until)).toList();
        var actionQueue=all.stream().filter(s->s.businessRevision()>0 && s.participants().stream().anyMatch(p->user.equals(p.memberUserId())&&!p.acknowledged())).toList();
        long pending=all.stream().filter(s->s.businessRevision()>0).flatMap(s->s.participants().stream()).filter(p->p.memberUserId()!=null&&!p.acknowledged()).count();
        return new Summary(all.size(),pending,upcoming,actionQueue);
    }
    public record Summary(int scheduleCount,long pendingAcknowledgementCount,List<ScheduleController.ScheduleResponse> upcomingSchedules,List<ScheduleController.ScheduleResponse> actionQueue) {}
}
