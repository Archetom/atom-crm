package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_work_task_reminder_occurrence")
public class WorkTaskReminderOccurrencePO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long workTaskId;
    private Long notificationProjectionVersion;
    private String stepKey;
    private LocalDateTime dueTime;
    private String status;
    private Integer attemptCount;
    private LocalDateTime availableAt;
    private LocalDateTime deadlineAt;
    @TableField("lease_owner") private String leaseOwner;
    private Long leaseFence;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdTime;
    private LocalDateTime terminalTime;
    private String safeReason;
}
