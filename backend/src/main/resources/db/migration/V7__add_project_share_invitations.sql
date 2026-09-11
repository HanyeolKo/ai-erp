-- Additive project creation idempotency and reusable shared invitation records.
CREATE TABLE project.project_creation_request (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    request_id UUID NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    requested_group_id UUID,
    project_id UUID NOT NULL REFERENCES project.project(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_creation_request_actor_key UNIQUE (actor_id, request_id)
);
CREATE INDEX project_creation_request_project_idx ON project.project_creation_request (project_id);

CREATE TABLE project.project_share_invitation (
    project_id UUID PRIMARY KEY REFERENCES project.project(id),
    code VARCHAR(16) NOT NULL UNIQUE,
    invited_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
