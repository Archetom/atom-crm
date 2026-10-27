ALTER TABLE crm_marketing_activity
    ADD COLUMN marketing_form_id BIGINT NULL AFTER related_object_type,
    ADD CONSTRAINT fk_crm_marketing_activity_marketing_form
        FOREIGN KEY (tenant_id, marketing_form_id)
            REFERENCES crm_marketing_form (tenant_id, id) ON DELETE RESTRICT,
    ADD INDEX idx_crm_marketing_activity_marketing_form
        (tenant_id, marketing_form_id);
