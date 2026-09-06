-- Additive guards only; V3 is reserved for the integration fixture.
CREATE UNIQUE INDEX project_invitation_one_pending_idx ON project.project_invitation (project_id, email) WHERE status = 'PENDING';
ALTER TABLE calendar_integration.calendar_projection ADD COLUMN business_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE calendar_integration.calendar_projection ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX event_publication_pending_idx ON platform.event_publication (created_at) WHERE published_at IS NULL;
