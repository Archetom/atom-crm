ALTER TABLE crm_work_task
    ADD COLUMN notification_projection_version BIGINT NOT NULL DEFAULT 0
        AFTER worklist_projection_version,
    ADD CONSTRAINT chk_crm_work_task_notification_projection_version
        CHECK (notification_projection_version >= 0);

CREATE TABLE crm_work_task_reminder_step
(
    tenant_id         BIGINT       NOT NULL,
    work_task_id      BIGINT       NOT NULL,
    step_key          VARCHAR(128) NOT NULL,
    timing            VARCHAR(16)  NOT NULL,
    offset_minutes    INT          NOT NULL,
    notify_assignee   TINYINT      NOT NULL DEFAULT 0,
    notify_creator    TINYINT      NOT NULL DEFAULT 0,
    notify_followers  TINYINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_work_task_reminder_step
        PRIMARY KEY (tenant_id, work_task_id, step_key),
    CONSTRAINT chk_crm_work_task_reminder_step_key
        CHECK (step_key REGEXP '^[A-Z0-9_]{3,128}$'),
    CONSTRAINT chk_crm_work_task_reminder_step_timing
        CHECK (timing IN ('BEFORE_DUE', 'AT_DUE', 'OVERDUE')),
    CONSTRAINT chk_crm_work_task_reminder_step_offset CHECK (
        (timing IN ('BEFORE_DUE', 'OVERDUE') AND offset_minutes BETWEEN 1 AND 43200)
        OR (timing = 'AT_DUE' AND offset_minutes = 0)),
    CONSTRAINT chk_crm_work_task_reminder_step_targets
        CHECK (notify_assignee + notify_creator + notify_followers >= 1),
    CONSTRAINT fk_crm_work_task_reminder_step_task
        FOREIGN KEY (tenant_id, work_task_id)
            REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_work_task_series_reminder_step
(
    tenant_id            BIGINT       NOT NULL,
    work_task_series_id  BIGINT       NOT NULL,
    step_key             VARCHAR(128) NOT NULL,
    timing               VARCHAR(16)  NOT NULL,
    offset_minutes       INT          NOT NULL,
    notify_assignee      TINYINT      NOT NULL DEFAULT 0,
    notify_creator       TINYINT      NOT NULL DEFAULT 0,
    notify_followers     TINYINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_work_task_series_reminder_step
        PRIMARY KEY (tenant_id, work_task_series_id, step_key),
    CONSTRAINT chk_crm_work_task_series_reminder_step_key
        CHECK (step_key REGEXP '^[A-Z0-9_]{3,128}$'),
    CONSTRAINT chk_crm_work_task_series_reminder_step_timing
        CHECK (timing IN ('BEFORE_DUE', 'AT_DUE', 'OVERDUE')),
    CONSTRAINT chk_crm_work_task_series_reminder_step_offset CHECK (
        (timing IN ('BEFORE_DUE', 'OVERDUE') AND offset_minutes BETWEEN 1 AND 43200)
        OR (timing = 'AT_DUE' AND offset_minutes = 0)),
    CONSTRAINT chk_crm_work_task_series_reminder_step_targets
        CHECK (notify_assignee + notify_creator + notify_followers >= 1),
    CONSTRAINT fk_crm_work_task_series_reminder_step_root
        FOREIGN KEY (tenant_id, work_task_series_id)
            REFERENCES crm_work_task_series (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_work_task_reminder_occurrence
(
    id                              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id                       BIGINT       NOT NULL,
    work_task_id                    BIGINT       NOT NULL,
    notification_projection_version BIGINT       NOT NULL,
    step_key                        VARCHAR(128) NOT NULL,
    due_time                        DATETIME(6)  NOT NULL,
    status                          VARCHAR(16)  NOT NULL DEFAULT 'READY',
    lease_owner                     VARCHAR(36)  NULL,
    lease_fence                     BIGINT       NOT NULL DEFAULT 0,
    lease_until                     DATETIME(6)  NULL,
    created_time                    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    terminal_time                   DATETIME(6)  NULL,

    CONSTRAINT pk_crm_work_task_reminder_occurrence PRIMARY KEY (id),
    CONSTRAINT uk_crm_work_task_reminder_occurrence_identity
        UNIQUE (tenant_id, work_task_id, notification_projection_version, step_key),
    CONSTRAINT uk_crm_work_task_reminder_occurrence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_work_task_reminder_occurrence_key
        CHECK (step_key REGEXP '^[A-Z0-9_]{3,128}$'),
    CONSTRAINT chk_crm_work_task_reminder_occurrence_version
        CHECK (notification_projection_version >= 0 AND lease_fence >= 0),
    CONSTRAINT chk_crm_work_task_reminder_occurrence_status CHECK (
        (status = 'READY' AND lease_owner IS NULL AND lease_until IS NULL AND terminal_time IS NULL)
        OR (status = 'LEASED' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL AND terminal_time IS NULL)
        OR (status IN ('EMITTED', 'CANCELLED') AND terminal_time IS NOT NULL)),
    CONSTRAINT fk_crm_work_task_reminder_occurrence_task
        FOREIGN KEY (tenant_id, work_task_id)
            REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_work_task_reminder_occurrence_due
        (status, due_time, id),
    INDEX idx_crm_work_task_reminder_occurrence_task
        (tenant_id, work_task_id, status, notification_projection_version),
    INDEX idx_crm_work_task_reminder_occurrence_retention
        (status, terminal_time, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_notification
(
    id                               BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id                        BIGINT       NOT NULL,
    recipient_actor_id               BIGINT       NOT NULL,
    kind                             VARCHAR(32)  NOT NULL,
    reminder_occurrence_id           BIGINT       NOT NULL,
    work_task_id                     BIGINT       NOT NULL,
    task_notification_projection_version BIGINT   NOT NULL,
    title                            VARCHAR(200) NOT NULL,
    priority                         VARCHAR(20)  NOT NULL,
    due_time                         DATETIME(6)  NOT NULL,
    status                           VARCHAR(16)  NOT NULL DEFAULT 'UNREAD',
    read_time                        DATETIME(6)  NULL,
    created_time                     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time                     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_notification PRIMARY KEY (id),
    CONSTRAINT uk_crm_notification_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_notification_occurrence_recipient
        UNIQUE (tenant_id, reminder_occurrence_id, recipient_actor_id),
    CONSTRAINT chk_crm_notification_kind
        CHECK (kind IN ('WORK_TASK_REMINDER', 'WORK_TASK_ESCALATION')),
    CONSTRAINT chk_crm_notification_title
        CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200),
    CONSTRAINT chk_crm_notification_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_crm_notification_version
        CHECK (task_notification_projection_version >= 0),
    CONSTRAINT chk_crm_notification_status CHECK (
        (status = 'UNREAD' AND read_time IS NULL)
        OR (status = 'READ' AND read_time IS NOT NULL)
        OR (status = 'WITHDRAWN' AND read_time IS NULL)),
    CONSTRAINT fk_crm_notification_recipient
        FOREIGN KEY (tenant_id, recipient_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_notification_occurrence
        FOREIGN KEY (tenant_id, reminder_occurrence_id)
            REFERENCES crm_work_task_reminder_occurrence (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_notification_task
        FOREIGN KEY (tenant_id, work_task_id)
            REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_notification_inbox
        (tenant_id, recipient_actor_id, status, created_time, id),
    INDEX idx_crm_notification_task
        (tenant_id, work_task_id, task_notification_projection_version, status, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
