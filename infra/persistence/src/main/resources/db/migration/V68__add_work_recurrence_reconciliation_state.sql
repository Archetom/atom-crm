ALTER TABLE crm_work_recurrence_control
    ADD COLUMN reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'READY' AFTER policy_revision,
    ADD COLUMN target_tzdb_fingerprint VARCHAR(128) NULL AFTER reconciliation_status,
    ADD COLUMN target_policy_revision VARCHAR(40) NULL AFTER target_tzdb_fingerprint,
    ADD COLUMN cursor_tenant_id BIGINT NULL AFTER target_policy_revision,
    ADD COLUMN cursor_series_id BIGINT NULL AFTER cursor_tenant_id,
    ADD CONSTRAINT chk_crm_work_recurrence_control_reconciliation_status
        CHECK (reconciliation_status IN ('READY', 'RECONCILING'));

ALTER TABLE crm_work_task_series
    ADD INDEX idx_crm_work_task_series_reconciliation (status, tenant_id, id);
