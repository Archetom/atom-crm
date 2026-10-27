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
@TableName("stage_workflow_record")
public class StageWorkflowRecordPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private Long workflowVersion;
    private String objectType;
    private Long subjectId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentStageCode;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long pendingApprovalInstanceId;
    private String progressJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String outcomeRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedTime;
}
