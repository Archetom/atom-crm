CREATE TABLE organization_tenant
(
    tenant_id               BIGINT      NOT NULL,
    initialized_by_actor_id BIGINT      NOT NULL,
    version                 BIGINT      NOT NULL DEFAULT 0,
    created_time            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_organization_tenant PRIMARY KEY (tenant_id),
    CONSTRAINT chk_organization_tenant_version CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
