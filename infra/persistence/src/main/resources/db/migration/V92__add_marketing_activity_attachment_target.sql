-- Marketing activity media uses B24's controlled attachment lifecycle rather than a public URL.
ALTER TABLE crm_attachment_relation
    DROP CHECK chk_crm_attachment_relation_target,
    ADD COLUMN marketing_activity_id BIGINT NULL AFTER return_visit_id,
    ADD INDEX idx_crm_attachment_relation_marketing_activity
        (tenant_id, marketing_activity_id, relation_state, file_id),
    ADD CONSTRAINT fk_crm_attachment_relation_marketing_activity
        FOREIGN KEY (tenant_id, marketing_activity_id)
            REFERENCES crm_marketing_activity (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_crm_attachment_relation_target CHECK ((
        (target_type = 'WORK_TASK' AND work_task_id > 0)
        OR (target_type = 'CUSTOMER' AND customer_id > 0)
        OR (target_type = 'CONTACT' AND contact_id > 0)
        OR (target_type = 'LEAD' AND lead_id > 0)
        OR (target_type = 'OPPORTUNITY' AND opportunity_id > 0)
        OR (target_type = 'CONTRACT' AND contract_id > 0)
        OR (target_type = 'INVOICE' AND invoice_id > 0)
        OR (target_type = 'RECEIVABLE' AND receivable_id > 0)
        OR (target_type = 'RECEIVABLE_PLAN' AND receivable_plan_id > 0)
        OR (target_type = 'PRODUCT' AND product_id > 0)
        OR (target_type = 'RETURN_VISIT' AND return_visit_id > 0)
        OR (target_type = 'MARKETING_ACTIVITY' AND marketing_activity_id > 0)
        OR (target_type = 'OA_ANNOUNCEMENT' AND oa_announcement_id > 0)
        OR (target_type = 'OA_REQUEST' AND oa_request_id > 0)
    ) AND (
        (work_task_id IS NOT NULL) + (customer_id IS NOT NULL) + (contact_id IS NOT NULL)
        + (lead_id IS NOT NULL) + (opportunity_id IS NOT NULL) + (contract_id IS NOT NULL)
        + (invoice_id IS NOT NULL) + (receivable_id IS NOT NULL) + (receivable_plan_id IS NOT NULL)
        + (product_id IS NOT NULL) + (return_visit_id IS NOT NULL)
        + (marketing_activity_id IS NOT NULL) + (oa_announcement_id IS NOT NULL)
        + (oa_request_id IS NOT NULL) = 1
    ));
