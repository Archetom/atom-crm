CREATE TABLE crm_opportunity
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT         NOT NULL,
    owner_actor_id      BIGINT         NOT NULL,
    customer_id         BIGINT         NOT NULL,
    name                VARCHAR(200)   NOT NULL,
    pipeline_code       VARCHAR(64)    NOT NULL,
    stage_code          VARCHAR(64)    NOT NULL,
    expected_close_date DATE           NULL,
    estimated_amount    DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    next_contact_time   DATETIME(6)    NULL,
    description         VARCHAR(2000)  NULL,
    outcome             VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    outcome_time        DATETIME(6)    NULL,
    outcome_remark      VARCHAR(1000)  NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version             BIGINT         NOT NULL DEFAULT 0,
    created_time        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity PRIMARY KEY (id),
    CONSTRAINT uk_crm_opportunity_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_opportunity_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_opportunity_outcome
        CHECK (outcome IN ('OPEN', 'WON', 'LOST', 'INVALID')),
    CONSTRAINT chk_crm_opportunity_result
        CHECK ((outcome = 'OPEN' AND outcome_time IS NULL AND outcome_remark IS NULL)
            OR (outcome <> 'OPEN' AND outcome_time IS NOT NULL)),
    CONSTRAINT chk_crm_opportunity_amount CHECK (estimated_amount >= 0),
    CONSTRAINT chk_crm_opportunity_version CHECK (version >= 0),
    CONSTRAINT fk_crm_opportunity_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_opportunity_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_opportunity_tenant_status_updated
        (tenant_id, status, updated_time, id),
    INDEX idx_crm_opportunity_tenant_owner
        (tenant_id, owner_actor_id, status),
    INDEX idx_crm_opportunity_tenant_customer
        (tenant_id, customer_id, status),
    INDEX idx_crm_opportunity_tenant_pipeline_stage
        (tenant_id, pipeline_code, stage_code, status),
    INDEX idx_crm_opportunity_tenant_outcome
        (tenant_id, outcome, status),
    INDEX idx_crm_opportunity_tenant_name
        (tenant_id, name, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_contact
(
    tenant_id     BIGINT      NOT NULL,
    opportunity_id BIGINT     NOT NULL,
    contact_id    BIGINT      NOT NULL,
    is_primary    TINYINT     NOT NULL DEFAULT 0,
    primary_slot  TINYINT GENERATED ALWAYS AS
        (CASE WHEN is_primary = 1 THEN 1 ELSE NULL END) STORED,
    created_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_contact
        PRIMARY KEY (tenant_id, opportunity_id, contact_id),
    CONSTRAINT uk_crm_opportunity_primary_contact
        UNIQUE (tenant_id, opportunity_id, primary_slot),
    CONSTRAINT chk_crm_opportunity_contact_primary
        CHECK (is_primary IN (0, 1)),
    CONSTRAINT fk_crm_opportunity_contact_root
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_opportunity_contact_target
        FOREIGN KEY (tenant_id, contact_id)
            REFERENCES crm_contact (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_opportunity_contact_target
        (tenant_id, contact_id, opportunity_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_collaborator
(
    tenant_id      BIGINT      NOT NULL,
    opportunity_id BIGINT      NOT NULL,
    actor_id       BIGINT      NOT NULL,
    access_level   VARCHAR(20) NOT NULL,
    created_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_collaborator
        PRIMARY KEY (tenant_id, opportunity_id, actor_id),
    CONSTRAINT chk_crm_opportunity_collaborator_access
        CHECK (access_level IN ('READ', 'WRITE')),
    CONSTRAINT fk_crm_opportunity_collaborator_root
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_opportunity_collaborator_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_crm_opportunity_collaborator_member
        (tenant_id, actor_id, access_level, opportunity_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_field_value
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT       NOT NULL,
    opportunity_id BIGINT       NOT NULL,
    object_type    VARCHAR(30)  NOT NULL DEFAULT 'OPPORTUNITY',
    field_id       BIGINT       NOT NULL,
    values_json    JSON         NOT NULL,
    scalar_value   VARCHAR(500) NULL,
    created_time   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_opportunity_field_value
        UNIQUE (tenant_id, opportunity_id, field_id),
    CONSTRAINT chk_crm_opportunity_field_value_object
        CHECK (object_type = 'OPPORTUNITY'),
    CONSTRAINT fk_crm_opportunity_field_value_root
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_opportunity_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_opportunity_field_value_search
        (tenant_id, field_id, scalar_value, opportunity_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_unique_field_value
(
    tenant_id        BIGINT      NOT NULL,
    opportunity_id   BIGINT      NOT NULL,
    object_type      VARCHAR(30) NOT NULL DEFAULT 'OPPORTUNITY',
    field_id         BIGINT      NOT NULL,
    normalized_hash  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value TEXT        NOT NULL,
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_unique_field_value
        PRIMARY KEY (tenant_id, opportunity_id, field_id),
    CONSTRAINT uk_crm_opportunity_unique_field_value
        UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_opportunity_unique_field_value_object
        CHECK (object_type = 'OPPORTUNITY'),
    CONSTRAINT fk_crm_opportunity_unique_field_value_root
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_opportunity_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_star
(
    tenant_id      BIGINT      NOT NULL,
    actor_id       BIGINT      NOT NULL,
    opportunity_id BIGINT      NOT NULL,
    created_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_star
        PRIMARY KEY (tenant_id, actor_id, opportunity_id),
    CONSTRAINT fk_crm_opportunity_star_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_opportunity_star_root
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_opportunity_star_root
        (tenant_id, opportunity_id, actor_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id,
       'OPPORTUNITY',
       definition.field_code,
       definition.display_name,
       NULL,
       definition.field_type,
       1,
       definition.is_required,
       0,
       definition.is_searchable,
       'ACTIVE',
       definition.display_order,
       'Opportunity',
       definition.options_json,
       0
FROM organization_tenant tenant
JOIN (
    SELECT 'opportunity_name' AS field_code, 'Opportunity name' AS display_name,
           'TEXT' AS field_type, 1 AS is_required, 1 AS is_searchable,
           10 AS display_order, JSON_ARRAY() AS options_json
    UNION ALL SELECT 'customer', 'Customer', 'RELATION', 1, 1, 20, JSON_ARRAY()
    UNION ALL SELECT 'primary_contact', 'Primary contact', 'RELATION', 0, 1, 30, JSON_ARRAY()
    UNION ALL SELECT 'contacts', 'Contacts', 'RELATION', 0, 0, 40, JSON_ARRAY()
    UNION ALL SELECT 'pipeline_code', 'Pipeline', 'TEXT', 1, 1, 50, JSON_ARRAY()
    UNION ALL SELECT 'stage_code', 'Sales stage', 'TEXT', 1, 1, 60, JSON_ARRAY()
    UNION ALL SELECT 'expected_close_date', 'Expected close date', 'DATE', 0, 1, 70, JSON_ARRAY()
    UNION ALL SELECT 'estimated_amount', 'Estimated amount', 'DECIMAL', 0, 1, 80, JSON_ARRAY()
    UNION ALL SELECT 'next_contact_time', 'Next contact time', 'DATETIME', 0, 1, 90, JSON_ARRAY()
    UNION ALL SELECT 'description', 'Description', 'TEXTAREA', 0, 1, 100, JSON_ARRAY()
    UNION ALL SELECT 'owner_actor', 'Owner', 'USER', 1, 1, 110, JSON_ARRAY()
    UNION ALL SELECT 'outcome', 'Outcome', 'SELECT', 0, 1, 120,
        JSON_ARRAY(JSON_OBJECT('value', 'OPEN', 'label', 'Open', 'order', 0),
                   JSON_OBJECT('value', 'WON', 'label', 'Won', 'order', 1),
                   JSON_OBJECT('value', 'LOST', 'label', 'Lost', 'order', 2),
                   JSON_OBJECT('value', 'INVALID', 'label', 'Invalid', 'order', 3))
    UNION ALL SELECT 'outcome_time', 'Outcome time', 'DATETIME', 0, 1, 130, JSON_ARRAY()
    UNION ALL SELECT 'outcome_remark', 'Outcome remark', 'TEXTAREA', 0, 1, 140, JSON_ARRAY()
    UNION ALL SELECT 'status', 'Status', 'SELECT', 0, 1, 150,
        JSON_ARRAY(JSON_OBJECT('value', 'ACTIVE', 'label', 'Active', 'order', 0))
    UNION ALL SELECT 'created_time', 'Created time', 'DATETIME', 0, 1, 160, JSON_ARRAY()
    UNION ALL SELECT 'updated_time', 'Updated time', 'DATETIME', 0, 1, 170, JSON_ARRAY()
) definition ON 1 = 1;
