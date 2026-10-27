-- B28b: retain the B27c Work source while adding one exact OA recipient source.
ALTER TABLE crm_notification_preference
    ADD COLUMN oa_announcement_email_enabled TINYINT NOT NULL DEFAULT 0 AFTER email_enabled,
    ADD CONSTRAINT chk_crm_notification_preference_oa_announcement_email
        CHECK (oa_announcement_email_enabled IN (0, 1));

ALTER TABLE crm_notification_delivery
    ADD COLUMN source_kind VARCHAR(40) NULL AFTER tenant_id;

UPDATE crm_notification_delivery
SET source_kind = 'WORK_TASK_NOTIFICATION'
WHERE source_kind IS NULL;

ALTER TABLE crm_notification_delivery
    MODIFY COLUMN source_kind VARCHAR(40) NOT NULL,
    MODIFY COLUMN notification_id BIGINT NULL,
    MODIFY COLUMN work_task_id BIGINT NULL,
    ADD COLUMN oa_announcement_id BIGINT NULL AFTER work_task_id,
    ADD CONSTRAINT chk_crm_notification_delivery_source_kind
        CHECK (source_kind IN ('WORK_TASK_NOTIFICATION', 'OA_ANNOUNCEMENT_RECIPIENT')),
    ADD CONSTRAINT chk_crm_notification_delivery_exact_source
        CHECK ((source_kind = 'WORK_TASK_NOTIFICATION'
                    AND notification_id IS NOT NULL AND work_task_id IS NOT NULL
                    AND oa_announcement_id IS NULL)
               OR (source_kind = 'OA_ANNOUNCEMENT_RECIPIENT'
                    AND notification_id IS NULL AND work_task_id IS NULL
                    AND oa_announcement_id IS NOT NULL));

ALTER TABLE crm_notification
    ADD CONSTRAINT uk_crm_notification_delivery_source
        UNIQUE (tenant_id, id, recipient_actor_id, work_task_id);

ALTER TABLE crm_notification_delivery
    ADD CONSTRAINT fk_crm_notification_delivery_work_source
        FOREIGN KEY (tenant_id, notification_id, recipient_actor_id, work_task_id)
            REFERENCES crm_notification (tenant_id, id, recipient_actor_id, work_task_id)
            ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_notification_delivery_oa_recipient
        FOREIGN KEY (tenant_id, oa_announcement_id, recipient_actor_id)
            REFERENCES oa_announcement_recipient (tenant_id, announcement_id, actor_id)
            ON DELETE RESTRICT,
    ADD CONSTRAINT uk_crm_notification_delivery_oa_channel
        UNIQUE (tenant_id, oa_announcement_id, recipient_actor_id, channel),
    ADD INDEX idx_crm_notification_delivery_oa_root
        (tenant_id, source_kind, oa_announcement_id, status, id);
