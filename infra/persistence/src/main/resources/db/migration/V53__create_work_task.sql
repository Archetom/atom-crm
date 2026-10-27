CREATE TABLE crm_work_task
(
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id                   BIGINT        NOT NULL,
    created_by_actor_id         BIGINT        NOT NULL,
    current_assignee_actor_id   BIGINT        NOT NULL,
    title                       VARCHAR(200)  NOT NULL,
    description                 TEXT          NULL,
    priority                    VARCHAR(20)   NOT NULL DEFAULT 'NORMAL',
    due_time                    DATETIME(6)   NULL,
    status                      VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    first_started_time          DATETIME(6)   NULL,
    worklist_projection_version BIGINT        NOT NULL DEFAULT 0,
    completed_time              DATETIME(6)   NULL,
    cancelled_time              DATETIME(6)   NULL,
    version                     BIGINT        NOT NULL DEFAULT 0,
    created_time                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_work_task PRIMARY KEY (id),
    CONSTRAINT uk_crm_work_task_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_work_task_title
        CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200),
    CONSTRAINT chk_crm_work_task_description
        CHECK (description IS NULL OR CHAR_LENGTH(description) <= 10000),
    CONSTRAINT chk_crm_work_task_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_crm_work_task_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED', 'DELETED')),
    CONSTRAINT chk_crm_work_task_projection_version
        CHECK (worklist_projection_version >= 0),
    CONSTRAINT chk_crm_work_task_version CHECK (version >= 0),
    CONSTRAINT chk_crm_work_task_terminal_times CHECK (
        (status = 'DONE' AND completed_time IS NOT NULL AND cancelled_time IS NULL)
        OR (status = 'CANCELLED' AND cancelled_time IS NOT NULL AND completed_time IS NULL)
        OR (status IN ('TODO', 'IN_PROGRESS', 'DELETED')
            AND completed_time IS NULL AND cancelled_time IS NULL)
    ),
    CONSTRAINT chk_crm_work_task_deleted_start
        CHECK (status <> 'DELETED' OR first_started_time IS NULL),
    CONSTRAINT chk_crm_work_task_in_progress_start
        CHECK (status <> 'IN_PROGRESS' OR first_started_time IS NOT NULL),
    CONSTRAINT fk_crm_work_task_creator
        FOREIGN KEY (tenant_id, created_by_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_work_task_assignee
        FOREIGN KEY (tenant_id, current_assignee_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_work_task_assignee_due
        (tenant_id, current_assignee_actor_id, status, due_time, id),
    INDEX idx_crm_work_task_creator_list
        (tenant_id, created_by_actor_id, status, updated_time, id),
    INDEX idx_crm_work_task_fixed_filter
        (tenant_id, status, priority, due_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
