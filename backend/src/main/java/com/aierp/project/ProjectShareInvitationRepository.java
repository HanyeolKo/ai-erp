package com.aierp.project;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface ProjectShareInvitationRepository extends JpaRepository<ProjectShareInvitationEntity, UUID> {
    Optional<ProjectShareInvitationEntity> findByCode(String code);
    @Query("select i.projectId from ProjectShareInvitationEntity i where i.code = :code")
    Optional<UUID> findProjectIdByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProjectShareInvitationEntity i where i.projectId = :projectId")
    Optional<ProjectShareInvitationEntity> findForUpdateByProjectId(UUID projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProjectShareInvitationEntity i where i.code = :code")
    Optional<ProjectShareInvitationEntity> findForUpdateByCode(String code);
}
