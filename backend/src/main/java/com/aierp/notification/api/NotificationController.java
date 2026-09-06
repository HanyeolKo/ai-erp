package com.aierp.notification.api;
import com.aierp.notification.*;
import com.aierp.identity.api.ApplicationPrincipal;
import java.util.*;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository notifications;
    public NotificationController(NotificationRepository notifications) {this.notifications=notifications;}
    @GetMapping public List<Response> list(Authentication auth) {return notifications.findTop100ByUserAccountIdOrderByCreatedAtDesc(user(auth)).stream().map(this::response).toList();}
    @PostMapping("/{id}/read") @Transactional public Response read(@PathVariable UUID id,Authentication auth) {
        var n=notifications.findByIdAndUserAccountId(id,user(auth)).orElseThrow(NoSuchElementException::new);
        if(n.readAt==null) {n.readAt=Instant.now();notifications.save(n);}return response(n);
    }
    private UUID user(Authentication a) {if(a.getPrincipal() instanceof ApplicationPrincipal p)return p.userId();throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
    private Response response(NotificationEntity n) {return new Response(n.id,n.type,n.link,n.readAt,n.createdAt);}
    public record Response(UUID id,String type,String link,Instant readAt,Instant createdAt) {}
}
