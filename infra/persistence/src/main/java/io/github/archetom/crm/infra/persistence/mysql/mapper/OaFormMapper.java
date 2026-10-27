package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaFormPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OaFormMapper extends BaseMapper<OaFormPO> {

    @Select("""
            SELECT COUNT(*)
              FROM oa_form_definition form_root
              JOIN oa_form_field form_field
                ON form_field.tenant_id = form_root.tenant_id
               AND form_field.oa_form_id = form_root.id
             WHERE form_root.tenant_id = #{tenantId}
               AND form_root.status = 'ACTIVE'
               AND form_field.object_type = 'OA_REQUEST'
               AND form_field.field_code = #{fieldCode}
            """)
    long countActiveReferencingField(
            @Param("tenantId") long tenantId,
            @Param("fieldCode") String fieldCode);

    @Select("""
            SELECT COUNT(*)
              FROM oa_form_definition
             WHERE tenant_id = #{tenantId}
               AND status = 'ACTIVE'
               AND approval_definition_id = #{definitionId}
            """)
    long countActiveReferencingApprovalDefinition(
            @Param("tenantId") long tenantId,
            @Param("definitionId") long definitionId);
}
