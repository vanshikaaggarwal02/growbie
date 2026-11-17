-- Messages from the public contact form. Always saved, so nothing is lost if email delivery fails.
CREATE TABLE contact_message (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(160) NOT NULL,
    email      VARCHAR(254) NOT NULL,
    business   VARCHAR(160),
    message    TEXT NOT NULL,
    emailed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
