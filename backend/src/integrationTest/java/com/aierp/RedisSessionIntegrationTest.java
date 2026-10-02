package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Import;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;

/** Live Spring Session Redis coverage. Requires AI_ERP_REDIS_TEST_URL pointing to loopback Redis 8.2.9. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(RedisSessionIntegrationTest.RedisSessionTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = NativeIntegrationRuntimeCleanupListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class RedisSessionIntegrationTest {
    private static final String REDIS_URL = requireLoopbackRedisUrl();
    private static final NativeIntegrationRuntime RUNTIME = NativeIntegrationRuntime.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", RUNTIME::postgresUrl);
        registry.add("spring.datasource.username", RUNTIME::postgresUsername);
        registry.add("spring.datasource.password", RUNTIME::postgresPassword);
        registry.add("spring.data.redis.url", () -> REDIS_URL);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/integration-migration");
    }

    @Autowired private SessionRepository<? extends Session> sessionRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private TestRestTemplate restTemplate;

    @Test
    void requiresRedis829PingAndApplicationReadiness() {
        assertThat(sessionRepository).isInstanceOf(RedisIndexedSessionRepository.class);
        try (var client = RedisClient.create(REDIS_URL); var connection = client.connect()) {
            assertThat(connection.sync().ping()).isEqualTo("PONG");
            String info = connection.sync().info("server");
            assertThat(java.util.Arrays.stream(info.split("\\R"))
                    .filter(line -> line.startsWith("redis_version:")).findFirst().orElse(""))
                    .isEqualTo("redis_version:8.2.9");
        }
        var readiness = restTemplate.exchange("/actuator/health/readiness", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() { });
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).containsOnlyKeys("status");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void roundTripsIndexesExpiresAndDeletesRedisBackedSession() throws InterruptedException {
        FindByIndexNameSessionRepository repository = (FindByIndexNameSessionRepository) sessionRepository;
        SessionRepository rawRepository = (SessionRepository) sessionRepository;
        String principal = "redis-session-" + UUID.randomUUID();
        Session session = (Session) repository.createSession();
        session.setMaxInactiveInterval(java.time.Duration.ofSeconds(3));
        var context = new SecurityContextImpl();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, "n/a"));
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        session.setAttribute("redis-integration-value", "round-trip");
        rawRepository.save(session);

        Session loaded = (Session) rawRepository.findById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat((String) loaded.getAttribute("redis-integration-value")).isEqualTo("round-trip");
        assertThat(repository.findByPrincipalName(principal)).containsKey(session.getId());
        Long ttl = redisTemplate.getExpire("spring:session:sessions:expires:" + session.getId());
        assertThat(ttl).isBetween(1L, 3L);

        long expiresAt = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (rawRepository.findById(session.getId()) != null && System.nanoTime() < expiresAt) {
            Thread.sleep(100);
        }
        assertThat(rawRepository.findById(session.getId())).isNull();
        assertThat(repository.findByPrincipalName(principal)).doesNotContainKey(session.getId());

        Session deletable = (Session) repository.createSession();
        deletable.setAttribute("redis-integration-value", "delete-me");
        rawRepository.save(deletable);
        rawRepository.deleteById(deletable.getId());
        assertThat(rawRepository.findById(deletable.getId())).isNull();
        assertThat(redisTemplate.hasKey("spring:session:sessions:expires:" + deletable.getId())).isFalse();
    }

    private static String requireLoopbackRedisUrl() {
        String value = System.getenv("AI_ERP_REDIS_TEST_URL");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("AI_ERP_REDIS_TEST_URL is required for redisIntegrationTest");
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"redis".equals(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"))
                    || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getUserInfo() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())) {
                throw new IllegalArgumentException("Redis test URL must be a loopback redis:// URL with an explicit port");
            }
            return value;
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Invalid AI_ERP_REDIS_TEST_URL: expected an explicit loopback Redis URL", invalid);
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableRedisIndexedHttpSession
    static class RedisSessionTestConfiguration { }
}
