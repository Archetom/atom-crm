CREATE TABLE t_user
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id        BIGINT       NOT NULL,
    username         VARCHAR(50)  NOT NULL,
    email            VARCHAR(254) NOT NULL,
    phone_number     VARCHAR(20)  NULL,
    password         VARCHAR(255) NOT NULL,
    real_name        VARCHAR(100) NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    external_id      VARCHAR(100) NULL,
    is_external_user BOOLEAN      NOT NULL DEFAULT FALSE,
    is_admin         BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_t_user PRIMARY KEY (id),
    CONSTRAINT uk_t_user_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uk_t_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT chk_t_user_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'DELETED')),
    CONSTRAINT chk_t_user_version CHECK (version >= 0),

    INDEX idx_t_user_tenant_status (tenant_id, status),
    INDEX idx_t_user_tenant_created_time (tenant_id, created_time),
    INDEX idx_t_user_tenant_external_id (tenant_id, external_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
