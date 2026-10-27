-- B22 core reliability records. Provider frames, E.164 values, recording references and errors
-- deliberately do not appear in this schema; private adapters own encrypted payloads separately.
CREATE TABLE crm_call_interaction
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id            BIGINT      NOT NULL,
    connector_id         BIGINT      NOT NULL,
    interaction_ref      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    direction            VARCHAR(16) NOT NULL,
    dispatch_state       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    observation_state    VARCHAR(20) NOT NULL DEFAULT 'COMPLETE',
    version              BIGINT      NOT NULL DEFAULT 0,
    created_time         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_call_interaction PRIMARY KEY (id),
    CONSTRAINT uk_crm_call_interaction_ref UNIQUE (tenant_id, interaction_ref),
    CONSTRAINT uk_crm_call_interaction_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_crm_call_interaction_direction CHECK (direction IN ('OUTBOUND', 'INBOUND')),
    CONSTRAINT chk_crm_call_interaction_dispatch CHECK (dispatch_state IN ('PENDING', 'CONFIRMED', 'REJECTED', 'AMBIGUOUS')),
    CONSTRAINT chk_crm_call_interaction_observation CHECK (observation_state IN ('COMPLETE', 'GAP_OPEN', 'RECONCILING', 'UNRESOLVED')),
    CONSTRAINT chk_crm_call_interaction_version CHECK (version >= 0),
    CONSTRAINT fk_crm_call_interaction_connector
        FOREIGN KEY (tenant_id, connector_id) REFERENCES crm_connector_registration (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_call_interaction_connector (tenant_id, connector_id, created_time, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_call_request_binding
(
    tenant_id              BIGINT      NOT NULL,
    initiating_actor_id    BIGINT      NOT NULL,
    request_id             VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest         BINARY(32)  NOT NULL,
    interaction_id         BIGINT      NOT NULL,
    created_time           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_call_request_binding PRIMARY KEY (tenant_id, initiating_actor_id, request_id),
    CONSTRAINT chk_crm_call_request_binding_id CHECK (request_id REGEXP '^[A-Za-z0-9_-]{16,128}$'),
    CONSTRAINT fk_crm_call_request_binding_member
        FOREIGN KEY (tenant_id, initiating_actor_id) REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_call_request_binding_interaction
        FOREIGN KEY (tenant_id, interaction_id) REFERENCES crm_call_interaction (tenant_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_call_start_outbox
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id            BIGINT      NOT NULL,
    connector_id         BIGINT      NOT NULL,
    interaction_id       BIGINT      NOT NULL,
    command_id           BINARY(16)  NOT NULL,
    command_digest       BINARY(32)  NOT NULL,
    state                VARCHAR(16) NOT NULL DEFAULT 'READY',
    lease_owner          BINARY(16)  NULL,
    lease_fence          BIGINT      NOT NULL DEFAULT 0,
    lease_until          DATETIME(6) NULL,
    attempt              BIGINT      NOT NULL DEFAULT 0,
    created_time         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_crm_call_start_outbox PRIMARY KEY (id),
    CONSTRAINT uk_crm_call_start_outbox_command UNIQUE (tenant_id, connector_id, command_id),
    CONSTRAINT uk_crm_call_start_outbox_interaction UNIQUE (tenant_id, interaction_id),
    CONSTRAINT chk_crm_call_start_outbox_state CHECK (state IN ('READY', 'LEASED', 'SETTLED')),
    CONSTRAINT chk_crm_call_start_outbox_numbers CHECK (lease_fence >= 0 AND attempt >= 0),
    CONSTRAINT chk_crm_call_start_outbox_lease CHECK ((state = 'LEASED' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR (state <> 'LEASED' AND lease_owner IS NULL AND lease_until IS NULL)),
    CONSTRAINT fk_crm_call_start_outbox_connector
        FOREIGN KEY (tenant_id, connector_id) REFERENCES crm_connector_registration (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_call_start_outbox_interaction
        FOREIGN KEY (tenant_id, interaction_id) REFERENCES crm_call_interaction (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_call_start_outbox_claim (state, lease_until, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_call_fact_inbox
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id            BIGINT      NOT NULL,
    connector_id         BIGINT      NOT NULL,
    delivery_ref         BINARY(32)  NOT NULL,
    fact_digest          BINARY(32)  NOT NULL,
    outcome              VARCHAR(20) NOT NULL,
    interaction_id       BIGINT      NULL,
    created_time         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    settled_time         DATETIME(6) NULL,

    CONSTRAINT pk_crm_call_fact_inbox PRIMARY KEY (id),
    CONSTRAINT uk_crm_call_fact_inbox_delivery UNIQUE (tenant_id, connector_id, delivery_ref),
    CONSTRAINT chk_crm_call_fact_inbox_outcome CHECK (outcome IN ('PENDING', 'APPLIED', 'NO_CHANGE', 'REJECTED_FINAL', 'CONFLICT')),
    CONSTRAINT chk_crm_call_fact_inbox_settled CHECK ((outcome = 'PENDING' AND settled_time IS NULL)
        OR (outcome <> 'PENDING' AND settled_time IS NOT NULL)),
    CONSTRAINT fk_crm_call_fact_inbox_connector
        FOREIGN KEY (tenant_id, connector_id) REFERENCES crm_connector_registration (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_call_fact_inbox_interaction
        FOREIGN KEY (tenant_id, interaction_id) REFERENCES crm_call_interaction (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_call_fact_inbox_pending (tenant_id, connector_id, outcome, created_time, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE crm_call_observation_gap
(
    tenant_id            BIGINT      NOT NULL,
    connector_id         BIGINT      NOT NULL,
    gap_ref              BINARY(16)  NOT NULL,
    state                VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    opened_time          DATETIME(6) NOT NULL,
    closed_time          DATETIME(6) NULL,
    version              BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_call_observation_gap PRIMARY KEY (tenant_id, connector_id, gap_ref),
    CONSTRAINT chk_crm_call_observation_gap_state CHECK (state IN ('OPEN', 'COVERED', 'UNRESOLVED')),
    CONSTRAINT chk_crm_call_observation_gap_close CHECK ((state = 'OPEN' AND closed_time IS NULL)
        OR (state <> 'OPEN' AND closed_time IS NOT NULL)),
    CONSTRAINT chk_crm_call_observation_gap_version CHECK (version >= 0),
    CONSTRAINT fk_crm_call_observation_gap_connector
        FOREIGN KEY (tenant_id, connector_id) REFERENCES crm_connector_registration (tenant_id, id) ON DELETE RESTRICT,
    INDEX idx_crm_call_observation_gap_open (tenant_id, connector_id, state, opened_time)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
