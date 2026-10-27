ALTER TABLE organization_department
    ADD CONSTRAINT uk_organization_department_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE crm_customer_pool
(
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id                    BIGINT       NOT NULL,
    name                         VARCHAR(100) NOT NULL,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    daily_claim_limit            INT          NOT NULL DEFAULT 0,
    previous_owner_cooldown_days INT          NOT NULL DEFAULT 0,
    automatic_release_enabled    TINYINT      NOT NULL DEFAULT 0,
    version                      BIGINT       NOT NULL DEFAULT 0,
    created_time                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active_name                  VARCHAR(100) GENERATED ALWAYS AS
        (CASE WHEN status <> 'DELETED' THEN name ELSE NULL END) STORED,

    CONSTRAINT pk_crm_customer_pool PRIMARY KEY (id),
    CONSTRAINT uk_crm_customer_pool_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_customer_pool_name UNIQUE (tenant_id, active_name),
    CONSTRAINT chk_crm_customer_pool_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_crm_customer_pool_claim_limit
        CHECK (daily_claim_limit BETWEEN 0 AND 10000),
    CONSTRAINT chk_crm_customer_pool_cooldown
        CHECK (previous_owner_cooldown_days BETWEEN 0 AND 3650),
    CONSTRAINT chk_crm_customer_pool_automatic
        CHECK (automatic_release_enabled IN (0, 1)),
    CONSTRAINT chk_crm_customer_pool_version CHECK (version >= 0),

    INDEX idx_crm_customer_pool_tenant_status (tenant_id, status, updated_time, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_pool_administrator
(
    tenant_id   BIGINT      NOT NULL,
    pool_id     BIGINT      NOT NULL,
    actor_id    BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_pool_administrator PRIMARY KEY (tenant_id, pool_id, actor_id),
    CONSTRAINT fk_crm_customer_pool_administrator_pool
        FOREIGN KEY (tenant_id, pool_id) REFERENCES crm_customer_pool (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_administrator_member
        FOREIGN KEY (tenant_id, actor_id) REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    INDEX idx_crm_customer_pool_administrator_actor (tenant_id, actor_id, pool_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_pool_eligible_member
(
    tenant_id   BIGINT      NOT NULL,
    pool_id     BIGINT      NOT NULL,
    actor_id    BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_pool_eligible_member PRIMARY KEY (tenant_id, pool_id, actor_id),
    CONSTRAINT fk_crm_customer_pool_eligible_member_pool
        FOREIGN KEY (tenant_id, pool_id) REFERENCES crm_customer_pool (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_eligible_member_membership
        FOREIGN KEY (tenant_id, actor_id) REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    INDEX idx_crm_customer_pool_eligible_member_actor (tenant_id, actor_id, pool_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_pool_eligible_department
(
    tenant_id    BIGINT      NOT NULL,
    pool_id      BIGINT      NOT NULL,
    department_id BIGINT     NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_pool_eligible_department PRIMARY KEY (tenant_id, pool_id, department_id),
    CONSTRAINT fk_crm_customer_pool_eligible_department_pool
        FOREIGN KEY (tenant_id, pool_id) REFERENCES crm_customer_pool (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_eligible_department_department
        FOREIGN KEY (tenant_id, department_id) REFERENCES organization_department (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_customer_pool_eligible_department_department (tenant_id, department_id, pool_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_pool_rule
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    pool_id       BIGINT      NOT NULL,
    rule_type     VARCHAR(30) NOT NULL,
    inactive_days INT         NOT NULL,
    created_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_pool_rule PRIMARY KEY (id),
    CONSTRAINT uk_crm_customer_pool_rule UNIQUE (tenant_id, pool_id, rule_type),
    CONSTRAINT chk_crm_customer_pool_rule_type CHECK (rule_type IN ('STALE_UPDATE', 'NOT_WON')),
    CONSTRAINT chk_crm_customer_pool_rule_days CHECK (inactive_days BETWEEN 1 AND 3650),
    CONSTRAINT fk_crm_customer_pool_rule_pool
        FOREIGN KEY (tenant_id, pool_id) REFERENCES crm_customer_pool (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_pool_membership
(
    tenant_id              BIGINT      NOT NULL,
    customer_id            BIGINT      NOT NULL,
    pool_id                BIGINT      NOT NULL,
    previous_owner_actor_id BIGINT     NULL,
    entered_time           DATETIME(6) NOT NULL,

    CONSTRAINT pk_crm_customer_pool_membership PRIMARY KEY (tenant_id, customer_id),
    CONSTRAINT fk_crm_customer_pool_membership_customer
        FOREIGN KEY (tenant_id, customer_id) REFERENCES crm_customer (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_membership_pool
        FOREIGN KEY (tenant_id, pool_id) REFERENCES crm_customer_pool (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_membership_previous_owner
        FOREIGN KEY (tenant_id, previous_owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    INDEX idx_crm_customer_pool_membership_pool (tenant_id, pool_id, entered_time, customer_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_customer_pool_claim_history
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT      NOT NULL,
    pool_id      BIGINT      NOT NULL,
    customer_id  BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    is_assigned  TINYINT     NOT NULL DEFAULT 0,
    claimed_time DATETIME(6) NOT NULL,

    CONSTRAINT pk_crm_customer_pool_claim_history PRIMARY KEY (id),
    CONSTRAINT chk_crm_customer_pool_claim_assigned CHECK (is_assigned IN (0, 1)),
    CONSTRAINT fk_crm_customer_pool_claim_pool
        FOREIGN KEY (tenant_id, pool_id) REFERENCES crm_customer_pool (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_claim_customer
        FOREIGN KEY (tenant_id, customer_id) REFERENCES crm_customer (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_claim_member
        FOREIGN KEY (tenant_id, actor_id) REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    INDEX idx_crm_customer_pool_claim_daily (tenant_id, pool_id, actor_id, claimed_time)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
