package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("crm_work_task_series_reminder_step")
public class WorkTaskSeriesReminderStepPO {
    private Long tenantId;
    private Long workTaskSeriesId;
    private String stepKey;
    private String timing;
    private Integer offsetMinutes;
    private Boolean notifyAssignee;
    private Boolean notifyCreator;
    private Boolean notifyFollowers;
}
