package com.aierp.platform.events;
import java.util.*;
import java.time.Instant;

/** Immutable event facts captured by the owning transaction and safe to relay later. */
public record DomainEvent(UUID publicationId,String type,UUID aggregateId,UUID projectId,UUID actorId,
                          List<UUID> recipients,long businessRevision,NotificationSnapshot notificationSnapshot) {
    /** Backward-compatible constructor for existing producers and persisted legacy payloads. */
    public DomainEvent(UUID publicationId,String type,UUID aggregateId,UUID projectId,UUID actorId,
                       List<UUID> recipients,long businessRevision) {
        this(publicationId,type,aggregateId,projectId,actorId,recipients,businessRevision,null);
    }
    public DomainEvent {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }

    public record NotificationSnapshot(String projectName, String scheduleTitle, String actorDisplayName,
            Instant occurredAt, String scheduleStatus, long businessRevision, String summary,
            List<ChangedField> changedFields) {
        public NotificationSnapshot { changedFields = changedFields == null ? List.of() : List.copyOf(changedFields); }
    }
    public record ChangedField(String field, String label, String before, String after) { }
}
