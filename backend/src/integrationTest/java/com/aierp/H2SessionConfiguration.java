package com.aierp;

import java.util.UUID;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicitly imported test-only repository; its H2 datasource and transaction manager stay private. */
@TestConfiguration(proxyBeanMethods = false)
@EnableSpringHttpSession
class H2SessionConfiguration {
    @Bean
    H2SessionStore h2SessionStore() throws SQLException {
        return new H2SessionStore();
    }

    @Bean
    SessionRepository<? extends Session> sessionRepository(H2SessionStore h2SessionStore) {
        return h2SessionStore.repository;
    }

    static final class H2SessionStore implements AutoCloseable {
        private final Connection lifecycleConnection;
        private final String jdbcUrl;
        private final JdbcIndexedSessionRepository repository;

        H2SessionStore() throws SQLException {
            var dataSource = newSessionDataSource();
            jdbcUrl = dataSource.getURL();
            lifecycleConnection = dataSource.getConnection();
            new ResourceDatabasePopulator(new ClassPathResource("org/springframework/session/jdbc/schema-h2.sql"))
                    .execute(dataSource);
            var transactionManager = new DataSourceTransactionManager(dataSource);
            repository = new JdbcIndexedSessionRepository(new JdbcTemplate(dataSource),
                    new TransactionTemplate(transactionManager));
        }

        String jdbcUrl() {
            return jdbcUrl;
        }

        JdbcIndexedSessionRepository repository() {
            return repository;
        }

        private static JdbcDataSource newSessionDataSource() {
            var dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:session-" + UUID.randomUUID());
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Override
        public void close() throws SQLException {
            lifecycleConnection.close();
        }
    }
}
