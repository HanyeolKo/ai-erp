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
    @GetMapping("/google/calendars") public org.springframework.http.ResponseEntity<CalendarPage> calendars(@RequestParam(required=false) String pageToken, Authentication auth) { return noStore(calendar.listCalendars(user(auth), pageToken)); }
    @GetMapping("/projects/{projectId}/calendar") public org.springframework.http.ResponseEntity<ProjectCalendar> projectCalendar(@PathVariable UUID projectId, Authentication auth) { return noStore(calendar.projectCalendar(projectId, user(auth))); }
    @PostMapping("/projects/{projectId}/calendar") public org.springframework.http.ResponseEntity<ProjectCalendar> bind(@PathVariable UUID projectId, @RequestBody Bind input, Authentication auth) { return noStore(calendar.bind(projectId, user(auth), input.calendarId())); }
    @DeleteMapping("/projects/{projectId}/calendar") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID projectId, Authentication auth) { calendar.disconnect(projectId, user(auth)); }
    private <T> org.springframework.http.ResponseEntity<T> noStore(T value) { return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(value); }
    @GetMapping("/projects/{projectId}/schedules/{id}/calendar") public Projection projection(@PathVariable UUID projectId,@PathVariable UUID id,Authentication auth) {return calendar.projection(projectId,id,user(auth));}
    @PostMapping("/projects/{projectId}/schedules/{id}/calendar/retry") public Projection retry(@PathVariable UUID projectId,@PathVariable UUID id,Authentication auth) {return calendar.retry(projectId,id,user(auth));}
    private UUID user(Authentication a) {if(a.getPrincipal() instanceof ApplicationPrincipal p) return p.userId();throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
    public record Connection(String status,boolean configurationRequired) {}
    public record Calendar(String id,String name) {}
    public record CalendarPage(java.util.List<Calendar> calendars,String nextPageToken) {}
    public record Bind(String calendarId) {}
    public record ProjectCalendar(String status,String calendarName,String ownerName,boolean isOwner,boolean canManage,boolean backfillPending) {}
    public record Projection(UUID id,UUID scheduleId,String status,String retryClassification,long businessRevision) {}
}
