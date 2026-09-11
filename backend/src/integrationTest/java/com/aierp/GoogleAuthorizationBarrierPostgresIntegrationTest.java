package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;

import com.aierp.identity.GoogleAuthorizationRepository;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.GoogleAuthorizationService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Validates the real account/grant lock barrier with PostgreSQL row locks. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class GoogleAuthorizationBarrierPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2.9"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/integration-migration");
        registry.add("APP_GOOGLE_WORKSPACE_ENABLED", () -> "true");
        registry.add("APP_OIDC_ENABLED", () -> "true");
        registry.add("GOOGLE_CLIENT_ID", () -> "integration-client");
        registry.add("GOOGLE_CLIENT_SECRET", () -> "integration-secret");
        registry.add("GOOGLE_TOKEN_ENCRYPTION_KEY", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Autowired GoogleAuthorizationService grants;
    @Autowired GoogleAuthorizationRepository grantRepository;
    @Autowired JdbcTemplate jdbc;

    private UUID user;

    @BeforeEach
    void prepare() {
        user = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",
                user, "barrier-%s@example.test".formatted(user), "Barrier Test");
        grants.connect(user, "google-subject-%s".formatted(user), "barrier@example.test", "access", "refresh",
                Set.of("https://www.googleapis.com/auth/drive.metadata.readonly"), Instant.now().plusSeconds(3600));
    }

    @Test
    void barrierWaitsForAccountLockAndRejectsGenerationAfterDisconnectStyleUpdate() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SELECT id FROM identity.user_account WHERE id='" + user + "' FOR UPDATE");
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Boolean> barrier = pool.submit(() -> grants.isCurrentForCommit(user, 1));
                try {
                    barrier.get(250, TimeUnit.MILLISECONDS);
                    throw new AssertionError("grant barrier did not wait for the account lock");
                } catch (TimeoutException expected) {
                    // The barrier is blocked behind the held account row.
                }
                statement.execute("UPDATE identity.google_authorization SET generation=2,status='NOT_CONNECTED',updated_at=CURRENT_TIMESTAMP WHERE user_account_id='" + user + "'");
                connection.commit();
                assertThat(barrier.get(10, TimeUnit.SECONDS)).isFalse();
            } finally {
                connection.rollback();
            }
        }
        assertThat(grantRepository.findByUserAccountId(user)).isPresent();
    }
}
