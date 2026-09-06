package com.aierp.identity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GoogleIdentityRepository extends JpaRepository<GoogleIdentityEntity,UUID> {
    @org.springframework.data.jpa.repository.Query(value="select 1 from pg_advisory_xact_lock(hashtextextended(:subject, 0))",nativeQuery=true)
    int lockSubject(String subject);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GoogleIdentityEntity> findBySubject(String subject);
}
