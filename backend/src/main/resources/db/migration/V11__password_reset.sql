-- Forgot password: a one-time token that expires, stored only as a hash. password_changed_at lets the server
-- reject sessions issued before a password change, so a reset also signs out anyone else holding an old session.
CREATE TABLE password_reset_token (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_user ON password_reset_token(user_id, created_at DESC);
ALTER TABLE app_user ADD COLUMN password_changed_at TIMESTAMPTZ;
