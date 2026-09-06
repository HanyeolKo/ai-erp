package com.aierp.platform.events;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.event.*;
import java.util.UUID;
@Component @EnableScheduling
public class OutboxWorker {
    private final EventRelay relay;
    private final EventPublicationRepository publications;
    public OutboxWorker(EventRelay relay,EventPublicationRepository publications) {this.relay=relay;this.publications=publications;}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void committed(DomainEvent event) { attempt(event.publicationId()); }
    @Scheduled(fixedDelayString="${app.outbox.delay-ms:10000}")
    public void retryPending() {publications.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().forEach(p->attempt(p.id));}
    private void attempt(UUID id) {
        try {relay.deliver(id);}
        catch(RuntimeException failure) {
            // Never propagate delivery failure to the already committed command; retry from durable storage.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Event delivery pending: {}",id);
        }
    }
}
