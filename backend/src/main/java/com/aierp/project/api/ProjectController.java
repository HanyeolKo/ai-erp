package com.aierp.project.api;

import com.aierp.group.api.GroupAccess;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.platform.web.ReadLimits;
import com.aierp.platform.web.ValidationFailure;
import com.aierp.project.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Project reads and membership changes backed by the project schema. */
@RestController @RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final GroupAccess groups;
    public ProjectController(ProjectRepository projects, ProjectMemberRepository members, GroupAccess groups) { this.projects = projects; this.members = members; this.groups = groups; }

    @GetMapping("/creation-options")
    public List<GroupAccess.CreationOption> creationOptions(Authentication auth,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer limit) {
        return groups.creationOptions(principal(auth).userId(),page,limit);
    }
    @PostMapping @Transactional public ProjectResponse create(@RequestBody CreateRequest body,Authentication auth) {
        var user = principal(auth);
        if (body == null) throw new ValidationFailure("groupId","A group is required");
        if (body.groupId() == null) throw new ValidationFailure("groupId","A group is required");
        var name = body.name() == null ? "" : body.name().trim();
        if (name.isBlank() || name.length() > 200) throw new ValidationFailure("name","Project name must contain 1 to 200 characters");
        groups.requireProjectCreator(body.groupId(),user.userId());
        var project = new ProjectEntity(UUID.randomUUID(),body.groupId(),name);
        projects.save(project);
        var creator = new ProjectMemberEntity();creator.projectId=project.id;creator.userAccountId=user.userId();creator.role=ProjectRole.MANAGER;
        members.save(creator);
        return new ProjectResponse(project.id,project.groupId,project.name,creator.role);
    }

    @GetMapping public List<ProjectResponse> list(Authentication auth, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer limit) {
        var user = principal(auth);
        var memberships = members.findByUserAccountId(user.userId(), ReadLimits.page(page,limit,Sort.by("projectId")));
        if (memberships.isEmpty()) return List.of();
        var byId = projects.findAllById(memberships.stream().map(m -> m.projectId).toList()).stream().collect(Collectors.toMap(p -> p.id, Function.identity()));
        return memberships.stream().filter(m -> byId.containsKey(m.projectId)).map(m -> {
            var p=byId.get(m.projectId);return new ProjectResponse(p.id,p.groupId,p.name,m.role);
        }).toList();
    }
    @GetMapping("/{projectId}/members") public List<MemberResponse> members(@PathVariable UUID projectId, Authentication auth, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer limit) {
        requireMember(projectId, principal(auth));
        return members.findByProjectId(projectId,ReadLimits.page(page,limit,Sort.by("userAccountId"))).stream().map(m -> new MemberResponse(m.userAccountId, m.role)).toList();
    }
    @PatchMapping("/{projectId}/members/{userId}") @Transactional public MemberResponse changeRole(@PathVariable UUID projectId, @PathVariable UUID userId, @RequestBody RoleRequest body, Authentication auth) {
        requireManager(projectId, principal(auth));
        if (body.role() == null) throw new com.aierp.platform.web.ValidationFailure("role", "A project role is required");
        var member = members.findByProjectIdAndUserAccountId(projectId, userId).orElseThrow(() -> new NoSuchElementException("PROJECT_MEMBER_NOT_FOUND"));
        member.role = body.role(); members.save(member); return new MemberResponse(member.userAccountId, member.role);
    }
    private ProjectMemberEntity requireMember(UUID projectId, ApplicationPrincipal user) { return members.findByProjectIdAndUserAccountId(projectId, user.userId()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("PROJECT_ACCESS_DENIED")); }
    private void requireManager(UUID projectId, ApplicationPrincipal user) { if (requireMember(projectId, user).role != ProjectRole.MANAGER) throw new org.springframework.security.access.AccessDeniedException("MANAGER_REQUIRED"); }
    private static ApplicationPrincipal principal(Authentication a) { if (a.getPrincipal() instanceof ApplicationPrincipal p) return p; throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED"); }
    public record ProjectResponse(UUID id, UUID groupId, String name, ProjectRole role) { }
    public record CreateRequest(UUID groupId,String name) { }
    public record MemberResponse(UUID userId, ProjectRole role) { }
    public record RoleRequest(ProjectRole role) { }
}
