package com.aierp.identity.api;

import com.aierp.identity.GoogleIdentityRepository;
import com.aierp.platform.web.OidcSettings;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/google")
public class GoogleConnectionController {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final GoogleAuthorizationService grants;
    private final GoogleIdentityRepository identities;
    private final Environment environment;
    public GoogleConnectionController(GoogleAuthorizationService grants,GoogleIdentityRepository identities,Environment environment){this.grants=grants;this.identities=identities;this.environment=environment;}

    @GetMapping("/connection")
    public ResponseEntity<ConnectionResponse> connection(Authentication auth) {
        var user=principal(auth); var state=grants.status(user.userId());
        boolean configured=Boolean.parseBoolean(environment.getProperty("APP_GOOGLE_WORKSPACE_ENABLED","false")) && OidcSettings.enabled(environment) && grants.configured();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ConnectionResponse(!configured,state.accountEmail(),new ServiceStatus(state.status(GoogleAccess.Feature.DRIVE)),new ServiceStatus(state.status(GoogleAccess.Feature.GMAIL)),new ServiceStatus(state.status(GoogleAccess.Feature.CALENDAR))));
    }

    @PostMapping("/connect")
    public ResponseEntity<AuthorizationResponse> connect(@RequestBody ConnectRequest request,Authentication auth,HttpSession session) {
        var user=principal(auth); if(request==null||request.feature()==null)throw new IllegalArgumentException("FEATURE_REQUIRED");
        if(!Boolean.parseBoolean(environment.getProperty("APP_GOOGLE_WORKSPACE_ENABLED","false"))||!OidcSettings.enabled(environment)||!grants.configured())return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new AuthorizationResponse(null,"configuration_required"));
        var identity=identities.findByUserAccountId(user.userId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("GOOGLE_IDENTITY_REQUIRED"));
        byte[] bytes=new byte[24];RANDOM.nextBytes(bytes);
        String intent=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        @SuppressWarnings("unchecked") var pending=(Map<String,Map<String,String>>)session.getAttribute("google.connect.pending");
        if(pending==null)pending=new LinkedHashMap<>();
        pending.entrySet().removeIf(entry -> Long.parseLong(entry.getValue().getOrDefault("created","0")) < System.currentTimeMillis()-600_000L);
        if(pending.size()>=8)pending.remove(pending.keySet().iterator().next());
        var context=new LinkedHashMap<String,String>();context.put("feature",request.feature().name());context.put("user",user.userId().toString());context.put("subject",identity.subject);context.put("generation",Long.toString(grants.generation(user.userId())));context.put("created",Long.toString(System.currentTimeMillis()));context.put("email",user.email());
        pending.put(intent,context);session.setAttribute("google.connect.pending",pending);session.setAttribute("google.connect.activeIntent",intent);
        session.setAttribute("google.connect.restoreUser",user.userId().toString());
        session.setAttribute("google.connect.restoreEmail",user.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AuthorizationResponse("/oauth2/authorization/google","pending"));
    }

    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnect(Authentication auth){grants.disconnect(principal(auth).userId());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    private static ApplicationPrincipal principal(Authentication a){if(a!=null&&a.getPrincipal() instanceof ApplicationPrincipal p)return p;throw new org.springframework.security.access.AccessDeniedException("APPLICATION_PRINCIPAL_REQUIRED");}
    public record ConnectRequest(GoogleAccess.Feature feature){}
    public record AuthorizationResponse(String authorizationUrl,String outcome){}
    public record ServiceStatus(GoogleAccess.Status status){}
    public record ConnectionResponse(boolean configurationRequired,String accountEmail,ServiceStatus drive,ServiceStatus gmail,ServiceStatus calendar){}
}
