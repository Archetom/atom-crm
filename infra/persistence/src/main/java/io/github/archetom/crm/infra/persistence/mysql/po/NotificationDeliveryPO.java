package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_notification_delivery")
public class NotificationDeliveryPO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String sourceKind;
    private Long notificationId;
    private Long recipientActorId;
    private Long workTaskId;
    private Long oaAnnouncementId;
    private String channel;
    private String templateRevision;
    private String payloadDigest;
    private String endpointDigest;
    private String endpointDigestVersion;
    private String status;
    private Integer attemptCount;
    private LocalDateTime availableAt;
    private LocalDateTime deadlineAt;
    @TableField("lease_owner") private String leaseOwner;
    private Long leaseFence;
    private LocalDateTime leaseUntil;
    private String guarantee;
    private String effectCertainty;
    private LocalDateTime idempotencyUntil;
    private LocalDateTime dispatchStartedTime;
    private LocalDateTime terminalTime;
    private String safeReason;
    private LocalDateTime createdTime;
}
