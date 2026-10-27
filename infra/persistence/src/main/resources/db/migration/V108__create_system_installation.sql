CREATE TABLE system_installation
(
    singleton_key         TINYINT     NOT NULL,
    initialized_tenant_id BIGINT      NOT NULL,
    initialized_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_system_installation PRIMARY KEY (singleton_key),
    CONSTRAINT chk_system_installation_singleton CHECK (singleton_key = 1)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Existing deployments are already initialized and must never regain a public setup window.
INSERT INTO system_installation (singleton_key, initialized_tenant_id)
SELECT 1, MIN(tenant_id)
FROM organization_tenant
HAVING COUNT(*) > 0;
