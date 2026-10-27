ALTER TABLE crm_lead
    ADD COLUMN converted_contact_id BIGINT NULL AFTER customer_id,
    ADD CONSTRAINT fk_crm_lead_converted_contact
        FOREIGN KEY (tenant_id, converted_contact_id)
            REFERENCES crm_contact (tenant_id, id)
            ON DELETE RESTRICT,
    ADD CONSTRAINT chk_crm_lead_converted_associations
        CHECK (status <> 'CONVERTED'
            OR (customer_id IS NOT NULL AND converted_contact_id IS NOT NULL)),
    ADD INDEX idx_crm_lead_tenant_converted_contact
        (tenant_id, converted_contact_id, status);
