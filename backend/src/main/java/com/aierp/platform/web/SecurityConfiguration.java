package com.aierp.platform.web;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.csrf.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clients,ObjectProvider<AuthenticationSuccessHandler> success,ObjectProvider<AuthenticationFailureHandler> failure,ObjectProvider<OAuth2AuthorizationRequestResolver> resolver) throws Exception {
        http.addFilterBefore(new TraceFilter(),org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/","/index.html","/assets/**","/favicon.ico","/api/v1/system/info","/api/v1/system/configuration",
                    "/api/v1/csrf","/actuator/health/liveness","/actuator/health/readiness","/oauth2/authorization/**","/login/oauth2/code/**").permitAll()
                .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
            .csrf(csrf->csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .exceptionHandling(errors->errors.authenticationEntryPoint((req,res,ex)->writeProblem(req,res,401,"UNAUTHENTICATED"))
                .accessDeniedHandler((req,res,ex)->writeProblem(req,res,403,"FORBIDDEN")))
            .logout(logout->logout.logoutUrl("/api/v1/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)));
        if(clients.getIfAvailable()!=null) {
            var configuredFailureHandler=failure.getIfAvailable();
            org.springframework.security.web.authentication.AuthenticationFailureHandler failureHandler=configuredFailureHandler==null?(req,res,ex)->res.sendRedirect("/?login=failed"):configuredFailureHandler;
            var requestResolver=resolver.getIfAvailable();
            http.oauth2Login(oidc->oidc.clientRegistrationRepository(clients.getObject()).authorizationEndpoint(endpoint->endpoint.authorizationRequestResolver(requestResolver))
                .successHandler(success.getObject()).failureHandler(failureHandler));
        }
        return http.build();
    }
    private static void writeProblem(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,int status,String code) throws java.io.IOException {
        response.setStatus(status);response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"code\":\"%s\",\"traceId\":\"%s\",\"fieldErrors\":[]}".formatted(code,TraceFilter.id(request)));
    }
    @Bean UserDetailsService noUsersConfigured() {return username->{throw new UsernameNotFoundException("No local login");};}
}
