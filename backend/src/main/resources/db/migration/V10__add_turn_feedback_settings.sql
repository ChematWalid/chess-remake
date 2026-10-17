ALTER TABLE user_settings
    ADD COLUMN turn_indicator_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN turn_sound_enabled BOOLEAN NOT NULL DEFAULT FALSE;
