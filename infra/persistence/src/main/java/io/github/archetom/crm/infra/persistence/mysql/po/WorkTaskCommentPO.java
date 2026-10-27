package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_work_task_comment")
public class WorkTaskCommentPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workTaskId;
    private Long authorActorId;
    private String content;
    private String status;
}
