ALTER TABLE crm_marketing_form
    ADD COLUMN target_type VARCHAR(20) NOT NULL DEFAULT 'LEAD' AFTER owner_actor_id,
    ADD CONSTRAINT chk_crm_marketing_form_target_type
        CHECK (target_type IN ('CUSTOMER', 'LEAD'));

ALTER TABLE crm_marketing_form_submission
    ADD COLUMN target_type VARCHAR(20) NOT NULL DEFAULT 'LEAD' AFTER form_version,
    ADD COLUMN customer_id BIGINT NULL AFTER lead_id,
    MODIFY COLUMN lead_id BIGINT NULL,
    ADD CONSTRAINT fk_crm_marketing_form_submission_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_crm_marketing_form_submission_customer
        UNIQUE (tenant_id, customer_id),
    ADD CONSTRAINT chk_crm_marketing_form_submission_target
        CHECK ((target_type = 'LEAD' AND lead_id IS NOT NULL AND customer_id IS NULL)
            OR (target_type = 'CUSTOMER' AND customer_id IS NOT NULL AND lead_id IS NULL));
