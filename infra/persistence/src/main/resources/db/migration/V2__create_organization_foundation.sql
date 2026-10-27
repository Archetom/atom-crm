CREATE TABLE organization_member_profile
(
    actor_id     BIGINT       NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email        VARCHAR(254) NULL,
    phone_number VARCHAR(20)  NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version      BIGINT       NOT NULL DEFAULT 0,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_organization_member_profile PRIMARY KEY (actor_id),
    CONSTRAINT chk_organization_member_profile_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_organization_member_profile_version CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE organization_department
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id            BIGINT       NOT NULL,
    parent_department_id BIGINT       NOT NULL DEFAULT 0,
    name                 VARCHAR(100) NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version              BIGINT       NOT NULL DEFAULT 0,
    created_time         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_organization_department PRIMARY KEY (id),
    CONSTRAINT uk_organization_department_parent_name
        UNIQUE (tenant_id, parent_department_id, name),
    CONSTRAINT chk_organization_department_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_organization_department_version CHECK (version >= 0),

    INDEX idx_organization_department_tenant_status (tenant_id, status),
    INDEX idx_organization_department_tenant_parent (tenant_id, parent_department_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE organization_membership
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    actor_id      BIGINT      NOT NULL,
    department_id BIGINT      NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version       BIGINT      NOT NULL DEFAULT 0,
    created_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_organization_membership PRIMARY KEY (id),
    CONSTRAINT uk_organization_membership_tenant_actor UNIQUE (tenant_id, actor_id),
    CONSTRAINT chk_organization_membership_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_organization_membership_version CHECK (version >= 0),

    INDEX idx_organization_membership_actor_status (actor_id, status),
    INDEX idx_organization_membership_tenant_department (tenant_id, department_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE organization_membership_request
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    applicant_actor_id   BIGINT       NOT NULL,
    target_tenant_id     BIGINT       NOT NULL,
    target_department_id BIGINT       NULL,
    message              VARCHAR(500) NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewer_actor_id    BIGINT       NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_time         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_organization_membership_request PRIMARY KEY (id),
    CONSTRAINT chk_organization_membership_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT chk_organization_membership_request_version CHECK (version >= 0),

    INDEX idx_organization_membership_request_target_status (target_tenant_id, status),
    INDEX idx_organization_membership_request_applicant_status (applicant_actor_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
