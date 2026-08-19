CREATE TABLE post_translation_versions (
    id                   BIGSERIAL   PRIMARY KEY,
    post_translation_id  BIGINT      NOT NULL REFERENCES post_translations(id) ON DELETE CASCADE,
    version_number       INT         NOT NULL,
    status               TEXT        NOT NULL DEFAULT 'draft',
    title                TEXT        NOT NULL,
    slug                 TEXT        NOT NULL,
    body                 TEXT        NOT NULL,
    excerpt              TEXT,
    author_id            BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at         TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_translation_id, version_number)
);

CREATE INDEX ON post_translation_versions (post_translation_id, status);

ALTER TABLE post_translations ADD COLUMN current_version_id BIGINT REFERENCES post_translation_versions(id);

ALTER TABLE post_translations DROP CONSTRAINT post_translations_site_id_lang_slug_key;
CREATE UNIQUE INDEX post_translations_site_lang_slug_live_uq
    ON post_translations (site_id, lang, slug) WHERE current_version_id IS NOT NULL;

-- Backfill: every existing translation's current content becomes its published version 1.
INSERT INTO post_translation_versions
    (post_translation_id, version_number, status, title, slug, body, excerpt, created_at, published_at, updated_at)
SELECT id, 1, 'published', title, slug, body, excerpt, created_at, updated_at, updated_at
FROM post_translations;

UPDATE post_translations pt
SET current_version_id = ptv.id
FROM post_translation_versions ptv
WHERE ptv.post_translation_id = pt.id AND ptv.version_number = 1;
