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
@TableName("crm_contract")
public class ContractPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long opportunityId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    private String name;
    private String contractNumber;
    private String contractNumberKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate orderDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contractType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String paymentType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private BigDecimal subtotal;
    private BigDecimal discountRate;
    private BigDecimal totalAmount;
    private String status;
}
