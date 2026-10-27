package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_customer_pool_rule")
public class CustomerPoolRulePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long poolId;
    private String ruleType;
    private Integer inactiveDays;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
