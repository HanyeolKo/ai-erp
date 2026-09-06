package com.aierp.identity.api;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** Only identity provisioning creates production principals; UUID is the persisted account ID. */
public record ApplicationPrincipal(UUID userId, String email, boolean emailVerified)
        implements Principal, Serializable {
    public ApplicationPrincipal {
        java.util.Objects.requireNonNull(userId);
        java.util.Objects.requireNonNull(email);
    }
    @Override public String getName() { return userId.toString(); }
}
