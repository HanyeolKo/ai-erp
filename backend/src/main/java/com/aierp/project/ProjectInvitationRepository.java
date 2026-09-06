package com.aierp.project;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitationEntity,UUID> {
    Optional<ProjectInvitationEntity> findByToken(String token);
    @Query("select i.projectId from ProjectInvitationEntity i where i.token = :token")
    Optional<UUID> findProjectIdByToken(String token);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProjectInvitationEntity i where i.token = :token")
    Optional<ProjectInvitationEntity> lockByToken(String token);
    List<ProjectInvitationEntity> findByProjectIdAndEmailAndStatus(UUID projectId,String email,ProjectInvitationEntity.Status status);
}
