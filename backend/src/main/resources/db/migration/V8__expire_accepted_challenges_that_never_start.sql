ALTER TABLE game_challenges
    ADD COLUMN accepted_at TIMESTAMPTZ,
    ADD COLUMN started_at TIMESTAMPTZ;

UPDATE game_challenges
SET accepted_at = created_at,
    started_at = created_at
WHERE status = 'ACCEPTED';

CREATE INDEX idx_challenges_accepted_not_started
    ON game_challenges (accepted_at)
    WHERE status = 'ACCEPTED' AND started_at IS NULL;
