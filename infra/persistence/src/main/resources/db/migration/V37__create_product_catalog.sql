CREATE TABLE crm_product_catalog_guard
(
    tenant_id    BIGINT      NOT NULL,
    updated_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_product_catalog_guard PRIMARY KEY (tenant_id),
    CONSTRAINT fk_crm_product_catalog_guard_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_product_category
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT       NOT NULL,
    parent_category_id BIGINT       NULL,
    parent_slot        BIGINT GENERATED ALWAYS AS
        (COALESCE(parent_category_id, 0)) STORED,
    name               VARCHAR(200) NOT NULL,
    name_key           VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    active_name_key    VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN name_key ELSE NULL END) STORED,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_product_category PRIMARY KEY (id),
    CONSTRAINT uk_crm_product_category_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_product_category_active_name
        UNIQUE (tenant_id, parent_slot, active_name_key),
    CONSTRAINT chk_crm_product_category_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_product_category_version CHECK (version >= 0),
    CONSTRAINT fk_crm_product_category_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_product_category_parent
        FOREIGN KEY (tenant_id, parent_category_id)
            REFERENCES crm_product_category (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_product_category_parent
        (tenant_id, parent_category_id, status, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_product
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    category_id     BIGINT         NULL,
    owner_actor_id  BIGINT         NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    code            VARCHAR(100)   NOT NULL,
    code_key        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    active_code_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS
        (CASE WHEN status <> 'DELETED' THEN code_key ELSE NULL END) STORED,
    unit            VARCHAR(50)    NULL,
    list_price      DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    description     VARCHAR(2000)  NULL,
    availability    VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    version         BIGINT         NOT NULL DEFAULT 0,
    created_time    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_product PRIMARY KEY (id),
    CONSTRAINT uk_crm_product_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_product_active_code UNIQUE (tenant_id, active_code_key),
    CONSTRAINT chk_crm_product_availability
        CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT chk_crm_product_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_product_price CHECK (list_price >= 0),
    CONSTRAINT chk_crm_product_version CHECK (version >= 0),
    CONSTRAINT fk_crm_product_category
        FOREIGN KEY (tenant_id, category_id)
            REFERENCES crm_product_category (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_product_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_crm_product_tenant_status_updated
        (tenant_id, status, updated_time, id),
    INDEX idx_crm_product_tenant_category
        (tenant_id, category_id, status, availability, id),
    INDEX idx_crm_product_tenant_owner
        (tenant_id, owner_actor_id, status),
    INDEX idx_crm_product_tenant_name
        (tenant_id, name, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_product_field_value
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    object_type  VARCHAR(30)  NOT NULL DEFAULT 'PRODUCT',
    field_id     BIGINT       NOT NULL,
    values_json  JSON         NOT NULL,
    scalar_value VARCHAR(500) NULL,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_product_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_product_field_value
        UNIQUE (tenant_id, product_id, field_id),
    CONSTRAINT chk_crm_product_field_value_object CHECK (object_type = 'PRODUCT'),
    CONSTRAINT fk_crm_product_field_value_root
        FOREIGN KEY (tenant_id, product_id)
            REFERENCES crm_product (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_product_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_product_field_value_search
        (tenant_id, field_id, scalar_value, product_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_product_unique_field_value
(
    tenant_id       BIGINT      NOT NULL,
    product_id      BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL DEFAULT 'PRODUCT',
    field_id        BIGINT      NOT NULL,
    normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value TEXT       NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_product_unique_field_value
        PRIMARY KEY (tenant_id, product_id, field_id),
    CONSTRAINT uk_crm_product_unique_field_value
        UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_product_unique_field_value_object
        CHECK (object_type = 'PRODUCT'),
    CONSTRAINT fk_crm_product_unique_field_value_root
        FOREIGN KEY (tenant_id, product_id)
            REFERENCES crm_product (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_product_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_opportunity_product_line
(
    tenant_id        BIGINT         NOT NULL,
    opportunity_id   BIGINT         NOT NULL,
    product_id       BIGINT         NOT NULL,
    product_name     VARCHAR(200)   NOT NULL,
    product_code     VARCHAR(100)   NOT NULL,
    category_name    VARCHAR(200)   NULL,
    unit             VARCHAR(50)    NULL,
    list_price       DECIMAL(18, 2) NOT NULL,
    unit_price       DECIMAL(18, 2) NOT NULL,
    quantity         DECIMAL(18, 4) NOT NULL,
    discount_rate    DECIMAL(9, 4)  NOT NULL,
    line_amount      DECIMAL(22, 2) NOT NULL,
    display_order    INT            NOT NULL,
    created_time     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_opportunity_product_line
        PRIMARY KEY (tenant_id, opportunity_id, product_id),
    CONSTRAINT uk_crm_opportunity_product_order
        UNIQUE (tenant_id, opportunity_id, display_order),
    CONSTRAINT chk_crm_opportunity_product_price
        CHECK (list_price >= 0 AND unit_price >= 0 AND line_amount >= 0),
    CONSTRAINT chk_crm_opportunity_product_quantity CHECK (quantity > 0),
    CONSTRAINT chk_crm_opportunity_product_discount
        CHECK (discount_rate >= 0 AND discount_rate <= 100),
    CONSTRAINT chk_crm_opportunity_product_order CHECK (display_order >= 0),
    CONSTRAINT fk_crm_opportunity_product_root
        FOREIGN KEY (tenant_id, opportunity_id)
            REFERENCES crm_opportunity (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_opportunity_product_target
        FOREIGN KEY (tenant_id, product_id)
            REFERENCES crm_product (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_opportunity_product_target
        (tenant_id, product_id, opportunity_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id,
       'PRODUCT',
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
       'Product',
       definition.options_json,
       0
FROM organization_tenant tenant
JOIN (
    SELECT 'product_name' AS field_code, 'Product name' AS display_name,
           'TEXT' AS field_type, 1 AS is_required, 1 AS is_searchable,
           10 AS display_order, JSON_ARRAY() AS options_json
    UNION ALL SELECT 'product_code', 'Product code', 'TEXT', 1, 1, 20, JSON_ARRAY()
    UNION ALL SELECT 'category', 'Category', 'RELATION', 0, 1, 30, JSON_ARRAY()
    UNION ALL SELECT 'unit', 'Unit', 'TEXT', 0, 1, 40, JSON_ARRAY()
    UNION ALL SELECT 'list_price', 'List price', 'DECIMAL', 1, 1, 50, JSON_ARRAY()
    UNION ALL SELECT 'description', 'Description', 'TEXTAREA', 0, 1, 60, JSON_ARRAY()
    UNION ALL SELECT 'owner_actor', 'Owner', 'USER', 1, 1, 70, JSON_ARRAY()
    UNION ALL SELECT 'availability', 'Availability', 'SELECT', 1, 1, 80,
        JSON_ARRAY(JSON_OBJECT('value', 'AVAILABLE', 'label', 'Available', 'order', 0),
                   JSON_OBJECT('value', 'UNAVAILABLE', 'label', 'Unavailable', 'order', 1))
    UNION ALL SELECT 'status', 'Status', 'SELECT', 0, 1, 90,
        JSON_ARRAY(JSON_OBJECT('value', 'ACTIVE', 'label', 'Active', 'order', 0))
    UNION ALL SELECT 'created_time', 'Created time', 'DATETIME', 0, 1, 100, JSON_ARRAY()
    UNION ALL SELECT 'updated_time', 'Updated time', 'DATETIME', 0, 1, 110, JSON_ARRAY()
) definition ON 1 = 1;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id, 'OPPORTUNITY', 'products', 'Products', NULL, 'RELATION',
       1, 0, 0, 0, 'ACTIVE', 175, 'Opportunity', JSON_ARRAY(), 0
FROM organization_tenant tenant;
