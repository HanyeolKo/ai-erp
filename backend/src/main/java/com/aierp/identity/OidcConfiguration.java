package com.aierp.identity;
import com.aierp.platform.web.OidcSettings;
import com.aierp.identity.api.IdentityProvisioning;
import com.aierp.identity.api.ApplicationPrincipal;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.GoogleAuthorizationService;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration(proxyBeanMethods=false)
@Conditional(OidcConfiguration.Enabled.class)
public class OidcConfiguration {
    static class Enabled implements Condition {
        @Override public boolean matches(ConditionContext context,org.springframework.core.type.AnnotatedTypeMetadata metadata) {return OidcSettings.enabled(context.getEnvironment());}
    }
    @Bean ClientRegistrationRepository clients(Environment env) {
        var registration=ClientRegistration.withRegistrationId("google")
            .clientId(env.getRequiredProperty("GOOGLE_CLIENT_ID")).clientSecret(env.getRequiredProperty("GOOGLE_CLIENT_SECRET"))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid","email","profile")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .issuerUri("https://accounts.google.com").tokenUri("https://oauth2.googleapis.com/token").jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo").userNameAttributeName("sub").clientName("Google").build();
        return new InMemoryClientRegistrationRepository(registration);
    }
    @Bean AuthenticationSuccessHandler googleSuccess(IdentityProvisioning identities, GoogleAuthorizationService grants, GoogleIdentityRepository googleIdentities, org.springframework.beans.factory.ObjectProvider<OAuth2AuthorizedClientService> authorizedClients) {
        return (request,response,authentication) -> {
            var oidc=(OidcUser)authentication.getPrincipal();
            var session=request.getSession(false);
            var flow=session==null?null:consumeFlow(session,request.getParameter("state"));
            if(flow==null) {
                if(authorizedClients.getIfAvailable()!=null) authorizedClients.getIfAvailable().removeAuthorizedClient("google",oidc.getSubject());
                restoreSnapshotOrClear(request,response,session);
                response.sendRedirect("/?login=failed");
                return;
            }
            if("CONNECT".equals(flow.get("kind"))) { handleConnection(request,response,oidc,grants,googleIdentities,authorizedClients.getIfAvailable(),session,flow); return; }
            if("true".equals(flow.get("_expired"))) {
                if(authorizedClients.getIfAvailable()!=null) authorizedClients.getIfAvailable().removeAuthorizedClient("google",oidc.getSubject());
                restoreSnapshotOrClear(request,response,session);
                response.sendRedirect("/?login=failed");
                return;
            }
            if(!"LOGIN".equals(flow.get("kind"))) {
                if(authorizedClients.getIfAvailable()!=null) authorizedClients.getIfAvailable().removeAuthorizedClient("google",oidc.getSubject());
                restoreSnapshotOrClear(request,response,session);
                response.sendRedirect("/?login=failed");
                return;
            }
            try {
                var principal=identities.provision(oidc.getSubject(),oidc.getEmail(),Boolean.TRUE.equals(oidc.getEmailVerified()),oidc.getFullName());
                var token=UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(token);SecurityContextHolder.setContext(context);
                new HttpSessionSecurityContextRepository().saveContext(context,request,response);
                var temporaryClient=authorizedClients.getIfAvailable();
                if(temporaryClient!=null) temporaryClient.removeAuthorizedClient("google",oidc.getSubject());
                response.sendRedirect("/");
            } catch(RuntimeException failure) {
                SecurityContextHolder.clearContext();
                var failureSession=request.getSession(false);if(failureSession!=null) failureSession.invalidate();
                response.sendRedirect("/?login=failed");
            }
        };
    }
    @Bean AuthenticationFailureHandler googleFailure() {
        return (request,response,exception) -> {
            var session=request.getSession(false); var flow=session==null?null:consumeFlow(session,request.getParameter("state"));
            if(flow!=null && "CONNECT".equals(flow.get("kind"))) {
                var p=new ApplicationPrincipal(UUID.fromString(flow.get("user")),flow.get("email"),true); restore(request,response,p);
                response.sendRedirect("/#/account/google?outcome=denied");
            } else {
                restoreSnapshotOrClear(request,response,session);
                response.sendRedirect("/?login=failed");
            }
        };
    }
    @Bean OAuth2AuthorizationRequestResolver googleAuthorizationRequestResolver(ClientRegistrationRepository clients) {
        var delegate=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) { return customize(delegate.resolve(request),request); }
            @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request,String registrationId) { return customize(delegate.resolve(request,registrationId),request); }
            private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest original,HttpServletRequest request) {
                if(original==null)return original;
                var session=request.getSession();
                @SuppressWarnings("unchecked") var pending=(Map<String,Map<String,String>>)session.getAttribute("google.connect.pending");
                String active=(String)session.getAttribute("google.connect.activeIntent");
                Map<String,String> intent=active==null||pending==null?null:pending.remove(active);
                if (pending != null) session.setAttribute("google.connect.pending", pending);
                if(intent==null) {
                    session.removeAttribute("google.connect.activeIntent");
                    var login=new LinkedHashMap<String,String>();
                    boolean trusted=SecurityContextHolder.getContext().getAuthentication()!=null
                        && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof ApplicationPrincipal;
                    login.put("kind",trusted?"INVALID":"LOGIN");
                    login.put("created",Long.toString(System.currentTimeMillis()));
                    if (!trusted) { session.removeAttribute("google.connect.restoreUser"); session.removeAttribute("google.connect.restoreEmail"); }
                    putFlow(session,original.getState(),login);return original;
                }
                session.removeAttribute("google.connect.activeIntent");
                var context=new LinkedHashMap<>(intent);context.put("kind","CONNECT");putFlow(session,original.getState(),context);
                var feature=GoogleAccess.Feature.valueOf(intent.get("feature"));
                var scopes=new java.util.LinkedHashSet<>(original.getScopes());
                switch(feature){case DRIVE->scopes.add("https://www.googleapis.com/auth/drive.metadata.readonly");case GMAIL->{scopes.add("https://www.googleapis.com/auth/gmail.readonly");scopes.add("https://www.googleapis.com/auth/gmail.send");}case CALENDAR->{scopes.add("https://www.googleapis.com/auth/calendar.calendarlist.readonly");scopes.add("https://www.googleapis.com/auth/calendar.events");}}
                var params=new java.util.LinkedHashMap<>(original.getAdditionalParameters());params.put("access_type","offline");params.put("include_granted_scopes","true");params.put("prompt","consent");
                return OAuth2AuthorizationRequest.from(original).scopes(scopes).additionalParameters(params).build();
            }
        };
    }

    private static void handleConnection(HttpServletRequest request,HttpServletResponse response,OidcUser oidc,GoogleAuthorizationService grants,GoogleIdentityRepository identities,OAuth2AuthorizedClientService clients,HttpSession session,Map<String,String> flow) throws IOException {
        ApplicationPrincipal original=new ApplicationPrincipal(UUID.fromString(flow.get("user")),flow.get("email"),true);
        String expectedSubject=flow.get("subject");
        String expectedUser=flow.get("user");
        Long expectedGeneration=Long.valueOf(flow.get("generation"));
        Long created=Long.valueOf(flow.get("created"));
        String outcome="denied";
        try {
            if(!original.userId().toString().equals(expectedUser) || !Objects.equals(expectedSubject,oidc.getSubject()) || !Boolean.TRUE.equals(oidc.getEmailVerified()) || created < System.currentTimeMillis()-600_000L || grants.generation(original.userId())!=expectedGeneration) throw new IllegalStateException("CONNECT_INTENT_INVALID");
            OAuth2AuthorizedClient client=clients==null?null:clients.loadAuthorizedClient("google",oidc.getSubject());
            if(client==null) throw new IllegalStateException("AUTHORIZED_CLIENT_MISSING");
            var scopes=client.getAccessToken().getScopes();
            var feature=GoogleAccess.Feature.valueOf(flow.get("feature"));
            if(!required(feature,scopes)) throw new IllegalStateException("GOOGLE_SCOPES_INSUFFICIENT");
            var refresh=client.getRefreshToken();
            grants.connect(original.userId(),oidc.getSubject(),oidc.getEmail(),client.getAccessToken().getTokenValue(),refresh==null?null:refresh.getTokenValue(),scopes,client.getAccessToken().getExpiresAt(),expectedGeneration);
            outcome="connected";
        } catch(RuntimeException ignored) {
            // Keep the existing ERP login and vault intact for every connect callback failure.
        } finally {
            if(clients!=null) clients.removeAuthorizedClient("google",oidc.getSubject());
            if(original!=null) restore(request,response,original);
            response.sendRedirect("/#/account/google?outcome="+outcome);
        }
    }
    private static boolean required(GoogleAccess.Feature f,java.util.Set<String> scopes) {
        if(scopes==null)return false;
        return switch(f) {
            case DRIVE -> scopes.contains("https://www.googleapis.com/auth/drive.metadata.readonly")||scopes.contains("https://www.googleapis.com/auth/drive")||scopes.contains("https://www.googleapis.com/auth/drive.readonly");
            case GMAIL -> (scopes.contains("https://www.googleapis.com/auth/gmail.readonly")||scopes.contains("https://www.googleapis.com/auth/gmail.modify")||scopes.contains("https://mail.google.com/")) && (scopes.contains("https://www.googleapis.com/auth/gmail.send")||scopes.contains("https://www.googleapis.com/auth/gmail.modify")||scopes.contains("https://mail.google.com/"));
            case CALENDAR -> (scopes.contains("https://www.googleapis.com/auth/calendar.calendarlist.readonly")&&scopes.contains("https://www.googleapis.com/auth/calendar.events"))||scopes.contains("https://www.googleapis.com/auth/calendar");
        };
    }
    private static void restore(HttpServletRequest request,HttpServletResponse response,ApplicationPrincipal principal) {
        var token=UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(token);SecurityContextHolder.setContext(context);new HttpSessionSecurityContextRepository().saveContext(context,request,response);
    }
    private static void restoreSnapshotOrClear(HttpServletRequest request,HttpServletResponse response,HttpSession session) {
        try {
            if (session != null) {
                String id=(String)session.getAttribute("google.connect.restoreUser");
                String email=(String)session.getAttribute("google.connect.restoreEmail");
                if (id != null && email != null) { restore(request,response,new ApplicationPrincipal(UUID.fromString(id),email,true)); return; }
            }
        } catch (RuntimeException ignored) { }
        // Spring's OAuth2 filter may have saved its OIDC authentication in the
        // session before this handler learns that the callback state is unknown
        // or expired. Persist the empty context as well as clearing the holder,
        // otherwise that saved authentication is restored on the next request.
        var empty=SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        new HttpSessionSecurityContextRepository().saveContext(empty,request,response);
    }
    @SuppressWarnings("unchecked")
    private static void putFlow(HttpSession session,String state,Map<String,String> flow) {
        synchronized(session) {
            var flows=(Map<String,Map<String,String>>)session.getAttribute("google.oauth.flows");
            if(flows==null)flows=new LinkedHashMap<>();
            flows.entrySet().removeIf(e->Long.parseLong(e.getValue().getOrDefault("created","0"))<System.currentTimeMillis()-600_000L);
            if(flows.size()>=8)flows.remove(flows.keySet().iterator().next());
            flows.put(state,new LinkedHashMap<>(flow));session.setAttribute("google.oauth.flows",flows);
        }
    }
    @SuppressWarnings("unchecked")
    private static Map<String,String> consumeFlow(HttpSession session,String state) {
        if(state==null||state.isBlank())return null;
        synchronized(session) {
            var flows=(Map<String,Map<String,String>>)session.getAttribute("google.oauth.flows");
            if(flows==null)return null;
            var flow=flows.remove(state);
            session.setAttribute("google.oauth.flows",flows);
            if (flow != null) {
                try {
                    if (Long.parseLong(flow.getOrDefault("created", "0")) < System.currentTimeMillis()-600_000L) {
                        flow.put("_expired", "true");
                    }
                } catch (RuntimeException expiredOrMalformed) {
                    return null;
                }
            }
            return flow;
        }
    }
}
