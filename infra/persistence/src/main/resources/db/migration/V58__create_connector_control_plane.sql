CREATE TABLE crm_connector_registration
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    connector_ref VARCHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version       BIGINT      NOT NULL DEFAULT 0,
    created_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    retired_time  DATETIME(6) NULL,

    CONSTRAINT pk_crm_connector_registration PRIMARY KEY (id),
    CONSTRAINT uk_crm_connector_registration_ref UNIQUE (connector_ref),
    CONSTRAINT uk_crm_connector_registration_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_connector_registration_identity
        UNIQUE (tenant_id, id, connector_ref),
    CONSTRAINT chk_crm_connector_registration_ref
        CHECK (connector_ref REGEXP '^[A-Za-z0-9_-]{43}$'),
    CONSTRAINT chk_crm_connector_registration_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'RETIRED')),
    CONSTRAINT chk_crm_connector_registration_retired CHECK (
        (status = 'RETIRED' AND retired_time IS NOT NULL)
        OR (status <> 'RETIRED' AND retired_time IS NULL)),
    CONSTRAINT chk_crm_connector_registration_version CHECK (version >= 0),
    CONSTRAINT fk_crm_connector_registration_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT,

    INDEX idx_crm_connector_registration_tenant_status
        (tenant_id, status, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_connector_credential
(
    tenant_id       BIGINT      NOT NULL,
    connector_id    BIGINT      NOT NULL,
    connector_ref   VARCHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_id           VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    secret_verifier CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_time     DATETIME(6) NOT NULL,
    revoked_time     DATETIME(6) NULL,

    CONSTRAINT pk_crm_connector_credential
        PRIMARY KEY (tenant_id, connector_id, key_id),
    CONSTRAINT chk_crm_connector_credential_key
        CHECK (key_id REGEXP '^[A-Za-z0-9_-]{24}$'),
    CONSTRAINT chk_crm_connector_credential_verifier
        CHECK (secret_verifier REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_crm_connector_credential_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_crm_connector_credential_revoked CHECK (
        (status = 'ACTIVE' AND revoked_time IS NULL)
        OR (status = 'REVOKED' AND revoked_time IS NOT NULL)),
    CONSTRAINT fk_crm_connector_credential_registration
        FOREIGN KEY (tenant_id, connector_id, connector_ref)
            REFERENCES crm_connector_registration (tenant_id, id, connector_ref)
            ON DELETE RESTRICT ON UPDATE RESTRICT,

    INDEX idx_crm_connector_credential_active
        (tenant_id, connector_id, status, key_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
