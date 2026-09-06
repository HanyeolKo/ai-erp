package com.aierp.notification;
import jakarta.persistence.*;
import java.util.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(schema="notification",name="notification")
public class NotificationEntity {
    @Id public UUID id; public UUID userAccountId; public String type; public String link;
    @JdbcTypeCode(SqlTypes.JSON) public Map<String,Object> payload;
    public Instant readAt; public Instant createdAt;
}
