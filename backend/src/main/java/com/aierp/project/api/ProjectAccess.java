package com.aierp.project.api;

import com.aierp.project.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccess {
    private final ProjectMemberRepository members;
    public ProjectAccess(ProjectMemberRepository members) { this.members = members; }

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
    public List<UUID> memberIds(UUID projectId) {
        return members.findByProjectId(projectId).stream().map(m -> m.userAccountId).toList();
    }
}
