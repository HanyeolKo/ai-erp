package com.aierp.project.api;

import com.aierp.project.ProjectMemberRepository;
import com.aierp.project.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Narrow notification read API that checks live project existence and membership together. */
@Service
public class ProjectNotificationAccess {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    public ProjectNotificationAccess(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects; this.members = members;
    }
    public Optional<ProjectView> visibleProject(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) return Optional.empty();
        var project = projects.findById(projectId).orElse(null);
        if (project == null || members.findByProjectIdAndUserAccountId(projectId, userId).isEmpty()) return Optional.empty();
        return Optional.of(new ProjectView(project.id, project.name));
    }
    public record ProjectView(UUID id, String name) { }
}
