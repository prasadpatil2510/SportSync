CREATE TABLE app_users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL COLLATE NOCASE UNIQUE,
  display_name TEXT NOT NULL,
  pin_salt TEXT NOT NULL,
  pin_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'PLAYER' CHECK (role IN ('PLAYER','SCORER','TOURNAMENT_ADMIN')),
  player_id TEXT,
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (player_id) REFERENCES players(id)
);

CREATE TABLE auth_sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_sessions_token_expiry ON auth_sessions(token_hash, expires_at);

CREATE TABLE auth_audit_log (
  id TEXT PRIMARY KEY,
  actor_user_id TEXT,
  target_user_id TEXT,
  action TEXT NOT NULL,
  details TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
