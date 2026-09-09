package com.aierp;

import com.aierp.googleworkspace.DriveService;
import com.aierp.googleworkspace.GoogleHttpClient;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.GoogleAuthorizationService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** PostgreSQL concurrency coverage for the project-lock/save barrier. */
@SpringBootTest
@Testcontainers
class DrivePersistenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2.9"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/integration-migration");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired DriveService drive;
    // Replace the concrete identity service so connection endpoints do not initialize
    // real OAuth configuration while Drive receives the same mocked GoogleAccess bean.
    @MockitoBean GoogleAuthorizationService access;
    @MockitoBean GoogleHttpClient http;
    UUID user;
    UUID project;

    @BeforeEach
    void fixture() {
        user = UUID.randomUUID();
        project = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.user_account(id,email,display_name,email_verified_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",
                user, user + "@example.test", "Drive User");
        jdbc.update("INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)", project, UUID.randomUUID(), "Drive Project");
        jdbc.update("INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MEMBER')", project, user);
        when(access.credential(user, GoogleAccess.Feature.DRIVE)).thenReturn(new GoogleAccess.Credential("access", 4));
        when(access.isCurrent(user, 4)).thenReturn(true);
        when(access.isCurrentForCommit(user, 4)).thenReturn(true);
    }

    @Test
    void demotionBeforeLockedSaveRejectsTheAttachmentAndLeavesNoRow() throws Exception {
        var providerStarted = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        when(http.execute(eq("GET"), any(), eq("access"), isNull())).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertThat(releaseProvider.await(10, TimeUnit.SECONDS)).isTrue();
            return new GoogleHttpClient.Response(200, "{\"id\":\"file_1\",\"name\":\"a\",\"mimeType\":\"text/plain\"}");
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> drive.attach(project, user, "file_1"));
            assertThat(providerStarted.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?", project, user);
            releaseProvider.countDown();
            assertThatThrownBy(() -> result.get(20, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_workspace.drive_reference WHERE project_id=?", Integer.class, project)).isZero();
    }

    @Test
    void duplicateAttachmentReplaysOneCommittedReference() {
        when(http.execute(eq("GET"), any(), eq("access"), isNull()))
                .thenReturn(new GoogleHttpClient.Response(200, "{\"id\":\"file_1\",\"name\":\"a\",\"mimeType\":\"text/plain\"}"));
        var first = drive.attach(project, user, "file_1");
        var replay = drive.attach(project, user, "file_1");

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_workspace.drive_reference WHERE project_id=? AND file_id=?", Integer.class, project, "file_1")).isEqualTo(1);
        verify(http, times(1)).execute(eq("GET"), any(), eq("access"), isNull());
    }

    @Test
    void projectLockKeepsDemotionBehindTheAtomicSave() throws Exception {
        when(http.execute(eq("GET"), any(), eq("access"), isNull()))
                .thenReturn(new GoogleHttpClient.Response(200, "{\"id\":\"file_2\",\"name\":\"b\",\"mimeType\":\"text/plain\"}"));
        var barrierReached = new CountDownLatch(1);
        var releaseSave = new CountDownLatch(1);
        doAnswer(invocation -> {
            barrierReached.countDown();
            assertThat(releaseSave.await(10, TimeUnit.SECONDS)).isTrue();
            return true;
        }).when(access).isCurrentForCommit(user, 4);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var attach = executor.submit(() -> drive.attach(project, user, "file_2"));
            assertThat(barrierReached.await(10, TimeUnit.SECONDS)).isTrue();
            var demotion = executor.submit(this::demoteAfterProjectLock);
            Thread.sleep(200);
            assertThat(demotion.isDone()).isFalse();
            releaseSave.countDown();
            assertThat(attach.get(20, TimeUnit.SECONDS).fileId()).isEqualTo("file_2");
            assertThat(demotion.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT role FROM project.project_member WHERE project_id=? AND user_account_id=?", String.class, project, user)).isEqualTo("VIEWER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_workspace.drive_reference WHERE project_id=? AND file_id=?", Integer.class, project, "file_2")).isEqualTo(1);
    }

    /** Uses the same stable project-row lock protocol as a real membership mutation. */
    private int demoteAfterProjectLock() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT id FROM project.project WHERE id=? FOR UPDATE")) {
                    lock.setObject(1, project);
                    try (var ignored = lock.executeQuery()) { }
                }
                int updated;
                try (var update = connection.prepareStatement(
                        "UPDATE project.project_member SET role='VIEWER' WHERE project_id=? AND user_account_id=?")) {
                    update.setObject(1, project);
                    update.setObject(2, user);
                    updated = update.executeUpdate();
                }
                connection.commit();
                return updated;
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }
}
