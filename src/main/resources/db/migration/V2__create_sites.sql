CREATE TABLE sites (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    name        TEXT        NOT NULL,
    domain      TEXT        NOT NULL UNIQUE,
    description       TEXT,
    styles_url        TEXT,
    available_themes  TEXT[]  NOT NULL DEFAULT '{light}',
    languages   TEXT[]      NOT NULL DEFAULT '{en}',
    config      JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
