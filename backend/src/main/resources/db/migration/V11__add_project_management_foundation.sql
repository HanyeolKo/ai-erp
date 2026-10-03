CREATE TABLE project.management_definition (
    project_id UUID PRIMARY KEY REFERENCES project.project(id),
    purpose VARCHAR(2000),
    success_criteria VARCHAR(2000),
    responsible_manager_id UUID REFERENCES identity.user_account(id),
    health VARCHAR(16) CHECK (health IS NULL OR health IN ('ON_TRACK','WATCH','AT_RISK')),
    health_reason VARCHAR(500),
    health_as_of DATE,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (health IS NULL OR (health_reason IS NOT NULL AND health_as_of IS NOT NULL))
);

CREATE TABLE project.task_execution (
    item_id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    priority VARCHAR(8) CHECK (priority IS NULL OR priority IN ('HIGH','MEDIUM','LOW')),
    completion_criterion VARCHAR(2000),
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, item_id),
    FOREIGN KEY (project_id, item_id) REFERENCES project.plan_item(project_id, id)
);

CREATE TABLE project.management_mutation_receipt (
    project_id UUID NOT NULL REFERENCES project.project(id),
    actor_id UUID NOT NULL REFERENCES identity.user_account(id),
    request_id UUID NOT NULL,
    operation VARCHAR(80) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    row_version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status = 'APPLIED'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, actor_id, request_id)
);

CREATE TABLE project.management_audit (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    actor_id UUID NOT NULL REFERENCES identity.user_account(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    row_version BIGINT NOT NULL,
    operation VARCHAR(80) NOT NULL,
    before_values JSONB,
    after_values JSONB NOT NULL
);
CREATE INDEX management_audit_resource_idx ON project.management_audit(project_id, resource_type, resource_id, occurred_at DESC, id DESC);
