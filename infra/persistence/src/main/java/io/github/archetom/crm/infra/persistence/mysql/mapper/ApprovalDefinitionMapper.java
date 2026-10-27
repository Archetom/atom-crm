package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalDefinitionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalDefinitionMapper extends BaseMapper<ApprovalDefinitionPO> {
    @Select("""
            SELECT COUNT(*)
              FROM approval_definition
             WHERE tenant_id = #{tenantId}
               AND object_type = #{objectType}
               AND status <> 'DELETED'
               AND JSON_SEARCH(config_json, 'one', #{fieldCode}, '!',
                       '$.steps[*].conditions[*].fieldCode') IS NOT NULL
            """)
    long countReferencingField(@Param("tenantId") Long tenantId,
                               @Param("objectType") String objectType,
                               @Param("fieldCode") String escapedFieldCode);
}
