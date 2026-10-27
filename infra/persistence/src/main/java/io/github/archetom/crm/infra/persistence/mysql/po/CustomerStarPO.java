package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_customer_star")
public class CustomerStarPO {
    private Long tenantId;
    private Long actorId;
    private Long customerId;
    private LocalDateTime createdTime;
}
