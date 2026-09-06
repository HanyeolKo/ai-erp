package com.aierp.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import java.util.UUID;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers(
                                "/api/v1/system/info",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness")
                        .permitAll()
                        .requestMatchers("/api/v1/me")
                        .authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(response, HttpStatus.FORBIDDEN, "FORBIDDEN")))
                .build();
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status, String code) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"code\":\"%s\",\"traceId\":\"%s\",\"fieldErrors\":[]}"
                .formatted(code, UUID.randomUUID()));
    }

    @Bean
    UserDetailsService noUsersConfigured() {
        return username -> {
            throw new UsernameNotFoundException("No authentication provider is configured during foundation phase");
        };
    }
}
