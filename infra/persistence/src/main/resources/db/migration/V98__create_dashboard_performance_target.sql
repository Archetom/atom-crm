CREATE TABLE dashboard_performance_target
(
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT         NOT NULL,
    actor_id     BIGINT         NOT NULL,
    metric       VARCHAR(40)    NOT NULL,
    target_year  SMALLINT       NOT NULL,
    target_month TINYINT        NOT NULL,
    amount       DECIMAL(19, 2) NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_time DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_dashboard_performance_target PRIMARY KEY (id),
    CONSTRAINT uk_dashboard_performance_target_member_period
        UNIQUE (tenant_id, actor_id, metric, target_year, target_month),
    CONSTRAINT chk_dashboard_performance_target_metric
        CHECK (metric IN ('ACTIVE_CONTRACT')),
    CONSTRAINT chk_dashboard_performance_target_year
        CHECK (target_year BETWEEN 2000 AND 9999),
    CONSTRAINT chk_dashboard_performance_target_month
        CHECK (target_month BETWEEN 1 AND 12),
    CONSTRAINT chk_dashboard_performance_target_amount
        CHECK (amount > 0),
    CONSTRAINT chk_dashboard_performance_target_version
        CHECK (version >= 0),
    CONSTRAINT fk_dashboard_performance_target_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,
    INDEX idx_dashboard_performance_target_member_year
        (tenant_id, actor_id, metric, target_year, target_month)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
