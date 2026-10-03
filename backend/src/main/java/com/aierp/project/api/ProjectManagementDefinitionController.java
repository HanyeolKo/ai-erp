package com.aierp.project.api;

import com.aierp.identity.api.ApplicationPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ProjectManagementDefinitionController {
    private final ProjectManagementDefinitionAccess definitions;
    public ProjectManagementDefinitionController(ProjectManagementDefinitionAccess definitions){this.definitions=definitions;}
    @GetMapping("/project-management/config") public Config config(Authentication auth){principal(auth);return new Config(definitions.enabled());}
    @GetMapping("/projects/{projectId}/management") public Definition get(@PathVariable UUID projectId,Authentication auth){return view(definitions.read(projectId,user(auth)));}
    @PatchMapping("/projects/{projectId}/management") public Definition patch(@PathVariable UUID projectId,@RequestBody DefinitionWrite body,Authentication auth){return view(definitions.update(projectId,new ProjectManagementDefinitionAccess.DefinitionWrite(body.purpose(),body.successCriteria(),body.responsibleManagerId(),body.health()==null?null:body.health().name(),body.healthReason(),body.healthAsOf(),body.rowVersion(),body.requestId()),user(auth)));}
    private static Definition view(ProjectManagementDefinitionAccess.DefinitionView v){return new Definition(v.projectId(),v.purpose(),v.successCriteria(),v.responsibleManagerId(),v.health()==null?null:Health.valueOf(v.health()),v.healthReason(),v.healthAsOf(),v.rowVersion(),v.canEdit());}
    private static UUID user(Authentication a){return principal(a).userId();} private static ApplicationPrincipal principal(Authentication a){if(a!=null&&a.getPrincipal() instanceof ApplicationPrincipal p)return p;throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
    public record Config(boolean enabled){}
    public enum Health{ON_TRACK,WATCH,AT_RISK}
    public record Definition(UUID projectId,String purpose,String successCriteria,UUID responsibleManagerId,Health health,String healthReason,LocalDate healthAsOf,long rowVersion,boolean canEdit){}
    public record DefinitionWrite(String purpose,String successCriteria,UUID responsibleManagerId,Health health,String healthReason,LocalDate healthAsOf,Long rowVersion,UUID requestId){}
}
