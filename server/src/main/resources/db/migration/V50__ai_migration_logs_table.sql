CREATE TABLE ai_migration_logs (
    id               BIGSERIAL PRIMARY KEY,
    source_provider  VARCHAR(40),
    action           VARCHAR(60) NOT NULL,
    details          TEXT,
    status           VARCHAR(30) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
