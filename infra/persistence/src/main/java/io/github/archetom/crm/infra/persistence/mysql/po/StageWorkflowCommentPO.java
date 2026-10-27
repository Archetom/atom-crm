package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stage_workflow_comment")
public class StageWorkflowCommentPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowRecordId;
    private Long authorActorId;
    private String content;
    private String status;
}
