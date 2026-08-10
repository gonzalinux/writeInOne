ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
UPDATE users
SET email_verified = true;

CREATE TABLE email_verification_tokens
(
    token_hash TEXT PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE password_reset_tokens
(
    token_hash TEXT PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);
