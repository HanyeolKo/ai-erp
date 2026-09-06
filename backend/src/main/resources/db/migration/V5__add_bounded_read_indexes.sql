-- EXPAND only: module-local indexes supporting bounded read models and aggregates.
CREATE INDEX schedule_participant_schedule_member_idx ON schedule.schedule_participant (schedule_id, member_user_account_id);
CREATE INDEX schedule_participant_member_schedule_idx ON schedule.schedule_participant (member_user_account_id, schedule_id);
CREATE INDEX schedule_acknowledgement_current_idx ON schedule.schedule_acknowledgement (schedule_id, business_revision, user_account_id);
CREATE INDEX schedule_change_recent_idx ON schedule.schedule_change (schedule_id, created_at DESC, id DESC);
CREATE INDEX project_schedule_project_window_idx ON schedule.project_schedule (project_id, starts_at, id) INCLUDE (ends_at, status, business_revision);
CREATE INDEX project_schedule_project_ends_idx ON schedule.project_schedule (project_id, ends_at);
CREATE INDEX project_member_user_project_idx ON project.project_member (user_account_id, project_id) INCLUDE (role);
CREATE INDEX notification_user_recent_idx ON notification.notification (user_account_id, created_at DESC, id DESC);
CREATE INDEX calendar_projection_risk_idx ON calendar_integration.calendar_projection (project_calendar_id, status);
