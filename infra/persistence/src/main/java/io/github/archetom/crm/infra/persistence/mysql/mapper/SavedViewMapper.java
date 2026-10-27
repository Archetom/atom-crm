package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.SavedViewPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SavedViewMapper extends BaseMapper<SavedViewPO> {

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM saved_view saved
                JOIN JSON_TABLE(
                    saved.conditions_json,
                    '$[*]' COLUMNS (field_code VARCHAR(64) PATH '$.fieldCode')
                ) conditions ON TRUE
                WHERE saved.tenant_id = #{tenantId}
                  AND saved.object_type = #{objectType}
                  AND conditions.field_code = #{fieldCode}
                LIMIT 1
            )
            """)
    boolean existsReferencingField(@Param("tenantId") Long tenantId,
                                   @Param("objectType") String objectType,
                                   @Param("fieldCode") String fieldCode);
}
