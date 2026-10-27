ALTER TABLE crm_attachment_relation
    DROP CHECK chk_crm_attachment_relation_target,
    MODIFY COLUMN work_task_id BIGINT NULL,
    ADD COLUMN customer_id BIGINT NULL AFTER work_task_id,
    ADD COLUMN contact_id BIGINT NULL AFTER customer_id,
    ADD COLUMN lead_id BIGINT NULL AFTER contact_id,
    ADD COLUMN opportunity_id BIGINT NULL AFTER lead_id,
    ADD COLUMN contract_id BIGINT NULL AFTER opportunity_id,
    ADD COLUMN invoice_id BIGINT NULL AFTER contract_id,
    ADD COLUMN receivable_id BIGINT NULL AFTER invoice_id,
    ADD COLUMN receivable_plan_id BIGINT NULL AFTER receivable_id,
    ADD INDEX idx_crm_attachment_relation_customer
        (tenant_id, customer_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_contact
        (tenant_id, contact_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_lead
        (tenant_id, lead_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_opportunity
        (tenant_id, opportunity_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_contract
        (tenant_id, contract_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_invoice
        (tenant_id, invoice_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_receivable
        (tenant_id, receivable_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_receivable_plan
        (tenant_id, receivable_plan_id, relation_state, file_id),
    ADD CONSTRAINT fk_crm_attachment_relation_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_contact
        FOREIGN KEY (tenant_id, contact_id)
            REFERENCES crm_contact (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_lead
        FOREIGN KEY (tenant_id, lead_id)
            REFERENCES crm_lead (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_opportunity
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_contract
        FOREIGN KEY (tenant_id, contract_id)
            REFERENCES crm_contract (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_invoice
        FOREIGN KEY (tenant_id, invoice_id)
            REFERENCES crm_invoice (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_receivable
        FOREIGN KEY (tenant_id, receivable_id)
            REFERENCES crm_receivable (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_receivable_plan
        FOREIGN KEY (tenant_id, receivable_plan_id)
            REFERENCES crm_receivable_plan (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_crm_attachment_relation_target CHECK (
        (target_type = 'WORK_TASK' AND work_task_id IS NOT NULL AND work_task_id > 0
            AND customer_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'CUSTOMER' AND customer_id IS NOT NULL AND customer_id > 0
            AND work_task_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'CONTACT' AND contact_id IS NOT NULL AND contact_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'LEAD' AND lead_id IS NOT NULL AND lead_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'OPPORTUNITY' AND opportunity_id IS NOT NULL AND opportunity_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'CONTRACT' AND contract_id IS NOT NULL AND contract_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'INVOICE' AND invoice_id IS NOT NULL AND invoice_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'RECEIVABLE' AND receivable_id IS NOT NULL AND receivable_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_plan_id IS NULL)
        OR (target_type = 'RECEIVABLE_PLAN' AND receivable_plan_id IS NOT NULL AND receivable_plan_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL)
    );
