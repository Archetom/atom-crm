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

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_receivable_plan")
public class ReceivablePlanPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long contractId;
    private Integer installment;
    private LocalDate dueDate;
    private BigDecimal plannedAmount;
    private String paymentMethod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer reminderDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private String status;
}
