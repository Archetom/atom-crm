ALTER TABLE crm_marketing_activity
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN submission_count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_crm_marketing_activity_view_count CHECK (view_count >= 0),
    ADD CONSTRAINT chk_crm_marketing_activity_submission_count CHECK (submission_count >= 0);
