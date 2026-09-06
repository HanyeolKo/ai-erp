package com.aierp.identity;
import com.aierp.platform.web.OidcSettings;
import com.aierp.identity.api.IdentityProvisioning;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
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
    @Bean AuthenticationSuccessHandler googleSuccess(IdentityProvisioning identities) {
        return (request,response,authentication) -> {
            var oidc=(OidcUser)authentication.getPrincipal();
            try {
                var principal=identities.provision(oidc.getSubject(),oidc.getEmail(),Boolean.TRUE.equals(oidc.getEmailVerified()),oidc.getFullName());
                var token=UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(token);SecurityContextHolder.setContext(context);
                new HttpSessionSecurityContextRepository().saveContext(context,request,response);
                response.sendRedirect("/");
            } catch(RuntimeException failure) {
                SecurityContextHolder.clearContext();
                var session=request.getSession(false);if(session!=null) session.invalidate();
                response.sendRedirect("/?login=failed");
            }
        };
    }
}
