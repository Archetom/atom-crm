ALTER TABLE organization_membership_request
    ADD COLUMN pending_applicant_actor_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN applicant_actor_id ELSE NULL END
        ) STORED,
    ADD CONSTRAINT uk_organization_membership_request_pending_applicant
        UNIQUE (target_tenant_id, pending_applicant_actor_id);
