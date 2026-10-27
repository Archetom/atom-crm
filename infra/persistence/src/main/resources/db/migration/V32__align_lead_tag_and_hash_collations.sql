ALTER TABLE crm_lead_tag
    MODIFY tag_name VARCHAR(50)
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_bin
        NOT NULL;

ALTER TABLE crm_lead_intake_client
    MODIFY api_key_hash CHAR(64)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL;

ALTER TABLE crm_customer_unique_field_value
    MODIFY normalized_hash CHAR(64)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL;

ALTER TABLE crm_contact_unique_field_value
    MODIFY normalized_hash CHAR(64)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL;

ALTER TABLE crm_lead_unique_field_value
    MODIFY normalized_hash CHAR(64)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL;
