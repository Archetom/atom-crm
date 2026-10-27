CREATE TABLE crm_customer_guard
(
    tenant_id    BIGINT      NOT NULL,
    guard_key    TINYINT     NOT NULL DEFAULT 1,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_guard PRIMARY KEY (tenant_id),
    CONSTRAINT chk_crm_customer_guard_key CHECK (guard_key = 1)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT        NOT NULL,
    owner_actor_id     BIGINT        NULL,
    parent_customer_id BIGINT        NULL,
    name               VARCHAR(200)  NOT NULL,
    phone_number       VARCHAR(30)   NULL,
    telephone          VARCHAR(50)   NULL,
    email              VARCHAR(254)  NULL,
    website            VARCHAR(500)  NULL,
    description        VARCHAR(2000) NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    is_locked          TINYINT       NOT NULL DEFAULT 0,
    deal_status        VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    deal_time          DATETIME(6)   NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_time       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer PRIMARY KEY (id),
    CONSTRAINT uk_crm_customer_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_customer_status CHECK (status IN ('ACTIVE', 'IN_POOL', 'DELETED')),
    CONSTRAINT chk_crm_customer_locked CHECK (is_locked IN (0, 1)),
    CONSTRAINT chk_crm_customer_deal_status CHECK (deal_status IN ('OPEN', 'WON')),
    CONSTRAINT chk_crm_customer_active_owner
        CHECK (status <> 'ACTIVE' OR owner_actor_id IS NOT NULL),
    CONSTRAINT chk_crm_customer_version CHECK (version >= 0),
    CONSTRAINT fk_crm_customer_owner
        FOREIGN KEY (tenant_id, owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_parent
        FOREIGN KEY (tenant_id, parent_customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_customer_tenant_status_updated
        (tenant_id, status, updated_time, id),
    INDEX idx_crm_customer_tenant_owner
        (tenant_id, owner_actor_id, status),
    INDEX idx_crm_customer_tenant_parent
        (tenant_id, parent_customer_id, status),
    INDEX idx_crm_customer_tenant_name
        (tenant_id, name, status),
    INDEX idx_crm_customer_tenant_phone
        (tenant_id, phone_number, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_address
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT        NOT NULL,
    customer_id   BIGINT        NOT NULL,
    address_key   VARCHAR(64)   NOT NULL,
    label         VARCHAR(100)  NULL,
    country       VARCHAR(100)  NULL,
    region        VARCHAR(100)  NULL,
    city          VARCHAR(100)  NULL,
    postal_code   VARCHAR(30)   NULL,
    address_line  VARCHAR(500)  NOT NULL,
    longitude     DECIMAL(10,7) NULL,
    latitude      DECIMAL(10,7) NULL,
    is_primary    TINYINT       NOT NULL DEFAULT 0,
    primary_slot  TINYINT GENERATED ALWAYS AS
        (CASE WHEN is_primary = 1 THEN 1 ELSE NULL END) STORED,
    created_time  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_address PRIMARY KEY (id),
    CONSTRAINT uk_crm_customer_address_key
        UNIQUE (tenant_id, customer_id, address_key),
    CONSTRAINT uk_crm_customer_address_primary
        UNIQUE (tenant_id, customer_id, primary_slot),
    CONSTRAINT chk_crm_customer_address_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_crm_customer_address_coordinates
        CHECK ((longitude IS NULL AND latitude IS NULL)
            OR (longitude BETWEEN -180 AND 180 AND latitude BETWEEN -90 AND 90)),
    CONSTRAINT fk_crm_customer_address_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_customer_address_coordinates
        (tenant_id, latitude, longitude, customer_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_tag
(
    tenant_id   BIGINT      NOT NULL,
    customer_id BIGINT      NOT NULL,
    tag_name    VARCHAR(50) NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_tag PRIMARY KEY (tenant_id, customer_id, tag_name),
    CONSTRAINT fk_crm_customer_tag_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_customer_tag_catalog (tenant_id, tag_name, customer_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_collaborator
(
    tenant_id    BIGINT      NOT NULL,
    customer_id  BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_collaborator
        PRIMARY KEY (tenant_id, customer_id, actor_id),
    CONSTRAINT chk_crm_customer_collaborator_access
        CHECK (access_level IN ('READ', 'WRITE')),
    CONSTRAINT fk_crm_customer_collaborator_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_collaborator_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_crm_customer_collaborator_member
        (tenant_id, actor_id, access_level, customer_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_field_value
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    customer_id   BIGINT       NOT NULL,
    object_type   VARCHAR(30)  NOT NULL DEFAULT 'CUSTOMER',
    field_id      BIGINT       NOT NULL,
    values_json   JSON         NOT NULL,
    scalar_value  VARCHAR(500) NULL,
    created_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_field_value PRIMARY KEY (id),
    CONSTRAINT uk_crm_customer_field_value
        UNIQUE (tenant_id, customer_id, field_id),
    CONSTRAINT chk_crm_customer_field_value_object
        CHECK (object_type = 'CUSTOMER'),
    CONSTRAINT fk_crm_customer_field_value_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_customer_field_value_search
        (tenant_id, field_id, scalar_value, customer_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_unique_field_value
(
    tenant_id       BIGINT       NOT NULL,
    customer_id     BIGINT       NOT NULL,
    object_type     VARCHAR(30)  NOT NULL DEFAULT 'CUSTOMER',
    field_id        BIGINT       NOT NULL,
    normalized_hash CHAR(64)     NOT NULL,
    normalized_value VARCHAR(500) NOT NULL,
    created_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_unique_field_value
        PRIMARY KEY (tenant_id, customer_id, field_id),
    CONSTRAINT uk_crm_customer_unique_field_value
        UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_crm_customer_unique_field_value_object
        CHECK (object_type = 'CUSTOMER'),
    CONSTRAINT fk_crm_customer_unique_field_value_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_star
(
    tenant_id    BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    customer_id  BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_star PRIMARY KEY (tenant_id, actor_id, customer_id),
    CONSTRAINT fk_crm_customer_star_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_star_customer
        FOREIGN KEY (tenant_id, customer_id)
            REFERENCES crm_customer (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_crm_customer_star_customer (tenant_id, customer_id, actor_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id,
       'CUSTOMER',
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
       'Customer',
       definition.options_json,
       0
FROM organization_tenant tenant
JOIN (
    SELECT 'customer_name' AS field_code, 'Customer name' AS display_name,
           'TEXT' AS field_type, 1 AS is_required, 1 AS is_searchable, 10 AS display_order,
           JSON_ARRAY() AS options_json
    UNION ALL SELECT 'phone_number', 'Phone number', 'PHONE', 0, 1, 20, JSON_ARRAY()
    UNION ALL SELECT 'telephone', 'Telephone', 'TEXT', 0, 1, 30, JSON_ARRAY()
    UNION ALL SELECT 'email', 'Email', 'EMAIL', 0, 1, 40, JSON_ARRAY()
    UNION ALL SELECT 'website', 'Website', 'URL', 0, 1, 50, JSON_ARRAY()
    UNION ALL SELECT 'description', 'Description', 'TEXTAREA', 0, 1, 60, JSON_ARRAY()
    UNION ALL SELECT 'tags', 'Tags', 'TEXTAREA', 0, 0, 65, JSON_ARRAY()
    UNION ALL SELECT 'addresses', 'Addresses', 'ADDRESS', 0, 0, 66, JSON_ARRAY()
    UNION ALL SELECT 'owner_actor', 'Owner', 'USER', 1, 1, 70, JSON_ARRAY()
    UNION ALL SELECT 'parent_customer', 'Parent customer', 'RELATION', 0, 1, 80, JSON_ARRAY()
    UNION ALL SELECT 'locked', 'Locked', 'BOOLEAN', 0, 1, 90, JSON_ARRAY()
    UNION ALL SELECT 'deal_status', 'Deal status', 'SELECT', 0, 1, 100,
        JSON_ARRAY(JSON_OBJECT('value', 'OPEN', 'label', 'Open', 'order', 0),
                   JSON_OBJECT('value', 'WON', 'label', 'Won', 'order', 1))
    UNION ALL SELECT 'status', 'Status', 'SELECT', 0, 1, 110,
        JSON_ARRAY(JSON_OBJECT('value', 'ACTIVE', 'label', 'Active', 'order', 0),
                   JSON_OBJECT('value', 'IN_POOL', 'label', 'In pool', 'order', 1))
    UNION ALL SELECT 'created_time', 'Created time', 'DATETIME', 0, 1, 120, JSON_ARRAY()
    UNION ALL SELECT 'updated_time', 'Updated time', 'DATETIME', 0, 1, 130, JSON_ARRAY()
) definition ON 1 = 1;
