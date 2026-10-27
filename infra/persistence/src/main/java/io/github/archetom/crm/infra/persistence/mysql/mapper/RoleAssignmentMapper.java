package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.RoleAssignmentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoleAssignmentMapper extends BaseMapper<RoleAssignmentPO> {

    @Select("""
            <script>
            SELECT DISTINCT role_assignment.actor_id
            FROM authorization_role_assignment role_assignment
            JOIN organization_membership membership
              ON membership.tenant_id = role_assignment.tenant_id
             AND membership.actor_id = role_assignment.actor_id
             AND membership.status = 'ACTIVE'
            WHERE role_assignment.tenant_id = #{tenantId}
              AND role_assignment.role_id IN
              <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
              </foreach>
            ORDER BY role_assignment.actor_id
            LIMIT #{limit}
            </script>
            """)
    List<Long> findActiveMemberIdsByRoles(
            @Param("tenantId") Long tenantId,
            @Param("roleIds") Collection<Long> roleIds,
            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM authorization_role_assignment role_assignment
            JOIN organization_membership membership
              ON membership.tenant_id = role_assignment.tenant_id
             AND membership.actor_id = role_assignment.actor_id
             AND membership.status = 'ACTIVE'
            WHERE role_assignment.tenant_id = #{tenantId}
              AND role_assignment.role_id = #{roleId}
            """)
    long countActiveMembersByRole(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}
