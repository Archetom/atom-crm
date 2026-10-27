CREATE TABLE crm_attachment_file
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT       NOT NULL,
    uploader_actor_id  BIGINT       NOT NULL,
    display_name       VARCHAR(512) NOT NULL,
    declared_media_type VARCHAR(127) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_bytes     BIGINT       NOT NULL,
    expected_sha256    BINARY(32)   NULL,
    byte_state         VARCHAR(20)  NOT NULL,
    inspection_state   VARCHAR(20)  NOT NULL,
    publication_state  VARCHAR(20)  NOT NULL,
    content_version    BIGINT       NOT NULL DEFAULT 1,
    actual_bytes       BIGINT       NOT NULL DEFAULT 0,
    media_type         VARCHAR(127) CHARACTER SET ascii COLLATE ascii_bin NULL,
    controlled_reason  VARCHAR(64)  NULL,
    available_time     DATETIME(6)  NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_file PRIMARY KEY (id),
    CONSTRAINT uk_crm_attachment_file_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_attachment_file_expected_bytes
        CHECK (expected_bytes BETWEEN 1 AND 16777216),
    CONSTRAINT chk_crm_attachment_file_actual_bytes CHECK (actual_bytes >= 0),
    CONSTRAINT chk_crm_attachment_file_version CHECK (version >= 0 AND content_version >= 1),
    CONSTRAINT chk_crm_attachment_file_byte_state
        CHECK (byte_state IN ('ABSENT', 'UPLOADING', 'STORED', 'REMOVING', 'REMOVED', 'UNKNOWN')),
    CONSTRAINT chk_crm_attachment_file_inspection_state
        CHECK (inspection_state IN ('NOT_RUN', 'PENDING', 'CLEAN', 'REJECTED', 'INDETERMINATE')),
    CONSTRAINT chk_crm_attachment_file_publication_state
        CHECK (publication_state IN ('PRIVATE', 'AVAILABLE', 'WITHDRAWN', 'DELETED')),
    CONSTRAINT chk_crm_attachment_file_available CHECK (
        publication_state <> 'AVAILABLE'
        OR (byte_state = 'STORED' AND inspection_state = 'CLEAN'
            AND actual_bytes > 0 AND media_type IS NOT NULL AND available_time IS NOT NULL)),
    CONSTRAINT chk_crm_attachment_file_removed CHECK (
        byte_state <> 'REMOVED' OR publication_state = 'DELETED'),
    CONSTRAINT fk_crm_attachment_file_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_file_uploader
        FOREIGN KEY (tenant_id, uploader_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_attachment_file_uploader
        (tenant_id, uploader_actor_id, publication_state, created_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_attachment_relation
(
    tenant_id      BIGINT      NOT NULL,
    file_id        BIGINT      NOT NULL,
    target_type    VARCHAR(30) NOT NULL,
    work_task_id   BIGINT      NOT NULL,
    relation_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT      NOT NULL DEFAULT 0,
    created_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_relation PRIMARY KEY (tenant_id, file_id),
    CONSTRAINT chk_crm_attachment_relation_target
        CHECK (target_type = 'WORK_TASK' AND work_task_id > 0),
    CONSTRAINT chk_crm_attachment_relation_state
        CHECK (relation_state IN ('ACTIVE', 'WITHDRAWN', 'DELETED')),
    CONSTRAINT chk_crm_attachment_relation_version CHECK (version >= 0),
    CONSTRAINT fk_crm_attachment_relation_file
        FOREIGN KEY (tenant_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_attachment_relation_work_task
        FOREIGN KEY (tenant_id, work_task_id)
            REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_attachment_relation_work
        (tenant_id, work_task_id, relation_state, file_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_attachment_content
(
    tenant_id               BIGINT        NOT NULL,
    file_id                 BIGINT        NOT NULL,
    content_version         BIGINT        NOT NULL,
    intent_version          BIGINT        NOT NULL,
    binding_ref             VARBINARY(256) NOT NULL,
    planned_locator         VARBINARY(512) NOT NULL,
    stored_identity         VARBINARY(512) NULL,
    storage_generation      VARBINARY(128) NULL,
    attempt                 BIGINT        NOT NULL DEFAULT 1,
    actual_bytes            BIGINT        NULL,
    sha256                  BINARY(32)    NULL,
    upload_deadline         DATETIME(6)   NOT NULL,
    stored_time             DATETIME(6)   NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_time            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_content PRIMARY KEY (tenant_id, file_id),
    CONSTRAINT chk_crm_attachment_content_version
        CHECK (content_version >= 1 AND intent_version >= 0 AND attempt >= 1 AND version >= 0),
    CONSTRAINT chk_crm_attachment_content_evidence CHECK (
        (stored_identity IS NULL AND actual_bytes IS NULL
            AND sha256 IS NULL AND stored_time IS NULL)
        OR (stored_identity IS NOT NULL AND storage_generation IS NOT NULL
            AND actual_bytes BETWEEN 1 AND 16777216 AND sha256 IS NOT NULL AND stored_time IS NOT NULL)),
    CONSTRAINT fk_crm_attachment_content_file
        FOREIGN KEY (tenant_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_attachment_job
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    content_version BIGINT      NOT NULL,
    kind            VARCHAR(32) NOT NULL,
    state           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    generation      BIGINT      NOT NULL DEFAULT 1,
    attempt         BIGINT      NOT NULL DEFAULT 0,
    not_before      DATETIME(6) NOT NULL,
    deadline        DATETIME(6) NOT NULL,
    lease_owner     VARBINARY(128) NULL,
    lease_fence     BIGINT      NOT NULL DEFAULT 0,
    lease_until     DATETIME(6) NULL,
    result_digest   BINARY(32)  NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_job PRIMARY KEY (id),
    CONSTRAINT uk_crm_attachment_job_identity
        UNIQUE (tenant_id, file_id, content_version, kind, generation),
    CONSTRAINT chk_crm_attachment_job_kind CHECK (kind IN (
        'UPLOAD_RECONCILIATION', 'INCOMPLETE_ABORT', 'INSPECTION',
        'PUBLICATION', 'DELETION', 'RETENTION_SWEEP')),
    CONSTRAINT chk_crm_attachment_job_state
        CHECK (state IN ('PENDING', 'LEASED', 'EFFECT_RECORDED', 'SETTLED', 'DEAD')),
    CONSTRAINT chk_crm_attachment_job_number
        CHECK (content_version >= 1 AND generation >= 1 AND attempt >= 0
            AND lease_fence >= 0 AND version >= 0),
    CONSTRAINT chk_crm_attachment_job_lease CHECK (
        (state = 'LEASED' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR (state <> 'LEASED' AND lease_owner IS NULL AND lease_until IS NULL)),
    CONSTRAINT fk_crm_attachment_job_file
        FOREIGN KEY (tenant_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_attachment_job_claim (state, not_before, id),
    INDEX idx_crm_attachment_job_file (tenant_id, file_id, content_version, kind, state)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_attachment_read_lease
(
    lease_id        BINARY(16)  NOT NULL,
    tenant_id       BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    content_version BIGINT      NOT NULL,
    lease_fence     BIGINT      NOT NULL,
    state           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    expires_at      DATETIME(6) NOT NULL,
    released_time   DATETIME(6) NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_read_lease PRIMARY KEY (lease_id),
    CONSTRAINT chk_crm_attachment_read_lease_state CHECK (state IN ('OPEN', 'RELEASED', 'CANCELLED')),
    CONSTRAINT chk_crm_attachment_read_lease_fence CHECK (content_version >= 1 AND lease_fence >= 0),
    CONSTRAINT chk_crm_attachment_read_lease_release CHECK (
        (state = 'OPEN' AND released_time IS NULL) OR (state <> 'OPEN' AND released_time IS NOT NULL)),
    CONSTRAINT fk_crm_attachment_read_lease_file
        FOREIGN KEY (tenant_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_attachment_read_lease_file (tenant_id, file_id, state, expires_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_attachment_tenant_usage
(
    tenant_id           BIGINT      NOT NULL,
    ready_bytes         BIGINT      NOT NULL DEFAULT 0,
    quarantine_bytes    BIGINT      NOT NULL DEFAULT 0,
    pending_bytes       BIGINT      NOT NULL DEFAULT 0,
    pending_intents     BIGINT      NOT NULL DEFAULT 0,
    active_uploads      BIGINT      NOT NULL DEFAULT 0,
    active_inspections  BIGINT      NOT NULL DEFAULT 0,
    active_downloads    BIGINT      NOT NULL DEFAULT 0,
    version             BIGINT      NOT NULL DEFAULT 0,
    updated_time        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_tenant_usage PRIMARY KEY (tenant_id),
    CONSTRAINT chk_crm_attachment_tenant_usage_nonnegative CHECK (
        ready_bytes >= 0 AND quarantine_bytes >= 0 AND pending_bytes >= 0
        AND pending_intents >= 0 AND active_uploads >= 0
        AND active_inspections >= 0 AND active_downloads >= 0 AND version >= 0),
    CONSTRAINT fk_crm_attachment_tenant_usage_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- A global reserve cannot be enforced by independently locked tenant rows.  Every attachment
-- reservation locks this durable singleton before its tenant counter, so all replicas share the
-- same byte-capacity decision without a JVM-local coordinator.
CREATE TABLE crm_attachment_global_usage
(
    singleton_id        TINYINT     NOT NULL,
    ready_bytes         BIGINT      NOT NULL DEFAULT 0,
    quarantine_bytes    BIGINT      NOT NULL DEFAULT 0,
    pending_bytes       BIGINT      NOT NULL DEFAULT 0,
    version             BIGINT      NOT NULL DEFAULT 0,
    updated_time        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_attachment_global_usage PRIMARY KEY (singleton_id),
    CONSTRAINT chk_crm_attachment_global_usage_singleton CHECK (singleton_id = 1),
    CONSTRAINT chk_crm_attachment_global_usage_nonnegative CHECK (
        ready_bytes >= 0 AND quarantine_bytes >= 0 AND pending_bytes >= 0 AND version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
