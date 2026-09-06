package com.aierp.platform.events;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EventPublicationRepository extends JpaRepository<EventPublication,UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from EventPublication p where p.id = :id")
    Optional<EventPublication> lockById(UUID id);
    List<EventPublication> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
