package com.aierp.project;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberEntity.Key> {
    List<ProjectMemberEntity> findByUserAccountId(UUID userAccountId);
    Optional<ProjectMemberEntity> findByProjectIdAndUserAccountId(UUID projectId, UUID userAccountId);
    List<ProjectMemberEntity> findByProjectId(UUID projectId);
}
