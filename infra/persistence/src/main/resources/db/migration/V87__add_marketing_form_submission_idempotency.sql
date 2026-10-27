ALTER TABLE crm_marketing_form_submission
    ADD COLUMN idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER lead_id,
    ADD COLUMN submission_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER idempotency_key;

UPDATE crm_marketing_form_submission
SET idempotency_key = CONCAT('legacy-', id),
    submission_digest = REPEAT('0', 64)
WHERE idempotency_key IS NULL OR submission_digest IS NULL;

ALTER TABLE crm_marketing_form_submission
    MODIFY COLUMN idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY COLUMN submission_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT uk_crm_marketing_form_submission_idempotency
        UNIQUE (tenant_id, marketing_form_id, idempotency_key),
    ADD CONSTRAINT chk_crm_marketing_form_submission_idempotency_key
        CHECK (idempotency_key REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
    ADD CONSTRAINT chk_crm_marketing_form_submission_submission_digest
        CHECK (submission_digest REGEXP '^[0-9a-f]{64}$');
