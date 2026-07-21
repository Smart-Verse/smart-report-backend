CREATE TABLE IF NOT EXISTS api_key (
    id uuid PRIMARY KEY,
    tenant varchar(120) NOT NULL,
    name varchar(120) NOT NULL,
    key_prefix varchar(32) NOT NULL UNIQUE,
    secret_hash varchar(64) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL,
    last_used_at timestamp,
    expires_at timestamp
);

CREATE INDEX IF NOT EXISTS idx_api_key_prefix_active ON api_key (key_prefix, active);
CREATE INDEX IF NOT EXISTS idx_api_key_tenant ON api_key (tenant);
