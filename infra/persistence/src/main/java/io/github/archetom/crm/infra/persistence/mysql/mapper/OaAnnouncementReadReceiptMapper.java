package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementReadReceiptPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OaAnnouncementReadReceiptMapper
        extends BaseMapper<OaAnnouncementReadReceiptPO> {

    @Insert("""
            INSERT INTO oa_announcement_read_receipt
                (tenant_id, announcement_id, actor_id, read_time)
            VALUES (#{tenantId}, #{announcementId}, #{actorId}, #{readTime})
            ON DUPLICATE KEY UPDATE
                read_time = oa_announcement_read_receipt.read_time
            """)
    int insertIfAbsent(
            @Param("tenantId") Long tenantId,
            @Param("announcementId") Long announcementId,
            @Param("actorId") Long actorId,
            @Param("readTime") LocalDateTime readTime);
}
