ALTER TABLE t_user
    ADD CONSTRAINT uk_t_user_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE authentication_password_reset_token
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    source_tenant_id BIGINT       NOT NULL,
    actor_id         BIGINT       NOT NULL,
    token_digest     CHAR(64)     NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    consumed_at      DATETIME(6)  NULL,
    created_time     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_authentication_password_reset_token PRIMARY KEY (id),
    CONSTRAINT uk_authentication_password_reset_token_digest UNIQUE (token_digest),
    CONSTRAINT fk_authentication_password_reset_token_user
        FOREIGN KEY (source_tenant_id, actor_id)
        REFERENCES t_user (tenant_id, id)
        ON DELETE RESTRICT,
    INDEX idx_authentication_password_reset_token_subject (source_tenant_id, actor_id),
    INDEX idx_authentication_password_reset_token_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
