package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contact_star")
public class ContactStarPO {
    private Long tenantId;
    private Long actorId;
    private Long contactId;
    private LocalDateTime createdTime;
}
