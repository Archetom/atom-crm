CREATE TABLE crm_return_visit
(
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT         NOT NULL,
    owner_actor_id BIGINT         NOT NULL,
    customer_id    BIGINT         NOT NULL,
    contact_id     BIGINT         NULL,
    contract_id    BIGINT         NULL,
    visit_number   VARCHAR(64)    NOT NULL,
    active_number  VARCHAR(64)
        GENERATED ALWAYS AS (CASE WHEN status <> 'DELETED' THEN visit_number ELSE NULL END) STORED,
    visit_time     DATETIME(6)    NOT NULL,
    remark         VARCHAR(2000)  NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT         NOT NULL DEFAULT 0,
    created_time   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_return_visit PRIMARY KEY (id),
    CONSTRAINT uk_crm_return_visit_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_return_visit_number UNIQUE (tenant_id, active_number),
    CONSTRAINT chk_crm_return_visit_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_return_visit_version CHECK (version >= 0),
    CONSTRAINT fk_crm_return_visit_owner FOREIGN KEY (tenant_id, owner_actor_id)
        REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_return_visit_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES crm_customer (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_return_visit_contact FOREIGN KEY (tenant_id, contact_id)
        REFERENCES crm_contact (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_return_visit_contract FOREIGN KEY (tenant_id, contract_id)
        REFERENCES crm_contract (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_return_visit_tenant_status_time
        (tenant_id, status, visit_time, id),
    INDEX idx_crm_return_visit_tenant_owner
        (tenant_id, owner_actor_id, status, visit_time),
    INDEX idx_crm_return_visit_tenant_customer
        (tenant_id, customer_id, status, visit_time),
    INDEX idx_crm_return_visit_tenant_contract
        (tenant_id, contract_id, status, visit_time)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_return_visit_field_value
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT       NOT NULL,
    return_visit_id BIGINT       NOT NULL,
    object_type     VARCHAR(30)  NOT NULL DEFAULT 'RETURN_VISIT',
    field_id        BIGINT       NOT NULL,
    values_json     JSON         NOT NULL,
    scalar_value    VARCHAR(500) NULL,
    created_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_return_visit_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_return_visit_field_value UNIQUE (tenant_id, return_visit_id, field_id),
    CONSTRAINT chk_crm_return_visit_field_value_object CHECK (object_type = 'RETURN_VISIT'),
    CONSTRAINT fk_crm_return_visit_field_value_root FOREIGN KEY (tenant_id, return_visit_id)
        REFERENCES crm_return_visit (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_return_visit_field_value_field FOREIGN KEY (tenant_id, object_type, field_id)
        REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    INDEX idx_crm_return_visit_field_value_search
        (tenant_id, field_id, scalar_value, return_visit_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_return_visit_unique_field_value
(
    tenant_id        BIGINT      NOT NULL,
    return_visit_id  BIGINT      NOT NULL,
    object_type      VARCHAR(30) NOT NULL DEFAULT 'RETURN_VISIT',
    field_id         BIGINT      NOT NULL,
    normalized_hash  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value TEXT        NOT NULL,
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_return_visit_unique_field_value PRIMARY KEY (tenant_id, return_visit_id, field_id),
    CONSTRAINT uk_crm_return_visit_unique_field_value UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_return_visit_unique_field_value_object CHECK (object_type = 'RETURN_VISIT'),
    CONSTRAINT fk_crm_return_visit_unique_field_value_root FOREIGN KEY (tenant_id, return_visit_id)
        REFERENCES crm_return_visit (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_return_visit_unique_field_value_field FOREIGN KEY (tenant_id, object_type, field_id)
        REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id, definition.object_type, definition.field_code, definition.display_name,
       NULL, definition.field_type, 1, definition.is_required, 0, 1, 'ACTIVE',
       definition.display_order, 'Return visit', definition.options_json, 0
FROM organization_tenant tenant
JOIN (
    SELECT 'RETURN_VISIT' object_type, 'visit_number' field_code, 'Visit number' display_name,
           'TEXT' field_type, 1 is_required, 10 display_order, JSON_ARRAY() options_json
    UNION ALL SELECT 'RETURN_VISIT', 'customer', 'Customer', 'RELATION', 1, 20, JSON_ARRAY()
    UNION ALL SELECT 'RETURN_VISIT', 'contact', 'Contact', 'RELATION', 0, 30, JSON_ARRAY()
    UNION ALL SELECT 'RETURN_VISIT', 'contract', 'Contract', 'RELATION', 0, 40, JSON_ARRAY()
    UNION ALL SELECT 'RETURN_VISIT', 'visit_time', 'Visit time', 'DATETIME', 1, 50, JSON_ARRAY()
    UNION ALL SELECT 'RETURN_VISIT', 'remark', 'Remark', 'TEXTAREA', 0, 60, JSON_ARRAY()
    UNION ALL SELECT 'RETURN_VISIT', 'owner_actor', 'Owner', 'USER', 1, 70, JSON_ARRAY()
    UNION ALL SELECT 'RETURN_VISIT', 'status', 'Status', 'SELECT', 0, 80,
        JSON_ARRAY(JSON_OBJECT('value', 'ACTIVE', 'label', 'Active', 'order', 0))
    UNION ALL SELECT 'RETURN_VISIT', 'created_time', 'Created time', 'DATETIME', 0, 90, JSON_ARRAY()
    UNION ALL SELECT 'RETURN_VISIT', 'updated_time', 'Updated time', 'DATETIME', 0, 100, JSON_ARRAY()
) definition ON 1 = 1;
