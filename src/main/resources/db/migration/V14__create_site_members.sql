CREATE TABLE site_members
(
    site_id    BIGINT      NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    role       TEXT        NOT NULL DEFAULT 'WRITER',
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, site_id)
);

CREATE INDEX idx_site_members_site_id ON site_members (site_id);

INSERT INTO site_members (user_id, site_id, role)
SELECT user_id, id, 'ADMIN'
FROM sites;