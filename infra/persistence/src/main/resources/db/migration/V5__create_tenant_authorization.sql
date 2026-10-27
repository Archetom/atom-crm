CREATE TABLE authorization_role
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    system_key   VARCHAR(30)  NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_authorization_role PRIMARY KEY (id),
    CONSTRAINT uk_authorization_role_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uk_authorization_role_tenant_system_key UNIQUE (tenant_id, system_key),
    CONSTRAINT uk_authorization_role_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_authorization_role_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_authorization_role_system_key
        CHECK (system_key IS NULL OR system_key IN ('TENANT_OWNER', 'TENANT_MEMBER')),
    CONSTRAINT chk_authorization_role_version CHECK (version >= 0),

    INDEX idx_authorization_role_tenant_status (tenant_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE authorization_role_grant
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT       NOT NULL,
    role_id        BIGINT       NOT NULL,
    authority_code VARCHAR(100) NOT NULL,
    data_scope     VARCHAR(30)  NULL,
    created_time   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_authorization_role_grant PRIMARY KEY (id),
    CONSTRAINT uk_authorization_role_grant_authority
        UNIQUE (tenant_id, role_id, authority_code),
    CONSTRAINT chk_authorization_role_grant_scope
        CHECK (data_scope IS NULL OR data_scope IN ('SELF', 'DEPARTMENT', 'DEPARTMENT_SUBTREE', 'TENANT')),
    CONSTRAINT fk_authorization_role_grant_role
        FOREIGN KEY (tenant_id, role_id)
            REFERENCES authorization_role (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_authorization_role_grant_role (tenant_id, role_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE authorization_role_assignment
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    role_id      BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_authorization_role_assignment PRIMARY KEY (id),
    CONSTRAINT uk_authorization_role_assignment_member_role
        UNIQUE (tenant_id, actor_id, role_id),
    CONSTRAINT fk_authorization_role_assignment_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_authorization_role_assignment_role
        FOREIGN KEY (tenant_id, role_id)
            REFERENCES authorization_role (tenant_id, id)
            ON DELETE RESTRICT,

    INDEX idx_authorization_role_assignment_role (tenant_id, role_id),
    INDEX idx_authorization_role_assignment_member (tenant_id, actor_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE navigation_preference
(
    tenant_id    BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    ordered_codes JSON       NOT NULL,
    hidden_codes  JSON       NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_navigation_preference PRIMARY KEY (tenant_id, actor_id),
    CONSTRAINT chk_navigation_preference_version CHECK (version >= 0),
    CONSTRAINT fk_navigation_preference_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO authorization_role (tenant_id, name, description, status, system_key)
SELECT tenant_id, 'Tenant Owner', 'Built-in tenant owner role', 'ACTIVE', 'TENANT_OWNER'
FROM organization_tenant;

INSERT INTO authorization_role (tenant_id, name, description, status, system_key)
SELECT tenant_id, 'Tenant Member', 'Built-in tenant member role', 'ACTIVE', 'TENANT_MEMBER'
FROM organization_tenant;

INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id)
SELECT membership.tenant_id, membership.actor_id, role.id
FROM organization_membership membership
JOIN authorization_role role
  ON role.tenant_id = membership.tenant_id
 AND role.system_key = 'TENANT_MEMBER'
WHERE membership.status = 'ACTIVE';

INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id)
SELECT tenant.tenant_id, tenant.initialized_by_actor_id, role.id
FROM organization_tenant tenant
JOIN organization_membership membership
  ON membership.tenant_id = tenant.tenant_id
 AND membership.actor_id = tenant.initialized_by_actor_id
 AND membership.status = 'ACTIVE'
JOIN authorization_role role
  ON role.tenant_id = tenant.tenant_id
 AND role.system_key = 'TENANT_OWNER';
