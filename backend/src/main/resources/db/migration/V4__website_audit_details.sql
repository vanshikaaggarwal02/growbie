-- Deterministic readiness checks and the audit's stated limitations are stored with the audit.
ALTER TABLE website_audit
    ADD COLUMN readiness_checks JSONB,
    ADD COLUMN limitations      JSONB;

-- LLD v2.1 source labels: findings the model infers are AI_DERIVED (previously AI_INFERENCE), and a
-- value that could not be obtained is UNAVAILABLE.
ALTER TABLE website_finding DROP CONSTRAINT website_finding_source_label_check;
ALTER TABLE website_finding ADD CONSTRAINT website_finding_source_label_check
    CHECK (source_label IN ('OBSERVED_PUBLIC_DATA', 'USER_PROVIDED', 'AUTHORISED_PLATFORM_DATA',
                            'AI_DERIVED', 'UNAVAILABLE'));
