package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contact_pool_administrator")
public class ContactPoolAdministratorPO {
    private Long tenantId;
    private Long poolId;
    private Long actorId;
    private LocalDateTime createdTime;
}
