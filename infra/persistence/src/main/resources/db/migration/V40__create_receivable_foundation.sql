CREATE TABLE crm_receivable_plan
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT         NOT NULL,
    owner_actor_id    BIGINT         NOT NULL,
    customer_id       BIGINT         NOT NULL,
    contract_id       BIGINT         NOT NULL,
    installment       INT            NOT NULL,
    active_installment INT
        GENERATED ALWAYS AS (CASE WHEN status <> 'DELETED' THEN installment ELSE NULL END) STORED,
    due_date          DATE           NOT NULL,
    planned_amount    DECIMAL(22, 2) NOT NULL,
    payment_method    VARCHAR(50)    NOT NULL,
    reminder_days     INT            NULL,
    remark            VARCHAR(2000)  NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version           BIGINT         NOT NULL DEFAULT 0,
    created_time      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_receivable_plan PRIMARY KEY (id),
    CONSTRAINT uk_crm_receivable_plan_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_receivable_plan_contract_installment
        UNIQUE (tenant_id, contract_id, active_installment),
    CONSTRAINT uk_crm_receivable_plan_contract_customer
        UNIQUE (tenant_id, id, contract_id, customer_id),
    CONSTRAINT chk_crm_receivable_plan_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_receivable_plan_installment CHECK (installment > 0),
    CONSTRAINT chk_crm_receivable_plan_amount CHECK (planned_amount > 0),
    CONSTRAINT chk_crm_receivable_plan_reminder
        CHECK (reminder_days IS NULL OR (reminder_days >= 0 AND reminder_days <= 3660)),
    CONSTRAINT chk_crm_receivable_plan_version CHECK (version >= 0),
    CONSTRAINT fk_crm_receivable_plan_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_receivable_plan_contract_customer
        FOREIGN KEY (tenant_id, contract_id, customer_id)
            REFERENCES crm_contract (tenant_id, id, customer_id)
            ON DELETE RESTRICT,

    INDEX idx_crm_receivable_plan_tenant_status_updated
        (tenant_id, status, updated_time, id),
    INDEX idx_crm_receivable_plan_tenant_contract
        (tenant_id, contract_id, status, due_date, id),
    INDEX idx_crm_receivable_plan_tenant_customer
        (tenant_id, customer_id, status, id),
    INDEX idx_crm_receivable_plan_tenant_owner
        (tenant_id, owner_actor_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_receivable
(
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT         NOT NULL,
    owner_actor_id         BIGINT         NOT NULL,
    customer_id            BIGINT         NOT NULL,
    contract_id            BIGINT         NOT NULL,
    receivable_plan_id     BIGINT         NULL,
    receipt_number         VARCHAR(64)    NOT NULL,
    active_receipt_number  VARCHAR(64)
        GENERATED ALWAYS AS (CASE WHEN status <> 'DELETED' THEN receipt_number ELSE NULL END) STORED,
    received_date          DATE           NOT NULL,
    payment_method         VARCHAR(50)    NOT NULL,
    amount                 DECIMAL(22, 2) NOT NULL,
    remark                 VARCHAR(2000)  NULL,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version                BIGINT         NOT NULL DEFAULT 0,
    created_time           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_receivable PRIMARY KEY (id),
    CONSTRAINT uk_crm_receivable_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_receivable_number UNIQUE (tenant_id, active_receipt_number),
    CONSTRAINT chk_crm_receivable_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_receivable_amount CHECK (amount > 0),
    CONSTRAINT chk_crm_receivable_version CHECK (version >= 0),
    CONSTRAINT fk_crm_receivable_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_receivable_contract_customer
        FOREIGN KEY (tenant_id, contract_id, customer_id)
            REFERENCES crm_contract (tenant_id, id, customer_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_receivable_plan_relation
        FOREIGN KEY (tenant_id, receivable_plan_id, contract_id, customer_id)
            REFERENCES crm_receivable_plan (tenant_id, id, contract_id, customer_id)
            ON DELETE RESTRICT,

    INDEX idx_crm_receivable_tenant_status_updated
        (tenant_id, status, updated_time, id),
    INDEX idx_crm_receivable_tenant_contract
        (tenant_id, contract_id, status, id),
    INDEX idx_crm_receivable_tenant_plan
        (tenant_id, receivable_plan_id, status, id),
    INDEX idx_crm_receivable_tenant_customer
        (tenant_id, customer_id, status, id),
    INDEX idx_crm_receivable_tenant_owner
        (tenant_id, owner_actor_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_receivable_field_value
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    receivable_id BIGINT      NOT NULL,
    object_type  VARCHAR(30)  NOT NULL DEFAULT 'RECEIVABLE',
    field_id     BIGINT       NOT NULL,
    values_json  JSON         NOT NULL,
    scalar_value VARCHAR(500) NULL,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_receivable_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_receivable_field_value UNIQUE (tenant_id, receivable_id, field_id),
    CONSTRAINT chk_crm_receivable_field_value_object CHECK (object_type = 'RECEIVABLE'),
    CONSTRAINT fk_crm_receivable_field_value_root
        FOREIGN KEY (tenant_id, receivable_id)
            REFERENCES crm_receivable (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_receivable_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    INDEX idx_crm_receivable_field_value_search
        (tenant_id, field_id, scalar_value, receivable_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_receivable_unique_field_value
(
    tenant_id       BIGINT      NOT NULL,
    receivable_id   BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL DEFAULT 'RECEIVABLE',
    field_id        BIGINT      NOT NULL,
    normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value TEXT       NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_receivable_unique_field_value PRIMARY KEY (tenant_id, receivable_id, field_id),
    CONSTRAINT uk_crm_receivable_unique_field_value UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_receivable_unique_field_value_object CHECK (object_type = 'RECEIVABLE'),
    CONSTRAINT fk_crm_receivable_unique_field_value_root
        FOREIGN KEY (tenant_id, receivable_id)
            REFERENCES crm_receivable (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_receivable_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_receivable_plan_field_value
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT       NOT NULL,
    receivable_plan_id BIGINT       NOT NULL,
    object_type        VARCHAR(30)  NOT NULL DEFAULT 'RECEIVABLE_PLAN',
    field_id           BIGINT       NOT NULL,
    values_json        JSON         NOT NULL,
    scalar_value       VARCHAR(500) NULL,
    created_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_receivable_plan_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_receivable_plan_field_value UNIQUE (tenant_id, receivable_plan_id, field_id),
    CONSTRAINT chk_crm_receivable_plan_field_value_object CHECK (object_type = 'RECEIVABLE_PLAN'),
    CONSTRAINT fk_crm_receivable_plan_field_value_root
        FOREIGN KEY (tenant_id, receivable_plan_id)
            REFERENCES crm_receivable_plan (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_receivable_plan_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    INDEX idx_crm_receivable_plan_field_value_search
        (tenant_id, field_id, scalar_value, receivable_plan_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_receivable_plan_unique_field_value
(
    tenant_id          BIGINT      NOT NULL,
    receivable_plan_id BIGINT      NOT NULL,
    object_type        VARCHAR(30) NOT NULL DEFAULT 'RECEIVABLE_PLAN',
    field_id           BIGINT      NOT NULL,
    normalized_hash    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value   TEXT        NOT NULL,
    created_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_receivable_plan_unique_field_value PRIMARY KEY (tenant_id, receivable_plan_id, field_id),
    CONSTRAINT uk_crm_receivable_plan_unique_field_value UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_receivable_plan_unique_field_value_object CHECK (object_type = 'RECEIVABLE_PLAN'),
    CONSTRAINT fk_crm_receivable_plan_unique_field_value_root
        FOREIGN KEY (tenant_id, receivable_plan_id)
            REFERENCES crm_receivable_plan (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_receivable_plan_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id, definition.object_type, definition.field_code, definition.display_name,
       NULL, definition.field_type, 1, definition.is_required, 0, 1, 'ACTIVE',
       definition.display_order, definition.section_name, definition.options_json, 0
FROM organization_tenant tenant
JOIN (
    SELECT 'RECEIVABLE' object_type, 'receipt_number' field_code, 'Receipt number' display_name,
           'TEXT' field_type, 1 is_required, 10 display_order, 'Receivable' section_name, JSON_ARRAY() options_json
    UNION ALL SELECT 'RECEIVABLE', 'customer', 'Customer', 'RELATION', 1, 20, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'contract', 'Contract', 'RELATION', 1, 30, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'receivable_plan', 'Receivable plan', 'RELATION', 0, 40, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'received_date', 'Received date', 'DATE', 1, 50, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'payment_method', 'Payment method', 'TEXT', 1, 60, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'amount', 'Received amount', 'DECIMAL', 1, 70, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'remark', 'Remark', 'TEXTAREA', 0, 80, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'owner_actor', 'Owner', 'USER', 1, 90, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'status', 'Status', 'SELECT', 0, 100, 'Receivable',
        JSON_ARRAY(JSON_OBJECT('value', 'ACTIVE', 'label', 'Active', 'order', 0))
    UNION ALL SELECT 'RECEIVABLE', 'created_time', 'Created time', 'DATETIME', 0, 110, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE', 'updated_time', 'Updated time', 'DATETIME', 0, 120, 'Receivable', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'plan_number', 'Plan number', 'INTEGER', 1, 10, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'customer', 'Customer', 'RELATION', 1, 20, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'contract', 'Contract', 'RELATION', 1, 30, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'due_date', 'Due date', 'DATE', 1, 40, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'planned_amount', 'Planned amount', 'DECIMAL', 1, 50, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'payment_method', 'Payment method', 'TEXT', 1, 60, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'reminder_days', 'Reminder days', 'INTEGER', 0, 70, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'remark', 'Remark', 'TEXTAREA', 0, 80, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'owner_actor', 'Owner', 'USER', 1, 90, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'received_amount', 'Received amount', 'DECIMAL', 0, 100, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'remaining_amount', 'Remaining amount', 'DECIMAL', 0, 110, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'progress', 'Progress', 'SELECT', 0, 120, 'Receivable plan',
        JSON_ARRAY(JSON_OBJECT('value', 'PENDING', 'label', 'Pending', 'order', 0),
                   JSON_OBJECT('value', 'PARTIAL', 'label', 'Partial', 'order', 1),
                   JSON_OBJECT('value', 'COMPLETED', 'label', 'Completed', 'order', 2),
                   JSON_OBJECT('value', 'OVERDUE', 'label', 'Overdue', 'order', 3))
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'status', 'Status', 'SELECT', 0, 130, 'Receivable plan',
        JSON_ARRAY(JSON_OBJECT('value', 'ACTIVE', 'label', 'Active', 'order', 0))
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'created_time', 'Created time', 'DATETIME', 0, 140, 'Receivable plan', JSON_ARRAY()
    UNION ALL SELECT 'RECEIVABLE_PLAN', 'updated_time', 'Updated time', 'DATETIME', 0, 150, 'Receivable plan', JSON_ARRAY()
) definition ON 1 = 1;
