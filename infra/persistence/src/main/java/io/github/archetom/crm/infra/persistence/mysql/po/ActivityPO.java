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
@TableName("crm_activity")
public class ActivityPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String subjectType;
    private Long subjectId;
    private String kind;
    private Long creatorActorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String category;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String purpose;
    private String content;
    private LocalDateTime occurredTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextContactTime;
    private String status;
}
