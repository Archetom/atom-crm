CREATE TABLE crm_activity
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT         NOT NULL,
    subject_type      VARCHAR(30)    NOT NULL,
    subject_id        BIGINT         NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    creator_actor_id  BIGINT         NOT NULL,
    category          VARCHAR(100)   NULL,
    purpose           VARCHAR(200)   NULL,
    content           VARCHAR(10000) NOT NULL,
    occurred_time     DATETIME(6)    NOT NULL,
    next_contact_time DATETIME(6)    NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version           BIGINT         NOT NULL DEFAULT 0,
    created_time      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_activity PRIMARY KEY (id),
    CONSTRAINT uk_crm_activity_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_activity_subject_type CHECK (subject_type IN
        ('CUSTOMER', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CONTRACT', 'INVOICE',
         'RECEIVABLE', 'RECEIVABLE_PLAN')),
    CONSTRAINT chk_crm_activity_kind CHECK (kind IN ('FOLLOW_UP', 'SYSTEM')),
    CONSTRAINT chk_crm_activity_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_activity_subject_id CHECK (subject_id > 0),
    CONSTRAINT chk_crm_activity_version CHECK (version >= 0),
    CONSTRAINT fk_crm_activity_creator FOREIGN KEY (tenant_id, creator_actor_id)
        REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_activity_subject_time
        (tenant_id, subject_type, subject_id, status, occurred_time, id),
    INDEX idx_crm_activity_creator_time
        (tenant_id, creator_actor_id, status, occurred_time, id),
    INDEX idx_crm_activity_next_contact
        (tenant_id, next_contact_time, status, subject_type, subject_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_activity_relation
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT      NOT NULL,
    activity_id  BIGINT      NOT NULL,
    object_type  VARCHAR(30) NOT NULL,
    record_id    BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_activity_relation PRIMARY KEY (id),
    CONSTRAINT uk_crm_activity_relation UNIQUE (tenant_id, activity_id, object_type, record_id),
    CONSTRAINT chk_crm_activity_relation_type CHECK (object_type IN
        ('CUSTOMER', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CONTRACT', 'INVOICE',
         'RECEIVABLE', 'RECEIVABLE_PLAN')),
    CONSTRAINT chk_crm_activity_relation_record CHECK (record_id > 0),
    CONSTRAINT fk_crm_activity_relation_root FOREIGN KEY (tenant_id, activity_id)
        REFERENCES crm_activity (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_activity_relation_record (tenant_id, object_type, record_id, activity_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
