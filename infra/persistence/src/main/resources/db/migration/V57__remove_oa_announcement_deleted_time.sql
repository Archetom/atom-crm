ALTER TABLE oa_announcement
    DROP CHECK chk_oa_announcement_lifecycle,
    DROP COLUMN deleted_time,
    ADD CONSTRAINT chk_oa_announcement_lifecycle CHECK (
        (status = 'DRAFT'
            AND published_time IS NULL AND withdrawn_time IS NULL)
        OR (status = 'PUBLISHED'
            AND published_time IS NOT NULL AND withdrawn_time IS NULL)
        OR (status = 'WITHDRAWN'
            AND published_time IS NOT NULL AND withdrawn_time IS NOT NULL)
        OR (status = 'DELETED'
            AND ((published_time IS NULL AND withdrawn_time IS NULL)
                OR (published_time IS NOT NULL AND withdrawn_time IS NOT NULL)))
    );
