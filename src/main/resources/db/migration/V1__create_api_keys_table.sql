CREATE TABLE api_keys (
    id         UUID PRIMARY KEY,
    key_hash   VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hex digest of the raw key; UNIQUE also gives us a lookup index
    name       VARCHAR(255) NOT NULL,
    tier       VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE
);
