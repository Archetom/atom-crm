CREATE TABLE dashboard_preference
(
    tenant_id        BIGINT      NOT NULL,
    actor_id         BIGINT      NOT NULL,
    time_range       VARCHAR(30) NOT NULL,
    custom_start_date DATE       NULL,
    custom_end_date   DATE       NULL,
    data_scope       VARCHAR(30) NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_dashboard_preference PRIMARY KEY (tenant_id, actor_id),
    CONSTRAINT chk_dashboard_preference_time_range
        CHECK (time_range IN ('TODAY', 'CURRENT_WEEK', 'CURRENT_MONTH',
                              'CURRENT_QUARTER', 'CURRENT_YEAR', 'CUSTOM')),
    CONSTRAINT chk_dashboard_preference_custom_range
        CHECK ((time_range = 'CUSTOM'
                    AND custom_start_date IS NOT NULL
                    AND custom_end_date IS NOT NULL
                    AND custom_end_date >= custom_start_date
                    AND DATEDIFF(custom_end_date, custom_start_date) <= 365)
               OR (time_range <> 'CUSTOM'
                    AND custom_start_date IS NULL
                    AND custom_end_date IS NULL)),
    CONSTRAINT chk_dashboard_preference_data_scope
        CHECK (data_scope IN ('SELF', 'DEPARTMENT', 'DEPARTMENT_SUBTREE', 'TENANT')),
    CONSTRAINT chk_dashboard_preference_version CHECK (version >= 0),
    CONSTRAINT fk_dashboard_preference_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
