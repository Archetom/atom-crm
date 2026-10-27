CREATE TABLE dashboard_department_performance_target
(
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT         NOT NULL,
    department_id BIGINT         NOT NULL,
    metric        VARCHAR(40)    NOT NULL,
    target_year   SMALLINT       NOT NULL,
    period        VARCHAR(20)    NOT NULL,
    period_number TINYINT        NOT NULL,
    amount        DECIMAL(19, 2) NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_time  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_dashboard_department_performance_target PRIMARY KEY (id),
    CONSTRAINT uk_dashboard_department_performance_target_period
        UNIQUE (tenant_id, department_id, metric, target_year, period, period_number),
    CONSTRAINT chk_dashboard_department_performance_target_metric
        CHECK (metric IN ('ACTIVE_CONTRACT')),
    CONSTRAINT chk_dashboard_department_performance_target_year
        CHECK (target_year BETWEEN 2000 AND 9999),
    CONSTRAINT chk_dashboard_department_performance_target_period
        CHECK ((period = 'YEAR' AND period_number = 1)
            OR (period = 'QUARTER' AND period_number BETWEEN 1 AND 4)
            OR (period = 'MONTH' AND period_number BETWEEN 1 AND 12)),
    CONSTRAINT chk_dashboard_department_performance_target_amount
        CHECK (amount > 0),
    CONSTRAINT chk_dashboard_department_performance_target_version
        CHECK (version >= 0),
    CONSTRAINT fk_dashboard_department_performance_target_department
        FOREIGN KEY (tenant_id, department_id)
            REFERENCES organization_department (tenant_id, id)
            ON DELETE RESTRICT,
    INDEX idx_dashboard_department_performance_target_department_year
        (tenant_id, department_id, metric, target_year, period, period_number)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
