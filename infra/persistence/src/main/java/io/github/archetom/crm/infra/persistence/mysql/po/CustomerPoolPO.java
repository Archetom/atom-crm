package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_pool")
public class CustomerPoolPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String status;
    private Integer dailyClaimLimit;
    private Integer previousOwnerCooldownDays;
    @TableField("automatic_release_enabled")
    private Boolean automaticReleaseEnabled;
}
