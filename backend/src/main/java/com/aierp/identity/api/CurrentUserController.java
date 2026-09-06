package com.aierp.identity.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only the principal supplied by the configured authentication provider. */
@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {

    @GetMapping
    public CurrentUserResponse currentUser(Authentication authentication) {
        return new CurrentUserResponse(
                authentication.getName(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    public record CurrentUserResponse(String id, java.util.List<String> authorities) {
    }
}
