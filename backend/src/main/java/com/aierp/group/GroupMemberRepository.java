package com.aierp.group;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity,GroupMemberEntity.Key> {
    boolean existsByGroupIdAndUserAccountId(UUID groupId,UUID userAccountId);
    List<GroupMemberEntity> findByUserAccountId(UUID userAccountId,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from GroupMemberEntity m where m.groupId = :groupId and m.userAccountId = :userId")
    Optional<GroupMemberEntity> findForProjectCreation(UUID groupId,UUID userId);
}
