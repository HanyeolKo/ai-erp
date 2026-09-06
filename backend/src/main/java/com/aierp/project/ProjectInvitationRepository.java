package com.aierp.project;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitationEntity,UUID>{ Optional<ProjectInvitationEntity> findByToken(String token); boolean existsByProjectIdAndEmailAndStatus(UUID projectId,String email,ProjectInvitationEntity.Status status); }
