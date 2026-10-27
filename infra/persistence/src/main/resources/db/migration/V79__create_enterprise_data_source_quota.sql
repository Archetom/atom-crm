CREATE TABLE crm_enterprise_data_source_quota
(
    source_key        VARCHAR(48) NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    request_count     INT         NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0,
    updated_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_enterprise_data_source_quota PRIMARY KEY (source_key),
    CONSTRAINT chk_crm_enterprise_data_source_quota_count CHECK (request_count >= 0),
    CONSTRAINT chk_crm_enterprise_data_source_quota_version CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
