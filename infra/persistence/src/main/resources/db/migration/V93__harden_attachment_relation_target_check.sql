-- SQL CHECK accepts UNKNOWN, so explicit NULL guards are required for every closed target type.
ALTER TABLE crm_attachment_relation
    DROP CHECK chk_crm_attachment_relation_target,
    ADD CONSTRAINT chk_crm_attachment_relation_target CHECK (
        CASE target_type
            WHEN 'WORK_TASK' THEN work_task_id IS NOT NULL AND work_task_id > 0
            WHEN 'CUSTOMER' THEN customer_id IS NOT NULL AND customer_id > 0
            WHEN 'CONTACT' THEN contact_id IS NOT NULL AND contact_id > 0
            WHEN 'LEAD' THEN lead_id IS NOT NULL AND lead_id > 0
            WHEN 'OPPORTUNITY' THEN opportunity_id IS NOT NULL AND opportunity_id > 0
            WHEN 'CONTRACT' THEN contract_id IS NOT NULL AND contract_id > 0
            WHEN 'INVOICE' THEN invoice_id IS NOT NULL AND invoice_id > 0
            WHEN 'RECEIVABLE' THEN receivable_id IS NOT NULL AND receivable_id > 0
            WHEN 'RECEIVABLE_PLAN' THEN receivable_plan_id IS NOT NULL AND receivable_plan_id > 0
            WHEN 'PRODUCT' THEN product_id IS NOT NULL AND product_id > 0
            WHEN 'RETURN_VISIT' THEN return_visit_id IS NOT NULL AND return_visit_id > 0
            WHEN 'MARKETING_ACTIVITY' THEN marketing_activity_id IS NOT NULL AND marketing_activity_id > 0
            WHEN 'OA_ANNOUNCEMENT' THEN oa_announcement_id IS NOT NULL AND oa_announcement_id > 0
            WHEN 'OA_REQUEST' THEN oa_request_id IS NOT NULL AND oa_request_id > 0
            ELSE FALSE
        END
        AND (
            (work_task_id IS NOT NULL) + (customer_id IS NOT NULL) + (contact_id IS NOT NULL)
            + (lead_id IS NOT NULL) + (opportunity_id IS NOT NULL) + (contract_id IS NOT NULL)
            + (invoice_id IS NOT NULL) + (receivable_id IS NOT NULL) + (receivable_plan_id IS NOT NULL)
            + (product_id IS NOT NULL) + (return_visit_id IS NOT NULL)
            + (marketing_activity_id IS NOT NULL) + (oa_announcement_id IS NOT NULL)
            + (oa_request_id IS NOT NULL) = 1
        )
    );
