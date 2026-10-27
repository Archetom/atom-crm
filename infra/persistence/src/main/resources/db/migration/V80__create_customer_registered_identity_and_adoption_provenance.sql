CREATE TABLE crm_customer_registered_identity
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id            BIGINT      NOT NULL,
    customer_id          BIGINT      NOT NULL,
    identity_scope_code  VARCHAR(48) NOT NULL,
    registry_scheme_code VARCHAR(48) NOT NULL,
    canonical_value      VARCHAR(64) NOT NULL,
    created_time         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    released_time        DATETIME(6) NULL,
    current_scope_code VARCHAR(48) GENERATED ALWAYS AS
        (CASE WHEN released_time IS NULL THEN identity_scope_code ELSE NULL END) STORED,
    current_scheme_code VARCHAR(48) GENERATED ALWAYS AS
        (CASE WHEN released_time IS NULL THEN registry_scheme_code ELSE NULL END) STORED,
    current_canonical_value VARCHAR(64) GENERATED ALWAYS AS
        (CASE WHEN released_time IS NULL THEN canonical_value ELSE NULL END) STORED,

    CONSTRAINT pk_crm_customer_registered_identity PRIMARY KEY (id),
    CONSTRAINT uk_crm_customer_registered_identity_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_customer_registered_identity_current
        UNIQUE (tenant_id, current_scope_code, current_scheme_code, current_canonical_value),
    CONSTRAINT fk_crm_customer_registered_identity_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    INDEX idx_crm_customer_registered_identity_customer
        (tenant_id, customer_id, released_time, identity_scope_code, registry_scheme_code, canonical_value)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_as_cs;

CREATE TABLE crm_enterprise_data_adoption
(
    id                              BIGINT       NOT NULL AUTO_INCREMENT,
    operation_id                    CHAR(36)     NOT NULL,
    tenant_id                       BIGINT       NOT NULL,
    customer_id                     BIGINT       NOT NULL,
    actor_id                        BIGINT       NOT NULL,
    primary_identity_association_id BIGINT       NOT NULL,
    source_kind                     VARCHAR(48)  NOT NULL,
    source_binding_ref              VARCHAR(128) NOT NULL,
    adapter_evidence_ref            VARCHAR(128) NOT NULL,
    policy_version                  VARCHAR(128) NOT NULL,
    attribution_version             VARCHAR(128) NOT NULL,
    mapping_version                 VARCHAR(128) NOT NULL,
    observed_at                     DATETIME(6)  NOT NULL,
    expires_at                      DATETIME(6)  NOT NULL,
    expected_customer_version       BIGINT       NOT NULL,
    committed_customer_version      BIGINT       NOT NULL,
    adopted_time                    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    delete_after                    DATETIME(6)  NOT NULL,

    CONSTRAINT pk_crm_enterprise_data_adoption PRIMARY KEY (id),
    CONSTRAINT uk_crm_enterprise_data_adoption_operation UNIQUE (tenant_id, operation_id),
    CONSTRAINT chk_crm_enterprise_data_adoption_versions
        CHECK (expected_customer_version >= 0
            AND committed_customer_version >= expected_customer_version),
    CONSTRAINT chk_crm_enterprise_data_adoption_deadlines
        CHECK (expires_at > observed_at AND delete_after > observed_at),
    CONSTRAINT fk_crm_enterprise_data_adoption_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_enterprise_data_adoption_actor
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_enterprise_data_adoption_identity
        FOREIGN KEY (tenant_id, primary_identity_association_id)
            REFERENCES crm_customer_registered_identity (tenant_id, id)
            ON DELETE RESTRICT,
    INDEX idx_crm_enterprise_data_adoption_retention (tenant_id, delete_after, id),
    INDEX idx_crm_enterprise_data_adoption_customer (tenant_id, customer_id, adopted_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_as_cs;

CREATE TABLE crm_enterprise_data_adoption_selection
(
    adoption_id     BIGINT       NOT NULL,
    line_no         SMALLINT     NOT NULL,
    fact_code       VARCHAR(48)  NOT NULL,
    target_locator  VARCHAR(128) NOT NULL,
    changed_value   TINYINT      NOT NULL,

    CONSTRAINT pk_crm_enterprise_data_adoption_selection PRIMARY KEY (adoption_id, line_no),
    CONSTRAINT uk_crm_enterprise_data_adoption_selection_target
        UNIQUE (adoption_id, fact_code, target_locator),
    CONSTRAINT chk_crm_enterprise_data_adoption_selection_changed CHECK (changed_value IN (0, 1)),
    CONSTRAINT fk_crm_enterprise_data_adoption_selection_header
        FOREIGN KEY (adoption_id)
            REFERENCES crm_enterprise_data_adoption (id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_as_cs;
