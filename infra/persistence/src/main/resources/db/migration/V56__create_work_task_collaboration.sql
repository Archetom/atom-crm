ALTER TABLE crm_work_task
    ADD COLUMN business_object_type VARCHAR(30) NULL AFTER current_assignee_actor_id,
    ADD COLUMN business_record_id BIGINT NULL AFTER business_object_type,
    ADD CONSTRAINT chk_crm_work_task_business_record CHECK (
        (business_object_type IS NULL AND business_record_id IS NULL)
        OR (business_object_type IS NOT NULL
            AND business_record_id IS NOT NULL
            AND business_object_type IN (
            'CUSTOMER', 'CONTACT', 'LEAD', 'OPPORTUNITY',
            'CONTRACT', 'INVOICE', 'RECEIVABLE', 'RECEIVABLE_PLAN')
            AND business_record_id > 0)
    ),
    ADD INDEX idx_crm_work_task_business_record
        (tenant_id, business_object_type, business_record_id, status, updated_time, id);

CREATE TABLE crm_work_task_follower
(
    tenant_id        BIGINT      NOT NULL,
    work_task_id     BIGINT      NOT NULL,
    follower_actor_id BIGINT     NOT NULL,
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_work_task_follower
        PRIMARY KEY (tenant_id, work_task_id, follower_actor_id),
    CONSTRAINT fk_crm_work_task_follower_task
        FOREIGN KEY (tenant_id, work_task_id)
            REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_work_task_follower_member
        FOREIGN KEY (tenant_id, follower_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_work_task_follower_member
        (tenant_id, follower_actor_id, work_task_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_work_task_comment
(
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT        NOT NULL,
    work_task_id    BIGINT        NOT NULL,
    author_actor_id BIGINT        NOT NULL,
    content         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT        NOT NULL DEFAULT 0,
    created_time    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_work_task_comment PRIMARY KEY (id),
    CONSTRAINT uk_crm_work_task_comment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_work_task_comment_content
        CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 5000),
    CONSTRAINT chk_crm_work_task_comment_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_work_task_comment_version CHECK (version >= 0),
    CONSTRAINT fk_crm_work_task_comment_task
        FOREIGN KEY (tenant_id, work_task_id)
            REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_work_task_comment_author
        FOREIGN KEY (tenant_id, author_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_work_task_comment_page
        (tenant_id, work_task_id, status, created_time, id),
    INDEX idx_crm_work_task_comment_author
        (tenant_id, author_actor_id, work_task_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
