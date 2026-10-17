ALTER TABLE game_challenges
    ADD COLUMN initial_fen TEXT,
    ADD COLUMN setup_moves JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN setup_fens JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE game_histories
    ADD COLUMN game_type VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN setup_moves JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN setup_fens JSONB NOT NULL DEFAULT '[]'::jsonb;
