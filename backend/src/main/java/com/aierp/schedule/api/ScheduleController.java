package com.aierp.schedule.api;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.schedule.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects/{projectId}/schedules")
public class ScheduleController {
    private final ScheduleService schedules;
    public ScheduleController(ScheduleService schedules) {this.schedules=schedules;}
    @GetMapping public List<ScheduleResponse> list(@PathVariable UUID projectId,Authentication auth,
        @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer limit) {
        return schedules.list(projectId,user(auth),from,to,page,limit);
    }
    @GetMapping("/{id}") public ScheduleResponse detail(@PathVariable UUID projectId,@PathVariable UUID id,Authentication auth,
        @RequestParam(required=false) Integer historyLimit) {return schedules.detail(projectId,id,user(auth),historyLimit);}
    @PostMapping public ScheduleResponse create(@PathVariable UUID projectId,@RequestBody Write input,Authentication auth) {return schedules.create(projectId,input,user(auth));}
    @PatchMapping("/{id}") public ScheduleResponse patch(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody Write input,Authentication auth) {return schedules.update(projectId,id,input,user(auth));}
    @PostMapping("/{id}/confirm") public ScheduleResponse confirm(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody Revision input,Authentication auth) {return schedules.confirm(projectId,id,input,user(auth));}
    @PostMapping("/{id}/cancel") public ScheduleResponse cancel(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody Revision input,Authentication auth) {return schedules.cancel(projectId,id,input,user(auth));}
    @PostMapping("/{id}/acknowledge") public ScheduleResponse acknowledge(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody Acknowledge input,Authentication auth) {return schedules.acknowledge(projectId,id,input,user(auth));}
    private UUID user(Authentication auth) {
        if(auth.getPrincipal() instanceof ApplicationPrincipal p) return p.userId();
        throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");
    }
    public record Write(String title,Instant startsAt,Instant endsAt,Long rowVersion,String description,List<UUID> memberParticipantIds,List<String> externalAttendeeEmails) {
        public Write(String title,Instant startsAt,Instant endsAt,long rowVersion) {this(title,startsAt,endsAt,rowVersion,null,null,null);}
    }
    public record Revision(Long rowVersion) { public Revision(long rowVersion) {this(Long.valueOf(rowVersion));} }
    public record Acknowledge(Long expectedBusinessRevision) {}
    public record Participant(UUID memberUserId,String externalEmail,boolean acknowledged) {}
    public record Change(long businessRevision,String type,UUID changedBy,Instant createdAt) {}
    public record ScheduleResponse(UUID id,UUID projectId,String title,ScheduleEntity.Status status,long rowVersion,long businessRevision,
        Instant startsAt,Instant endsAt,String description,UUID createdBy,List<Participant> participants,List<Change> changes) {}
}
