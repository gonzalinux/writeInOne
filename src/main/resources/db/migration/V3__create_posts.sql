CREATE TABLE posts (
    id           BIGSERIAL   PRIMARY KEY,
    site_id      BIGINT      NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    status       TEXT        NOT NULL DEFAULT 'draft',
    cover_url    TEXT,
    view_count   BIGINT      NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    scheduled_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE post_translations (
    id         BIGSERIAL   PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    site_id    BIGINT      NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    lang       TEXT        NOT NULL,
    title      TEXT        NOT NULL,
    slug       TEXT        NOT NULL,
    body       TEXT        NOT NULL,
    excerpt    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, lang),
    UNIQUE (site_id, lang, slug)
);
