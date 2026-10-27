CREATE TABLE crm_notification_preference
(
    tenant_id          BIGINT      NOT NULL,
    recipient_actor_id BIGINT      NOT NULL,
    email_enabled      TINYINT     NOT NULL DEFAULT 0,
    updated_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_notification_preference PRIMARY KEY (tenant_id, recipient_actor_id),
    CONSTRAINT chk_crm_notification_preference_email CHECK (email_enabled IN (0, 1)),
    CONSTRAINT fk_crm_notification_preference_recipient
        FOREIGN KEY (tenant_id, recipient_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_notification_delivery
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT       NOT NULL,
    notification_id       BIGINT       NOT NULL,
    recipient_actor_id    BIGINT       NOT NULL,
    work_task_id          BIGINT       NOT NULL,
    channel               VARCHAR(16)  NOT NULL,
    template_revision     VARCHAR(64)  NOT NULL,
    payload_digest        CHAR(64)     NOT NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'READY',
    attempt_count         INT          NOT NULL DEFAULT 0,
    available_at          DATETIME(6)  NOT NULL,
    deadline_at           DATETIME(6)  NOT NULL,
    lease_owner           VARCHAR(36)  NULL,
    lease_fence           BIGINT       NOT NULL DEFAULT 0,
    lease_until           DATETIME(6)  NULL,
    guarantee             VARCHAR(24)  NULL,
    idempotency_until     DATETIME(6)  NULL,
    dispatch_started_time DATETIME(6)  NULL,
    terminal_time         DATETIME(6)  NULL,
    safe_reason           VARCHAR(40)  NULL,
    created_time          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_notification_delivery PRIMARY KEY (id),
    CONSTRAINT uk_crm_notification_delivery_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_notification_delivery_notification_channel
        UNIQUE (tenant_id, notification_id, channel),
    CONSTRAINT chk_crm_notification_delivery_channel CHECK (channel = 'EMAIL'),
    CONSTRAINT chk_crm_notification_delivery_digest CHECK (payload_digest REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_crm_notification_delivery_attempt CHECK (attempt_count >= 0 AND lease_fence >= 0),
    CONSTRAINT chk_crm_notification_delivery_deadline CHECK (deadline_at > available_at),
    CONSTRAINT chk_crm_notification_delivery_guarantee CHECK (
        guarantee IS NULL OR guarantee IN ('IDEMPOTENT_SEND', 'AT_MOST_ONCE_SEND')),
    CONSTRAINT chk_crm_notification_delivery_safe_reason CHECK (
        safe_reason IS NULL OR CHAR_LENGTH(safe_reason) BETWEEN 1 AND 40),
    CONSTRAINT chk_crm_notification_delivery_state CHECK (
        (status IN ('READY', 'RETRY_WAIT')
            AND lease_owner IS NULL AND lease_until IS NULL AND terminal_time IS NULL)
        OR (status = 'LEASED'
            AND lease_owner IS NOT NULL AND lease_until IS NOT NULL AND terminal_time IS NULL)
        OR (status = 'DISPATCHING'
            AND lease_owner IS NOT NULL AND lease_until IS NOT NULL
            AND guarantee IS NOT NULL AND dispatch_started_time IS NOT NULL AND terminal_time IS NULL)
        OR (status IN ('ACCEPTED', 'REJECTED', 'SUPPRESSED', 'AMBIGUOUS', 'DEAD')
            AND lease_owner IS NULL AND lease_until IS NULL AND terminal_time IS NOT NULL)
    ),
    CONSTRAINT fk_crm_notification_delivery_notification
        FOREIGN KEY (tenant_id, notification_id)
            REFERENCES crm_notification (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_notification_delivery_recipient
        FOREIGN KEY (tenant_id, recipient_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_notification_delivery_task
        FOREIGN KEY (tenant_id, work_task_id)
            REFERENCES crm_work_task (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_notification_delivery_claim (status, available_at, deadline_at, id),
    INDEX idx_crm_notification_delivery_task
        (tenant_id, work_task_id, status, notification_id, id),
    INDEX idx_crm_notification_delivery_retention (status, terminal_time, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
