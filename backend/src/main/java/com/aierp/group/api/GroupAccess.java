package com.aierp.group.api;
import com.aierp.group.*;
import com.aierp.platform.web.ReadLimits;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupAccess {
    private final GroupMemberRepository members;
    private final ErpGroupRepository groups;
    public GroupAccess(GroupMemberRepository members,ErpGroupRepository groups) {this.members=members;this.groups=groups;}
    public void requireMember(UUID groupId,UUID userId) {
        if(!members.existsByGroupIdAndUserAccountId(groupId,userId)) throw new AccessDeniedException("GROUP_ACCESS_DENIED");
    }
    public List<CreationOption> creationOptions(UUID userId, Integer page, Integer limit) {
        var memberships = members.findByUserAccountId(userId, ReadLimits.page(page, limit, Sort.by("groupId")));
        if (memberships.isEmpty()) return List.of();
        var byId = groups.findAllById(memberships.stream().map(m -> m.groupId).toList()).stream()
            .collect(Collectors.toMap(g -> g.id, Function.identity(), (left, right) -> left));
        return memberships.stream().map(m -> {
            var group = byId.get(m.groupId);
            if (group == null) return null;
            boolean canCreate = canCreate(m.role);
            String reason = canCreate ? null : (m.role == null ? "GROUP_ROLE_NOT_CONFIGURED" : "GROUP_ROLE_REQUIRED");
            return new CreationOption(group.id, group.name, canCreate, reason);
        }).filter(Objects::nonNull).toList();
    }
    /** The lock lasts through project and creator-membership persistence in the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireProjectCreator(UUID groupId, UUID userId) {
        var member = members.findForProjectCreation(groupId, userId).orElseThrow(() -> new AccessDeniedException("GROUP_ACCESS_DENIED"));
        if (!canCreate(member.role) || !groups.existsById(groupId))
            throw new AccessDeniedException("GROUP_PROJECT_CREATION_DENIED");
    }
    private static boolean canCreate(GroupRole role) { return role == GroupRole.OWNER || role == GroupRole.ADMIN; }
    public record CreationOption(UUID id,String name,boolean canCreate,String reason) {}
}
