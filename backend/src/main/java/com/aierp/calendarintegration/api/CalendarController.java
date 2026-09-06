package com.aierp.calendarintegration.api;
import com.aierp.calendarintegration.CalendarService;
import com.aierp.identity.api.ApplicationPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class CalendarController {
    private final CalendarService calendar;
    public CalendarController(CalendarService calendar) {this.calendar=calendar;}
    @GetMapping("/calendar/connection") public Connection connection(Authentication auth) {return calendar.connection(user(auth));}
    @PostMapping("/calendar/reconnect") public Connection reconnect(Authentication auth) {return calendar.reconnect(user(auth));}
    @GetMapping("/projects/{projectId}/schedules/{id}/calendar") public Projection projection(@PathVariable UUID projectId,@PathVariable UUID id,Authentication auth) {return calendar.projection(projectId,id,user(auth));}
    @PostMapping("/projects/{projectId}/schedules/{id}/calendar/retry") public Projection retry(@PathVariable UUID projectId,@PathVariable UUID id,Authentication auth) {return calendar.retry(projectId,id,user(auth));}
    private UUID user(Authentication a) {if(a.getPrincipal() instanceof ApplicationPrincipal p) return p.userId();throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
    public record Connection(String status,boolean configurationRequired) {}
    public record Projection(UUID id,UUID scheduleId,String status,String retryClassification,long businessRevision) {}
}
