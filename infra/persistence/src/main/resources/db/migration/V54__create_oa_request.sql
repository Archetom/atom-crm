ALTER TABLE approval_instance
    ADD CONSTRAINT uk_approval_instance_oa_subject
        UNIQUE (tenant_id, id, object_type, subject_id);

CREATE TABLE oa_request
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT       NOT NULL,
    form_id                 BIGINT       NOT NULL,
    form_version            BIGINT       NOT NULL,
    form_code               VARCHAR(200) NOT NULL,
    request_title           VARCHAR(200) NOT NULL,
    applicant_actor_id      BIGINT       NOT NULL,
    applicant_department_id BIGINT       NULL,
    approval_instance_id    BIGINT       NULL,
    object_type             VARCHAR(30)  NOT NULL DEFAULT 'OA_REQUEST',
    idempotency_key         VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submission_digest       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submitted_time          DATETIME(6)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_time            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_oa_request PRIMARY KEY (id),
    CONSTRAINT uk_oa_request_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_oa_request_idempotency
        UNIQUE (tenant_id, applicant_actor_id, idempotency_key),
    CONSTRAINT uk_oa_request_approval UNIQUE (tenant_id, approval_instance_id),
    CONSTRAINT chk_oa_request_object CHECK (object_type = 'OA_REQUEST'),
    CONSTRAINT chk_oa_request_versions CHECK (form_version >= 0 AND version >= 0),
    CONSTRAINT chk_oa_request_digest
        CHECK (submission_digest REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT fk_oa_request_form FOREIGN KEY (tenant_id, form_id)
        REFERENCES oa_form_definition (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_request_applicant FOREIGN KEY (tenant_id, applicant_actor_id)
        REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_request_department FOREIGN KEY (tenant_id, applicant_department_id)
        REFERENCES organization_department (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_request_approval
        FOREIGN KEY (tenant_id, approval_instance_id, object_type, id)
        REFERENCES approval_instance (tenant_id, id, object_type, subject_id)
        ON DELETE RESTRICT,

    INDEX idx_oa_request_tenant_submitted
        (tenant_id, submitted_time, id),
    INDEX idx_oa_request_tenant_applicant
        (tenant_id, applicant_actor_id, submitted_time, id),
    INDEX idx_oa_request_tenant_form
        (tenant_id, form_id, submitted_time, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE oa_request_field_value
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    oa_request_id BIGINT       NOT NULL,
    object_type   VARCHAR(30)  NOT NULL DEFAULT 'OA_REQUEST',
    field_id      BIGINT       NOT NULL,
    values_json   JSON         NOT NULL,
    scalar_value  VARCHAR(500) NULL,
    created_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_oa_request_field_value PRIMARY KEY (id),
    CONSTRAINT uk_oa_request_field_value
        UNIQUE (tenant_id, oa_request_id, field_id),
    CONSTRAINT chk_oa_request_field_value_object CHECK (object_type = 'OA_REQUEST'),
    CONSTRAINT fk_oa_request_field_value_root
        FOREIGN KEY (tenant_id, oa_request_id)
        REFERENCES oa_request (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_request_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
        REFERENCES custom_field_definition (tenant_id, object_type, id)
        ON DELETE RESTRICT,

    INDEX idx_oa_request_field_value_search
        (tenant_id, field_id, scalar_value, oa_request_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE oa_request_unique_field_value
(
    tenant_id       BIGINT      NOT NULL,
    oa_request_id   BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL DEFAULT 'OA_REQUEST',
    field_id        BIGINT      NOT NULL,
    normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_value TEXT       NOT NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_oa_request_unique_field_value
        PRIMARY KEY (tenant_id, oa_request_id, field_id),
    CONSTRAINT uk_oa_request_unique_field_value
        UNIQUE (tenant_id, field_id, normalized_hash),
    CONSTRAINT chk_oa_request_unique_field_value_object
        CHECK (object_type = 'OA_REQUEST'),
    CONSTRAINT fk_oa_request_unique_field_value_root
        FOREIGN KEY (tenant_id, oa_request_id)
        REFERENCES oa_request (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_request_unique_field_value_field
        FOREIGN KEY (tenant_id, object_type, field_id)
        REFERENCES custom_field_definition (tenant_id, object_type, id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
