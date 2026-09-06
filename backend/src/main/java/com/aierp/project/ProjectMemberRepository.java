package com.aierp.project;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberEntity.Key> {
    List<ProjectMemberEntity> findByUserAccountId(UUID userAccountId, Pageable page);
    Optional<ProjectMemberEntity> findByProjectIdAndUserAccountId(UUID projectId, UUID userAccountId);
    List<ProjectMemberEntity> findByProjectId(UUID projectId, Pageable page);
    long countByProjectId(UUID projectId);
    @Query("select m.userAccountId from ProjectMemberEntity m where m.projectId = :projectId and m.userAccountId in :userIds and m.role <> com.aierp.project.ProjectRole.VIEWER")
    Set<UUID> findEligibleAcknowledgers(UUID projectId, Collection<UUID> userIds);
}
