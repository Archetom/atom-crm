package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_product")
public class ProductPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
    private Long ownerActorId;
    private String name;
    private String code;
    private String codeKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;
    private BigDecimal listPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String availability;
    private String status;
}
