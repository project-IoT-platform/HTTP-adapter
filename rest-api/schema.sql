CREATE TABLE IF NOT EXISTS devices (
    device_id    VARCHAR(64) PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS telemetry (
    id          BIGSERIAL PRIMARY KEY,
    device_id   VARCHAR(64) NOT NULL REFERENCES devices(device_id),
    message_id  BIGINT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload     JSONB NOT NULL,
    UNIQUE (device_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_device_time ON telemetry (device_id, received_at DESC);
