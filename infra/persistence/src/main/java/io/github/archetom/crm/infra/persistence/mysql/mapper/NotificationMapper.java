package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.NotificationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationPO> {

    @Update("""
            UPDATE crm_notification
            SET status = 'READ', read_time = UTC_TIMESTAMP(6), updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND id = #{notificationId}
              AND recipient_actor_id = #{recipientActorId} AND status = 'UNREAD'
            """)
    int markReadIfUnread(@Param("tenantId") long tenantId,
                         @Param("notificationId") long notificationId,
                         @Param("recipientActorId") long recipientActorId);

    @Delete("""
            DELETE FROM crm_notification
            WHERE created_time < #{beforeExclusive}
              AND NOT EXISTS (
                  SELECT 1 FROM crm_notification_delivery delivery
                  WHERE delivery.tenant_id = crm_notification.tenant_id
                    AND delivery.notification_id = crm_notification.id
              )
            ORDER BY created_time ASC, id ASC LIMIT #{limit}
            """)
    int deleteExpired(@Param("beforeExclusive") java.time.LocalDateTime beforeExclusive,
                      @Param("limit") int limit);
}
