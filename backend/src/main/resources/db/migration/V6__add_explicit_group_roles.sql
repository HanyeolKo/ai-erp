-- Existing group membership has no proven creation authority; leave its role NULL.
ALTER TABLE "group".group_member ADD COLUMN role VARCHAR(16);
ALTER TABLE "group".group_member ADD CONSTRAINT group_member_role_check
    CHECK (role IS NULL OR role IN ('OWNER', 'ADMIN', 'MEMBER'));
CREATE INDEX group_member_user_group_idx ON "group".group_member (user_account_id, group_id);
