ALTER TABLE tournaments ADD COLUMN city TEXT;
ALTER TABLE tournaments ADD COLUMN ground TEXT;
ALTER TABLE tournaments ADD COLUMN organiser_name TEXT;
ALTER TABLE tournaments ADD COLUMN organiser_phone TEXT;
ALTER TABLE tournaments ADD COLUMN organiser_email TEXT;
ALTER TABLE tournaments ADD COLUMN start_date TEXT;
ALTER TABLE tournaments ADD COLUMN end_date TEXT;
ALTER TABLE tournaments ADD COLUMN category TEXT NOT NULL DEFAULT 'OPEN';
ALTER TABLE tournaments ADD COLUMN ball_type TEXT NOT NULL DEFAULT 'TENNIS';
ALTER TABLE tournaments ADD COLUMN pitch_type TEXT;
ALTER TABLE tournaments ADD COLUMN match_type TEXT NOT NULL DEFAULT 'LIMITED_OVERS';
ALTER TABLE tournaments ADD COLUMN banner_url TEXT;
ALTER TABLE tournaments ADD COLUMN logo_url TEXT;

ALTER TABLE teams ADD COLUMN city TEXT;
ALTER TABLE teams ADD COLUMN captain_name TEXT;
ALTER TABLE teams ADD COLUMN captain_phone TEXT;

ALTER TABLE team_players ADD COLUMN member_role TEXT NOT NULL DEFAULT 'PLAYER';
ALTER TABLE team_players ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0;
ALTER TABLE team_players ADD COLUMN is_captain INTEGER NOT NULL DEFAULT 0;
ALTER TABLE team_players ADD COLUMN is_wicket_keeper INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_tournament_teams_tournament ON tournament_teams(tournament_id);
CREATE INDEX IF NOT EXISTS idx_team_players_team ON team_players(team_id);
