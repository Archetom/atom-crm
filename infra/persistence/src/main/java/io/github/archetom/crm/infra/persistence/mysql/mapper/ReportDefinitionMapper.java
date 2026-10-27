package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ReportDefinitionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportDefinitionMapper extends BaseMapper<ReportDefinitionPO> {

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM report_definition definition_root
                WHERE definition_root.tenant_id = #{tenantId}
                  AND definition_root.object_type = #{objectType}
                  AND definition_root.status = 'ACTIVE'
                  AND (definition_root.time_field_code = #{fieldCode}
                       OR definition_root.dimension_field_code = #{fieldCode}
                       OR EXISTS (
                           SELECT 1
                           FROM JSON_TABLE(
                               definition_root.filters_json,
                               '$[*]' COLUMNS (field_code VARCHAR(64) PATH '$.fieldCode')
                           ) report_filter
                           WHERE report_filter.field_code = #{fieldCode}
                       )
                       OR EXISTS (
                           SELECT 1
                           FROM JSON_TABLE(
                               definition_root.measures_json,
                               '$[*]' COLUMNS (field_code VARCHAR(64) PATH '$.fieldCode')
                           ) report_measure
                           WHERE report_measure.field_code = #{fieldCode}
                       ))
                LIMIT 1
            )
            """)
    boolean existsReferencingField(
            @Param("tenantId") long tenantId,
            @Param("objectType") String objectType,
            @Param("fieldCode") String fieldCode);
}
