-- Fail before any DDL when historical pairs cannot become immutable binary catalog codes.
-- The projection only evaluates for a violating row and deliberately raises ER_INVALID_JSON_TEXT.
SELECT JSON_EXTRACT(CONCAT('invalid-opportunity-pipeline-code:', pipeline_code, ':', stage_code), '$')
FROM crm_opportunity
WHERE OCTET_LENGTH(pipeline_code) <> CHAR_LENGTH(pipeline_code)
   OR OCTET_LENGTH(stage_code) <> CHAR_LENGTH(stage_code)
   OR pipeline_code NOT REGEXP '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'
   OR stage_code NOT REGEXP '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'
LIMIT 1;

ALTER TABLE crm_opportunity
    MODIFY COLUMN pipeline_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY COLUMN stage_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

CREATE TABLE crm_opportunity_pipeline_catalog_guard
(
    tenant_id BIGINT NOT NULL,

    CONSTRAINT pk_crm_opportunity_pipeline_catalog_guard PRIMARY KEY (tenant_id),
    CONSTRAINT fk_crm_opportunity_pipeline_catalog_guard_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_pipeline
(
    id            BIGINT                                NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT                                NOT NULL,
    code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name          VARCHAR(200)                          NOT NULL,
    display_order INT                                   NOT NULL,
    status        VARCHAR(20)                           NOT NULL,
    version       BIGINT                                NOT NULL DEFAULT 0,
    created_time  DATETIME(6)                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6)                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_pipeline PRIMARY KEY (id),
    CONSTRAINT uk_crm_opportunity_pipeline_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_opportunity_pipeline_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_crm_opportunity_pipeline_code
        CHECK (code REGEXP '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    CONSTRAINT chk_crm_opportunity_pipeline_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 200),
    CONSTRAINT chk_crm_opportunity_pipeline_order CHECK (display_order BETWEEN 0 AND 1000000),
    CONSTRAINT chk_crm_opportunity_pipeline_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_opportunity_pipeline_version CHECK (version >= 0),
    CONSTRAINT fk_crm_opportunity_pipeline_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE CASCADE,

    INDEX idx_crm_opportunity_pipeline_tenant_status_order
        (tenant_id, status, display_order, code, id),
    INDEX idx_crm_opportunity_pipeline_tenant_order
        (tenant_id, display_order, code, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_pipeline_stage
(
    id            BIGINT                                NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT                                NOT NULL,
    pipeline_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name          VARCHAR(200)                          NOT NULL,
    display_order INT                                   NOT NULL,
    probability   INT                                   NOT NULL,
    status        VARCHAR(20)                           NOT NULL,
    version       BIGINT                                NOT NULL DEFAULT 0,
    created_time  DATETIME(6)                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6)                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_pipeline_stage PRIMARY KEY (id),
    CONSTRAINT uk_crm_opportunity_pipeline_stage_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_opportunity_pipeline_stage_pair
        UNIQUE (tenant_id, pipeline_code, code),
    CONSTRAINT chk_crm_opportunity_pipeline_stage_pipeline_code
        CHECK (pipeline_code REGEXP '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    CONSTRAINT chk_crm_opportunity_pipeline_stage_code
        CHECK (code REGEXP '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    CONSTRAINT chk_crm_opportunity_pipeline_stage_name
        CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 200),
    CONSTRAINT chk_crm_opportunity_pipeline_stage_order
        CHECK (display_order BETWEEN 0 AND 1000000),
    CONSTRAINT chk_crm_opportunity_pipeline_stage_probability
        CHECK (probability BETWEEN 0 AND 100),
    CONSTRAINT chk_crm_opportunity_pipeline_stage_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_opportunity_pipeline_stage_version CHECK (version >= 0),
    CONSTRAINT fk_crm_opportunity_pipeline_stage_pipeline
        FOREIGN KEY (tenant_id, pipeline_code)
            REFERENCES crm_opportunity_pipeline (tenant_id, code)
            ON DELETE CASCADE,

    INDEX idx_crm_opportunity_pipeline_stage_tenant_status_order
        (tenant_id, pipeline_code, status, display_order, code, id),
    INDEX idx_crm_opportunity_pipeline_stage_tenant_order
        (tenant_id, pipeline_code, display_order, code, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO crm_opportunity_pipeline_catalog_guard (tenant_id)
SELECT tenant_id
FROM organization_tenant;

INSERT INTO crm_opportunity_pipeline
    (tenant_id, code, name, display_order, status, version)
SELECT DISTINCT tenant_id, pipeline_code, pipeline_code, 0, 'ACTIVE', 0
FROM crm_opportunity;

INSERT INTO crm_opportunity_pipeline_stage
    (tenant_id, pipeline_code, code, name, display_order, probability, status, version)
SELECT DISTINCT tenant_id, pipeline_code, stage_code, stage_code, 0, 0, 'ACTIVE', 0
FROM crm_opportunity;

INSERT INTO crm_opportunity_pipeline
    (tenant_id, code, name, display_order, status, version)
SELECT tenant.tenant_id, 'direct', 'Direct', 10, 'ACTIVE', 0
FROM organization_tenant tenant
WHERE NOT EXISTS (
    SELECT 1
    FROM crm_opportunity opportunity
    WHERE opportunity.tenant_id = tenant.tenant_id
);

INSERT INTO crm_opportunity_pipeline_stage
    (tenant_id, pipeline_code, code, name, display_order, probability, status, version)
SELECT tenant.tenant_id, 'direct', 'qualification', 'Qualification', 10, 0, 'ACTIVE', 0
FROM organization_tenant tenant
WHERE NOT EXISTS (
    SELECT 1
    FROM crm_opportunity opportunity
    WHERE opportunity.tenant_id = tenant.tenant_id
);

INSERT INTO crm_opportunity_pipeline_stage
    (tenant_id, pipeline_code, code, name, display_order, probability, status, version)
SELECT tenant.tenant_id, 'direct', 'negotiation', 'Negotiation', 20, 0, 'ACTIVE', 0
FROM organization_tenant tenant
WHERE NOT EXISTS (
    SELECT 1
    FROM crm_opportunity opportunity
    WHERE opportunity.tenant_id = tenant.tenant_id
);

ALTER TABLE crm_opportunity
    ADD CONSTRAINT fk_crm_opportunity_pipeline_stage_pair
        FOREIGN KEY (tenant_id, pipeline_code, stage_code)
            REFERENCES crm_opportunity_pipeline_stage (tenant_id, pipeline_code, code)
            ON DELETE RESTRICT;
