package com.aierp.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NotificationRepository extends JpaRepository<NotificationEntity,UUID> {
    List<NotificationEntity> findTop100ByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId);
    Optional<NotificationEntity> findByIdAndUserAccountId(UUID id,UUID userAccountId);
}
