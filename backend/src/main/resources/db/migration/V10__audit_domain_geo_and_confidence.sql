-- The domain review (measured facts plus the written assessment) and GEO readiness signals are saved with
-- each audit so they have history like the rest of the audit. A recommendation also records how strong
-- its evidence is, shown next to impact and effort.
ALTER TABLE website_audit
    ADD COLUMN domain_review  JSONB,
    ADD COLUMN geo_readiness  JSONB;
ALTER TABLE recommendation
    ADD COLUMN confidence VARCHAR(10) CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE recommendation DROP CONSTRAINT recommendation_module_check;
ALTER TABLE recommendation ADD CONSTRAINT recommendation_module_check
    CHECK (module IN ('WEBSITE', 'CHANNEL', 'DOMAIN', 'BRAND', 'LAUNCH', 'GEO'));
