package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.EnterpriseDataAdoptionPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EnterpriseDataAdoptionMapper extends BaseMapper<EnterpriseDataAdoptionPO> {
    @Select("""
            SELECT id FROM crm_enterprise_data_adoption
            WHERE tenant_id = #{tenantId} AND delete_after <= #{now}
            ORDER BY delete_after ASC, id ASC
            LIMIT #{limit}
            FOR UPDATE
            """)
    List<Long> selectExpiredIdsForUpdate(@Param("tenantId") long tenantId,
                                         @Param("now") LocalDateTime now,
                                         @Param("limit") int limit);

    @Delete("""
            <script>
            DELETE FROM crm_enterprise_data_adoption
            WHERE tenant_id = #{tenantId} AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int deleteByTenantAndIds(@Param("tenantId") long tenantId, @Param("ids") List<Long> ids);
}
