-- V109 removed the marker whenever no active login user held a tenant-owner role, so that
-- development fixtures carrying memberships without a login user could still be installed.
-- A deployment that authenticates through an external identity system has no login users by
-- design, so that condition also reopened the public setup window on initialized systems.
--
-- Installation completeness is now derived from tenant existence, which only ever moves
-- forward. Restore the marker so the table agrees with that answer for systems V109 cleared.
INSERT INTO system_installation (singleton_key, initialized_tenant_id)
SELECT 1, MIN(tenant_id)
FROM organization_tenant
HAVING COUNT(*) > 0;
