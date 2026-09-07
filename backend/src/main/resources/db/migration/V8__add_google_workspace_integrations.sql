CREATE SCHEMA IF NOT EXISTS google_workspace;

CREATE TABLE identity.google_authorization (
    user_account_id UUID PRIMARY KEY REFERENCES identity.user_account(id),
    subject VARCHAR(255),
    account_email VARCHAR(320),
    access_token_ciphertext TEXT,
    refresh_token_ciphertext TEXT,
    granted_scopes TEXT NOT NULL DEFAULT '',
    expires_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'CONNECTED',
    generation BIGINT NOT NULL DEFAULT 0,
    refresh_claim_token UUID,
    refresh_lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX google_authorization_subject_idx ON identity.google_authorization(subject);

CREATE TABLE google_workspace.drive_reference (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    file_id VARCHAR(255) NOT NULL,
    name VARCHAR(500) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    url VARCHAR(600) NOT NULL,
    attached_by UUID NOT NULL,
    attached_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, file_id)
);
CREATE INDEX drive_reference_project_idx ON google_workspace.drive_reference(project_id, attached_at DESC);

CREATE TABLE google_workspace.mail_send_request (
    user_account_id UUID NOT NULL REFERENCES identity.user_account(id),
    request_id UUID NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('SENDING','SENT','UNKNOWN','FAILED')),
    credential_generation BIGINT NOT NULL DEFAULT 0,
    message_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_account_id, request_id)
);
CREATE INDEX mail_send_request_age_idx ON google_workspace.mail_send_request(status, updated_at);

ALTER TABLE calendar_integration.project_calendar ADD COLUMN binding_owner UUID REFERENCES identity.user_account(id);
ALTER TABLE calendar_integration.project_calendar ADD COLUMN binding_generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE calendar_integration.project_calendar ADD COLUMN calendar_name VARCHAR(255);
ALTER TABLE calendar_integration.project_calendar ADD COLUMN backfill_cursor UUID;
ALTER TABLE calendar_integration.project_calendar ADD COLUMN backfill_pending BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE calendar_integration.calendar_projection ADD COLUMN claim_token UUID;
ALTER TABLE calendar_integration.calendar_projection ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE calendar_integration.calendar_projection ADD COLUMN delivered_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE calendar_integration.calendar_projection ADD COLUMN reconcile_until TIMESTAMPTZ;
ALTER TABLE calendar_integration.calendar_projection ADD COLUMN next_reconcile_at TIMESTAMPTZ;
