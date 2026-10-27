-- V108 treated every organization bootstrap row as a completed human installation.
-- Development/test organization fixtures can have memberships and roles without any login user.
-- Keep the marker only when an active user can actually enter an active tenant-owner membership.
DELETE FROM system_installation
WHERE singleton_key = 1
  AND NOT EXISTS (
    SELECT 1
    FROM t_user user
    JOIN organization_membership membership
      ON membership.tenant_id = user.tenant_id
     AND membership.actor_id = user.id
     AND membership.status = 'ACTIVE'
    JOIN authorization_role_assignment assignment
      ON assignment.tenant_id = membership.tenant_id
     AND assignment.actor_id = membership.actor_id
    JOIN authorization_role role
      ON role.tenant_id = assignment.tenant_id
     AND role.id = assignment.role_id
     AND role.status = 'ACTIVE'
     AND role.system_key = 'TENANT_OWNER'
    WHERE user.status = 'ACTIVE'
  );
