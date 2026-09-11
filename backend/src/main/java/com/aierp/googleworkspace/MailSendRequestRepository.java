package com.aierp.googleworkspace;
import java.util.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
public interface MailSendRequestRepository extends JpaRepository<MailSendRequestEntity,MailSendRequestEntity.Id> {
    /**
     * Claims a request using the database primary key as the ownership barrier.
     * The caller must run this in a short transaction and inspect the row count.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO google_workspace.mail_send_request "
            + "(user_account_id, request_id, payload_hash, status, credential_generation, created_at, updated_at) "
            + "VALUES (:userAccountId, :requestId, :payloadHash, 'SENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (user_account_id, request_id) DO NOTHING", nativeQuery = true)
    int insertClaim(@Param("userAccountId") UUID userAccountId,
                    @Param("requestId") UUID requestId,
                    @Param("payloadHash") String payloadHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MailSendRequestEntity s where s.id = :id")
    Optional<MailSendRequestEntity> lockById(@Param("id") MailSendRequestEntity.Id id);
}
