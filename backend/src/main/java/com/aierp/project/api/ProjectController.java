package com.aierp.project.api;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.project.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Project reads and membership changes backed by the project schema. */
@RestController @RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    public ProjectController(ProjectRepository projects, ProjectMemberRepository members) { this.projects = projects; this.members = members; }

    @GetMapping public List<ProjectResponse> list(Authentication auth) {
        var user = principal(auth);
        return members.findByUserAccountId(user.userId()).stream().map(m -> projects.findById(m.projectId).map(p -> new ProjectResponse(p.id, p.name, m.role)).orElse(null)).filter(Objects::nonNull).toList();
    }
    @GetMapping("/{projectId}/dashboard") public DashboardResponse dashboard(@PathVariable UUID projectId, Authentication auth) {
        requireMember(projectId, principal(auth));
        return new DashboardResponse(projectId, members.findByProjectId(projectId).size());
    }
    @GetMapping("/{projectId}/members") public List<MemberResponse> members(@PathVariable UUID projectId, Authentication auth) {
        requireMember(projectId, principal(auth));
        return members.findByProjectId(projectId).stream().map(m -> new MemberResponse(m.userAccountId, m.role)).toList();
    }
    @PatchMapping("/{projectId}/members/{userId}") @Transactional public MemberResponse changeRole(@PathVariable UUID projectId, @PathVariable UUID userId, @RequestBody RoleRequest body, Authentication auth) {
        requireManager(projectId, principal(auth));
        var member = members.findByProjectIdAndUserAccountId(projectId, userId).orElseThrow(() -> new NoSuchElementException("PROJECT_MEMBER_NOT_FOUND"));
        member.role = body.role(); members.save(member); return new MemberResponse(member.userAccountId, member.role);
    }
    private ProjectMemberEntity requireMember(UUID projectId, ApplicationPrincipal user) { return members.findByProjectIdAndUserAccountId(projectId, user.userId()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("PROJECT_ACCESS_DENIED")); }
    private void requireManager(UUID projectId, ApplicationPrincipal user) { if (requireMember(projectId, user).role != ProjectRole.MANAGER) throw new org.springframework.security.access.AccessDeniedException("MANAGER_REQUIRED"); }
    private static ApplicationPrincipal principal(Authentication a) { if (a.getPrincipal() instanceof ApplicationPrincipal p) return p; throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED"); }
    public record ProjectResponse(UUID id, String name, ProjectRole role) { }
    public record DashboardResponse(UUID projectId, int memberCount) { }
    public record MemberResponse(UUID userId, ProjectRole role) { }
    public record RoleRequest(ProjectRole role) { }
}
