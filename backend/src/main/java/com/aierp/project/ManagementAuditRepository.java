package com.aierp.project;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagementAuditRepository extends JpaRepository<ManagementAuditEntity,UUID> {
    Page<ManagementAuditEntity> findByProjectIdAndResourceTypeAndResourceIdOrderByOccurredAtDescIdDesc(UUID projectId,String resourceType,UUID resourceId,Pageable page);
}
