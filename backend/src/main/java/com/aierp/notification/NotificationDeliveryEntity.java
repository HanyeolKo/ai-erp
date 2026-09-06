package com.aierp.notification;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(schema="notification",name="notification_delivery")
public class NotificationDeliveryEntity {
    @Id public UUID id;public UUID notificationId;public String channel;public String status;
    public String retryClassification;public Instant createdAt;
}
