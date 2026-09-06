package com.aierp.platform.events;
import java.util.*;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class EventJournal {
    private final EventPublicationRepository publications;
    private final ApplicationEventPublisher publisher;
    public EventJournal(EventPublicationRepository publications,ApplicationEventPublisher publisher) {this.publications=publications;this.publisher=publisher;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(String type,UUID aggregateId,UUID projectId,UUID actorId,List<UUID> recipients,long revision) {
        var event=new DomainEvent(UUID.randomUUID(),type,aggregateId,projectId,actorId,List.copyOf(recipients),revision);
        var stored=new EventPublication();stored.id=event.publicationId();stored.eventType=type;stored.aggregateId=aggregateId;stored.payload=event;stored.createdAt=Instant.now();
        publications.save(stored);
        publisher.publishEvent(event);
    }
}
