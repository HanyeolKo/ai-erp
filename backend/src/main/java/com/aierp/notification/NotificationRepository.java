package com.aierp.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.domain.Pageable;
public interface NotificationRepository extends JpaRepository<NotificationEntity,UUID> {
    List<NotificationEntity> findByUserAccountId(UUID userAccountId, Pageable page);
    Optional<NotificationEntity> findByIdAndUserAccountId(UUID id,UUID userAccountId);
}
