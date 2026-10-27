ALTER TABLE crm_notification_delivery
    ADD COLUMN endpoint_digest_version VARCHAR(64) NULL AFTER endpoint_digest,
    ADD COLUMN effect_certainty VARCHAR(16) NOT NULL DEFAULT 'NO_EFFECT' AFTER guarantee;

UPDATE crm_notification_delivery
SET endpoint_digest_version = CASE WHEN endpoint_digest IS NULL THEN NULL ELSE 'LEGACY_SHA256' END,
    effect_certainty = CASE
        WHEN status = 'ACCEPTED' THEN 'ACCEPTED'
        WHEN status = 'REJECTED' OR guarantee IS NULL THEN 'NO_EFFECT'
        ELSE 'AMBIGUOUS'
    END;

ALTER TABLE crm_notification_delivery
    ADD CONSTRAINT chk_crm_notification_delivery_endpoint_digest_version
        CHECK ((endpoint_digest IS NULL AND endpoint_digest_version IS NULL)
               OR (endpoint_digest IS NOT NULL AND endpoint_digest_version IS NOT NULL
                   AND CHAR_LENGTH(endpoint_digest_version) BETWEEN 1 AND 64)),
    ADD CONSTRAINT chk_crm_notification_delivery_effect_certainty
        CHECK (effect_certainty IN ('NO_EFFECT', 'AMBIGUOUS', 'ACCEPTED'));
