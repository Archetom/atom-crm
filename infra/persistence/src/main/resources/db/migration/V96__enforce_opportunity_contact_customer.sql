ALTER TABLE crm_opportunity_contact
    ADD COLUMN customer_id BIGINT NULL AFTER contact_id;

UPDATE crm_opportunity_contact opportunity_contact
INNER JOIN crm_opportunity opportunity
    ON opportunity.tenant_id = opportunity_contact.tenant_id
    AND opportunity.id = opportunity_contact.opportunity_id
SET opportunity_contact.customer_id = opportunity.customer_id;

ALTER TABLE crm_opportunity_contact
    MODIFY COLUMN customer_id BIGINT NOT NULL,
    DROP FOREIGN KEY fk_crm_opportunity_contact_root,
    DROP FOREIGN KEY fk_crm_opportunity_contact_target,
    ADD INDEX idx_crm_opportunity_contact_root_customer
        (tenant_id, opportunity_id, customer_id),
    ADD INDEX idx_crm_opportunity_contact_target_customer
        (tenant_id, contact_id, customer_id),
    ADD CONSTRAINT fk_crm_opportunity_contact_root_customer
        FOREIGN KEY (tenant_id, opportunity_id, customer_id)
            REFERENCES crm_opportunity (tenant_id, id, customer_id)
            ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_opportunity_contact_target_customer
        FOREIGN KEY (tenant_id, contact_id, customer_id)
            REFERENCES crm_contact (tenant_id, id, customer_id)
            ON DELETE RESTRICT;
