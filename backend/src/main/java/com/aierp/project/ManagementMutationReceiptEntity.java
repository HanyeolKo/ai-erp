package com.aierp.project;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(schema="project", name="management_mutation_receipt")
public class ManagementMutationReceiptEntity {
    @EmbeddedId public Key id;
    @Column(length=80, nullable=false) public String operation;
    @Column(name="resource_type", length=32, nullable=false) public String resourceType;
    @Column(name="resource_id", nullable=false) public UUID resourceId;
    @Column(name="payload_hash", length=64, nullable=false) public String payloadHash;
    @Column(name="row_version", nullable=false) public long rowVersion;
    @Column(length=16, nullable=false) public String status;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    public ManagementMutationReceiptEntity() { }
    public ManagementMutationReceiptEntity(UUID projectId,UUID actorId,UUID requestId) { id=new Key(projectId,actorId,requestId); }
    @Embeddable public static class Key {
        @Column(name="project_id") public UUID projectId; @Column(name="actor_id") public UUID actorId; @Column(name="request_id") public UUID requestId;
        public Key() { } public Key(UUID p,UUID a,UUID r){projectId=p;actorId=a;requestId=r;}
        @Override public boolean equals(Object o){return o instanceof Key k&&Objects.equals(projectId,k.projectId)&&Objects.equals(actorId,k.actorId)&&Objects.equals(requestId,k.requestId);}
        @Override public int hashCode(){return Objects.hash(projectId,actorId,requestId);}
    }
}
