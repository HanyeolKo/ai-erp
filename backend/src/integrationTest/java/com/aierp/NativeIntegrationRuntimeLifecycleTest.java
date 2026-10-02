package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NativeIntegrationRuntimeLifecycleTest {

    @Test
    void startsPostgres18OnLoopbackAndStopsItAndRemovesItsData() throws Exception {
        var runtime = NativeIntegrationRuntime.start();
        var postgresPort = runtime.postgresPort();
        var postgresDataDirectory = runtime.postgresDataDirectory();
        try {
            try (var connection = DriverManager.getConnection(
                    runtime.postgresUrl(), runtime.postgresUsername(), runtime.postgresPassword())) {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("SELECT version(), current_setting('listen_addresses')")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).contains("PostgreSQL 18.6");
                    assertThat(result.getString(2)).isEqualTo("127.0.0.1");
                }
            }
        } finally {
            runtime.close();
        }

        assertThat(Files.exists(postgresDataDirectory)).isFalse();
        assertThatThrownBy(() -> connect(postgresPort)).isInstanceOf(ConnectException.class);
    }

    @Test
    void closesPostgresAndRemovesItsDataWhenPostgresStartupFails() throws Exception {
        var postgresProcesses = nativeProcessIds("postgres");
        var dataDirectories = integrationDataDirectories();
        var invalidDirectory = Path.of(System.getProperty("java.io.tmpdir"), "ai-erp-test-file-" + System.nanoTime());
        Files.writeString(invalidDirectory, "not a directory");
        try {
            assertThatThrownBy(() -> NativeIntegrationRuntime.start(invalidDirectory))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to start native PostgreSQL integration service");
            assertThat(nativeProcessIds("postgres")).isEqualTo(postgresProcesses);
            assertThat(integrationDataDirectories()).isEqualTo(dataDirectories);
        } finally {
            Files.deleteIfExists(invalidDirectory);
        }
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
