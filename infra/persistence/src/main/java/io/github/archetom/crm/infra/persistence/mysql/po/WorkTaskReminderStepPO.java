package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("crm_work_task_reminder_step")
public class WorkTaskReminderStepPO {
    private Long tenantId;
    private Long workTaskId;
    private String stepKey;
    private String timing;
    private Integer offsetMinutes;
    private Boolean notifyAssignee;
    private Boolean notifyCreator;
    private Boolean notifyFollowers;
}
