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
@TableName("approval_instance")
public class ApprovalInstancePO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Long definitionVersion;
    private String objectType;
    private Long subjectId;
    private String idempotencyKey;
    private String title;
    private Long applicantActorId;
    private String repeatMode;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer currentStepOrder;
    private String factsJson;
    private LocalDateTime submittedTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedTime;
}
