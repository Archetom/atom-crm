-- Product images are a first-class media relation, not a FILE custom-field value or a generic
-- name-based attachment convention.  Draft candidates deliberately have no parent relation
-- until Product creation atomically consumes the exact private set.
ALTER TABLE crm_attachment_relation
    ADD COLUMN product_image_type VARCHAR(20) NULL AFTER product_id,
    ADD INDEX idx_crm_attachment_relation_product_image
        (tenant_id, product_id, product_image_type, relation_state, file_id),
    ADD CONSTRAINT chk_crm_attachment_relation_product_image_type CHECK (
        product_image_type IS NULL
        OR (target_type = 'PRODUCT' AND product_image_type IN ('PRIMARY', 'DETAIL'))
    );

CREATE TABLE crm_product_image_draft
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT      NOT NULL,
    uploader_actor_id BIGINT      NOT NULL,
    deadline          DATETIME(6) NOT NULL,
    state             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version           BIGINT      NOT NULL DEFAULT 0,
    created_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_product_image_draft PRIMARY KEY (id),
    CONSTRAINT uk_crm_product_image_draft_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_product_image_draft_state
        CHECK (state IN ('ACTIVE', 'CONSUMED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT chk_crm_product_image_draft_version CHECK (version >= 0),
    CONSTRAINT fk_crm_product_image_draft_tenant
        FOREIGN KEY (tenant_id) REFERENCES organization_tenant (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_product_image_draft_uploader
        FOREIGN KEY (tenant_id, uploader_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,

    INDEX idx_crm_product_image_draft_expiry (state, deadline, id),
    INDEX idx_crm_product_image_draft_uploader (tenant_id, uploader_actor_id, state, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_product_image_draft_file
(
    tenant_id          BIGINT      NOT NULL,
    draft_id           BIGINT      NOT NULL,
    image_type         VARCHAR(20) NOT NULL,
    file_id            BIGINT      NOT NULL,
    ordinal            SMALLINT    NOT NULL,
    state              VARCHAR(20) NOT NULL DEFAULT 'STAGED',
    created_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_product_image_draft_file PRIMARY KEY (tenant_id, draft_id, image_type, ordinal),
    CONSTRAINT uk_crm_product_image_draft_file_file UNIQUE (tenant_id, file_id),
    CONSTRAINT uk_crm_product_image_draft_file_slot UNIQUE (tenant_id, draft_id, image_type, file_id),
    CONSTRAINT chk_crm_product_image_draft_file_type CHECK (image_type IN ('PRIMARY', 'DETAIL')),
    CONSTRAINT chk_crm_product_image_draft_file_ordinal CHECK (ordinal BETWEEN 0 AND 8),
    CONSTRAINT chk_crm_product_image_draft_file_state CHECK (state IN ('STAGED', 'BOUND', 'WITHDRAWN')),
    CONSTRAINT fk_crm_product_image_draft_file_draft
        FOREIGN KEY (tenant_id, draft_id)
            REFERENCES crm_product_image_draft (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_product_image_draft_file_file
        FOREIGN KEY (tenant_id, file_id)
            REFERENCES crm_attachment_file (tenant_id, id) ON DELETE RESTRICT,

    INDEX idx_crm_product_image_draft_file_file (tenant_id, file_id, state)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
