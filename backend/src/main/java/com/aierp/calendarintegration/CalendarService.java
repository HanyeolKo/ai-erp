package com.aierp.calendarintegration;
import com.aierp.project.api.ProjectAccess;
import com.aierp.calendarintegration.api.CalendarController.*;
import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class CalendarService {
    private final CalendarConnectionRepository connections;
    private final ProjectCalendarRepository calendars;
    private final CalendarProjectionRepository projections;
    private final CalendarAdapter adapter;
    private final ProjectAccess access;
    public CalendarService(CalendarConnectionRepository connections,ProjectCalendarRepository calendars,CalendarProjectionRepository projections,CalendarAdapter adapter,ProjectAccess access) {
        this.connections=connections;this.calendars=calendars;this.projections=projections;this.adapter=adapter;this.access=access;
    }
    public Connection connection(UUID user) {
        return new Connection(connections.findByUserAccountId(user).map(c->c.status).orElse("NOT_CONNECTED"),!adapter.configured());
    }
    @Transactional public Connection reconnect(UUID user) {
        var c=connections.findByUserAccountId(user).orElseGet(()->{var n=new CalendarConnectionEntity();n.id=UUID.randomUUID();n.userAccountId=user;return n;});
        c.status=adapter.configured()?"PENDING":"NOT_CONNECTED";c.updatedAt=Instant.now();connections.save(c);return new Connection(c.status,!adapter.configured());
    }
    public Projection projection(UUID projectId,UUID scheduleId,UUID user) {
        access.role(projectId,user);
        return calendars.findByProjectId(projectId).flatMap(c->projections.findByScheduleIdAndProjectCalendarId(scheduleId,c.id))
            .map(this::response).orElse(new Projection(null,scheduleId,"NOT_CONNECTED","CONFIGURATION_REQUIRED",0));
    }
    @Transactional public Projection retry(UUID projectId,UUID scheduleId,UUID user) {
        access.requireManager(projectId,user);
        var calendar=calendars.findByProjectId(projectId).orElseThrow(NoSuchElementException::new);
        var snapshot=projections.findByScheduleIdAndProjectCalendarId(scheduleId,calendar.id).orElseThrow(NoSuchElementException::new);
        var p=projections.lockById(snapshot.id).orElseThrow();
        if(!adapter.configured()) {p.status="PENDING";p.retryClassification="CONFIGURATION_REQUIRED";}
        else {
            p.lastAttemptAt=Instant.now();
            try {p.externalEventId=adapter.deliver(p);p.status="SYNCED";p.retryClassification=null;}
            catch(CalendarAdapter.ReauthorizationRequired failure) {p.status="REAUTH_REQUIRED";p.retryClassification="REAUTH_REQUIRED";}
            catch(CalendarAdapter.PermanentFailure failure) {p.status="FAILED";p.retryClassification="PERMANENT";}
            catch(RuntimeException failure) {p.status="FAILED";p.retryClassification="TRANSIENT";}
        }
        p.updatedAt=Instant.now();projections.saveAndFlush(p);return response(p);
    }
    private Projection response(CalendarProjectionEntity p) {return new Projection(p.id,p.scheduleId,p.status,p.retryClassification,p.businessRevision);}
}
