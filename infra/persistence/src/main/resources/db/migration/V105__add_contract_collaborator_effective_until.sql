ALTER TABLE crm_contract_collaborator
    ADD COLUMN effective_until DATE NULL AFTER access_level,
    ADD CONSTRAINT chk_crm_contract_collaborator_effective_until
        CHECK (effective_until IS NULL
            OR (effective_until >= '1000-01-01' AND effective_until <= '9999-12-31'));

CREATE INDEX idx_crm_contract_collaborator_effective_access
    ON crm_contract_collaborator (tenant_id, actor_id, effective_until, contract_id);
