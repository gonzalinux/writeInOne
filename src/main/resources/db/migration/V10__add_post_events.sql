CREATE TABLE post_events
(
    id               BIGSERIAL PRIMARY KEY,
    post_id          BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    event_type       VARCHAR(50) NOT NULL,
    value            INTEGER     NOT NULL DEFAULT 1,
    fingerprint_hash VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_events_post_id_type ON post_events (post_id, event_type);
CREATE INDEX idx_post_events_fingerprint ON post_events (fingerprint_hash) WHERE fingerprint_hash IS NOT NULL;
