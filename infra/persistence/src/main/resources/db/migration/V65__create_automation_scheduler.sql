CREATE TABLE crm_automation_schedule
(
    tenant_id        BIGINT       NOT NULL,
    kind             VARCHAR(48)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    generation       BIGINT       NOT NULL DEFAULT 1,
    cadence_revision VARCHAR(40)  NOT NULL,
    next_due_at      DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_automation_schedule PRIMARY KEY (tenant_id, kind),
    CONSTRAINT chk_crm_automation_schedule_kind
        CHECK (kind IN ('CUSTOMER_POOL_RELEASE', 'CONTACT_POOL_RELEASE')),
    CONSTRAINT chk_crm_automation_schedule_status CHECK (status IN ('ACTIVE', 'PAUSED')),
    CONSTRAINT chk_crm_automation_schedule_generation CHECK (generation >= 1),
    CONSTRAINT chk_crm_automation_schedule_next_due CHECK (
        (status = 'ACTIVE' AND next_due_at IS NOT NULL)
        OR (status = 'PAUSED' AND next_due_at IS NULL)),
    CONSTRAINT chk_crm_automation_schedule_version CHECK (version >= 0),
    CONSTRAINT fk_crm_automation_schedule_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE CASCADE,

    INDEX idx_crm_automation_schedule_due (status, next_due_at, tenant_id, kind)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_automation_run
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id        BIGINT       NOT NULL,
    kind             VARCHAR(48)  NOT NULL,
    generation       BIGINT       NOT NULL,
    due_at           DATETIME(6)  NOT NULL,
    evaluation_time  DATETIME(6)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    available_at     DATETIME(6)  NOT NULL,
    attempt          INT          NOT NULL DEFAULT 0,
    lease_owner      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expires_at DATETIME(6)  NULL,
    lease_fence      BIGINT       NOT NULL DEFAULT 0,
    deadline_at      DATETIME(6)  NOT NULL,
    result_code      VARCHAR(40)  NULL,
    safe_reason      VARCHAR(40)  NULL,
    affected_count   INT          NOT NULL DEFAULT 0,
    completed_time   DATETIME(6)  NULL,
    active_slot      TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('READY', 'LEASED', 'RETRY_WAIT') THEN 1 ELSE NULL END) STORED,
    created_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_automation_run PRIMARY KEY (id),
    CONSTRAINT uk_crm_automation_run_occurrence UNIQUE (tenant_id, kind, generation, due_at),
    CONSTRAINT uk_crm_automation_run_active UNIQUE (tenant_id, kind, active_slot),
    CONSTRAINT chk_crm_automation_run_kind
        CHECK (kind IN ('CUSTOMER_POOL_RELEASE', 'CONTACT_POOL_RELEASE')),
    CONSTRAINT chk_crm_automation_run_status
        CHECK (status IN ('READY', 'LEASED', 'RETRY_WAIT', 'SUCCEEDED', 'CANCELLED', 'DEAD')),
    CONSTRAINT chk_crm_automation_run_generation CHECK (generation >= 1),
    CONSTRAINT chk_crm_automation_run_attempt CHECK (attempt >= 0),
    CONSTRAINT chk_crm_automation_run_fence CHECK (lease_fence >= 0),
    CONSTRAINT chk_crm_automation_run_affected CHECK (affected_count >= 0),
    CONSTRAINT chk_crm_automation_run_lease CHECK (
        (status = 'LEASED' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'LEASED' AND lease_owner IS NULL AND lease_expires_at IS NULL)),
    CONSTRAINT chk_crm_automation_run_terminal CHECK (
        (status IN ('SUCCEEDED', 'CANCELLED', 'DEAD') AND completed_time IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'CANCELLED', 'DEAD') AND completed_time IS NULL)),
    CONSTRAINT fk_crm_automation_run_schedule
        FOREIGN KEY (tenant_id, kind) REFERENCES crm_automation_schedule (tenant_id, kind)
            ON DELETE CASCADE ON UPDATE RESTRICT,

    INDEX idx_crm_automation_run_claim (status, available_at, lease_expires_at, id),
    INDEX idx_crm_automation_run_retention (status, completed_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO crm_automation_schedule
    (tenant_id, kind, status, generation, cadence_revision, next_due_at)
SELECT pool.tenant_id,
       'CUSTOMER_POOL_RELEASE',
       'ACTIVE',
       1,
       'B26A_V1',
       UTC_TIMESTAMP(6)
FROM crm_customer_pool pool
WHERE pool.status = 'ACTIVE'
  AND pool.automatic_release_enabled = 1
GROUP BY pool.tenant_id;

INSERT INTO crm_automation_schedule
    (tenant_id, kind, status, generation, cadence_revision, next_due_at)
SELECT pool.tenant_id,
       'CONTACT_POOL_RELEASE',
       'ACTIVE',
       1,
       'B26A_V1',
       UTC_TIMESTAMP(6)
FROM crm_contact_pool pool
WHERE pool.status = 'ACTIVE'
  AND pool.automatic_release_enabled = 1
GROUP BY pool.tenant_id;
