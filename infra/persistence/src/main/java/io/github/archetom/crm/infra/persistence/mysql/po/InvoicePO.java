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
@TableName("crm_invoice")
public class InvoicePO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long contractId;
    private String applicationNumber;
    private BigDecimal amount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate requestedDate;
    private String invoiceType;
    private String titleType;
    private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String taxNumber;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String registeredAddress;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String registeredPhone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactPhone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shippingAddress;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String invoiceNumber;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate issuedDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String logisticsNumber;
}
