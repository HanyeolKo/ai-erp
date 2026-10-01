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
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = NativeIntegrationRuntimeCleanupListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class FoundationIntegrationTest {
    private static final NativeIntegrationRuntime RUNTIME = NativeIntegrationRuntime.start(true);



    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", RUNTIME::postgresUrl);
        registry.add("spring.datasource.username", RUNTIME::postgresUsername);
        registry.add("spring.datasource.password", RUNTIME::postgresPassword);
        registry.add("spring.data.redis.url", RUNTIME::redisUrl);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/integration-migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void prepareQuerydslProbeTable() {
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
        assertThat(redisTemplate.getConnectionFactory().getConnection().ping()).isEqualTo("PONG");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void persists_and_reloads_a_spring_session_through_redis() {
        SessionRepository repository = (SessionRepository) sessionRepository;
        Session session = (Session) repository.createSession();
        session.setAttribute("native-runtime-session", "survives-round-trip");
        repository.save(session);

        Session loaded = (Session) repository.findById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat((String) loaded.getAttribute("native-runtime-session")).isEqualTo("survives-round-trip");
    }

    @Test
    @Transactional
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
