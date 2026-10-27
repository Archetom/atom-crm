-- B29a extends the two closed file target catalogs with typed same-tenant parents.  No scalar
-- field table gains a file identifier; only B24's ordinary relation and structured binding do.

ALTER TABLE crm_attachment_relation
    DROP CHECK chk_crm_attachment_relation_target,
    ADD COLUMN product_id BIGINT NULL AFTER receivable_plan_id,
    ADD COLUMN return_visit_id BIGINT NULL AFTER product_id,
    ADD INDEX idx_crm_attachment_relation_product
        (tenant_id, product_id, relation_state, file_id),
    ADD INDEX idx_crm_attachment_relation_return_visit
        (tenant_id, return_visit_id, relation_state, file_id),
    ADD CONSTRAINT fk_crm_attachment_relation_product
        FOREIGN KEY (tenant_id, product_id)
            REFERENCES crm_product (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_relation_return_visit
        FOREIGN KEY (tenant_id, return_visit_id)
            REFERENCES crm_return_visit (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_crm_attachment_relation_target CHECK (
        (target_type = 'WORK_TASK' AND work_task_id IS NOT NULL AND work_task_id > 0
            AND customer_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'CUSTOMER' AND customer_id IS NOT NULL AND customer_id > 0
            AND work_task_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'CONTACT' AND contact_id IS NOT NULL AND contact_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'LEAD' AND lead_id IS NOT NULL AND lead_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'OPPORTUNITY' AND opportunity_id IS NOT NULL AND opportunity_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'CONTRACT' AND contract_id IS NOT NULL AND contract_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'INVOICE' AND invoice_id IS NOT NULL AND invoice_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'RECEIVABLE' AND receivable_id IS NOT NULL AND receivable_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'RECEIVABLE_PLAN' AND receivable_plan_id IS NOT NULL AND receivable_plan_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
        OR (target_type = 'PRODUCT' AND product_id IS NOT NULL AND product_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND return_visit_id IS NULL)
        OR (target_type = 'RETURN_VISIT' AND return_visit_id IS NOT NULL AND return_visit_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL)
    );

ALTER TABLE crm_file_field_draft
    DROP CHECK chk_crm_file_field_draft_object,
    ADD CONSTRAINT chk_crm_file_field_draft_object CHECK (object_type IN (
        'CUSTOMER', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CONTRACT', 'INVOICE',
        'RECEIVABLE', 'RECEIVABLE_PLAN', 'PRODUCT', 'RETURN_VISIT', 'OA_REQUEST'));

ALTER TABLE crm_attachment_field_binding
    DROP CHECK chk_crm_attachment_field_binding_object,
    DROP CHECK chk_crm_attachment_field_binding_target,
    DROP INDEX uk_crm_attachment_field_binding_active_slot,
    DROP COLUMN target_id,
    ADD COLUMN product_id BIGINT NULL AFTER receivable_plan_id,
    ADD COLUMN return_visit_id BIGINT NULL AFTER product_id,
    ADD COLUMN target_id BIGINT GENERATED ALWAYS AS (COALESCE(customer_id, contact_id, lead_id,
        opportunity_id, contract_id, invoice_id, receivable_id, receivable_plan_id,
        product_id, return_visit_id)) STORED,
    ADD CONSTRAINT fk_crm_attachment_field_binding_product
        FOREIGN KEY (tenant_id, product_id) REFERENCES crm_product (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_field_binding_return_visit
        FOREIGN KEY (tenant_id, return_visit_id)
            REFERENCES crm_return_visit (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_crm_attachment_field_binding_object CHECK (object_type IN (
        'CUSTOMER', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CONTRACT', 'INVOICE',
        'RECEIVABLE', 'RECEIVABLE_PLAN', 'PRODUCT', 'RETURN_VISIT')),
    ADD CONSTRAINT chk_crm_attachment_field_binding_target CHECK (
        (object_type = 'CUSTOMER' AND customer_id IS NOT NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'CONTACT' AND contact_id IS NOT NULL AND customer_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'LEAD' AND lead_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'OPPORTUNITY' AND opportunity_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'CONTRACT' AND contract_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'INVOICE' AND invoice_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'RECEIVABLE' AND receivable_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'RECEIVABLE_PLAN' AND receivable_plan_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND product_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'PRODUCT' AND product_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL AND return_visit_id IS NULL)
        OR (object_type = 'RETURN_VISIT' AND return_visit_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL AND product_id IS NULL)
    ),
    ADD UNIQUE KEY uk_crm_attachment_field_binding_active_slot
        (tenant_id, object_type, target_id, field_id, active_ordinal),
    ADD INDEX idx_crm_attachment_field_binding_product (tenant_id, product_id, field_id, ordinal),
    ADD INDEX idx_crm_attachment_field_binding_return_visit
        (tenant_id, return_visit_id, field_id, ordinal);
