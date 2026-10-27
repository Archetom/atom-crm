ALTER TABLE crm_attachment_job
    DROP CHECK chk_crm_attachment_job_state,
    ADD CONSTRAINT chk_crm_attachment_job_state
        CHECK (state IN ('PENDING', 'PAUSED', 'LEASED', 'EFFECT_RECORDED', 'SETTLED', 'DEAD'));
