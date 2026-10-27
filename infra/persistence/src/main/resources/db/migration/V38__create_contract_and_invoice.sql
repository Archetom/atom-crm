CREATE TABLE crm_contract
(
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT         NOT NULL,
    owner_actor_id     BIGINT         NOT NULL,
    customer_id        BIGINT         NOT NULL,
    opportunity_id     BIGINT         NULL,
    contact_id         BIGINT         NULL,
    name               VARCHAR(200)   NOT NULL,
    contract_number    VARCHAR(100)   NOT NULL,
    contract_number_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    active_number_key  VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS
        (CASE WHEN status <> 'DELETED' THEN contract_number_key ELSE NULL END) STORED,
    order_date         DATE           NULL,
    start_date         DATE           NULL,
    end_date           DATE           NULL,
    contract_type      VARCHAR(100)   NULL,
    payment_type       VARCHAR(200)   NULL,
    remark             VARCHAR(2000)  NULL,
    subtotal           DECIMAL(22, 2) NOT NULL DEFAULT 0.00,
    discount_rate      DECIMAL(9, 4)  NOT NULL DEFAULT 0.0000,
    total_amount       DECIMAL(22, 2) NOT NULL DEFAULT 0.00,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version            BIGINT         NOT NULL DEFAULT 0,
    created_time       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_contract PRIMARY KEY (id),
    CONSTRAINT uk_crm_contract_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_contract_tenant_id_customer
        UNIQUE (tenant_id, id, customer_id),
    CONSTRAINT uk_crm_contract_number UNIQUE (tenant_id, active_number_key),
    CONSTRAINT chk_crm_contract_status
        CHECK (status IN ('ACTIVE', 'VOIDED', 'DELETED')),
    CONSTRAINT chk_crm_contract_dates
        CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_crm_contract_amounts
        CHECK (subtotal >= 0 AND total_amount >= 0
            AND discount_rate >= 0 AND discount_rate <= 100),
    CONSTRAINT chk_crm_contract_version CHECK (version >= 0),
    CONSTRAINT fk_crm_contract_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_contract_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_contract_opportunity
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_contract_contact
        FOREIGN KEY (tenant_id, contact_id)
            REFERENCES crm_contact (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_contract_tenant_status_updated
        (tenant_id, status, updated_time, id),
    INDEX idx_crm_contract_tenant_customer
        (tenant_id, customer_id, status, id),
    INDEX idx_crm_contract_tenant_opportunity
        (tenant_id, opportunity_id, status, id),
    INDEX idx_crm_contract_tenant_owner
        (tenant_id, owner_actor_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_contract_collaborator
(
    tenant_id    BIGINT      NOT NULL,
    contract_id  BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_contract_collaborator
        PRIMARY KEY (tenant_id, contract_id, actor_id),
    CONSTRAINT chk_crm_contract_collaborator_access
        CHECK (access_level IN ('READ', 'WRITE')),
    CONSTRAINT fk_crm_contract_collaborator_root
        FOREIGN KEY (tenant_id, contract_id)
            REFERENCES crm_contract (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_contract_collaborator_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_contract_product_line
(
    tenant_id       BIGINT         NOT NULL,
    contract_id     BIGINT         NOT NULL,
    product_id      BIGINT         NOT NULL,
    product_name    VARCHAR(200)   NOT NULL,
    product_code    VARCHAR(100)   NOT NULL,
    category_name   VARCHAR(200)   NULL,
    unit            VARCHAR(50)    NULL,
    list_price      DECIMAL(18, 2) NOT NULL,
    unit_price      DECIMAL(18, 2) NOT NULL,
    quantity        DECIMAL(18, 4) NOT NULL,
    discount_rate   DECIMAL(9, 4)  NOT NULL,
    line_amount     DECIMAL(22, 2) NOT NULL,
    display_order   INT            NOT NULL,
    created_time    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_contract_product_line
        PRIMARY KEY (tenant_id, contract_id, product_id),
    CONSTRAINT uk_crm_contract_product_order
        UNIQUE (tenant_id, contract_id, display_order),
    CONSTRAINT chk_crm_contract_product_price
        CHECK (list_price >= 0 AND unit_price >= 0 AND line_amount >= 0),
    CONSTRAINT chk_crm_contract_product_quantity CHECK (quantity > 0),
    CONSTRAINT chk_crm_contract_product_discount
        CHECK (discount_rate >= 0 AND discount_rate <= 100),
    CONSTRAINT chk_crm_contract_product_order CHECK (display_order >= 0),
    CONSTRAINT fk_crm_contract_product_root
        FOREIGN KEY (tenant_id, contract_id)
            REFERENCES crm_contract (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_contract_product_target
        FOREIGN KEY (tenant_id, product_id)
            REFERENCES crm_product (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_contract_product_target
        (tenant_id, product_id, contract_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_contract_field_value
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    contract_id  BIGINT       NOT NULL,
    object_type  VARCHAR(30)  NOT NULL DEFAULT 'CONTRACT',
    field_id     BIGINT       NOT NULL,
    values_json  JSON         NOT NULL,
    scalar_value VARCHAR(500) NULL,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_contract_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_contract_field_value
        UNIQUE (tenant_id, contract_id, field_id),
    CONSTRAINT chk_crm_contract_field_value_object CHECK (object_type = 'CONTRACT'),
    CONSTRAINT fk_crm_contract_field_value_root
        FOREIGN KEY (tenant_id, contract_id)
            REFERENCES crm_contract (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_contract_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT,
    INDEX idx_crm_contract_field_value_search
        (tenant_id, field_id, scalar_value, contract_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_contract_unique_field_value
(
    tenant_id       BIGINT      NOT NULL,
    contract_id     BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL DEFAULT 'CONTRACT',
    field_id        BIGINT      NOT NULL,
    normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value TEXT       NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_contract_unique_field_value
        PRIMARY KEY (tenant_id, contract_id, field_id),
    CONSTRAINT uk_crm_contract_unique_field_value
        UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_contract_unique_field_value_object
        CHECK (object_type = 'CONTRACT'),
    CONSTRAINT fk_crm_contract_unique_field_value_root
        FOREIGN KEY (tenant_id, contract_id)
            REFERENCES crm_contract (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_contract_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_invoice
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT         NOT NULL,
    owner_actor_id      BIGINT         NOT NULL,
    customer_id         BIGINT         NOT NULL,
    contract_id         BIGINT         NOT NULL,
    application_number  VARCHAR(100)   NOT NULL,
    active_application_number VARCHAR(100)
        GENERATED ALWAYS AS
        (CASE WHEN status <> 'DELETED' THEN application_number ELSE NULL END) STORED,
    amount              DECIMAL(22, 2) NOT NULL,
    requested_date      DATE           NULL,
    invoice_type        VARCHAR(50)    NOT NULL,
    title_type          VARCHAR(20)    NOT NULL,
    title               VARCHAR(200)   NOT NULL,
    tax_number          VARCHAR(100)   NULL,
    bank_name           VARCHAR(200)   NULL,
    bank_account        VARCHAR(100)   NULL,
    registered_address  VARCHAR(500)   NULL,
    registered_phone    VARCHAR(50)    NULL,
    contact_name        VARCHAR(100)   NULL,
    contact_phone       VARCHAR(50)    NULL,
    shipping_address    VARCHAR(500)   NULL,
    remark              VARCHAR(2000)  NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    invoice_number      VARCHAR(100)   NULL,
    active_invoice_number VARCHAR(100)
        GENERATED ALWAYS AS
        (CASE WHEN status = 'ISSUED' THEN invoice_number ELSE NULL END) STORED,
    issued_date         DATE           NULL,
    logistics_number   VARCHAR(100)   NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_time        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_invoice PRIMARY KEY (id),
    CONSTRAINT uk_crm_invoice_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_invoice_application
        UNIQUE (tenant_id, active_application_number),
    CONSTRAINT uk_crm_invoice_number
        UNIQUE (tenant_id, active_invoice_number),
    CONSTRAINT chk_crm_invoice_status
        CHECK (status IN ('PENDING', 'ISSUED', 'DELETED')),
    CONSTRAINT chk_crm_invoice_title_type
        CHECK (title_type IN ('ORGANIZATION', 'INDIVIDUAL')),
    CONSTRAINT chk_crm_invoice_amount CHECK (amount > 0),
    CONSTRAINT chk_crm_invoice_issue_state CHECK (
        (status = 'ISSUED' AND invoice_number IS NOT NULL AND issued_date IS NOT NULL)
        OR (status <> 'ISSUED' AND invoice_number IS NULL AND issued_date IS NULL
            AND logistics_number IS NULL)),
    CONSTRAINT chk_crm_invoice_organization_tax CHECK (
        title_type <> 'ORGANIZATION' OR tax_number IS NOT NULL),
    CONSTRAINT chk_crm_invoice_individual_fields CHECK (
        title_type <> 'INDIVIDUAL' OR
        (tax_number IS NULL AND bank_name IS NULL AND bank_account IS NULL
            AND registered_address IS NULL AND registered_phone IS NULL)),
    CONSTRAINT chk_crm_invoice_version CHECK (version >= 0),
    CONSTRAINT fk_crm_invoice_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_invoice_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_invoice_contract_customer
        FOREIGN KEY (tenant_id, contract_id, customer_id)
            REFERENCES crm_contract (tenant_id, id, customer_id)
            ON DELETE RESTRICT,

    INDEX idx_crm_invoice_tenant_status_updated
        (tenant_id, status, updated_time, id),
    INDEX idx_crm_invoice_tenant_contract
        (tenant_id, contract_id, status, id),
    INDEX idx_crm_invoice_tenant_customer
        (tenant_id, customer_id, status, id),
    INDEX idx_crm_invoice_tenant_owner
        (tenant_id, owner_actor_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_invoice_field_value
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    invoice_id   BIGINT       NOT NULL,
    object_type  VARCHAR(30)  NOT NULL DEFAULT 'INVOICE',
    field_id     BIGINT       NOT NULL,
    values_json  JSON         NOT NULL,
    scalar_value VARCHAR(500) NULL,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_invoice_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_invoice_field_value
        UNIQUE (tenant_id, invoice_id, field_id),
    CONSTRAINT chk_crm_invoice_field_value_object CHECK (object_type = 'INVOICE'),
    CONSTRAINT fk_crm_invoice_field_value_root
        FOREIGN KEY (tenant_id, invoice_id)
            REFERENCES crm_invoice (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_invoice_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT,
    INDEX idx_crm_invoice_field_value_search
        (tenant_id, field_id, scalar_value, invoice_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_invoice_unique_field_value
(
    tenant_id       BIGINT      NOT NULL,
    invoice_id      BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL DEFAULT 'INVOICE',
    field_id        BIGINT      NOT NULL,
    normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value TEXT       NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_invoice_unique_field_value
        PRIMARY KEY (tenant_id, invoice_id, field_id),
    CONSTRAINT uk_crm_invoice_unique_field_value
        UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_invoice_unique_field_value_object
        CHECK (object_type = 'INVOICE'),
    CONSTRAINT fk_crm_invoice_unique_field_value_root
        FOREIGN KEY (tenant_id, invoice_id)
            REFERENCES crm_invoice (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_invoice_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_invoice_profile
(
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT        NOT NULL,
    customer_id         BIGINT        NOT NULL,
    title_type          VARCHAR(20)   NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    tax_number          VARCHAR(100)  NULL,
    bank_name           VARCHAR(200)  NULL,
    bank_account        VARCHAR(100)  NULL,
    registered_address  VARCHAR(500)  NULL,
    registered_phone    VARCHAR(50)   NULL,
    remark              VARCHAR(1000) NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version             BIGINT        NOT NULL DEFAULT 0,
    created_time        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_invoice_profile PRIMARY KEY (id),
    CONSTRAINT uk_crm_invoice_profile_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_invoice_profile_title_type
        CHECK (title_type IN ('ORGANIZATION', 'INDIVIDUAL')),
    CONSTRAINT chk_crm_invoice_profile_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_invoice_profile_organization_tax CHECK (
        title_type <> 'ORGANIZATION' OR tax_number IS NOT NULL),
    CONSTRAINT chk_crm_invoice_profile_individual_fields CHECK (
        title_type <> 'INDIVIDUAL' OR
        (tax_number IS NULL AND bank_name IS NULL AND bank_account IS NULL
            AND registered_address IS NULL AND registered_phone IS NULL)),
    CONSTRAINT chk_crm_invoice_profile_version CHECK (version >= 0),
    CONSTRAINT fk_crm_invoice_profile_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    INDEX idx_crm_invoice_profile_customer
        (tenant_id, customer_id, status, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id, 'CONTRACT', definition.field_code, definition.display_name,
       NULL, definition.field_type, 1, definition.is_required, 0,
       definition.is_searchable, 'ACTIVE', definition.display_order,
       'Contract', definition.options_json, 0
FROM organization_tenant tenant
JOIN (
    SELECT 'contract_name' field_code, 'Contract name' display_name, 'TEXT' field_type,
           1 is_required, 1 is_searchable, 10 display_order, JSON_ARRAY() options_json
    UNION ALL SELECT 'contract_number', 'Contract number', 'TEXT', 1, 1, 20, JSON_ARRAY()
    UNION ALL SELECT 'customer', 'Customer', 'RELATION', 1, 1, 30, JSON_ARRAY()
    UNION ALL SELECT 'opportunity', 'Opportunity', 'RELATION', 0, 1, 40, JSON_ARRAY()
    UNION ALL SELECT 'contact', 'Contact', 'RELATION', 0, 1, 50, JSON_ARRAY()
    UNION ALL SELECT 'order_date', 'Order date', 'DATE', 0, 1, 60, JSON_ARRAY()
    UNION ALL SELECT 'start_date', 'Start date', 'DATE', 0, 1, 70, JSON_ARRAY()
    UNION ALL SELECT 'end_date', 'End date', 'DATE', 0, 1, 80, JSON_ARRAY()
    UNION ALL SELECT 'contract_type', 'Contract type', 'TEXT', 0, 1, 90, JSON_ARRAY()
    UNION ALL SELECT 'payment_type', 'Payment type', 'TEXT', 0, 1, 100, JSON_ARRAY()
    UNION ALL SELECT 'remark', 'Remark', 'TEXTAREA', 0, 1, 110, JSON_ARRAY()
    UNION ALL SELECT 'products', 'Products', 'RELATION', 0, 0, 120, JSON_ARRAY()
    UNION ALL SELECT 'subtotal', 'Product subtotal', 'DECIMAL', 0, 1, 130, JSON_ARRAY()
    UNION ALL SELECT 'discount_rate', 'Discount rate', 'DECIMAL', 0, 1, 140, JSON_ARRAY()
    UNION ALL SELECT 'total_amount', 'Contract amount', 'DECIMAL', 0, 1, 150, JSON_ARRAY()
    UNION ALL SELECT 'owner_actor', 'Owner', 'USER', 1, 1, 160, JSON_ARRAY()
    UNION ALL SELECT 'status', 'Status', 'SELECT', 0, 1, 170,
        JSON_ARRAY(JSON_OBJECT('value', 'ACTIVE', 'label', 'Active', 'order', 0),
                   JSON_OBJECT('value', 'VOIDED', 'label', 'Voided', 'order', 1))
    UNION ALL SELECT 'created_time', 'Created time', 'DATETIME', 0, 1, 180, JSON_ARRAY()
    UNION ALL SELECT 'updated_time', 'Updated time', 'DATETIME', 0, 1, 190, JSON_ARRAY()
) definition ON 1 = 1;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id, 'INVOICE', definition.field_code, definition.display_name,
       NULL, definition.field_type, 1, definition.is_required, 0,
       definition.is_searchable, 'ACTIVE', definition.display_order,
       'Invoice', definition.options_json, 0
FROM organization_tenant tenant
JOIN (
    SELECT 'application_number' field_code, 'Application number' display_name,
           'TEXT' field_type, 1 is_required, 1 is_searchable, 10 display_order,
           JSON_ARRAY() options_json
    UNION ALL SELECT 'customer', 'Customer', 'RELATION', 1, 1, 20, JSON_ARRAY()
    UNION ALL SELECT 'contract', 'Contract', 'RELATION', 1, 1, 30, JSON_ARRAY()
    UNION ALL SELECT 'amount', 'Invoice amount', 'DECIMAL', 1, 1, 40, JSON_ARRAY()
    UNION ALL SELECT 'requested_date', 'Requested date', 'DATE', 0, 1, 50, JSON_ARRAY()
    UNION ALL SELECT 'invoice_type', 'Invoice type', 'TEXT', 1, 1, 60, JSON_ARRAY()
    UNION ALL SELECT 'title_type', 'Title type', 'SELECT', 1, 1, 70,
        JSON_ARRAY(JSON_OBJECT('value', 'ORGANIZATION', 'label', 'Organization', 'order', 0),
                   JSON_OBJECT('value', 'INDIVIDUAL', 'label', 'Individual', 'order', 1))
    UNION ALL SELECT 'title', 'Title', 'TEXT', 1, 1, 80, JSON_ARRAY()
    UNION ALL SELECT 'tax_number', 'Tax number', 'TEXT', 0, 1, 90, JSON_ARRAY()
    UNION ALL SELECT 'bank_name', 'Bank name', 'TEXT', 0, 1, 100, JSON_ARRAY()
    UNION ALL SELECT 'bank_account', 'Bank account', 'TEXT', 0, 1, 110, JSON_ARRAY()
    UNION ALL SELECT 'registered_address', 'Registered address', 'ADDRESS', 0, 1, 120, JSON_ARRAY()
    UNION ALL SELECT 'registered_phone', 'Registered phone', 'PHONE', 0, 1, 130, JSON_ARRAY()
    UNION ALL SELECT 'contact_name', 'Contact name', 'TEXT', 0, 1, 140, JSON_ARRAY()
    UNION ALL SELECT 'contact_phone', 'Contact phone', 'PHONE', 0, 1, 150, JSON_ARRAY()
    UNION ALL SELECT 'shipping_address', 'Shipping address', 'ADDRESS', 0, 1, 160, JSON_ARRAY()
    UNION ALL SELECT 'remark', 'Remark', 'TEXTAREA', 0, 1, 170, JSON_ARRAY()
    UNION ALL SELECT 'owner_actor', 'Owner', 'USER', 1, 1, 180, JSON_ARRAY()
    UNION ALL SELECT 'status', 'Status', 'SELECT', 0, 1, 190,
        JSON_ARRAY(JSON_OBJECT('value', 'PENDING', 'label', 'Pending', 'order', 0),
                   JSON_OBJECT('value', 'ISSUED', 'label', 'Issued', 'order', 1))
    UNION ALL SELECT 'invoice_number', 'Invoice number', 'TEXT', 0, 1, 200, JSON_ARRAY()
    UNION ALL SELECT 'issued_date', 'Issued date', 'DATE', 0, 1, 210, JSON_ARRAY()
    UNION ALL SELECT 'logistics_number', 'Logistics number', 'TEXT', 0, 1, 220, JSON_ARRAY()
    UNION ALL SELECT 'created_time', 'Created time', 'DATETIME', 0, 1, 230, JSON_ARRAY()
    UNION ALL SELECT 'updated_time', 'Updated time', 'DATETIME', 0, 1, 240, JSON_ARRAY()
) definition ON 1 = 1;
