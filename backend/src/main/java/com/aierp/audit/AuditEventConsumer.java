package com.aierp.audit;
import com.aierp.platform.events.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class AuditEventConsumer implements EventConsumer {
    private final AuditLogRepository logs;
    public AuditEventConsumer(AuditLogRepository logs) {this.logs=logs;}
    @Override public void consume(DomainEvent event) {
        var log=new AuditLogEntity();log.id=event.publicationId();log.actorId=event.actorId();log.action=event.type();
        log.aggregateType=event.type().startsWith("SCHEDULE")?"SCHEDULE":"INVITATION";log.aggregateId=event.aggregateId();
        log.payload=Map.of("projectId",event.projectId().toString(),"businessRevision",event.businessRevision());log.occurredAt=Instant.now();logs.save(log);
    }
}
