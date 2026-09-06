package com.aierp.project.api;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.project.*;
import com.aierp.platform.web.ValidationFailure;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class InvitationController {
    private final InvitationService invitations;
    public InvitationController(InvitationService invitations) {this.invitations=invitations;}
    @PostMapping("/groups/{groupId}/invitations") public InvitationResponse invite(@PathVariable UUID groupId,@RequestBody InviteRequest input,Authentication auth) {return invitations.invite(groupId,input,principal(auth));}
    @GetMapping("/invitations/{token}") public InvitationResponse get(@PathVariable String token,Authentication auth) {return invitations.detail(token,principal(auth));}
    @PostMapping("/invitations/{token}/accept") public InvitationResponse accept(@PathVariable String token,Authentication auth) {return invitations.resolve(token,principal(auth),true);}
    @PostMapping("/invitations/{token}/reject") public InvitationResponse reject(@PathVariable String token,Authentication auth) {return invitations.resolve(token,principal(auth),false);}
    @PostMapping("/invitations/{token}") public InvitationResponse resolve(@PathVariable String token,@RequestBody Resolution input,Authentication auth) {
        if(!"ACCEPT".equals(input.action()) && !"REJECT".equals(input.action())) throw new ValidationFailure("action","Expected ACCEPT or REJECT");
        return invitations.resolve(token,principal(auth),"ACCEPT".equals(input.action()));
    }
    private static ApplicationPrincipal principal(Authentication auth) {
        if(auth.getPrincipal() instanceof ApplicationPrincipal p) return p;
        throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");
    }
    public record Resolution(String action) {}
    public record InviteRequest(UUID projectId,String email) {}
    public record InvitationResponse(UUID id,UUID projectId,String email,String token,ProjectInvitationEntity.Status status,Instant expiresAt) {}
}
