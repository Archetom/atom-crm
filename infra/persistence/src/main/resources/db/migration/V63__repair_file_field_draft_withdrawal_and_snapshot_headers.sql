-- V62 intentionally retains withdrawn draft rows so a file can never be staged twice.
-- Such rows were never bound, therefore their immutable content evidence is correctly absent.
ALTER TABLE crm_file_field_draft_file
    DROP CHECK chk_crm_file_field_draft_file_evidence;

ALTER TABLE crm_file_field_draft_file
    ADD CONSTRAINT chk_crm_file_field_draft_file_evidence CHECK (
        (state IN ('STAGED', 'WITHDRAWN') AND content_version IS NULL AND evidence_digest IS NULL)
        OR (state = 'BOUND' AND content_version >= 1 AND evidence_digest IS NOT NULL));

-- A snapshot may be intentionally empty.  Headers make that capture immutable rather than
-- inferring it from the presence of child facts.
CREATE TABLE crm_approval_instance_file_snapshot
(
    tenant_id    BIGINT      NOT NULL,
    instance_id  BIGINT      NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_approval_instance_file_snapshot PRIMARY KEY (tenant_id, instance_id),
    CONSTRAINT fk_crm_approval_instance_file_snapshot_instance
        FOREIGN KEY (tenant_id, instance_id) REFERENCES approval_instance (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_stage_workflow_file_snapshot
(
    tenant_id    BIGINT      NOT NULL,
    record_id    BIGINT      NOT NULL,
    stage_code   VARCHAR(64) NOT NULL,
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_stage_workflow_file_snapshot PRIMARY KEY (tenant_id, record_id, stage_code),
    CONSTRAINT fk_crm_stage_workflow_file_snapshot_record
        FOREIGN KEY (tenant_id, record_id) REFERENCES stage_workflow_record (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
