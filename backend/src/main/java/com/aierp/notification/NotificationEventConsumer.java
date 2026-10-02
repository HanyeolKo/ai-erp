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
        if(event.recipients()==null) return;
        for(UUID recipient:event.recipients()) {
            var notification=new NotificationEntity();notification.id=UUID.randomUUID();notification.userAccountId=recipient;
            notification.type=event.type();notification.link=event.type().startsWith("SCHEDULE")?"/projects/"+event.projectId()+"/schedules/"+event.aggregateId():"/projects/"+event.projectId();
            var payload=new LinkedHashMap<String,Object>();payload.put("businessRevision",event.businessRevision());
            if(event.notificationSnapshot()!=null) payload.put("content",toPayload(event.notificationSnapshot()));
            notification.payload=payload;notification.createdAt=Instant.now();notifications.save(notification);
            var delivery=new NotificationDeliveryEntity();delivery.id=UUID.randomUUID();delivery.notificationId=notification.id;
            delivery.channel="IN_APP";delivery.status="SENT";delivery.createdAt=Instant.now();deliveries.save(delivery);
        }
    }
    private Map<String,Object> toPayload(DomainEvent.NotificationSnapshot snapshot) {
        var content=new LinkedHashMap<String,Object>();
        content.put("projectName",snapshot.projectName());content.put("scheduleTitle",snapshot.scheduleTitle());
        content.put("actorDisplayName",snapshot.actorDisplayName());content.put("occurredAt",snapshot.occurredAt()==null?null:snapshot.occurredAt().toString());
        content.put("scheduleStatus",snapshot.scheduleStatus());content.put("businessRevision",snapshot.businessRevision());
        content.put("summary",snapshot.summary());
        content.put("changedFields",snapshot.changedFields().stream().map(f -> {
            var field=new LinkedHashMap<String,Object>();field.put("field",f.field());field.put("label",f.label());
            field.put("before",f.before());field.put("after",f.after());return field;
        }).toList());
        return content;
    }
}
