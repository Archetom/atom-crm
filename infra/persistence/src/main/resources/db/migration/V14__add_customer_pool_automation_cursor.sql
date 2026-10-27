CREATE TABLE crm_customer_pool_automation_cursor
(
    tenant_id    BIGINT      NOT NULL,
    last_pool_id BIGINT      NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_customer_pool_automation_cursor PRIMARY KEY (tenant_id),
    CONSTRAINT fk_crm_customer_pool_automation_cursor_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
