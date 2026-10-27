package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_instance_manager")
public class ApprovalManagerPO {
    private Long tenantId;
    private Long instanceId;
    private Long actorId;
    private LocalDateTime createdTime;
}
