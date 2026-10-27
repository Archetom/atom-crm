ALTER TABLE crm_work_task_reminder_occurrence
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN available_at DATETIME(6) NULL AFTER due_time,
    ADD COLUMN deadline_at DATETIME(6) NULL AFTER available_at,
    ADD COLUMN safe_reason VARCHAR(40) NULL AFTER terminal_time;

UPDATE crm_work_task_reminder_occurrence
SET available_at = due_time,
    deadline_at = DATE_ADD(due_time, INTERVAL 1 DAY)
WHERE available_at IS NULL OR deadline_at IS NULL;

ALTER TABLE crm_work_task_reminder_occurrence
    MODIFY COLUMN available_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deadline_at DATETIME(6) NOT NULL,
    DROP CHECK chk_crm_work_task_reminder_occurrence_status,
    ADD CONSTRAINT chk_crm_work_task_reminder_occurrence_attempt
        CHECK (attempt_count >= 0),
    ADD CONSTRAINT chk_crm_work_task_reminder_occurrence_deadline
        CHECK (deadline_at > due_time),
    ADD CONSTRAINT chk_crm_work_task_reminder_occurrence_status CHECK (
        (status IN ('READY', 'RETRY_WAIT')
            AND lease_owner IS NULL AND lease_until IS NULL AND terminal_time IS NULL)
        OR (status = 'LEASED'
            AND lease_owner IS NOT NULL AND lease_until IS NOT NULL AND terminal_time IS NULL)
        OR (status IN ('EMITTED', 'CANCELLED', 'DEAD') AND terminal_time IS NOT NULL)
    ),
    ADD INDEX idx_crm_work_task_reminder_occurrence_claim
        (status, available_at, deadline_at, id);

ALTER TABLE crm_notification
    ADD INDEX idx_crm_notification_retention (created_time, id);
