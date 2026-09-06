package com.aierp.group;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity,GroupMemberEntity.Key> {
    boolean existsByGroupIdAndUserAccountId(UUID groupId,UUID userAccountId);
}
