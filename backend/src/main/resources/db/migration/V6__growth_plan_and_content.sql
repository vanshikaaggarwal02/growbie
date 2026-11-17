-- Growth plans: a recommendation records its qualitative impact, the evidence ids it rests on, how
-- progress can be measured, and an optional owner note. Priority is computed from impact and effort.
ALTER TABLE growth_plan
    ADD COLUMN limitations JSONB,
    ADD COLUMN warnings    JSONB,
    ADD COLUMN started_at  TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE recommendation
    ADD COLUMN impact         VARCHAR(10) CHECK (impact IN ('HIGH', 'MEDIUM', 'LOW')),
    ADD COLUMN source_ids     JSONB,
    ADD COLUMN how_to_measure TEXT,
    ADD COLUMN source_label   VARCHAR(30)
        CHECK (source_label IN ('OBSERVED_PUBLIC_DATA', 'USER_PROVIDED', 'AI_DERIVED')),
    ADD COLUMN position       INTEGER,
    ADD COLUMN note           TEXT,
    ADD COLUMN updated_at     TIMESTAMPTZ NOT NULL DEFAULT now();

-- Generated content: owners review variants and approve one.
ALTER TABLE generated_content
    ADD COLUMN status           VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED')),
    ADD COLUMN selected_variant INTEGER,
    ADD COLUMN updated_at       TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE generated_content DROP CONSTRAINT generated_content_content_type_check;
ALTER TABLE generated_content ADD CONSTRAINT generated_content_content_type_check
    CHECK (content_type IN ('HOMEPAGE_COPY', 'META_TAGS', 'PRODUCT_DESCRIPTION', 'YT_TITLE', 'YT_DESCRIPTION',
                            'YT_HOOK', 'YT_SCRIPT', 'CALENDAR', 'TAGLINE', 'OTHER'));
