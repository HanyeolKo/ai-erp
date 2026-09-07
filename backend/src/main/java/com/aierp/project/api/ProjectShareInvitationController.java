package com.aierp.project.api;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.project.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ProjectShareInvitationController {
    private final ProjectShareInvitationService service;
    public ProjectShareInvitationController(ProjectShareInvitationService service) { this.service=service; }
    @GetMapping("/projects/{projectId}/share-invitation") public ProjectShareInvitationService.ShareInvitationResponse get(@PathVariable UUID projectId,Authentication auth) { return service.current(projectId,principal(auth)); }
    @PostMapping("/projects/{projectId}/share-invitation") public ProjectShareInvitationService.ShareInvitationResponse rotate(@PathVariable UUID projectId,Authentication auth) { return service.rotate(projectId,principal(auth)); }
    @DeleteMapping("/projects/{projectId}/share-invitation") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID projectId,Authentication auth) { service.revoke(projectId,principal(auth)); }
    @GetMapping("/project-invitations/{code}") public ProjectShareInvitationService.SharePreviewResponse preview(@PathVariable String code,Authentication auth) { return service.preview(code,principal(auth)); }
    @PostMapping("/project-invitations/{code}/join") public ProjectShareInvitationService.ProjectControllerResponse join(@PathVariable String code,Authentication auth) { return service.join(code,principal(auth)); }
    private static ApplicationPrincipal principal(Authentication auth) { if(auth.getPrincipal() instanceof ApplicationPrincipal p) return p; throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED"); }
}
