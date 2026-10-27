package io.github.archetom.crm.infra.persistence.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomizationObjectGuardMapper {

    @Insert("""
            INSERT INTO customization_object_guard (tenant_id, object_type)
            VALUES (#{tenantId}, #{objectType})
            ON DUPLICATE KEY UPDATE
                object_type = customization_object_guard.object_type
            """)
    int acquire(@Param("tenantId") Long tenantId, @Param("objectType") String objectType);
}
