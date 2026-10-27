CREATE TABLE worklist_acknowledgement
(
    tenant_id     BIGINT      NOT NULL,
    actor_id      BIGINT      NOT NULL,
    kind          VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id     BIGINT      NOT NULL,
    source_version BIGINT     NOT NULL,
    due_time      DATETIME(6) NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_worklist_acknowledgement PRIMARY KEY
        (tenant_id, actor_id, kind, source_id, source_version, due_time),
    CONSTRAINT chk_worklist_acknowledgement_source CHECK (source_id > 0),
    CONSTRAINT chk_worklist_acknowledgement_version CHECK
        (source_version >= 0 AND version >= 0),
    CONSTRAINT fk_worklist_acknowledgement_member FOREIGN KEY (tenant_id, actor_id)
        REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_worklist_acknowledgement_member_due (tenant_id, actor_id, due_time, kind)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
