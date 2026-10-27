package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Relational root row for a tenant customer. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class CustomerPO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ownerActorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentCustomerId;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phoneNumber;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String telephone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String website;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String status;
    @TableField("is_locked")
    private Boolean locked;
    private String dealStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dealTime;
}
