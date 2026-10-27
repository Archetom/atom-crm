-- B28a: OA-owned one-shot scheduled publication. This is deliberately not a B26 run.
ALTER TABLE oa_announcement
    DROP CHECK chk_oa_announcement_status,
    DROP CHECK chk_oa_announcement_lifecycle,
    ADD COLUMN scheduled_publish_time DATETIME(6) NULL AFTER status,
    ADD COLUMN scheduled_by_actor_id BIGINT NULL AFTER scheduled_publish_time,
    ADD COLUMN publication_generation BIGINT NOT NULL DEFAULT 0 AFTER scheduled_by_actor_id,
    ADD COLUMN last_publication_safe_reason VARCHAR(64) NULL AFTER publication_generation,
    ADD CONSTRAINT chk_oa_announcement_status CHECK (
        status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'WITHDRAWN', 'DELETED')
    ),
    ADD CONSTRAINT chk_oa_announcement_publication_generation
        CHECK (publication_generation >= 0),
    ADD CONSTRAINT chk_oa_announcement_publication_reason CHECK (
        last_publication_safe_reason IS NULL OR last_publication_safe_reason IN (
            'MISFIRE_EXPIRED', 'SCHEDULING_MEMBER_UNAUTHORIZED', 'AUDIENCE_INVALID',
            'AUDIENCE_EMPTY', 'AUDIENCE_LIMIT', 'RETRY_EXHAUSTED'
        )
    ),
    ADD CONSTRAINT chk_oa_announcement_lifecycle CHECK (
        (status = 'DRAFT'
            AND scheduled_publish_time IS NULL AND scheduled_by_actor_id IS NULL
            AND published_time IS NULL AND withdrawn_time IS NULL)
        OR (status = 'SCHEDULED'
            AND scheduled_publish_time IS NOT NULL AND scheduled_by_actor_id IS NOT NULL
            AND published_time IS NULL AND withdrawn_time IS NULL
            AND last_publication_safe_reason IS NULL)
        OR (status = 'PUBLISHED'
            AND scheduled_publish_time IS NULL AND scheduled_by_actor_id IS NULL
            AND published_time IS NOT NULL AND withdrawn_time IS NULL)
        OR (status = 'WITHDRAWN'
            AND scheduled_publish_time IS NULL AND scheduled_by_actor_id IS NULL
            AND published_time IS NOT NULL AND withdrawn_time IS NOT NULL)
        OR (status = 'DELETED'
            AND scheduled_publish_time IS NULL AND scheduled_by_actor_id IS NULL
            AND ((published_time IS NULL AND withdrawn_time IS NULL)
                OR (published_time IS NOT NULL AND withdrawn_time IS NOT NULL)))
    ),
    ADD CONSTRAINT fk_oa_announcement_scheduling_member
        FOREIGN KEY (tenant_id, scheduled_by_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    ADD INDEX idx_oa_announcement_schedule
        (tenant_id, status, scheduled_publish_time, id);

CREATE TABLE oa_announcement_publication_occurrence
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id              BIGINT       NOT NULL,
    announcement_id        BIGINT       NOT NULL,
    publication_generation BIGINT       NOT NULL,
    due_time               DATETIME(6)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    attempt_count          INT          NOT NULL DEFAULT 0,
    available_at           DATETIME(6)  NOT NULL,
    deadline_at            DATETIME(6)  NOT NULL,
    lease_owner            CHAR(36)     NULL,
    lease_fence            BIGINT       NOT NULL DEFAULT 0,
    lease_until            DATETIME(6)  NULL,
    terminal_time          DATETIME(6)  NULL,
    safe_reason            VARCHAR(64)  NULL,
    active_announcement_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('READY', 'RETRY_WAIT', 'LEASED') THEN announcement_id ELSE NULL END
    ) STORED,
    created_time           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_oa_announcement_publication_occurrence PRIMARY KEY (id),
    CONSTRAINT uk_oa_announcement_publication_occurrence_identity
        UNIQUE (tenant_id, announcement_id, publication_generation),
    CONSTRAINT uk_oa_announcement_publication_occurrence_active
        UNIQUE (tenant_id, active_announcement_id),
    CONSTRAINT chk_oa_announcement_publication_occurrence_generation
        CHECK (publication_generation > 0),
    CONSTRAINT chk_oa_announcement_publication_occurrence_attempt
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_oa_announcement_publication_occurrence_fence
        CHECK (lease_fence >= 0),
    CONSTRAINT chk_oa_announcement_publication_occurrence_times
        CHECK (deadline_at > due_time),
    CONSTRAINT chk_oa_announcement_publication_occurrence_status CHECK (
        (status IN ('READY', 'RETRY_WAIT')
            AND lease_owner IS NULL AND lease_until IS NULL AND terminal_time IS NULL)
        OR (status = 'LEASED'
            AND lease_owner IS NOT NULL AND lease_until IS NOT NULL AND terminal_time IS NULL)
        OR (status IN ('PUBLISHED', 'CANCELLED', 'DEAD')
            AND lease_owner IS NULL AND lease_until IS NULL AND terminal_time IS NOT NULL)
    ),
    CONSTRAINT chk_oa_announcement_publication_occurrence_reason CHECK (
        safe_reason IS NULL OR safe_reason IN (
            'MISFIRE_EXPIRED', 'SCHEDULING_MEMBER_UNAUTHORIZED', 'AUDIENCE_INVALID',
            'AUDIENCE_EMPTY', 'AUDIENCE_LIMIT', 'RETRY_EXHAUSTED',
            'RETRY', 'CANCELLED', 'SUPERSEDED'
        )
    ),
    CONSTRAINT fk_oa_announcement_publication_occurrence_root
        FOREIGN KEY (tenant_id, announcement_id)
            REFERENCES oa_announcement (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_oa_announcement_publication_occurrence_claim
        (status, available_at, deadline_at, due_time, id),
    INDEX idx_oa_announcement_publication_occurrence_retention
        (status, terminal_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
