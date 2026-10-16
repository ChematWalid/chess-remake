CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100),
    display_name VARCHAR(80) NOT NULL,
    bio VARCHAR(500),
    avatar_key TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT TRUE,
    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER',
    onboarding_complete BOOLEAN NOT NULL DEFAULT TRUE,
    total_games INTEGER NOT NULL DEFAULT 0 CHECK (total_games >= 0),
    wins INTEGER NOT NULL DEFAULT 0 CHECK (wins >= 0),
    losses INTEGER NOT NULL DEFAULT 0 CHECK (losses >= 0),
    draws INTEGER NOT NULL DEFAULT 0 CHECK (draws >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_username_format CHECK (username = lower(username) AND length(username) BETWEEN 3 AND 30),
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
CREATE INDEX idx_users_username_prefix ON users (username text_pattern_ops) WHERE searchable = TRUE;
CREATE INDEX idx_users_display_name_trgm ON users USING gin (lower(display_name) gin_trgm_ops) WHERE searchable = TRUE;

CREATE TABLE oauth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_oauth_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    session_label VARCHAR(100),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at);

CREATE TABLE email_action_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    action VARCHAR(20) NOT NULL CHECK (action IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'OAUTH_ONBOARDING')),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_email_action_user_action ON email_action_tokens(user_id, action);

CREATE TABLE friendships (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'BLOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_friendship_distinct CHECK (requester_id <> addressee_id)
);
CREATE UNIQUE INDEX uk_friendships_unordered_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX idx_friendships_addressee_status ON friendships(addressee_id, status);
CREATE INDEX idx_friendships_requester_status ON friendships(requester_id, status);

CREATE TABLE friend_invites (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    consumed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_friend_invite_active_owner ON friend_invites(owner_id) WHERE consumed_at IS NULL;

CREATE TABLE game_challenges (
    id BIGSERIAL PRIMARY KEY,
    challenger_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenged_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    initial_time_seconds INTEGER,
    increment_seconds INTEGER,
    challenger_side VARCHAR(10) NOT NULL CHECK (challenger_side IN ('WHITE', 'BLACK', 'RANDOM')),
    challenger_color VARCHAR(5) CHECK (challenger_color IN ('WHITE', 'BLACK')),
    room_code VARCHAR(10) UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED', 'EXPIRED', 'COMPLETED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_challenge_distinct_players CHECK (challenger_id <> challenged_id),
    CONSTRAINT ck_challenge_time_control CHECK (
      (initial_time_seconds IS NULL AND increment_seconds IS NULL) OR
      (initial_time_seconds BETWEEN 5 AND 2592000 AND increment_seconds BETWEEN 0 AND 3600)
    )
);
CREATE INDEX idx_challenges_challenged_status ON game_challenges(challenged_id, status, expires_at);
CREATE INDEX idx_challenges_challenger_status ON game_challenges(challenger_id, status, expires_at);

CREATE TABLE game_histories (
    id BIGSERIAL PRIMARY KEY,
    challenge_id BIGINT NOT NULL UNIQUE REFERENCES game_challenges(id),
    room_code VARCHAR(10) NOT NULL UNIQUE,
    white_player_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    black_player_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    white_username_snapshot VARCHAR(30) NOT NULL,
    black_username_snapshot VARCHAR(30) NOT NULL,
    winner_player_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    is_draw BOOLEAN NOT NULL,
    termination_reason VARCHAR(40) NOT NULL,
    initial_time_seconds INTEGER,
    increment_seconds INTEGER,
    total_moves INTEGER NOT NULL CHECK (total_moves >= 0),
    pgn TEXT NOT NULL,
    moves_history JSONB NOT NULL,
    fens_history JSONB NOT NULL,
    played_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_seconds INTEGER NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0)
);
CREATE INDEX idx_game_histories_white_played ON game_histories(white_player_id, played_at DESC);
CREATE INDEX idx_game_histories_black_played ON game_histories(black_player_id, played_at DESC);
CREATE INDEX idx_game_histories_played ON game_histories(played_at DESC);

CREATE TABLE oauth_exchange_codes (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
