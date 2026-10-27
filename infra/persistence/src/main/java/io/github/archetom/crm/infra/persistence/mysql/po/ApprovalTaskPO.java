package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_review_task")
public class ApprovalTaskPO extends BasePO {
    @TableId(type = IdType.INPUT)
    private String taskKey;
    private Long instanceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceTaskKey;
    private String stepCode;
    private String stepName;
    private Integer stepOrder;
    @TableField("approval_mode")
    private String mode;
    private Integer actorOrder;
    private Long assignedActorId;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime actedTime;
}
