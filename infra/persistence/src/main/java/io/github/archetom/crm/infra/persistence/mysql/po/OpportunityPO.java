package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Relational root row for a tenant opportunity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity")
public class OpportunityPO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private String name;
    private String pipelineCode;
    private String stageCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedCloseDate;
    private BigDecimal estimatedAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextContactTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String outcome;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime outcomeTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String outcomeRemark;
    private String status;
}
