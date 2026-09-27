package com.aierp.projectplan;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;

@Entity @Table(schema="project", name="plan_item_dependency") @IdClass(PlanItemDependencyEntity.Key.class)
public class PlanItemDependencyEntity {
    @Id public UUID itemId;
    @Id public UUID predecessorId;
    public PlanItemDependencyEntity() { }
    public PlanItemDependencyEntity(UUID itemId, UUID predecessorId) { this.itemId=itemId; this.predecessorId=predecessorId; }
    public static class Key implements Serializable {
        public UUID itemId; public UUID predecessorId;
        public Key() { }
        public Key(UUID itemId, UUID predecessorId) { this.itemId=itemId; this.predecessorId=predecessorId; }
        public boolean equals(Object o) { return o instanceof Key k && Objects.equals(itemId,k.itemId) && Objects.equals(predecessorId,k.predecessorId); }
        public int hashCode() { return Objects.hash(itemId,predecessorId); }
    }
}
