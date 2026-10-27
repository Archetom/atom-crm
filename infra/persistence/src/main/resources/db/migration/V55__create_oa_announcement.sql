-- B20c tenant OA announcements, immutable publication recipients, and read receipts.
CREATE TABLE oa_announcement
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT        NOT NULL,
    created_by_actor_id   BIGINT        NOT NULL,
    title                 VARCHAR(200)  NOT NULL,
    body                  MEDIUMTEXT    NOT NULL,
    tenant_audience       TINYINT       NOT NULL DEFAULT 0,
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    published_time        DATETIME(6)   NULL,
    withdrawn_time        DATETIME(6)   NULL,
    deleted_time          DATETIME(6)   NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_time          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_oa_announcement PRIMARY KEY (id),
    CONSTRAINT uk_oa_announcement_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_oa_announcement_title
        CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200),
    CONSTRAINT chk_oa_announcement_body
        CHECK (CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 20000),
    CONSTRAINT chk_oa_announcement_tenant_audience
        CHECK (tenant_audience IN (0, 1)),
    CONSTRAINT chk_oa_announcement_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN', 'DELETED')),
    CONSTRAINT chk_oa_announcement_version CHECK (version >= 0),
    CONSTRAINT chk_oa_announcement_lifecycle CHECK (
        (status = 'DRAFT' AND published_time IS NULL
            AND withdrawn_time IS NULL AND deleted_time IS NULL)
        OR (status = 'PUBLISHED' AND published_time IS NOT NULL
            AND withdrawn_time IS NULL AND deleted_time IS NULL)
        OR (status = 'WITHDRAWN' AND published_time IS NOT NULL
            AND withdrawn_time IS NOT NULL AND deleted_time IS NULL)
        OR (status = 'DELETED' AND deleted_time IS NOT NULL
            AND ((published_time IS NULL AND withdrawn_time IS NULL)
                OR (published_time IS NOT NULL AND withdrawn_time IS NOT NULL)))
    ),
    CONSTRAINT fk_oa_announcement_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_oa_announcement_creator
        FOREIGN KEY (tenant_id, created_by_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_oa_announcement_management
        (tenant_id, status, updated_time, id),
    INDEX idx_oa_announcement_publication
        (tenant_id, status, published_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE oa_announcement_audience_department
(
    tenant_id      BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    department_id  BIGINT NOT NULL,

    CONSTRAINT pk_oa_announcement_audience_department
        PRIMARY KEY (tenant_id, announcement_id, department_id),
    CONSTRAINT fk_oa_announcement_audience_department_root
        FOREIGN KEY (tenant_id, announcement_id)
            REFERENCES oa_announcement (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_announcement_audience_department_department
        FOREIGN KEY (tenant_id, department_id)
            REFERENCES organization_department (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_oa_announcement_audience_department_lookup
        (tenant_id, department_id, announcement_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE oa_announcement_audience_member
(
    tenant_id       BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    actor_id         BIGINT NOT NULL,

    CONSTRAINT pk_oa_announcement_audience_member
        PRIMARY KEY (tenant_id, announcement_id, actor_id),
    CONSTRAINT fk_oa_announcement_audience_member_root
        FOREIGN KEY (tenant_id, announcement_id)
            REFERENCES oa_announcement (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_announcement_audience_member_membership
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_oa_announcement_audience_member_lookup
        (tenant_id, actor_id, announcement_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE oa_announcement_recipient
(
    tenant_id       BIGINT      NOT NULL,
    announcement_id BIGINT      NOT NULL,
    actor_id         BIGINT      NOT NULL,
    delivered_time  DATETIME(6) NOT NULL,

    CONSTRAINT pk_oa_announcement_recipient
        PRIMARY KEY (tenant_id, announcement_id, actor_id),
    CONSTRAINT fk_oa_announcement_recipient_root
        FOREIGN KEY (tenant_id, announcement_id)
            REFERENCES oa_announcement (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_oa_announcement_recipient_membership
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_oa_announcement_recipient_inbox
        (tenant_id, actor_id, delivered_time, announcement_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE oa_announcement_read_receipt
(
    tenant_id       BIGINT      NOT NULL,
    announcement_id BIGINT      NOT NULL,
    actor_id         BIGINT      NOT NULL,
    read_time        DATETIME(6) NOT NULL,

    CONSTRAINT pk_oa_announcement_read_receipt
        PRIMARY KEY (tenant_id, announcement_id, actor_id),
    CONSTRAINT fk_oa_announcement_read_receipt_recipient
        FOREIGN KEY (tenant_id, announcement_id, actor_id)
            REFERENCES oa_announcement_recipient
                (tenant_id, announcement_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_oa_announcement_read_receipt_member
        (tenant_id, actor_id, read_time, announcement_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
