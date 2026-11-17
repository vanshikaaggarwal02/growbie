-- YouTube channel analysis: the measured channel statistics are saved with every analysis, so
-- later analyses can be compared with earlier ones (snapshots). Format is a duration-based estimate.
ALTER TABLE channel_analysis
    ADD COLUMN channel_stats JSONB,
    ADD COLUMN warnings      JSONB,
    ADD COLUMN started_at    TIMESTAMPTZ,
    ADD COLUMN completed_at  TIMESTAMPTZ;

ALTER TABLE channel_video
    ADD COLUMN tags        JSONB,
    ADD COLUMN live_status VARCHAR(12);

ALTER TABLE channel_video DROP CONSTRAINT channel_video_format_check;
ALTER TABLE channel_video ADD CONSTRAINT channel_video_format_check
    CHECK (format IN ('SHORT', 'MID_LENGTH', 'LONG_FORM'));
