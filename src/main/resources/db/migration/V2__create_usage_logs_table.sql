CREATE TABLE usage_logs (
    id             UUID PRIMARY KEY,
    key_id         UUID NOT NULL REFERENCES api_keys(id),
    provider       VARCHAR(50) NOT NULL,
    tokens         INTEGER NOT NULL,
    estimated_cost DOUBLE PRECISION NOT NULL,
    latency_ms     BIGINT NOT NULL,
    cached         BOOLEAN NOT NULL,
    timestamp      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Every query in this phase (GET /v1/usage) filters by key_id.
CREATE INDEX idx_usage_logs_key_id ON usage_logs (key_id);
