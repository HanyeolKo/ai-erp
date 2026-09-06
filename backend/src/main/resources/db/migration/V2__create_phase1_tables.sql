-- Phase 1 is an EXPAND-only migration. Existing V1 schemas and data remain intact.
CREATE TABLE identity.user_account (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE identity.google_identity (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL UNIQUE REFERENCES identity.user_account(id),
    subject VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "group".erp_group (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE "group".group_member (
    group_id UUID NOT NULL REFERENCES "group".erp_group(id),
    user_account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_account_id)
);

CREATE TABLE project.project (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE project.project_member (
    project_id UUID NOT NULL REFERENCES project.project(id),
    user_account_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('MANAGER', 'MEMBER', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_account_id)
);
CREATE TABLE project.project_invitation (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    email VARCHAR(320) NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    invited_by UUID NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX project_invitation_pending_email_idx ON project.project_invitation (email, expires_at) WHERE status = 'PENDING';

CREATE TABLE schedule.project_schedule (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    created_by UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    row_version BIGINT NOT NULL DEFAULT 0,
    business_revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (ends_at > starts_at)
);
CREATE INDEX project_schedule_project_starts_idx ON schedule.project_schedule (project_id, starts_at);
CREATE TABLE schedule.schedule_participant (
    schedule_id UUID NOT NULL,
    id UUID PRIMARY KEY,
    member_user_account_id UUID,
    external_email VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((member_user_account_id IS NULL) <> (external_email IS NULL))
);
CREATE TABLE schedule.schedule_acknowledgement (
    schedule_id UUID NOT NULL REFERENCES schedule.project_schedule(id),
    user_account_id UUID NOT NULL,
    business_revision BIGINT NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id, user_account_id, business_revision)
);
CREATE TABLE schedule.schedule_change (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES schedule.project_schedule(id),
    business_revision BIGINT NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    changed_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification.notification (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    link VARCHAR(512),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX notification_unread_idx ON notification.notification (user_account_id, created_at DESC) WHERE read_at IS NULL;
CREATE TABLE notification.notification_delivery (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notification.notification(id),
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    retry_classification VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE calendar_integration.calendar_connection (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('NOT_CONNECTED', 'PENDING', 'SYNCED', 'FAILED', 'REAUTH_REQUIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE calendar_integration.project_calendar (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE,
    calendar_connection_id UUID REFERENCES calendar_integration.calendar_connection(id),
    external_calendar_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE calendar_integration.calendar_projection (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL,
    project_calendar_id UUID NOT NULL REFERENCES calendar_integration.project_calendar(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'SYNCED', 'FAILED', 'REAUTH_REQUIRED')),
    retry_classification VARCHAR(32),
    external_event_id VARCHAR(255),
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (schedule_id, project_calendar_id)
);

CREATE TABLE audit.audit_log (
    id UUID PRIMARY KEY,
    actor_id UUID,
    action VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX audit_log_aggregate_idx ON audit.audit_log (aggregate_type, aggregate_id, occurred_at DESC);
CREATE TABLE platform.event_publication (
    id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    aggregate_id UUID,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
