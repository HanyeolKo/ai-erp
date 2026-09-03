package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = false)
class FoundationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2.9"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void prepareQuerydslProbeTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS platform.querydsl_probe (id BIGSERIAL PRIMARY KEY, label VARCHAR(255) NOT NULL)");
        jdbcTemplate.execute("TRUNCATE TABLE platform.querydsl_probe");
    }

    @Test
    void applies_foundation_schema_migration_and_reports_readiness_without_details() {
        var schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN "
                        + "('platform', 'identity', 'group', 'project', 'schedule', 'notification', 'calendar_integration', 'audit')",
                String.class);

        var readiness = restTemplate.exchange("/actuator/health/readiness", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() { });

        assertThat(schemas).containsExactlyInAnyOrder(
                "platform", "identity", "group", "project", "schedule", "notification", "calendar_integration", "audit");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).containsOnlyKeys("status");
    }

    @Test
    void generates_q_type_and_executes_hql_against_the_test_only_entity() {
        entityManager.persist(new QuerydslProbe("foundation"));
        entityManager.flush();

        var hqlResult = entityManager.createQuery(
                        "select probe from QuerydslProbe probe where probe.label = :label", QuerydslProbe.class)
                .setParameter("label", "foundation")
                .getSingleResult();
        var qTypeResult = new JPAQuery<QuerydslProbe>(entityManager)
                .select(QQuerydslProbe.querydslProbe)
                .from(QQuerydslProbe.querydslProbe)
                .where(QQuerydslProbe.querydslProbe.label.eq("foundation"))
                .fetchOne();

        assertThat(hqlResult.label()).isEqualTo("foundation");
        assertThat(qTypeResult).isNotNull();
        assertThat(qTypeResult.label()).isEqualTo("foundation");
    }
}
