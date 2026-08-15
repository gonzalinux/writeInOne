-- A subdomain released by a rename or a site deletion stays parked for its previous
-- owner for a configurable window, so nobody else can grab the handle in the meantime.
CREATE TABLE subdomain_reservations
(
    label       TEXT        PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    released_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subdomain_reservations_released_at ON subdomain_reservations (released_at);
