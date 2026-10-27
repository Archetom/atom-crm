package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentReadLeasePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AttachmentReadLeaseMapper extends BaseMapper<AttachmentReadLeasePO> {
    @Select("""
            SELECT EXISTS(SELECT 1 FROM crm_attachment_read_lease
             WHERE tenant_id = #{tenantId} AND file_id = #{fileId} AND content_version = #{contentVersion}
               AND state = 'OPEN' AND expires_at > #{now})
            """)
    boolean existsOpen(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                       @Param("contentVersion") long contentVersion, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_read_lease SET state = 'RELEASED', released_time = #{now}
             WHERE lease_id = #{leaseId} AND tenant_id = #{tenantId} AND file_id = #{fileId}
               AND content_version = #{contentVersion} AND state = 'OPEN'
            """)
    int release(@Param("leaseId") byte[] leaseId, @Param("tenantId") long tenantId,
                @Param("fileId") long fileId, @Param("contentVersion") long contentVersion,
                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_read_lease SET state = 'RELEASED', released_time = #{now}
             WHERE tenant_id = #{tenantId} AND state = 'OPEN' AND expires_at <= #{now}
            """)
    int expireOpen(@Param("tenantId") long tenantId, @Param("now") LocalDateTime now);
}
