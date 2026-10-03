package com.aierp.project;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface ManagementMutationReceiptRepository extends JpaRepository<ManagementMutationReceiptEntity,ManagementMutationReceiptEntity.Key> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ManagementMutationReceiptEntity r where r.id.projectId=:projectId and r.id.actorId=:actorId and r.id.requestId=:requestId")
    Optional<ManagementMutationReceiptEntity> lock(UUID projectId,UUID actorId,UUID requestId);
    Optional<ManagementMutationReceiptEntity> findByIdProjectIdAndIdActorIdAndIdRequestId(UUID projectId,UUID actorId,UUID requestId);
}
