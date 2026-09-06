package com.aierp.group.api;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.aierp.group.GroupMemberRepository;
@Service
public class GroupAccess {
    private final GroupMemberRepository members;
    public GroupAccess(GroupMemberRepository members) {this.members=members;}
    public void requireMember(UUID groupId,UUID userId) {
        if(!members.existsByGroupIdAndUserAccountId(groupId,userId)) throw new org.springframework.security.access.AccessDeniedException("GROUP_ACCESS_DENIED");
    }
}
