# Backend integration tests

Run general integration tests with `./gradlew integrationTest` (or `gradlew.bat integrationTest`). They start a temporary PostgreSQL 18.6 process on loopback and keep the existing PostgreSQL migration and SQL assertions. Spring Session uses a per-test-context H2 JDBC repository, isolated from the PostgreSQL datasource; those tests cover persistence, deletion, and expiry. No Redis or Docker is needed for this task.

Run Redis parity coverage with `AI_ERP_REDIS_TEST_URL=redis://127.0.0.1:<port> ./gradlew redisIntegrationTest` (PowerShell: `$env:AI_ERP_REDIS_TEST_URL='redis://127.0.0.1:<port>'; .\gradlew.bat redisIntegrationTest`). The task rejects a missing or non-loopback URL and verifies Redis 8.2.9, readiness, session round trips, principal indexing, TTL/expiry, and deletion. It must target a disposable local Redis 8.2.9 instance; it never uses a production endpoint. CI starts an ephemeral `redis:8.2.9` service on a dynamic loopback port and passes its URL explicitly.

The PostgreSQL fixture sets `lc_messages=C` so database error assertions remain consistent across host locales. The fixture removes its temporary data and stops its process after each test class. The full CI workflow also performs Compose checks and builds the application image with Docker.
