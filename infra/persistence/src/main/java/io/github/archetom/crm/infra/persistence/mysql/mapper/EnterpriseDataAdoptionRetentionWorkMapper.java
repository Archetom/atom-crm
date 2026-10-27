package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.EnterpriseDataAdoptionRetentionWorkPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface EnterpriseDataAdoptionRetentionWorkMapper
        extends BaseMapper<EnterpriseDataAdoptionRetentionWorkPO> {
    @Select("""
            SELECT * FROM crm_enterprise_data_adoption_retention_work
            WHERE (work_state = 'READY' AND delete_after <= #{now})
               OR (work_state = 'CLAIMED' AND lease_expires_at <= #{now})
            ORDER BY delete_after ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    EnterpriseDataAdoptionRetentionWorkPO selectDueForUpdate(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_enterprise_data_adoption_retention_work
            SET work_state = 'CLAIMED', lease_owner = #{owner}, lease_expires_at = #{leaseExpiresAt},
                lease_fence = lease_fence + 1
            WHERE id = #{id}
            """)
    int claim(@Param("id") long id, @Param("owner") String owner,
              @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("""
            UPDATE crm_enterprise_data_adoption_retention_work
            SET work_state = 'READY', lease_owner = NULL, lease_expires_at = NULL
            WHERE id = #{id} AND work_state = 'CLAIMED'
              AND lease_owner = #{owner} AND lease_fence = #{fence}
            """)
    int release(@Param("id") long id, @Param("owner") String owner, @Param("fence") long fence);
}
