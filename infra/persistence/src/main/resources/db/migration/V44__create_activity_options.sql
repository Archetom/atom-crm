CREATE TABLE crm_activity_option
(
    tenant_id     BIGINT       NOT NULL,
    option_type   VARCHAR(20)  NOT NULL,
    option_value  VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    label         VARCHAR(200) NOT NULL,
    display_order INT          NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_activity_option PRIMARY KEY (tenant_id, option_type, option_value),
    CONSTRAINT chk_crm_activity_option_type CHECK (option_type IN ('METHOD', 'TEMPLATE')),
    CONSTRAINT chk_crm_activity_option_order CHECK (display_order BETWEEN 0 AND 10000),
    CONSTRAINT chk_crm_activity_option_version CHECK (version >= 0),
    CONSTRAINT fk_crm_activity_option_tenant FOREIGN KEY (tenant_id)
        REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT,

    INDEX idx_crm_activity_option_order (tenant_id, option_type, display_order, option_value)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO crm_activity_option
    (tenant_id, option_type, option_value, label, display_order)
SELECT tenant_id, option_type, option_value, label, display_order
FROM organization_tenant
JOIN (
    SELECT 'METHOD' option_type, 'CALL' option_value, 'Call' label, 10 display_order
    UNION ALL SELECT 'METHOD', 'MEETING', 'Meeting', 20
    UNION ALL SELECT 'METHOD', 'EMAIL', 'Email', 30
    UNION ALL SELECT 'TEMPLATE', 'GENERAL_FOLLOW_UP', 'General follow-up', 10
) defaults ON 1 = 1;
