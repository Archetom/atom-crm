CREATE TABLE crm_customer_pool_release_history
(
    id                      BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT      NOT NULL,
    pool_id                 BIGINT      NOT NULL,
    customer_id             BIGINT      NOT NULL,
    previous_owner_actor_id BIGINT      NULL,
    released_time           DATETIME(6) NOT NULL,

    CONSTRAINT pk_crm_customer_pool_release_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_customer_pool_release_history_pool
        FOREIGN KEY (tenant_id, pool_id) REFERENCES crm_customer_pool (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_release_history_customer
        FOREIGN KEY (tenant_id, customer_id) REFERENCES crm_customer (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_crm_customer_pool_release_history_previous_owner
        FOREIGN KEY (tenant_id, previous_owner_actor_id)
            REFERENCES organization_membership (tenant_id, actor_id) ON DELETE RESTRICT,
    INDEX idx_crm_customer_pool_release_dashboard_time
        (tenant_id, released_time, previous_owner_actor_id, pool_id),
    INDEX idx_crm_customer_pool_release_dashboard_actor
        (tenant_id, previous_owner_actor_id, released_time, pool_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

ALTER TABLE crm_customer_pool_claim_history
    ADD INDEX idx_crm_customer_pool_claim_dashboard_time
        (tenant_id, is_assigned, claimed_time, actor_id, pool_id),
    ADD INDEX idx_crm_customer_pool_claim_dashboard_actor
        (tenant_id, actor_id, is_assigned, claimed_time, pool_id);
