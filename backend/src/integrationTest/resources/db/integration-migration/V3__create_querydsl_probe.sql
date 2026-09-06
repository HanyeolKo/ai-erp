-- V3 avoids collision with the production Phase 1 V2 migration.
CREATE TABLE platform.querydsl_probe (
    id BIGSERIAL PRIMARY KEY,
    label VARCHAR(255) NOT NULL
);
