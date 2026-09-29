CREATE TABLE IF NOT EXISTS match_broadcast_grants (
  match_id TEXT PRIMARY KEY REFERENCES matches(id) ON DELETE CASCADE,
  pin_salt TEXT NOT NULL,
  pin_hash TEXT NOT NULL,
  phone_token_hash TEXT NOT NULL,
  overlay_token_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked_at TEXT,
  created_by TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS match_broadcast_attempts (
  client_key TEXT PRIMARY KEY,
  failed_count INTEGER NOT NULL DEFAULT 0,
  window_started_at TEXT NOT NULL,
  blocked_until TEXT
);

CREATE INDEX IF NOT EXISTS idx_match_broadcast_grants_expires ON match_broadcast_grants(expires_at);
