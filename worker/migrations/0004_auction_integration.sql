ALTER TABLE teams ADD COLUMN external_source TEXT;
ALTER TABLE teams ADD COLUMN external_id TEXT;
ALTER TABLE players ADD COLUMN external_source TEXT;
ALTER TABLE players ADD COLUMN external_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_teams_external_identity
  ON teams(external_source, external_id)
  WHERE external_source IS NOT NULL AND external_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_players_external_identity
  ON players(external_source, external_id)
  WHERE external_source IS NOT NULL AND external_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS import_runs (
  id TEXT PRIMARY KEY,
  source TEXT NOT NULL,
  tournament_id TEXT REFERENCES tournaments(id) ON DELETE SET NULL,
  status TEXT NOT NULL,
  teams_created INTEGER NOT NULL DEFAULT 0,
  teams_updated INTEGER NOT NULL DEFAULT 0,
  players_created INTEGER NOT NULL DEFAULT 0,
  players_updated INTEGER NOT NULL DEFAULT 0,
  memberships_added INTEGER NOT NULL DEFAULT 0,
  error_text TEXT,
  started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_import_runs_source_started
  ON import_runs(source, started_at DESC);
