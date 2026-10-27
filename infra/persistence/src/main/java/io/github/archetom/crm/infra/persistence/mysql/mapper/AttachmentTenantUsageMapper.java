package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentTenantUsagePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AttachmentTenantUsageMapper extends BaseMapper<AttachmentTenantUsagePO> {
    @Insert("INSERT IGNORE INTO crm_attachment_tenant_usage (tenant_id) VALUES (#{tenantId})")
    int insertIfAbsent(@Param("tenantId") long tenantId);

    @Select("SELECT * FROM crm_attachment_tenant_usage WHERE tenant_id = #{tenantId} FOR UPDATE")
    AttachmentTenantUsagePO selectForUpdate(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE crm_attachment_tenant_usage SET ready_bytes = #{readyBytes}, quarantine_bytes = #{quarantineBytes},
              pending_bytes = #{pendingBytes}, pending_intents = #{pendingIntents}, active_uploads = #{activeUploads},
              active_inspections = #{activeInspections}, active_downloads = #{activeDownloads},
              updated_time = #{now}, version = version + 1
            WHERE tenant_id = #{tenantId} AND version = #{version}
            """)
    int replaceChecked(@Param("tenantId") long tenantId, @Param("version") long version,
                       @Param("readyBytes") long readyBytes, @Param("quarantineBytes") long quarantineBytes,
                       @Param("pendingBytes") long pendingBytes, @Param("pendingIntents") long pendingIntents,
                       @Param("activeUploads") long activeUploads, @Param("activeInspections") long activeInspections,
                       @Param("activeDownloads") long activeDownloads, @Param("now") LocalDateTime now);
}
