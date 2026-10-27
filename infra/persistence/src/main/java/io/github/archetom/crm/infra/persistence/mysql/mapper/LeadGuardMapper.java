package io.github.archetom.crm.infra.persistence.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LeadGuardMapper {

    @Insert("""
            INSERT INTO crm_lead_guard (tenant_id, guard_key)
            VALUES (#{tenantId}, 1)
            ON DUPLICATE KEY UPDATE guard_key = crm_lead_guard.guard_key
            """)
    int acquire(@Param("tenantId") Long tenantId);
}
