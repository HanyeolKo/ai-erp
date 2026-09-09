package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the real PostgreSQL upgrade boundary from the last pre-Workspace schema. */
@Testcontainers
class V8MigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6");

    @Test
    void v7ToV8PreservesLegacyRowsAndAddsWorkspaceCalendarSchema() throws Exception {
        var database = "aierp_v7_to_v8_" + UUID.randomUUID().toString().replace("-", "");
        var adminUrl = "jdbc:postgresql://%s:%d/postgres".formatted(postgres.getHost(), postgres.getMappedPort(5432));
        var databaseUrl = "jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getMappedPort(5432), database);
        try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE " + identifier(database));
        }
        var user = UUID.randomUUID();
        var group = UUID.randomUUID();
        var project = UUID.randomUUID();
        var invitation = UUID.randomUUID();
        var connection = UUID.randomUUID();
        var calendar = UUID.randomUUID();
        var schedule = UUID.randomUUID();
        var projection = UUID.randomUUID();
        try {
            Flyway.configure().dataSource(databaseUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target("7").load().migrate();
            try (var db = DriverManager.getConnection(databaseUrl, postgres.getUsername(), postgres.getPassword())) {
                insertLegacyRows(db, user, group, project, invitation, connection, calendar, schedule, projection);
            }

            Flyway.configure().dataSource(databaseUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target("8").load().migrate();
            try (var db = DriverManager.getConnection(databaseUrl, postgres.getUsername(), postgres.getPassword())) {
                assertThat(scalar(db, "SELECT name FROM project.project WHERE id=?", project)).isEqualTo("Legacy project");
                assertThat(scalar(db, "SELECT status FROM project.project_invitation WHERE id=?", invitation)).isEqualTo("PENDING");
                assertThat(scalar(db, "SELECT role FROM \"group\".group_member WHERE group_id=? AND user_account_id=?", group, user)).isEqualTo("OWNER");
                assertThat(scalar(db, "SELECT external_calendar_id FROM calendar_integration.project_calendar WHERE id=?", calendar)).isEqualTo("primary");
                assertThat(scalar(db, "SELECT external_event_id FROM calendar_integration.calendar_projection WHERE id=?", projection)).isEqualTo("legacy-event");

                assertThat(scalar(db, "SELECT count(*) FROM google_workspace.drive_reference")).isEqualTo(0L);
                assertThat(scalar(db, "SELECT count(*) FROM google_workspace.mail_send_request")).isEqualTo(0L);
                assertThat(scalar(db, "SELECT binding_generation FROM calendar_integration.project_calendar WHERE id=?", calendar)).isEqualTo(0L);
                assertThat(scalar(db, "SELECT backfill_pending FROM calendar_integration.project_calendar WHERE id=?", calendar)).isEqualTo(false);
                assertThat(scalar(db, "SELECT delivered_revision FROM calendar_integration.calendar_projection WHERE id=?", projection)).isEqualTo(0L);
                assertThat(scalar(db, "SELECT version FROM flyway_schema_history WHERE version='8' AND success", new Object[0])).isEqualTo("8");
            }
        } finally {
            try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
                admin.createStatement().execute("DROP DATABASE IF EXISTS " + identifier(database));
            }
        }
    }

    private static void insertLegacyRows(Connection db, UUID user, UUID group, UUID project, UUID invitation,
                                          UUID connection, UUID calendar, UUID schedule, UUID projection) throws SQLException {
        execute(db, "INSERT INTO identity.user_account(id,email,display_name,email_verified_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", user, "legacy@example.test", "Legacy user");
        execute(db, "INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)", group, "Legacy group");
        execute(db, "INSERT INTO \"group\".group_member(group_id,user_account_id,role) VALUES (?,?,'OWNER')", group, user);
        execute(db, "INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)", project, group, "Legacy project");
        execute(db, "INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')", project, user);
        execute(db, "INSERT INTO project.project_invitation(id,project_id,email,token,status,expires_at,invited_by) VALUES (?,?,?,'legacy-token','PENDING',?,?)",
            invitation, project, "invitee@example.test", Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")), user);
        execute(db, "INSERT INTO calendar_integration.calendar_connection(id,user_account_id,status) VALUES (?,?,'SYNCED')", connection, user);
        execute(db, "INSERT INTO calendar_integration.project_calendar(id,project_id,calendar_connection_id,external_calendar_id) VALUES (?,?,?,'primary')", calendar, project, connection);
        execute(db, "INSERT INTO schedule.project_schedule(id,project_id,created_by,title,starts_at,ends_at,status) VALUES (?,?,?,'Legacy schedule',?,?,'CONFIRMED')",
            schedule, project, user, Timestamp.from(Instant.parse("2099-01-01T10:00:00Z")), Timestamp.from(Instant.parse("2099-01-01T11:00:00Z")));
        execute(db, "INSERT INTO calendar_integration.calendar_projection(id,schedule_id,project_calendar_id,status,external_event_id) VALUES (?,?,?,'SYNCED','legacy-event')",
            projection, schedule, calendar);
    }

    private static void execute(Connection db, String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (var i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static Object scalar(Connection db, String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (var i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return result.getObject(1);
            }
        }
    }

    private static String identifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
