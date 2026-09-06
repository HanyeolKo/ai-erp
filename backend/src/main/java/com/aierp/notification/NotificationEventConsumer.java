package com.aierp.notification;
import com.aierp.platform.events.*;
import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component
public class NotificationEventConsumer implements EventConsumer {
    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    public NotificationEventConsumer(NotificationRepository notifications,NotificationDeliveryRepository deliveries) {this.notifications=notifications;this.deliveries=deliveries;}
    @Override public void consume(DomainEvent event) {
        if(event.type().equals("SCHEDULE_ACKNOWLEDGED")) return;
        for(UUID recipient:event.recipients()) {
            var notification=new NotificationEntity();notification.id=UUID.randomUUID();notification.userAccountId=recipient;
            notification.type=event.type();notification.link=event.type().startsWith("SCHEDULE")?"/projects/"+event.projectId()+"/schedules/"+event.aggregateId():"/projects/"+event.projectId();
            notification.payload=Map.of("businessRevision",event.businessRevision());notification.createdAt=Instant.now();notifications.save(notification);
            var delivery=new NotificationDeliveryEntity();delivery.id=UUID.randomUUID();delivery.notificationId=notification.id;
            delivery.channel="IN_APP";delivery.status="SENT";delivery.createdAt=Instant.now();deliveries.save(delivery);
        }
    }
}
