package com.aierp.project.api;

import com.aierp.project.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccess {
    private final ProjectMemberRepository members;
    private final ProjectRepository projects;
    public ProjectAccess(ProjectMemberRepository members) { this(members, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public ProjectAccess(ProjectMemberRepository members, ProjectRepository projects) { this.members = members; this.projects = projects; }

    /** Serializes project-scoped configuration mutations against the stable project row. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, readOnly = true)
    public void lockProject(UUID projectId) {
        if (projects == null || projects.lockById(projectId).isEmpty()) throw new NoSuchElementException("PROJECT_NOT_FOUND");
    }

    public String role(UUID projectId, UUID userId) {
        return members.findByProjectIdAndUserAccountId(projectId, userId)
                .orElseThrow(() -> new AccessDeniedException("PROJECT_ACCESS_DENIED")).role.name();
    }
    public void requireManager(UUID projectId, UUID userId) {
        if (!role(projectId, userId).equals("MANAGER")) throw new AccessDeniedException("MANAGER_REQUIRED");
    }
    public void requireWriter(UUID projectId, UUID userId, UUID creator) {
        String role = role(projectId, userId);
        if (role.equals("VIEWER") || (role.equals("MEMBER") && creator != null && !creator.equals(userId)))
            throw new AccessDeniedException("SCHEDULE_WRITE_DENIED");
    }
    /** Planning is a collaborative project resource: active MANAGER and MEMBER may write. */
    public void requirePlanWriter(UUID projectId, UUID userId) {
        String current = role(projectId,userId);
        if (current.equals("VIEWER")) throw new AccessDeniedException("PROJECT_PLAN_WRITE_DENIED");
    }
    /** Assignees are checked through the project module so planning never reads membership tables directly. */
    public void requirePlanAssignee(UUID projectId, UUID assigneeId) {
        if (assigneeId != null && members.findByProjectIdAndUserAccountId(projectId,assigneeId)
                .filter(m -> m.role != ProjectRole.VIEWER).isEmpty())
            throw new com.aierp.platform.web.ValidationFailure("assigneeId","Assignee must be an active project MANAGER or MEMBER");
    }
    public long memberCount(UUID projectId) { return members.countByProjectId(projectId); }
    /** Resolves current active MANAGER/MEMBER eligibility in one module-owned batch. */
    public Set<UUID> eligibleAcknowledgers(UUID projectId, Collection<UUID> userIds) {
        if (userIds.size() > 200) throw new IllegalArgumentException("Eligibility batch exceeds 200");
        return userIds.isEmpty() ? Set.of() : members.findEligibleAcknowledgers(projectId, userIds);
    }
}
