package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lettuce.core.RedisClient;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NativeIntegrationRuntimeLifecycleTest {

    @Test
    void startsPostgres18AndRedisOnLoopbackAndStopsBothProcessesAndRemovesData() throws Exception {
        var runtime = NativeIntegrationRuntime.start(true);
        var postgresPort = runtime.postgresPort();
        var redisPort = runtime.redisPort();
        var postgresDataDirectory = runtime.postgresDataDirectory();
        try {
            assertThat(nativeProcessIds("redis-server")).isNotEmpty();
            try (var connection = DriverManager.getConnection(
                    runtime.postgresUrl(), runtime.postgresUsername(), runtime.postgresPassword())) {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery(
                                "SELECT version(), current_setting('listen_addresses')")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).contains("PostgreSQL 18.6");
                    assertThat(result.getString(2)).isEqualTo("127.0.0.1");
                }
            }

            try (var client = RedisClient.create(runtime.redisUrl());
                    var connection = client.connect()) {
                assertThat(connection.sync().ping()).isEqualTo("PONG");
                assertThat(connection.sync().info()).doesNotContain("\\u");
            }
        } finally {
            runtime.close();
        }

        assertThat(Files.exists(postgresDataDirectory)).isFalse();
        assertThatThrownBy(() -> connect(postgresPort)).isInstanceOf(ConnectException.class);
        assertThatThrownBy(() -> connect(redisPort)).isInstanceOf(ConnectException.class);
    }

    @Test
    void stopsPostgresAndRemovesItsDataWhenRedisCannotBind() throws Exception {
        var postgresProcesses = nativeProcessIds("postgres");
        var redisProcesses = nativeProcessIds("redis-server");
        var dataDirectories = integrationDataDirectories();

        try (var reservedPort = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int occupiedPort = reservedPort.getLocalPort();
            assertThatThrownBy(() -> NativeIntegrationRuntime.start(true, occupiedPort))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to start native integration services")
                    .hasCauseInstanceOf(Exception.class);
        }

        assertThat(nativeProcessIds("postgres")).isEqualTo(postgresProcesses);
        assertThat(nativeProcessIds("redis-server")).isEqualTo(redisProcesses);
        assertThat(integrationDataDirectories()).isEqualTo(dataDirectories);
    }

    private static void connect(int port) throws Exception {
        try (var socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
        }
    }

    private static Set<Long> nativeProcessIds(String executable) {
        return ProcessHandle.allProcesses()
                .filter(process -> process.info().command().map(Path::of).map(Path::getFileName)
                        .map(Path::toString)
                        .map(name -> name.toLowerCase(java.util.Locale.ROOT).replaceFirst("\\.exe$", ""))
                        .filter(name -> name.equals(executable) || name.startsWith(executable + "-")).isPresent())
                .map(ProcessHandle::pid)
                .collect(Collectors.toSet());
    }

    private static Set<Path> integrationDataDirectories() throws Exception {
        try (var entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("ai-erp-integration-postgres-"))
                    .collect(Collectors.toSet());
        }
    }
}
