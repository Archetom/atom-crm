package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_customer_pool_membership")
public class CustomerPoolMembershipPO {
    private Long tenantId;
    private Long customerId;
    private Long poolId;
    private Long previousOwnerActorId;
    private LocalDateTime enteredTime;
}
