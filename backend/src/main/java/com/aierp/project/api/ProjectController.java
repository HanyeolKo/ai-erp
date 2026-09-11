package com.aierp.project.api;

import com.aierp.group.api.GroupAccess;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.identity.api.IdentityProfiles;
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
    private final ProjectCreationRequestRepository creationRequests;
    private final IdentityProfiles profiles;
    @org.springframework.beans.factory.annotation.Autowired
    public ProjectController(ProjectRepository projects, ProjectMemberRepository members, GroupAccess groups,
                             ProjectCreationRequestRepository creationRequests, IdentityProfiles profiles) {
        this.projects = projects; this.members = members; this.groups = groups; this.creationRequests = creationRequests; this.profiles = profiles;
    }

    @GetMapping("/creation-options")
    public List<GroupAccess.CreationOption> creationOptions(Authentication auth,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer limit) {
        return groups.creationOptions(principal(auth).userId(),page,limit);
    }
    @PostMapping @Transactional public ProjectResponse create(@RequestBody CreateRequest body,Authentication auth) {
        var user = principal(auth);
        if (body == null) throw new ValidationFailure("name","Project name must contain 1 to 200 characters");
        var name = body.name() == null ? "" : body.name().trim();
        if (name.isBlank() || name.length() > 200) throw new ValidationFailure("name","Project name must contain 1 to 200 characters");
        if (body.requestId() != null) {
            creationRequests.lockRequest("project-create:" + user.userId() + ":" + body.requestId());
            var prior = creationRequests.findByActorIdAndRequestId(user.userId(), body.requestId());
            if (prior.isPresent()) {
                if (!name.equals(prior.get().normalizedName) || !Objects.equals(body.groupId(), prior.get().requestedGroupId))
                    throw new IllegalStateException("PROJECT_CREATION_PAYLOAD_MISMATCH");
                var existing = projects.findById(prior.get().projectId).orElseThrow(NoSuchElementException::new);
                var membership = members.findByProjectIdAndUserAccountId(existing.id,user.userId()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("PROJECT_ACCESS_DENIED"));
                return new ProjectResponse(existing.id,existing.groupId,existing.name,membership.role);
            }
        }
        UUID groupId = body.groupId();
        if (groupId == null) groupId = groups.bootstrapProjectGroup(user.userId(),name);
        else groups.requireProjectCreator(groupId,user.userId());
        var project = new ProjectEntity(UUID.randomUUID(),groupId,name);
        projects.save(project);
        var creator = new ProjectMemberEntity();creator.projectId=project.id;creator.userAccountId=user.userId();creator.role=ProjectRole.MANAGER;
        members.save(creator);
        if (body.requestId() != null) {
            var request = new ProjectCreationRequestEntity();
            request.id=UUID.randomUUID(); request.actorId=user.userId(); request.requestId=body.requestId();
            request.normalizedName=name; request.requestedGroupId=body.groupId(); request.projectId=project.id; request.createdAt=java.time.Instant.now();
            creationRequests.save(request);
        }
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
        var rows = members.findByProjectId(projectId,ReadLimits.page(page,limit,Sort.by("userAccountId")));
        var profileMap = profiles.find(rows.stream().map(m -> m.userAccountId).toList());
        return rows.stream().map(m -> memberResponse(m,profileMap)).toList();
    }
    @PatchMapping("/{projectId}/members/{userId}") @Transactional public MemberResponse changeRole(@PathVariable UUID projectId, @PathVariable UUID userId, @RequestBody RoleRequest body, Authentication auth) {
        if (body == null || body.role() == null) throw new com.aierp.platform.web.ValidationFailure("role", "A project role is required");
        var actor = principal(auth);
        var project = projects.lockById(projectId).orElseThrow(NoSuchElementException::new);
        requireManagerLocked(projectId, actor);
        var member = members.findByProjectIdAndUserAccountId(projectId, userId).orElseThrow(() -> new NoSuchElementException("PROJECT_MEMBER_NOT_FOUND"));
        if (member.role == ProjectRole.MANAGER && body.role() != ProjectRole.MANAGER && members.countByProjectIdAndRole(projectId, ProjectRole.MANAGER) <= 1)
            throw new IllegalStateException("LAST_MANAGER_REQUIRED");
        member.role = body.role(); members.save(member);
        return memberResponse(member, profiles.find(Set.of(member.userAccountId)));
    }
    private ProjectMemberEntity requireMember(UUID projectId, ApplicationPrincipal user) { return members.findByProjectIdAndUserAccountId(projectId, user.userId()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("PROJECT_ACCESS_DENIED")); }
    private void requireManager(UUID projectId, ApplicationPrincipal user) { if (requireMember(projectId, user).role != ProjectRole.MANAGER) throw new org.springframework.security.access.AccessDeniedException("MANAGER_REQUIRED"); }
    private void requireManagerLocked(UUID projectId, ApplicationPrincipal user) { if (members.findByProjectIdAndUserAccountId(projectId, user.userId()).filter(m -> m.role == ProjectRole.MANAGER).isEmpty()) throw new org.springframework.security.access.AccessDeniedException("MANAGER_REQUIRED"); }
    private static MemberResponse memberResponse(ProjectMemberEntity member, Map<UUID,IdentityProfiles.PublicProfile> profileMap) {
        var p=profileMap.get(member.userAccountId); return new MemberResponse(member.userAccountId,member.role,p == null ? "사용자" : p.displayName(),p == null ? null : p.email());
    }
    private static ApplicationPrincipal principal(Authentication a) { if (a.getPrincipal() instanceof ApplicationPrincipal p) return p; throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED"); }
    public record ProjectResponse(UUID id, UUID groupId, String name, ProjectRole role) { }
    public record CreateRequest(UUID groupId,String name,UUID requestId) {
        public CreateRequest(UUID groupId,String name) { this(groupId,name,null); }
    }
    public record MemberResponse(UUID userId, ProjectRole role, String displayName, String email) {
        public MemberResponse(UUID userId, ProjectRole role) { this(userId,role,"사용자",null); }
    }
    public record RoleRequest(ProjectRole role) { }
}
