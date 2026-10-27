-- B20a OA request field catalog and governed form definitions.
CREATE TABLE oa_form_definition
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id              BIGINT       NOT NULL,
    created_by_actor_id    BIGINT       NOT NULL,
    code                   VARCHAR(100) NOT NULL,
    normalized_code        VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    name                   VARCHAR(200) NOT NULL,
    description            VARCHAR(2000) NULL,
    approval_definition_id BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'INACTIVE',
    live_normalized_code   VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS
            (CASE WHEN status <> 'DELETED' THEN normalized_code ELSE NULL END) STORED,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_time           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_oa_form_definition PRIMARY KEY (id),
    CONSTRAINT uk_oa_form_definition_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_oa_form_definition_live_code
        UNIQUE (tenant_id, live_normalized_code),
    CONSTRAINT chk_oa_form_definition_status
        CHECK (status IN ('INACTIVE', 'ACTIVE', 'DELETED')),
    CONSTRAINT chk_oa_form_definition_version CHECK (version >= 0),
    CONSTRAINT fk_oa_form_definition_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_oa_form_definition_creator
        FOREIGN KEY (tenant_id, created_by_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_oa_form_definition_approval
        FOREIGN KEY (tenant_id, approval_definition_id)
            REFERENCES approval_definition (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_oa_form_definition_list (tenant_id, status, updated_time, id),
    INDEX idx_oa_form_definition_approval
        (tenant_id, approval_definition_id, status, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE oa_form_field
(
    tenant_id   BIGINT      NOT NULL,
    oa_form_id  BIGINT      NOT NULL,
    object_type VARCHAR(30) NOT NULL DEFAULT 'OA_REQUEST',
    field_code  VARCHAR(64) NOT NULL,
    is_required TINYINT     NOT NULL DEFAULT 0,
    sort_order  INT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_oa_form_field
        PRIMARY KEY (tenant_id, oa_form_id, field_code),
    CONSTRAINT chk_oa_form_field_object CHECK (object_type = 'OA_REQUEST'),
    CONSTRAINT chk_oa_form_field_required CHECK (is_required IN (0, 1)),
    CONSTRAINT chk_oa_form_field_order CHECK (sort_order >= 0 AND sort_order <= 10000),
    CONSTRAINT fk_oa_form_field_form
        FOREIGN KEY (tenant_id, oa_form_id)
            REFERENCES oa_form_definition (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_form_field_definition
        FOREIGN KEY (tenant_id, object_type, field_code)
            REFERENCES custom_field_definition (tenant_id, object_type, field_code)
            ON DELETE RESTRICT,

    INDEX idx_oa_form_field_reference (tenant_id, object_type, field_code, oa_form_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO custom_field_definition
    (tenant_id, object_type, field_code, display_name, description, field_type,
     is_builtin, is_required, is_unique, is_searchable, status, display_order,
     section_name, options_json, version)
SELECT tenant.tenant_id, definition.object_type, definition.field_code,
       definition.display_name, NULL, definition.field_type, 1,
       definition.is_required, 0, 1, 'ACTIVE', definition.display_order,
       'OA request', JSON_ARRAY(), 0
FROM organization_tenant tenant
JOIN (
    SELECT 'OA_REQUEST' object_type, 'request_title' field_code,
           'Request title' display_name, 'TEXT' field_type,
           1 is_required, 10 display_order
    UNION ALL SELECT 'OA_REQUEST', 'form_code', 'Form code', 'TEXT', 1, 20
    UNION ALL SELECT 'OA_REQUEST', 'applicant_actor', 'Applicant', 'USER', 1, 30
    UNION ALL SELECT 'OA_REQUEST', 'applicant_department',
                     'Applicant department', 'DEPARTMENT', 0, 40
    UNION ALL SELECT 'OA_REQUEST', 'submitted_time',
                     'Submitted time', 'DATETIME', 1, 50
) definition ON 1 = 1;
