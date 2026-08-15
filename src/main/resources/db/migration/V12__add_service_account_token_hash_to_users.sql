ALTER TABLE users ADD COLUMN service_account_token_hash TEXT UNIQUE;
ALTER TABLE users ADD COLUMN owner_id BIGINT REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

CREATE INDEX idx_users_owner_id ON users (owner_id);