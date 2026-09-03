package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

class SessionCookieConfigurationTest {

    @Test
    void binds_secure_session_cookie_defaults_from_the_common_configuration() throws IOException {
        var cookie = bindCookie("application.yml");

        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.LAX);
        assertThat(cookie.getSecure()).isTrue();
    }

    @Test
    void binds_insecure_cookie_only_when_local_profile_configuration_overrides_it() throws IOException {
        var cookie = bindCookie("application.yml", "application-local.yml");

        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.LAX);
        assertThat(cookie.getSecure()).isFalse();
    }

    private Cookie bindCookie(String... resources) throws IOException {
        Map<String, Object> values = new HashMap<>();
        var loader = new YamlPropertySourceLoader();
        for (String resource : resources) {
            for (var source : loader.load(resource, new ClassPathResource(resource))) {
                ((MapPropertySource) source).getSource().forEach((key, value) -> values.put(
                        key,
                        value instanceof OriginTrackedValue trackedValue ? trackedValue.getValue() : value));
            }
        }
        var properties = new Binder(
                ConfigurationPropertySources.from(new MapPropertySource("test", values)),
                null,
                ApplicationConversionService.getSharedInstance())
                .bind("server", Bindable.of(ServerProperties.class))
                .orElseThrow(() -> new IllegalStateException("server properties were not bound"));
        return properties.getServlet().getSession().getCookie();
    }
}
