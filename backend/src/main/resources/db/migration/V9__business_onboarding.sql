-- One business-centric product: both onboarding paths can add a Google Business Profile link, and a
-- business planning to launch can say when it expects to open.
ALTER TABLE business_details
    ADD COLUMN google_business_url TEXT,
    ADD COLUMN launch_timeline     VARCHAR(160);
