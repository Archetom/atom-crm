-- B29c: announcement attachments are ordinary B24 relations owned by the OA root.
-- V79 through V82 are already occupied by merged migrations, so this follow-up uses V83.
ALTER TABLE crm_attachment_relation
    DROP CHECK chk_crm_attachment_relation_target,
    ADD COLUMN oa_announcement_id BIGINT NULL AFTER return_visit_id,
    ADD INDEX idx_crm_attachment_relation_oa_announcement
        (tenant_id, oa_announcement_id, relation_state, file_id),
    ADD CONSTRAINT fk_crm_attachment_relation_oa_announcement
        FOREIGN KEY (tenant_id, oa_announcement_id)
            REFERENCES oa_announcement (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_crm_attachment_relation_target CHECK (
        (target_type = 'WORK_TASK' AND work_task_id IS NOT NULL AND work_task_id > 0
            AND customer_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'CUSTOMER' AND customer_id IS NOT NULL AND customer_id > 0
            AND work_task_id IS NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'CONTACT' AND contact_id IS NOT NULL AND contact_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'LEAD' AND lead_id IS NOT NULL AND lead_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'OPPORTUNITY' AND opportunity_id IS NOT NULL AND opportunity_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'CONTRACT' AND contract_id IS NOT NULL AND contract_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'INVOICE' AND invoice_id IS NOT NULL AND invoice_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'RECEIVABLE' AND receivable_id IS NOT NULL AND receivable_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'RECEIVABLE_PLAN' AND receivable_plan_id IS NOT NULL AND receivable_plan_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'PRODUCT' AND product_id IS NOT NULL AND product_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND return_visit_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'RETURN_VISIT' AND return_visit_id IS NOT NULL AND return_visit_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND oa_announcement_id IS NULL)
        OR (target_type = 'OA_ANNOUNCEMENT'
            AND oa_announcement_id IS NOT NULL AND oa_announcement_id > 0
            AND work_task_id IS NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL AND receivable_plan_id IS NULL
            AND product_id IS NULL AND return_visit_id IS NULL)
    );

ALTER TABLE oa_announcement
    DROP CHECK chk_oa_announcement_publication_reason,
    ADD CONSTRAINT chk_oa_announcement_publication_reason CHECK (
        last_publication_safe_reason IS NULL OR last_publication_safe_reason IN (
            'MISFIRE_EXPIRED', 'SCHEDULING_MEMBER_UNAUTHORIZED', 'AUDIENCE_INVALID',
            'AUDIENCE_EMPTY', 'AUDIENCE_LIMIT', 'ATTACHMENT_UNAVAILABLE', 'RETRY_EXHAUSTED'
        )
    );

ALTER TABLE oa_announcement_publication_occurrence
    DROP CHECK chk_oa_announcement_publication_occurrence_reason,
    ADD CONSTRAINT chk_oa_announcement_publication_occurrence_reason CHECK (
        (status IN ('READY', 'LEASED', 'PUBLISHED') AND safe_reason IS NULL)
        OR (status = 'RETRY_WAIT' AND safe_reason = 'RETRY')
        OR (status = 'CANCELLED' AND safe_reason IN (
            'SCHEDULING_MEMBER_UNAUTHORIZED', 'AUDIENCE_INVALID', 'AUDIENCE_EMPTY',
            'AUDIENCE_LIMIT', 'ATTACHMENT_UNAVAILABLE', 'CANCELLED', 'SUPERSEDED'
        ))
        OR (status = 'DEAD' AND safe_reason IN ('MISFIRE_EXPIRED', 'RETRY_EXHAUSTED'))
    );
