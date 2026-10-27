CREATE TABLE crm_lead_intake_client
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    owner_actor_id BIGINT     NOT NULL,
    key_id       VARCHAR(64)  NOT NULL,
    api_key_hash CHAR(64)     NOT NULL,
    is_active    TINYINT      NOT NULL DEFAULT 1,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_lead_intake_client PRIMARY KEY (id),
    CONSTRAINT uk_crm_lead_intake_client_key UNIQUE (key_id),
    CONSTRAINT uk_crm_lead_intake_client_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_lead_intake_client_active CHECK (is_active IN (0, 1)),
    CONSTRAINT chk_crm_lead_intake_client_version CHECK (version >= 0),
    CONSTRAINT fk_crm_lead_intake_client_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_crm_lead_intake_client_tenant_active (tenant_id, is_active, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
