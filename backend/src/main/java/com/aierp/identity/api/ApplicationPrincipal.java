package com.aierp.identity.api;

import java.security.Principal;
import java.util.UUID;

/** Principal supplied by the configured OIDC/session authentication adapter. */
public record ApplicationPrincipal(UUID userId, String email, boolean emailVerified) implements Principal {
    @Override public String getName() { return userId.toString(); }
}
