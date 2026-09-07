package com.aierp.project;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface ProjectCreationRequestRepository extends JpaRepository<ProjectCreationRequestEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectCreationRequestEntity> findByActorIdAndRequestId(UUID actorId, UUID requestId);
    @Query(value="select 1 from pg_advisory_xact_lock(hashtextextended(:scope, 0))", nativeQuery=true)
    int lockRequest(String scope);
}
