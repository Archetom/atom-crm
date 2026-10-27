CREATE TABLE crm_marketing_activity_participant_department
(
    tenant_id             BIGINT NOT NULL,
    marketing_activity_id BIGINT NOT NULL,
    department_id         BIGINT NOT NULL,

    CONSTRAINT pk_crm_marketing_activity_participant_department
        PRIMARY KEY (tenant_id, marketing_activity_id, department_id),
    CONSTRAINT fk_crm_marketing_activity_participant_department_activity
        FOREIGN KEY (tenant_id, marketing_activity_id)
            REFERENCES crm_marketing_activity (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_marketing_activity_participant_department_department
        FOREIGN KEY (tenant_id, department_id)
            REFERENCES organization_department (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_marketing_activity_participant_department_department
        (tenant_id, department_id, marketing_activity_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
