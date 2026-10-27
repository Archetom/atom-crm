package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_lead_star")
public class LeadStarPO {
    private Long tenantId;
    private Long actorId;
    private Long leadId;
    private LocalDateTime createdTime;
}
