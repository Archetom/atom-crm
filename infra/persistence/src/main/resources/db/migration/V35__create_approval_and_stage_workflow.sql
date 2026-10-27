CREATE TABLE approval_definition
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT       NOT NULL,
    object_type     VARCHAR(30)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'INACTIVE',
    live_name       VARCHAR(200) GENERATED ALWAYS AS
        (CASE WHEN status <> 'DELETED' THEN normalized_name ELSE NULL END) STORED,
    description     VARCHAR(1000) NULL,
    repeat_mode     VARCHAR(20)  NOT NULL DEFAULT 'ONCE',
    config_json     JSON         NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_approval_definition PRIMARY KEY (id),
    CONSTRAINT uk_approval_definition_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_approval_definition_live_name
        UNIQUE (tenant_id, object_type, live_name),
    CONSTRAINT chk_approval_definition_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_approval_definition_repeat
        CHECK (repeat_mode IN ('ONCE', 'EVERY_STEP')),
    CONSTRAINT chk_approval_definition_version CHECK (version >= 0),
    CONSTRAINT fk_approval_definition_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE RESTRICT,

    INDEX idx_approval_definition_tenant_object_status
        (tenant_id, object_type, status, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE approval_instance
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id            BIGINT       NOT NULL,
    definition_id        BIGINT       NOT NULL,
    definition_version   BIGINT       NOT NULL,
    object_type          VARCHAR(30)  NOT NULL,
    subject_id           BIGINT       NOT NULL,
    idempotency_key      VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title                VARCHAR(200) NOT NULL,
    applicant_actor_id   BIGINT       NOT NULL,
    repeat_mode          VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    current_step_order   INT          NULL,
    facts_json           JSON         NOT NULL,
    submitted_time       DATETIME(6)  NOT NULL,
    completed_time       DATETIME(6)  NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_time         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_approval_instance PRIMARY KEY (id),
    CONSTRAINT uk_approval_instance_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_approval_instance_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_approval_instance_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')),
    CONSTRAINT chk_approval_instance_repeat
        CHECK (repeat_mode IN ('ONCE', 'EVERY_STEP')),
    CONSTRAINT chk_approval_instance_subject CHECK (subject_id > 0),
    CONSTRAINT chk_approval_instance_definition_version CHECK (definition_version >= 0),
    CONSTRAINT chk_approval_instance_version CHECK (version >= 0),
    CONSTRAINT chk_approval_instance_runtime
        CHECK ((status = 'PENDING' AND current_step_order IS NOT NULL AND completed_time IS NULL)
            OR (status <> 'PENDING' AND current_step_order IS NULL AND completed_time IS NOT NULL)),
    CONSTRAINT fk_approval_instance_definition
        FOREIGN KEY (tenant_id, definition_id)
            REFERENCES approval_definition (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_approval_instance_applicant
        FOREIGN KEY (tenant_id, applicant_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_approval_instance_tenant_status
        (tenant_id, status, updated_time, id),
    INDEX idx_approval_instance_tenant_applicant
        (tenant_id, applicant_actor_id, updated_time, id),
    INDEX idx_approval_instance_tenant_subject
        (tenant_id, object_type, subject_id, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE approval_instance_watcher
(
    tenant_id    BIGINT      NOT NULL,
    instance_id  BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_approval_instance_watcher
        PRIMARY KEY (tenant_id, instance_id, actor_id),
    CONSTRAINT fk_approval_instance_watcher_root
        FOREIGN KEY (tenant_id, instance_id)
            REFERENCES approval_instance (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_approval_instance_watcher_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_approval_instance_watcher_member
        (tenant_id, actor_id, instance_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE approval_review_task
(
    tenant_id          BIGINT      NOT NULL,
    task_key           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    instance_id        BIGINT      NOT NULL,
    source_task_key    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    step_code          VARCHAR(64) NOT NULL,
    step_name          VARCHAR(100) NOT NULL,
    step_order         INT         NOT NULL,
    approval_mode      VARCHAR(20) NOT NULL,
    actor_order        INT         NOT NULL,
    assigned_actor_id  BIGINT      NOT NULL,
    status             VARCHAR(20) NOT NULL,
    remark             VARCHAR(1000) NULL,
    acted_time         DATETIME(6) NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_approval_review_task PRIMARY KEY (tenant_id, task_key),
    CONSTRAINT chk_approval_review_task_mode
        CHECK (approval_mode IN ('SEQUENTIAL', 'ALL', 'ANY')),
    CONSTRAINT chk_approval_review_task_status
        CHECK (status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'TRANSFERRED', 'SKIPPED')),
    CONSTRAINT chk_approval_review_task_order CHECK (step_order >= 0 AND actor_order >= 0),
    CONSTRAINT chk_approval_review_task_version CHECK (version >= 0),
    CONSTRAINT chk_approval_review_task_action
        CHECK ((status IN ('WAITING', 'PENDING') AND acted_time IS NULL)
            OR (status NOT IN ('WAITING', 'PENDING') AND acted_time IS NOT NULL)),
    CONSTRAINT fk_approval_review_task_root
        FOREIGN KEY (tenant_id, instance_id)
            REFERENCES approval_instance (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_approval_review_task_member
        FOREIGN KEY (tenant_id, assigned_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_approval_review_task_source
        FOREIGN KEY (tenant_id, source_task_key)
            REFERENCES approval_review_task (tenant_id, task_key)
            ON DELETE RESTRICT,

    INDEX idx_approval_review_task_actor_status
        (tenant_id, assigned_actor_id, status, updated_time, instance_id),
    INDEX idx_approval_review_task_instance
        (tenant_id, instance_id, step_order, actor_order, created_time)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE stage_workflow_definition
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT       NOT NULL,
    object_type     VARCHAR(30)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'INACTIVE',
    live_name       VARCHAR(200) GENERATED ALWAYS AS
        (CASE WHEN status <> 'DELETED' THEN normalized_name ELSE NULL END) STORED,
    success_name    VARCHAR(100) NOT NULL,
    failure_name    VARCHAR(100) NOT NULL,
    config_json     JSON         NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_stage_workflow_definition PRIMARY KEY (id),
    CONSTRAINT uk_stage_workflow_definition_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_stage_workflow_definition_live_name
        UNIQUE (tenant_id, object_type, live_name),
    CONSTRAINT chk_stage_workflow_definition_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_stage_workflow_definition_version CHECK (version >= 0),
    CONSTRAINT fk_stage_workflow_definition_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE RESTRICT,

    INDEX idx_stage_workflow_definition_tenant_object_status
        (tenant_id, object_type, status, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE stage_workflow_record
(
    id                           BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id                    BIGINT      NOT NULL,
    workflow_id                  BIGINT      NOT NULL,
    workflow_version             BIGINT      NOT NULL,
    object_type                  VARCHAR(30) NOT NULL,
    subject_id                   BIGINT      NOT NULL,
    current_stage_code           VARCHAR(64) NULL,
    status                       VARCHAR(30) NOT NULL,
    pending_approval_instance_id BIGINT      NULL,
    progress_json                JSON        NOT NULL,
    outcome_remark               VARCHAR(1000) NULL,
    completed_time               DATETIME(6) NULL,
    version                      BIGINT      NOT NULL DEFAULT 0,
    created_time                 DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time                 DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_stage_workflow_record PRIMARY KEY (id),
    CONSTRAINT uk_stage_workflow_record_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_stage_workflow_record_subject
        UNIQUE (tenant_id, object_type, subject_id),
    CONSTRAINT chk_stage_workflow_record_status
        CHECK (status IN ('ACTIVE', 'AWAITING_APPROVAL', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_stage_workflow_record_subject_id CHECK (subject_id > 0),
    CONSTRAINT chk_stage_workflow_record_versions
        CHECK (workflow_version >= 0 AND version >= 0),
    CONSTRAINT chk_stage_workflow_record_runtime
        CHECK ((status = 'ACTIVE' AND current_stage_code IS NOT NULL
                   AND pending_approval_instance_id IS NULL AND completed_time IS NULL)
            OR (status = 'AWAITING_APPROVAL' AND current_stage_code IS NOT NULL
                   AND pending_approval_instance_id IS NOT NULL AND completed_time IS NULL)
            OR (status IN ('SUCCEEDED', 'FAILED') AND current_stage_code IS NULL
                   AND pending_approval_instance_id IS NULL AND completed_time IS NOT NULL)),
    CONSTRAINT fk_stage_workflow_record_definition
        FOREIGN KEY (tenant_id, workflow_id)
            REFERENCES stage_workflow_definition (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_stage_workflow_record_approval
        FOREIGN KEY (tenant_id, pending_approval_instance_id)
            REFERENCES approval_instance (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_stage_workflow_record_workflow
        (tenant_id, workflow_id, status, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE stage_workflow_comment
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT        NOT NULL,
    workflow_record_id BIGINT        NOT NULL,
    author_actor_id    BIGINT        NOT NULL,
    content            VARCHAR(2000) NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version            BIGINT        NOT NULL DEFAULT 0,
    created_time       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_stage_workflow_comment PRIMARY KEY (id),
    CONSTRAINT chk_stage_workflow_comment_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_stage_workflow_comment_version CHECK (version >= 0),
    CONSTRAINT fk_stage_workflow_comment_record
        FOREIGN KEY (tenant_id, workflow_record_id)
            REFERENCES stage_workflow_record (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_stage_workflow_comment_author
        FOREIGN KEY (tenant_id, author_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_stage_workflow_comment_record
        (tenant_id, workflow_record_id, status, created_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
