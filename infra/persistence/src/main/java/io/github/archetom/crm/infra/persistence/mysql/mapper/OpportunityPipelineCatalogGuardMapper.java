package io.github.archetom.crm.infra.persistence.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OpportunityPipelineCatalogGuardMapper {

    @Insert("""
            INSERT INTO crm_opportunity_pipeline_catalog_guard (tenant_id)
            VALUES (#{tenantId})
            ON DUPLICATE KEY UPDATE tenant_id = crm_opportunity_pipeline_catalog_guard.tenant_id
            """)
    int acquire(@Param("tenantId") Long tenantId);
}
