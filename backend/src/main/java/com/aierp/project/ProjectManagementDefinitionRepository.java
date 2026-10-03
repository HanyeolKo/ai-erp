package com.aierp.project;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface ProjectManagementDefinitionRepository extends JpaRepository<ProjectManagementDefinitionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ProjectManagementDefinitionEntity d where d.projectId = :projectId")
    Optional<ProjectManagementDefinitionEntity> lockByProjectId(UUID projectId);
}
