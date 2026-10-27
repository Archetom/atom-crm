ALTER TABLE crm_customer_address
    DROP CHECK chk_crm_customer_address_coordinates;

ALTER TABLE crm_customer_address
    ADD CONSTRAINT chk_crm_customer_address_coordinates
        CHECK ((longitude IS NULL AND latitude IS NULL)
            OR (longitude IS NOT NULL AND latitude IS NOT NULL
                AND longitude BETWEEN -180 AND 180
                AND latitude BETWEEN -90 AND 90));
