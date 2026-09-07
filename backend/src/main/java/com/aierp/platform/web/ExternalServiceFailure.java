package com.aierp.platform.web;

import java.util.Set;

/** Safe provider failure category. Provider response bodies and credential values never escape this type. */
public class ExternalServiceFailure extends RuntimeException {
    private static final Set<String> SAFE_CODES = Set.of(
        "EXTERNAL_SERVICE_FAILURE", "GOOGLE_CONNECTED", "GOOGLE_NOT_CONNECTED",
        "GOOGLE_PERMISSION_REQUIRED", "GOOGLE_REAUTH_REQUIRED", "REFRESH_UNAVAILABLE",
        "REFRESH_STALE", "REFRESH_IN_PROGRESS", "CONNECT_STALE", "ACCOUNT_MISMATCH",
        "GOOGLE_API_DISABLED", "GOOGLE_QUOTA_EXCEEDED", "GOOGLE_PROVIDER_UNAVAILABLE",
        "GOOGLE_INVALID_RESPONSE", "GOOGLE_RESPONSE_TOO_LARGE", "GOOGLE_SCOPE_REGRESSION"
    );
    public enum Category { CONFIGURATION_REQUIRED, REAUTH_REQUIRED, PERMISSION_REQUIRED, TEMPORARY, DEFINITIVE }
    private final Category category;
    public ExternalServiceFailure(Category category,String safeCode){super(SAFE_CODES.contains(safeCode) ? safeCode : "EXTERNAL_SERVICE_FAILURE");this.category=category;}
    public Category category(){return category;}
    public String safeCode(){return getMessage();}
}
