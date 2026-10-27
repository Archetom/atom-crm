package io.github.archetom.crm.infra.persistence.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductCatalogGuardMapper {

    @Insert("""
            INSERT INTO crm_product_catalog_guard (tenant_id)
            VALUES (#{tenantId})
            ON DUPLICATE KEY UPDATE tenant_id = crm_product_catalog_guard.tenant_id
            """)
    int acquire(@Param("tenantId") Long tenantId);
}
