CREATE TABLE crm_work_task_series
(
    id                           BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id                    BIGINT        NOT NULL,
    created_by_actor_id          BIGINT        NOT NULL,
    current_assignee_actor_id    BIGINT        NOT NULL,
    title                        VARCHAR(200)  NOT NULL,
    description                  TEXT          NULL,
    priority                     VARCHAR(20)   NOT NULL DEFAULT 'NORMAL',
    recurrence_frequency         VARCHAR(20)   NOT NULL,
    recurrence_interval          INT           NOT NULL,
    month_day                    TINYINT       NULL,
    last_day_of_month            TINYINT       NOT NULL DEFAULT 0,
    zone_id                      VARCHAR(100)  NOT NULL,
    start_local_date             DATE          NOT NULL,
    local_time                   TIME          NOT NULL,
    end_local_date               DATE          NULL,
    status                       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    generation                   BIGINT        NOT NULL DEFAULT 1,
    next_occurrence_sequence     BIGINT        NOT NULL DEFAULT 1,
    next_nominal_local_time      DATETIME(6)   NULL,
    next_due_at                  DATETIME(6)   NULL,
    occurrence_count             BIGINT        NOT NULL DEFAULT 0,
    skipped_count                BIGINT        NOT NULL DEFAULT 0,
    last_safe_reason             VARCHAR(64)   NULL,
    version                      BIGINT        NOT NULL DEFAULT 0,
    created_time                 DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time                 DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_work_task_series PRIMARY KEY (id),
    CONSTRAINT uk_crm_work_task_series_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_work_task_series_title
        CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200),
    CONSTRAINT chk_crm_work_task_series_description
        CHECK (description IS NULL OR CHAR_LENGTH(description) <= 10000),
    CONSTRAINT chk_crm_work_task_series_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_crm_work_task_series_frequency
        CHECK (recurrence_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_crm_work_task_series_interval CHECK (
        (recurrence_frequency = 'DAILY' AND recurrence_interval BETWEEN 1 AND 365)
        OR (recurrence_frequency = 'WEEKLY' AND recurrence_interval BETWEEN 1 AND 52)
        OR (recurrence_frequency = 'MONTHLY' AND recurrence_interval BETWEEN 1 AND 24)),
    CONSTRAINT chk_crm_work_task_series_monthly CHECK (
        (recurrence_frequency = 'MONTHLY' AND ((month_day BETWEEN 1 AND 31 AND last_day_of_month = 0)
            OR (month_day IS NULL AND last_day_of_month = 1)))
        OR (recurrence_frequency <> 'MONTHLY' AND month_day IS NULL AND last_day_of_month = 0)),
    CONSTRAINT chk_crm_work_task_series_dates CHECK (
        start_local_date >= '1000-01-01' AND start_local_date <= '9999-12-31'
        AND (end_local_date IS NULL OR (end_local_date >= start_local_date AND end_local_date <= '9999-12-31'))),
    CONSTRAINT chk_crm_work_task_series_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED', 'DELETED')),
    CONSTRAINT chk_crm_work_task_series_cursor CHECK (
        generation >= 1 AND next_occurrence_sequence >= 1 AND occurrence_count BETWEEN 0 AND 10000
        AND skipped_count >= 0
        AND ((status = 'ACTIVE' AND next_nominal_local_time IS NOT NULL AND next_due_at IS NOT NULL)
             OR (status <> 'ACTIVE'))),
    CONSTRAINT chk_crm_work_task_series_reason
        CHECK (last_safe_reason IS NULL OR last_safe_reason REGEXP '^[A-Z0-9_]{1,64}$'),
    CONSTRAINT chk_crm_work_task_series_version CHECK (version >= 0),
    CONSTRAINT fk_crm_work_task_series_tenant FOREIGN KEY (tenant_id)
        REFERENCES organization_tenant (tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_crm_work_task_series_creator FOREIGN KEY (tenant_id, created_by_actor_id)
        REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_work_task_series_assignee FOREIGN KEY (tenant_id, current_assignee_actor_id)
        REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_work_task_series_due (tenant_id, status, next_due_at, id),
    INDEX idx_crm_work_task_series_creator (tenant_id, created_by_actor_id, status, updated_time, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_work_task_series_weekday
(
    tenant_id               BIGINT      NOT NULL,
    work_task_series_id     BIGINT      NOT NULL,
    weekday                 TINYINT     NOT NULL,

    CONSTRAINT pk_crm_work_task_series_weekday PRIMARY KEY (tenant_id, work_task_series_id, weekday),
    CONSTRAINT chk_crm_work_task_series_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT fk_crm_work_task_series_weekday_root FOREIGN KEY (tenant_id, work_task_series_id)
        REFERENCES crm_work_task_series (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_work_task_series_follower
(
    tenant_id               BIGINT      NOT NULL,
    work_task_series_id     BIGINT      NOT NULL,
    follower_actor_id       BIGINT      NOT NULL,
    created_time            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_work_task_series_follower PRIMARY KEY (tenant_id, work_task_series_id, follower_actor_id),
    CONSTRAINT fk_crm_work_task_series_follower_root FOREIGN KEY (tenant_id, work_task_series_id)
        REFERENCES crm_work_task_series (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_work_task_series_follower_member FOREIGN KEY (tenant_id, follower_actor_id)
        REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    INDEX idx_crm_work_task_series_follower_member (tenant_id, follower_actor_id, work_task_series_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE crm_work_task
    ADD COLUMN origin_kind VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER current_assignee_actor_id,
    ADD COLUMN origin_series_id BIGINT NULL AFTER origin_kind,
    ADD COLUMN origin_series_generation BIGINT NULL AFTER origin_series_id,
    ADD COLUMN origin_occurrence_sequence BIGINT NULL AFTER origin_series_generation,
    ADD COLUMN origin_nominal_local_time DATETIME(6) NULL AFTER origin_occurrence_sequence,
    ADD COLUMN origin_zone_id VARCHAR(100) NULL AFTER origin_nominal_local_time,
    ADD COLUMN origin_offset_seconds INT NULL AFTER origin_zone_id,
    ADD COLUMN origin_resolved_due_time DATETIME(6) NULL AFTER origin_offset_seconds,
    ADD COLUMN generated_open_series_slot BIGINT GENERATED ALWAYS AS (
        CASE WHEN origin_kind = 'RECURRENCE' AND status IN ('TODO', 'IN_PROGRESS') THEN origin_series_id ELSE NULL END
    ) STORED AFTER cancelled_time,
    ADD CONSTRAINT chk_crm_work_task_origin CHECK (
        (origin_kind = 'MANUAL' AND origin_series_id IS NULL AND origin_series_generation IS NULL
            AND origin_occurrence_sequence IS NULL AND origin_nominal_local_time IS NULL
            AND origin_zone_id IS NULL AND origin_offset_seconds IS NULL AND origin_resolved_due_time IS NULL)
        OR (origin_kind = 'RECURRENCE' AND origin_series_id IS NOT NULL
            AND origin_series_generation >= 1 AND origin_occurrence_sequence >= 1
            AND origin_nominal_local_time IS NOT NULL AND origin_zone_id IS NOT NULL
            AND origin_offset_seconds IS NOT NULL AND origin_resolved_due_time IS NOT NULL)),
    ADD CONSTRAINT fk_crm_work_task_origin_series FOREIGN KEY (tenant_id, origin_series_id)
        REFERENCES crm_work_task_series (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_crm_work_task_recurrence_occurrence
        UNIQUE (tenant_id, origin_series_id, origin_series_generation, origin_occurrence_sequence),
    ADD CONSTRAINT uk_crm_work_task_generated_open_series
        UNIQUE (tenant_id, generated_open_series_slot);

CREATE TABLE crm_work_task_series_occurrence
(
    id                           BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id                    BIGINT        NOT NULL,
    work_task_series_id          BIGINT        NOT NULL,
    series_generation            BIGINT        NOT NULL,
    occurrence_sequence          BIGINT        NOT NULL,
    status                       VARCHAR(20)   NOT NULL,
    nominal_local_time           DATETIME(6)   NOT NULL,
    zone_id                      VARCHAR(100)  NOT NULL,
    resolved_offset_seconds      INT           NOT NULL,
    resolved_due_time            DATETIME(6)   NOT NULL,
    generated_work_task_id       BIGINT        NULL,
    safe_reason                  VARCHAR(64)   NULL,
    created_time                 DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_work_task_series_occurrence PRIMARY KEY (id),
    CONSTRAINT uk_crm_work_task_series_occurrence_identity
        UNIQUE (tenant_id, work_task_series_id, series_generation, occurrence_sequence),
    CONSTRAINT chk_crm_work_task_series_occurrence_status CHECK (status IN ('MATERIALIZED', 'SKIPPED')),
    CONSTRAINT chk_crm_work_task_series_occurrence_payload CHECK (
        (status = 'MATERIALIZED' AND generated_work_task_id IS NOT NULL AND safe_reason IS NULL)
        OR (status = 'SKIPPED' AND generated_work_task_id IS NULL AND safe_reason REGEXP '^[A-Z0-9_]{1,64}$')),
    CONSTRAINT fk_crm_work_task_series_occurrence_root FOREIGN KEY (tenant_id, work_task_series_id)
        REFERENCES crm_work_task_series (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_work_task_series_occurrence_task FOREIGN KEY (tenant_id, generated_work_task_id)
        REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_work_task_series_occurrence_page
        (tenant_id, work_task_series_id, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE crm_automation_schedule
    DROP CHECK chk_crm_automation_schedule_kind,
    ADD CONSTRAINT chk_crm_automation_schedule_kind
        CHECK (kind IN ('CUSTOMER_POOL_RELEASE', 'CONTACT_POOL_RELEASE', 'WORK_TASK_RECURRENCE'));

ALTER TABLE crm_automation_run
    DROP CHECK chk_crm_automation_run_kind,
    ADD CONSTRAINT chk_crm_automation_run_kind
        CHECK (kind IN ('CUSTOMER_POOL_RELEASE', 'CONTACT_POOL_RELEASE', 'WORK_TASK_RECURRENCE'));

CREATE TABLE crm_work_recurrence_control
(
    control_key      VARCHAR(40)  NOT NULL,
    tzdb_fingerprint VARCHAR(128) NOT NULL,
    policy_revision  VARCHAR(40)  NOT NULL,
    reconciled_time  DATETIME(6)  NOT NULL,
    created_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crm_work_recurrence_control PRIMARY KEY (control_key),
    CONSTRAINT chk_crm_work_recurrence_control_key CHECK (control_key = 'RECURRENCE_TZDB')
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
