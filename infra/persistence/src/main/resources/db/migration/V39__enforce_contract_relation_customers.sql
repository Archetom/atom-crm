ALTER TABLE crm_opportunity
    ADD CONSTRAINT uk_crm_opportunity_tenant_id_customer
        UNIQUE (tenant_id, id, customer_id);

ALTER TABLE crm_contact
    ADD CONSTRAINT uk_crm_contact_tenant_id_customer
        UNIQUE (tenant_id, id, customer_id);

ALTER TABLE crm_contract
    DROP FOREIGN KEY fk_crm_contract_opportunity,
    DROP FOREIGN KEY fk_crm_contract_contact,
    ADD CONSTRAINT fk_crm_contract_opportunity_customer
        FOREIGN KEY (tenant_id, opportunity_id, customer_id)
            REFERENCES crm_opportunity (tenant_id, id, customer_id)
            ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_contract_contact_customer
        FOREIGN KEY (tenant_id, contact_id, customer_id)
            REFERENCES crm_contact (tenant_id, id, customer_id)
            ON DELETE RESTRICT;
