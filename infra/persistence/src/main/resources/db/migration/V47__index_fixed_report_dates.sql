CREATE INDEX idx_crm_opportunity_report_date
    ON crm_opportunity
        (tenant_id, status, outcome, outcome_time, estimated_amount, owner_actor_id);

CREATE INDEX idx_crm_contract_report_date
    ON crm_contract
        (tenant_id, status, order_date, total_amount, owner_actor_id);

CREATE INDEX idx_crm_receivable_report_date
    ON crm_receivable
        (tenant_id, status, received_date, amount, owner_actor_id);
