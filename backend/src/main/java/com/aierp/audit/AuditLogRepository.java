package com.aierp.audit;
import java.util.UUID;
import org.springframework.data.repository.Repository;
public interface AuditLogRepository extends Repository<AuditLogEntity,UUID> {
    AuditLogEntity save(AuditLogEntity audit);
}
