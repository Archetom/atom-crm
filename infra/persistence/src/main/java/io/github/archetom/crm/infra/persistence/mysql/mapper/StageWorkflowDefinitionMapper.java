package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowDefinitionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StageWorkflowDefinitionMapper extends BaseMapper<StageWorkflowDefinitionPO> {
    @Select("""
            SELECT COUNT(*)
              FROM stage_workflow_definition
             WHERE tenant_id = #{tenantId}
               AND object_type = #{objectType}
               AND status <> 'DELETED'
               AND JSON_SEARCH(config_json, 'one', #{fieldCode}, '!',
                       '$.stages[*].requiredFieldCodes[*]') IS NOT NULL
            """)
    long countReferencingField(@Param("tenantId") Long tenantId,
                               @Param("objectType") String objectType,
                               @Param("fieldCode") String escapedFieldCode);

    @Select("""
            SELECT COUNT(*)
              FROM stage_workflow_definition workflow
              JOIN JSON_TABLE(workflow.config_json, '$.stages[*]'
                   COLUMNS (approval_definition_id BIGINT PATH '$.approvalDefinitionId'
                            NULL ON EMPTY)) stage_ref
             WHERE workflow.tenant_id = #{tenantId}
               AND workflow.status <> 'DELETED'
               AND stage_ref.approval_definition_id = #{approvalDefinitionId}
            """)
    long countReferencingApprovalDefinition(
            @Param("tenantId") Long tenantId,
            @Param("approvalDefinitionId") Long approvalDefinitionId);

    @Select("""
            SELECT DISTINCT workflow.*
              FROM stage_workflow_definition workflow
              JOIN JSON_TABLE(workflow.config_json, '$.stages[*]'
                   COLUMNS (approval_definition_id BIGINT PATH '$.approvalDefinitionId'
                            NULL ON EMPTY)) stage_ref
             WHERE workflow.tenant_id = #{tenantId}
               AND workflow.status <> 'DELETED'
               AND stage_ref.approval_definition_id = #{approvalDefinitionId}
            """)
    List<StageWorkflowDefinitionPO> selectReferencingApprovalDefinition(
            @Param("tenantId") Long tenantId,
            @Param("approvalDefinitionId") Long approvalDefinitionId);
}
