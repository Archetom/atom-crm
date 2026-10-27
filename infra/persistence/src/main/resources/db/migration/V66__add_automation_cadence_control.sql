CREATE TABLE crm_automation_control
(
    control_key      VARCHAR(40) NOT NULL,
    cadence_revision VARCHAR(40) NOT NULL,
    reconciled_time  DATETIME(6) NOT NULL,
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_automation_control PRIMARY KEY (control_key),
    CONSTRAINT chk_crm_automation_control_key CHECK (control_key = 'POOL_CADENCE'),
    CONSTRAINT chk_crm_automation_control_revision CHECK (cadence_revision REGEXP '^B26A_V[1-9][0-9]{0,8}$')
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO crm_automation_control (control_key, cadence_revision, reconciled_time)
VALUES ('POOL_CADENCE', 'B26A_V1', UTC_TIMESTAMP(6));
