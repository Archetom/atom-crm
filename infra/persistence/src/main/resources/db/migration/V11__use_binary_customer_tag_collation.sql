ALTER TABLE crm_customer_tag
    MODIFY tag_name VARCHAR(50)
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_bin
        NOT NULL;
