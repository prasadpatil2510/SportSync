ALTER TABLE deliveries ADD COLUMN fielder_id TEXT REFERENCES players(id);
ALTER TABLE deliveries ADD COLUMN assistant_fielder_id TEXT REFERENCES players(id);
