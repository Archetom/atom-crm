CREATE TABLE crm_marketing_form_submission
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT      NOT NULL,
    marketing_form_id BIGINT      NOT NULL,
    form_version      BIGINT      NOT NULL,
    lead_id           BIGINT      NOT NULL,
    submitted_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_marketing_form_submission PRIMARY KEY (id),
    CONSTRAINT uk_crm_marketing_form_submission_tenant_id
        UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_marketing_form_submission_lead
        UNIQUE (tenant_id, lead_id),
    CONSTRAINT chk_crm_marketing_form_submission_version
        CHECK (form_version >= 0),
    CONSTRAINT fk_crm_marketing_form_submission_form
        FOREIGN KEY (tenant_id, marketing_form_id)
            REFERENCES crm_marketing_form (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_marketing_form_submission_lead
        FOREIGN KEY (tenant_id, lead_id)
            REFERENCES crm_lead (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_marketing_form_submission_form_time
        (tenant_id, marketing_form_id, submitted_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
