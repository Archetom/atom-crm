-- B28a follow-up: an occurrence's safe reason is meaningful only for its current lifecycle state.
UPDATE oa_announcement_publication_occurrence
SET safe_reason = NULL
WHERE status IN ('READY', 'LEASED', 'PUBLISHED')
  AND safe_reason IS NOT NULL;

UPDATE oa_announcement_publication_occurrence
SET safe_reason = 'RETRY'
WHERE status = 'RETRY_WAIT'
  AND safe_reason IS NULL;

ALTER TABLE oa_announcement_publication_occurrence
    DROP CHECK chk_oa_announcement_publication_occurrence_reason,
    ADD CONSTRAINT chk_oa_announcement_publication_occurrence_reason CHECK (
        (status IN ('READY', 'LEASED', 'PUBLISHED') AND safe_reason IS NULL)
        OR (status = 'RETRY_WAIT' AND safe_reason = 'RETRY')
        OR (status = 'CANCELLED' AND safe_reason IN (
            'SCHEDULING_MEMBER_UNAUTHORIZED', 'AUDIENCE_INVALID', 'AUDIENCE_EMPTY',
            'AUDIENCE_LIMIT', 'CANCELLED', 'SUPERSEDED'
        ))
        OR (status = 'DEAD' AND safe_reason IN ('MISFIRE_EXPIRED', 'RETRY_EXHAUSTED'))
    );
