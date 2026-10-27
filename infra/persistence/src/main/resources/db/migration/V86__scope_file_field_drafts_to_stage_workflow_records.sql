ALTER TABLE crm_file_field_draft
    ADD COLUMN stage_workflow_record_id BIGINT NULL AFTER target_id,
    ADD CONSTRAINT chk_crm_file_field_draft_stage_record
        CHECK (stage_workflow_record_id IS NULL OR (
            stage_workflow_record_id > 0
            AND object_type <> 'OA_REQUEST'
            AND target_type IS NULL
            AND target_id IS NULL
            AND oa_form_id IS NULL
            AND oa_form_version IS NULL)),
    ADD CONSTRAINT fk_crm_file_field_draft_stage_record
        FOREIGN KEY (tenant_id, stage_workflow_record_id)
            REFERENCES stage_workflow_record (tenant_id, id) ON DELETE RESTRICT,
    ADD INDEX idx_crm_file_field_draft_stage_record
        (tenant_id, stage_workflow_record_id, state, deadline, id);
