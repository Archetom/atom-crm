package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationPreferencePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface NotificationPreferenceMapper extends BaseMapper<NotificationPreferencePO> {
    @Insert("""
            INSERT INTO crm_notification_preference
              (tenant_id, recipient_actor_id, email_enabled, oa_announcement_email_enabled, updated_time)
            VALUES (#{tenantId}, #{recipientActorId}, #{emailEnabled}, #{oaAnnouncementEmailEnabled}, #{updatedTime})
            ON DUPLICATE KEY UPDATE email_enabled = VALUES(email_enabled),
                oa_announcement_email_enabled = VALUES(oa_announcement_email_enabled),
                updated_time = VALUES(updated_time)
            """)
    int upsert(@Param("tenantId") long tenantId, @Param("recipientActorId") long recipientActorId,
               @Param("emailEnabled") boolean emailEnabled,
               @Param("oaAnnouncementEmailEnabled") boolean oaAnnouncementEmailEnabled,
               @Param("updatedTime") LocalDateTime updatedTime);

    @Insert("""
            INSERT INTO crm_notification_preference
              (tenant_id, recipient_actor_id, email_enabled, updated_time)
            VALUES (#{tenantId}, #{recipientActorId}, #{emailEnabled}, #{updatedTime})
            ON DUPLICATE KEY UPDATE email_enabled = VALUES(email_enabled),
                updated_time = VALUES(updated_time)
            """)
    int upsertEmailPreservingOa(@Param("tenantId") long tenantId,
                                @Param("recipientActorId") long recipientActorId,
                                @Param("emailEnabled") boolean emailEnabled,
                                @Param("updatedTime") LocalDateTime updatedTime);
}
