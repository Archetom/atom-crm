package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_work_task_follower")
public class WorkTaskFollowerPO {
    private Long tenantId;
    private Long workTaskId;
    private Long followerActorId;
    private LocalDateTime createdTime;
}
