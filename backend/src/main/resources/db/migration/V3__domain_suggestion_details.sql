-- GoDaddy returns an inventory type (REGISTRY or PREMIUM) and a one-time premium fee.
-- Prices are stored exactly as returned, in minor currency units (for USD, cents).
ALTER TABLE domain_suggestion
    ADD COLUMN inventory   VARCHAR(20),
    ADD COLUMN premium_fee BIGINT;
