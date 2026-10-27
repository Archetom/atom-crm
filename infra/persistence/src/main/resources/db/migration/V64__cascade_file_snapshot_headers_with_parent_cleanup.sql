-- FILE snapshot headers are immutable while their approval/stage parent exists.  Test and
-- administrative parent cleanup must not strand a header that blocks the parent root deletion.
ALTER TABLE crm_approval_instance_file_snapshot
    DROP FOREIGN KEY fk_crm_approval_instance_file_snapshot_instance;

ALTER TABLE crm_approval_instance_file_snapshot
    ADD CONSTRAINT fk_crm_approval_instance_file_snapshot_instance
        FOREIGN KEY (tenant_id, instance_id) REFERENCES approval_instance (tenant_id, id) ON DELETE CASCADE;

ALTER TABLE crm_stage_workflow_file_snapshot
    DROP FOREIGN KEY fk_crm_stage_workflow_file_snapshot_record;

ALTER TABLE crm_stage_workflow_file_snapshot
    ADD CONSTRAINT fk_crm_stage_workflow_file_snapshot_record
        FOREIGN KEY (tenant_id, record_id) REFERENCES stage_workflow_record (tenant_id, id) ON DELETE CASCADE;
