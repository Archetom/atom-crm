ALTER TABLE crm_attachment_relation
    ADD COLUMN marketing_activity_image_type VARCHAR(20) NULL AFTER marketing_activity_id,
    ADD INDEX idx_crm_attachment_relation_marketing_activity_image
        (tenant_id, marketing_activity_id, marketing_activity_image_type, relation_state, file_id),
    ADD CONSTRAINT chk_crm_attachment_relation_marketing_activity_image_type CHECK (
        marketing_activity_image_type IS NULL
        OR (target_type = 'MARKETING_ACTIVITY'
            AND marketing_activity_image_type IN ('COVER', 'DETAIL'))
    );
