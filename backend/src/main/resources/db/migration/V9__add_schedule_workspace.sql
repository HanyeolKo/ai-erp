-- Additive schedule workspace storage. Existing schedule rows and projections are untouched.
CREATE UNIQUE INDEX project_schedule_project_id_id_uq ON schedule.project_schedule(project_id,id);
CREATE TABLE schedule.schedule_property (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    name VARCHAR(80) NOT NULL,
    property_type VARCHAR(24) NOT NULL CHECK (property_type IN ('TEXT','NUMBER','CHECKBOX','DATE','SINGLE_SELECT')),
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 10000),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (project_id, id),
    UNIQUE (id, property_type)
);
CREATE INDEX schedule_property_project_idx ON schedule.schedule_property(project_id, position, id);

CREATE TABLE schedule.schedule_property_option (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES schedule.schedule_property(id),
    label VARCHAR(80) NOT NULL,
    color VARCHAR(16) NOT NULL CHECK (color IN ('gray','blue','green','amber','red','purple','pink','teal')),
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 10000),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (property_id, id)
);
CREATE INDEX schedule_property_option_property_idx ON schedule.schedule_property_option(property_id, position, id);

CREATE TABLE schedule.schedule_property_value (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    schedule_id UUID NOT NULL REFERENCES schedule.project_schedule(id),
    property_id UUID NOT NULL REFERENCES schedule.schedule_property(id),
    value_type VARCHAR(24) NOT NULL CHECK (value_type IN ('TEXT','NUMBER','CHECKBOX','DATE','SINGLE_SELECT')),
    text_value TEXT,
    number_value NUMERIC,
    checkbox_value BOOLEAN,
    date_value DATE,
    option_id UUID,
    CHECK ((text_value IS NOT NULL)::int + (number_value IS NOT NULL)::int + (checkbox_value IS NOT NULL)::int + (date_value IS NOT NULL)::int + (option_id IS NOT NULL)::int = 1),
    CHECK ((value_type = 'TEXT' AND text_value IS NOT NULL AND number_value IS NULL AND checkbox_value IS NULL AND date_value IS NULL AND option_id IS NULL)
        OR (value_type = 'NUMBER' AND text_value IS NULL AND number_value IS NOT NULL AND checkbox_value IS NULL AND date_value IS NULL AND option_id IS NULL)
        OR (value_type = 'CHECKBOX' AND text_value IS NULL AND number_value IS NULL AND checkbox_value IS NOT NULL AND date_value IS NULL AND option_id IS NULL)
        OR (value_type = 'DATE' AND text_value IS NULL AND number_value IS NULL AND checkbox_value IS NULL AND date_value IS NOT NULL AND option_id IS NULL)
        OR (value_type = 'SINGLE_SELECT' AND text_value IS NULL AND number_value IS NULL AND checkbox_value IS NULL AND date_value IS NULL AND option_id IS NOT NULL)),
    UNIQUE (project_id, schedule_id, property_id),
    FOREIGN KEY (project_id, schedule_id) REFERENCES schedule.project_schedule(project_id, id),
    FOREIGN KEY (project_id, property_id) REFERENCES schedule.schedule_property(project_id, id),
    FOREIGN KEY (property_id, value_type) REFERENCES schedule.schedule_property(id, property_type),
    FOREIGN KEY (property_id, option_id) REFERENCES schedule.schedule_property_option(property_id, id)
);
CREATE INDEX schedule_property_value_query_idx ON schedule.schedule_property_value(project_id, property_id, schedule_id);

CREATE TABLE schedule.schedule_saved_view (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project.project(id),
    name VARCHAR(80) NOT NULL,
    scope VARCHAR(16) NOT NULL CHECK (scope IN ('PERSONAL','SHARED')),
    owner_id UUID NOT NULL,
    config JSONB NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (project_id, id)
);
CREATE INDEX schedule_saved_view_project_scope_idx ON schedule.schedule_saved_view(project_id, scope, archived);

CREATE TABLE schedule.schedule_dashboard_view (
    project_id UUID PRIMARY KEY REFERENCES project.project(id),
    dashboard_view_id UUID,
    builtin_view VARCHAR(32) NOT NULL DEFAULT 'builtin-cards' CHECK (builtin_view IN ('builtin-calendar','builtin-cards','builtin-list')),
    row_version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (project_id, dashboard_view_id) REFERENCES schedule.schedule_saved_view(project_id, id)
);
