CREATE TABLE report_definition
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT       NOT NULL,
    creator_member_id     BIGINT       NOT NULL,
    name                  VARCHAR(400) NOT NULL,
    normalized_name       TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    normalized_name_hash  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (SHA2(normalized_name, 256)) STORED,
    description           VARCHAR(2000) NULL,
    object_type           VARCHAR(30)  NOT NULL,
    time_field_code       VARCHAR(64)  NOT NULL,
    granularity           VARCHAR(10)  NOT NULL,
    dimension_field_code  VARCHAR(64)  NULL,
    filters_json          JSON         NOT NULL,
    measures_json         JSON         NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    active_normalized_name_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN SHA2(normalized_name, 256) ELSE NULL END) STORED,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_time          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_report_definition PRIMARY KEY (id),
    CONSTRAINT uk_report_definition_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_report_definition_active_name
        UNIQUE (tenant_id, creator_member_id, object_type, active_normalized_name_hash),
    CONSTRAINT chk_report_definition_object
        CHECK (object_type IN ('OPPORTUNITY', 'CONTRACT', 'RECEIVABLE')),
    CONSTRAINT chk_report_definition_granularity
        CHECK (granularity IN ('DAY', 'WEEK', 'MONTH')),
    CONSTRAINT chk_report_definition_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_report_definition_version CHECK (version >= 0),
    CONSTRAINT fk_report_definition_creator
        FOREIGN KEY (tenant_id, creator_member_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_report_definition_member_list
        (tenant_id, creator_member_id, status, object_type, updated_time, id),
    INDEX idx_report_definition_object_dependency
        (tenant_id, object_type, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
