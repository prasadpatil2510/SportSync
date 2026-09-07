PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS matches (
  id TEXT PRIMARY KEY,
  tournament_id TEXT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
  round_name TEXT NOT NULL DEFAULT 'League Match',
  team_a_id TEXT NOT NULL REFERENCES teams(id),
  team_b_id TEXT NOT NULL REFERENCES teams(id),
  scheduled_at TEXT,
  ground TEXT,
  overs_per_innings INTEGER NOT NULL DEFAULT 20,
  status TEXT NOT NULL DEFAULT 'SCHEDULED',
  toss_winner_id TEXT REFERENCES teams(id),
  toss_decision TEXT,
  batting_team_id TEXT REFERENCES teams(id),
  bowling_team_id TEXT REFERENCES teams(id),
  current_innings INTEGER NOT NULL DEFAULT 0,
  result_text TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (team_a_id <> team_b_id)
);

CREATE TABLE IF NOT EXISTS match_players (
  match_id TEXT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  team_id TEXT NOT NULL REFERENCES teams(id),
  player_id TEXT NOT NULL REFERENCES players(id),
  is_playing INTEGER NOT NULL DEFAULT 1,
  is_substitute INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (match_id, team_id, player_id)
);

CREATE TABLE IF NOT EXISTS innings (
  id TEXT PRIMARY KEY,
  match_id TEXT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  innings_number INTEGER NOT NULL,
  batting_team_id TEXT NOT NULL REFERENCES teams(id),
  bowling_team_id TEXT NOT NULL REFERENCES teams(id),
  striker_id TEXT REFERENCES players(id),
  non_striker_id TEXT REFERENCES players(id),
  bowler_id TEXT REFERENCES players(id),
  runs INTEGER NOT NULL DEFAULT 0,
  wickets INTEGER NOT NULL DEFAULT 0,
  legal_balls INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'LIVE',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(match_id, innings_number)
);

CREATE TABLE IF NOT EXISTS deliveries (
  id TEXT PRIMARY KEY,
  innings_id TEXT NOT NULL REFERENCES innings(id) ON DELETE CASCADE,
  sequence_number INTEGER NOT NULL,
  striker_id TEXT REFERENCES players(id),
  non_striker_id TEXT REFERENCES players(id),
  bowler_id TEXT REFERENCES players(id),
  batter_runs INTEGER NOT NULL DEFAULT 0,
  extra_runs INTEGER NOT NULL DEFAULT 0,
  extra_type TEXT NOT NULL DEFAULT 'NONE',
  is_wicket INTEGER NOT NULL DEFAULT 0,
  dismissal_type TEXT,
  dismissed_player_id TEXT REFERENCES players(id),
  is_legal INTEGER NOT NULL DEFAULT 1,
  note TEXT,
  is_void INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(innings_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_matches_tournament ON matches(tournament_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_match_players_match ON match_players(match_id, team_id);
CREATE INDEX IF NOT EXISTS idx_innings_match ON innings(match_id, innings_number);
CREATE INDEX IF NOT EXISTS idx_deliveries_innings ON deliveries(innings_id, sequence_number);
