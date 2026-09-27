package com.aierp.projectplan;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

@Entity @Table(schema="project", name="plan_item_creation_request") @IdClass(PlanItemCreationRequestEntity.Key.class)
public class PlanItemCreationRequestEntity {
    @Id public UUID projectId;
    @Id public UUID actorId;
    @Id public UUID requestId;
    public String payloadHash;
    public UUID itemId;
    public Instant createdAt;
    public PlanItemCreationRequestEntity() { }
    public static class Key implements Serializable {
        public UUID projectId; public UUID actorId; public UUID requestId;
        public Key() { }
        public boolean equals(Object o) { return o instanceof Key k && Objects.equals(projectId,k.projectId) && Objects.equals(actorId,k.actorId) && Objects.equals(requestId,k.requestId); }
        public int hashCode() { return Objects.hash(projectId,actorId,requestId); }
    }
}
