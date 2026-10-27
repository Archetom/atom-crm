package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.FieldLayoutPreferencePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FieldLayoutPreferenceMapper extends BaseMapper<FieldLayoutPreferencePO> {

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM field_layout_preference
                WHERE tenant_id = #{tenantId}
                  AND object_type = #{objectType}
                  AND (
                    JSON_CONTAINS(ordered_codes, JSON_QUOTE(#{fieldCode}))
                    OR JSON_CONTAINS(hidden_codes, JSON_QUOTE(#{fieldCode}))
                    OR JSON_CONTAINS(pinned_codes, JSON_QUOTE(#{fieldCode}))
                    OR JSON_CONTAINS_PATH(
                        column_widths, 'one', CONCAT('$."', #{fieldCode}, '"'))
                  )
                LIMIT 1
            )
            """)
    boolean existsReferencingField(@Param("tenantId") Long tenantId,
                                   @Param("objectType") String objectType,
                                   @Param("fieldCode") String fieldCode);
}
