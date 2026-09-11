package com.aierp.identity;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface GoogleAuthorizationRepository extends JpaRepository<GoogleAuthorizationEntity, UUID> {
    Optional<GoogleAuthorizationEntity> findByUserAccountId(UUID userAccountId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from GoogleAuthorizationEntity g where g.userAccountId = :userAccountId")
    Optional<GoogleAuthorizationEntity> lockByUserAccountId(UUID userAccountId);
    Optional<GoogleAuthorizationEntity> findBySubject(String subject);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from GoogleAuthorizationEntity g where g.subject = :subject")
    Optional<GoogleAuthorizationEntity> lockBySubject(String subject);
}
