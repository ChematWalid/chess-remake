CREATE TYPE theme_preference AS ENUM ('DARK', 'LIGHT', 'SYSTEM');

ALTER TABLE user_settings
    ADD COLUMN theme_preference theme_preference NOT NULL DEFAULT 'DARK';
