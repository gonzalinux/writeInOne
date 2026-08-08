CREATE TABLE site_invitations
(
    id          BIGSERIAL PRIMARY KEY,
    site_id     BIGINT      NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    role        TEXT        NOT NULL DEFAULT 'WRITER',
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ
);

CREATE INDEX idx_site_invitations_site_id ON site_invitations (site_id);