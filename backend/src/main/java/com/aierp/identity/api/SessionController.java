package com.aierp.identity.api;
import com.aierp.platform.web.OidcSettings;
import org.springframework.core.env.Environment;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController
public class SessionController {
    private final Environment environment;
    public SessionController(Environment environment) {this.environment=environment;}
    @GetMapping("/api/v1/csrf") public CsrfResponse csrf(CsrfToken csrf) {return new CsrfResponse(csrf.getHeaderName(),csrf.getToken());}
    @GetMapping("/api/v1/system/configuration") public ConfigurationResponse configuration() {
        return new ConfigurationResponse(OidcSettings.enabled(environment)?"READY":"CONFIGURATION_REQUIRED","CONFIGURATION_REQUIRED",
            OidcSettings.enabled(environment)?"/oauth2/authorization/google":null);
    }
    public record CsrfResponse(String headerName,String token) {}
    public record ConfigurationResponse(String login,String calendar,String loginUrl) {}
}
