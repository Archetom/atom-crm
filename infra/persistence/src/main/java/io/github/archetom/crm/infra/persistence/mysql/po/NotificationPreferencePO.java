package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_notification_preference")
public class NotificationPreferencePO {
    private Long tenantId;
    private Long recipientActorId;
    private Boolean emailEnabled;
    private Boolean oaAnnouncementEmailEnabled;
    private LocalDateTime updatedTime;
}
