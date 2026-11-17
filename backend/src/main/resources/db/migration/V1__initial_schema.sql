-- LaunchMate AI initial schema (LLD v2.0, section 5)

CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(160) NOT NULL,
    is_admin      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE growth_profile (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES app_user(id),
    profile_type    VARCHAR(30) NOT NULL
        CHECK (profile_type IN ('NEW_BUSINESS', 'EXISTING_BUSINESS', 'CREATOR')),
    display_name    VARCHAR(160) NOT NULL,
    website_url     TEXT,
    target_audience TEXT,
    market_location VARCHAR(160),
    primary_goal    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_growth_profile_owner ON growth_profile(owner_id);

CREATE TABLE business_details (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id UUID NOT NULL UNIQUE REFERENCES growth_profile(id),
    category          VARCHAR(160),
    description       TEXT,
    products_services JSONB,
    brand_preferences JSONB,
    draft_copy        TEXT,
    current_domain    VARCHAR(253)
);

CREATE TABLE creator_details (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id UUID NOT NULL UNIQUE REFERENCES growth_profile(id),
    niche             VARCHAR(160),
    creator_bio       TEXT,
    content_language  VARCHAR(80),
    content_goals     JSONB
);

CREATE TABLE creator_channel (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id   UUID NOT NULL REFERENCES growth_profile(id),
    platform            VARCHAR(30) NOT NULL CHECK (platform IN ('YOUTUBE', 'INSTAGRAM')),
    channel_url         TEXT NOT NULL,
    platform_channel_id VARCHAR(180),
    connection_status   VARCHAR(30) NOT NULL DEFAULT 'URL_ONLY'
        CHECK (connection_status IN ('URL_ONLY', 'NOT_FOUND', 'NOT_SUPPORTED')),
    last_synced_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_creator_channel_profile ON creator_channel(growth_profile_id);

CREATE TABLE website_audit (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id UUID NOT NULL REFERENCES growth_profile(id),
    website_url       TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    pages_crawled     JSONB,
    pagespeed_data    JSONB,
    warnings          JSONB,
    summary           TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_website_audit_profile ON website_audit(growth_profile_id, created_at DESC);

CREATE TABLE website_finding (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id       UUID NOT NULL REFERENCES website_audit(id),
    category       VARCHAR(40) NOT NULL,
    severity       VARCHAR(10) NOT NULL CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
    page_url       TEXT,
    title          TEXT NOT NULL,
    evidence       TEXT NOT NULL,
    impact         TEXT,
    recommendation TEXT,
    suggested_copy TEXT,
    source_label   VARCHAR(40) NOT NULL DEFAULT 'OBSERVED_PUBLIC_DATA'
        CHECK (source_label IN ('OBSERVED_PUBLIC_DATA', 'USER_PROVIDED', 'AUTHORISED_PLATFORM_DATA', 'AI_INFERENCE'))
);
CREATE INDEX idx_website_finding_audit ON website_finding(audit_id);

CREATE TABLE channel_analysis (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id UUID NOT NULL REFERENCES growth_profile(id),
    channel_id        UUID NOT NULL REFERENCES creator_channel(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    videos_analysed   INTEGER NOT NULL DEFAULT 0,
    data_limitations  JSONB,
    analysis          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_channel_analysis_profile ON channel_analysis(growth_profile_id, created_at DESC);

CREATE TABLE channel_video (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id      UUID NOT NULL REFERENCES channel_analysis(id),
    youtube_video_id VARCHAR(40) NOT NULL,
    title            TEXT NOT NULL,
    description      TEXT,
    published_at     TIMESTAMPTZ,
    duration_seconds INTEGER,
    views            BIGINT,   -- NULL means not available, never zero
    likes            BIGINT,
    comments         BIGINT,
    format           VARCHAR(20) CHECK (format IN ('SHORT', 'LONG_FORM'))
);
CREATE INDEX idx_channel_video_analysis ON channel_video(analysis_id);

CREATE TABLE domain_suggestion (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id UUID NOT NULL REFERENCES growth_profile(id),
    domain_name       VARCHAR(253) NOT NULL,
    origin            VARCHAR(30) NOT NULL
        CHECK (origin IN ('USER_CANDIDATE', 'AI_SUGGESTED', 'PROVIDER_SUGGESTED', 'CURRENT')),
    availability      VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED'
        CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE', 'UNKNOWN', 'NOT_CHECKED')),
    price             BIGINT,         -- raw provider value, unit as returned (minor units)
    currency          VARCHAR(10),
    period            INTEGER,
    renewal_price     BIGINT,
    analysis          JSONB,
    provider          VARCHAR(30),
    checked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_domain_suggestion_profile ON domain_suggestion(growth_profile_id, checked_at DESC);

CREATE TABLE growth_plan (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id UUID NOT NULL REFERENCES growth_profile(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    summary           TEXT,
    inputs            JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_growth_plan_profile ON growth_plan(growth_profile_id, created_at DESC);

CREATE TABLE recommendation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID NOT NULL REFERENCES growth_plan(id),
    module           VARCHAR(20) NOT NULL
        CHECK (module IN ('WEBSITE', 'CHANNEL', 'DOMAIN', 'BRAND', 'LAUNCH')),
    priority         VARCHAR(10) NOT NULL CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    effort           VARCHAR(10) NOT NULL CHECK (effort IN ('LOW', 'MEDIUM', 'HIGH')),
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'DISMISSED')),
    title            TEXT NOT NULL,
    evidence         TEXT NOT NULL,
    rationale        TEXT NOT NULL,
    suggested_action TEXT NOT NULL,
    completed_at     TIMESTAMPTZ
);
CREATE INDEX idx_recommendation_plan ON recommendation(plan_id, status, priority);

CREATE TABLE generated_content (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id UUID NOT NULL REFERENCES growth_profile(id),
    recommendation_id UUID REFERENCES recommendation(id),
    content_type      VARCHAR(30) NOT NULL
        CHECK (content_type IN ('HOMEPAGE_COPY', 'META_TAGS', 'YT_TITLE', 'YT_DESCRIPTION',
                                'YT_HOOK', 'CALENDAR', 'TAGLINE', 'OTHER')),
    title             TEXT,
    body              TEXT NOT NULL,
    original_text     TEXT,
    reason            TEXT,
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_generated_content_profile ON generated_content(growth_profile_id, created_at DESC);

CREATE TABLE ai_usage (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_profile_id           UUID REFERENCES growth_profile(id),
    feature                     VARCHAR(40) NOT NULL,
    model                       VARCHAR(60) NOT NULL,
    prompt_version              VARCHAR(20),
    input_tokens                INTEGER,
    output_tokens               INTEGER,
    cache_creation_input_tokens INTEGER,
    cache_read_input_tokens     INTEGER,
    stop_reason                 VARCHAR(30),
    latency_ms                  INTEGER,
    request_id                  VARCHAR(80),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_usage_feature ON ai_usage(feature, created_at DESC);
