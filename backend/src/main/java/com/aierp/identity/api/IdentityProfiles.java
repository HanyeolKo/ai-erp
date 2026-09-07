package com.aierp.identity.api;

import com.aierp.identity.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Bounded internal profile reads for modules that already authorized an account relationship. */
@Service
public class IdentityProfiles {
    private final UserAccountRepository users;
    public IdentityProfiles(UserAccountRepository users) { this.users = users; }
    public Map<UUID, PublicProfile> find(Collection<UUID> ids) {
        var distinct = ids == null ? Set.<UUID>of() : ids.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (distinct.size() > 200) throw new IllegalArgumentException("PROFILE_READ_LIMIT_EXCEEDED");
        if (distinct.isEmpty()) return Map.of();
        var result = new HashMap<UUID, PublicProfile>();
        users.findAllById(distinct).forEach(u -> result.put(u.id, new PublicProfile(u.id, u.displayName, u.email)));
        return Map.copyOf(result);
    }
    public record PublicProfile(UUID id, String displayName, String email) { }
}
