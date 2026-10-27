package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_invoice_profile")
public class InvoiceProfilePO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
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
    private String remark;
    private String status;
}
