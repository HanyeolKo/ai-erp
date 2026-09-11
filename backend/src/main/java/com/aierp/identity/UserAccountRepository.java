package com.aierp.identity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserAccountRepository extends JpaRepository<UserAccountEntity,UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccountEntity u where u.id = :id")
    Optional<UserAccountEntity> lockById(UUID id);
}
