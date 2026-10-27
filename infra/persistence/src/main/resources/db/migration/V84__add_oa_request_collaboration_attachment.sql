-- B29d: post-submit OA collaboration files are ordinary B24 relations, never OA FILE facts.
ALTER TABLE crm_attachment_relation
    DROP CHECK chk_crm_attachment_relation_target,
    ADD COLUMN oa_request_id BIGINT NULL AFTER oa_announcement_id,
    ADD INDEX idx_crm_attachment_relation_oa_request
        (tenant_id, oa_request_id, relation_state, file_id),
    ADD CONSTRAINT fk_crm_attachment_relation_oa_request
        FOREIGN KEY (tenant_id, oa_request_id)
            REFERENCES oa_request (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_crm_attachment_relation_target CHECK (
        (target_type = 'WORK_TASK' AND work_task_id IS NOT NULL AND work_task_id > 0
            AND customer_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'CUSTOMER' AND customer_id IS NOT NULL AND customer_id > 0
            AND work_task_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'CONTACT' AND contact_id IS NOT NULL AND contact_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'LEAD' AND lead_id IS NOT NULL AND lead_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'OPPORTUNITY' AND opportunity_id IS NOT NULL AND opportunity_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'CONTRACT' AND contract_id IS NOT NULL AND contract_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'INVOICE' AND invoice_id IS NOT NULL AND invoice_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'RECEIVABLE' AND receivable_id IS NOT NULL AND receivable_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'RECEIVABLE_PLAN' AND receivable_plan_id IS NOT NULL AND receivable_plan_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL
            AND oa_request_id IS NULL)
        OR (target_type = 'PRODUCT' AND product_id IS NOT NULL AND product_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND return_visit_id IS NULL AND oa_announcement_id IS NULL AND oa_request_id IS NULL)
        OR (target_type = 'RETURN_VISIT' AND return_visit_id IS NOT NULL AND return_visit_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND oa_announcement_id IS NULL AND oa_request_id IS NULL)
        OR (target_type = 'OA_ANNOUNCEMENT'
            AND oa_announcement_id IS NOT NULL AND oa_announcement_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_request_id IS NULL)
        OR (target_type = 'OA_REQUEST'
            AND oa_request_id IS NOT NULL AND oa_request_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
    );
