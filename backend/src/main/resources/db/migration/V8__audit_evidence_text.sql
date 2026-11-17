-- The measured evidence each audit's findings were checked against. Kept so a later audit can tell whether an
-- earlier finding's quoted evidence is really gone from the site, rather than merely not repeated by the model.
ALTER TABLE website_audit
    ADD COLUMN evidence_text TEXT;
