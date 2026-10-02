package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

class H2SessionStoreLifecycleTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sessionDatabaseIsIsolatedAndRemovedWhenItsContextCloses() throws Exception {
        var first = new H2SessionConfiguration.H2SessionStore();
        String sessionId;
        String jdbcUrl = first.jdbcUrl();
        try {
            SessionRepository repository = first.repository();
            Session session = (Session) repository.createSession();
            sessionId = session.getId();
            session.setAttribute("lifecycle", UUID.randomUUID().toString());
            repository.save(session);
            assertThat(repository.findById(sessionId)).isNotNull();

            try (var isolated = new H2SessionConfiguration.H2SessionStore()) {
                assertThat(((SessionRepository) isolated.repository()).findById(sessionId)).isNull();
            }
        } finally {
            first.close();
        }

        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name='SPRING_SESSION'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }
}
