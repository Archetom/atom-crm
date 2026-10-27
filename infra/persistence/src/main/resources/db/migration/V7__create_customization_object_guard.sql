CREATE TABLE customization_object_guard
(
    tenant_id    BIGINT      NOT NULL,
    object_type  VARCHAR(30) NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_customization_object_guard PRIMARY KEY (tenant_id, object_type),
    CONSTRAINT fk_customization_object_guard_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES organization_tenant (tenant_id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO customization_object_guard (tenant_id, object_type)
SELECT DISTINCT tenant_id, object_type
FROM custom_field_definition;
