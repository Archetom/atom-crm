CREATE TABLE approval_instance_manager
(
    tenant_id   BIGINT      NOT NULL,
    instance_id BIGINT      NOT NULL,
    actor_id    BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_approval_instance_manager
        PRIMARY KEY (tenant_id, instance_id, actor_id),
    CONSTRAINT fk_approval_instance_manager_root
        FOREIGN KEY (tenant_id, instance_id)
            REFERENCES approval_instance (tenant_id, id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_approval_instance_manager_member
        FOREIGN KEY (tenant_id, actor_id)
            REFERENCES organization_membership (tenant_id, actor_id)
            ON DELETE RESTRICT,

    INDEX idx_approval_instance_manager_member
        (tenant_id, actor_id, instance_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Existing B11 instances predate the dedicated snapshot relation. The current
-- definition is the only recoverable source during upgrade; all new instances
-- persist the exact submission-time set in the application transaction.
INSERT INTO approval_instance_manager (tenant_id, instance_id, actor_id)
SELECT instance.tenant_id, instance.id, manager.actor_id
  FROM approval_instance instance
  JOIN approval_definition definition
    ON definition.tenant_id = instance.tenant_id
   AND definition.id = instance.definition_id
  JOIN JSON_TABLE(
        definition.config_json,
        '$.managerMemberIds[*]' COLUMNS (actor_id BIGINT PATH '$')
  ) AS manager ON TRUE;
