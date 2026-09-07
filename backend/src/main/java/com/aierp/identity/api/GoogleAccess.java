package com.aierp.identity.api;

import java.util.Map;
import java.util.UUID;

/** Named interface for server-side Google capabilities. Tokens never cross a controller boundary. */
public interface GoogleAccess {
    enum Feature { DRIVE, GMAIL, CALENDAR }
    enum Status { CONNECTED, NOT_CONNECTED, PERMISSION_REQUIRED, REAUTH_REQUIRED }

    record Credential(String accessToken, long generation) {
        public Credential {
            if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("ACCESS_TOKEN_REQUIRED");
        }
        @Override public String toString() { return "Credential[accessToken=[REDACTED], generation=" + generation + "]"; }
    }

    record Connection(String accountEmail, Map<Feature, Status> services) {
        public Connection { services = Map.copyOf(services); }
        public Status status(Feature feature) { return services.getOrDefault(feature, Status.NOT_CONNECTED); }
    }

    class GoogleAccessException extends com.aierp.platform.web.ExternalServiceFailure {
        public final Status status;
        public GoogleAccessException(Status status) { super(category(status),"GOOGLE_" + status.name()); this.status=status; }
        public GoogleAccessException(String code) { this(code,com.aierp.platform.web.ExternalServiceFailure.Category.TEMPORARY); }
        public GoogleAccessException(String code,com.aierp.platform.web.ExternalServiceFailure.Category category) { super(category,code); this.status=status(category); }
        public Status status() { return status; }
        private static Status status(com.aierp.platform.web.ExternalServiceFailure.Category category) {
            return switch (category) {
                case REAUTH_REQUIRED -> Status.REAUTH_REQUIRED;
                case PERMISSION_REQUIRED -> Status.PERMISSION_REQUIRED;
                case CONFIGURATION_REQUIRED -> Status.NOT_CONNECTED;
                case CONNECTED, TEMPORARY, DEFINITIVE -> null;
            };
        }
        private static com.aierp.platform.web.ExternalServiceFailure.Category category(Status status) { return switch(status){case REAUTH_REQUIRED->com.aierp.platform.web.ExternalServiceFailure.Category.REAUTH_REQUIRED;case PERMISSION_REQUIRED->com.aierp.platform.web.ExternalServiceFailure.Category.PERMISSION_REQUIRED;case NOT_CONNECTED->com.aierp.platform.web.ExternalServiceFailure.Category.CONFIGURATION_REQUIRED;case CONNECTED->com.aierp.platform.web.ExternalServiceFailure.Category.DEFINITIVE;}; }
    }

    Connection status(UUID userId);
    Credential credential(UUID userId, Feature feature);
    boolean isCurrent(UUID userId, long generation);
    boolean configured();
}
