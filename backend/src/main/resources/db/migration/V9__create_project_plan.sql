CREATE TABLE project.project_plan (
    project_id UUID PRIMARY KEY REFERENCES project.project(id),
    row_version BIGINT NOT NULL DEFAULT 0,
    target_start DATE,
    target_end DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (target_start IS NULL OR target_end IS NULL OR target_start <= target_end)
);

CREATE TABLE project.plan_item (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    parent_id UUID,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('EPIC','TOPIC','TASK','MILESTONE')),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    assignee_id UUID REFERENCES identity.user_account(id),
    state VARCHAR(16) NOT NULL CHECK (state IN ('BACKLOG','READY','IN_PROGRESS','BLOCKED','DONE','CANCELLED')),
    target_start DATE,
    target_end DATE,
    deadline DATE,
    sort_order INTEGER NOT NULL,
    labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES identity.user_account(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, id),
    FOREIGN KEY (project_id, parent_id) REFERENCES project.plan_item(project_id, id),
    CHECK (target_start IS NULL OR target_end IS NULL OR target_start <= target_end),
    CHECK (sort_order >= -2147483648 AND sort_order <= 2147483647)
);
CREATE INDEX plan_item_project_order_idx ON project.plan_item(project_id, sort_order, id);
CREATE INDEX plan_item_project_parent_idx ON project.plan_item(project_id, parent_id);

CREATE TABLE project.plan_item_dependency (
    item_id UUID NOT NULL REFERENCES project.plan_item(id),
    predecessor_id UUID NOT NULL REFERENCES project.plan_item(id),
    PRIMARY KEY (item_id, predecessor_id),
    CHECK (item_id <> predecessor_id)
);
CREATE INDEX plan_dependency_predecessor_idx ON project.plan_item_dependency(predecessor_id);

CREATE TABLE project.plan_item_history (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    item_id UUID NOT NULL REFERENCES project.plan_item(id),
    actor_id UUID NOT NULL REFERENCES identity.user_account(id),
    at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL,
    reason VARCHAR(500),
    before_values JSONB,
    after_values JSONB NOT NULL
);
CREATE INDEX plan_item_history_item_idx ON project.plan_item_history(item_id, at DESC, id DESC);

CREATE TABLE project.project_plan_history (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    actor_id UUID NOT NULL REFERENCES identity.user_account(id),
    at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL,
    reason VARCHAR(500),
    before_values JSONB,
    after_values JSONB NOT NULL
);
CREATE INDEX project_plan_history_project_idx ON project.project_plan_history(project_id, at DESC, id DESC);

CREATE TABLE project.plan_item_creation_request (
    project_id UUID NOT NULL REFERENCES project.project(id),
    actor_id UUID NOT NULL REFERENCES identity.user_account(id),
    request_id UUID NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    item_id UUID NOT NULL REFERENCES project.plan_item(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, actor_id, request_id)
);
