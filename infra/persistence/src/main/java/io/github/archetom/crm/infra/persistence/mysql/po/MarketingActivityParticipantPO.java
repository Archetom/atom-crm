package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("crm_marketing_activity_participant")
public class MarketingActivityParticipantPO {
    private Long tenantId;
    private Long marketingActivityId;
    private Long actorId;
}
