-- What the crawler read, skipped and why, stored with each audit so the report can explain its own limits.
ALTER TABLE website_audit
    ADD COLUMN crawl_report JSONB;
