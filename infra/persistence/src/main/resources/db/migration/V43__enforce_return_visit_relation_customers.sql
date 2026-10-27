ALTER TABLE crm_return_visit
    DROP FOREIGN KEY fk_crm_return_visit_contact,
    DROP FOREIGN KEY fk_crm_return_visit_contract,
    ADD CONSTRAINT fk_crm_return_visit_contact_customer
        FOREIGN KEY (tenant_id, contact_id, customer_id)
            REFERENCES crm_contact (tenant_id, id, customer_id)
            ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_return_visit_contract_customer
        FOREIGN KEY (tenant_id, contract_id, customer_id)
            REFERENCES crm_contract (tenant_id, id, customer_id)
            ON DELETE RESTRICT;
