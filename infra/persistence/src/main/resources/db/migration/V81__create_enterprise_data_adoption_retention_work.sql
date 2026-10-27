ALTER TABLE crm_enterprise_data_adoption
    ADD CONSTRAINT uk_crm_enterprise_data_adoption_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE crm_enterprise_data_adoption_retention_work
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id        BIGINT       NOT NULL,
    adoption_id      BIGINT       NOT NULL,
    delete_after     DATETIME(6)  NOT NULL,
    work_state       VARCHAR(16)  NOT NULL DEFAULT 'READY',
    lease_owner      CHAR(36)     NULL,
    lease_expires_at DATETIME(6)  NULL,
    lease_fence      BIGINT       NOT NULL DEFAULT 0,
    created_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_enterprise_data_adoption_retention_work PRIMARY KEY (id),
    CONSTRAINT uk_crm_enterprise_data_adoption_retention_work_adoption UNIQUE (tenant_id, adoption_id),
    CONSTRAINT chk_crm_enterprise_data_adoption_retention_work_state
        CHECK (work_state IN ('READY', 'CLAIMED')),
    CONSTRAINT chk_crm_enterprise_data_adoption_retention_work_fence CHECK (lease_fence >= 0),
    CONSTRAINT fk_crm_enterprise_data_adoption_retention_work_adoption
        FOREIGN KEY (tenant_id, adoption_id)
            REFERENCES crm_enterprise_data_adoption (tenant_id, id)
            ON DELETE CASCADE,
    INDEX idx_crm_enterprise_data_adoption_retention_work_due
        (work_state, delete_after, id),
    INDEX idx_crm_enterprise_data_adoption_retention_work_lease
        (work_state, lease_expires_at, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_as_cs;
