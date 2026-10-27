CREATE TABLE custom_field_definition
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT       NOT NULL,
    object_type    VARCHAR(30)  NOT NULL,
    field_code     VARCHAR(64)  NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    description    VARCHAR(500) NULL,
    field_type     VARCHAR(30)  NOT NULL,
    is_builtin     TINYINT      NOT NULL DEFAULT 0,
    is_required    TINYINT      NOT NULL DEFAULT 0,
    is_unique      TINYINT      NOT NULL DEFAULT 0,
    is_searchable  TINYINT      NOT NULL DEFAULT 1,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order  INT          NOT NULL DEFAULT 0,
    section_name   VARCHAR(100) NULL,
    options_json   JSON         NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_time   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_custom_field_definition PRIMARY KEY (id),
    CONSTRAINT uk_custom_field_definition_code
        UNIQUE (tenant_id, object_type, field_code),
    CONSTRAINT uk_custom_field_definition_tenant_object_id
        UNIQUE (tenant_id, object_type, id),
    CONSTRAINT chk_custom_field_definition_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_custom_field_definition_flags
        CHECK (is_builtin IN (0, 1) AND is_required IN (0, 1)
            AND is_unique IN (0, 1) AND is_searchable IN (0, 1)),
    CONSTRAINT chk_custom_field_definition_order
        CHECK (display_order >= 0 AND display_order <= 10000),
    CONSTRAINT chk_custom_field_definition_version CHECK (version >= 0),

    INDEX idx_custom_field_definition_list
        (tenant_id, object_type, status, display_order, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE field_layout_preference
(
    tenant_id      BIGINT      NOT NULL,
    actor_id       BIGINT      NOT NULL,
    object_type    VARCHAR(30) NOT NULL,
    ordered_codes  JSON        NOT NULL,
    hidden_codes   JSON        NOT NULL,
    pinned_codes   JSON        NOT NULL,
    column_widths   JSON        NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_field_layout_preference
        PRIMARY KEY (tenant_id, actor_id, object_type),
    CONSTRAINT chk_field_layout_preference_version CHECK (version >= 0),
    CONSTRAINT fk_field_layout_preference_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE role_field_policy
(
    tenant_id    BIGINT      NOT NULL,
    role_id      BIGINT      NOT NULL,
    object_type  VARCHAR(30) NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_role_field_policy PRIMARY KEY (tenant_id, role_id, object_type),
    CONSTRAINT chk_role_field_policy_version CHECK (version >= 0),
    CONSTRAINT fk_role_field_policy_role
        FOREIGN KEY (tenant_id, role_id)
            REFERENCES authorization_role (tenant_id, id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE role_field_access
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    role_id       BIGINT      NOT NULL,
    object_type   VARCHAR(30) NOT NULL,
    field_id      BIGINT      NOT NULL,
    access_level  VARCHAR(20) NOT NULL,
    mask_type     VARCHAR(20) NOT NULL DEFAULT 'NONE',
    created_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_role_field_access PRIMARY KEY (id),
    CONSTRAINT uk_role_field_access_field
        UNIQUE (tenant_id, role_id, object_type, field_id),
    CONSTRAINT chk_role_field_access_level
        CHECK (access_level IN ('READ', 'WRITE')),
    CONSTRAINT chk_role_field_access_mask
        CHECK (mask_type IN ('NONE', 'PARTIAL', 'FULL')),
    CONSTRAINT fk_role_field_access_policy
        FOREIGN KEY (tenant_id, role_id, object_type)
            REFERENCES role_field_policy (tenant_id, role_id, object_type)
            ON DELETE RESTRICT,
    CONSTRAINT fk_role_field_access_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id)
            ON DELETE RESTRICT,

    INDEX idx_role_field_access_role (tenant_id, role_id, object_type)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE saved_view
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    actor_id      BIGINT       NOT NULL,
    object_type   VARCHAR(30)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    conditions_json JSON       NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    is_hidden     TINYINT      NOT NULL DEFAULT 0,
    is_default    TINYINT      NOT NULL DEFAULT 0,
    default_slot  TINYINT GENERATED ALWAYS AS
        (CASE WHEN is_default = 1 THEN 1 ELSE NULL END) STORED,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_saved_view PRIMARY KEY (id),
    CONSTRAINT uk_saved_view_name
        UNIQUE (tenant_id, actor_id, object_type, name),
    CONSTRAINT uk_saved_view_default
        UNIQUE (tenant_id, actor_id, object_type, default_slot),
    CONSTRAINT uk_saved_view_tenant_actor_id
        UNIQUE (tenant_id, actor_id, id),
    CONSTRAINT chk_saved_view_flags CHECK (is_hidden IN (0, 1) AND is_default IN (0, 1)),
    CONSTRAINT chk_saved_view_order CHECK (sort_order >= 0 AND sort_order <= 10000),
    CONSTRAINT chk_saved_view_version CHECK (version >= 0),
    CONSTRAINT fk_saved_view_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_saved_view_list (tenant_id, actor_id, object_type, is_hidden, sort_order, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE embedded_page
(
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT        NOT NULL,
    created_by_actor_id BIGINT        NOT NULL,
    object_type         VARCHAR(30)   NOT NULL,
    platform            VARCHAR(20)   NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    description         VARCHAR(500)  NULL,
    url_template        VARCHAR(1000) NOT NULL,
    sort_order          INT           NOT NULL DEFAULT 0,
    status              VARCHAR(20)   NOT NULL DEFAULT 'INACTIVE',
    version             BIGINT        NOT NULL DEFAULT 0,
    created_time        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_embedded_page PRIMARY KEY (id),
    CONSTRAINT uk_embedded_page_name
        UNIQUE (tenant_id, object_type, platform, name),
    CONSTRAINT chk_embedded_page_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_embedded_page_order CHECK (sort_order >= 0 AND sort_order <= 10000),
    CONSTRAINT chk_embedded_page_version CHECK (version >= 0),
    CONSTRAINT fk_embedded_page_creator
        FOREIGN KEY (tenant_id, created_by_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_embedded_page_list
        (tenant_id, object_type, platform, status, sort_order, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
