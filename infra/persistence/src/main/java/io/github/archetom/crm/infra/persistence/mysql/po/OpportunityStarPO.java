package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_opportunity_star")
public class OpportunityStarPO {
    private Long tenantId;
    private Long actorId;
    private Long opportunityId;
    private LocalDateTime createdTime;
}
