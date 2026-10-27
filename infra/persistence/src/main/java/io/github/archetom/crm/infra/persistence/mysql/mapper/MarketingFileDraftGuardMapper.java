package io.github.archetom.crm.infra.persistence.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MarketingFileDraftGuardMapper {
    @Insert("""
            INSERT INTO crm_marketing_file_draft_guard (tenant_id, marketing_form_id)
            VALUES (#{tenantId}, #{formId})
            ON DUPLICATE KEY UPDATE version = version
            """)
    int ensure(@Param("tenantId") long tenantId, @Param("formId") long formId);

    @Select("""
            SELECT version FROM crm_marketing_file_draft_guard
             WHERE tenant_id = #{tenantId} AND marketing_form_id = #{formId} FOR UPDATE
            """)
    Long lock(@Param("tenantId") long tenantId, @Param("formId") long formId);
}
