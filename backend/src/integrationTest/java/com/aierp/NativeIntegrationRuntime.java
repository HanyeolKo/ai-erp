package com.aierp;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import redis.embedded.core.ExecutableProvider;

/** Owns disposable loopback-only PostgreSQL and optional Redis processes for one test class. */
final class NativeIntegrationRuntime implements AutoCloseable {
    private static final String POSTGRES_USER = "postgres";
    private static final String POSTGRES_PASSWORD = "postgres";

    private final Path postgresDataDirectory;
    private final EmbeddedPostgres postgres;
    private final NativeRedisProcess redis;
    private final int redisPort;

    private NativeIntegrationRuntime(Path postgresDataDirectory, EmbeddedPostgres postgres,
            NativeRedisProcess redis, int redisPort) {
        this.postgresDataDirectory = postgresDataDirectory;
        this.postgres = postgres;
        this.redis = redis;
        this.redisPort = redisPort;
    }

    static NativeIntegrationRuntime start(boolean includeRedis) {
        return start(includeRedis, 0);
    }

    static NativeIntegrationRuntime start(boolean includeRedis, int requestedRedisPort) {
        Path postgresDataDirectory = null;
        EmbeddedPostgres postgres = null;
        NativeRedisProcess redis = null;
        int redisPort = -1;
        try {
            postgresDataDirectory = Files.createTempDirectory("ai-erp-integration-postgres-");
            postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(postgresDataDirectory.toFile())
                    .setPort(0)
                    .setServerConfig("listen_addresses", "127.0.0.1")
                    .setServerConfig("lc_messages", "C")
                    .start();

            if (includeRedis) {
                redisPort = requestedRedisPort > 0 ? requestedRedisPort : reserveLoopbackPort();
                redis = startRedis(redisPort);
            }
            return new NativeIntegrationRuntime(postgresDataDirectory, postgres, redis, redisPort);
        } catch (Exception failure) {
            cleanupAfterStartupFailure(redis, postgres, postgresDataDirectory, failure);
            throw new IllegalStateException("Unable to start native integration services", failure);
        }
    }

    String postgresUrl() {
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to read embedded PostgreSQL JDBC URL", failure);
        }
    }

    String postgresUsername() {
        return POSTGRES_USER;
    }

    String postgresPassword() {
        return POSTGRES_PASSWORD;
    }

    String postgresUrlFor(String database) {
        var url = postgresUrl();
        return url.substring(0, url.lastIndexOf('/') + 1) + database;
    }

    Path postgresDataDirectory() {
        return postgresDataDirectory;
    }

    int postgresPort() {
        return URI.create(postgresUrl().substring("jdbc:".length())).getPort();
    }

    int redisPort() {
        return redisPort;
    }

    String redisUrl() {
        if (redis == null) {
            throw new IllegalStateException("Redis was not started for this integration runtime");
        }
        return "redis://127.0.0.1:" + redisPort;
    }

    @Override
    public void close() {
        var failures = new ArrayList<Exception>();
        int postgresPort = postgres.getPort();
        int runningRedisPort = redis == null ? -1 : redisPort;
        Process postgresProcess = postgres.getProcess();
        close(redis, failures);
        close(postgres, failures);
        if (postgresProcess.isAlive()) {
            try {
                postgresProcess.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failures.add(interrupted);
            }
        }
        if (postgresProcess.isAlive()) {
            failures.add(new IllegalStateException("Owned PostgreSQL process is still alive after close: pid="
                    + postgresProcess.pid()));
        }
        verifyPortClosed(postgresPort, "PostgreSQL", failures);
        if (runningRedisPort > 0) {
            verifyPortClosed(runningRedisPort, "Redis", failures);
        }
        try {
            delete(postgresDataDirectory);
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
        if (!failures.isEmpty()) {
            var cleanupFailure = new IllegalStateException("Unable to clean up native integration services");
            failures.forEach(cleanupFailure::addSuppressed);
            throw cleanupFailure;
        }
    }

    private static int reserveLoopbackPort() throws IOException {
        try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void close(NativeRedisProcess server, List<Exception> failures) {
        if (server != null) {
            try {
                shutdownRedis(server);
            } catch (Exception failure) {
                failures.add(failure);
            }
        }
    }

    private static void close(EmbeddedPostgres server, List<Exception> failures) {
        if (server != null) {
            try {
                server.close();
            } catch (Exception failure) {
                failures.add(failure);
            }
        }
    }

    private static void verifyPortClosed(int port, String service, List<Exception> failures) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try (var socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
            } catch (java.net.ConnectException refused) {
                return;
            } catch (IOException failure) {
                failures.add(new IllegalStateException("Unable to verify " + service
                        + " shutdown on 127.0.0.1:" + port, failure));
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
        failures.add(new IllegalStateException(service + " still accepts loopback connections after close on port " + port));
    }

    private static void delete(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
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

    private static void cleanupAfterStartupFailure(NativeRedisProcess redis, EmbeddedPostgres postgres,
            Path directory, Exception startupFailure) {
        if (redis != null) {
            var failures = new ArrayList<Exception>();
            try {
                shutdownRedis(redis);
            } catch (Exception cleanupFailure) {
                failures.add(cleanupFailure);
            }
            failures.forEach(startupFailure::addSuppressed);
        }
        if (postgres != null) {
            int port = postgres.getPort();
            try {
                postgres.close();
            } catch (Exception cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
            var cleanupFailures = new ArrayList<Exception>();
            verifyPortClosed(port, "PostgreSQL after startup failure", cleanupFailures);
            cleanupFailures.forEach(startupFailure::addSuppressed);
        }
        try {
            delete(directory);
        } catch (RuntimeException cleanupFailure) {
            startupFailure.addSuppressed(cleanupFailure);
        }
    }

    private static NativeRedisProcess startRedis(int port) throws IOException {
        File executable = ExecutableProvider.newJarResourceProvider().get();
        String executablePath = executable.getAbsolutePath().replace('\\', '/');
        var arguments = List.of(
                "\"" + executablePath + "\" --bind 127.0.0.1 --port " + port + " --save \"\" --appendonly no");
        Process process;
        if (isWindows()) {
            String commandProcessor = System.getenv().getOrDefault("COMSPEC", "cmd.exe");
            process = new ProcessBuilder(commandProcessor, "/d", "/s", "/c", arguments.get(0))
                    .directory(executable.getParentFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } else {
            process = new ProcessBuilder(executablePath, "--bind", "127.0.0.1", "--port", Integer.toString(port),
                    "--save", "", "--appendonly", "no")
                    .directory(executable.getParentFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
        var nativeProcess = new NativeRedisProcess(process, port);
        try {
            awaitRedis(nativeProcess);
            return nativeProcess;
        } catch (IOException startupFailure) {
            try {
                nativeProcess.stopOwnedProcesses();
            } catch (IOException cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
            throw startupFailure;
        }
    }

    private static void awaitRedis(NativeRedisProcess redis) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            redis.captureServerProcess();
            if (!redis.launcher.isAlive()) {
                throw new IOException("Redis launcher exited before readiness with code " + redis.launcher.exitValue());
            }
            try (var socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", redis.port), 150);
                socket.setSoTimeout(500);
                OutputStream output = socket.getOutputStream();
                output.write("*1\r\n$4\r\nPING\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                output.flush();
                String response = new String(socket.getInputStream().readNBytes(7),
                        java.nio.charset.StandardCharsets.US_ASCII);
                if (response.equals("+PONG\r\n")) {
                    redis.captureServerProcess();
                    if (redis.serverProcess == null) {
                        throw new IOException("Unable to identify the ready Redis server process");
                    }
                    return;
                }
                lastFailure = new IOException("Unexpected Redis readiness response: " + response);
            } catch (IOException failure) {
                lastFailure = failure;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for Redis readiness", interrupted);
            }
        }
        throw new IOException("Redis did not become ready on loopback port " + redis.port, lastFailure);
    }

    private static void shutdownRedis(NativeRedisProcess redis) throws IOException {
        try {
            if (redis.serverProcess == null) {
                throw new IOException("Refusing to send Redis shutdown without an identified owned process");
            }
            if (redis.serverProcess.isAlive()) {
                try (var socket = new Socket()) {
                    socket.connect(new java.net.InetSocketAddress("127.0.0.1", redis.port), 500);
                    socket.setSoTimeout(1000);
                    var output = socket.getOutputStream();
                    output.write("*2\r\n$8\r\nSHUTDOWN\r\n$6\r\nNOSAVE\r\n"
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    output.flush();
                    try {
                        socket.getInputStream().read();
                    } catch (IOException expectedShutdownReset) {
                        // Redis closes the connection while shutting down; verify the owned process below.
                    }
                } catch (java.net.ConnectException alreadyStopped) {
                    if (redis.serverProcess.isAlive()) {
                        throw alreadyStopped;
                    }
                }
            }
            waitForProcess(redis.serverProcess, "Redis server");
            waitForProcess(redis.launcher.toHandle(), "Redis command wrapper");
            redis.removeShutdownHook();
        } catch (IOException shutdownFailure) {
            try {
                redis.stopOwnedProcesses();
            } catch (IOException cleanupFailure) {
                shutdownFailure.addSuppressed(cleanupFailure);
            }
            throw shutdownFailure;
        }
    }

    private static void waitForProcess(ProcessHandle process, String name) throws IOException {
        if (process == null || !process.isAlive()) {
            return;
        }
        try {
            process.onExit().get(3, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new IOException(name + " process remains alive after shutdown: pid=" + process.pid(), timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + name + " process", interrupted);
        } catch (Exception failure) {
            throw new IOException("Unable to wait for " + name + " process", failure);
        }
    }

    private static void terminateOwnedProcess(ProcessHandle process, String name) throws IOException {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            process.onExit().get(1, TimeUnit.SECONDS);
            return;
        } catch (java.util.concurrent.TimeoutException ignored) {
            process.destroyForcibly();
            waitForProcess(process, name);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping " + name, interrupted);
        } catch (Exception failure) {
            throw new IOException("Unable to stop " + name, failure);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
    }

    private static final class NativeRedisProcess {
        private final Process launcher;
        private final int port;
        private ProcessHandle serverProcess;
        private final Thread shutdownHook;
        private boolean shutdownHookRegistered;

        private NativeRedisProcess(Process launcher, int port) {
            this.launcher = launcher;
            this.port = port;
            this.shutdownHook = new Thread(() -> {
                try {
                    if (serverProcess == null) {
                        stopOwnedProcesses();
                    } else {
                        shutdownRedis(this);
                    }
                } catch (IOException hookFailure) {
                    try {
                        stopOwnedProcesses();
                    } catch (IOException cleanupFailure) {
                        hookFailure.addSuppressed(cleanupFailure);
                        System.err.println("Unable to stop owned Redis processes during JVM shutdown: " + hookFailure);
                    }
                }
            }, "ai-erp-integration-redis-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            shutdownHookRegistered = true;
        }

        private void captureServerProcess() throws IOException {
            var descendants = launcher.toHandle().descendants().toList();
            ProcessHandle found = descendants.stream()
                    .filter(process -> process.info().command().map(Path::of).map(Path::getFileName)
                            .map(Path::toString).map(name -> name.toLowerCase(java.util.Locale.ROOT))
                            .filter(name -> name.startsWith("redis-server")).isPresent())
                    .findFirst()
                    .orElseGet(() -> isWindows() ? null : launcher.toHandle());
            if (found != null) {
                serverProcess = found;
            }
        }

        private void stopOwnedProcesses() throws IOException {
            IOException failure = null;
            try {
                captureServerProcess();
                if (serverProcess != null && serverProcess.isAlive()) {
                    terminateOwnedProcess(serverProcess, "Redis server after startup failure");
                }
            } catch (IOException cleanupFailure) {
                failure = cleanupFailure;
            } finally {
                try {
                    terminateOwnedProcess(launcher.toHandle(), "Redis command wrapper after startup failure");
                } catch (IOException wrapperFailure) {
                    if (failure == null) {
                        failure = wrapperFailure;
                    } else {
                        failure.addSuppressed(wrapperFailure);
                    }
                }
                removeShutdownHook();
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void removeShutdownHook() {
            if (shutdownHookRegistered) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException shuttingDown) {
                    // The JVM is already running shutdown hooks.
                }
                shutdownHookRegistered = false;
            }
        }
    }
}
