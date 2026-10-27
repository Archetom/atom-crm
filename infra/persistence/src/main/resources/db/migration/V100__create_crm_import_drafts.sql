CREATE TABLE crm_import_draft
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT      NOT NULL,
    uploader_actor_id BIGINT      NOT NULL,
    object_type       VARCHAR(20) NOT NULL,
    deadline          DATETIME(6) NOT NULL,
    state             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    imported_rows     INT         NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_import_draft PRIMARY KEY (id),
    CONSTRAINT uk_crm_import_draft_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_import_draft_object_type CHECK (object_type IN ('LEAD', 'CONTACT')),
    CONSTRAINT chk_crm_import_draft_state CHECK (state IN ('ACTIVE', 'CONSUMED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT chk_crm_import_draft_imported_rows CHECK (imported_rows >= 0),
    CONSTRAINT chk_crm_import_draft_version CHECK (version >= 0),
    CONSTRAINT fk_crm_import_draft_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_import_draft_uploader
        FOREIGN KEY (tenant_id, uploader_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_import_draft_expiry (state, deadline, id),
    INDEX idx_crm_import_draft_uploader (tenant_id, uploader_actor_id, state, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE crm_attachment_relation
    DROP CHECK chk_crm_attachment_relation_target,
    ADD COLUMN import_draft_id BIGINT NULL AFTER oa_request_id,
    ADD INDEX idx_crm_attachment_relation_import_draft
        (tenant_id, import_draft_id, relation_state, file_id),
    ADD CONSTRAINT fk_crm_attachment_relation_import_draft
        FOREIGN KEY (tenant_id, import_draft_id)
            REFERENCES crm_import_draft (tenant_id, id) ON DELETE RESTRICT,
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
            WHEN 'IMPORT_DRAFT' THEN import_draft_id IS NOT NULL AND import_draft_id > 0
            ELSE FALSE
        END
        AND (
            (work_task_id IS NOT NULL) + (customer_id IS NOT NULL) + (contact_id IS NOT NULL)
            + (lead_id IS NOT NULL) + (opportunity_id IS NOT NULL) + (contract_id IS NOT NULL)
            + (invoice_id IS NOT NULL) + (receivable_id IS NOT NULL) + (receivable_plan_id IS NOT NULL)
            + (product_id IS NOT NULL) + (return_visit_id IS NOT NULL)
            + (marketing_activity_id IS NOT NULL) + (oa_announcement_id IS NOT NULL)
            + (oa_request_id IS NOT NULL) + (import_draft_id IS NOT NULL) = 1
        )
    );
