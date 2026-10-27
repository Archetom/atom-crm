package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_marketing_activity")
public class MarketingActivityPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long createdByActorId;
    private String name;
    private String relatedObjectType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long marketingFormId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String activityType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal budget;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private Long viewCount;
    private Long submissionCount;
    private Boolean oneSubmissionPerUser;
    private String status;
}
