CREATE TABLE crm_marketing_form
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT       NOT NULL,
    created_by_actor_id BIGINT      NOT NULL,
    owner_actor_id     BIGINT       NOT NULL,
    public_key         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name               VARCHAR(100) NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        VARCHAR(2000) NULL,
    submit_button_text VARCHAR(50)  NOT NULL,
    success_message    VARCHAR(500) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'INACTIVE',
    version            BIGINT       NOT NULL DEFAULT 0,
    created_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_marketing_form PRIMARY KEY (id),
    CONSTRAINT uk_crm_marketing_form_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_marketing_form_public_key UNIQUE (tenant_id, public_key),
    CONSTRAINT chk_crm_marketing_form_status
        CHECK (status IN ('INACTIVE', 'ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_marketing_form_version CHECK (version >= 0),
    CONSTRAINT fk_crm_marketing_form_creator
        FOREIGN KEY (tenant_id, created_by_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_marketing_form_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_marketing_form_list (tenant_id, status, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_marketing_form_field
(
    tenant_id        BIGINT       NOT NULL,
    marketing_form_id BIGINT      NOT NULL,
    field_code       VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    label            VARCHAR(100) NOT NULL,
    placeholder      VARCHAR(200) NULL,
    help_text        VARCHAR(500) NULL,
    is_required      TINYINT      NOT NULL DEFAULT 0,
    sort_order       INT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_marketing_form_field
        PRIMARY KEY (tenant_id, marketing_form_id, field_code),
    CONSTRAINT chk_crm_marketing_form_field_required CHECK (is_required IN (0, 1)),
    CONSTRAINT chk_crm_marketing_form_field_order
        CHECK (sort_order >= 0 AND sort_order <= 10000),
    CONSTRAINT fk_crm_marketing_form_field_form
        FOREIGN KEY (tenant_id, marketing_form_id)
            REFERENCES crm_marketing_form (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_marketing_form_field_reference (tenant_id, field_code, marketing_form_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
