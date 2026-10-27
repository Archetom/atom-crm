-- B24c keeps FILE values outside the scalar custom-value and approval-fact stores.  A staged
-- file has no ordinary attachment relation: it is consumed exactly once into one typed field
-- value by the owning CRM create/update or OA submit transaction.
CREATE TABLE crm_file_field_draft
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT      NOT NULL,
    uploader_actor_id BIGINT    NOT NULL,
    object_type     VARCHAR(30) NOT NULL,
    target_type     VARCHAR(30) NULL,
    target_id       BIGINT      NULL,
    oa_form_id      BIGINT      NULL,
    oa_form_version BIGINT      NULL,
    purpose_digest  BINARY(32)  NOT NULL,
    deadline        DATETIME(6) NOT NULL,
    state           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT      NOT NULL DEFAULT 0,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_file_field_draft PRIMARY KEY (id),
    CONSTRAINT uk_crm_file_field_draft_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_file_field_draft_object CHECK (object_type IN (
        'CUSTOMER', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CONTRACT', 'INVOICE',
        'RECEIVABLE', 'RECEIVABLE_PLAN', 'OA_REQUEST')),
    CONSTRAINT chk_crm_file_field_draft_state
        CHECK (state IN ('ACTIVE', 'CONSUMED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT chk_crm_file_field_draft_oa CHECK (
        (object_type = 'OA_REQUEST' AND oa_form_id IS NOT NULL AND oa_form_version IS NOT NULL)
        OR (object_type <> 'OA_REQUEST' AND oa_form_id IS NULL AND oa_form_version IS NULL)),
    CONSTRAINT chk_crm_file_field_draft_target CHECK (
        (target_type IS NULL AND target_id IS NULL)
        OR (target_type = object_type AND target_id > 0 AND object_type <> 'OA_REQUEST')),
    CONSTRAINT chk_crm_file_field_draft_version CHECK (version >= 0),
    CONSTRAINT fk_crm_file_field_draft_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_file_field_draft_uploader
        FOREIGN KEY (tenant_id, uploader_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_file_field_draft_oa_form
        FOREIGN KEY (tenant_id, oa_form_id)
            REFERENCES oa_form_definition (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_file_field_draft_expiry (state, deadline, id),
    INDEX idx_crm_file_field_draft_uploader (tenant_id, uploader_actor_id, state, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_file_field_draft_file
(
    tenant_id       BIGINT      NOT NULL,
    draft_id        BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL,
    field_id        BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    ordinal         SMALLINT    NOT NULL,
    state           VARCHAR(20) NOT NULL DEFAULT 'STAGED',
    content_version BIGINT      NULL,
    evidence_digest BINARY(32)  NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_file_field_draft_file PRIMARY KEY (tenant_id, draft_id, field_id, ordinal),
    CONSTRAINT uk_crm_file_field_draft_file_file UNIQUE (tenant_id, file_id),
    CONSTRAINT uk_crm_file_field_draft_file_slot UNIQUE (tenant_id, draft_id, field_id, file_id),
    CONSTRAINT chk_crm_file_field_draft_file_ordinal CHECK (ordinal BETWEEN 0 AND 9),
    CONSTRAINT chk_crm_file_field_draft_file_state CHECK (state IN ('STAGED', 'BOUND', 'WITHDRAWN')),
    CONSTRAINT chk_crm_file_field_draft_file_evidence CHECK (
        (state = 'STAGED' AND content_version IS NULL AND evidence_digest IS NULL)
        OR (state <> 'STAGED' AND content_version >= 1 AND evidence_digest IS NOT NULL)),
    CONSTRAINT fk_crm_file_field_draft_file_draft
        FOREIGN KEY (tenant_id, draft_id)
            REFERENCES crm_file_field_draft (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_file_field_draft_file_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_file_field_draft_file_file
        FOREIGN KEY (tenant_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_file_field_draft_file_file (tenant_id, file_id, state)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_attachment_field_binding
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT      NOT NULL,
    file_id            BIGINT      NOT NULL,
    object_type        VARCHAR(30) NOT NULL,
    field_id           BIGINT      NOT NULL,
    customer_id        BIGINT      NULL,
    contact_id         BIGINT      NULL,
    lead_id            BIGINT      NULL,
    opportunity_id     BIGINT      NULL,
    contract_id        BIGINT      NULL,
    invoice_id         BIGINT      NULL,
    receivable_id      BIGINT      NULL,
    receivable_plan_id BIGINT      NULL,
    target_id          BIGINT GENERATED ALWAYS AS (COALESCE(customer_id, contact_id, lead_id,
        opportunity_id, contract_id, invoice_id, receivable_id, receivable_plan_id)) STORED,
    ordinal            SMALLINT    NOT NULL,
    content_version    BIGINT      NOT NULL,
    evidence_digest    BINARY(32)  NOT NULL,
    state              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_file_id     BIGINT GENERATED ALWAYS AS (
        CASE WHEN state = 'ACTIVE' THEN file_id ELSE NULL END) STORED,
    active_ordinal     SMALLINT GENERATED ALWAYS AS (
        CASE WHEN state = 'ACTIVE' THEN ordinal ELSE NULL END) STORED,
    created_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_field_binding PRIMARY KEY (id),
    CONSTRAINT chk_crm_attachment_field_binding_object CHECK (object_type IN (
        'CUSTOMER', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CONTRACT', 'INVOICE',
        'RECEIVABLE', 'RECEIVABLE_PLAN')),
    CONSTRAINT chk_crm_attachment_field_binding_ordinal CHECK (ordinal BETWEEN 0 AND 9),
    CONSTRAINT chk_crm_attachment_field_binding_state CHECK (state IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT chk_crm_attachment_field_binding_target CHECK (
        (object_type = 'CUSTOMER' AND customer_id IS NOT NULL AND contact_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (object_type = 'CONTACT' AND contact_id IS NOT NULL AND customer_id IS NULL AND lead_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (object_type = 'LEAD' AND lead_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND opportunity_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (object_type = 'OPPORTUNITY' AND opportunity_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND contract_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (object_type = 'CONTRACT' AND contract_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND invoice_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (object_type = 'INVOICE' AND invoice_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND receivable_id IS NULL AND receivable_plan_id IS NULL)
        OR (object_type = 'RECEIVABLE' AND receivable_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_plan_id IS NULL)
        OR (object_type = 'RECEIVABLE_PLAN' AND receivable_plan_id IS NOT NULL AND customer_id IS NULL AND contact_id IS NULL
            AND lead_id IS NULL AND opportunity_id IS NULL AND contract_id IS NULL
            AND invoice_id IS NULL AND receivable_id IS NULL)),
    CONSTRAINT fk_crm_attachment_field_binding_file
        FOREIGN KEY (tenant_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_customer
        FOREIGN KEY (tenant_id, customer_id) REFERENCES crm_customer (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_contact
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contact (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_lead
        FOREIGN KEY (tenant_id, lead_id) REFERENCES crm_lead (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_opportunity
        FOREIGN KEY (tenant_id, opportunity_id) REFERENCES crm_opportunity (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES crm_contract (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES crm_invoice (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_receivable
        FOREIGN KEY (tenant_id, receivable_id) REFERENCES crm_receivable (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_field_binding_receivable_plan
        FOREIGN KEY (tenant_id, receivable_plan_id) REFERENCES crm_receivable_plan (tenant_id, id) ON DELETE RESTRICT,

    -- Historical WITHDRAWN rows are deliberately retained.  Nullable active projections
    -- make only the live binding unique, leaving every withdrawn revision representable.
    UNIQUE KEY uk_crm_attachment_field_binding_active_file (tenant_id, active_file_id),
    UNIQUE KEY uk_crm_attachment_field_binding_active_slot
        (tenant_id, object_type, target_id, field_id, active_ordinal),
    INDEX idx_crm_attachment_field_binding_customer (tenant_id, customer_id, field_id, ordinal),
    INDEX idx_crm_attachment_field_binding_contact (tenant_id, contact_id, field_id, ordinal),
    INDEX idx_crm_attachment_field_binding_lead (tenant_id, lead_id, field_id, ordinal),
    INDEX idx_crm_attachment_field_binding_opportunity (tenant_id, opportunity_id, field_id, ordinal),
    INDEX idx_crm_attachment_field_binding_contract (tenant_id, contract_id, field_id, ordinal),
    INDEX idx_crm_attachment_field_binding_invoice (tenant_id, invoice_id, field_id, ordinal),
    INDEX idx_crm_attachment_field_binding_receivable (tenant_id, receivable_id, field_id, ordinal),
    INDEX idx_crm_attachment_field_binding_receivable_plan (tenant_id, receivable_plan_id, field_id, ordinal)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_oa_request_file_value
(
    tenant_id       BIGINT      NOT NULL,
    oa_request_id   BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL,
    field_id        BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    ordinal         SMALLINT    NOT NULL,
    content_version BIGINT      NOT NULL,
    evidence_digest BINARY(32)  NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_oa_request_file_value PRIMARY KEY (tenant_id, oa_request_id, field_id, ordinal),
    CONSTRAINT uk_crm_oa_request_file_value_file UNIQUE (tenant_id, file_id),
    CONSTRAINT chk_crm_oa_request_file_value_ordinal CHECK (ordinal BETWEEN 0 AND 9),
    CONSTRAINT fk_crm_oa_request_file_value_request
        FOREIGN KEY (tenant_id, oa_request_id) REFERENCES oa_request (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_oa_request_file_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_oa_request_file_value_file
        FOREIGN KEY (tenant_id, file_id) REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_oa_request_file_value_request (tenant_id, oa_request_id, field_id, ordinal)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_approval_instance_file_fact
(
    tenant_id       BIGINT      NOT NULL,
    instance_id     BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL,
    field_id        BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    ordinal         SMALLINT    NOT NULL,
    content_version BIGINT      NOT NULL,
    evidence_digest BINARY(32)  NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crm_approval_instance_file_fact PRIMARY KEY (tenant_id, instance_id, field_id, ordinal),
    CONSTRAINT uk_crm_approval_instance_file_fact_file UNIQUE (tenant_id, instance_id, file_id),
    CONSTRAINT chk_crm_approval_instance_file_fact_ordinal CHECK (ordinal BETWEEN 0 AND 9),
    CONSTRAINT fk_crm_approval_instance_file_fact_instance
        FOREIGN KEY (tenant_id, instance_id) REFERENCES approval_instance (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_approval_instance_file_fact_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_approval_instance_file_fact_file
        FOREIGN KEY (tenant_id, file_id) REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_stage_workflow_file_fact
(
    tenant_id       BIGINT      NOT NULL,
    record_id       BIGINT      NOT NULL,
    stage_code      VARCHAR(64) NOT NULL,
    object_type     VARCHAR(30) NOT NULL,
    field_id        BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    ordinal         SMALLINT    NOT NULL,
    content_version BIGINT      NOT NULL,
    evidence_digest BINARY(32)  NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crm_stage_workflow_file_fact PRIMARY KEY (tenant_id, record_id, stage_code, field_id, ordinal),
    CONSTRAINT uk_crm_stage_workflow_file_fact_file UNIQUE (tenant_id, record_id, stage_code, file_id),
    CONSTRAINT chk_crm_stage_workflow_file_fact_ordinal CHECK (ordinal BETWEEN 0 AND 9),
    CONSTRAINT fk_crm_stage_workflow_file_fact_record
        FOREIGN KEY (tenant_id, record_id) REFERENCES stage_workflow_record (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_stage_workflow_file_fact_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_stage_workflow_file_fact_file
        FOREIGN KEY (tenant_id, file_id) REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
