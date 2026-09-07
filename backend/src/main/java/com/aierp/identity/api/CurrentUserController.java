package com.aierp.identity.api;

import com.aierp.identity.UserAccountRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only the principal supplied by the configured authentication provider. */
@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {
    private final UserAccountRepository users;
    @org.springframework.beans.factory.annotation.Autowired
    public CurrentUserController(UserAccountRepository users) { this.users = users; }

    @GetMapping
    public CurrentUserResponse currentUser(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof ApplicationPrincipal principal)) {
            throw new org.springframework.security.access.AccessDeniedException("Application principal required");
        }
        var account = users.findById(principal.userId());
        return new CurrentUserResponse(
                principal.userId().toString(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                account.map(a -> a.displayName).orElse("사용자"), account.map(a -> a.email).orElse(principal.email()));
    }

    public record CurrentUserResponse(String id, java.util.List<String> authorities, String displayName, String email) {
        public CurrentUserResponse(String id, java.util.List<String> authorities) { this(id,authorities,"사용자",null); }
    }
}
