package com.aierp.notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity,UUID> {}
