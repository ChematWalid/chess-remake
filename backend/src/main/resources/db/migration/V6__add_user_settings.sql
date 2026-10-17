CREATE TYPE board_palette AS ENUM ('FOREST', 'WALNUT', 'MARBLE');
CREATE TYPE board_orientation AS ENUM ('AUTO', 'WHITE', 'BLACK');
CREATE TYPE motion_preference AS ENUM ('SYSTEM', 'REDUCED', 'FULL');

CREATE TABLE user_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    board_palette board_palette NOT NULL DEFAULT 'FOREST',
    board_orientation board_orientation NOT NULL DEFAULT 'AUTO',
    motion_preference motion_preference NOT NULL DEFAULT 'SYSTEM',
    show_coordinates BOOLEAN NOT NULL DEFAULT TRUE,
    show_legal_move_hints BOOLEAN NOT NULL DEFAULT TRUE,
    premoves_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    move_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    game_sounds_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    in_app_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notification_sounds_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO user_settings(user_id) SELECT id FROM users;
