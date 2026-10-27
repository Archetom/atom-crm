CREATE TABLE crm_marketing_activity
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT       NOT NULL,
    created_by_actor_id     BIGINT       NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    related_object_type     VARCHAR(30)  NOT NULL,
    activity_type           VARCHAR(100) NULL,
    start_time              DATETIME(6)  NOT NULL,
    end_time                DATETIME(6)  NOT NULL,
    budget                  DECIMAL(18, 2) NULL,
    address                 VARCHAR(300) NULL,
    description             VARCHAR(2000) NULL,
    one_submission_per_user TINYINT      NOT NULL DEFAULT 1,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_time            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_marketing_activity PRIMARY KEY (id),
    CONSTRAINT uk_crm_marketing_activity_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_marketing_activity_related_object
        CHECK (related_object_type IN ('CUSTOMER', 'CONTACT', 'LEAD')),
    CONSTRAINT chk_crm_marketing_activity_time CHECK (start_time <= end_time),
    CONSTRAINT chk_crm_marketing_activity_budget CHECK (budget IS NULL OR budget >= 0),
    CONSTRAINT chk_crm_marketing_activity_single_submission
        CHECK (one_submission_per_user IN (0, 1)),
    CONSTRAINT chk_crm_marketing_activity_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_marketing_activity_version CHECK (version >= 0),
    CONSTRAINT fk_crm_marketing_activity_creator
        FOREIGN KEY (tenant_id, created_by_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_marketing_activity_list (tenant_id, status, updated_time, id),
    INDEX idx_crm_marketing_activity_related (tenant_id, related_object_type, status, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_marketing_activity_participant
(
    tenant_id             BIGINT NOT NULL,
    marketing_activity_id BIGINT NOT NULL,
    actor_id              BIGINT NOT NULL,

    CONSTRAINT pk_crm_marketing_activity_participant
        PRIMARY KEY (tenant_id, marketing_activity_id, actor_id),
    CONSTRAINT fk_crm_marketing_activity_participant_activity
        FOREIGN KEY (tenant_id, marketing_activity_id)
            REFERENCES crm_marketing_activity (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_marketing_activity_participant_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_marketing_activity_participant_actor
        (tenant_id, actor_id, marketing_activity_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
