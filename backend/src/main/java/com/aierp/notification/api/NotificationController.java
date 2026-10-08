package com.aierp.notification.api;

import com.aierp.notification.*;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.project.api.ProjectNotificationAccess;
import com.aierp.schedule.api.ScheduleLookup;
import java.util.*;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.aierp.platform.web.ReadLimits;
import org.springframework.data.domain.Sort;

@RestController @RequestMapping("/api/v1/notifications")
public class NotificationController {
    private static final Set<String> SCHEDULE_TYPES = Set.of("SCHEDULE_CREATED", "SCHEDULE_CHANGED", "SCHEDULE_UPDATED", "SCHEDULE_CONFIRMED", "SCHEDULE_CANCELLED");
    private static final Pattern PROJECT_LINK = Pattern.compile("^/projects/([0-9a-fA-F-]{36})$");
    private static final Pattern SCHEDULE_LINK = Pattern.compile("^/projects/([0-9a-fA-F-]{36})/schedules/([0-9a-fA-F-]{36})$");
    private final NotificationRepository notifications;
    private final ProjectNotificationAccess projects;
    private final ScheduleLookup schedules;
    public NotificationController(NotificationRepository notifications, ProjectNotificationAccess projects, ScheduleLookup schedules) {
        this.notifications=notifications;this.projects=projects;this.schedules=schedules;
    }
    @GetMapping public List<Response> list(Authentication auth,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer limit) {
        UUID user=user(auth);
        return notifications.findByUserAccountId(user,ReadLimits.page(page,limit,Sort.by(Sort.Direction.DESC,"createdAt","id"))).stream().map(n->response(n,user)).toList();
    }
    @PostMapping("/{id}/read") @Transactional public Response read(@PathVariable UUID id,Authentication auth) {
        UUID user=user(auth);
        var n=notifications.findByIdAndUserAccountId(id,user).orElseThrow(NoSuchElementException::new);
        if(n.readAt==null) {n.readAt=Instant.now();notifications.save(n);}return response(n,user);
    }
    private Response response(NotificationEntity n,UUID user) {
        Target target=parse(n.link,n.type);
        if(target==null) return new Response(n.id,safeType(n.type),null,n.readAt,n.createdAt,
            neutral("UNAVAILABLE",n.createdAt,"Notification details unavailable"));
        var visible=projects.visibleProject(target.projectId(),user);
        if(visible.isEmpty()) return new Response(n.id,"RESTRICTED_ITEM",null,n.readAt,n.createdAt,
            neutral("RESTRICTED",n.createdAt,"This item is unavailable"));
        String safeLink=target.path();
        if(target.scheduleId()!=null) {
            var current=schedules.notificationTarget(target.projectId(),target.scheduleId());
            if(current.isEmpty()) safeLink=null;
            var history=readSnapshot(n.payload);
            if(!isScheduleType(n.type)) history=null;
            if(history==null) return new Response(n.id,safeType(n.type),safeLink,n.readAt,n.createdAt,
                new Content("LEGACY",null,null,null,n.createdAt,null,null,"Change details unavailable",List.of(),
                    visible.get().name(),current.map(ScheduleLookup.NotificationTarget::title).orElse(null),
                    current.map(ScheduleLookup.NotificationTarget::status).orElse(null),current.isPresent()));
            return new Response(n.id,safeType(n.type),safeLink,n.readAt,n.createdAt,
                new Content("SNAPSHOT",history.projectName(),history.scheduleTitle(),history.actorDisplayName(),
                    history.occurredAt()==null?n.createdAt:history.occurredAt(),history.scheduleStatus(),history.businessRevision(),
                    history.summary()==null?"Change details unavailable":history.summary(),history.changedFields(),
                    visible.get().name(),current.map(ScheduleLookup.NotificationTarget::title).orElse(null),
                    current.map(ScheduleLookup.NotificationTarget::status).orElse(null),current.isPresent()));
        }
        return new Response(n.id,safeType(n.type),safeLink,n.readAt,n.createdAt,
            new Content("LEGACY",null,null,null,n.createdAt,null,null,"Notification details unavailable",List.of(),
                visible.get().name(),null,null,true));
    }
    private Target parse(String link,String type) {
        if(link==null) return null;
        boolean scheduleType=isScheduleType(type);
        var matcher=scheduleType?SCHEDULE_LINK.matcher(link):PROJECT_LINK.matcher(link);
        if(!matcher.matches()) return null;
        try { UUID project=UUID.fromString(matcher.group(1));UUID schedule=scheduleType?UUID.fromString(matcher.group(2)):null;
            return new Target(project,schedule,link);
        } catch (IllegalArgumentException malformed) { return null; }
    }
    private Snapshot readSnapshot(Map<String,Object> payload) {
        if(payload==null||!(payload.get("content") instanceof Map<?,?> raw)) return null;
        String projectName=string(raw.get("projectName")),scheduleTitle=string(raw.get("scheduleTitle"));
        if(projectName==null||scheduleTitle==null) return null;
        Instant occurred=parseInstant(raw.get("occurredAt"));
        String status=string(raw.get("scheduleStatus"));String actor=string(raw.get("actorDisplayName"));
        String summary=string(raw.get("summary"));Long revision=number(raw.get("businessRevision"));
        var changes=new ArrayList<ChangedField>();
        if(raw.get("changedFields") instanceof Collection<?> entries) for(Object entry:entries) if(entry instanceof Map<?,?> field) {
            String key=string(field.get("field")),label=string(field.get("label"));
            if(key!=null&&label!=null) changes.add(new ChangedField(key,label,string(field.get("before")),string(field.get("after"))));
        }
        return new Snapshot(projectName,scheduleTitle,actor,occurred,status,revision,summary,List.copyOf(changes));
    }
    private static Instant parseInstant(Object value) { try {return value instanceof String s?Instant.parse(s):value instanceof Instant i?i:null;}catch(RuntimeException ignored){return null;} }
    private static String string(Object value) { return value instanceof String s && !s.isBlank()?s:null; }
    private static Long number(Object value) { return value instanceof Number n?n.longValue():null; }
    private static boolean isScheduleType(String type) { return type!=null&&SCHEDULE_TYPES.contains(type); }
    private Content neutral(String provenance,Instant time,String summary) {return new Content(provenance,null,null,null,time,null,null,summary,List.of(),null,null,null,false);}
    private String safeType(String type) {return type!=null&&type.matches("[A-Z_]{1,64}")?type:"UNKNOWN";}
    private UUID user(Authentication a) {if(a.getPrincipal() instanceof ApplicationPrincipal p)return p.userId();throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
    private record Target(UUID projectId,UUID scheduleId,String path) { }
    private record Snapshot(String projectName,String scheduleTitle,String actorDisplayName,Instant occurredAt,String scheduleStatus,Long businessRevision,String summary,List<ChangedField> changedFields) { }
    public record ChangedField(String field,String label,String before,String after) { }
    public record Content(String provenance,String projectName,String scheduleTitle,String actorDisplayName,Instant occurredAt,
                          String scheduleStatus,Long businessRevision,String summary,List<ChangedField> changedFields,
                          String currentProjectName,String currentScheduleTitle,String currentScheduleStatus,boolean resourceAvailable) { }
    public record Response(UUID id,String type,String link,Instant readAt,Instant createdAt,Content content) { }
}
