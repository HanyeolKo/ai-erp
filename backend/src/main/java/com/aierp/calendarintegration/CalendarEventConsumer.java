package com.aierp.calendarintegration;
import com.aierp.platform.events.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class CalendarEventConsumer implements EventConsumer {
    private final ProjectCalendarRepository calendars;
    private final CalendarProjectionRepository projections;
    private final CalendarConnectionRepository connections;
    public CalendarEventConsumer(ProjectCalendarRepository calendars,CalendarProjectionRepository projections,CalendarConnectionRepository connections) {this.calendars=calendars;this.projections=projections;this.connections=connections;}
    @Override public void consume(DomainEvent event) {
        if(event.type().equals("INVITATION_ACCEPTED")) {
            if(connections.findByUserAccountId(event.actorId()).isEmpty()) {
                var c=new CalendarConnectionEntity();c.id=UUID.randomUUID();c.userAccountId=event.actorId();c.status="NOT_CONNECTED";c.updatedAt=Instant.now();connections.save(c);
            }
        }
        if(!event.type().startsWith("SCHEDULE") || event.businessRevision()==0 || event.type().equals("SCHEDULE_ACKNOWLEDGED")) return;
        var calendar=calendars.findByProjectId(event.projectId()).orElseGet(()-> {
            var c=new ProjectCalendarEntity();c.id=UUID.randomUUID();c.projectId=event.projectId();return calendars.saveAndFlush(c);
        });
        var p=projections.findByScheduleIdAndProjectCalendarId(event.aggregateId(),calendar.id).orElseGet(()-> {
            var n=new CalendarProjectionEntity();n.id=UUID.randomUUID();n.scheduleId=event.aggregateId();n.projectCalendarId=calendar.id;return n;
        });
        if(event.businessRevision()<p.businessRevision) return;
        p.businessRevision=event.businessRevision();p.status="PENDING";p.retryClassification="CONFIGURATION_REQUIRED";p.updatedAt=Instant.now();projections.save(p);
    }
}
