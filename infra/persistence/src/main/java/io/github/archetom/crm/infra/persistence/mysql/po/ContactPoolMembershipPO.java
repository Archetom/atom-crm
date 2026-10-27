package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contact_pool_membership")
public class ContactPoolMembershipPO {
    private Long tenantId;
    private Long contactId;
    private Long poolId;
    private Long previousOwnerActorId;
    private LocalDateTime enteredTime;
}
