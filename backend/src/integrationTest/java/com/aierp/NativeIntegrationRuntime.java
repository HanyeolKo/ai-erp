package com.aierp;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Owns one disposable loopback-only PostgreSQL process and its temporary data directory. */
final class NativeIntegrationRuntime implements AutoCloseable {
    private static final String POSTGRES_USER = "postgres";
    private static final String POSTGRES_PASSWORD = "postgres";

    private final Path postgresDataDirectory;
    private final EmbeddedPostgres postgres;

    private NativeIntegrationRuntime(Path postgresDataDirectory, EmbeddedPostgres postgres) {
        this.postgresDataDirectory = postgresDataDirectory;
        this.postgres = postgres;
    }

    static NativeIntegrationRuntime start() {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("ai-erp-integration-postgres-");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to create native PostgreSQL integration data directory", failure);
        }
        return start(directory);
    }

    static NativeIntegrationRuntime start(Path directory) {
        EmbeddedPostgres postgres = null;
        try {
            postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(directory.toFile())
                    .setPort(0)
                    .setServerConfig("listen_addresses", "127.0.0.1")
                    .setServerConfig("lc_messages", "C")
                    .start();
            return new NativeIntegrationRuntime(directory, postgres);
        } catch (Exception failure) {
            cleanupAfterStartupFailure(postgres, directory, failure);
            throw new IllegalStateException("Unable to start native PostgreSQL integration service", failure);
        }
    }

    String postgresUrl() {
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to read embedded PostgreSQL JDBC URL", failure);
        }
    }

    String postgresUsername() { return POSTGRES_USER; }
    String postgresPassword() { return POSTGRES_PASSWORD; }

    String postgresUrlFor(String database) {
        var url = postgresUrl();
        return url.substring(0, url.lastIndexOf('/') + 1) + database;
    }

    Path postgresDataDirectory() { return postgresDataDirectory; }
    int postgresPort() { return URI.create(postgresUrl().substring("jdbc:".length())).getPort(); }

    @Override
    public void close() {
        var failures = new ArrayList<Exception>();
        int port = postgres.getPort();
        Process process = postgres.getProcess();
        try {
            postgres.close();
        } catch (Exception failure) {
            failures.add(failure);
        }
        if (process.isAlive()) {
            try {
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failures.add(interrupted);
            }
        }
        if (process.isAlive()) {
            failures.add(new IllegalStateException("Owned PostgreSQL process is still alive after close: pid="
                    + process.pid()));
        }
        verifyPortClosed(port, failures);
        try {
            delete(postgresDataDirectory);
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
        if (!failures.isEmpty()) {
            var cleanupFailure = new IllegalStateException("Unable to clean up native PostgreSQL integration service");
            failures.forEach(cleanupFailure::addSuppressed);
            throw cleanupFailure;
        }
    }

    private static void verifyPortClosed(int port, List<Exception> failures) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try (var socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
            } catch (java.net.ConnectException refused) {
                return;
            } catch (IOException failure) {
                failures.add(new IllegalStateException("Unable to verify PostgreSQL shutdown on 127.0.0.1:" + port,
                        failure));
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failures.add(interrupted);
                return;
            }
        }
        failures.add(new IllegalStateException("PostgreSQL still accepts loopback connections after close on port " + port));
    }

    private static void delete(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException failure) {
                    throw new IllegalStateException("Unable to remove integration database data " + path, failure);
                }
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to remove integration database data " + directory, failure);
        }
    }

    private static void cleanupAfterStartupFailure(EmbeddedPostgres postgres, Path directory, Exception cause) {
        if (postgres != null) {
            int port = postgres.getPort();
            try {
                postgres.close();
            } catch (Exception failure) {
                cause.addSuppressed(failure);
            }
            var cleanupFailures = new ArrayList<Exception>();
            verifyPortClosed(port, cleanupFailures);
            cleanupFailures.forEach(cause::addSuppressed);
        }
        try {
            delete(directory);
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }
}
