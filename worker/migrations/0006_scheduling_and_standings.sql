ALTER TABLE tournaments ADD COLUMN auction_reference TEXT;
ALTER TABLE matches ADD COLUMN stage_type TEXT NOT NULL DEFAULT 'LEAGUE';
CREATE INDEX IF NOT EXISTS idx_matches_tournament_stage_status ON matches(tournament_id, stage_type, status);
