PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS match_player_substitutions (
  id TEXT PRIMARY KEY,
  match_id TEXT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  team_id TEXT NOT NULL REFERENCES teams(id),
  incoming_player_id TEXT NOT NULL REFERENCES players(id),
  outgoing_player_id TEXT REFERENCES players(id),
  actor_user_id TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_match_player_substitutions_match
  ON match_player_substitutions(match_id, created_at DESC);
