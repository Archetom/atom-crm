CREATE INDEX idx_crm_opportunity_report_expected_close
    ON crm_opportunity (tenant_id, status, expected_close_date);

CREATE INDEX idx_crm_opportunity_report_next_contact
    ON crm_opportunity (tenant_id, status, next_contact_time);

CREATE INDEX idx_crm_opportunity_report_outcome_time
    ON crm_opportunity (tenant_id, status, outcome_time);

CREATE INDEX idx_crm_opportunity_report_created_time
    ON crm_opportunity (tenant_id, status, created_time);

CREATE INDEX idx_crm_contract_report_start_date
    ON crm_contract (tenant_id, status, start_date);

CREATE INDEX idx_crm_contract_report_end_date
    ON crm_contract (tenant_id, status, end_date);

CREATE INDEX idx_crm_contract_report_created_time
    ON crm_contract (tenant_id, status, created_time);

CREATE INDEX idx_crm_receivable_report_created_time
    ON crm_receivable (tenant_id, status, created_time);
