package com.aierp.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/system/info",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness")
                        .permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    UserDetailsService noUsersConfigured() {
        return username -> {
            throw new UsernameNotFoundException("No authentication provider is configured during foundation phase");
        };
    }
}
