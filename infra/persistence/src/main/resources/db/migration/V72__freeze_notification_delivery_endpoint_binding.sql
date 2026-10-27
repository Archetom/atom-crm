ALTER TABLE crm_notification_delivery
    ADD COLUMN endpoint_digest CHAR(64) NULL AFTER payload_digest,
    ADD CONSTRAINT chk_crm_notification_delivery_endpoint_digest
        CHECK (endpoint_digest IS NULL OR endpoint_digest REGEXP '^[0-9a-f]{64}$');
