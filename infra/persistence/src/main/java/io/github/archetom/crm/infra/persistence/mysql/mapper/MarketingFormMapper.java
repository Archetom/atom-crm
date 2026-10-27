package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MarketingFormMapper extends BaseMapper<MarketingFormPO> {

    @Select("""
            SELECT COUNT(*)
            FROM crm_marketing_form form_root
            JOIN crm_marketing_form_field form_field
              ON form_field.tenant_id = form_root.tenant_id
             AND form_field.marketing_form_id = form_root.id
            WHERE form_root.tenant_id = #{tenantId}
              AND form_root.status = 'ACTIVE'
              AND form_field.field_code = #{fieldCode}
            """)
    long countActiveReferencingField(
            @Param("tenantId") long tenantId,
            @Param("fieldCode") String fieldCode);
}
