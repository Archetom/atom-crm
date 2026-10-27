CREATE TABLE authentication_password_login_challenge
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    challenge_digest CHAR(64)    NOT NULL,
    expires_at       DATETIME(6) NOT NULL,
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_authentication_password_login_challenge PRIMARY KEY (id),
    CONSTRAINT uk_authentication_password_login_challenge_digest UNIQUE (challenge_digest),
    INDEX idx_authentication_password_login_challenge_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
