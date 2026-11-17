-- LLD v2.1: CREATOR is renamed to YOUTUBE_CREATOR.
ALTER TABLE growth_profile DROP CONSTRAINT growth_profile_profile_type_check;
ALTER TABLE growth_profile ADD CONSTRAINT growth_profile_profile_type_check
    CHECK (profile_type IN ('NEW_BUSINESS', 'EXISTING_BUSINESS', 'YOUTUBE_CREATOR'));
