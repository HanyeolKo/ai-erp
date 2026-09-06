package com.aierp.platform.web;
import org.springframework.core.env.Environment;
public final class OidcSettings {
    private OidcSettings() {}
    public static boolean enabled(Environment environment) {
        return Boolean.parseBoolean(environment.getProperty("APP_OIDC_ENABLED","false"))
            && !environment.getProperty("GOOGLE_CLIENT_ID","").isBlank()
            && !environment.getProperty("GOOGLE_CLIENT_SECRET","").isBlank();
    }
}
