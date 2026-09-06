package com.aierp.platform.events;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class EventRelay {
    private final EventPublicationRepository publications;
    private final List<EventConsumer> consumers;
    public EventRelay(EventPublicationRepository publications,List<EventConsumer> consumers) {this.publications=publications;this.consumers=consumers;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void deliver(UUID id) {
        var publication=publications.lockById(id).orElseThrow();
        if(publication.publishedAt!=null) return;
        for(var consumer:consumers) consumer.consume(publication.payload);
        publication.publishedAt=Instant.now();publications.save(publication);
    }
}
