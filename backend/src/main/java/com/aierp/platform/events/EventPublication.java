package com.aierp.platform.events;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(schema="platform",name="event_publication")
public class EventPublication {
    @Id public UUID id;
    public String eventType;
    public UUID aggregateId;
    @JdbcTypeCode(SqlTypes.JSON) public DomainEvent payload;
    public Instant publishedAt;
    public Instant createdAt;
}
