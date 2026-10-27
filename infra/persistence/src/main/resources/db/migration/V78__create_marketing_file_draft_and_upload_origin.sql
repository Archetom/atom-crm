-- B29b: anonymous MarketingForm FILE candidates are bound to a hash-only one-shot draft.
-- No plaintext credential, source address, request metadata, owner actor, or Lead ID is stored.
CREATE TABLE crm_marketing_file_draft
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT      NOT NULL,
    marketing_form_id BIGINT      NOT NULL,
    form_version      BIGINT      NOT NULL,
    credential_digest BINARY(32)  NOT NULL,
    deadline          DATETIME(6) NOT NULL,
    state             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reserved_bytes    BIGINT      NOT NULL DEFAULT 0,
    actual_bytes      BIGINT      NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_marketing_file_draft PRIMARY KEY (id),
    CONSTRAINT uk_crm_marketing_file_draft_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_marketing_file_draft_state
        CHECK (state IN ('ACTIVE', 'CONSUMED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT chk_crm_marketing_file_draft_numbers
        CHECK (form_version >= 0 AND reserved_bytes >= 0 AND actual_bytes >= 0 AND version >= 0),
    CONSTRAINT fk_crm_marketing_file_draft_form
        FOREIGN KEY (tenant_id, marketing_form_id)
            REFERENCES crm_marketing_form (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_marketing_file_draft_form_active
        (tenant_id, marketing_form_id, state, deadline, id),
    INDEX idx_crm_marketing_file_draft_expiry (state, deadline, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- A per-form row is the multi-replica capacity lock. It contains no business data.
CREATE TABLE crm_marketing_file_draft_guard
(
    tenant_id         BIGINT      NOT NULL,
    marketing_form_id BIGINT      NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    updated_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crm_marketing_file_draft_guard PRIMARY KEY (tenant_id, marketing_form_id),
    CONSTRAINT fk_crm_marketing_file_draft_guard_form
        FOREIGN KEY (tenant_id, marketing_form_id)
            REFERENCES crm_marketing_form (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_crm_marketing_file_draft_guard_version CHECK (version >= 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Existing internal rows retain MEMBER attribution. Public rows can reference one exact draft.
ALTER TABLE crm_attachment_file
    DROP FOREIGN KEY fk_crm_attachment_file_uploader,
    MODIFY COLUMN uploader_actor_id BIGINT NULL,
    ADD COLUMN upload_origin VARCHAR(30) NULL DEFAULT 'MEMBER' AFTER tenant_id,
    ADD COLUMN public_marketing_draft_id BIGINT NULL AFTER uploader_actor_id;

UPDATE crm_attachment_file SET upload_origin = 'MEMBER' WHERE upload_origin IS NULL;

ALTER TABLE crm_attachment_file
    MODIFY COLUMN upload_origin VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
    ADD CONSTRAINT chk_crm_attachment_file_upload_origin CHECK (
        (upload_origin = 'MEMBER' AND uploader_actor_id IS NOT NULL
            AND public_marketing_draft_id IS NULL)
        OR (upload_origin = 'PUBLIC_MARKETING_FORM' AND uploader_actor_id IS NULL
            AND public_marketing_draft_id IS NOT NULL)),
    ADD CONSTRAINT fk_crm_attachment_file_uploader
        FOREIGN KEY (tenant_id, uploader_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_crm_attachment_file_public_marketing_draft
        FOREIGN KEY (tenant_id, public_marketing_draft_id)
            REFERENCES crm_marketing_file_draft (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_crm_attachment_file_public_marketing_draft_file
        UNIQUE (tenant_id, public_marketing_draft_id, id),
    ADD INDEX idx_crm_attachment_file_public_marketing_draft
        (tenant_id, public_marketing_draft_id, publication_state, id);

-- A candidate cannot be rewired to a public file issued by another draft: the three-column
-- foreign key terminates in the attachment's exact public draft origin.
CREATE TABLE crm_marketing_file_draft_candidate
(
    tenant_id       BIGINT      NOT NULL,
    draft_id        BIGINT      NOT NULL,
    object_type     VARCHAR(30) NOT NULL DEFAULT 'LEAD',
    field_id        BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    ordinal         SMALLINT    NOT NULL,
    state           VARCHAR(20) NOT NULL DEFAULT 'STAGED',
    content_version BIGINT      NULL,
    evidence_digest BINARY(32)  NULL,
    created_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_marketing_file_draft_candidate PRIMARY KEY (tenant_id, draft_id, field_id, ordinal),
    CONSTRAINT uk_crm_marketing_file_draft_candidate_file UNIQUE (tenant_id, file_id),
    CONSTRAINT chk_crm_marketing_file_draft_candidate_object CHECK (object_type = 'LEAD'),
    CONSTRAINT chk_crm_marketing_file_draft_candidate_ordinal CHECK (ordinal BETWEEN 0 AND 9),
    CONSTRAINT chk_crm_marketing_file_draft_candidate_state
        CHECK (state IN ('STAGED', 'BOUND', 'WITHDRAWN')),
    CONSTRAINT chk_crm_marketing_file_draft_candidate_evidence CHECK (
        (state IN ('STAGED', 'WITHDRAWN') AND content_version IS NULL AND evidence_digest IS NULL)
        OR (state = 'BOUND' AND content_version >= 1 AND evidence_digest IS NOT NULL)),
    CONSTRAINT fk_crm_marketing_file_draft_candidate_draft
        FOREIGN KEY (tenant_id, draft_id)
            REFERENCES crm_marketing_file_draft (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_marketing_file_draft_candidate_field
        FOREIGN KEY (tenant_id, object_type, field_id)
            REFERENCES custom_field_definition (tenant_id, object_type, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_marketing_file_draft_candidate_file_origin
        FOREIGN KEY (tenant_id, draft_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, public_marketing_draft_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_marketing_file_draft_candidate_draft
        (tenant_id, draft_id, state, field_id, ordinal)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
