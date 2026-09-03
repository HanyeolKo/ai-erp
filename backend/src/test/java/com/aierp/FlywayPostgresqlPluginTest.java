package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.junit.jupiter.api.Test;

class FlywayPostgresqlPluginTest {

    @Test
    void selects_the_postgresql_database_plugin_for_a_postgresql_jdbc_url() {
        var types = DatabaseTypeRegister.getDatabaseTypesForUrl(
                "jdbc:postgresql://localhost:5432/aierp", new FluentConfiguration());

        assertThat(types)
                .extracting(type -> type.getClass().getName())
                .contains("org.flywaydb.database.postgresql.PostgreSQLDatabaseType");
    }
}
