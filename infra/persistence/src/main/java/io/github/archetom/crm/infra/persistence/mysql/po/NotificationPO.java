package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_notification")
public class NotificationPO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long recipientActorId;
    private String kind;
    private Long reminderOccurrenceId;
    private Long workTaskId;
    private Long taskNotificationProjectionVersion;
    private String title;
    private String priority;
    private LocalDateTime dueTime;
    private String status;
    @TableField("read_time") private LocalDateTime readTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
